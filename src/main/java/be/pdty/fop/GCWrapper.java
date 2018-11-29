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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
 * The wrapper will also "adopt" the clipping area at the time of creation and
 * will treat it as the maximum area for issuing drawing requests. Any
 * additional clipping request will be combined (intersected) with this base
 * area.
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
	private boolean dirtyTransform;

	private PathData clip;
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
		dirtyTransform = false;

		clip = null;
		dirtyClip = false;
	}

	private static PathData scale(PathData data) {
		float[] points = new float[data.points.length];
		for (int i = 0; i < data.points.length; i++) {
			points[i] = data.points[i] * PF;
		}
		PathData ans = new PathData();
		ans.points = points;
		ans.types = data.types;
		return ans;
	}

	private static void transformPath(PathData data, Transform transform) {
		float[] f = new float[2];
		for (int i = 0; i < data.points.length; i += 2) {
			f[0] = data.points[i];
			f[1] = data.points[i + 1];
			transform.transform(f);
			data.points[i] = f[0];
			data.points[i + 1] = f[1];
		}
	}

	private static void pathToRegion(PathData data, Region region) {
		List<Integer> points = new ArrayList<Integer>();
		int offset = 0;
		for (byte type : data.types) {
			switch (type) {
			case SWT.PATH_MOVE_TO:
				if (points.size() >= 2) {
					int[] pts = new int[points.size()];
					for (int i = 0; i < pts.length; i++) {
						pts[i] = points.get(i);
					}
					region.add(pts);
				}
				points.clear();
				points.add((int) data.points[offset++]);
				points.add((int) data.points[offset++]);
				break;
			case SWT.PATH_LINE_TO:
				points.add((int) data.points[offset++]);
				points.add((int) data.points[offset++]);
				break;
			case SWT.PATH_QUAD_TO: {
				float x0 = 0.0f;
				float y0 = 0.0f;
				if (offset >= 2) {
					x0 = data.points[offset - 2];
					y0 = data.points[offset - 1];
				}
				float x1 = data.points[offset++];
				float y1 = data.points[offset++];
				float x2 = data.points[offset++];
				float y2 = data.points[offset++];

				float distance = (float) (Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
				        + Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)));

				int segments = (int) (distance / 5);
				if (segments < 1)
					segments = 1;

				for (int i = 1; i <= segments; i++) {
					float t = 1.0f * i / segments;
					float rt = 1.0f - t;
					float a = rt * rt;
					float b = 2 * rt * t;
					float c = t * t;
					int x = (int) (a * x0 + b * x1 + c * x2);
					int y = (int) (a * y0 + b * y1 + c * y2);
					points.add(x);
					points.add(y);
				}
				break;
			}
			case SWT.PATH_CUBIC_TO: {
				float x0 = 0.0f;
				float y0 = 0.0f;
				if (offset >= 2) {
					x0 = data.points[offset - 2];
					y0 = data.points[offset - 1];
				}
				float x1 = data.points[offset++];
				float y1 = data.points[offset++];
				float x2 = data.points[offset++];
				float y2 = data.points[offset++];
				float x3 = data.points[offset++];
				float y3 = data.points[offset++];

				float distance = (float) (Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
				        + Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
				        + Math.sqrt((x3 - x2) * (x3 - x2) + (y3 - y2) * (y3 - y2)));

				int segments = (int) (distance / 5);
				if (segments < 1)
					segments = 1;

				for (int i = 1; i <= segments; i++) {
					float t = 1.0f * i / segments;
					float rt = 1.0f - t;
					float a = rt * rt * rt;
					float b = 3 * rt * rt * t;
					float c = 3 * rt * t * t;
					float d = t * t * t;
					int x = (int) (a * x0 + b * x1 + c * x2 + d * x3);
					int y = (int) (a * y0 + b * y1 + c * y2 + d * y3);
					points.add(x);
					points.add(y);
				}
				break;
			}
			case SWT.PATH_CLOSE:
				if (points.size() >= 2) {
					int[] pts = new int[points.size() + 2];
					for (int i = 0; i < pts.length - 2; i++) {
						pts[i] = points.get(i);
					}
					pts[pts.length - 2] = points.get(0);
					pts[pts.length - 1] = points.get(1);
					region.add(pts);
				}
				points.clear();
				break;
			}
		}
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
			Transform swtTransform = new Transform(gc.getDevice());
			Transform userTransform = transform == null ? new Transform(gc.getDevice())
			        : new Transform(gc.getDevice(), transform);
			try {
				swtTransform.multiply(baseTransform);
				swtTransform.scale(sx, sy);
				swtTransform.multiply(userTransform);
				swtTransform.scale(1.0f / PF, 1.0f / PF);

				gc.setTransform(swtTransform);
				dirtyTransform = false;
			} finally {
				userTransform.dispose();
				swtTransform.dispose();
			}
		}

		if (dirtyClip) {
			// Okay so this is quite complicated. We will try to merge the base
			// clipping area with the requested area, but each of those are
			// expressed using different transformation matrixes so we need to
			// be careful not to get confused...

			// Note: one could believe it would have been much easier to just
			// use a combination of setClipping/getClipping to transform from
			// path to region, as well as to use
			// setClipping/setTransform/getClipping to perform the
			// transformation, unfortunately there seems to be some issues with
			// getClipping on MacOSX that makes it run extremely slowly (several
			// seconds per single call), so the following approach is used
			// instead.

			Transform currentTransform = new Transform(gc.getDevice());
			try {
				// Let's save the current transform as we will need to restore
				// it back
				gc.getTransform(currentTransform);
				if (clip != null) {
					Region newRegion = new Region(gc.getDevice());
					Transform currentToBase = new Transform(gc.getDevice());
					try {
						// We will express the current requested clip in the
						// base transform so that we can merge it with the base
						// clipping area.

						// First we scale the requested path
						PathData scaled = scale(clip);

						// Then we build a "current transform to base transform"
						// matrix
						gc.getTransform(currentToBase);
						currentToBase.invert();
						currentToBase.multiply(baseTransform);
						currentToBase.invert();

						// We now express the requested clip path in the current
						// base, then convert it as a region
						transformPath(scaled, currentToBase);
						pathToRegion(scaled, newRegion);

						// Good! Now intersect this region with the base
						// clipping area
						newRegion.intersect(baseClip);

						// Set this clipping, using the base transform
						gc.setTransform(baseTransform);
						gc.setClipping(newRegion);

						// And finally, move back to current transform
						gc.setTransform(currentTransform);
					} finally {
						currentToBase.dispose();
						newRegion.dispose();
					}
				} else {
					gc.setTransform(baseTransform);
					gc.setClipping(baseClip);
					gc.setTransform(currentTransform);
				}
			} finally {
				currentTransform.dispose();
			}

			dirtyClip = false;
		}

		if (dirtyLineAttributes) {
			LineAttributes copy = new LineAttributes(lineAttributes.width * PF);
			copy.cap = lineAttributes.cap;
			if (lineAttributes.dash != null) {
				copy.dash = new float[lineAttributes.dash.length];
				for (int i = 0; i < lineAttributes.dash.length; i++) {
					copy.dash[i] = lineAttributes.dash[i] * PF;
					if(copy.dash[i]<=0) copy.dash[i]=1;
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
   * Draw a rectangle with the given bounds.
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
  public void drawRectangle(float x, float y, float w, float h) {
    commit();
    gc.drawRectangle((int) (x * PF), (int) (y * PF), (int) (w * PF), (int) (h * PF));
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
		// images are truncated when honoring clipping requests while drawing
		// images)
		Transform currentTransform = new Transform(gc.getDevice());
		try {
			// Save the current clipping region and transform
			gc.getTransform(currentTransform);

			// Set the original clipping region using the original transform
			gc.setTransform(baseTransform);
			gc.setClipping(baseClip);

			// Switch back to the current transform
			gc.setTransform(currentTransform);

			// The actual drawing
			gc.drawImage(image, b.x, b.y, b.width, b.height, (int) (x * PF), (int) (y * PF), (int) (b.width * PF),
			        (int) (b.height * PF));

			// Request to reset clipping at next commit
			dirtyClip = true;
		} finally {
			currentTransform.dispose();
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
