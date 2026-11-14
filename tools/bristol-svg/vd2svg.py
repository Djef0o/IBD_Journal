import xml.etree.ElementTree as ET, sys, re
AND="{http://schemas.android.com/apk/res/android}"
def a(el, name):
    return el.get(f"{AND}{name}")
def group_transform(el):
    rot=float(a(el,"rotation") or 0); px=float(a(el,"pivotX") or 0); py=float(a(el,"pivotY") or 0)
    sx=float(a(el,"scaleX") or 1); sy=float(a(el,"scaleY") or 1)
    tx=float(a(el,"translateX") or 0); ty=float(a(el,"translateY") or 0)
    parts=[]
    if tx or ty: parts.append(f"translate({tx} {ty})")
    if rot: parts.append(f"translate({px} {py}) rotate({rot}) translate({-px} {-py})")
    if sx!=1 or sy!=1:
        if px or py: parts.append(f"translate({px} {py}) scale({sx} {sy}) translate({-px} {-py})")
        else: parts.append(f"scale({sx} {sy})")
    return " ".join(parts)
def convert_path(vp):
    attrs={"d": a(vp,"pathData") or ""}
    m={"fillColor":"fill","strokeColor":"stroke","strokeWidth":"stroke-width","strokeLineCap":"stroke-linecap","strokeLineJoin":"stroke-linejoin","strokeMiterLimit":"stroke-miterlimit","fillAlpha":"fill-opacity","strokeAlpha":"stroke-opacity","fillType":"fill-rule"}
    for k,v in m.items():
        val=a(vp,k)
        if val is not None:
            if k=="fillType": val="evenodd" if val.lower()=="evenodd" else "nonzero"
            attrs[v]=val
    if "fill" not in attrs: attrs["fill"]="none"
    return attrs
def walk(vnode, snode):
    for child in vnode:
        tag=child.tag.split("}")[-1]
        if tag=="path":
            ET.SubElement(snode,"path",convert_path(child))
        elif tag=="group":
            g=ET.SubElement(snode,"g")
            if child.get(f"{AND}name"): g.set("id",a(child,"name"))
            t=group_transform(child)
            if t: g.set("transform",t)
            walk(child,g)
        elif tag=="clip-path":
            print(f"warning: clip-path skipped: {a(child,'pathData')}",file=sys.stderr)
def main(src,dst):
    t=ET.parse(src); r=t.getroot()
    vw, vh=r.get(f"{AND}viewportWidth"), r.get(f"{AND}viewportHeight")
    w=(r.get(f"{AND}width") or vw).replace("dp",""); h=(r.get(f"{AND}height") or vh).replace("dp","")
    svg=ET.Element("svg",{"xmlns":"http://www.w3.org/2000/svg","width":w,"height":h,"viewBox":f"0 0 {vw} {vh}"})
    walk(r,svg)
    ET.indent(svg)
    ET.ElementTree(svg).write(dst,encoding="unicode",xml_declaration=True)
    print(f"{src} -> {dst}")
if __name__=="__main__": main(sys.argv[1],sys.argv[2])
