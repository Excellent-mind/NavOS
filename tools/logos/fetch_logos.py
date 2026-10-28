#!/usr/bin/env python3
"""
Fetches candidate logo / sign files from Wikimedia Commons for NavOS:
petrol brands, toll payment lanes (Touch 'n Go, RFID, SmartTAG) and
Malaysian road signs (R&R, toll, Smart Lane). Commons rate-limits the dev
machine, so this runs as a GitHub Actions job (.github/workflows/logos.yml).

For every query it keeps the top SVG/PNG hits with their licence and author
(extmetadata), so the right file can be picked by eye and credited.

Usage: fetch_logos.py OUT_DIR
"""
import json
import os
import sys
import time
import urllib.parse
import urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = "NavOS-logo-fetch/1.0 (https://github.com/Excellent-mind/NavOS)"

QUERIES = {
    "petronas": ["Petronas logo", "Petronas station"],
    "shell": ["Shell logo", "Royal Dutch Shell logo"],
    "petron": ["Petron logo", "Petron Corporation logo"],
    "caltex": ["Caltex logo", "Caltex brand logo"],
    "bhpetrol": ["BHPetrol logo", "BHP petrol Malaysia"],
    "five": ["FIVE Petroleum logo Malaysia"],
    "tng": ["Touch 'n Go logo", "Touch n Go"],
    "rfid": ["Touch n Go RFID", "RFID toll Malaysia"],
    "smarttag": ["SmartTAG Malaysia"],
    "rnr": ["Malaysia road sign R&R", "Rehat dan Rawat sign", "Malaysian expressway rest area sign"],
    "toll-sign": ["Malaysia road sign toll", "Malaysian toll plaza sign", "Plaza Tol sign Malaysia"],
    "smartlane": ["PLUS Smart Lane sign", "Malaysia emergency lane sign"],
    "my-signs": ["Malaysian road signs"],
}


def get(params):
    params = dict(params, format="json")
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(5):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except Exception as e:  # rate limit or network: back off
            print("retry", attempt, e, file=sys.stderr)
            time.sleep(5 * (attempt + 1))
    return {}


def search(q, limit=8):
    d = get({"action": "query", "list": "search", "srnamespace": 6, "srlimit": limit, "srsearch": q})
    return [x["title"] for x in d.get("query", {}).get("search", [])]


def info(titles):
    d = get({"action": "query", "prop": "imageinfo", "titles": "|".join(titles),
             "iiprop": "url|extmetadata|size|mime", "iiurlwidth": 512})
    out = []
    for p in d.get("query", {}).get("pages", {}).values():
        ii = (p.get("imageinfo") or [{}])[0]
        md = ii.get("extmetadata", {})
        out.append({
            "title": p.get("title"),
            "url": ii.get("url"),
            "thumb": ii.get("thumburl"),
            "mime": ii.get("mime"),
            "license": md.get("LicenseShortName", {}).get("value"),
            "artist": md.get("Artist", {}).get("value"),
            "credit": md.get("Credit", {}).get("value"),
            "restrictions": md.get("Restrictions", {}).get("value"),
            "page": "https://commons.wikimedia.org/wiki/" + urllib.parse.quote((p.get("title") or "").replace(" ", "_")),
        })
    return out


def download(url, path):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r, open(path, "wb") as f:
        f.write(r.read())


def main(out):
    os.makedirs(out, exist_ok=True)
    index = {}
    for key, qs in QUERIES.items():
        titles = []
        for q in qs:
            for t in search(q):
                if t not in titles and t.lower().endswith((".svg", ".png")):
                    titles.append(t)
            time.sleep(1)
        files = info(titles[:12]) if titles else []
        os.makedirs(os.path.join(out, key), exist_ok=True)
        for i, f in enumerate(files):
            src = f["url"] if f["mime"] == "image/svg+xml" else (f["thumb"] or f["url"])
            if not src:
                continue
            ext = ".svg" if f["mime"] == "image/svg+xml" else ".png"
            name = f"{i:02d}{ext}"
            try:
                download(src, os.path.join(out, key, name))
                f["file"] = f"{key}/{name}"
                if f["thumb"] and ext == ".svg":
                    download(f["thumb"], os.path.join(out, key, f"{i:02d}.preview.png"))
            except Exception as e:
                f["error"] = str(e)
            time.sleep(1)
        index[key] = files
        print(key, len(files))
    with open(os.path.join(out, "index.json"), "w") as fh:
        json.dump(index, fh, indent=1)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "logos")
