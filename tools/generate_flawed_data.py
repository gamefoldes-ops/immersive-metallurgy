"""Regenerates the flawed-metal data pack for every alloy in IMAlloys.

Keep ALLOYS in sync with src/main/java/.../registry/IMAlloys.java: the Java list owns identities
(fluids, items, tints), this table owns the temperatures a flawed alloy inherits from its clean metal,
which TFC keeps in data. Run `python tools/generate_flawed_data.py` after changing it.

Item textures live under our own namespace so they can be redrawn one by one; a placeholder is only
written when the file is missing, so real art is never overwritten.
"""

import json
import os
import struct
import zlib

MOD = "immersive_metallurgy"
RES = os.path.join(os.path.dirname(__file__), os.pardir, "src", "main", "resources")

SPECIFIC_HEAT = 0.008571429
INGOT_HEAT_CAPACITY = 2.857143
DOUBLE_INGOT_HEAT_CAPACITY = 5.714286
MILLIBUCKETS = 100
CAST_BREAK_CHANCE = 0.1

ALLOYS = [
    ("bronze", "Bronze", 950.0, 570.0, 760.0, True, 0x9B7A44),
    ("bismuth_bronze", "Bismuth Bronze", 985.0, 591.0, 788.0, True, 0x7F8A76),
    ("black_bronze", "Black Bronze", 1070.0, 642.0, 856.0, True, 0x4E4A55),
    ("brass", "Brass", 930.0, 558.0, 744.0, True, 0x9A8A4E),
    ("rose_gold", "Rose Gold", 960.0, 576.0, 768.0, True, 0xA87B6E),
    ("sterling_silver", "Sterling Silver", 950.0, 570.0, 760.0, True, 0x8C8F93),
    ("weak_steel", "Weak Steel", 1540.0, 924.00006, 1232.0, False, 0x5B6470),
    ("weak_blue_steel", "Weak Blue Steel", 1540.0, 924.00006, 1232.0, False, 0x4F6373),
    ("weak_red_steel", "Weak Red Steel", 1540.0, 924.00006, 1232.0, False, 0x6E5348),
]


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
    "immersive_metallurgy.screen.metal_totals": "Total metals",
    "immersive_metallurgy.tooltip.defects": "Defects: %s",
    "immersive_metallurgy.tooltip.sharpness": "Edge: %s",
    "immersive_metallurgy.tooltip.structure": "Structure: %s of clean metal",
    "immersive_metallurgy.tooltip.damage": "Attack damage: %s",
    "immersive_metallurgy.tooltip.mining": "Mining speed: %s",
    "immersive_metallurgy.tooltip.blunting": "Dulling: %s",
    "immersive_metallurgy.tooltip.chipping": "Chipping: %s per hit",
    f"item.{MOD}.whetstone": "Whetstone",
    "immersive_metallurgy.defect.brittle": "Brittle",
    "immersive_metallurgy.defect.soft": "Soft",
    "immersive_metallurgy.defect.edge_wear": "Chips easily",
    "immersive_metallurgy.defect.flowy": "Cold shut",
    f"metal.{MOD}.slag": "Slag",
    f"fluid.{MOD}.slag": "Molten Slag",
}

mold_fluids = []
ingot_tag_values = []
double_ingot_tag_values = []

for key, display, melt, forging, welding, has_double, tint in ALLOYS:
    flawed = f"flawed_{key}"
    fluid = f"{MOD}:metal/{flawed}"
    ingot = f"{MOD}:metal/ingot/{flawed}"
    double_ingot = f"{MOD}:metal/double_ingot/{flawed}"

    write("data", MOD, "tfc", "fluid_heat", f"{flawed}.json", value={
        "fluid": fluid,
        "melt_temperature": melt,
        "specific_heat_capacity": SPECIFIC_HEAT,
    })

    # TFC renders molten metal from its own fluid type, but a fluid block still needs a state and model,
    # or the client fills the log with missing-model warnings for every new metal.
    write("assets", MOD, "blockstates", "fluid", "metal", f"{flawed}.json", value={
        "variants": {"": {"model": f"{MOD}:block/fluid/metal/{flawed}"}},
    })
    write("assets", MOD, "models", "block", "fluid", "metal", f"{flawed}.json", value={
        "textures": {"particle": "tfc:block/molten_still"},
    })

    write("data", MOD, "recipe", "casting", f"{flawed}_ingot.json", value={
        "type": "tfc:casting",
        "break_chance": CAST_BREAK_CHANCE,
        "fluid": {"amount": MILLIBUCKETS, "fluid": fluid},
        "mold": {"item": "tfc:ceramic/ingot_mold"},
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
    write_png_if_missing("assets", MOD, "textures", "item", "metal", "ingot", f"{flawed}.png", pixels=make_ingot(tint, False))

    ingot_tag_values.append(f"#c:ingots/{flawed}")
    mold_fluids.append(fluid)

    lang[f"fluid.{MOD}.{flawed}"] = f"Molten Flawed {display}"
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
    write_png_if_missing("assets", MOD, "textures", "item", "metal", "double_ingot", f"{flawed}.png", pixels=make_ingot(tint, True))

    double_ingot_tag_values.append(f"#c:double_ingots/{flawed}")
    lang[f"item.{MOD}.metal.double_ingot.{flawed}"] = f"Flawed {display} Double Ingot"
    lang[f"tag.item.c.double_ingots.{flawed}"] = f"Flawed {display} Double Ingots"

# A mold only accepts fluids that its own tag lists, and TFC's definition of that tag is clean metal.
write("data", "tfc", "tags", "fluid", "usable_in_ingot_mold.json",
      value={"values": mold_fluids + [f"{MOD}:slag"]})
write("data", "c", "tags", "item", "ingots", "flawed.json", value={"values": ingot_tag_values})
write("data", "c", "tags", "item", "double_ingots", "flawed.json", value={"values": double_ingot_tag_values})
lang["tag.item.c.ingots.flawed"] = "Flawed Ingots"
lang["tag.item.c.double_ingots.flawed"] = "Flawed Double Ingots"

write_png_if_missing("assets", MOD, "textures", "item", "whetstone.png", pixels=make_ingot(0x6B6B6B, True))
write("assets", MOD, "lang", "en_us.json", value=dict(sorted(lang.items())))

doubles = sum(1 for alloy in ALLOYS if alloy[5])
print(f"flawed data written for {len(ALLOYS)} alloys, {doubles} with double ingots")
