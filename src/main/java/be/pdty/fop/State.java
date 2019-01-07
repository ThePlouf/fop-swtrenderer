/*
 * Copyright 2018 Philippe Detournay
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
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.geom.NoninvertibleTransformException;

/**
 * Keeps information about the current state of the SWTRenderer. This class is
 * not linked to any Device in particular. The update and combine methods will
 * not act on anything and will merely "remember" the requested state to be
 * into.
 * 
 * This class feels a little bit "weird" in the sense that it mixes AWT and SWT
 * concepts. The rationale is that a lot of input we will received from the rest
 * of the FOP infrastructure in the SWTRenderer will be AWT, and we also want to
 * take advantage of composition operations such as path building and matrix
 * operations which are not that easily available via SWT.
 */
public class State {
	private Color color;
	private AffineTransform transform;
	private String fontName;
	private int fontSize;

	private BasicStroke stroke;
	private Area clip;

	/**
	 * Create a new default, empty state.
	 */
	public State() {
		transform = new AffineTransform();
	}

	/**
	 * Copy constructor.
	 * 
	 * @param org
	 *            the instance to copy
	 */
	public State(State org) {
		color = org.color;
		transform = org.transform;
		fontName = org.fontName;
		fontSize = org.fontSize;
		clip = org.clip;
		stroke = org.stroke;
	}

	/**
	 * Configure the given GCWrapper according to the requested state.
	 * 
	 * @param gc
	 *            GCWrapper to configure.
	 */
	public void configureGC(GCWrapper gc) {
	  gc.setColor(Convert.toRGBA(color));

		if (fontName != null) {
			gc.setFont(fontName, fontSize);
		}

		gc.setTransform(Convert.toFloatArray(transform));
		if (clip == null) {
			gc.setClipping(null);
		} else {
		  gc.setClipping(Convert.toPathData(clip));
		}
		gc.setLineAttributes(Convert.toLineAttributes(stroke));
	}

	/**
	 * Update the foreground color.
	 * 
	 * @param col
	 *            new color.
	 */
	public void updateColor(Color col) {
		color = col;
	}

	/**
	 * Update the Base14 font.
	 * 
	 * @param name
	 *            Base14 font name.
	 * @param size
	 *            font size.
	 */
	public void updateFont(String name, int size) {
		fontName = name;
		fontSize = size;
	}

	/**
	 * Update the stroke.
	 * 
	 * @param baseStroke
	 *            stroke.
	 */
	public void updateStroke(BasicStroke baseStroke) {
		stroke = baseStroke;
	}

	/**
	 * Close and combine the given path with the current clipping area.
	 * 
	 * @param cl
	 *            clipping area.
	 */
	public void combineClip(GeneralPath cl) {
		if (clip != null) {
			clip = new Area(clip);
			clip.intersect(new Area(cl));
		} else {
			clip = new Area(cl);
		}
	}

	/**
	 * Combine the current transformation with the given one.
	 * 
	 * @param tf
	 *            transform to add.
	 */
	public void combineTransform(AffineTransform tf) {
	  if(tf.getDeterminant()==0.0) {
	    //We will refuse this transformation...
	    return;
	  }
    try
    {
      if(clip!=null) {
        clip.transform(transform);
  	  }
  		transform = new AffineTransform(transform);
  		transform.concatenate(tf);
  		if(clip!=null) {
  		  clip.transform(transform.createInverse());
  		}
		}
    catch(NoninvertibleTransformException ex)
    {
      //Not supposed to happen as we refuse non-invertible matrices...
      throw new RuntimeException(ex);
    }
	}
}
