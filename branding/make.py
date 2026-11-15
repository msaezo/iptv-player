import asyncio, sys
from playwright.async_api import async_playwright

SW = 62  # stroke width
def T(x, y, w=180, h=260): return f'<path d="M{x} {y} H{x+w} M{x+w/2} {y} V{y+h}"/>'
def U(x, y, w=170, h=260):
    r = w/2
    return f'<path d="M{x} {y} V{y+h-r} A{r} {r} 0 0 0 {x+w} {y+h-r} V{y}"/>'
def V(x, y, w=190, h=260): return f'<path d="M{x} {y} L{x+w/2} {y+h} L{x+w} {y}"/>'
def play(cx, cy, s): return f'<path d="M{cx-s*0.38} {cy-s*0.5} L{cx+s*0.55} {cy} L{cx-s*0.38} {cy+s*0.5} Z" fill="url(#g)" stroke="url(#g)" stroke-width="10" stroke-linejoin="round"/>'

DEFS = '''
<defs>
  <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#06222A"/><stop offset="0.5" stop-color="#0A0F1C"/><stop offset="1" stop-color="#1D1238"/>
  </linearGradient>
  <radialGradient id="glow1" cx="0.18" cy="0.2" r="0.6"><stop offset="0" stop-color="#2FD3C0" stop-opacity="0.35"/><stop offset="1" stop-color="#2FD3C0" stop-opacity="0"/></radialGradient>
  <radialGradient id="glow2" cx="0.85" cy="0.85" r="0.6"><stop offset="0" stop-color="#9B6BFF" stop-opacity="0.40"/><stop offset="1" stop-color="#9B6BFF" stop-opacity="0"/></radialGradient>
  <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#5EF2DA"/><stop offset="0.55" stop-color="#7FB2FF"/><stop offset="1" stop-color="#C08BFF"/>
  </linearGradient>
  <linearGradient id="w" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFFFFF"/><stop offset="1" stop-color="#D6E4EE"/>
  </linearGradient>
  <filter id="blur" filterUnits="userSpaceOnUse" x="-200" y="-200" width="1800" height="1500"><feGaussianBlur stdDeviation="22"/></filter>
</defs>'''

def banner():
    W, H = 1280, 720
    y = 0; gap = 100
    tx1 = 0; ux = tx1 + 180 + gap; tx2 = ux + 170 + gap; vx = tx2 + 180 + gap
    total = vx + 190
    k = 0.86
    white = T(tx1, y) + U(ux, y)
    grad = T(tx2, y) + V(vx, y)
    pl = play(ux + 77, y + 122, 80)
    tr = f'translate({W/2} {H/2 - 20}) scale({k}) translate({-total/2} {-130})'
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">{DEFS}
<rect width="{W}" height="{H}" fill="url(#bg)"/>
<rect width="{W}" height="{H}" fill="url(#glow1)"/><rect width="{W}" height="{H}" fill="url(#glow2)"/>
<circle cx="{W*0.86}" cy="{H*0.12}" r="300" fill="none" stroke="#ffffff" stroke-opacity="0.05" stroke-width="2"/>
<circle cx="{W*0.86}" cy="{H*0.12}" r="390" fill="none" stroke="#ffffff" stroke-opacity="0.035" stroke-width="2"/>
<g transform="{tr}">
<g fill="none" stroke="url(#g)" stroke-width="{SW}" stroke-linecap="round" stroke-linejoin="round" opacity="0.55" filter="url(#blur)">{grad}</g>
<g fill="none" stroke="url(#w)" stroke-width="{SW}" stroke-linecap="round" stroke-linejoin="round">{white}</g>
<g fill="none" stroke="url(#g)" stroke-width="{SW}" stroke-linecap="round" stroke-linejoin="round">{grad}</g>
{pl}
</g>
<rect x="{W/2-80}" y="{H/2+150}" width="160" height="9" rx="4.5" fill="url(#g)" opacity="0.9"/>
</svg>'''

def icon():
    S = 1024
    s = 0.62  # letter scale
    def sc(svg): return svg
    y1, y2 = 205, 555
    x1, x2 = 250, 560
    white = T(x1, y1, 220, 260) + U(x2, y1, 210, 260)
    grad = T(x1, y2, 220, 260) + V(x2 - 6, y2, 222, 260)
    pl = play(x2 + 105, y1 + 122, 92)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{S}" height="{S}" viewBox="0 0 {S} {S}">{DEFS}
<rect width="{S}" height="{S}" rx="220" fill="url(#bg)"/>
<rect width="{S}" height="{S}" rx="220" fill="url(#glow1)"/><rect width="{S}" height="{S}" rx="220" fill="url(#glow2)"/>
<g transform="translate(512 512) scale(0.86) translate(-512 -540)">
<g fill="none" stroke="url(#g)" stroke-width="66" stroke-linecap="round" stroke-linejoin="round" opacity="0.5" filter="url(#blur)">{grad}</g>
<g fill="none" stroke="url(#w)" stroke-width="66" stroke-linecap="round" stroke-linejoin="round">{white}</g>
<g fill="none" stroke="url(#g)" stroke-width="66" stroke-linecap="round" stroke-linejoin="round">{grad}</g>
{pl}
</g>
</svg>'''

open('banner.svg','w').write(banner()); open('icon.svg','w').write(icon())
def wordmark():
    b = banner()
    import re
    b = re.sub(r'<rect width="1280" height="720" fill="url\(#(bg|glow1|glow2)\)"/>', '', b)
    b = re.sub(r'<circle[^>]*/>', '', b)
    return b

async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        pg = await b.new_page(viewport={'width':1280,'height':720})
        await pg.set_content(f'<html><body style="margin:0;background:transparent">{banner()}</body></html>')
        await pg.locator('svg').screenshot(path='banner.png')
        pg2 = await b.new_page(viewport={'width':1024,'height':1024})
        await pg2.set_content(f'<html><body style="margin:0;background:transparent">{icon()}</body></html>')
        await pg2.locator('svg').screenshot(path='icon.png', omit_background=True)
        pg3 = await b.new_page(viewport={'width':1280,'height':720})
        await pg3.set_content(f'<html><body style="margin:0;background:transparent">{wordmark()}</body></html>')
        await pg3.locator('svg').screenshot(path='wordmark.png', omit_background=True)
        await b.close()
asyncio.run(main())
print('done')
