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

import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.PathData;
import org.eclipse.swt.graphics.RGBA;

import be.pdty.fop.Base14FontProvider.FontInfo;

public class TextOutput {
    private static class Request {
        public float x;
        public float baseline;
        public float length;
        public GCWrapper.Metrics fm;
        public Shape outline;

        public Request(float px, float bl, float l, GCWrapper.Metrics m, Shape o) {
            x = px;
            baseline = bl;
            length = l;
            fm = m;
            outline = o;
        }
    }

    private static class Metrics {
        public float left;
        public float right;
        public float over;
        public float under;
        public float strength;
        public Shape shape;
    }

    private GCWrapper gc;
    private boolean def;

    private RGBA underline;
    private List<Request> underlineRequests;
    private RGBA strike;
    private List<Request> strikeRequests;
    private RGBA overline;
    private List<Request> overlineRequests;

    public TextOutput(GCWrapper wrapper, boolean deferred) {
        gc = wrapper;
        def = deferred;
        underlineRequests = new ArrayList<>();
        strikeRequests = new ArrayList<>();
        overlineRequests = new ArrayList<>();
    }

    private Shape getStringOutlineAtBaseline(String s, float x, float baseline, String fontName, int fontSize) {
        FontInfo nfo = gc.getFontProvider().getFontInfo(fontName);
        int fontStyle = java.awt.Font.PLAIN;
        if ((nfo.style & SWT.ITALIC) != 0)
            fontStyle |= java.awt.Font.ITALIC;
        if ((nfo.style & SWT.BOLD) != 0)
            fontStyle |= java.awt.Font.BOLD;

        java.awt.Font awtFont = new java.awt.Font(nfo.name, fontStyle, (int) (fontSize / (1000.0f)));

        Map attributes = awtFont.getAttributes();
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        awtFont = awtFont.deriveFont(attributes);

        FontRenderContext context = new FontRenderContext(null, true, true);
        TextLayout layout = new TextLayout(s, awtFont, context);

        AffineTransform awtTransform = new AffineTransform();
        awtTransform.translate(x, baseline);
        java.awt.Shape shape = layout.getOutline(awtTransform);
        
        return shape;
    }
    
    private Metrics metrics(List<Request> requests) {
        Metrics ans = new Metrics();

        ans.left = Float.MAX_VALUE;
        ans.right = Float.MIN_VALUE;
        ans.over = Float.MAX_VALUE;
        ans.under = Float.MIN_VALUE;
        ans.strength = Float.MIN_VALUE;
        ans.shape = null;
        
        Path2D.Double shape = null;
        
        for (Request r : requests) {
            float left = r.x;
            float right = r.x + r.length;
            float ascent = r.baseline - r.fm.ascent;
            float descent = r.baseline + r.fm.descent;
            float strength = r.fm.height / 12.0f;
            descent -= strength/2;

            if (left < ans.left)
                ans.left = left;
            if (right > ans.right)
                ans.right = right;
            if (ascent < ans.over)
                ans.over = ascent;
            if (descent > ans.under)
                ans.under = descent;
            if (strength > ans.strength)
                ans.strength = strength;
            
            if(r.outline != null) {
                if(shape == null) shape = new Path2D.Double();
                shape.append(r.outline,false);
            }
            
        }

        ans.shape = shape;
        return ans;

    }

    private void line(float left, float right, float y, float s, RGBA color) {
        PathData path = new PathData();
    path.types=new byte[] {
        SWT.PATH_MOVE_TO,
        SWT.PATH_LINE_TO,
        SWT.PATH_LINE_TO,
        SWT.PATH_LINE_TO,
                SWT.PATH_CLOSE };
    path.points=new float[] {
        left,y-s/2,
        right,y-s/2,
        right,y+s/2,
        left,y+s/2};

        gc.setColor(color);
        if (def) {
            gc.fillPathDeferred(path);
        } else {
            gc.fillPath(path);
        }
    }

    private void closeUnderline() {
        if (underline == null || underlineRequests.size() == 0)
            return;

        Metrics m = metrics(underlineRequests);
        if(m.shape == null) {
            line(m.left, m.right, m.under, m.strength, underline);
        } else {
            Rectangle2D u = new Rectangle2D.Float(m.left, m.under-m.strength/2, m.right-m.left, m.strength);
            List<Shape> shape = Geometry.getUnderlineShapes(m.shape,u,Geometry.UnderlineMethod.OFFSET_MASK);
            gc.setColor(underline);
            for(Shape s:shape) {
                PathData data = Convert.toPathData(s);
                if(def) {
                    gc.fillPathDeferred(data);
                } else {
                    gc.fillPath(data);
                }
            }
        }

        underlineRequests.clear();
    }

    private void closeStrike() {
        if (strike == null || strikeRequests.size() == 0)
            return;

        Metrics m = metrics(strikeRequests);
        line(m.left, m.right, (m.under + m.over) / 2, m.strength, strike);

        strikeRequests.clear();
    }

    private void closeOverline() {
        if (overline == null || overlineRequests.size() == 0)
            return;

        Metrics m = metrics(overlineRequests);
        line(m.left, m.right, m.over, m.strength, overline);

        overlineRequests.clear();
    }

    private boolean different(RGBA a, RGBA b) {
        if (a == b)
            return false;
        if (a == null || b == null)
            return true;
        return !a.equals(b);
    }
  
    public void text(String s, float x, float baseline, String font, int fontSize, RGBA color, RGBA underlineColor,
            RGBA strikeColor, RGBA overlineColor) {
        if (different(underlineColor, underline))
            closeUnderline();
        if (different(strikeColor, strike))
            closeStrike();
        if (different(overlineColor, overline))
            closeOverline();

        underline = underlineColor;
        strike = strikeColor;
        overline = overlineColor;

        gc.setFont(font, fontSize);
        gc.setColor(color);
        GCWrapper.Metrics metrics = gc.getFontMetrics();
        gc.drawString(s, x, baseline - metrics.leading - metrics.ascent);

        if (underline != null || strike != null || overline != null) {
            Shape shape = null;
            if (underline != null) {
                shape = getStringOutlineAtBaseline(s, x, baseline, font, fontSize);
            }

            Request r = new Request(x, baseline, gc.stringExtentWidth(s), metrics, shape);

            if (underline != null)
                underlineRequests.add(r);
            if (strike != null)
                strikeRequests.add(r);
            if (overline != null)
                overlineRequests.add(r);
        }

    }

    public void endLine() {
        closeUnderline();
        closeStrike();
        closeOverline();
    }
}
