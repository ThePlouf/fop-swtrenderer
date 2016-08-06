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

import org.eclipse.swt.graphics.GC;

/**
 * Printable.
 */
public interface Printable {
	/**
	 * Render the printable to the given GC. If there is a transformation set on
	 * to the GC then it will be applied to the rendering. However this method
	 * does not honor existing clipping region and draws over the entire GC.
	 * This method returns any modified GC attribute by the time it returns.
	 * 
	 * @param gc
	 *            target GC.
	 */
	public void print(GC gc);
}
