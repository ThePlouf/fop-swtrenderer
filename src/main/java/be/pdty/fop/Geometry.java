/*
 * Copyright 2019 Philippe Detournay
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package be.pdty.fop;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Geometry utilities.
 */
public class Geometry
{
  private static final double ITERATOR_FLATNESS=1.0;
  private static final double MITER_LIMIT=4.0;
  private static final double ROUND_POINT_DISTANCE=10.0;
  private static final double ROUND_MAX_INCREMENT=0.5;

  //We assume that if a point is already defined in the target path (that is, first is false), then it is somewhere on
  //this segment's extended path. In other words, we do *not* have to first position the curve at the beginning of the
  //segment.
  //If this is the very first segment to be added to the path (first is true), then we also assume that the last point
  //that will be added on the curse will be on this segment's extended path. So and similarly to the previous point,
  //we do not have to position the curve at the beginning of the segment and we can effectively "moveTo" directly
  //to the end of the segment.
  private static void appendSegment(double c1x,double c1y,double c2x,double c2y,double nx,double ny,double offset,int joinType,boolean first,Path2D.Double target) {
    //Current segment
    double cdx=c2x-c1x;
    double cdy=c2y-c1y;
    double clength=Math.sqrt(cdx*cdx+cdy*cdy);
    cdx=cdx/clength;
    cdy=cdy/clength;
    double lc2x=c2x+cdy*offset;
    double lc2y=c2y-cdx*offset;
    
    //Next segment
    double ndx=nx-c2x;
    double ndy=ny-c2y;
    double nlength=Math.sqrt(ndx*ndx+ndy*ndy);
    ndx=ndx/nlength;
    ndy=ndy/nlength;
    double ln1x=c2x+ndy*offset;
    double ln1y=c2y-ndx*offset;

    //Miter junction point
    double mdx=cdy+ndy;
    double mdy=-(cdx+ndx);
    double mLength=Math.sqrt(mdx*mdx+mdy*mdy);
    if(mLength>0) {
      mdx/=mLength;
      mdy/=mLength;
    }
    
    //This gives us the cosine of the half-angle. Note that it can be zero!
    double cos=cdy*mdx-cdx*mdy;
    
    if(cdy*ndx-cdx*ndy>=0) {
      //A concave point. Draw via the original (non-extended) point as
      //per http://www.me.berkeley.edu/~mcmains/pubs/DAC05OffsetPolygon.pdf
      if(first) {
        target.moveTo(lc2x,lc2y);
      } else {
        target.lineTo(lc2x,lc2y);
      }
      target.lineTo(c2x,c2y);
      target.lineTo(ln1x,ln1y);
    } else {
      //A convex point
      switch(joinType) {
        case BasicStroke.JOIN_BEVEL:
          //Simple bevel, we'll go to our end first, then to the beginning of next line.
          if(first) {
            target.moveTo(lc2x,lc2y);
          } else {
            target.lineTo(lc2x,lc2y);
          }
          target.lineTo(ln1x,ln1y);
          break;
        case BasicStroke.JOIN_MITER:
        default:
          //Miter.
          double lim=MITER_LIMIT;
          //The length of the miter is extent/cos, and we allow up to lim*extent.
          //Therefore if extent/cos>lim*extent (or cos*lim<1), we'll revert to bevel.
          if(cos*lim<1.0) {
            //Bevel
            if(first) {
              target.moveTo(lc2x,lc2y);
            } else {
              target.lineTo(lc2x,lc2y);
            }
            target.lineTo(ln1x,ln1y);
          } else {
            //Actual miter.
            double mitX=c2x+mdx*offset/cos;
            double mitY=c2y+mdy*offset/cos;
            if(first) {
              target.moveTo(mitX,mitY);
            } else {
              target.lineTo(mitX,mitY);
            }
          }
          break;
        case BasicStroke.JOIN_ROUND:
          //Let's go to the beginning of the joint first.
          if(first) {
            target.moveTo(lc2x,lc2y);
          } else {
            target.lineTo(lc2x,lc2y);
          }

          //Draw the circle. Note that we could get a very good approximation of a circle by
          //using cubic curves, but there is some value in having a pure polygonal answer.
          if(offset>0) {
            //Starting angle
            double angle=Math.atan2(-cdx,cdy);
            //Length of arc
            double arc=Math.acos(cos)*2;
            //Arc increment
            double increment=ROUND_POINT_DISTANCE/offset;
            if(increment>ROUND_MAX_INCREMENT) increment=ROUND_MAX_INCREMENT;
            //Ending angle
            double angleEnd=angle+arc-increment;
            
            //Let's try to ensure the partial increment
            //due to rounding error is evenly spread on two ends
            double off=arc-((int)(arc/increment+0.5))*increment;
            angle+=off/2;
            
            while(angle<angleEnd) {
              angle+=increment;
              double x=Math.cos(angle)*offset+c2x;
              double y=Math.sin(angle)*offset+c2y;
              target.lineTo(x,y);
            }
            target.lineTo(ln1x,ln1y);
          }
          break;
      }
      
    }
  }

  //See https://en.wikipedia.org/wiki/Shoelace_formula
  private static double area(List<Line2D.Double> lines) {
    double ans=0;
    for(Line2D.Double line:lines) {
      ans+=(line.x1*line.y2)-(line.x2*line.y1);
    }
    return ans;
  }
  
  private static Area renderSimpleShape(List<Line2D.Double> lines,double offset,int joinType) {
    Path2D.Double ans = new Path2D.Double(Path2D.WIND_NON_ZERO);
    
    for(int i=0;i<lines.size();i++) {
      int nextIndex=i+1;
      if(nextIndex==lines.size()) nextIndex=0;
      
      Line2D.Double current=lines.get(i);
      Line2D.Double next=lines.get(nextIndex);
      
      appendSegment(current.x1,current.y1,current.x2,current.y2,next.x2,next.y2,offset,joinType,i==0,ans);
    }
    
    //Creating an area with non-zero winding will perform a proper cleanup of the overlapping areas
    //generated by the concave points.
    return new Area(ans);

  }
  
  /**
   * Return an area that is the offset of the given input (closed) shape. Note that only clockwise (on the
   * screen coordinates) sub-shapes will be considered. That is, any "hole" in the shape will be removed, and
   * only the outer outline will be considered.
   * Note that this implementation is simpler than the Clipper library (http://www.angusj.com/delphi/clipper.php)
   * because we take advantage of the AWT Area features instead of implementing winding ourselves. But the
   * approach is very similar.
   * Also note that we will flatten the input shape. That is, the output will be a polygon area even if there
   * are curves defined in the input shape.
   * @param shape input shape.
   * @param offset offset (must be greater than 0).
   * @param joinType joinType, use constants from BasicStroke.
   * @return offset area.
   */
  public static Area offsetShape(Shape shape,double offset,int joinType) {
    PathIterator pi=shape.getPathIterator(null,ITERATOR_FLATNESS);
    double[] data=new double[2];
    
    //As a first step, we will build a collection of simple shapes (that contain only one starting point
    //and one looping point), also eliminating empty segments by the way.
    double firstX=0;
    double firstY=0;
    double previousX=0;
    double previousY=0;
    List<List<Line2D.Double>> simpleShapes=new ArrayList<>();
    List<Line2D.Double> current=new ArrayList<>();
    
    while(!pi.isDone()) {
      int type=pi.currentSegment(data);
      double targetX=0;
      double targetY=0;
      switch(type)
      {
        case PathIterator.SEG_MOVETO:
          if(current.size()>0) {
            simpleShapes.add(current);
            current=new ArrayList<>();
          }
          firstX=data[0];
          firstY=data[1];
          targetX=firstX;
          targetY=firstY;
          break;
        case PathIterator.SEG_LINETO:
          targetX=data[0];
          targetY=data[1];
          if(previousX!=targetX || previousY!=targetY) {
            current.add(new Line2D.Double(previousX,previousY,targetX,targetY));
          }
          break;
        case PathIterator.SEG_CLOSE:
          targetX=firstX;
          targetY=firstY;
          if(previousX!=targetX || previousY!=targetY) {
            current.add(new Line2D.Double(previousX,previousY,targetX,targetY));
          }
          if(current.size()>0) {
            simpleShapes.add(current);
            current=new ArrayList<>();
          }
          break;
      }
      
      previousX=targetX;
      previousY=targetY;
      
      pi.next();
    }    
    
    //The real work starts here.
    Area finalArea=new Area();
    for(int s=0;s<simpleShapes.size();s++) {
      //Only consider positive area shapes (i.e. ignore holes).
      if(area(simpleShapes.get(s))>0) {
        finalArea.add(renderSimpleShape(simpleShapes.get(s),offset/2.0,joinType));
      }
    }
    
    return finalArea;
  }
  
}
