/*
 * Copyright 2018 Philippe Detournay
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
import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.PathData;
import org.eclipse.swt.graphics.RGBA;

/**
 * Utility class to convert AWT stuff into (headless) SWT stuff.
 */
public class Convert {
    /**
     * Convert an AWT color into an RGBA.
     * 
     * @param color AWT color.
     * @return SWT RGBA.
     */
    public static RGBA toRGBA(Color color) {
        if (color == null)
            return new RGBA(0, 0, 0, 0);
        return new RGBA(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    /**
     * Convert an AWT BasicStroke into a LineAttributes.
     * 
     * @param stroke AWT stroke.
     * @return SWT LineAttributes.
     */
    public static LineAttributes toLineAttributes(BasicStroke stroke) {
        if (stroke == null)
            return new LineAttributes(1);

        LineAttributes ans = new LineAttributes(stroke.getLineWidth());
        ans.dash = stroke.getDashArray();
        ans.dashOffset = stroke.getDashPhase();
        ans.miterLimit = stroke.getMiterLimit();
        ans.style = SWT.LINE_CUSTOM;
        switch (stroke.getEndCap()) {
        case BasicStroke.CAP_BUTT:
            ans.cap = SWT.CAP_FLAT;
            break;
        case BasicStroke.CAP_ROUND:
            ans.cap = SWT.CAP_ROUND;
            break;
        case BasicStroke.CAP_SQUARE:
            ans.cap = SWT.CAP_SQUARE;
            break;
        default:
            break;
        }
        switch (stroke.getLineJoin()) {
        case BasicStroke.JOIN_BEVEL:
            ans.join = SWT.JOIN_BEVEL;
            break;
        case BasicStroke.JOIN_MITER:
            ans.join = SWT.JOIN_MITER;
            break;
        case BasicStroke.JOIN_ROUND:
            ans.join = SWT.JOIN_ROUND;
            break;
        default:
            break;
        }
        return ans;
    }

    /**
     * Convert an AWT Shape into a PathData.
     * 
     * @param shape AWT shape.
     * @return SWT PathData.
     */
    public static PathData toPathData(Shape shape) {
        if (shape == null)
            return null;
        PathIterator it = shape.getPathIterator(null);

        List<Byte> typeList = new ArrayList<>();
        List<Float> pointList = new ArrayList<>();
        float[] tmp = new float[6];
        while (!it.isDone()) {
            int type = it.currentSegment(tmp);
            switch (type) {
            case PathIterator.SEG_MOVETO:
                typeList.add((byte) SWT.PATH_MOVE_TO);
                pointList.add(tmp[0]);
                pointList.add(tmp[1]);
                break;
            case PathIterator.SEG_LINETO:
                typeList.add((byte) SWT.PATH_LINE_TO);
                pointList.add(tmp[0]);
                pointList.add(tmp[1]);
                break;
            case PathIterator.SEG_QUADTO:
                typeList.add((byte) SWT.PATH_QUAD_TO);
                pointList.add(tmp[0]);
                pointList.add(tmp[1]);
                pointList.add(tmp[2]);
                pointList.add(tmp[3]);
                break;
            case PathIterator.SEG_CUBICTO:
                typeList.add((byte) SWT.PATH_CUBIC_TO);
                pointList.add(tmp[0]);
                pointList.add(tmp[1]);
                pointList.add(tmp[2]);
                pointList.add(tmp[3]);
                pointList.add(tmp[4]);
                pointList.add(tmp[5]);
                break;
            case PathIterator.SEG_CLOSE:
                typeList.add((byte) SWT.PATH_CLOSE);
                break;
            default:
                break;
            }
            it.next();
        }

        byte[] types = new byte[typeList.size()];
        for (int i = 0; i < types.length; i++)
            types[i] = typeList.get(i);

        float[] points = new float[pointList.size()];
        for (int i = 0; i < points.length; i++)
            points[i] = pointList.get(i);

        PathData ans = new PathData();
        ans.types = types;
        ans.points = points;
        return ans;
    }

    /**
     * Convert an AWT transform into an array of floats for SWT transform.
     * 
     * @param transform AWT transform.
     * @return array of floats for SWT transform.
     */
    public static float[] toFloatArray(AffineTransform transform) {
        double[] data = new double[6];
        transform.getMatrix(data);
        float[] swtData = new float[] { (float) data[0], (float) data[1], (float) data[2], (float) data[3],
                (float) data[4], (float) data[5] };
        return swtData;
    }
}
