/*
 * Copyright 2016 Philippe Detournay
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
import org.apache.fop.fonts.Typeface;
import org.apache.fop.fonts.base14.Base14FontCollection;
import org.apache.fop.render.AbstractPathOrientedRenderer;
import org.apache.fop.render.RendererContext;
import org.apache.fop.render.pdf.CTMHelper;
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

	private void fill(GeneralPath path) {
		state.configureGC(wrapper);
		wrapper.fillPath(Convert.toPathData(path));
	}

	private void draw(float x1, float y1, float x2, float y2) {
		state.configureGC(wrapper);
		wrapper.drawLine(x1, y1, x2, y2);
	}

	private void drawH(float x1, float x2, float y) {
		wrapper.drawLine(x1/* +.5f */, y, x2/*-.5f*/, y);
	}

	private void drawV(float x, float y1, float y2) {
		wrapper.drawLine(x, y1/* +.5f */, x, y2/*-.5f*/);
	}

	@Override
	protected void drawBorderLine(float x1, float y1, float x2, float y2, boolean horz, boolean startOrBefore,
	        int style, Color col) {
		float width = x2 - x1;
		float height = y2 - y1;
		drawBorderLine(new Rectangle2D.Float(x1, y1, width, height), horz, startOrBefore, style, col);
	}

	private void drawBorderLine(Rectangle2D.Float lineRect, boolean horz, boolean startOrBefore, int style, Color col) {
		float x1 = lineRect.x;
		float y1 = lineRect.y;
		float x2 = x1 + lineRect.width;
		float y2 = y1 + lineRect.height;
		float w = lineRect.width;
		float h = lineRect.height;
		if ((w < 0) || (h < 0)) {
			return;
		}

		state.configureGC(wrapper);

		switch (style) {
		case Constants.EN_DASHED:
			wrapper.setColor(Convert.toRGBA(col));
			if (horz) {
				float unit = Math.abs(2 * h);
				int rep = (int) (w / unit);
				if (rep % 2 == 0) {
					rep++;
				}
				unit = w / rep;
				float ym = y1 + (h / 2);
				BasicStroke s = new BasicStroke(h, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
				        new float[] { unit, unit }, 0);
				wrapper.setLineAttributes(Convert.toLineAttributes(s));
				drawH(x1, x2, ym);
			} else {
				float unit = Math.abs(2 * w);
				int rep = (int) (h / unit);
				if (rep % 2 == 0) {
					rep++;
				}
				unit = h / rep;
				float xm = x1 + (w / 2);
				BasicStroke s = new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f,
				        new float[] { unit, unit }, 0);
				wrapper.setLineAttributes(Convert.toLineAttributes(s));
				drawV(xm, y1, y2);
			}
			break;
		case Constants.EN_DOTTED:
			wrapper.setColor(Convert.toRGBA(col));
			if (horz) {
				float unit = Math.abs(2 * h);
				int rep = (int) (w / unit);
				if (rep % 2 == 0) {
					rep++;
				}
				unit = w / rep;
				float ym = y1 + (h / 2);
				BasicStroke s = new BasicStroke(h, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 10.0f,
				        new float[] { 0.1f, unit }, 0);
				wrapper.setLineAttributes(Convert.toLineAttributes(s));
				drawH(x1, x2, ym);
			} else {
				float unit = Math.abs(2 * w);
				int rep = (int) (h / unit);
				if (rep % 2 == 0) {
					rep++;
				}
				unit = h / rep;
				float xm = x1 + (w / 2);
				BasicStroke s = new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER, 10.0f,
				        new float[] { 0.1f, unit }, 0);
				wrapper.setLineAttributes(Convert.toLineAttributes(s));
				drawV(xm, y1, y2);
			}
			break;
		case Constants.EN_DOUBLE:
			wrapper.setColor(Convert.toRGBA(col));
			if (horz) {
				float h3 = h / 3;
				float ym1 = y1 + (h3 / 2);
				float ym2 = ym1 + h3 + h3;
				BasicStroke s = new BasicStroke(h3);
				wrapper.setLineAttributes(Convert.toLineAttributes(s));
				drawH(x1, x2, ym1);
				drawH(x1, x2, ym2);
			} else {
				float w3 = w / 3;
				float xm1 = x1 + (w3 / 2);
				float xm2 = xm1 + w3 + w3;
				BasicStroke s = new BasicStroke(w3);
				wrapper.setLineAttributes(Convert.toLineAttributes(s));
				drawV(xm1, y1, y2);
				drawV(xm2, y1, y2);
			}
			break;
		case Constants.EN_GROOVE:
		case Constants.EN_RIDGE:
			float colFactor = (style == EN_GROOVE ? 0.4f : -0.4f);
			if (horz) {
				Color uppercol = ColorUtil.lightenColor(col, -colFactor);
				Color lowercol = ColorUtil.lightenColor(col, colFactor);
				float h3 = h / 3;
				float ym1 = y1 + (h3 / 2);
				wrapper.setLineAttributes(Convert.toLineAttributes(new BasicStroke(h3)));
				wrapper.setColor(Convert.toRGBA(uppercol));
				drawH(x1, x2, ym1);
				wrapper.setColor(Convert.toRGBA(col));
				drawH(x1, x2, ym1 + h3);
				wrapper.setColor(Convert.toRGBA(lowercol));
				drawH(x1, x2, ym1 + h3 + h3);
			} else {
				Color leftcol = ColorUtil.lightenColor(col, -colFactor);
				Color rightcol = ColorUtil.lightenColor(col, colFactor);
				float w3 = w / 3;
				float xm1 = x1 + (w3 / 2);
				wrapper.setLineAttributes(Convert.toLineAttributes(new BasicStroke(w3)));
				wrapper.setColor(Convert.toRGBA(leftcol));
				drawV(xm1, y1, y2);
				wrapper.setColor(Convert.toRGBA(col));
				drawV(xm1 + w3, y1, y2);
				wrapper.setColor(Convert.toRGBA(rightcol));
				drawV(xm1 + w3 + w3, y1, y2);
			}
			break;
		case Constants.EN_INSET:
		case Constants.EN_OUTSET:
			colFactor = (style == EN_OUTSET ? 0.4f : -0.4f);
			if (horz) {
				col = ColorUtil.lightenColor(col, (startOrBefore ? 1 : -1) * colFactor);
				wrapper.setLineAttributes(Convert.toLineAttributes(new BasicStroke(h)));
				float ym1 = y1 + (h / 2);
				wrapper.setColor(Convert.toRGBA(col));
				drawH(x1, x2, ym1);
			} else {
				col = ColorUtil.lightenColor(col, (startOrBefore ? 1 : -1) * colFactor);
				float xm1 = x1 + (w / 2);
				wrapper.setLineAttributes(Convert.toLineAttributes(new BasicStroke(w)));
				wrapper.setColor(Convert.toRGBA(col));
				drawV(xm1, y1, y2);
			}
			break;
		case Constants.EN_HIDDEN:
			break;
		default:
			wrapper.setColor(Convert.toRGBA(col));
			if (horz) {
				float ym = y1 + (h / 2);
				wrapper.setLineAttributes(Convert.toLineAttributes(new BasicStroke(h)));
				drawH(x1, x2, ym);
			} else {
				float xm = x1 + (w / 2);
				wrapper.setLineAttributes(Convert.toLineAttributes(new BasicStroke(w)));
				drawV(xm, y1, y2);
			}
		}

	}

	@Override
	public void renderText(TextArea text) {
		renderInlineAreaBackAndBorders(text);

		int rx = currentIPPosition + text.getBorderAndPaddingWidthStart();
		int bl = currentBPPosition + text.getBlockProgressionOffset() + text.getBaselineOffset();
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

		int style = area.getRuleStyle();
		switch (style) {
		case EN_SOLID:
		case EN_DASHED:
		case EN_DOUBLE:
			drawBorderLine(startx, starty, endx, starty + ruleThickness, true, true, style, col);
			break;
		case EN_DOTTED:
			state.updateStroke(new BasicStroke(ruleThickness, BasicStroke.CAP_ROUND, BasicStroke.JOIN_BEVEL, 0f,
			        new float[] { 0, 2 * ruleThickness }, ruleThickness));
			float rt2 = ruleThickness / 2f;
			line.setLine(line.getX1(), line.getY1() + rt2, line.getX2(), line.getY2() + rt2);
			draw(line.x1, line.y1, line.x2, line.y2);
			break;
		case EN_GROOVE:
		case EN_RIDGE:
			float half = area.getRuleThickness() / 2000f;

			state.updateColor(ColorUtil.lightenColor(col, 0.6f));
			moveTo(startx, starty);
			lineTo(endx, starty);
			lineTo(endx, starty + 2 * half);
			lineTo(startx, starty + 2 * half);
			closePath();
			fill(currentPath);
			currentPath = null;
			state.updateColor(col);
			if (style == EN_GROOVE) {
				moveTo(startx, starty);
				lineTo(endx, starty);
				lineTo(endx, starty + half);
				lineTo(startx + half, starty + half);
				lineTo(startx, starty + 2 * half);
			} else {
				moveTo(endx, starty);
				lineTo(endx, starty + 2 * half);
				lineTo(startx, starty + 2 * half);
				lineTo(startx, starty + half);
				lineTo(endx - half, starty + half);
			}
			closePath();
			fill(currentPath);
			currentPath = null;
			break;

		case EN_NONE:
			// No rule is drawn
			break;
		default:
			break;
		} // end switch

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
