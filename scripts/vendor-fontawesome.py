"""Refresh the bundled Free Solid subset. Optional maintenance; never run by Gradle."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import urllib.request
import xml.etree.ElementTree as ET
from xml.sax.saxutils import quoteattr

VERSION = '6.7.2'
BASE = f'https://raw.githubusercontent.com/FortAwesome/Font-Awesome/{VERSION}/'
ICONS = tuple('''arrow-left arrows-rotate backward-step broom calendar-days check
chevron-up chevron-down circle-dot circle-info clock-rotate-left closed-captioning compress expand eye
eye-slash file-lines forward-step gear key language link list-ul lock
magnifying-glass pause pen-to-square play plug server shield-halved star tag
trash-can tv unlock user volume-high xmark'''.split())


def vector(data: bytes, name: str) -> str:
    svg = ET.fromstring(data)
    x, y, width, height = map(float, svg.attrib['viewBox'].split())
    assert x == y == 0 and width > 0 and height > 0
    paths = svg.findall('{http://www.w3.org/2000/svg}path')
    assert paths and len(paths) == len(svg), 'Only static SVG paths are supported'
    credit = re.search(r'<!--(.*?)-->', data.decode(), re.S).group(1).strip()
    assert f'Font Awesome Free {VERSION}' in credit
    side = max(width, height)
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f'<!-- {credit} -->',
        f'<!-- Source: {BASE}svgs/solid/{name}.svg',
        '     Converted to Android VectorDrawable; centered in a square viewport. -->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp" android:height="24dp"',
        *(['    android:autoMirrored="true"'] if name == 'arrow-left' else []),
        f'    android:viewportWidth="{side:g}" android:viewportHeight="{side:g}">',
        f'    <group android:translateX="{(side-width)/2:g}" android:translateY="{(side-height)/2:g}">',
    ]
    for path in paths:
        lines.append('        <path android:fillColor="#FFFFFFFF" android:pathData=' + quoteattr(path.attrib['d']) + ' />')
    return '\n'.join(lines + ['    </group>', '</vector>', ''])


def main():
    root = Path(__file__).resolve().parents[1]
    names = ('LICENSE.txt',) + tuple(f'svgs/solid/{name}.svg' for name in ICONS)
    def fetch(name):
        with urllib.request.urlopen(BASE + name, timeout=40) as response:
            return name, response.read()
    # Fetch first, then validate and write only the selected local assets.
    with ThreadPoolExecutor(max_workers=6) as pool:
        upstream = dict(pool.map(fetch, names))
    converted = {name: vector(upstream[f'svgs/solid/{name}.svg'], name) for name in ICONS}
    license_text = upstream['LICENSE.txt']
    assert b'Icons: CC BY 4.0 License' in license_text
    drawables = root / 'app/src/main/res/drawable'
    licenses = root / 'app/src/main/assets/licenses'
    drawables.mkdir(parents=True, exist_ok=True)
    licenses.mkdir(parents=True, exist_ok=True)
    for name, xml in converted.items():
        (drawables / ('fa_' + name.replace('-', '_') + '.xml')).write_text(xml, encoding='utf-8', newline='\n')
    (licenses / 'fontawesome-free-license.txt').write_bytes(license_text)
    print(f'Bundled {len(converted)} Font Awesome Free {VERSION} Solid vectors and the upstream license.')


if __name__ == '__main__':
    main()
