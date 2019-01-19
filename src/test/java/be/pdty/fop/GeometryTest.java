package be.pdty.fop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Rectangle2D;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;

import be.pdty.fop.Geometry.UnderlineMethod;

@SuppressWarnings("javadoc")
public class GeometryTest {
    private static void testCloseEnough(Shape ref,Shape test) {
        Area xor = new Area(ref);
        xor.exclusiveOr(new Area(test));
        
        double sumOfArea = 0;
        PathIterator it = xor.getPathIterator(null,0.1f);
        double[] p = new double[2];
        double firstX = 0;
        double firstY = 0;
        double prevX = 0;
        double prevY = 0;
        while(!it.isDone()) {
            int type = it.currentSegment(p);
            switch(type) {
            case PathIterator.SEG_MOVETO:
                firstX = p[0];
                firstY = p[1];
                prevX = firstX;
                prevY = firstY;
                break;
            case PathIterator.SEG_LINETO:
                double x = p[0];
                double y = p[1];
                sumOfArea += (prevX * y) - (x * prevY);
                prevX = x;
                prevY = y;
                break;
            case PathIterator.SEG_CLOSE:
                sumOfArea += (prevX * firstY) - (firstX * prevY);
                break;
            }
            it.next();
        }
        
        assertTrue(sumOfArea < 0.1);
    }
    
    @Test
    public void testStraight() {
        Path2D.Double textOutline = new Path2D.Double();
        Rectangle2D.Double underline = new Rectangle2D.Double(0,140,800,20);
        List<Shape> l = Geometry.getUnderlineShapes(textOutline,underline,UnderlineMethod.STRAIGHT);
        assertEquals(1,l.size());
        
        assertTrue(l.get(0) instanceof Rectangle2D);
        Rectangle2D r1 = (Rectangle2D)l.get(0);
        assertEquals(underline,r1);
    }
    
    @Test
    public void testLargestGapStraight() {
        Path2D.Double textOutline = new Path2D.Double();
        
        textOutline.moveTo(100,100);
        textOutline.lineTo(200,100);
        textOutline.lineTo(200,200);
        textOutline.lineTo(100,200);
        textOutline.closePath();
        
        textOutline.moveTo(220,100);
        textOutline.lineTo(240,100);
        textOutline.lineTo(240,200);
        textOutline.lineTo(200,200);
        textOutline.closePath();
        
        Rectangle2D.Double underline = new Rectangle2D.Double(0,140,800,20);
        
        List<Shape> l = Geometry.getUnderlineShapes(textOutline,underline,UnderlineMethod.LARGEST_GAP);
        assertEquals(2,l.size());
        
        assertTrue(l.get(0) instanceof Rectangle2D);
        Rectangle2D r1 = (Rectangle2D)l.get(0);
        assertEquals(new Rectangle2D.Double(0,140,80,20),r1);

        assertTrue(l.get(1) instanceof Rectangle2D);
        Rectangle2D r2 = (Rectangle2D)l.get(1);
        assertEquals(new Rectangle2D.Double(260,140,540,20),r2);
    }

    @Test
    public void testLargestGapQuad() {
        Path2D.Double textOutline = new Path2D.Double();
        
        textOutline.moveTo(100,100);
        textOutline.lineTo(200,100);
        textOutline.quadTo(250,150,200,200);
        textOutline.lineTo(100,200);
        textOutline.closePath();
        
        textOutline.moveTo(300,100);
        textOutline.lineTo(400,100);
        textOutline.quadTo(350,150,400,200);
        textOutline.lineTo(300,200);
        textOutline.closePath();
        
        textOutline.moveTo(500,100);
        textOutline.lineTo(600,100);
        textOutline.quadTo(650,150,700,200);
        textOutline.lineTo(500,200);
        textOutline.closePath();
        
        Rectangle2D.Double underline = new Rectangle2D.Double(0,140,800,20);
        
        List<Shape> l = Geometry.getUnderlineShapes(textOutline,underline,UnderlineMethod.LARGEST_GAP);
        assertEquals(4,l.size());
        
        assertTrue(l.get(0) instanceof Rectangle2D);
        Rectangle2D r1 = (Rectangle2D)l.get(0);
        assertEquals(new Rectangle2D.Double(0,140,80,20),r1);

        assertTrue(l.get(1) instanceof Rectangle2D);
        Rectangle2D r2 = (Rectangle2D)l.get(1);
        assertEquals(new Rectangle2D.Double(245,140,35,20),r2);

        assertTrue(l.get(2) instanceof Rectangle2D);
        Rectangle2D r3 = (Rectangle2D)l.get(2);
        assertEquals(new Rectangle2D.Double(396,140,84,20),r3);

        assertTrue(l.get(3) instanceof Rectangle2D);
        Rectangle2D r4 = (Rectangle2D)l.get(3);
        assertEquals(new Rectangle2D.Double(680,140,120,20),r4);
    }
    
    private double round(double d) {
        return Math.round(d*100)/100.0;
    }
    
    private Rectangle2D round(Rectangle2D in) {
        return new Rectangle2D.Double(round(in.getMinX()),round(in.getMinY()),round(in.getWidth()),round(in.getHeight()));
    }
    
    @Test
    public void testLargestGapCubic() {
        Path2D.Double textOutline = new Path2D.Double();
        
        textOutline.moveTo(100,100);
        textOutline.lineTo(200,100);
        textOutline.curveTo(250,100,250,200,200,200);
        textOutline.lineTo(100,200);
        textOutline.closePath();

        textOutline.moveTo(300,100);
        textOutline.lineTo(400,100);
        textOutline.curveTo(420,100,430,200,450,200);
        textOutline.lineTo(300,200);
        textOutline.closePath();

        textOutline.moveTo(500,100);
        textOutline.lineTo(600,100);
        textOutline.curveTo(700,150,600,200,650,200);
        textOutline.lineTo(500,200);
        textOutline.closePath();
        
        textOutline.moveTo(800,100);
        textOutline.lineTo(900,100);
        textOutline.curveTo(750,160,925,200,950,200);
        textOutline.lineTo(800,200);
        textOutline.closePath();
        
        Rectangle2D.Double underline = new Rectangle2D.Double(0,140,1000,20);
        List<Shape> l = Geometry.getUnderlineShapes(textOutline,underline,UnderlineMethod.LARGEST_GAP);

        assertEquals(5,l.size());
        
        assertTrue(l.get(0) instanceof Rectangle2D);
        Rectangle2D r1 = (Rectangle2D)l.get(0);
        assertEquals(new Rectangle2D.Double(0,140,80,20),round(r1));

        assertTrue(l.get(1) instanceof Rectangle2D);
        Rectangle2D r2 = (Rectangle2D)l.get(1);
        assertEquals(new Rectangle2D.Double(257.5,140,22.5,20),round(r2));

        assertTrue(l.get(2) instanceof Rectangle2D);
        Rectangle2D r3 = (Rectangle2D)l.get(2);
        assertEquals(new Rectangle2D.Double(448.02,140,31.98,20),round(r3));

        assertTrue(l.get(3) instanceof Rectangle2D);
        Rectangle2D r4 = (Rectangle2D)l.get(3);
        assertEquals(new Rectangle2D.Double(666.59,140,113.41,20),round(r4));

        assertTrue(l.get(4) instanceof Rectangle2D);
        Rectangle2D r5 = (Rectangle2D)l.get(4);
        assertEquals(new Rectangle2D.Double(864.71,140,135.29,20),round(r5));
    }    
    
    @Test
    public void testStraightUnderlineOffset() {
        Path2D.Double textOutline = new Path2D.Double();
        
        textOutline.moveTo(100,100);
        textOutline.lineTo(200,100);
        textOutline.lineTo(200,200);
        textOutline.lineTo(100,200);
        textOutline.closePath();
        
        textOutline.moveTo(220,100);
        textOutline.lineTo(240,100);
        textOutline.lineTo(240,200);
        textOutline.lineTo(200,200);
        textOutline.closePath();
        
        Rectangle2D.Double underline = new Rectangle2D.Double(0,140,800,20);
        
        List<Shape> l1 = Geometry.getUnderlineShapes(textOutline,underline,UnderlineMethod.LARGEST_GAP);
        List<Shape> l2 = Geometry.getUnderlineShapes(textOutline,underline,UnderlineMethod.OFFSET_MASK);
        
        Path2D.Double combinedL1 = new Path2D.Double();
        l1.forEach(l->combinedL1.append(l,false));

        Path2D.Double combinedL2 = new Path2D.Double();
        l2.forEach(l->combinedL2.append(l,false));
        
        testCloseEnough(combinedL1,combinedL2);
    }
    
    @SuppressWarnings("nls")
    private static void save(Shape shape,Path target,String description) throws IOException {
        Files.createDirectories(target.getParent());
        try(Writer writer=Files.newBufferedWriter(target,Charset.forName("UTF-8"))) {
            writer.append("# "+description+"\n\n");
            PathIterator it=shape.getPathIterator(null);
            double[] p=new double[6];
            while(!it.isDone()) {
                int type = it.currentSegment(p);
                switch(type) {
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    writer.append(""+type+"|"+p[0]+"|"+p[1]+"\n");
                    break;
                case PathIterator.SEG_QUADTO:
                    writer.append(""+type+"|"+p[0]+"|"+p[1]+"|"+p[2]+"|"+p[3]+"\n");
                    break;
                case PathIterator.SEG_CUBICTO:
                    writer.append(""+type+"|"+p[0]+"|"+p[1]+"|"+p[2]+"|"+p[3]+"|"+p[4]+"|"+p[5]+"\n");
                    break;
                case PathIterator.SEG_CLOSE:
                    writer.append(""+type+"\n\n");
                    break;
                }
                it.next();
            }
            writer.flush();
        }
    }
    
    @SuppressWarnings("nls")
    private static Shape load(InputStream is) throws IOException {
        Path2D.Double ans = new Path2D.Double();
        
        try(BufferedReader br = new BufferedReader(new InputStreamReader(is,Charset.forName("UTF-8")))) {
            String line = br.readLine();
            while(line!=null) {
                line=line.trim();
                if(line.length()>0 && !line.startsWith("#")) {
                    String[] parts = line.split("\\|");
                    int type = Integer.parseInt(parts[0]);
                    switch(type) {
                        case PathIterator.SEG_MOVETO:
                            ans.moveTo(Double.parseDouble(parts[1]),Double.parseDouble(parts[2]));
                            break;
                        case PathIterator.SEG_LINETO:
                            ans.lineTo(Double.parseDouble(parts[1]),Double.parseDouble(parts[2]));
                            break;
                        case PathIterator.SEG_QUADTO:
                            ans.quadTo(Double.parseDouble(parts[1]),Double.parseDouble(parts[2]),Double.parseDouble(parts[3]),Double.parseDouble(parts[4]));
                            break;
                        case PathIterator.SEG_CUBICTO:
                            ans.curveTo(Double.parseDouble(parts[1]),Double.parseDouble(parts[2]),Double.parseDouble(parts[3]),Double.parseDouble(parts[4]),Double.parseDouble(parts[5]),Double.parseDouble(parts[6]));
                            break;
                        case PathIterator.SEG_CLOSE:
                            ans.closePath();
                            break;
                    }
                }
                
                line = br.readLine();
            }
        }
        return ans;
    }
    
    
    @SuppressWarnings({ "nls", "unused" })
    private static void genData(String text,String name) throws IOException {
        Font font = new Font("Times", Font.PLAIN, 120);
        FontRenderContext ctx = new FontRenderContext(null, true, true);
        TextLayout layout = new TextLayout(text, font, ctx);
        Shape textShape = layout.getOutline(null);
        save(textShape,Paths.get("src/test/resources/be/pdty/fop/offset/"+name+"_base.txt"),"Base geometry for "+name+": "+text);

        double offset = 20;
        save(Geometry.offsetShape(textShape,offset,BasicStroke.JOIN_BEVEL),Paths.get("src/test/resources/be/pdty/fop/offset/"+name+"_bevel.txt"),"Offset "+offset+" Bevel geometry for "+name+": "+text);
        save(Geometry.offsetShape(textShape,offset,BasicStroke.JOIN_MITER),Paths.get("src/test/resources/be/pdty/fop/offset/"+name+"_miter.txt"),"Offset "+offset+" Miter geometry for "+name+": "+text);
        save(Geometry.offsetShape(textShape,offset,BasicStroke.JOIN_ROUND),Paths.get("src/test/resources/be/pdty/fop/offset/"+name+"_round.txt"),"Offset "+offset+" Round geometry for "+name+": "+text);
    }
    

    @SuppressWarnings("nls")
    private static void testOffset(String text,String name) throws IOException {
        Shape base = load(GeometryTest.class.getClassLoader().getResourceAsStream("be/pdty/fop/offset/"+name+"_base.txt"));
        Shape ref_bevel = load(GeometryTest.class.getClassLoader().getResourceAsStream("be/pdty/fop/offset/"+name+"_bevel.txt"));
        Shape ref_miter = load(GeometryTest.class.getClassLoader().getResourceAsStream("be/pdty/fop/offset/"+name+"_miter.txt"));
        Shape ref_round = load(GeometryTest.class.getClassLoader().getResourceAsStream("be/pdty/fop/offset/"+name+"_round.txt"));
        
        double offset = 20;
        Shape bevel = Geometry.offsetShape(base, offset, BasicStroke.JOIN_BEVEL);
        Shape miter = Geometry.offsetShape(base, offset, BasicStroke.JOIN_MITER);
        Shape round = Geometry.offsetShape(base, offset, BasicStroke.JOIN_ROUND);
        
        testCloseEnough(ref_bevel,bevel);
        testCloseEnough(ref_miter,miter);
        testCloseEnough(ref_round,round);
    }
    
    @SuppressWarnings("nls")
    private static final String[] testData = new String[] {
            "l","letter_l",
            "w","letter_w",
            "o","letter_o",
            "jlçrjpgaj","latin",
            "सभी मनुष्यों को गौरव और","hindi",
            "تَعْليق","arabic",
            "พยัญชนะรูปสระ","thai",
            "在尊严和权利上一","chinese_1",
            "龘齉爨馕","chinese_2",
            "👱 👨‍❤💋‍👧‍👦","emoji",
    };
    
    /*
    public static void main(String[] args) throws IOException {
        for(int i=0;i<testData.length;i+=2) {
            genData(testData[i],testData[i+1]);
        }
    }
    */
    
    @Test
    public void testOffset() throws IOException {
        for(int i=0;i<testData.length;i+=2) {
            testOffset(testData[i],testData[i+1]);
        }
    }
    
    @Test
    public void testSmallOffset() {
        Path2D.Double shape = new Path2D.Double();
        
        shape.moveTo(100,100);
        shape.lineTo(200,100);
        shape.lineTo(200,200);
        shape.lineTo(100,200);
        shape.closePath();
        
        shape.moveTo(220,100);
        shape.lineTo(240,100);
        shape.lineTo(240,200);
        shape.lineTo(200,200);
        shape.closePath();
        
        testCloseEnough(shape,Geometry.offsetShape(shape, 0, BasicStroke.JOIN_BEVEL));
        testCloseEnough(shape,Geometry.offsetShape(shape, 0, BasicStroke.JOIN_MITER));
        testCloseEnough(shape,Geometry.offsetShape(shape, 0, BasicStroke.JOIN_ROUND));

        testCloseEnough(shape,Geometry.offsetShape(shape, 0.1, BasicStroke.JOIN_BEVEL));
        testCloseEnough(shape,Geometry.offsetShape(shape, 0.1, BasicStroke.JOIN_MITER));
        testCloseEnough(shape,Geometry.offsetShape(shape, 0.1, BasicStroke.JOIN_ROUND));
    }
}
