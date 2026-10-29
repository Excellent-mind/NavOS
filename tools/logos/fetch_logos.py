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
    # Second pass (the first found Petronas, Caltex, Touch 'n Go, SmartTAG, R&R).
    "shell": ["Shell pecten", "Shell logo.svg", "Shell plc logo"],
    "petron": ["Petron.svg", "Petron Corporation", "Petron Malaysia"],
    "bhpetrol": ["BHPetrol", "Boustead Petroleum Marketing"],
    "rfid": ["Touch n Go RFID logo", "TNG RFID"],
    "my-symbols": ["Malaysia Symbols", "Malaysia Symbols Toll", "Malaysia Symbols Petrol"],
    "toll-symbol": ["Malaysia expressway toll symbol", "Malaysia road sign toll plaza symbol"],
    "smartlane": ["Smart Lane Malaysia", "SmartLane PLUS", "lorong kecemasan"],
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


def search(q, limit=20):
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
        files = [f for i in range(0, min(len(titles), 40), 20) for f in info(titles[i:i + 20])]
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
