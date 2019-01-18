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
import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.ImageObserver;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JSlider;

/**
 * GeometryTest.
 */
@SuppressWarnings({ "serial", "nls", "unused" })
public class GeometryTest {
    private static void offset() {
        final JFrame frame = new JFrame();
        frame.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                frame.dispose();
            }
        });

        final JSlider slider = new JSlider(-100, 500);

        Canvas canvas = new Canvas() {
            @Override
            public void update(Graphics g) {
                paint(g);
            }

            @Override
            public void paint(Graphics graphics) {
                Image img = createImage(getSize().width, getSize().height);
                Graphics2D g = (Graphics2D) img.getGraphics();

                g.setColor(new Color(255, 255, 255));
                Rectangle r = getBounds();
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Font font = new Font("Times", Font.PLAIN, 140); //$NON-NLS-1$
                Map map = font.getAttributes();
                map.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
                map.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
                font = font.deriveFont(map);
                g.setFont(font);

                //String str="सभी मनुष्यों को गौरव और";
                //String str="تَعْليق";
                //String str="พยัญชนะรูปสระ";
                //String str="在尊严和权利上一";
                String str = "WAWAç jpg xylop";
                //String str="龘齉爨馕";
                //String str="👱 👨‍❤💋‍👧‍👦";
                g.setColor(new Color(0, 0, 0));
                //g.drawString(str, 10f, 70f+font.getSize2D());

                FontRenderContext context = new FontRenderContext(null, true, true);
                TextLayout layout = new TextLayout(str, font, context);
                AffineTransform tr = new AffineTransform();
                tr.translate(10f, 70f + font.getSize2D());
                Shape shape = layout.getOutline(tr);

                /*
                Path2D.Float shape=new Path2D.Float();
                
                shape.moveTo(100,100);
                shape.lineTo(290,490);
                shape.lineTo(400,500);
                shape.lineTo(100,500);
                shape.closePath();
                */

                g.setColor(new Color(0, 0, 0));
                g.fill(shape);

                Rectangle2D.Float underline = new Rectangle2D.Float(10f,
                        70f + font.getSize2D() + g.getFontMetrics().getDescent() - font.getSize2D() / 10f,
                        g.getFontMetrics().stringWidth(str), font.getSize2D() / 10f);
                Area ua = new Area(underline);

                float extend = font.getSize2D() / 30f * slider.getValue() / 25f;
                long before = System.nanoTime();
                Area ta = new Area(Geometry.offsetShape(shape, extend, BasicStroke.JOIN_MITER));
                long after = System.nanoTime();
                System.out.println((after - before) / 1_000_000f);

                g.setColor(new Color(0, 255, 0));
                //g.fill(ta);

                /*
                BasicStroke st=new BasicStroke(extend,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f);
                g.setColor(new Color(0,255,0,128));
                g.setStroke(st);
                g.draw(shape);
                g.setStroke(new  BasicStroke());
                */

                g.setColor(new Color(255, 0, 255));
                g.draw(ta);

                ua.subtract(ta);
                g.setColor(new Color(0, 0, 0));
                g.fill(ua);

                graphics.drawImage(img, 0, 0, new ImageObserver() {
                    @Override
                    public boolean imageUpdate(Image i, int infoflags, int x, int y, int width, int height) {
                        return false;
                    }
                });
                img.flush();
            }
        };
        frame.add(canvas, BorderLayout.CENTER);
        slider.setValue(100);
        frame.add(slider, BorderLayout.SOUTH);
        slider.addChangeListener(e -> canvas.repaint());

        frame.setSize(1024, 768);

        frame.setVisible(true);
    }

    private static void intersect() {
        final JFrame frame = new JFrame();
        frame.setLayout(new BorderLayout());
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent we) {
                frame.dispose();
            }
        });

        Canvas canvas = new Canvas() {

            @Override
            public void update(Graphics graphics) {
                paint(graphics);
            }

            @Override
            public void paint(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                g.setColor(new Color(255, 255, 255));
                g.fillRect(0, 0, getSize().width, getSize().height);
                g.setColor(new Color(0, 0, 0));
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                String str = "jlçrjpgaj";
                //String str="सभी मनुष्यों को गौरव और";
                //String str="تَعْليق";
                //String str="พยัญชนะรูปสระ";
                //String str="在尊严和权利上一";
                //String str="龘齉爨馕";
                //String str="👱 👨‍❤💋‍👧‍👦";
                Font font = new Font("Times", Font.PLAIN, 120);
                FontRenderContext ctx = new FontRenderContext(null, true, true);
                TextLayout layout = new TextLayout(str, font, ctx);
                AffineTransform tr = new AffineTransform();
                tr.translate(50, font.getSize());
                Shape textShape = layout.getOutline(tr);
                g.fill(textShape);

                Rectangle2D underline = new Rectangle2D.Double(textShape.getBounds().getX(),
                        textShape.getBounds().getY() + font.getSize() * 5 / 6, textShape.getBounds().getWidth(),
                        font.getSize() / 12);

                List<Shape> split = Geometry.getUnderlineShapes(textShape, underline, Geometry.UnderlineMethod.OFFSET_MASK);

                g.setColor(new Color(0, 0, 255));
                split.forEach(s -> g.fill(s));
            }

        };
        frame.add(canvas, BorderLayout.CENTER);
        frame.setSize(640, 400);

        frame.setVisible(true);

    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        intersect();
    }
}
