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
import java.util.Map;

import javax.swing.JFrame;
import javax.swing.JSlider;

/**
 * GeometryTest.
 */
public class GeometryTest
{
  /**
   * @param args
   */
  @SuppressWarnings("serial")
  public static void main(String[] args)
  {
    final JFrame frame=new JFrame();
    frame.setLayout(new BorderLayout());
    frame.addWindowListener(new WindowAdapter()
    {
      @Override
      public void windowClosing(WindowEvent we)
      {
        frame.dispose();
      }
    });
    
    final JSlider slider=new JSlider(0,500);

    Canvas canvas=new Canvas()
    {
      @Override
      public void update(Graphics g)
      {
        paint(g);
      }
      
      @SuppressWarnings("nls")
      @Override
      public void paint(Graphics graphics)
      {
        Image img=createImage(getSize().width,getSize().height);
        Graphics2D g=(Graphics2D)img.getGraphics();
        
        g.setColor(new Color(255,255,255));
        Rectangle r=getBounds();
        g.fillRect(r.x,r.y,r.width,r.height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        
        Font font=new Font("Times",Font.PLAIN,140); //$NON-NLS-1$
        Map map=font.getAttributes();
        map.put(TextAttribute.KERNING,TextAttribute.KERNING_ON);
        map.put(TextAttribute.LIGATURES,TextAttribute.LIGATURES_ON);
        font=font.deriveFont(map);
        g.setFont(font);
        
        

        //String str="सभी मनुष्यों को गौरव और";
        //String str="تَعْليق";
        //String str="พยัญชนะรูปสระ";
        //String str="在尊严和权利上一";
        String str="WAWAç jpg xylop";
        //String str="龘齉爨馕";
        //String str="👱 👨‍❤💋‍👧‍👦";
        g.setColor(new Color(0,0,0));
        //g.drawString(str, 10f, 70f+font.getSize2D());

        
        FontRenderContext context=new FontRenderContext(null,true,true);
        TextLayout layout=new TextLayout(str,font,context);
        AffineTransform tr=new AffineTransform();
        tr.translate(10f,70f+font.getSize2D());
        Shape shape=layout.getOutline(tr);
        
        
        /*Path2D.Float shape=new Path2D.Float();
        
        shape.moveTo(100,100);
        shape.lineTo(290,490);
        shape.lineTo(400,500);
        shape.lineTo(100,500);
        shape.closePath();
        */
        
        g.setColor(new Color(0,0,0));
        g.fill(shape);
        
        Rectangle2D.Float underline=new Rectangle2D.Float(10f,70f+font.getSize2D()+g.getFontMetrics().getDescent()-font.getSize2D()/10f,g.getFontMetrics().stringWidth(str),font.getSize2D()/10f);
        Area ua=new Area(underline);

        float extend=font.getSize2D()/30f*slider.getValue()/25f;
        long before=System.nanoTime();
        Area ta=new Area(Geometry.offsetShape(shape,extend,BasicStroke.JOIN_MITER));
        long after=System.nanoTime();
        System.out.println((after-before)/1_000_000f);
        
        g.setColor(new Color(0,255,0));
        //g.fill(ta);

        /*
        BasicStroke st=new BasicStroke(extend,BasicStroke.CAP_BUTT,BasicStroke.JOIN_MITER,10f);
        g.setColor(new Color(0,255,0,128));
        g.setStroke(st);
        g.draw(shape);
        g.setStroke(new  BasicStroke());
        */
        
        g.setColor(new Color(255,0,255));
        //g.draw(ta);
        
        ua.subtract(ta);
        g.setColor(new Color(0,0,0));
        g.fill(ua);
        
        graphics.drawImage(img,0,0,new ImageObserver()
        {
          @Override
          public boolean imageUpdate(Image i,int infoflags,int x,int y,int width,int height)
          {
            return false;
          }
        });
        img.flush();
      }
    };
    frame.add(canvas,BorderLayout.CENTER);
    slider.setValue(100);
    frame.add(slider,BorderLayout.SOUTH);
    slider.addChangeListener(e->canvas.repaint());
    
    
    frame.setSize(1024,768);

    frame.setVisible(true);
  }
}
