import re,sys,xml.etree.ElementTree as ET
ICONS='/tmp/claude-0/-home-user-NavOS/398f798d-0041-5015-bc50-511ac0037b43/scratchpad/icons'
RES='/home/user/NavOS/app/src/main/res/drawable/'
A='{http://schemas.android.com/apk/res/android}'
sys.path.insert(0,ICONS)
src=open(ICONS+'/pair.py').read()
PETROL=re.search(r"PETROL='''(.*?)'''",src,re.S).group(1)
EV=re.search(r"EV='''(.*?)'''",src,re.S).group(1)
MAP={'ic_search':'search','ic_settings':'settings','ic_close':'x','ic_arrow_back':'arrow-left','ic_add':'plus','ic_check':'check',
'ic_clear_circle':'circle-x','ic_history':'history','ic_home':'house','ic_work':'briefcase','ic_fuel':PETROL,'ic_ev':EV,
'ic_recenter':'locate-fixed','ic_volume':'volume-2','ic_volume_off':'volume-x','ic_alt_route':'route','ic_turn_list':'list','ic_alert_camera':'cctv'}
def f(x): return float(x)
def shapes(svgbody):
    root=ET.fromstring('<svg xmlns="http://www.w3.org/2000/svg">'+svgbody+'</svg>')
    out=[]
    for e in root.iter():
        t=e.tag.split('}')[-1]
        if t=='path': out.append(e.get('d'))
        elif t=='line': out.append(f"M{e.get('x1')} {e.get('y1')}L{e.get('x2')} {e.get('y2')}")
        elif t=='circle':
            cx,cy,r=f(e.get('cx')),f(e.get('cy')),f(e.get('r'))
            out.append(f"M{cx-r:g} {cy:g}a{r:g} {r:g} 0 1 0 {2*r:g} 0a{r:g} {r:g} 0 1 0 {-2*r:g} 0")
        elif t=='rect':
            x,y,w,h=f(e.get('x','0')),f(e.get('y','0')),f(e.get('width')),f(e.get('height'))
            rx=f(e.get('rx',e.get('ry','0')))
            if rx<=0: out.append(f"M{x:g} {y:g}h{w:g}v{h:g}h{-w:g}z")
            else: out.append(f"M{x+rx:g} {y:g}h{w-2*rx:g}a{rx:g} {rx:g} 0 0 1 {rx:g} {rx:g}v{h-2*rx:g}a{rx:g} {rx:g} 0 0 1 {-rx:g} {rx:g}h{-(w-2*rx):g}a{rx:g} {rx:g} 0 0 1 {-rx:g} {-rx:g}v{-(h-2*rx):g}a{rx:g} {rx:g} 0 0 1 {rx:g} {-rx:g}z")
        elif t in('polyline','polygon'):
            pts=re.split(r'[\s,]+',e.get('points').strip()); pairs=[pts[i]+' '+pts[i+1] for i in range(0,len(pts),2)]
            out.append('M'+'L'.join(pairs)+('z' if t=='polygon' else ''))
    return out
for name,ref in MAP.items():
    old=open(RES+name+'.xml').read()
    w=re.search(r'android:width="([^"]+)"',old).group(1); h=re.search(r'android:height="([^"]+)"',old).group(1)
    tint=re.search(r'android:tint="([^"]+)"',old)
    colors=[c for c in re.findall(r'android:(?:fillColor|strokeColor)="([^"]+)"',old) if c!='#00000000']
    color=colors[0]
    body=ref if ref.lstrip().startswith('<') else re.sub(r'<!--.*?-->|<svg[^>]*>|</svg>','',open(f'{ICONS}/lucide/{ref}.svg').read(),flags=re.S)
    paths=''.join(f'''
    <path
        android:pathData="{d}"
        android:strokeColor="{color}"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />''' for d in shapes(body))
    src_note='custom, Lucide style' if not ref.startswith('<') is False and ref.lstrip().startswith('<') else f'Lucide "{ref}" (ISC)'
    xml=f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {src_note}. 24 grid, 2px round stroke. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{w}"
    android:height="{h}"
    android:viewportWidth="24"
    android:viewportHeight="24"{f'{chr(10)}    android:tint="{tint.group(1)}"' if tint else ''}>{paths}
</vector>
'''
    open(RES+name+'.xml','w').write(xml)
    print(name, len(shapes(body)),'paths', color)
