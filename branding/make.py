import asyncio
from playwright.async_api import async_playwright
RED = "#E8384F"; RED2 = "#B5172E"
SW = 70
def T(x, y, w=176, h=260, sw=SW):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{sw}"/><rect x="{x+w/2-sw/2}" y="{y}" width="{sw}" height="{h}"/>'
def U(x, y, w=230, h=260, sw=SW):
    # x,y = outer top-left; drawn as stroke centred inside the outer box
    xi = x + sw/2; wi = w - sw; r = wi/2
    return f'<path d="M{xi} {y} V{y+h-sw/2-r} A{r} {r} 0 0 0 {xi+wi} {y+h-sw/2-r} V{y}" fill="none" stroke-width="{sw}" stroke-linecap="butt"/>'
def V(x, y, w=250, h=260, sw=SW):
    a = sw*1.05; b = sw*0.45; cx = x + w/2
    # inner apex: intersection of inner edges
    import math
    slope = (h) / (w/2 - b)          # outer edge slope (dy/dx)
    # inner edge is offset horizontally by a at the top; apex where they meet in the middle
    iy = y + slope * (w/2 - a)
    return f'<path d="M{x} {y} L{x+a} {y} L{cx} {iy} L{x+w-a} {y} L{x+w} {y} L{cx+b} {y+h} L{cx-b} {y+h} Z"/>'
def play(cx, cy, s, fill): return f'<path d="M{cx-s*0.38} {cy-s*0.5} L{cx+s*0.55} {cy} L{cx-s*0.38} {cy+s*0.5} Z" fill="{fill}" stroke="{fill}" stroke-width="6" stroke-linejoin="round"/>'
DEFS = f'''<defs>
<linearGradient id="red" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{RED}"/><stop offset="1" stop-color="{RED2}"/></linearGradient>
<radialGradient id="glow" cx="0.5" cy="1.05" r="0.75"><stop offset="0" stop-color="{RED}" stop-opacity="0.33"/><stop offset="1" stop-color="{RED}" stop-opacity="0"/></radialGradient>
</defs>'''
def word():
    tx1=0; ux=220; tx2=ux+230+44; vx=tx2+176+40
    total=vx+250
    g = (f'<g fill="#FFFFFF" stroke="#FFFFFF">{T(tx1,0)}{U(ux,0)}</g>'
         f'<g fill="{RED}" stroke="{RED}">{T(tx2,0)}{V(vx,0)}</g>' + play(ux+115-4, 108, 76, RED))
    return g, total
def banner(bg=True):
    W,H=1280,720
    g,total=word(); k=0.82
    tr=f'translate({W/2} {H/2}) scale({k}) translate({-total/2} {-130})'
    back = f'<rect width="{W}" height="{H}" fill="#0A0A0A"/><rect width="{W}" height="{H}" fill="url(#glow)"/>' if bg else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">{DEFS}{back}<g transform="{tr}">{g}</g></svg>'
def icon():
    S=1024
    sw=74
    x1,x2,y1,y2=232,490,215,565
    white=T(x1,y1,214,250,sw)+U(x2,y1,262,250,sw)
    red=T(x1,y2,214,250,sw)+V(x2,y2,262,250,sw)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{S}" height="{S}" viewBox="0 0 {S} {S}">{DEFS}
<rect width="{S}" height="{S}" rx="220" fill="#0A0A0A"/><rect width="{S}" height="{S}" rx="220" fill="url(#glow)"/>
<g transform="translate(512 512) scale(0.86) translate(-512 -540)">
<g fill="#FFFFFF" stroke="#FFFFFF">{white}</g><g fill="{RED}" stroke="{RED}">{red}</g>{play(x2+127, y1+104, 82, RED)}</g></svg>'''
async def main():
    async with async_playwright() as p:
        b = await p.chromium.launch()
        for name, svg, w, h, omit in [('banner', banner(), 1280, 720, False), ('icon', icon(), 1024, 1024, True), ('wordmark', banner(False), 1280, 720, True)]:
            pg = await b.new_page(viewport={'width': w, 'height': h})
            await pg.set_content(f'<html><body style="margin:0;background:transparent">{svg}</body></html>')
            await pg.locator('svg').screenshot(path=f'{name}.png', omit_background=omit)
            open(f'{name}.svg','w').write(svg)
        await b.close()
asyncio.run(main())
