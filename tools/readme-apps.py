#!/usr/bin/env python3
"""Rebuilds the README's application list from catalog.json and icons/color.

Run it after adding an application or an icon:  python3 tools/readme-apps.py
The list sits between the <!-- apps --> and <!-- /apps --> markers in README.md.
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, 'app/src/main/assets/catalog.json')
README = os.path.join(ROOT, 'README.md')
COLS = 6

# Where each name links: the project's page where it's open source, otherwise the site.
LINKS = {
    'openhamclock': 'https://github.com/accius/openhamclock',
    'hamclock': 'https://www.clearskyinstitute.com/ham/HamClock/',
    'satnogs': 'https://github.com/satnogs',
    'tinygs': 'https://github.com/tinygs/tinyGS',
    'meshmonitor': 'https://github.com/Yeraze/meshmonitor',
    'meshmap': 'https://github.com/brianshea2/meshmap.net',
    'meshcoremap': 'https://github.com/meshcore-dev/map.meshcore.io',
    'meshtasticnode': 'https://github.com/meshtastic/firmware',
    'adsblol': 'https://github.com/adsblol',
    'adsbfi': 'https://github.com/adsbfi',
    'tar1090': 'https://github.com/wiedehopf/tar1090',
    'aiscatcher': 'https://github.com/jvde-github/AIS-catcher',
}


def cell(app):
    have = os.path.exists(os.path.join(ROOT, 'icons/color', app['id'] + '.png'))
    icon = 'icons/color/%s.png' % (app['id'] if have else 'custom')
    link = LINKS.get(app['id']) or app.get('url', '')
    name = app['name']
    img = '<img src="%s" width="64" height="64" alt="%s"/>' % (icon, name)
    if link:
        img = '<a href="%s">%s</a>' % (link, img)
        label = '<a href="%s">%s</a>' % (link, name)
    else:
        label = name
    return '<td align="center" width="110">%s<br/><sub>%s</sub></td>' % (img, label)


def build():
    catalog = json.load(open(CATALOG))
    out = []
    for category in catalog['categories']:
        apps = [a for a in catalog['apps'] if a.get('category') == category]
        if not apps:
            continue
        out.append('**%s**\n' % category)
        out.append('<table>')
        for i in range(0, len(apps), COLS):
            out.append('<tr>' + ''.join(cell(a) for a in apps[i:i + COLS]) + '</tr>')
        out.append('</table>\n')
    out.append('Names starting with "My" or "your own" are for your own server or device. '
               'Applications without their own icon yet show the generic one.')
    return '\n'.join(out)


def main():
    text = open(README).read()
    new = re.sub(r'<!-- apps -->.*?<!-- /apps -->', lambda m: '<!-- apps -->\n' + build() + '\n<!-- /apps -->',
                 text, flags=re.S)
    if new == text and '<!-- apps -->' not in text:
        raise SystemExit('README.md has no <!-- apps --> markers')
    open(README, 'w').write(new)


if __name__ == '__main__':
    main()
