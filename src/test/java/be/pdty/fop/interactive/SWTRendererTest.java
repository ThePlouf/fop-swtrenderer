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
package be.pdty.fop.interactive;

import java.io.File;
import java.io.FileInputStream;
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
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.Ignore;
import org.junit.Test;

import be.pdty.fop.Printable;
import be.pdty.fop.SWTRenderer;

/**
 * SWTRendererTest.
 */
@Ignore
public class SWTRendererTest {
    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        FopFactoryBuilder builder = new FopFactoryBuilder(new File(".").toURI(), new ResourceResolver() //$NON-NLS-1$
        {

            @SuppressWarnings("nls")
            @Override
            public Resource getResource(URI uri) throws IOException {
                //if(true) return null;
                String path = uri.getPath();
                int pos = path.lastIndexOf('/');
                path = path.substring(pos + 1);
                //path="be/pdty/fop/"+path+".png";
                path = "d:\\temp\\" + path;

                System.out.println(path);
                return new Resource(new FileInputStream(path));
                //return new Resource(SWTRendererTest.class.getClassLoader().getResourceAsStream(path));
            }

            @Override
            public OutputStream getOutputStream(URI uri) throws IOException {
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
        Source src = new StreamSource(new java.io.FileInputStream("d:\\temp\\doc5.xml")); //$NON-NLS-1$
        //Source src = new StreamSource(SWTRendererTest.class.getClassLoader().getResourceAsStream("be/pdty/fop/doc2.xml")); //$NON-NLS-1$
        //Source src = new StreamSource(new java.net.URL("http://xep.xattic.com/xep/testsuite/features/leader.fo").openStream()); //$NON-NLS-1$
        //Source src = new StreamSource(new java.net.URL("http://xep.xattic.com/xep/testsuite/features/containers.fo").openStream()); //$NON-NLS-1$
        //Source src = new StreamSource(new java.net.URL("http://xep.xattic.com/xep/testsuite/features/lists.fo").openStream()); //$NON-NLS-1$

        //Source src = new StreamSource(new java.net.URL("http://www.renderx.com/files/demos/examples/mail/direct_mail.fo").openStream()); //$NON-NLS-1$
        //Source src = new StreamSource(new java.net.URL("http://www.renderx.com/files/demos/examples/report/internal_report.fo").openStream()); //$NON-NLS-1$
        //Source src = new StreamSource(new java.net.URL("http://www.renderx.com/files/demos/examples/order/receipt_order.fo").openStream()); //$NON-NLS-1$
        //Source src = new StreamSource(new java.net.URL("http://www.renderx.com/files/demos/examples/balance/balance_sheet.fo").openStream()); //$NON-NLS-1$

        //Source src = new StreamSource(new java.net.URL("http://xep.xattic.com/xep/testsuite/features/decoration.fo").openStream()); //$NON-NLS-1$
        Result res = new SAXResult(fop.getDefaultHandler());
        transformer.transform(src, res);

        Display display = Display.getDefault();

        /*
        PrinterData pdata = Printer.getDefaultPrinterData();
        Printer printer = new Printer(pdata);
        printer.startJob("test");
        printer.startPage();
        
        GC gc = new GC(printer);
        Printable p = renderer.getPrintable(0);
        p.print(gc);
        
        
        printer.endPage();
        printer.endJob();
        
        if(true) return;
        */

        for (int i = 0; i < renderer.getNumberOfPages() * 0 + 1; i++) {
            Shell sh = new Shell(display);
            sh.setLayout(new FillLayout());
            Canvas canvas = new Canvas(sh, SWT.NONE);
            Printable printable = renderer.getPrintable(i);
            canvas.addPaintListener(e -> {
                e.gc.setBackground(e.gc.getDevice().getSystemColor(SWT.COLOR_WHITE));
                e.gc.fillRectangle(canvas.getBounds());

                Region region = new Region(display);
                region.add(50, 50, 400, 400);
                region.add(200, 400, 200, 200);
                //e.gc.setClipping(region);

                Transform transform = new Transform(display);
                //transform.translate(-500,-2500);
                //transform.scale(2,2);
                transform.rotate(20);
                //e.gc.setTransform(transform);

                printable.print(e.gc);

                region.dispose();
                transform.dispose();
            });

            int width = (int) (renderer.getPageFormat(i).getWidth() / 72.0 * sh.getDisplay().getDPI().x);
            int height = (int) (renderer.getPageFormat(i).getHeight() / 72.0 * sh.getDisplay().getDPI().y);
            sh.setSize(width + 30, height + 30);
            sh.setVisible(true);
            sh.addDisposeListener((e) -> {
                System.exit(0);
            });
        }

        while (!display.isDisposed()) {
            if (!display.readAndDispatch() && !display.isDisposed())
                display.sleep();
        }
    }

    /**
     * 
     * @throws Exception
     */
    @Test
    public void test() throws Exception {
        main(new String[0]);
    }
}
