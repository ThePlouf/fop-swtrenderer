/*
 * Copyright 2018 Philippe Detournay
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
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.apache.fop.ResourceEventProducer;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.area.CTM;
import org.apache.fop.area.PageViewport;
import org.apache.fop.area.Trait;
import org.apache.fop.area.inline.Image;
import org.apache.fop.area.inline.InlineArea;
import org.apache.fop.area.inline.Leader;
import org.apache.fop.area.inline.SpaceArea;
import org.apache.fop.area.inline.TextArea;
import org.apache.fop.area.inline.WordArea;
import org.apache.fop.datatypes.URISpecification;
import org.apache.fop.fo.Constants;
import org.apache.fop.fonts.Font;
import org.apache.fop.fonts.FontCollection;
import org.apache.fop.fonts.FontInfo;
import org.apache.fop.fonts.FontManager;
import org.apache.fop.fonts.FontMetrics;
import org.apache.fop.fonts.Typeface;
import org.apache.fop.fonts.base14.Base14FontCollection;
import org.apache.fop.render.AbstractPathOrientedRenderer;
import org.apache.fop.render.RendererContext;
import org.apache.fop.render.pdf.CTMHelper;
import org.apache.fop.traits.BorderProps;
import org.apache.fop.traits.BorderProps.Mode;
import org.apache.fop.util.ColorUtil;
import org.apache.xmlgraphics.image.loader.ImageException;
import org.apache.xmlgraphics.image.loader.ImageFlavor;
import org.apache.xmlgraphics.image.loader.ImageInfo;
import org.apache.xmlgraphics.image.loader.ImageManager;
import org.apache.xmlgraphics.image.loader.ImageSessionContext;
import org.apache.xmlgraphics.image.loader.impl.ImageGraphics2D;
import org.apache.xmlgraphics.image.loader.impl.ImageRendered;
import org.apache.xmlgraphics.image.loader.impl.ImageXMLDOM;
import org.apache.xmlgraphics.image.loader.util.ImageUtil;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;

/**
 * SWTRenderer. Compared to the AWTRenderer, the SWTRenderer has the following
 * limitations: - Only Base14 fonts are supported; - Fonts glyph offsets are not
 * supported; - Graphics2D images are not supported.
 */
public class SWTRenderer extends AbstractPathOrientedRenderer implements Pageable {
	private List<PageViewport> pageViewportList;
	private Stack<State> stateStack;

	private GeneralPath currentPath;
	private State state;
	private GCWrapper wrapper;

	/**
	 * Default constructor
	 * 
	 * @param uAgent
	 *            the user agent that contains configuration details. This
	 *            cannot be null.
	 */
	public SWTRenderer(FOUserAgent uAgent) {
		super(uAgent);
		userAgent.setRendererOverride(this);
	}

	@Override
	public int getNumberOfPages() {
		return pageViewportList.size();
	}

	@Override
	public PageFormat getPageFormat(int pageIndex) {
		PageViewport page = pageViewportList.get(pageIndex);
		double width = page.getViewArea().getWidth() / 1000d;
		double height = page.getViewArea().getHeight() / 1000d;

		return new PageFormat() {
			@Override
			public double getWidth() {
				return width;
			}

			@Override
			public double getHeight() {
				return height;
			}
		};
	}

	@Override
	public Printable getPrintable(int pageIndex) {
		return new Printable() {
			@Override
			public void print(GC targetGc) {
				wrapper = new GCWrapper(targetGc);
				try {
					state = new State();
					stateStack = new Stack<>();

					currentBPPosition = 0;
					currentIPPosition = 0;
					renderPageAreas(pageViewportList.get(pageIndex).getPage());
				} finally {
					wrapper.dispose();
				}
			}
		};
	}

	@Override
	public FOUserAgent getUserAgent() {
		return userAgent;
	}

	@Override
	public void setupFontInfo(FontInfo inFontInfo) {
		fontInfo = inFontInfo;

		FontManager fontManager = userAgent.getFontManager();

		FontCollection[] fontCollections = new FontCollection[] { new Base14FontCollection(true) };
		fontManager.setup(getFontInfo(), fontCollections);
	}

	@Override
	public void startRenderer(OutputStream out) throws IOException {
		pageViewportList = new ArrayList<>();
		super.startRenderer(out);
	}

	@Override
	public void stopRenderer() throws IOException {
	}

	@Override
	public void renderPage(PageViewport pageViewport) throws IOException, FOPException {
		try {
			pageViewportList.add((PageViewport) pageViewport.clone());
		} catch (CloneNotSupportedException e) {
			throw new FOPException(e);
		}
	}

	@Override
	protected void saveGraphicsState() {
		stateStack.push(state);
		state = new State(state);
	}

	@Override
	protected void restoreGraphicsState() {
		state = stateStack.pop();
	}

	@Override
	protected void concatenateTransformationMatrix(AffineTransform at) {
		state.combineTransform(at);
	}

	@Override
	protected void startVParea(CTM ctm, java.awt.Rectangle clippingRect) {

		saveGraphicsState();

		if (clippingRect != null) {
			clipRect((float) clippingRect.getX() / 1000f, (float) clippingRect.getY() / 1000f,
			        (float) clippingRect.getWidth() / 1000f, (float) clippingRect.getHeight() / 1000f);
		}

		state.combineTransform(new AffineTransform(CTMHelper.toPDFArray(ctm)));
	}

	@Override
	protected void endVParea() {
		restoreGraphicsState();
	}

	@Override
	protected void startLayer(String layer) {
	}

	@Override
	protected void endLayer() {
	}

	@Override
	protected List<State> breakOutOfStateStack() {
		List<State> breakOutList;
		breakOutList = new java.util.ArrayList<>();
		while (!stateStack.isEmpty()) {
			breakOutList.add(0, state);
			state = stateStack.pop();
		}
		return breakOutList;
	}

	@Override
	protected void restoreStateStackAfterBreakOut(List breakOutList) {
		Iterator it = breakOutList.iterator();
		while (it.hasNext()) {
			State s = (State) it.next();
			stateStack.push(state);
			state = s;
		}
	}

	@Override
	protected void updateColor(Color col, boolean fill) {
		state.updateColor(col);
	}

	@Override
	protected void clip() {
		if (currentPath == null) {
			throw new IllegalStateException("No current path available!"); //$NON-NLS-1$
		}
		state.combineClip(currentPath);
		currentPath = null;
	}

	@Override
	protected void closePath() {
		currentPath.closePath();
	}

	@Override
	protected void lineTo(float x, float y) {
		if (currentPath == null) {
			currentPath = new GeneralPath();
		}
		currentPath.lineTo(x, y);
	}

	@Override
	protected void moveTo(float x, float y) {
		if (currentPath == null) {
			currentPath = new GeneralPath();
		}
		currentPath.moveTo(x, y);
	}

	@Override
	protected void clipRect(float x, float y, float width, float height) {
		state.combineClip(new GeneralPath(new Rectangle2D.Float(x, y, width, height)));
	}

	@Override
	protected void fillRect(float x, float y, float width, float height) {
		state.configureGC(wrapper);
		wrapper.fillRectangle(x, y, width, height);
	}

	@Override
	protected void drawBorderLine(float x1, float y1, float x2, float y2, boolean horz, boolean startOrBefore,
	        int style, Color col) {
	  //This function is actually not used but our parent imposes to implement it...
	  if(horz) {
	    float width=y2-y1;
	    BorderProps props=new BorderProps(style,(int)(width*1000f),0,0,col,Mode.SEPARATE);
	    drawHLine(x1,x2,y1+width/2,startOrBefore,props);
	  } else {
      float width=x2-x1;
      BorderProps props=new BorderProps(style,(int)(width*1000f),0,0,col,Mode.SEPARATE);
      drawVLine(x1+width/2,y1,y2,startOrBefore,props);
	  }
	}
	
	private static class Rect {
	  float x;
	  float y;
	  float w;
	  float h;
	  
	  public Rect(float left,float top,float width,float height) {
	    x=left;
	    y=top;
	    w=width;
	    h=height;
	  }
	}
	
	private static boolean outer(BorderProps a) {
	  if(a.style==Constants.EN_NONE) return false;
	  if(a.isCollapseOuter()) return true;
	  if(BorderProps.getClippedWidth(a)==0) return true;
	  return false;
	}
	
  private BasicStroke getStroke(BorderProps p) {
    return getStroke(p,1f);
  }
  
	private BasicStroke getStroke(BorderProps p,float weightFactor) {
	  float width=p.width/1000f*weightFactor;
    float unit = Math.abs(4 * width);

    switch(p.style) {
      case Constants.EN_DOTTED:
        return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] { unit/4.0f, unit/2.0f }, 0);
	    case Constants.EN_DASHED:
        return new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] { unit, unit }, 0);
	    case Constants.EN_SOLID:
	    default:
	      return new BasicStroke(width,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER);
	  }
	}
	
	//Draw a rectangle from x,y and of size w,h as chord.
	private void drawRectangle(float x,float y,float w,float h,BorderProps props) {
    switch(props.style) {
      case Constants.EN_HIDDEN:
      case Constants.EN_NONE:
        break;
      case Constants.EN_DOUBLE: {
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props,1f/3f)));
        float weight=props.width/1000f;
        float leftMost=x-weight/2;
        float topMost=y-weight/2;
        wrapper.drawRectangle(leftMost+weight/6,topMost+weight/6,w+weight*2/3,h+weight*2/3);
        wrapper.drawRectangle(leftMost+5*weight/6,topMost+5*weight/6,w-weight*2/3,h-weight*2/3);
        break;
      }
      case Constants.EN_GROOVE:
      case Constants.EN_RIDGE: {
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props,1f/3f)));
        float weight=props.width/1000f;
        float leftMost=x-weight/2;
        float topMost=y-weight/2;
        float colFactor = (props.style == EN_GROOVE ? 0.4f : -0.4f);
        Color uppercol = ColorUtil.lightenColor(props.color, -colFactor);
        Color lowercol = ColorUtil.lightenColor(props.color, colFactor);
        
        wrapper.setColor(Convert.toRGBA(uppercol));
        //Opportunity to use a shape
        wrapper.drawLine(leftMost,topMost+weight/6,leftMost+w+weight*2/3,topMost+weight/6);
        wrapper.drawLine(leftMost+weight/6,topMost+weight*1f/6f,leftMost+weight/6,topMost+h+weight);
        
        wrapper.setColor(Convert.toRGBA(lowercol));
        //Opportunity to use a shape
        wrapper.drawLine(leftMost+2*weight/6,topMost+h+5*weight/6,leftMost+w+weight*2/3,topMost+h+5*weight/6);
        wrapper.drawLine(leftMost+w+5*weight/6,topMost,leftMost+w+5*weight/6,topMost+h+weight);
        
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.drawRectangle(x,y,w,h);
        break;
      }
      case Constants.EN_INSET:
      case Constants.EN_OUTSET: {
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props)));
        float weight=props.width/1000f;
        float colFactor = (props.style == EN_OUTSET ? 0.4f : -0.4f);
        Color uppercol = ColorUtil.lightenColor(props.color, -colFactor);
        Color lowercol = ColorUtil.lightenColor(props.color, colFactor);
        wrapper.setColor(Convert.toRGBA(lowercol));
        //Opportunity to use a shape
        wrapper.drawLine(x-weight/2,y,x+w+weight/2,y);
        wrapper.drawLine(x,y+weight/2,x,y+h-weight/2);
        wrapper.setColor(Convert.toRGBA(uppercol));
        //Opportunity to use a shape
        wrapper.drawLine(x-weight/2,y+h,x+w+weight/2,y+h);
        wrapper.drawLine(x+w,y+weight/2,x+w,y+h-weight/2);
        break;
      }
      case Constants.EN_DOTTED:
      case Constants.EN_DASHED:
      case Constants.EN_SOLID:
      default:
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props)));
        wrapper.drawRectangle(x,y,w,h);
        break;
    }
	}
	
	//Draw a line from x1 to x2 (inclusive), with the middle-line as y.
	//This means the outer box of the line is (x1,y-w/2)-(x2,y+w/2)
	private void drawHLine(float x1,float x2,float y,boolean top,BorderProps props) {
	  switch(props.style) {
	    case Constants.EN_HIDDEN:
      case Constants.EN_NONE:
	      break;
	    case Constants.EN_DOUBLE: {
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props,1f/3f)));
        float h=props.width/1000f;
        float ytop=y-h/2;
        float y1=ytop+h/6;
        float y2=ytop+5*h/6;
        wrapper.drawLine(x1,y1,x2,y1);
        wrapper.drawLine(x1,y2,x2,y2);
	      break;
	    }
	    case Constants.EN_GROOVE:
	    case Constants.EN_RIDGE: {
	      float colFactor = (props.style == EN_GROOVE ? 0.4f : -0.4f);
        Color uppercol = ColorUtil.lightenColor(props.color, -colFactor);
        Color lowercol = ColorUtil.lightenColor(props.color, colFactor);
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props,1f/3f)));
        float h=props.width/1000f;
        float ytop=y-h/2;
        float y1=ytop+h/6;
        float y2=ytop+5*h/6;
        wrapper.setColor(Convert.toRGBA(uppercol));
        wrapper.drawLine(x1,y1,x2,y1);
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.drawLine(x1,y,x2,y);
        wrapper.setColor(Convert.toRGBA(lowercol));
        wrapper.drawLine(x1,y2,x2,y2);
	      break;
	    }
      case Constants.EN_INSET:
      case Constants.EN_OUTSET: {
        float colFactor = (props.style == EN_OUTSET ? 0.4f : -0.4f);
        Color col = ColorUtil.lightenColor(props.color, (top ? 1 : -1) * colFactor);
        wrapper.setColor(Convert.toRGBA(col));
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props)));
        wrapper.drawLine(x1,y,x2,y);
	      break;
      }
	    case Constants.EN_DOTTED:
	    case Constants.EN_DASHED:
	    case Constants.EN_SOLID:
	    default:
	      wrapper.setColor(Convert.toRGBA(props.color));
	      wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props)));
	      wrapper.drawLine(x1,y,x2,y);
	      break;
	  }
	}
	
	//Draw a line from y1 to y2 (inclusive), with the middle-line as x.
	//This means the outer box of the line is (x-w/2,y1)-(x+w/2,y2)
	private void drawVLine(float x,float y1,float y2,boolean left,BorderProps props) {
    switch(props.style) {
      case Constants.EN_HIDDEN:
      case Constants.EN_NONE:
        break;
      case Constants.EN_DOUBLE: {
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props,1f/3f)));
        float h=props.width/1000f;
        float xleft=x-h/2;
        float x1=xleft+h/6;
        float x2=xleft+5*h/6;
        wrapper.drawLine(x1,y1,x1,y2);
        wrapper.drawLine(x2,y1,x2,y2);
        break;
      }
      case Constants.EN_GROOVE:
      case Constants.EN_RIDGE: {
        float colFactor = (props.style == EN_GROOVE ? 0.4f : -0.4f);
        Color uppercol = ColorUtil.lightenColor(props.color, -colFactor);
        Color lowercol = ColorUtil.lightenColor(props.color, colFactor);
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props,1f/3f)));
        float h=props.width/1000f;
        float xleft=x-h/2;
        float x1=xleft+h/6;
        float x2=xleft+5*h/6;
        wrapper.setColor(Convert.toRGBA(uppercol));
        wrapper.drawLine(x1,y1,x1,y2);
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.drawLine(x,y1,x,y2);
        wrapper.setColor(Convert.toRGBA(lowercol));
        wrapper.drawLine(x2,y1,x2,y2);
        break;
      }
      case Constants.EN_INSET:
      case Constants.EN_OUTSET: {
        float colFactor = (props.style == EN_OUTSET ? 0.4f : -0.4f);
        Color col = ColorUtil.lightenColor(props.color, (left ? 1 : -1) * colFactor);
        wrapper.setColor(Convert.toRGBA(col));
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props)));
        wrapper.drawLine(x,y1,x,y2);
        break;
      }
      case Constants.EN_DOTTED:
      case Constants.EN_DASHED:
      case Constants.EN_SOLID:
      default:
        wrapper.setColor(Convert.toRGBA(props.color));
        wrapper.setLineAttributes(Convert.toLineAttributes(getStroke(props)));
        wrapper.drawLine(x,y1,x,y2);
        break;
    }
	}
	
	private static Color BLACK=new Color(0,0,0);
	
  @Override
  protected void drawBorders(Rectangle2D.Float borderRect,BorderProps bpsTop,BorderProps bpsBottom,BorderProps bpsLeft,BorderProps bpsRight,Color innerBackgroundColor)
  {
    if(bpsTop==null) bpsTop=new BorderProps(Constants.EN_NONE,0,0,0,BLACK,Mode.SEPARATE);
    if(bpsBottom==null) bpsBottom=new BorderProps(Constants.EN_NONE,0,0,0,BLACK,Mode.SEPARATE);
    if(bpsLeft==null) bpsLeft=new BorderProps(Constants.EN_NONE,0,0,0,BLACK,Mode.SEPARATE);
    if(bpsRight==null) bpsRight=new BorderProps(Constants.EN_NONE,0,0,0,BLACK,Mode.SEPARATE);
    
    // If border is separate, clip will be 0.
    // If border is part of a group of borders, then it will be set to half the line width.
    float clipLeft=BorderProps.getClippedWidth(bpsLeft)/1000f;
    float clipRight=BorderProps.getClippedWidth(bpsRight)/1000f;
    float clipTop=BorderProps.getClippedWidth(bpsTop)/1000f;
    float clipBottom=BorderProps.getClippedWidth(bpsBottom)/1000f;

    // This rectangle represents the "chord" of the border. That is, it is the rectangle that
    // is the most "inside", mid-way from outer and inner limits of the border.
    Rect middle=new Rect(
        borderRect.x+clipLeft,
        borderRect.y+clipTop,
        borderRect.width-clipLeft-clipRight,
        borderRect.height-clipTop-clipBottom
        );
    
    // By knowing the width of the borders, we'll be able to compute the inner and outer
    // limits from the middle rectangle we computed above.
    float widthLeft=bpsLeft.width/1000f;
    float widthRight=bpsRight.width/1000f;
    float widthTop=bpsTop.width/1000f;
    float widthBottom=bpsBottom.width/1000f;
    
    // If we are inner cells, we want to slightly widen the area to have our borders collapse
    // with the side ones.
    if(!bpsLeft.isCollapseOuter()) {
      middle.x-=clipLeft-widthLeft/2;
      middle.w+=clipLeft-widthLeft/2;
    }
    
    if(!bpsRight.isCollapseOuter()) {
      middle.w+=clipRight-widthRight/2;
    }
    
    if(!bpsTop.isCollapseOuter()) {
      middle.y-=clipTop-widthTop/2;
      middle.h+=clipTop-widthTop/2;
    }
    
    if(!bpsBottom.isCollapseOuter()) {
      middle.h+=clipBottom-widthBottom/2;
    }
    
    saveGraphicsState();
    state.configureGC(wrapper);

    if(widthTop==widthBottom && widthBottom==widthLeft && widthLeft==widthRight && widthRight==widthTop &&
        bpsTop.style==bpsBottom.style && bpsBottom.style==bpsLeft.style && bpsLeft.style==bpsRight.style && bpsRight.style==bpsTop.style &&
        bpsTop.color.equals(bpsBottom.color) && bpsBottom.color.equals(bpsLeft.color) && bpsLeft.color.equals(bpsRight.color) && bpsRight.color.equals(bpsTop.color)) {
      drawRectangle(middle.x,middle.y,middle.w,middle.h,bpsTop);
    } else {
      Rect outer=new Rect(middle.x-widthLeft/2,middle.y-widthTop/2,middle.w+widthLeft/2+widthRight/2,middle.h+widthTop/2+widthBottom/2);
      
      //Top
      if(bpsTop.style!=Constants.EN_NONE) {
        float left=outer.x;
        float right=outer.x+outer.w;
        
        if(outer(bpsTop)) {
          //For corners, we don't support the diagonal cut, we let horizontal lines "win" over vertical.
          //So we keep top and bottom at full length, and we reduce left and right.
        } else {
          if(outer(bpsLeft)) left+=widthLeft;
          if(outer(bpsRight)) right-=widthRight;
        }
        drawHLine(left,right,middle.y,true,bpsTop);
      }

      //Bottom
      if(bpsBottom.style!=Constants.EN_NONE) {
        float left=outer.x;
        float right=outer.x+outer.w;
        if(outer(bpsBottom)) {
          //For corners, we don't support the diagonal cut, we let horizontal lines "win" over vertical.
          //So we keep top and bottom at full length, and we reduce left and right.
        } else {
          if(outer(bpsLeft)) left+=widthLeft;
          if(outer(bpsRight)) right-=widthRight;
        }
        drawHLine(left,right,middle.y+middle.h,false,bpsBottom);
      }

      //Left
      if(bpsLeft.style!=Constants.EN_NONE) {
        float top=outer.y;
        float bottom=outer.y+outer.h;
        if(outer(bpsLeft)) {
          //For corners, we don't support the diagonal cut, we let horizontal lines "win" over vertical.
          //So we keep top and bottom at full length, and we reduce left and right.
          if(outer(bpsTop)) top+=widthTop;
          if(outer(bpsBottom)) bottom-=widthBottom;
        } else {
          if(outer(bpsTop)) top+=widthTop;
          if(outer(bpsBottom)) bottom-=widthBottom;
        }
        drawVLine(middle.x,top,bottom,true,bpsLeft);
      }

      //Right
      if(bpsRight.style!=Constants.EN_NONE) {
        float top=outer.y;
        float bottom=outer.y+outer.h;
        if(outer(bpsRight)) {
          //For corners, we don't support the diagonal cut, we let horizontal lines "win" over vertical.
          //So we keep top and bottom at full length, and we reduce left and right.
          if(outer(bpsTop)) top+=widthTop;
          if(outer(bpsBottom)) bottom-=widthBottom;
        } else
        {
          if(outer(bpsTop)) top+=widthTop;
          if(outer(bpsBottom)) bottom-=widthBottom;
        }
        drawVLine(middle.x+middle.w,top,bottom,false,bpsRight);
      }
    }
    restoreGraphicsState();
  }

	@Override
  protected void renderTextDecoration(FontMetrics fm,int fontsize,InlineArea inline,int baseline,int startx)
  {
    boolean hasTextDeco=inline.hasUnderline()||inline.hasOverline()||inline.hasLineThrough();
    if(hasTextDeco)
    {
      endTextObject();
      float descender=fm.getDescender(fontsize)/1000f;
      float capHeight=fm.getCapHeight(fontsize)/1000f;
      float endx=startx+inline.getIPD();
      if(inline.hasUnderline())
      {
        Color ct=(Color)inline.getTrait(Trait.UNDERLINE_COLOR);
        BorderProps props=new BorderProps(Constants.EN_SOLID,(int)(fm.getDescender(fontsize)/-8000f),0,0,ct,Mode.SEPARATE);
        float y=baseline-descender;
        drawHLine(startx/1000f,endx/1000f,y/1000f,true,props);
      }
      if(inline.hasOverline())
      {
        Color ct=(Color)inline.getTrait(Trait.OVERLINE_COLOR);
        BorderProps props=new BorderProps(Constants.EN_SOLID,(int)(fm.getDescender(fontsize)/-8000f),0,0,ct,Mode.SEPARATE);
        float y=(float)(baseline-(1.2*capHeight));
        drawHLine(startx/1000f,endx/1000f,y/1000f,true,props);
      }
      if(inline.hasLineThrough())
      {
        Color ct=(Color)inline.getTrait(Trait.LINETHROUGH_COLOR);
        BorderProps props=new BorderProps(Constants.EN_SOLID,(int)(fm.getDescender(fontsize)/-8000f),0,0,ct,Mode.SEPARATE);
        float y=(float)(baseline-(0.45*capHeight));
        drawHLine(startx/1000f,endx/1000f,y/1000f,true,props);
      }
    }
  }
  
	@Override
	public void renderText(TextArea text) {
		renderInlineAreaBackAndBorders(text);

		int rx = currentIPPosition + text.getBorderAndPaddingWidthStart();
		int bl = currentBPPosition + text.getBaselineOffset();
		int saveIP = currentIPPosition;

		Font font = getFontFromArea(text);
		Typeface tf = fontInfo.getFonts().get(font.getFontName());
		
		state.updateFont(tf.getFontName(), font.getFontSize());
		Color col = (Color) text.getTrait(Trait.COLOR);
		state.configureGC(wrapper);
		wrapper.setColor(Convert.toRGBA(col));

		renderText(text, font, rx / 1000f, bl / 1000f);

		currentIPPosition = saveIP + text.getAllocIPD();

		int fontsize = text.getTraitAsInteger(Trait.FONT_SIZE);
		renderTextDecoration(tf, fontsize, text, bl, rx);
	}

	private void renderText(TextArea text, Font font, float x, float y) {
		float textCursor = x;

		Iterator iter = text.getChildAreas().iterator();
		while (iter.hasNext()) {
			InlineArea child = (InlineArea) iter.next();
			if (child instanceof WordArea) {
				WordArea word = (WordArea) child;
				String s = word.getWord();
				wrapper.drawString(s, textCursor,
				        y + 1.0f - font.getAscender() / 1000.0f + font.getDescender() / 1000.0f, true);
				textCursor += wrapper.stringExtentWidth(s);
			} else if (child instanceof SpaceArea) {
				SpaceArea space = (SpaceArea) child;
				String s = space.getSpace();
				char sp = s.charAt(0);
				int tws = (space.isAdjustable() ? text.getTextWordSpaceAdjust() + 2 * text.getTextLetterSpaceAdjust()
				        : 0);

				textCursor += (font.getCharWidth(sp) + tws) / 1000f;
			} else {
				throw new IllegalStateException("Unsupported child element: " + child); //$NON-NLS-1$
			}
		}
	}

	@Override
	public void renderLeader(Leader area) {
		renderInlineAreaBackAndBorders(area);

		float startx = (currentIPPosition + area.getBorderAndPaddingWidthStart()) / 1000f;
		float starty = ((currentBPPosition + area.getBlockProgressionOffset()) / 1000f);
		float endx = (currentIPPosition + area.getBorderAndPaddingWidthStart() + area.getIPD()) / 1000f;

		Color col = (Color) area.getTrait(Trait.COLOR);
		state.updateColor(col);

		Line2D.Float line = new Line2D.Float();
		line.setLine(startx, starty, endx, starty);
		float ruleThickness = area.getRuleThickness() / 1000f;
		
		BorderProps props;
		switch(area.getRuleStyle()) {
		  case EN_RIDGE:
		  case EN_GROOVE:
		    props=new BorderProps(area.getRuleStyle(),area.getRuleThickness()/2,0,0,col,Mode.SEPARATE);
        drawRectangle(startx+ruleThickness/2,starty+3*ruleThickness/4,endx-startx-ruleThickness,ruleThickness/4,props);
        fillRect(startx+ruleThickness/2,starty+3*ruleThickness/4,endx-startx-ruleThickness,ruleThickness/4);
		    break;
		  default:
		    props=new BorderProps(area.getRuleStyle(),area.getRuleThickness(),0,0,col,Mode.SEPARATE);
		    drawHLine(startx,endx,starty+ruleThickness,true,props);
		    break;
		}
    super.renderLeader(area);
	}

	private void drawRenderedImage(RenderedImage image, AffineTransform at) {
		saveGraphicsState();
		state.combineTransform(at);
		state.configureGC(wrapper);

		ColorModel color = image.getColorModel();

		Raster raster = image.getData();

		ImageData imageData = new ImageData(raster.getWidth(), raster.getHeight(), 32,
		        new PaletteData(0xFF0000, 0xFF00, 0xFF));

		Object data = null;
		for (int i = 0; i < raster.getWidth(); i++) {
			for (int j = 0; j < raster.getHeight(); j++) {
				data = raster.getDataElements(i, j, data);
				int rgb = color.getRGB(data);
				imageData.setPixel(i, j, rgb & 0xFFFFFF);
				imageData.setAlpha(i, j, (rgb >> 24) & 0xFF);
			}
		}
		org.eclipse.swt.graphics.Image swtImage = new org.eclipse.swt.graphics.Image(wrapper.getDevice(), imageData);
		try {
			wrapper.drawImage(swtImage, 0, 0);
		} finally {
			swtImage.dispose();
		}
		restoreGraphicsState();
	}

	@Override
	public void renderImage(Image image, Rectangle2D pos) {
		String url = image.getURL();
		drawImage(url, pos);
	}

	private static final ImageFlavor[] FLAVOURS = new ImageFlavor[] { ImageFlavor.BUFFERED_IMAGE,
	        ImageFlavor.RENDERED_IMAGE, ImageFlavor.XML_DOM };

	@Override
	protected void drawImage(String uri, Rectangle2D pos, Map foreignAttributes) {

		int x = currentIPPosition + (int) Math.round(pos.getX());
		int y = currentBPPosition + (int) Math.round(pos.getY());
		uri = URISpecification.getURL(uri);

		ImageManager manager = getUserAgent().getImageManager();
		ImageInfo info = null;

		try {
			ImageSessionContext sessionContext = getUserAgent().getImageSessionContext();
			info = manager.getImageInfo(uri, sessionContext);
			Map hints = ImageUtil.getDefaultHints(sessionContext);
			org.apache.xmlgraphics.image.loader.Image img = manager.getImage(info, FLAVOURS, hints, sessionContext);

			if (img instanceof ImageGraphics2D) {
				ImageGraphics2D imageG2D = (ImageGraphics2D) img;
				int width = (int) pos.getWidth();
				int height = (int) pos.getHeight();
				RendererContext context = createRendererContext(x, y, width, height, foreignAttributes);
				getGraphics2DAdapter().paintImage(imageG2D.getGraphics2DImagePainter(), context, x, y, width, height);
			} else if (img instanceof ImageRendered) {
				ImageRendered imgRend = (ImageRendered) img;
				AffineTransform at = new AffineTransform();
				at.translate(x / 1000f, y / 1000f);
				double sx = pos.getWidth() / info.getSize().getWidthMpt();
				double sy = pos.getHeight() / info.getSize().getHeightMpt();
				sx *= userAgent.getSourceResolution() / info.getSize().getDpiHorizontal();
				sy *= userAgent.getSourceResolution() / info.getSize().getDpiVertical();
				at.scale(sx, sy);
				drawRenderedImage(imgRend.getRenderedImage(), at);
			} else if (img instanceof ImageXMLDOM) {
				ImageXMLDOM imgXML = (ImageXMLDOM) img;
				renderDocument(imgXML.getDocument(), imgXML.getRootNamespace(), pos, foreignAttributes);
			}
		} catch (ImageException ie) {
			ResourceEventProducer eventProducer = ResourceEventProducer.Provider
			        .get(getUserAgent().getEventBroadcaster());
			eventProducer.imageError(this, (info != null ? info.toString() : uri), ie, null);
		} catch (FileNotFoundException fe) {
			ResourceEventProducer eventProducer = ResourceEventProducer.Provider
			        .get(getUserAgent().getEventBroadcaster());
			eventProducer.imageNotFound(this, (info != null ? info.toString() : uri), fe, null);
		} catch (IOException ioe) {
			ResourceEventProducer eventProducer = ResourceEventProducer.Provider
			        .get(getUserAgent().getEventBroadcaster());
			eventProducer.imageIOError(this, (info != null ? info.toString() : uri), ioe, null);
		}
	}

	@Override
	protected RendererContext createRendererContext(int x, int y, int width, int height, Map foreignAttributes) {
		RendererContext context = super.createRendererContext(x, y, width, height, foreignAttributes);
		context.setProperty("swtState", state); //$NON-NLS-1$
		return context;
	}

	@Override
	protected void beginTextObject() {
	}

	@Override
	protected void endTextObject() {
	}

	@Override
	public String getMimeType() {
		return "SWT"; //$NON-NLS-1$
	}
}
