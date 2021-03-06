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

/**
 * The SWTPageable encapsulates a collection of pages that can be rendered to a
 * GC.
 */
public interface Pageable {
    /**
     * Get the number of pages.
     * 
     * @return page count.
     */
    public int getNumberOfPages();

    /**
     * Get the page format at the given 0-based page index.
     * 
     * @param pageIndex 0-based page index.
     * @return page format.
     */
    public PageFormat getPageFormat(int pageIndex);

    /**
     * Get a printable for the given 0-based page index.
     * 
     * @param pageIndex 0-based page index.
     * @return printable.
     */
    public Printable getPrintable(int pageIndex);
}
