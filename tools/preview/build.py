#!/usr/bin/env python3
"""Genera una vista previa web (un solo HTML) de los layouts XML de la app.

Uso:  python3 tools/preview/build.py
Salida: tools/preview/dist/preview.html

Lee app/src/main/res (layouts, drawables, colores, textos, menús) y lo
empaqueta junto a renderer.js. El navegador interpreta los XML de Android y
los dibuja con HTML/CSS. Es una aproximación: no reemplaza el render real de
Android Studio.
"""
import base64
import json
import pathlib
import re
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app" / "src" / "main" / "res"
HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "dist" / "preview.html"


def read_values(kind):
    out = {}
    for f in sorted((RES / "values").glob("*.xml")):
        try:
            tree = ET.parse(f)
        except ET.ParseError as e:
            print(f"AVISO: no se pudo leer {f.name}: {e}")
            continue
        for el in tree.getroot().iter(kind):
            name = el.get("name")
            if name:
                out[name] = (el.text or "").strip()
    return out


def read_styles():
    """Estilos y temas de values/*.xml: {nombre: {parent, items}}."""
    out = {}
    for f in sorted((RES / "values").glob("*.xml")):
        try:
            root = ET.parse(f).getroot()
        except ET.ParseError:
            continue
        for st in root.iter("style"):
            items = {it.get("name"): (it.text or "").strip() for it in st.iter("item")}
            out[st.get("name")] = {"parent": st.get("parent"), "items": items}
    return out


def read_dir(sub, exts=(".xml",)):
    out = {}
    d = RES / sub
    if not d.exists():
        return out
    for f in sorted(d.iterdir()):
        if f.suffix.lower() in exts:
            out[f.stem] = f.read_text(encoding="utf-8")
    return out


def read_drawables():
    out = {}
    for f in sorted((RES / "drawable").iterdir()):
        ext = f.suffix.lower()
        if ext == ".xml":
            out[f.stem] = {"type": "xml", "text": f.read_text(encoding="utf-8")}
        elif ext in (".png", ".jpg", ".jpeg", ".webp"):
            mime = "image/png" if ext == ".png" else "image/jpeg" if ext != ".webp" else "image/webp"
            data = base64.b64encode(f.read_bytes()).decode()
            out[f.stem] = {"type": "img", "src": f"data:{mime};base64,{data}"}
    return out


def read_onboarding_pages():
    """Saca título, descripción e imagen de cada página de OnboardingActivity.kt."""
    kt = next(ROOT.glob("app/src/main/java/**/OnboardingActivity.kt"), None)
    if not kt:
        return []
    src = kt.read_text(encoding="utf-8")
    pat = re.compile(r'OnboardingPage\(\s*"([^"]*)",\s*"([^"]*)",\s*R\.drawable\.(\w+)\s*\)', re.S)
    return [{"title": t, "desc": d, "image": i} for t, d, i in pat.findall(src)]


def main():
    bundle = {
        "layouts": read_dir("layout"),
        "menus": read_dir("menu"),
        "colorLists": read_dir("color"),
        "drawables": read_drawables(),
        "colors": read_values("color"),
        "strings": read_values("string"),
        "styles": read_styles(),
        "onboarding": read_onboarding_pages(),
    }
    template = (HERE / "template.html").read_text(encoding="utf-8")
    renderer = (HERE / "renderer.js").read_text(encoding="utf-8")
    data = json.dumps(bundle, ensure_ascii=False).replace("</", "<\\/")
    html = template.replace("/*__BUNDLE__*/", f"window.RES = {data};").replace("/*__RENDERER__*/", renderer)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(html, encoding="utf-8")
    print(f"OK: {OUT.relative_to(ROOT)} ({OUT.stat().st_size // 1024} KB, {len(bundle['layouts'])} layouts)")


if __name__ == "__main__":
    main()
