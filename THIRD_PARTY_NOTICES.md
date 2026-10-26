# Third-party notices

## Font Awesome Free 6.7.2 — Solid icons

Copyright 2024 Fonticons, Inc. https://fontawesome.com

The selected SVG icons are licensed under Creative Commons Attribution 4.0
International (CC BY 4.0): https://creativecommons.org/licenses/by/4.0/.
Upstream source: https://github.com/FortAwesome/Font-Awesome/tree/6.7.2/svgs/solid.
The original attribution comments are retained in each vector resource.

RAPOPLAYER converts the paths to Android VectorDrawables, centers them in square
viewports without distorting their geometry, and applies the application's tint.
Only Free Solid icons are used; no Font Awesome fonts, JavaScript, brand icons or
Pro assets are bundled. The upstream license is included in
[fontawesome-free-license.txt](app/src/main/assets/licenses/fontawesome-free-license.txt)
and is available offline in Settings → About and diagnostics → Icon licenses.

The optional `python scripts/vendor-fontawesome.py` maintenance command recreates
the selected assets from the pinned upstream release. Gradle and icon rendering
do not run this command or download icons. See `RapoGlyph` for the application map.
