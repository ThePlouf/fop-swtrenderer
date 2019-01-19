/*
 * Copyright 2018 Philippe Detournay
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Device;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;

/**
 * The Base14FontProvider acts as both a factory and a cache, mapping the Base14
 * fonts to SWT fonts. Because it acts as a cache for the fonts, instances of
 * this class must be disposed once finished with.
 */
@SuppressWarnings("nls")
public class Base14FontProvider {
    /**
     * Font information.
     */
    public static class FontInfo {
        /**
         * System name.
         */
        public String name;
        /**
         * SWT style.
         */
        public int style;

        /**
         * Create a new FontInfo.
         * 
         * @param n system name.
         * @param s SWT style.
         */
        public FontInfo(String n, int s) {
            name = n;
            style = s;
        }
    }

    private boolean disposed;
    private Device device;
    private Map<String, Font> fonts;
    private Map<String, FontInfo> infos;

    /**
     * Create a new Base14FontProvider for the given Device.
     * 
     * @param dev device.
     */
    public Base14FontProvider(Device dev) {
        device = dev;
        fonts = new HashMap<>();
        infos = new HashMap<>();

        FontData[] datas = device.getFontList(null, true);
        Set<String> fontSet = new HashSet<>();
        for (FontData data : datas) {
            if (data.getStyle() == SWT.NORMAL) {
                fontSet.add(data.getName());
            }
        }

        String systemName;

        // Times
        systemName = "Times";
        if (!fontSet.contains(systemName))
            systemName = "Times New Roman";
        if (!fontSet.contains(systemName))
            systemName = "Times Roman";
        if (!fontSet.contains(systemName))
            systemName = "Times-Roman";
        if (!fontSet.contains(systemName))
            systemName = "serif";
        if (!fontSet.contains(systemName))
            systemName = "Times";
        infos.put("Times-Roman", new FontInfo(systemName, SWT.NORMAL));
        infos.put("Times-Italic", new FontInfo(systemName, SWT.ITALIC));
        infos.put("Times-Bold", new FontInfo(systemName, SWT.BOLD));
        infos.put("Times-BoldItalic", new FontInfo(systemName, SWT.ITALIC | SWT.BOLD));

        // Helvetica
        systemName = "Helvetica";
        if (!fontSet.contains(systemName))
            systemName = "Arial";
        if (!fontSet.contains(systemName))
            systemName = "sans-serif";
        if (!fontSet.contains(systemName))
            systemName = "SansSerif";
        if (!fontSet.contains(systemName))
            systemName = "Helvetica";
        infos.put("Helvetica", new FontInfo(systemName, SWT.NORMAL));
        infos.put("Helvetica-Oblique", new FontInfo(systemName, SWT.ITALIC));
        infos.put("Helvetica-Bold", new FontInfo(systemName, SWT.BOLD));
        infos.put("Helvetica-BoldOblique", new FontInfo(systemName, SWT.ITALIC | SWT.BOLD));

        // Courier
        systemName = "Courier";
        if (!fontSet.contains(systemName))
            systemName = "Courier New";
        if (!fontSet.contains(systemName))
            systemName = "monospace";
        if (!fontSet.contains(systemName))
            systemName = "Monospaced";
        if (!fontSet.contains(systemName))
            systemName = "Courier";
        infos.put("Courier", new FontInfo(systemName, SWT.NORMAL));
        infos.put("Courier-Oblique", new FontInfo(systemName, SWT.ITALIC));
        infos.put("Courier-Bold", new FontInfo(systemName, SWT.BOLD));
        infos.put("Courier-BoldOblique", new FontInfo(systemName, SWT.ITALIC | SWT.BOLD));

        // Symbol
        systemName = "Symbol";
        infos.put("Symbol", new FontInfo(systemName, SWT.NORMAL));

        // ZapfDingbats
        systemName = "ZapfDingbats";
        infos.put("ZapfDingbats", new FontInfo(systemName, SWT.NORMAL));
    }

    /**
     * Dispose this instance. Any font that was previously returned by this
     * instance will also be disposed and should not be used anymore.
     */
    public void dispose() {
        if (disposed)
            return;
        for (Font f : fonts.values()) {
            f.dispose();
        }
        fonts.clear();
        disposed = true;
    }

    /**
     * Get the device this provider is bound to.
     * 
     * @return device.
     */
    public Device getDevice() {
        return device;
    }

    /**
     * Check whether this instance has already been disposed or not.
     * 
     * @return true if instance is disposed, false otherwise.
     */
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Get the closest possible SWT font for the given Base14 font. The returned
     * font will be cached and should not be disposed. It will be disposed when
     * this provider instance gets disposed.
     * 
     * @param name Base14 font name.
     * @param size font size.
     * @return SWT font.
     */
    public Font getFont(String name, int size) {
        String key = name + ":" + size;

        Font ans = fonts.get(key);
        if (ans != null)
            return ans;

        FontInfo nfo = getFontInfo(name);

        ans = new Font(device, nfo.name, size, nfo.style);
        fonts.put(key, ans);

        return ans;
    }

    /**
     * Get the information (system name and style) for the given Base14 font
     * name.
     * 
     * @param name Base14 font name.
     * @return font information.
     */
    public FontInfo getFontInfo(String name) {
        String systemName = "";
        int style = SWT.NORMAL;

        FontInfo info = infos.get(name);
        if (info != null) {
            systemName = info.name;
            style = info.style;
        } else {
            systemName = name;
            if (name.contains("Oblique") || name.contains("Italic"))
                style |= SWT.ITALIC;
            if (name.contains("Bold") || name.contains("Strong"))
                style |= SWT.BOLD;
        }

        return new FontInfo(systemName, style);
    }

}
