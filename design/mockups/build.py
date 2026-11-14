#!/usr/bin/env python3
"""Génère les maquettes HTML puis les rend en PNG (Firefox headless)."""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SVG_DIR = os.path.abspath(os.path.join(HERE, "..", "..", "tools", "bristol-svg"))

ICONS = {
    "settings": "M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58a.49.49 0 0 0 .12-.61l-1.92-3.32a.49.49 0 0 0-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54a.484.484 0 0 0-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58a.49.49 0 0 0-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z",
    "home": "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
    "chart": "M5 9.2h3V19H5zM10.6 5h2.8v14h-2.8zm5.6 8H19v6h-2.8z",
    "calendar": "M19 3h-1V1h-2v2H8V1H6v2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm0 16H5V9h14v10z",
    "plus": "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
    "restaurant": "M11 9H9V2H7v7H5V2H3v7c0 2.12 1.66 3.84 3.75 3.97V22h2.5v-9.03C11.34 12.84 13 11.12 13 9V2h-2v7zm5-3v8h2.5v8H21V2c-2.76 0-5 2.24-5 4z",
    "drop": "M12 2c-5.33 4.55-8 8.48-8 11.8 0 4.33 3.67 8.2 8 8.2s8-3.87 8-8.2c0-3.32-2.67-7.25-8-11.8z",
    "left": "M15.41 7.41 14 6l-6 6 6 6 1.41-1.41L10.83 12z",
    "right": "M8.59 16.59 10 18l6-6-6-6-1.41 1.41L13.17 12z",
    "check": "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm-2 15-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
    "alarm": "M22 5.72l-4.6-3.86-1.29 1.53 4.6 3.86L22 5.72zM7.88 3.39 6.6 1.86 2 5.71l1.29 1.53 4.59-3.85zM12.5 8H11v6l4.75 2.85.75-1.23-4-2.37V8zM12 4a8 8 0 1 0 0 16 8 8 0 0 0 0-16zm0 14a6 6 0 1 1 0-12 6 6 0 0 1 0 12z",
    "photo": "M21 19V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2zM8.5 13.5l2.5 3 3.5-4.5 4.5 6H5l3.5-4.5z",
    "bell": "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
    "download": "M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z",
    "upload": "M9 16h6v-6h4l-7-7-7 7h4zm-4 2h14v2H5z",
    "arrow_back": "M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z",
    "lock": "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V10a2 2 0 0 0-2-2zm-6 9a2 2 0 1 1 0-4 2 2 0 0 1 0 4zM9 8V6a3 3 0 0 1 6 0v2H9z",
}

BRISTOL_COLORS = ["#FF6B6B", "#8D5A3B", "#A06A44", "#B57E4F", "#C99565", "#D9AE80", "#E0C08A", "#FFB74D"]


def icon(name, size=22, cls=""):
    return (f'<svg class="{cls}" width="{size}" height="{size}" viewBox="0 0 24 24" '
            f'fill="currentColor" aria-hidden="true"><path d="{ICONS[name]}"/></svg>')


def bristol(n, size=30):
    path = os.path.join(SVG_DIR, f"ic_bristol_{n}.svg")
    raw = open(path, encoding="utf-8").read()
    inner = raw.split(">", 1)[1].rsplit("</svg>", 1)[0]
    inner = re.sub(r"\s+", " ", inner).strip()
    return (f'<svg width="{size}" height="{size}" viewBox="0 0 48 48" '
            f'aria-hidden="true">{inner}</svg>')


def statusbar():
    return """<div class="statusbar"><span>17:44</span>
      <div class="sicons"><span>5G</span>
        <div class="sig"><i></i><i></i><i></i><i></i></div>
        <div class="batt"><i></i></div>
      </div></div>"""


def bottomnav(active):
    items = [("home", "Aujourd’hui"), ("calendar", "Historique"), ("chart", "Analyses"), ("settings", "Réglages")]
    out = []
    for i, (ic, label) in enumerate(items):
        cls = "navitem active" if i == active else "navitem"
        out.append(f'<div class="{cls}"><div class="ic">{icon(ic, 21)}</div><span>{label}</span></div>')
    return '<div class="bottomnav">' + "".join(out) + '</div><div class="homebar"></div>'


def page(body, name):
    html = f"""<!doctype html>
<html lang="fr"><head><meta charset="utf-8">
<title>{name}</title>
<link rel="stylesheet" href="style.css">
</head><body><div class="stage"><div class="phone">{body}</div></div></body></html>"""
    with open(os.path.join(HERE, f"{name}.html"), "w", encoding="utf-8") as f:
        f.write(html)


# --------------------------------------------------------------------------- A1
def screen_a1():
    rows = [
        ("meal", "08:12", "Petit-déjeuner", "Yaourt, café, banane", "photo"),
        ("meal", "13:30", "Déjeuner", "Pâtes complètes, poulet", "photo"),
        ("selle", "18:30", "Selle · Type 4", "Sans sang, sans glaires", "bristol"),
    ]
    trs = []
    for kind, t, title, sub, thumb in rows:
        th = bristol(4, 26) if thumb == "bristol" else icon("photo", 18)
        trs.append(f"""<div class="trow {kind}"><div class="tbody">
          <div class="tmain"><b>{title}</b><time>{t}</time></div>
          <div class="tsub">{sub}</div></div><div class="thumb">{th}</div></div>""")

    body = f"""{statusbar()}
<header class="hdr"><div><div class="eyebrow">Bonjour</div><h1>Vendredi 6 novembre</h1></div>
  <button class="iconbtn">{icon("settings", 20)}</button></header>
<div class="content">
  <section class="card summary">
    <div class="row between"><span class="label">Aujourd’hui</span><span class="pill">Journée calme</span></div>
    <div class="stats">
      <div class="stat"><b>2</b><span>selles</span></div><div class="sep"></div>
      <div class="stat"><b>1</b><span>repas noté</span></div><div class="sep"></div>
      <div class="stat"><b>4,0</b><span>Bristol moyen</span></div>
    </div>
  </section>
  <div class="actions">
    <button class="action meal"><div class="aicon">{icon("restaurant", 20)}</div>
      <div><b>Repas</b><span>Photo ou code-barres</span></div><div class="plus">+</div></button>
    <button class="action selle"><div class="aicon">{icon("drop", 20)}</div>
      <div><b>Selle</b><span>En 2 gestes</span></div><div class="plus">+</div></button>
  </div>
  <div class="sechead"><h2>Le fil du jour</h2><span class="link">Tout voir</span></div>
  <div class="timeline">{''.join(trs)}</div>
  <section class="card" style="padding:14px 16px;margin-top:18px">
    <div class="row between"><span class="label">Cette semaine</span><span class="link">Détails</span></div>
    <div class="stats" style="margin-top:10px">
      <div class="stat"><b>9</b><span>selles</span></div><div class="sep"></div>
      <div class="stat"><b>4,2</b><span>moyenne</span></div><div class="sep"></div>
      <div class="stat"><b>2</b><span>jours calmes</span></div>
    </div>
  </section>
  <div class="reminder">
    <div class="ic">{icon("bell", 18)}</div>
    <div class="txt"><b>Prochain rappel · 20:00</b><span>Ce soir, notification exacte</span></div>
  </div>
</div>
{bottomnav(0)}"""
    page(body, "A1-aujourdhui")


# --------------------------------------------------------------------------- A2
def screen_a2():
    # Novembre 2026 : le 1er est un dimanche. Grille démarrant le lundi 26 octobre.
    weeks = [
        [26, 27, 28, 29, 30, 31, 1],
        [2, 3, 4, 5, 6, 7, 8],
        [9, 10, 11, 12, 13, 14, 15],
        [16, 17, 18, 19, 20, 21, 22],
        [23, 24, 25, 26, 27, 28, 29],
        [30, 1, 2, 3, 4, 5, 6],
    ]
    values = {1: 3, 2: 1, 3: 2, 4: 4, 5: 3, 6: 4}
    cells = []
    for wi, week in enumerate(weeks):
        for day in week:
            muted = (wi == 0 and day > 20) or (wi == 5 and day < 20)
            if muted:
                cells.append('<div class="day sm muted"></div>')
                continue
            cls = "day sm"
            bar = "background:transparent"
            if day in values:
                bar = f"background:{BRISTOL_COLORS[values[day]]}"
            if day == 6:
                cls += " today"
            cells.append(f'<div class="{cls}"><span class="n">{day}</span><span class="bar" style="{bar}"></span></div>')

    legend = "".join(
        f'<span class="dot" style="background:{BRISTOL_COLORS[i]}"></span>' for i in range(6, 8)
    )
    bars = [34, 52, 28, 60, 44, 66, 40]
    chart = "".join(f'<i style="height:{b}%"></i>' for b in bars)

    body = f"""{statusbar()}
<header class="hdr"><div><div class="eyebrow">Novembre 2026</div><h1>Historique</h1></div>
  <button class="iconbtn">{icon("chart", 20)}</button></header>
<div class="content">
  <section class="card monthcard">
    <div class="mhead"><div class="mnav">{icon("left", 18)}</div><b>Novembre 2026</b><div class="mnav">{icon("right", 18)}</div></div>
    <div class="wdays"><span>L</span><span>M</span><span>M</span><span>J</span><span>V</span><span>S</span><span>D</span></div>
    <div class="grid">{''.join(cells)}</div>
    <div class="legend"><span class="lbl">Bristol</span><span>0</span><span class="dot" style="background:{BRISTOL_COLORS[0]}"></span><span class="dot" style="background:{BRISTOL_COLORS[1]}"></span><span class="dot" style="background:{BRISTOL_COLORS[2]}"></span><span class="dot" style="background:{BRISTOL_COLORS[3]}"></span><span class="dot" style="background:{BRISTOL_COLORS[4]}"></span><span class="dot" style="background:{BRISTOL_COLORS[5]}"></span><span class="dot" style="background:{BRISTOL_COLORS[6]}"></span><span class="dot" style="background:{BRISTOL_COLORS[7]}"></span><span>7</span></div>
  </section>
  <section class="card" style="padding:14px 16px;margin-top:14px">
    <div class="row between"><div><div class="label">Ce mois</div><div style="font-size:22px;font-weight:700;margin-top:4px">12 selles</div></div>
      <span class="pill">moyenne 4,1</span></div>
    <div class="minichart" style="margin-top:12px">{chart}</div>
    <div class="row between" style="margin-top:8px;color:var(--dim2);font-size:10px"><span>31 oct.</span><span>6 nov.</span></div>
  </section>
  <div class="sechead" style="margin-top:16px"><h2>Derniers jours</h2><span class="link">Tout voir</span></div>
  <section class="card" style="padding:2px 14px">
    <div class="setrow"><div class="ic" style="padding:5px">{bristol(3, 24)}</div><div class="txt"><b>Jeudi 5 novembre</b><span>1 selle · 18:30</span></div></div>
    <div class="setrow"><div class="ic" style="padding:5px">{bristol(4, 24)}</div><div class="txt"><b>Mercredi 4 novembre</b><span>1 selle · 18:30</span></div></div>
  </section>
</div>
{bottomnav(1)}"""
    page(body, "A2-historique")


# --------------------------------------------------------------------------- A3
def screen_a3():
    tiles = []
    for i in range(8):
        cls = "btile sel" if i == 4 else "btile"
        tiles.append(f'<div class="{cls}">{bristol(i, 34)}<span>Type {i}</span></div>')
    chips = [("Sang", 0), ("Glaires", 0), ("Douleur", 0), ("Fatigue", 0)]
    chips_html = "".join(f'<span class="chip">{c}</span>' for c, _ in chips)

    body = f"""{statusbar()}
<header class="hdr"><div><div class="eyebrow">Bonjour</div><h1>Vendredi 6 novembre</h1></div>
  <button class="iconbtn">{icon("settings", 20)}</button></header>
<div class="content">
  <section class="card summary">
    <div class="row between"><span class="label">Aujourd’hui</span><span class="pill">Journée calme</span></div>
    <div class="stats">
      <div class="stat"><b>2</b><span>selles</span></div><div class="sep"></div>
      <div class="stat"><b>1</b><span>repas noté</span></div><div class="sep"></div>
      <div class="stat"><b>4,0</b><span>Bristol moyen</span></div>
    </div>
  </section>
  <div class="actions">
    <button class="action meal"><div class="aicon">{icon("restaurant", 20)}</div><div><b>Repas</b><span>Photo ou code-barres</span></div></button>
    <button class="action selle"><div class="aicon">{icon("drop", 20)}</div><div><b>Selle</b><span>En 2 gestes</span></div></button>
  </div>
</div>
<div class="dim"></div>
<div class="sheet">
  <div class="grab"></div>
  <h3>Nouvelle selle</h3>
  <div class="sub">Vendredi 6 novembre · 18:30</div>
  <div class="bgrid">{''.join(tiles)}</div>
  <div class="chips">{chips_html}</div>
  <button class="btn">{icon("check", 20)} Enregistrer</button>
  <button class="btn ghost">Annuler</button>
</div>"""
    page(body, "A3-saisie")


# --------------------------------------------------------------------------- B1
def screen_b1():
    weeks = [
        [26, 27, 28, 29, 30, 31, 1],
        [2, 3, 4, 5, 6, 7, 8],
        [9, 10, 11, 12, 13, 14, 15],
        [16, 17, 18, 19, 20, 21, 22],
        [23, 24, 25, 26, 27, 28, 29],
        [30, 1, 2, 3, 4, 5, 6],
    ]
    values = {1: 3, 2: 1, 3: 2, 4: 4, 5: 3}
    cells = []
    for wi, week in enumerate(weeks):
        for day in week:
            muted = (wi == 0 and day > 20) or (wi == 5 and day < 20)
            if muted:
                cells.append('<div class="day sm muted"></div>')
                continue
            cls = "day"
            bar = "background:transparent"
            if day in values:
                bar = f"background:{BRISTOL_COLORS[values[day]]}"
            if day == 5:
                cls += " sel"
            cells.append(f'<div class="{cls}"><span class="n">{day}</span><span class="bar" style="{bar}"></span></div>')

    body = f"""{statusbar()}
<header class="hdr"><div><div class="eyebrow">Ton suivi</div><h1>Calendrier</h1></div>
  <span class="link" style="padding-top:10px">Aujourd’hui</span></header>
<div class="content">
  <section class="card monthcard">
    <div class="mhead"><div class="mnav">{icon("left", 18)}</div><b>Novembre 2026</b><div class="mnav">{icon("right", 18)}</div></div>
    <div class="wdays"><span>L</span><span>M</span><span>M</span><span>J</span><span>V</span><span>S</span><span>D</span></div>
    <div class="grid">{''.join(cells)}</div>
  </section>
  <div class="sechead" style="margin-top:16px"><h2>Jeudi 5 novembre</h2><span class="link">Ouvrir</span></div>
  <section class="card" style="padding:4px 14px">
    <div class="setrow"><div class="ic">{icon("restaurant", 18)}</div><div class="txt"><b>Petit-déjeuner</b><span>08:12 · Yaourt, café</span></div></div>
    <div class="setrow"><div class="ic" style="padding:5px">{bristol(3, 24)}</div><div class="txt"><b>Selle · Type 3</b><span>18:30 · Rien à signaler</span></div></div>
  </section>
  <section class="card" style="padding:14px 16px;margin-top:14px">
    <div class="row between" style="margin-bottom:10px"><span class="label">Cette semaine</span><span class="link">9 selles</span></div>
    <div class="weekstrip">
      <div class="wd"><span>L</span><b>2</b><i style="background:{BRISTOL_COLORS[2]}"></i></div>
      <div class="wd"><span>M</span><b>3</b><i style="background:{BRISTOL_COLORS[1]}"></i></div>
      <div class="wd"><span>M</span><b>4</b><i style="background:{BRISTOL_COLORS[4]}"></i></div>
      <div class="wd"><span>J</span><b>5</b><i style="background:{BRISTOL_COLORS[3]}"></i></div>
      <div class="wd on"><span>V</span><b>6</b><i style="background:{BRISTOL_COLORS[4]}"></i></div>
      <div class="wd"><span>S</span><b>7</b><i></i></div>
      <div class="wd"><span>D</span><b>8</b><i></i></div>
    </div>
  </section>
</div>
<div class="fab">{icon("plus", 26)}</div>"""
    page(body, "B1-calendrier")


# --------------------------------------------------------------------------- B2
def screen_b2():
    def row(t, dot, title, sub, tags):
        tg = "".join(f'<span class="tag">{x}</span>' for x in tags)
        return f"""<div class="tlrow"><div class="tltime">{t}</div>
          <div class="tlrail"><div class="tldot {dot}"></div></div>
          <div class="tlcard"><b>{title}</b><div class="s">{sub}</div><div class="tags">{tg}</div></div></div>"""

    rows = "".join([
        row("08:12", "meal", "Petit-déjeuner", "Yaourt, café, banane", ["Sans lactose"]),
        row("13:30", "meal", "Déjeuner", "Pâtes complètes, poulet, courgettes", ["Riche en fibres"]),
        row("16:00", "meal", "Collation", "Une pomme, un thé vert", []),
        row("18:30", "selle", "Selle · Type 4", "Sans sang · sans glaires", ["Bristol 4"]),
        row("21:00", "", "Rien noté", "Un repas ou une selle à ajouter ?", []),
        row("22:00", "", "Note du soir", "Journée plutôt calme, peu de douleurs.", ["Ressenti 2/5"]),
    ])

    body = f"""{statusbar()}
<header class="hdr" style="align-items:center;padding-bottom:10px">
  <button class="iconbtn" style="background:transparent;border:0">{icon("arrow_back", 22)}</button>
  <div style="text-align:center"><div class="eyebrow">Journal</div><h1 style="font-size:19px">Jeudi 5 novembre</h1></div>
  <button class="iconbtn" style="background:transparent;border:0">{icon("right", 22)}</button>
</header>
<div class="content">
  <section class="card" style="padding:12px 16px;margin-bottom:14px">
    <div class="stats" style="margin-top:0">
      <div class="stat"><b>2</b><span>repas</span></div><div class="sep"></div>
      <div class="stat"><b>1</b><span>selle</span></div><div class="sep"></div>
      <div class="stat"><b>4,0</b><span>Bristol moyen</span></div>
    </div>
  </section>
  <div class="tl">{rows}</div>
  <div class="addrow">{icon("plus", 16)} Ajouter une entrée</div>
</div>
<div class="fab">{icon("plus", 26)}</div>"""
    page(body, "B2-journee")


# --------------------------------------------------------------------------- B3
def screen_b3():
    body = f"""{statusbar()}
<header class="hdr"><div><div class="eyebrow">IBD Journal</div><h1>Réglages</h1></div></header>
<div class="content">
  <div class="setgroup"><span class="label">Rappels</span>
    <section class="card">
      <div class="setrow"><div class="ic" style="color:var(--primary)">{icon("bell", 18)}</div>
        <div class="txt"><b>Rappel quotidien</b><span>Une notification par jour</span></div>
        <div class="switch"><i></i></div></div>
      <div class="setrow"><div class="ic">{icon("alarm", 18)}</div>
        <div class="txt"><b>Heure du rappel</b><span>Tous les jours</span></div>
        <span class="timeval">20:00</span></div>
      <div class="setrow"><div class="ic" style="color:var(--ok)">{icon("check", 18)}</div>
        <div class="txt"><b>Alarmes exactes</b><span>Autorisées par le système</span></div></div>
    </section>
  </div>
  <div class="setgroup"><span class="label">Données</span>
    <section class="card">
      <div class="setrow"><div class="ic">{icon("download", 18)}</div><div class="txt"><b>Exporter</b><span>Sauvegarde JSON</span></div></div>
      <div class="setrow"><div class="ic">{icon("upload", 18)}</div><div class="txt"><b>Importer</b><span>Restaurer une sauvegarde</span></div></div>
    </section>
  </div>
  <div class="setgroup"><span class="label">Confidentialité</span>
    <section class="card">
      <div class="setrow"><div class="ic">{icon("lock", 18)}</div><div class="txt"><b>Données locales</b><span>Rien n’est envoyé sur un serveur</span></div></div>
    </section>
  </div>
</div>
{bottomnav(3)}"""
    page(body, "B3-reglages")


SCREENS = {
    "A1-aujourdhui": screen_a1,
    "A2-historique": screen_a2,
    "A3-saisie": screen_a3,
    "B1-calendrier": screen_b1,
    "B2-journee": screen_b2,
    "B3-reglages": screen_b3,
}


def render(name):
    out = os.path.join(HERE, f"{name}.png")
    prof = f"/tmp/ffmock-{name}"
    os.makedirs(prof, exist_ok=True)
    cmd = [
        "firefox", "--headless", "--no-remote", "--profile", prof,
        "--window-size=860,1864", f"--screenshot={out}",
        f"file://{os.path.join(HERE, name + '.html')}",
    ]
    subprocess.run(cmd, check=True, capture_output=True, timeout=120)
    print("rendu:", out)


if __name__ == "__main__":
    only = sys.argv[1:] or list(SCREENS)
    for name in only:
        SCREENS[name]()
        render(name)
