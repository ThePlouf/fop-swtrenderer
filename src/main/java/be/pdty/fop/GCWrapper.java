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

import java.util.Arrays;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.LineAttributes;
import org.eclipse.swt.graphics.Path;
import org.eclipse.swt.graphics.PathData;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.printing.Printer;

/**
 * Wraps a GC to offer multiple additional services: Decimal-precision drawing
 * primitives; Optimization of redundant attribute settings (setting twice the
 * same color will have no effect); Resource management.
 * 
 * Note that it does not offer advanced caching: changing the color in rapid
 * intervals will still cause the underlying SWT color to be created and
 * disposed each time.
 * 
 * Because the instance will "remember" the last used colors and resources, it
 * is important to dispose the wrapper when finished. Note that the wrapper does
 * not dispose the underlying GC.
 * 
 * The wrapper will "adopt" the GC transform at the time of creation and will
 * treat it as its "no transform" status. In other words setting the transform
 * to null will reset it back to the transform at the time the wrapper got
 * created.
 * 
 * The wrapper will ignore any existing clipping region and will always draw
 * over the entire GC. Any further clipping request done through the wrapper
 * will be considered global and will not be combined with the base clipping.
 * 
 * Any GC property (Font, colors...) will be restored when the wrapper is
 * disposed.
 *
 * This class only supports Base14 fonts for font management.
 * 
 * It is strongly discouraged to continue using the GC directly after having
 * manipulated it using a wrapper and before disposing this wrapper. For this
 * reason there is no getGC method.
 */
public class GCWrapper {
	private static final float PF = 200f;

	private GC gc;
	private boolean disposed;
	private float sx;
	private float sy;

	private Base14FontProvider fontCache;

	private String fontName;
	private int fontSize;
	private boolean dirtyFont;

	private RGBA color;
	private Color swtColor;
	private boolean dirtyColor;

	private float[] transform;
	private Transform swtTransform;
	private boolean dirtyTransform;

	private PathData clip;
	private Path swtClip;
	private boolean dirtyClip;

	private LineAttributes lineAttributes;
	private boolean dirtyLineAttributes;

	private Transform baseTransform;
	private Color baseForeground;
	private Color baseBackground;
	private Font baseFont;
	private LineAttributes baseAttributes;
	private boolean baseAdvanced;
	private int baseAntialias;
	private int baseTextAntialias;
	private int baseInterpolation;
	private Region baseClip;

	/**
	 * Create a new GCWrapper.
	 * 
	 * @param gcToWrap
	 *            GC to wrap.
	 */
	public GCWrapper(GC gcToWrap) {
		disposed = false;
		gc = gcToWrap;

		sx = gc.getDevice().getDPI().x / 72.0f;
		sy = gc.getDevice().getDPI().y / 72.0f;

		// Temporary workaround for SWT bug 498062
		if (gc.getDevice() instanceof Printer && System.getProperty("os.name").contains("Windows")) { //$NON-NLS-1$ //$NON-NLS-2$
			String sz = System.getProperty("org.eclipse.swt.internal.deviceZoom", "100"); //$NON-NLS-1$ //$NON-NLS-2$
			float zoom;
			try {
				zoom = Integer.parseInt(sz) / 100.0f;
			} catch (NumberFormatException ex) {
				zoom = 1.0f;
			}
			sx /= zoom;
			sy /= zoom;
		}

		fontCache = new Base14FontProvider(gcToWrap.getDevice());

		baseTransform = new Transform(gc.getDevice());
		gc.getTransform(baseTransform);

		baseClip = new Region(gc.getDevice());
		gc.getClipping(baseClip);
		gc.setClipping((Region) null);

		baseFont = gc.getFont();
		baseForeground = gc.getForeground();
		baseBackground = gc.getBackground();
		baseAttributes = gc.getLineAttributes();

		baseAdvanced = gc.getAdvanced();
		gc.setAdvanced(true);
		baseAntialias = gc.getAntialias();
		gc.setAntialias(SWT.ON);
		baseTextAntialias = gc.getTextAntialias();
		gc.setTextAntialias(SWT.ON);
		baseInterpolation = gc.getInterpolation();
		gc.setInterpolation(SWT.HIGH);

		fontName = ""; //$NON-NLS-1$
		fontSize = 0;
		dirtyFont = false;

		color = null;
		swtColor = null;
		dirtyColor = false;

		transform = null;
		swtTransform = null;
		dirtyTransform = false;

		clip = null;
		swtClip = null;
		dirtyClip = false;
	}

	private PathData scale(PathData data) {
		float[] points = new float[data.points.length];
		for (int i = 0; i < data.points.length; i++) {
			points[i] = data.points[i] * PF;
		}
		PathData ans = new PathData();
		ans.points = points;
		ans.types = data.types;
		return ans;
	}

	private void commit() {
		if (dirtyFont) {
			Font font = fontCache.getFont(fontName, (int) (PF * fontSize / (1000.0f * sy)));
			gc.setFont(font);
			dirtyFont = false;
		}

		if (dirtyColor) {
			if (swtColor != null) {
				swtColor.dispose();
			}
			swtColor = new Color(gc.getDevice(), color);
			gc.setForeground(swtColor);
			gc.setBackground(swtColor);
			dirtyColor = false;
		}

		if (dirtyTransform) {
			if (swtTransform != null) {
				swtTransform.dispose();
			}
			swtTransform = new Transform(gc.getDevice());

			Transform userTransform = transform == null ? new Transform(gc.getDevice())
			        : new Transform(gc.getDevice(), transform);
			try {
				swtTransform.multiply(baseTransform);
				swtTransform.scale(sx, sy);
				swtTransform.multiply(userTransform);
				swtTransform.scale(1.0f / PF, 1.0f / PF);
			} finally {
				userTransform.dispose();
			}

			gc.setTransform(swtTransform);
			dirtyTransform = false;
		}

		if (dirtyClip) {
			if (swtClip != null) {
				swtClip.dispose();
			}
			if (clip == null) {
				swtClip = null;
			} else {
				swtClip = new Path(gc.getDevice(), scale(clip));
			}
			gc.setClipping(swtClip);
			dirtyClip = false;
		}

		if (dirtyLineAttributes) {
			LineAttributes copy = new LineAttributes(lineAttributes.width * PF);
			copy.cap = lineAttributes.cap;
			if (lineAttributes.dash != null) {
				copy.dash = new float[lineAttributes.dash.length];
				for (int i = 0; i < lineAttributes.dash.length; i++) {
					copy.dash[i] = lineAttributes.dash[i] * PF;
				}
			} else {
				copy.dash = null;
			}
			copy.dashOffset = lineAttributes.dashOffset * PF;
			copy.join = lineAttributes.join;
			copy.miterLimit = lineAttributes.miterLimit * PF;
			copy.style = lineAttributes.style;

			gc.setLineAttributes(copy);
			dirtyLineAttributes = false;
		}

	}

	/**
	 * Dispose the wrapper. This does not dispose the underlying GC.
	 */
	public void dispose() {
		if (disposed)
			return;

		gc.setTransform(baseTransform);
		baseTransform.dispose();
		baseTransform = null;

		gc.setClipping(baseClip);
		baseClip.dispose();
		baseClip = null;

		gc.setForeground(baseForeground);
		baseForeground = null;
		gc.setBackground(baseBackground);
		baseBackground = null;
		gc.setFont(baseFont);
		baseFont = null;
		gc.setLineAttributes(baseAttributes);
		baseAttributes = null;

		gc.setAdvanced(baseAdvanced);
		gc.setAntialias(baseAntialias);
		gc.setTextAntialias(baseTextAntialias);
		gc.setInterpolation(baseInterpolation);

		fontCache.dispose();
		fontCache = null;

		if (swtColor != null) {
			swtColor.dispose();
			swtColor = null;
		}
		if (swtTransform != null) {
			swtTransform.dispose();
			swtTransform = null;
		}
		if (swtClip != null) {
			swtClip.dispose();
			swtClip = null;
		}

		disposed = true;
		gc = null;
	}

	/**
	 * Check whether the wrapper is disposed.
	 * 
	 * @return true if the wrapper is disposed, false otherwise.
	 */
	public boolean isDisposed() {
		return disposed;
	}

	/**
	 * Set the current transformation (on top of the initial transformation that
	 * was already applied at the time of the wrapper's creation).
	 * 
	 * @param transformValues
	 *            new transformation. Set to null to restore initial
	 *            transformation.
	 */
	public void setTransform(float[] transformValues) {
		if (transformValues == null && transform != null) {
			transform = null;
			dirtyTransform = true;
		} else if (transform == null || !Arrays.equals(transform, transformValues)) {
			transform = transformValues;
			dirtyTransform = true;
		}
	}

	/**
	 * Set the color.
	 * 
	 * @param rgba
	 *            color.
	 */
	public void setColor(RGBA rgba) {
		if (color == null || !color.equals(rgba)) {
			color = rgba;
			dirtyColor = true;
		}
	}

	/**
	 * Set the Base14 font name and size.
	 * 
	 * @param name
	 *            Base14 font name.
	 * @param size
	 *            font size.
	 */
	public void setFont(String name, int size) {
		if (fontName == null || !fontName.equals(name) || fontSize != size) {
			fontName = name;
			fontSize = size;
			dirtyFont = true;
		}
	}

	/**
	 * Set the clipping area.
	 * 
	 * @param data
	 *            clipping path data.
	 */
	public void setClipping(PathData data) {
		if (clip == data)
			return;
		if (clip != null && data == null) {
			clip = null;
			dirtyClip = true;
		} else if (clip == null || !Arrays.equals(clip.points, data.points) || !Arrays.equals(clip.types, data.types)) {
			clip = data;
			dirtyClip = true;
		}
	}

	/**
	 * Set the line attributes.
	 * 
	 * @param attributes
	 *            line attributes.
	 */
	public void setLineAttributes(LineAttributes attributes) {
		if (lineAttributes == null || !lineAttributes.equals(attributes)) {
			lineAttributes = attributes;
			dirtyLineAttributes = true;
		}
	}

	/**
	 * Fill a rectangle with the given bounds.
	 * 
	 * @param x
	 *            x.
	 * @param y
	 *            y.
	 * @param w
	 *            width.
	 * @param h
	 *            height.
	 */
	public void fillRectangle(float x, float y, float w, float h) {
		commit();
		gc.fillRectangle((int) (x * PF), (int) (y * PF), (int) (w * PF), (int) (h * PF));
	}

	/**
	 * Draw a line between the two given points.
	 * 
	 * @param x1
	 *            x of first point.
	 * @param y1
	 *            y of first point.
	 * @param x2
	 *            x of second point.
	 * @param y2
	 *            y of second point.
	 */
	public void drawLine(float x1, float y1, float x2, float y2) {
		commit();
		gc.drawLine((int) (x1 * PF), (int) (y1 * PF), (int) (x2 * PF), (int) (y2 * PF));
	}

	/**
	 * Draw a string at the given location.
	 * 
	 * @param s
	 *            string.
	 * @param x
	 *            x.
	 * @param y
	 *            y.
	 * @param transparent
	 *            true if no background should be displayed.
	 */
	public void drawString(String s, float x, float y, boolean transparent) {
		commit();
		gc.drawString(s, (int) (x * PF), (int) (y * PF), transparent);
	}

	/**
	 * Compute the string extend width.
	 * 
	 * @param s
	 *            string.
	 * @return width.
	 */
	public float stringExtentWidth(String s) {
		commit();
		return gc.stringExtent(s).x / PF;
	}

	/**
	 * Draw the given image at the given position.
	 * 
	 * @param image
	 *            image.
	 * @param x
	 *            x.
	 * @param y
	 *            y.
	 */
	public void drawImage(Image image, float x, float y) {
		commit();
		Rectangle b = image.getBounds();

		// Disable clipping (not sure whether there is a bug with AWT or FOP but
		// images are truncated when
		// honoring clipping requests while drawing images)
		Region region = new Region(gc.getDevice());
		try {
			gc.getClipping(region);
			gc.drawImage(image, b.x, b.y, b.width, b.height, (int) (x * PF), (int) (y * PF), (int) (b.width * PF),
			        (int) (b.height * PF));
			gc.setClipping(region);
		} finally {
			region.dispose();
		}
	}

	/**
	 * Fill the given path.
	 * 
	 * @param data
	 *            path data.
	 */
	public void fillPath(PathData data) {
		commit();
		Path path = new Path(gc.getDevice(), scale(data));
		try {
			gc.fillPath(path);
		} finally {
			path.dispose();
		}
	}

	/**
	 * Get the underlying device.
	 * 
	 * @return underlying device.
	 */
	public Device getDevice() {
		return gc.getDevice();
	}

}
