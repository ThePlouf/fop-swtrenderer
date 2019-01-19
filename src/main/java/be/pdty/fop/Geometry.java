/*
 * Copyright 2019 Philippe Detournay
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package be.pdty.fop;

import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Geometry utilities.
 */
public class Geometry {
    private static final double ITERATOR_FLATNESS = 1.0;
    private static final double MITER_LIMIT = 4.0;
    private static final double ROUND_POINT_DISTANCE = 10.0;
    private static final double ROUND_MAX_INCREMENT = 0.5;

    //We assume that if a point is already defined in the target path (that is, first is false), then it is somewhere on
    //this segment's extended path. In other words, we do *not* have to first position the curve at the beginning of the
    //segment.
    //If this is the very first segment to be added to the path (first is true), then we also assume that the last point
    //that will be added on the curse will be on this segment's extended path. So and similarly to the previous point,
    //we do not have to position the curve at the beginning of the segment and we can effectively "moveTo" directly
    //to the end of the segment.
    private static void appendSegment(double c1x, double c1y, double c2x, double c2y, double nx, double ny,
            double offset, int joinType, boolean first, Path2D.Double target) {
        //Current segment
        double cdx = c2x - c1x;
        double cdy = c2y - c1y;
        double clength = Math.sqrt(cdx * cdx + cdy * cdy);
        cdx = cdx / clength;
        cdy = cdy / clength;
        double lc2x = c2x + cdy * offset;
        double lc2y = c2y - cdx * offset;

        //Next segment
        double ndx = nx - c2x;
        double ndy = ny - c2y;
        double nlength = Math.sqrt(ndx * ndx + ndy * ndy);
        ndx = ndx / nlength;
        ndy = ndy / nlength;
        double ln1x = c2x + ndy * offset;
        double ln1y = c2y - ndx * offset;

        //Miter junction point
        double mdx = cdy + ndy;
        double mdy = -(cdx + ndx);
        double mLength = Math.sqrt(mdx * mdx + mdy * mdy);
        if (mLength > 0) {
            mdx /= mLength;
            mdy /= mLength;
        }

        //This gives us the cosine of the half-angle. Note that it can be zero!
        double cos = cdy * mdx - cdx * mdy;

        if (cdy * ndx - cdx * ndy >= 0) {
            //A concave point. Draw via the original (non-extended) point as
            //per http://www.me.berkeley.edu/~mcmains/pubs/DAC05OffsetPolygon.pdf
            if (first) {
                target.moveTo(lc2x, lc2y);
            } else {
                target.lineTo(lc2x, lc2y);
            }
            target.lineTo(c2x, c2y);
            target.lineTo(ln1x, ln1y);
        } else {
            //A convex point
            switch (joinType) {
            case BasicStroke.JOIN_BEVEL:
                //Simple bevel, we'll go to our end first, then to the beginning of next line.
                if (first) {
                    target.moveTo(lc2x, lc2y);
                } else {
                    target.lineTo(lc2x, lc2y);
                }
                target.lineTo(ln1x, ln1y);
                break;
            case BasicStroke.JOIN_MITER:
            default:
                //Miter.
                double lim = MITER_LIMIT;
                //The length of the miter is extent/cos, and we allow up to lim*extent.
                //Therefore if extent/cos>lim*extent (or cos*lim<1), we'll revert to bevel.
                if (cos * lim < 1.0) {
                    //Bevel
                    if (first) {
                        target.moveTo(lc2x, lc2y);
                    } else {
                        target.lineTo(lc2x, lc2y);
                    }
                    target.lineTo(ln1x, ln1y);
                } else {
                    //Actual miter.
                    double mitX = c2x + mdx * offset / cos;
                    double mitY = c2y + mdy * offset / cos;
                    if (first) {
                        target.moveTo(mitX, mitY);
                    } else {
                        target.lineTo(mitX, mitY);
                    }
                }
                break;
            case BasicStroke.JOIN_ROUND:
                //Let's go to the beginning of the joint first.
                if (first) {
                    target.moveTo(lc2x, lc2y);
                } else {
                    target.lineTo(lc2x, lc2y);
                }

                //Draw the circle. Note that we could get a very good approximation of a circle by
                //using cubic curves, but there is some value in having a pure polygonal answer.
                if (offset > 0) {
                    //Starting angle
                    double angle = Math.atan2(-cdx, cdy);
                    //Length of arc
                    double arc = Math.acos(cos) * 2;
                    //Arc increment
                    double increment = ROUND_POINT_DISTANCE / offset;
                    if (increment > ROUND_MAX_INCREMENT)
                        increment = ROUND_MAX_INCREMENT;
                    //Ending angle
                    double angleEnd = angle + arc - increment;

                    //Let's try to ensure the partial increment
                    //due to rounding error is evenly spread on two ends
                    double off = arc - ((int) (arc / increment + 0.5)) * increment;
                    angle += off / 2;

                    while (angle < angleEnd) {
                        angle += increment;
                        double x = Math.cos(angle) * offset + c2x;
                        double y = Math.sin(angle) * offset + c2y;
                        target.lineTo(x, y);
                    }
                    target.lineTo(ln1x, ln1y);
                }
                break;
            }

        }
    }

    //See https://en.wikipedia.org/wiki/Shoelace_formula
    private static double area(List<Line2D.Double> lines) {
        double ans = 0;
        for (Line2D.Double line : lines) {
            ans += (line.x1 * line.y2) - (line.x2 * line.y1);
        }
        return ans;
    }

    private static Area renderSimpleShape(List<Line2D.Double> lines, double offset, int joinType) {
        Path2D.Double ans = new Path2D.Double(Path2D.WIND_NON_ZERO);

        for (int i = 0; i < lines.size(); i++) {
            int nextIndex = i + 1;
            if (nextIndex == lines.size())
                nextIndex = 0;

            Line2D.Double current = lines.get(i);
            Line2D.Double next = lines.get(nextIndex);

            appendSegment(current.x1, current.y1, current.x2, current.y2, next.x2, next.y2, offset, joinType, i == 0,
                    ans);
        }

        //Creating an area with non-zero winding will perform a proper cleanup of the overlapping areas
        //generated by the concave points.
        return new Area(ans);

    }

    /**
     * Return an area that is the offset of the given input (closed) shape. Note
     * that only clockwise (on the screen coordinates) sub-shapes will be
     * considered. That is, any "hole" in the shape will be removed, and only
     * the outer outline will be considered. Note that this implementation is
     * simpler than the Clipper library
     * (http://www.angusj.com/delphi/clipper.php) because we take advantage of
     * the AWT Area features instead of implementing winding ourselves. But the
     * approach is very similar. Also note that we will flatten the input shape.
     * That is, the output will be a polygon area even if there are curves
     * defined in the input shape.
     * 
     * @param shape input shape.
     * @param offset offset (must be greater than 0).
     * @param joinType joinType, use constants from BasicStroke.
     * @return offset area.
     */
    public static Area offsetShape(Shape shape, double offset, int joinType) {
        PathIterator pi = shape.getPathIterator(null, ITERATOR_FLATNESS);
        double[] data = new double[2];

        //As a first step, we will build a collection of simple shapes (that contain only one starting point
        //and one looping point), also eliminating empty segments by the way.
        double firstX = 0;
        double firstY = 0;
        double previousX = 0;
        double previousY = 0;
        List<List<Line2D.Double>> simpleShapes = new ArrayList<>();
        List<Line2D.Double> current = new ArrayList<>();

        while (!pi.isDone()) {
            int type = pi.currentSegment(data);
            double targetX = 0;
            double targetY = 0;
            switch (type) {
            case PathIterator.SEG_MOVETO:
                if (current.size() > 0) {
                    simpleShapes.add(current);
                    current = new ArrayList<>();
                }
                firstX = data[0];
                firstY = data[1];
                targetX = firstX;
                targetY = firstY;
                break;
            case PathIterator.SEG_LINETO:
                targetX = data[0];
                targetY = data[1];
                if (previousX != targetX || previousY != targetY) {
                    current.add(new Line2D.Double(previousX, previousY, targetX, targetY));
                }
                break;
            case PathIterator.SEG_CLOSE:
                targetX = firstX;
                targetY = firstY;
                if (previousX != targetX || previousY != targetY) {
                    current.add(new Line2D.Double(previousX, previousY, targetX, targetY));
                }
                if (current.size() > 0) {
                    simpleShapes.add(current);
                    current = new ArrayList<>();
                }
                break;
            default:
                break;
            }

            previousX = targetX;
            previousY = targetY;

            pi.next();
        }

        //The real work starts here.
        Area finalArea = new Area();
        for (int s = 0; s < simpleShapes.size(); s++) {
            //Only consider positive area shapes (i.e. ignore holes).
            if (area(simpleShapes.get(s)) > 0) {
                finalArea.add(renderSimpleShape(simpleShapes.get(s), offset, joinType));
            }
        }

        return finalArea;
    }

    private static List<Shape> getUnderlineShapesOffsetMask(Shape textOutline, Rectangle2D underline) {
        double extend = underline.getHeight();
        Area ta = new Area(offsetShape(textOutline, extend, BasicStroke.JOIN_MITER));
        Area ua = new Area(underline);
        ua.subtract(ta);
        return Collections.singletonList(ua);
    }

    //Sort and merge a list of intervals.
    private static List<double[]> sortAndMergeIntervals(List<double[]> l) {
        //First step, we build a tree to sort the events.
        //A "start" event is +1, a "stop" event is -1.
        //We combine by addition if there are events at same position.
        TreeMap<Double, Integer> events = new TreeMap<>();
        for (double[] p : l) {
            double start = p[0];
            double length = p[1];
            events.put(start, events.getOrDefault(start, 0) + 1);
            events.put(start + length, events.getOrDefault(start + length, 0) - 1);
        }

        //And now we iterate through the map to build the final list of intervals.
        List<double[]> ans = new ArrayList<>();
        int w = 0;
        double start = Double.MIN_VALUE;
        for (Map.Entry<Double, Integer> entry : events.entrySet()) {
            int prev = w;
            w += entry.getValue();
            if (prev == 0 && w == 1) {
                //Begin interval.
                start = entry.getKey();
            } else if (prev == 1 && w == 0) {
                //End interval.
                ans.add(new double[] { start, entry.getKey() - start });
            }
        }
        // assert (w == 0);
        return ans;
    }

    //Negate a list of intervals, by subtracting it from the given larger interval.
    private static List<double[]> negate(List<double[]> l, double from, double length) {
        double prev = from;
        List<double[]> ans = new ArrayList<>();
        for (double[] d : l) {
            ans.add(new double[] { prev, d[0] - prev });
            prev = d[0] + d[1];
        }
        ans.add(new double[] { prev, from + length - prev });
        return ans;
    }

    private static List<Shape> getUnderlineShapesLargestGap(Shape textOutline, Rectangle2D underline) {
        //First we compute the intersection of the text outline and the underline.
        Area intersect = new Area(textOutline);
        intersect.intersect(new Area(underline));

        //This intersection area is a set of mostly disjoint segments. For each shape we
        //will compute the maximum horizontal extent.
        List<double[]> segments = new ArrayList<>();

        //We try to be as accurate as possible, so we'll compute the proper extent
        //even for quadratic and cubic curves.
        PathIterator pi = intersect.getPathIterator(null);
        double[] p = new double[6];
        double left = Double.MAX_VALUE;
        double right = Double.MIN_VALUE;
        double firstX = 0;
        double prevX = 0;
        while (!pi.isDone()) {
            int type = pi.currentSegment(p);
            switch (type) {
            case PathIterator.SEG_MOVETO:
                firstX = p[0];
                left = Math.min(left, p[0]);
                right = Math.max(right, p[0]);
                prevX = p[0];
                break;
            case PathIterator.SEG_LINETO:
                left = Math.min(left, p[0]);
                right = Math.max(right, p[0]);
                prevX = p[0];
                break;
            case PathIterator.SEG_QUADTO:
                left = Math.min(left, p[2]);
                right = Math.max(right, p[2]);
                double t = (p[0] - prevX) / (2 * p[0] - prevX - p[2]);
                if (t > 0 && t < 1) {
                    double x = (1 - t) * (1 - t) * prevX + 2 * (1 - t) * t * p[0] + t * t * p[2];
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
                prevX = p[2];
                break;
            case PathIterator.SEG_CUBICTO:
                left = Math.min(left, p[4]);
                right = Math.max(right, p[4]);
                double a = -prevX + 3 * p[0] - 3 * p[2] + p[4]; //-P0+3P1-3P2+P3
                double b = 2 * prevX - 4 * p[0] + 2 * p[2]; //2P0-4P1+2P2
                double c = p[0] - prevX; //P1-P0
                double ro = b * b - 4 * a * c;
                if (ro >= 0) {
                    double t1 = (-b - Math.sqrt(ro)) / (2 * a);
                    double t2 = (-b + Math.sqrt(ro)) / (2 * a);
                    if (t1 > 0 && t1 < 1) {
                        double x1 = (1 - t1) * (1 - t1) * (1 - t1) * prevX + 3 * t1 * (1 - t1) * (1 - t1) * p[0]
                                + 3 * t1 * t1 * (1 - t1) * p[2] + t1 * t1 * t1 * p[4];
                        left = Math.min(left, x1);
                        right = Math.max(right, x1);
                    }
                    if (t2 > 0 && t2 < 1) {
                        double x2 = (1 - t2) * (1 - t2) * (1 - t2) * prevX + 3 * t2 * (1 - t2) * (1 - t2) * p[0]
                                + 3 * t2 * t2 * (1 - t2) * p[2] + t2 * t2 * t2 * p[4];
                        left = Math.min(left, x2);
                        right = Math.max(right, x2);
                    }
                }
                prevX = p[4];
                break;
            case PathIterator.SEG_CLOSE:
                segments.add(new double[] { left, right - left });
                left = Double.MAX_VALUE;
                right = Double.MIN_VALUE;
                prevX = firstX;
                break;
            default:
                break;
            }
            pi.next();
        }

        //Merge and negate to have the list of allowable underline.
        segments = sortAndMergeIntervals(segments);
        segments = negate(segments, underline.getX(), underline.getWidth());

        //We will add left and right margins, except for first and last segments.
        double margin = underline.getHeight();
        for (int i = 0; i < segments.size(); i++) {
            double[] d = segments.get(i);
            if (i > 0) {
                d[0] += margin;
                d[1] -= margin;
            }
            if (i < segments.size() - 1)
                d[1] -= margin;
        }

        //Cleanup segments that are too small.
        segments.removeIf(d -> d[1] < margin);

        //Build the final list of shapes.
        List<Shape> ans = new ArrayList<>();
        for (double[] d : segments) {
            Shape seg = new Rectangle2D.Double(d[0], underline.getY(), d[1], underline.getHeight());
            ans.add(seg);
        }
        return ans;
    }

    /**
     * Method for computing underline shape for text.
     */
    public static enum UnderlineMethod {
        /**
         * The underline should be a straight line without any further
         * processing. This will basically just take the input base underline
         * and return it unchanged. This is the fastest method.
         */
        STRAIGHT,
        /**
         * The underline should be a collection of rectangles, forming a subset
         * of the base underline from which the largest width of the descending
         * parts should be excluded. An additional margin is added, equal to the
         * height of the base underline.
         */
        LARGEST_GAP,
        /**
         * The underline will be a collection of complex shapes that will
         * closely follow the shape of the descending parts with an offset equal
         * to the height of the base underline. This is the slowest method.
         */
        OFFSET_MASK
    }

    /**
     * Compute a set of shapes representing the actual underline of a given
     * text.
     * 
     * @param textOutline the text outline.
     * @param underline the base underline shape.
     * @param method method to be used.
     * @return set of shapes.
     */
    public static List<Shape> getUnderlineShapes(Shape textOutline, Rectangle2D underline, UnderlineMethod method) {
        switch (method) {
        case STRAIGHT:
        default:
            return Collections.singletonList(underline);
        case LARGEST_GAP:
            return getUnderlineShapesLargestGap(textOutline, underline);
        case OFFSET_MASK:
            return getUnderlineShapesOffsetMask(textOutline, underline);
        }
    }

}
