import xml.etree.ElementTree as ET, sys, re
def parse_transform(t):
    import re
    ops=[]
    for op, args in re.findall(r"(\w+)\(([^)]*)\)", t or ""):
        nums=[float(x) for x in re.split(r"[,\s]+",args.strip()) if x]
        ops.append((op,nums))
    # detect pivot sandwich: translate(px,py) ... translate(-px,-py)
    px=py=0
    if len(ops)>=3 and ops[0][0]=="translate" and ops[-1][0]=="translate":
        a,b=ops[0][1][0], ops[0][1][1] if len(ops[0][1])>1 else 0
        c,d=ops[-1][1][0], ops[-1][1][1] if len(ops[-1][1])>1 else 0
        if abs(a+c)<1e-6 and abs(b+d)<1e-6:
            px,py=a,b
            ops=ops[1:-1]
    tx=ty=0; sx=sy=1; rot=0
    for op,nums in ops:
        if op=="translate":
            tx+=nums[0]; ty+=nums[1] if len(nums)>1 else 0
        elif op=="scale":
            sx*=nums[0]; sy*=nums[1] if len(nums)>1 else nums[0]
        elif op=="rotate":
            rot+=nums[0]
        elif op=="matrix":
            print(f"warning: matrix transform '{t}' cannot round-trip exactly; in Inkscape select all and do Object > Ungroup / Path > Object to Path to bake it in",file=sys.stderr)
    g={}
    if tx: g["translateX"]=str(tx)
    if ty: g["translateY"]=str(ty)
    if sx!=1: g["scaleX"]=str(sx)
    if sy!=1: g["scaleY"]=str(sy)
    if rot: g["rotation"]=str(rot)
    if (sx!=1 or sy!=1 or rot) and (px or py):
        g["pivotX"]=str(px); g["pivotY"]=str(py)
    return g
def svg_path_to_vd(sp, vd_parent, AND):
    vd=ET.SubElement(vd_parent,"path")
    if sp.get("d"): vd.set(f"{AND}pathData",sp.get("d"))
    if sp.get("fill") and sp.get("fill")!="none": vd.set(f"{AND}fillColor",sp.get("fill"))
    m={"stroke":"strokeColor","stroke-width":"strokeWidth","stroke-linecap":"strokeLineCap","stroke-linejoin":"strokeLineJoin","stroke-miterlimit":"strokeMiterLimit","fill-opacity":"fillAlpha","stroke-opacity":"strokeAlpha"}
    for k,v in m.items():
        if sp.get(k): vd.set(f"{AND}{v}",sp.get(k))
    if sp.get("fill-rule"): vd.set(f"{AND}fillType", "evenOdd" if "even" in sp.get("fill-rule") else "nonZero")
    if sp.get("transform"):
        print(f"warning: path with transform skipped flatten: {sp.get('transform')[:60]} -- in Inkscape do Path>Object to Path or remove transform",file=sys.stderr)
def walk(snode, vd_parent, AND):
    for child in snode:
        tag=child.tag.split("}")[-1]
        if tag=="path":
            svg_path_to_vd(child, vd_parent, AND)
        elif tag=="g":
            g=ET.SubElement(vd_parent,"group")
            for k,v in parse_transform(child.get("transform")).items(): g.set(f"{AND}{k}",v)
            # NOTE: pivot lost (assumed 0,0); for scale-about-pivot groups re-add pivotX/pivotY manually
            walk(child,g,AND)
def main(src,dst,vw=None,vh=None,w=None,h=None):
    ET.register_namespace("android","http://schemas.android.com/apk/res/android")
    t=ET.parse(src); r=t.getroot()
    vb=r.get("viewBox","0 0 48 48").split(); vw=vw or vb[2]; vh=vh or vb[3]
    w=w or r.get("width","48").replace("px",""); h=h or r.get("height","48").replace("px","")
    AND="{http://schemas.android.com/apk/res/android}"
    vec=ET.Element("vector",{f"{AND}width":f"{w}dp",f"{AND}height":f"{h}dp",f"{AND}viewportWidth":str(vw),f"{AND}viewportHeight":str(vh)})
    walk(r,vec,AND)
    ET.indent(vec)
    ET.ElementTree(vec).write(dst,encoding="utf-8",xml_declaration=True)
    print(f"{src} -> {dst}")
if __name__=="__main__":
    import argparse
    p=argparse.ArgumentParser(); p.add_argument("src"); p.add_argument("dst"); p.add_argument("--vw"); p.add_argument("--vh"); p.add_argument("--w"); p.add_argument("--h")
    a=p.parse_args(); main(a.src,a.dst,a.vw,a.vh,a.w,a.h)
