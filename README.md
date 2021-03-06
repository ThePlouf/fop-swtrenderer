# fop-swtrenderer
An SWT renderer for FOP. It is mostly based on existing FOP's AWTRenderer from version 2.0.

Compared to AWTRenderer, it has the following limitations:
  * Only the Base14 fonts and their platform-specific variations are supported. Neither the AWT nor the SWT fonts will be recognized;
  * Font glyph offsets are not supported;
  * Images coming from AWT's Graphics2D are not supported.

It brings interesting features, however:
  * All geometries such as table borders are merged together and rasterized at once, thus avoiding any "gaps" especially at table corners;
  * Several optimizations to coalesce consecutive text nodes such as text leaders bring significant performance gains;
  * Advanced underlining logic combines several consecutive text does to find the best position and height for the line instead of rendering it word per word;
  * Underlining is done using a "skip ink" logic that avoid having the underline crossing the character's descenders;
  * Some bugfixes were implemented on top of the AWT renderer.

For anything else, it is probably *good enough* for any practical use.

Using the SWTRenderer is quite similar to using the AWTRenderer: it implements an SWT variation of a Pageable object, allowing to render pages to specific GC's. The actual rendering is done lazily, i.e. no actual rendering will take place during the creation of the Pageable object itself but will be deferred until actually rendered to a GC.

Both initial GC's transformation matrix and clipping area will be honored, and as such no scaling or rendering area need to be specified. DPI is taken into account and scaling is done appropriately, meaning that in practice no transformation matrix should be needed for either screen or printer rendering.

Note that, no matter what, any GC property will be restored to its initial value after the rendering is done.

Bug reports or inquiries: ploufATpdtyDOTbe
