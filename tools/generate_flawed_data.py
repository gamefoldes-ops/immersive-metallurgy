"""Regenerates the flawed-metal data pack for every metal in IMAlloys.

Identities (fluids, items, tints) live in src/main/java/.../registry/IMAlloys.java and are read back from that
file here, so the two cannot drift; what TFC keeps in its own data - the melt temperature, the forging and
welding temperatures, and the specific heat of each metal - is read out of the TFC jar. Run
`python tools/generate_flawed_data.py` after changing the Java list or updating TFC.

Item art is not written here: generate_flawed_textures.py derives it from TFC's own textures.
"""

import json
import os
import re
import struct
import zipfile
import zlib

MOD = "immersive_metallurgy"
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, os.pardir, "src", "main", "resources")
IDENTITIES = os.path.join(HERE, os.pardir, "src", "main", "java", "immersivemetallurgy", "registry", "IMAlloys.java")

INGOT_HEAT_CAPACITY = 2.857143
DOUBLE_INGOT_HEAT_CAPACITY = 5.714286
MILLIBUCKETS = 100
CAST_BREAK_CHANCE = 0.1



def default_jar():
    base = os.environ.get("APPDATA", "")
    return os.path.join(base, ".minecraft", "versions", "TFC+Millenaire", "mods",
                        "TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar")


def identities():
    """The rows of IMAlloys.ALL, in declaration order: key, display, tint, double ingot."""
    source = open(IDENTITIES, encoding="utf-8").read()
    return [(key, display, int(tint, 16) & 0xFFFFFF, is_double == "true")
            for key, display, tint, is_double
            in re.findall(r'new IMAlloys\("(\w+)", "(.+?)", 0x([0-9A-Fa-f]{6,8}), (true|false)\)', source)]


def tfc_temperatures(archive):
    """melt temperature and specific heat per metal, straight from TFC's own tfc:fluid_heat data."""
    heat = {}
    for name in archive.namelist():
        if name.startswith("data/tfc/tfc/fluid_heat/") and name.endswith(".json"):
            entry = json.loads(archive.read(name))
            heat[entry["fluid"].split("/")[-1]] = (entry["melt_temperature"], entry["specific_heat_capacity"])
    return heat


def tfc_work_temperatures(archive, metal):
    """forging and welding temperature of a clean ingot of this metal, from TFC's tfc:item_heat data."""
    name = f"data/tfc/tfc/item_heat/{metal}/ingot.json"
    if name not in archive.namelist():
        return 0.0, 0.0
    entry = json.loads(archive.read(name))
    return entry.get("forging_temperature", 0.0), entry.get("welding_temperature", 0.0)


# Which mold items TFC actually pours each metal's ingot in. Some metals have two: a plain ingot mold and
# a fire clay one, and a flawed recipe that only knows the first makes the flawed metal uncastable in the
# mold the player is holding.
CASTING = {}


def collect_casting(archive):
    for name in archive.namelist():
        if not name.startswith("data/tfc/recipe/casting/") or not name.endswith(".json"):
            continue
        recipe = json.loads(archive.read(name))
        result = recipe.get("result") or {}
        result_id = result.get("id", "")
        if not result_id.startswith("tfc:metal/ingot/"):
            continue
        key = result_id.rsplit("/", 1)[-1]
        fluid = recipe.get("fluid", {})
        mold = recipe.get("mold", {}).get("item")
        if mold and fluid.get("fluid") == f"tfc:metal/{key}":
            CASTING.setdefault(key, []).append((mold, fluid.get("amount", MILLIBUCKETS),
                                                recipe.get("break_chance", CAST_BREAK_CHANCE)))


def build_alloys():
    jar = default_jar()
    if not os.path.exists(jar):
        raise SystemExit(f"TFC jar not found at {jar}; pass nothing but fix the path in default_jar()")
    with zipfile.ZipFile(jar) as archive:
        collect_casting(archive)
        heat = tfc_temperatures(archive)
        rows = []
        for key, display, tint, double_ingot in identities():
            if key not in heat:
                raise SystemExit(f"{key} is in IMAlloys.ALL but TFC has no fluid_heat for it")
            melt, specific_heat = heat[key]
            forging, welding = tfc_work_temperatures(archive, key)
            rows.append((key, display, melt, forging, welding, double_ingot, tint, specific_heat))
        return rows


ALLOYS = build_alloys()


import shutil

CASTING_FOLDER = os.path.join(RES, "data", MOD, "recipe", "casting")
if os.path.isdir(CASTING_FOLDER):
    shutil.rmtree(CASTING_FOLDER)


def write(*path, value):
    full = os.path.join(RES, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, separators=(",", ":"), sort_keys=False)
        handle.write("\n")


def write_png_if_missing(*path, pixels):
    """A 16x16 RGBA placeholder, so a new metal shows a coloured ingot rather than the missing-texture grid."""
    full = os.path.join(RES, *path)
    if os.path.exists(full):
        return
    os.makedirs(os.path.dirname(full), exist_ok=True)
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, body):
        return (struct.pack(">I", len(body)) + kind + body
                + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF))

    with open(full, "wb") as handle:
        handle.write(b"\x89PNG\r\n\x1a\n")
        handle.write(chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 6, 0, 0, 0)))
        handle.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        handle.write(chunk(b"IEND", b""))


def make_ingot(color, double):
    empty = (0, 0, 0, 0)
    top = tuple(min(255, int(c * 1.25)) for c in ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF))
    mid = ((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF)
    dark = tuple(int(c * 0.7) for c in mid)

    rows = []
    left_edge, right_edge = (2, 13) if double else (3, 12)
    top_row, bottom_row = (5, 11) if double else (6, 10)
    for y in range(16):
        row = []
        for x in range(16):
            if not (top_row <= y <= bottom_row and left_edge <= x <= right_edge):
                row.append(empty)
                continue
            if y == top_row:
                row.append(top + (255,))
            elif y == bottom_row:
                row.append(dark + (255,))
            elif x == left_edge or x == right_edge:
                row.append(dark + (255,))
            else:
                row.append(mid + (255,))
        rows.append(row)
    return rows


lang = {
    "itemGroup.immersive_metallurgy": "Immersive Metallurgy",
    "immersive_metallurgy.screen.skim_ready": "[skim ready]",
    "immersive_metallurgy.tooltip.defects": "Defects: %s",
    "immersive_metallurgy.name.flawed": "Flawed %s",
    "immersive_metallurgy.tooltip.sharpness": "Edge: %s",
    "immersive_metallurgy.tooltip.structure": "Structure: %s of clean metal",
    "immersive_metallurgy.tooltip.damage": "Attack damage: %s",
    "immersive_metallurgy.tooltip.mining": "Mining speed: %s",
    "immersive_metallurgy.tooltip.blunting": "Dulling: %s",
    "immersive_metallurgy.tooltip.chipping": "Chipping: %s per hit",
    "immersive_metallurgy.defect.brittle": "Brittle",
    "immersive_metallurgy.defect.soft": "Soft",
    "immersive_metallurgy.defect.edge_wear": "Chips easily",
    "immersive_metallurgy.defect.flowy": "Cold shut",
    "immersive_metallurgy.tooltip.grade": "Grade: %s",
    "immersive_metallurgy.grade.light": "Minor",
    "immersive_metallurgy.grade.medium": "Major",
    "immersive_metallurgy.grade.heavy": "Severe",
    f"metal.{MOD}.slag": "Slag",
    f"fluid.{MOD}.slag": "Slag",
    f"item.{MOD}.slag_dust": "Slag Dust",
    "immersive_metallurgy.molten": "Molten %s",
}

mold_fluids = []
ingot_tag_values = []
double_ingot_tag_values = []

for key, display, melt, forging, welding, has_double, tint, specific_heat in ALLOYS:
    flawed = f"flawed_{key}"
    fluid = f"{MOD}:metal/{flawed}"
    ingot = f"{MOD}:metal/ingot/{flawed}"
    double_ingot = f"{MOD}:metal/double_ingot/{flawed}"

    write("data", MOD, "tfc", "fluid_heat", f"{flawed}.json", value={
        "fluid": fluid,
        "melt_temperature": melt,
        "specific_heat_capacity": specific_heat,
    })

    # TFC renders molten metal from its own fluid type, but a fluid block still needs a state and model,
    # or the client fills the log with missing-model warnings for every new metal.
    write("assets", MOD, "blockstates", "fluid", "metal", f"{flawed}.json", value={
        "variants": {"": {"model": f"{MOD}:block/fluid/metal/{flawed}"}},
    })
    write("assets", MOD, "models", "block", "fluid", "metal", f"{flawed}.json", value={
        "textures": {"particle": "tfc:block/molten_still"},
    })

    molds = CASTING.get(key) or [("tfc:ceramic/ingot_mold", MILLIBUCKETS, CAST_BREAK_CHANCE)]
    for mold_item, amount, break_chance in molds:
        write("data", MOD, "recipe", "casting",
              f"{flawed}_ingot_{mold_item.rsplit('/', 1)[-1]}.json", value={
            "type": "tfc:casting",
            "break_chance": break_chance,
            "fluid": {"amount": amount, "fluid": fluid},
            "mold": {"item": mold_item},
            "result": {"count": 1, "id": ingot},
        })

    write("data", MOD, "recipe", "heating", "metal", "ingot", f"{flawed}.json", value={
        "type": "tfc:heating",
        "ingredient": {"tag": f"c:ingots/{flawed}"},
        "result_fluid": {"amount": MILLIBUCKETS, "id": fluid},
        "temperature": melt,
    })

    write("data", MOD, "tfc", "item_heat", f"{flawed}_ingot.json", value={
        "ingredient": {"tag": f"c:ingots/{flawed}"},
        "forging_temperature": forging,
        "welding_temperature": welding,
        "heat_capacity": INGOT_HEAT_CAPACITY,
    })

    write("data", "c", "tags", "item", "ingots", f"{flawed}.json", value={"values": [ingot]})
    write("assets", MOD, "models", "item", "metal", "ingot", f"{flawed}.json", value={
        "parent": "item/generated",
        "textures": {"layer0": f"{MOD}:item/metal/ingot/{flawed}"},
    })

    # Skimmed slag is named for the bath it was lifted off, because what it is good for depends on the metal
    # still hanging in it. The plain dust stays for a bath that is not a metal we can name.
    write("assets", MOD, "models", "item", "slag_dust", f"{key}.json", value={
        "parent": "item/generated",
        "textures": {"layer0": f"{MOD}:item/slag_dust/{key}"},
    })
    lang[f"item.{MOD}.slag_dust/{key}"] = f"{display} Slag Dust"

    ingot_tag_values.append(f"#c:ingots/{flawed}")
    mold_fluids.append(fluid)

    # The fluid is named after the metal it is; "Molten" is added where the game knows the
    # contents are above their melting point, so tooltips and JEI stop claiming a cold object is melt.
    lang[f"fluid.{MOD}.{flawed}"] = f"Flawed {display}"
    lang[f"metal.{MOD}.{flawed}"] = f"Flawed {display}"
    lang[f"item.{MOD}.metal.ingot.{flawed}"] = f"Flawed {display} Ingot"
    lang[f"tag.item.c.ingots.{flawed}"] = f"Flawed {display} Ingots"

    if not has_double:
        continue

    write("data", MOD, "recipe", "heating", "metal", "double_ingot", f"{flawed}.json", value={
        "type": "tfc:heating",
        "ingredient": {"tag": f"c:double_ingots/{flawed}"},
        "result_fluid": {"amount": MILLIBUCKETS * 2, "id": fluid},
        "temperature": melt,
    })

    write("data", MOD, "recipe", "welding", "metal", "double_ingot", f"{flawed}.json", value={
        "type": "tfc:welding",
        "first_input": {"tag": f"c:ingots/{flawed}"},
        "second_input": {"tag": f"c:ingots/{flawed}"},
        "tier": 1,
        "result": {"count": 1, "id": double_ingot},
    })

    write("data", MOD, "tfc", "item_heat", f"{flawed}_double_ingot.json", value={
        "ingredient": {"tag": f"c:double_ingots/{flawed}"},
        "forging_temperature": forging,
        "welding_temperature": welding,
        "heat_capacity": DOUBLE_INGOT_HEAT_CAPACITY,
    })

    write("data", "c", "tags", "item", "double_ingots", f"{flawed}.json", value={"values": [double_ingot]})
    write("assets", MOD, "models", "item", "metal", "double_ingot", f"{flawed}.json", value={
        "parent": "item/generated",
        "textures": {"layer0": f"{MOD}:item/metal/double_ingot/{flawed}"},
    })

    double_ingot_tag_values.append(f"#c:double_ingots/{flawed}")
    lang[f"item.{MOD}.metal.double_ingot.{flawed}"] = f"Flawed {display} Double Ingot"
    lang[f"tag.item.c.double_ingots.{flawed}"] = f"Flawed {display} Double Ingots"

# Nothing is written to the mold tags here: generate_flawed_part_recipes.py extends TFC's own fluid tags
# from the jar, which is what makes a flawed melt pourable wherever its clean metal is. Slag stays out of
# all of them, because it is the denominator of purity, not something a mold takes.
write("data", "c", "tags", "item", "ingots", "flawed.json", value={"values": ingot_tag_values})
write("data", "c", "tags", "item", "double_ingots", "flawed.json", value={"values": double_ingot_tag_values})
lang["tag.item.c.ingots.flawed"] = "Flawed Ingots"
lang["tag.item.c.double_ingots.flawed"] = "Flawed Double Ingots"

# Wood would not survive touching a bath of molten metal, so the plain bowl is not a skimmer.
write("data", MOD, "tags", "item", "slag_skimmers.json", value={"values": ["tfc:ceramic/bowl"]})
write("data", MOD, "tags", "item", "burns_in_the_crucible.json", value={"values": ["minecraft:bowl"]})

# The one item that is not a metal, but exists because skimmed slag has to go somewhere.
write("assets", MOD, "models", "item", "slag_dust.json", value={
    "parent": "item/generated",
    "textures": {"layer0": f"{MOD}:item/slag_dust"},
})
write("assets", MOD, "lang", "en_us.json", value=dict(sorted(lang.items())))

doubles = sum(1 for alloy in ALLOYS if alloy[5])
print(f"flawed data written for {len(ALLOYS)} alloys, {doubles} with double ingots")
