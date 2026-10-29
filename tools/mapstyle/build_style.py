#!/usr/bin/env python3
"""
Builds the NavOS "Jalan" MapLibre styles (day + night) from OpenFreeMap
(OpenMapTiles schema) vector tiles.

Roads are drawn the way they really are in Malaysia:
  * from zoom 15 up every road is its true width in metres (JKR lane 3.5 m),
    so a 3-lane PLUS carriageway is ~13.5 m and a taman road ~5.5 m;
  * from zoom 17 lane markings appear: white dashed lane lines, solid white
    edge lines (JKR Arahan Teknik 2D/85: markings are white);
  * toll expressways get a JKR-sign green edge so they read as "E" roads;
  * route numbers (E1, FT1 ...) sit on chrome-yellow shields like JKR markers.

Below zoom 15 widths are cartographic (readable), and grow smoothly into the
true widths.

Usage: build_style.py OUT_DIR   ->  jalan-day.json, jalan-night.json
       (the app ships them in app/src/main/assets/mapstyle/)
"""
import json
import math
import sys

TILES = "https://tiles.openfreemap.org/planet"
GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
LANE = 3.5  # m, JKR R6 minimum lane width

# Metres per pixel at zoom 0 for 512 px tiles, at Malaysia's latitude (~3.5 N).
M_PER_PX_Z0 = 40075016.686 * math.cos(math.radians(3.5)) / 512


def px(m, z):
    return m * (2 ** z) / M_PER_PX_Z0


def width(m, low):
    """low: {zoom: px} cartographic widths below z15; true metres from z15."""
    stops = []
    for z in sorted(low):
        stops += [z, low[z]]
    for z in (15, 22):
        stops += [z, round(max(px(m, z), low[max(low)] if z == 15 else 0), 3)]
    return ["interpolate", ["exponential", 2], ["zoom"]] + stops


def metric(m):
    """A pure metre measure (line offset / marking width), valid from z15."""
    return ["interpolate", ["exponential", 2], ["zoom"], 15, round(px(m, 15), 4), 22, round(px(m, 22), 3)]


# Road kinds: (name, filter, carriageway metres, lanes, oneway?, low-zoom widths, minzoom)
def cls(*c):
    return ["in", ["get", "class"], ["literal", list(c)]]


ONEWAY = ["==", ["get", "oneway"], 1]
TWOWAY = ["!=", ["get", "oneway"], 1]
NOT_RAMP = ["!=", ["get", "ramp"], 1]

KINDS = [
    # name,            filter,                                       m,    lanes, oneway, low widths,                          minz
    ("motorway",       ["all", cls("motorway"), ONEWAY, NOT_RAMP],   13.5, 3, True,  {6: 0.8, 9: 1.6, 12: 3.0, 14: 4.6},  6),
    ("motorway2",      ["all", cls("motorway"), TWOWAY, NOT_RAMP],   26.0, 6, False, {6: 1.2, 9: 2.4, 12: 4.2, 14: 6.5},  6),
    ("trunk",          ["all", cls("trunk"), ONEWAY, NOT_RAMP],       8.5, 2, True,  {7: 0.6, 10: 1.4, 12: 2.4, 14: 3.6},  7),
    ("trunk2",         ["all", cls("trunk"), TWOWAY, NOT_RAMP],      15.0, 4, False, {7: 0.8, 10: 1.8, 12: 3.0, 14: 4.8},  7),
    ("primary",        ["all", cls("primary"), ONEWAY, NOT_RAMP],     8.0, 2, True,  {9: 0.6, 12: 2.0, 14: 3.2},          9),
    ("primary2",       ["all", cls("primary"), TWOWAY, NOT_RAMP],    13.0, 4, False, {9: 0.8, 12: 2.4, 14: 4.0},          9),
    ("secondary",      ["all", cls("secondary")],                     7.5, 2, False, {11: 0.6, 13: 1.8, 14: 2.8},         11),
    ("tertiary",       ["all", cls("tertiary")],                      7.0, 2, False, {12: 0.5, 13: 1.4, 14: 2.4},         12),
    ("link",           ["any", ["==", ["get", "ramp"], 1], cls("motorway_link", "trunk_link", "primary_link")],
                                                                      5.5, 1, True,  {12: 0.6, 14: 2.0},                  12),
    ("minor",          cls("minor"),                                  5.5, 2, False, {13: 0.4, 14: 1.6},                  13),
    ("service",        cls("service", "track"),                       3.5, 1, False, {14: 0.6},                           14),
]

PALETTE = {
    "day": dict(
        land="#EEECE6", residential="#E8E5DD", park="#D3E5C6", wood="#C3DAB3", water="#A8CDE3",
        waterline="#8FBDD8", building="#DCD8CF", building3d="#D4CFC4", industrial="#E6E2DA",
        kerb="#A9A59C", asphalt_far="#9A9DA3", asphalt_minor_far="#B4B6BA", asphalt="#6C7077", asphalt_hw="#5A5E65",
        toll="#0A7D46", marking="#F7F7F2", marking_op=0.92, rail="#B8B4AC",
        label="#3A3D42", label_halo="#F4F2EC", road_label="#FFFFFF", road_halo="#4A4E55",
        place="#24272B", water_label="#4E86A8", shadow="#000000", boundary="#A9A39A",
    ),
    "night": dict(
        land="#14171B", residential="#171A1F", park="#16231A", wood="#14211A", water="#0D2230",
        waterline="#123247", building="#1F232A", building3d="#242932", industrial="#181B20",
        kerb="#3A3F47", asphalt_far="#3A3E45", asphalt_minor_far="#2E3238", asphalt="#30343A", asphalt_hw="#363A41",
        toll="#1FA866", marking="#D5D8DD", marking_op=0.70, rail="#2C3036",
        label="#C4C8CE", label_halo="#14171B", road_label="#E8EAED", road_halo="#24272C",
        place="#E6E8EB", water_label="#5E93B5", shadow="#000000", boundary="#4A4F57",
    ),
}

FONT = ["Noto Sans Regular"]
FONT_BOLD = ["Noto Sans Bold"]


def tr(layer_id, f, **kw):
    out = {"id": layer_id, "type": "line", "source": "openmaptiles", "source-layer": "transportation", "filter": f}
    out.update(kw)
    # Paint lines join round so tile and segment joins don't leave white ticks.
    out.setdefault("layout", {"line-join": "bevel", "line-cap": "butt"})
    return out


def build(theme):
    c = PALETTE[theme]
    L = []
    L.append({"id": "land", "type": "background", "paint": {"background-color": c["land"]}})
    L.append({"id": "residential", "type": "fill", "source": "openmaptiles", "source-layer": "landuse",
              "filter": cls("residential", "suburb", "neighbourhood"), "paint": {"fill-color": c["residential"]}})
    L.append({"id": "industrial", "type": "fill", "source": "openmaptiles", "source-layer": "landuse",
              "filter": cls("industrial", "commercial", "retail", "railway"), "paint": {"fill-color": c["industrial"]}})
    L.append({"id": "park", "type": "fill", "source": "openmaptiles", "source-layer": "park", "paint": {"fill-color": c["park"]}})
    L.append({"id": "landcover", "type": "fill", "source": "openmaptiles", "source-layer": "landcover",
              "filter": cls("wood", "grass", "farmland", "wetland"),
              "paint": {"fill-color": ["match", ["get", "class"], "wood", c["wood"], c["park"]], "fill-opacity": 0.85}})
    L.append({"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water", "paint": {"fill-color": c["water"]}})
    L.append({"id": "waterway", "type": "line", "source": "openmaptiles", "source-layer": "waterway",
              "paint": {"line-color": c["waterline"], "line-width": ["interpolate", ["linear"], ["zoom"], 10, 0.5, 16, 3]}})
    L.append({"id": "building", "type": "fill", "source": "openmaptiles", "source-layer": "building", "minzoom": 14,
              "paint": {"fill-color": c["building"], "fill-opacity": ["interpolate", ["linear"], ["zoom"], 14, 0, 15, 1]}})

    order = list(reversed(KINDS))  # small roads first, expressways on top
    tunnel = ["==", ["get", "brunnel"], "tunnel"]
    bridge = ["==", ["get", "brunnel"], "bridge"]
    surface = ["!", ["in", ["get", "brunnel"], ["literal", ["tunnel", "bridge"]]]]

    def road_stack(prefix, extra, op):
        # Three passes, like a road atlas: every road's kerb first, then every road's asphalt,
        # then the lane bands. Drawing kerb + asphalt per road type let one type's kerb paint
        # across the next type's surface where they join (a grey line cutting the road).
        for name, f, m, lanes, oneway, low, minz in order:
            ff = ["all", f, extra]
            # Kerb / shoulder edge, 0.5 m each side (and the toll-green edge on expressways).
            kerb_color = c["kerb"]
            if name.startswith("motorway"):
                kerb_color = ["case", ["==", ["get", "toll"], 1], c["toll"], c["kerb"]]
            L.append(tr(f"{prefix}{name}-kerb", ff, minzoom=max(minz, 12),
                        paint={"line-color": kerb_color, "line-opacity": op,
                               "line-width": width(m + 1.0, {k: v + 1.2 for k, v in low.items()})},
                        layout={"line-cap": "round", "line-join": "round"}))
        for name, f, m, lanes, oneway, low, minz in order:
            ff = ["all", f, extra]
            hw = name.startswith(("motorway", "trunk"))
            L.append(tr(f"{prefix}{name}", ff, minzoom=minz,
                        paint={"line-color": ["interpolate", ["linear"], ["zoom"], 13, c["asphalt_far"] if hw or name.startswith("primary") else c["asphalt_minor_far"], 16,
                                              c["asphalt_hw"] if hw else c["asphalt"]],
                               "line-opacity": op, "line-width": width(m, low)},
                        layout={"line-cap": "round", "line-join": "round"}))
        for name, f, m, lanes, oneway, low, minz in order:
            ff = ["all", f, extra]
            hw = name.startswith(("motorway", "trunk"))
            if name.startswith(("motorway", "trunk", "primary")):
                # Solid edge lines without line-offset (offsets spike at segment joins): a white
                # band as wide as the lanes, then the lanes' asphalt again on top of it.
                lanes_m = lanes * LANE
                L.append(tr(f"{prefix}{name}-edgeband", ff, minzoom=16.5,
                            paint={"line-color": c["marking"], "line-opacity": c["marking_op"] * op,
                                   "line-width": metric(lanes_m + 0.2)},
                            layout={"line-cap": "round", "line-join": "round"}))
                L.append(tr(f"{prefix}{name}-lanes", ff, minzoom=16.5,
                            paint={"line-color": c["asphalt_hw"] if hw else c["asphalt"], "line-opacity": op,
                                   "line-width": metric(lanes_m - 0.2)},
                            layout={"line-cap": "round", "line-join": "round"}))

    def markings(prefix, extra, op):
        for name, f, m, lanes, oneway, low, minz in order:
            # Lane paint only on the big roads: town streets, service roads and ramps are short
            # pieces whose dashes criss-cross at every junction.
            if not name.startswith(("motorway", "trunk", "primary", "secondary")):
                continue
            ff = ["all", f, extra]
            mk = {"line-color": c["marking"], "line-opacity": c["marking_op"] * op}
            # Edge lines on bigger roads are drawn in road_stack.
            # Ramps get no paint: at a diverge their edge lines would cut across the main carriageway.
            if name == "link":
                continue
            # Lane lines: dashed 3 m line / 6 m gap, 0.15 m wide.
            dash = ["literal", [20, 40]]
            if oneway:
                offs = [(i - lanes / 2) * LANE for i in range(1, lanes)]
            else:
                per = lanes // 2
                offs = [i * LANE for i in range(-per + 1, per) if i != 0]
                # Centre line: dashed on small roads, double solid on divided-less big roads.
                if name in ("trunk2", "primary2", "motorway2"):
                    for o in (-0.18, 0.18):
                        L.append(tr(f"{prefix}{name}-centre{o}", ff, minzoom=16.5,
                                    paint=dict(mk, **{"line-width": metric(0.12), "line-offset": metric(o)})))
                else:
                    L.append(tr(f"{prefix}{name}-centre", ff, minzoom=17,
                                paint=dict(mk, **{"line-width": metric(0.15), "line-dasharray": dash})))
            for i, o in enumerate(offs):
                L.append(tr(f"{prefix}{name}-lane{i}", ff, minzoom=17,
                            paint=dict(mk, **{"line-width": metric(0.15), "line-offset": metric(o), "line-dasharray": dash})))

    road_stack("tunnel-", tunnel, 0.45)
    road_stack("", surface, 1.0)
    markings("", surface, 1.0)
    # Bridges: a soft shadow under, then the road again on top.
    for name, f, m, lanes, oneway, low, minz in order:
        if name in ("service", "minor"):
            continue
        L.append(tr(f"bridge-{name}-shadow", ["all", f, bridge], minzoom=15,
                    paint={"line-color": c["shadow"], "line-opacity": 0.18, "line-blur": metric(2.0),
                           "line-width": width(m + 4, {k: v + 2 for k, v in low.items()}), "line-translate": [0, 3]}))
    road_stack("bridge-", bridge, 1.0)
    markings("bridge-", bridge, 1.0)

    L.append(tr("rail", cls("rail", "transit"), minzoom=12,
                paint={"line-color": c["rail"], "line-width": ["interpolate", ["linear"], ["zoom"], 12, 0.8, 17, 3]}))

    # 3D buildings from zoom 15.5: real heights from the map (render_height), fading in.
    L.append({"id": "building-3d", "type": "fill-extrusion", "source": "openmaptiles", "source-layer": "building", "minzoom": 15.5,
              "paint": {"fill-extrusion-color": c["building3d"],
                        "fill-extrusion-height": ["coalesce", ["get", "render_height"], 6],
                        "fill-extrusion-base": ["coalesce", ["get", "render_min_height"], 0],
                        "fill-extrusion-opacity": ["interpolate", ["linear"], ["zoom"], 15.5, 0, 16.5, 0.82]}})

    # One-way arrows on town roads from z16.5 (expressways are all one way: no arrows there).
    ow_roads = cls("secondary", "tertiary", "minor")
    for name, sign, rot in (("oneway", 1, 0), ("oneway-back", -1, 180)):
        L.append({"id": name, "type": "symbol", "source": "openmaptiles", "source-layer": "transportation", "minzoom": 17,
                  "filter": ["all", ow_roads, ["==", ["get", "oneway"], sign], NOT_RAMP],
                  "layout": {"symbol-placement": "line", "symbol-spacing": 260, "icon-padding": 12, "icon-image": "navos-oneway",
                             "icon-rotate": rot, "icon-size": ["interpolate", ["linear"], ["zoom"], 16.5, 0.6, 19, 1.0],
                             "icon-rotation-alignment": "map", "icon-allow-overlap": False},
                  "paint": {"icon-opacity": 0.75}})

    L.append({"id": "boundary", "type": "line", "source": "openmaptiles", "source-layer": "boundary",
              "filter": ["<=", ["get", "admin_level"], 4],
              "paint": {"line-color": c["boundary"], "line-dasharray": [3, 2], "line-width": 1}})

    # Labels.
    L.append({"id": "road-label", "type": "symbol", "source": "openmaptiles", "source-layer": "transportation_name", "minzoom": 14,
              "filter": cls("motorway", "trunk", "primary", "secondary", "tertiary", "minor"),
              "layout": {"symbol-placement": "line", "text-field": ["coalesce", ["get", "name:latin"], ["get", "name"]],
                         "text-font": FONT, "text-size": ["interpolate", ["linear"], ["zoom"], 14, 10, 18, 14],
                         "text-letter-spacing": 0.02, "symbol-spacing": 350},
              "paint": {"text-color": c["road_label"], "text-halo-color": c["road_halo"], "text-halo-width": 1.4}})
    L.append({"id": "road-shield", "type": "symbol", "source": "openmaptiles", "source-layer": "transportation_name", "minzoom": 8,
              "filter": ["all", cls("motorway", "trunk", "primary"), ["has", "ref"], ["<=", ["get", "ref_length"], 6]],
              "maxzoom": 16.5, "layout": {"symbol-placement": "line", "symbol-spacing": 900, "text-field": ["get", "ref"],
                         "text-font": FONT_BOLD, "text-size": 11, "text-rotation-alignment": "viewport",
                         "icon-image": "shield-yellow", "icon-text-fit": "both", "icon-text-fit-padding": [2, 5, 2, 5],
                         "icon-rotation-alignment": "viewport"},
              "paint": {"text-color": "#111111"}})
    # Places (OpenMapTiles poi): a coloured round icon per kind, name underneath.
    # What shows depends on zoom: landmarks from 12, stations from 13, the most
    # important places from 15, more from 16, nearly everything from 17.
    # Street furniture (gates, bollards, bins, shelters...) never shows.
    noise = ["gate", "lift_gate", "bollard", "waste_basket", "shelter", "swimming_pool", "brownfield", "cycle_barrier",
             "stile", "telephone", "drinking_water", "bicycle_parking", "motorcycle_parking", "pitch", "office",
             "information", "recycling", "bench", "vending", "entrance"]
    group = ["match", ["get", "class"],
             ["fuel"], "fuel",
             ["restaurant", "fast_food", "cafe", "bar", "ice_cream", "bakery", "beer", "food_court"], "food",
             ["shop", "grocery", "clothing_store", "hairdresser", "laundry", "alcohol_shop", "butcher", "furniture", "hardware", "electronics", "gift", "shoes"], "shop",
             ["hospital", "pharmacy", "dentist", "doctors"], "health",
             ["place_of_worship"], "worship",
             ["school", "college", "library", "kindergarten"], "edu",
             ["railway", "bus"], "transit",
             ["park", "garden", "playground", "golf", "sports_centre", "stadium", "picnic_site", "campsite"], "park",
             ["lodging"], "lodging",
             ["bank", "atm"], "money",
             ["police", "post", "town_hall", "fire_station"], "civic",
             ["parking"], "parking",
             ["toll_booth"], "toll",
             ["attraction", "museum", "theatre", "cinema", "theme_park", "art_gallery", "castle", "zoo"], "fun",
             ["toilets"], "toilet",
             ["car"], "car",
             "other"]
    not_noise = ["!", ["in", ["get", "class"], ["literal", noise]]]
    named = ["has", "name"]
    poi_layout = {"icon-image": ["concat", "navos-poi-", group], "icon-size": 1,
                  "text-field": ["coalesce", ["get", "name:latin"], ["get", "name"]], "text-font": FONT,
                  "text-size": 11, "text-anchor": "top", "text-offset": [0, 0.95], "text-max-width": 8,
                  "text-optional": True, "symbol-sort-key": ["get", "rank"]}
    poi_paint = {"text-color": c["label"], "text-halo-color": c["label_halo"], "text-halo-width": 1.3}
    landmark = ["any", ["in", ["get", "subclass"], ["literal", ["mall", "hospital", "university", "stadium", "theme_park", "zoo"]]],
                ["all", ["==", ["get", "class"], "hospital"], ["<=", ["get", "rank"], 2]]]
    L.append({"id": "poi-major", "type": "symbol", "source": "openmaptiles", "source-layer": "poi", "minzoom": 12,
              "filter": ["all", named, landmark], "layout": dict(poi_layout, **{"text-size": 12, "symbol-sort-key": 0}), "paint": poi_paint})
    L.append({"id": "transit-label", "type": "symbol", "source": "openmaptiles", "source-layer": "poi", "minzoom": 13, "maxzoom": 15,
              "filter": ["all", named, ["==", ["get", "class"], "railway"], ["in", ["get", "subclass"], ["literal", ["station", "halt"]]]],
              "layout": poi_layout, "paint": poi_paint})
    always = ["any", ["in", ["get", "class"], ["literal", ["fuel", "toll_booth", "railway"]]],
              ["all", ["==", ["get", "class"], "hospital"], ["==", ["get", "subclass"], "hospital"]]]
    # (zoom from, to, top rank shown, icon also without room for its name)
    for zmin, zmax, top, lone_icon in ((15, 16, 8, False), (16, 17, 40, False), (17, 18, 120, True), (18, 24, 10000, True)):
        f = ["all", named, not_noise, ["!", landmark], ["any", always, ["<=", ["get", "rank"], top]]]
        if zmin < 17:
            f.append(["!", ["in", ["get", "class"], ["literal", ["bus", "toilets", "atm", "car"]]]])  # small stuff only close in
            f.append(["!=", group, "other"])
        L.append({"id": f"poi-label-{zmin}", "type": "symbol", "source": "openmaptiles", "source-layer": "poi",
                  "minzoom": zmin, "maxzoom": zmax, "filter": f, "layout": dict(poi_layout, **{"text-optional": lone_icon}), "paint": poi_paint})
    # Toll plazas from z13 (they matter for the route) even without a name.
    L.append({"id": "toll-booth", "type": "symbol", "source": "openmaptiles", "source-layer": "poi", "minzoom": 13, "maxzoom": 15,
              "filter": ["==", ["get", "class"], "toll_booth"], "layout": dict(poi_layout, **{"icon-image": "navos-poi-toll"}), "paint": poi_paint})
    # House numbers, very close in.
    L.append({"id": "housenumber", "type": "symbol", "source": "openmaptiles", "source-layer": "housenumber", "minzoom": 17.5,
              "layout": {"text-field": ["get", "housenumber"], "text-font": FONT, "text-size": 10},
              "paint": {"text-color": c["label"], "text-opacity": 0.7, "text-halo-color": c["label_halo"], "text-halo-width": 1}})
    L.append({"id": "water-label", "type": "symbol", "source": "openmaptiles", "source-layer": "water_name",
              "layout": {"text-field": ["get", "name"], "text-font": FONT, "text-size": 12},
              "paint": {"text-color": c["water_label"], "text-halo-color": c["land"], "text-halo-width": 1}})
    L.append({"id": "place-label", "type": "symbol", "source": "openmaptiles", "source-layer": "place",
              "filter": cls("city", "town", "suburb", "village", "neighbourhood"),
              "layout": {"text-field": ["coalesce", ["get", "name:latin"], ["get", "name"]], "text-font": FONT_BOLD,
                         "text-size": ["match", ["get", "class"], "city", 16, "town", 14, 12],
                         "text-transform": ["match", ["get", "class"], ["suburb", "neighbourhood"], "uppercase", "none"],
                         "text-letter-spacing": ["match", ["get", "class"], ["suburb", "neighbourhood"], 0.08, 0]},
              "paint": {"text-color": c["place"], "text-halo-color": c["label_halo"], "text-halo-width": 1.5}})
    return {
        "version": 8,
        "name": f"NavOS Jalan {theme}",
        "metadata": {"navos:theme": theme, "attribution": "OpenFreeMap, OpenMapTiles, OpenStreetMap contributors"},
        "glyphs": GLYPHS,
        "sources": {"openmaptiles": {"type": "vector", "url": TILES,
                            "attribution": "<a href=\"https://openfreemap.org\">OpenFreeMap</a> © OpenMapTiles © OpenStreetMap contributors"}},
        "light": {"anchor": "viewport", "intensity": 0.25},
        "layers": L,
    }


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    for theme in PALETTE:
        s = build(theme)
        with open(f"{out}/jalan-{theme}.json", "w") as fh:
            json.dump(s, fh, separators=(",", ":"))
        print(theme, len(s["layers"]), "layers")
