# fop-swtrenderer
An SWT renderer for FOP. It is mostly based on existing FOP's AWTRenderer from version 2.0.

Compared to AWTRenderer, it has the following limitations:
  * Only the Base14 fonts and their platform-specific variations are supported. Neither the AWT nor the SWT fonts will be recognized;
  * Font glyph offsets are not supported;
  * Images coming from AWT's Graphics2D are not supported;
  * Clipping requests are not honored as it appears SWT's clipping is quite different than AWT's. 

For anything else, it is probably *good enough* for any practical use.

Using the SWTRenderer is quite similar to using the AWTRenderer: it implements an SWT variation of a Pageable object, allowing to render pages to specific GC's. The actual rendering is done lazily, i.e. no actual rendering will take place during the creation of the Pageable object itself but will be deferred until actually rendered to a GC.

Initial GC's transformation matrix and clipping will be honored, and as such no scaling nor rendering area need to be specified. DPI is taken into account and scaling is done appropriately, meaning that in practice no transformation matrix should be needed for either screen or printer rendering.

Bug reports or inquiries: ploufATpdtyDOTbe
