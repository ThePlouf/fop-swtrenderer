package be.pdty.fop;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FopFactoryBuilder;
import org.apache.xmlgraphics.io.Resource;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * SWTRendererTest.
 */
public class SWTRendererTest
{
  /**
   * @param args
   * @throws Exception
   */
  public static void main(String[] args) throws Exception {
    
    FopFactoryBuilder builder = new FopFactoryBuilder(new File(".").toURI(),new ResourceResolver() //$NON-NLS-1$
    {
      
      @SuppressWarnings("nls")
      @Override
      public Resource getResource(URI uri) throws IOException
      {
        String path=uri.getPath();
        int pos=path.lastIndexOf('/');
        path=path.substring(pos+1);
        path="be/pdty/fop/"+path+".png";
        return new Resource(SWTRendererTest.class.getClassLoader().getResourceAsStream(path));
      }
      
      @Override
      public OutputStream getOutputStream(URI uri) throws IOException
      {
        return null;
      }
    });
    builder.setStrictFOValidation(false);
    FopFactory factory = builder.build();
    FOUserAgent agent = factory.newFOUserAgent();
    SWTRenderer renderer = new SWTRenderer(agent);
    agent.setRendererOverride(renderer);
    Fop fop = factory.newFop(agent);

    TransformerFactory transformerFactory = TransformerFactory.newInstance();
    Transformer transformer = transformerFactory.newTransformer();
    Source src = new StreamSource(SWTRendererTest.class.getClassLoader().getResourceAsStream("be/pdty/fop/doc2.xml")); //$NON-NLS-1$
    Result res = new SAXResult(fop.getDefaultHandler());
    transformer.transform(src, res);
    
    Display display=Display.getDefault();
    Shell sh=new Shell(display);
    sh.setLayout(new FillLayout());
    Canvas canvas=new Canvas(sh,SWT.NONE);
    Printable printable = renderer.getPrintable(0);
    canvas.addPaintListener(e->{
      e.gc.setBackground(e.gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
      e.gc.fillRectangle(canvas.getBounds());
      printable.print(e.gc);
    });
    
    int width=(int)(renderer.getPageFormat(0).getWidth()/72.0*sh.getDisplay().getDPI().x);
    int height=(int)(renderer.getPageFormat(0).getHeight()/72.0*sh.getDisplay().getDPI().y);
    sh.setSize(width+30,height+30);
    sh.setVisible(true);
    
    while(!sh.isDisposed()) {
      if(!display.readAndDispatch())
        display.sleep();
    }
  }
}
