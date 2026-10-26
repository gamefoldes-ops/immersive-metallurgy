"""Writes the ore dressing chain: the quern recipes that crush ore, the barrel recipe that washes it, and the
heating, heat-capacity, model and language entries the two new items need.

Why the chain is shaped like this: TFC hands every ore lump to the furnace whole and calls its yield pure, so a
player has no way at all to part mineral from rock. Real dressing is two steps, and this copies them. Crushing
liberates the mineral grains from the rock without removing anything, so crushed ore is exactly as dirty as the
lump it came from and worth nothing on its own. Washing then sorts on density, which is the only thing a barrel
of water can do - and it only works on minerals that are much heavier than quartz. Hematite, limonite,
magnetite, cassiterite, malachite and the three native metals qualify; sphalerite, bismuthinite, tetrahedrite
and garnierite do not, and are not in the list below. Recovery is deliberately under a hundred percent, because
the fine grains a hand jig cannot catch go into the tailings.

Run `python tools/generate_ore_dressing.py` after changing the numbers below or updating TFC, then
`python tools/generate_flawed_textures.py` for the art and `python tools/generate_ore_fouling.py` for the dirt
those items bring into a melt.
"""

import argparse
import json
import os
import re
import zipfile

MOD = "immersive_metallurgy"
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, os.pardir, "src", "main", "resources")
ITEMS = os.path.join(HERE, os.pardir, "src", "main", "java", "immersivemetallurgy", "registry", "IMItems.java")

# The ore varieties gravity separation actually takes, minus the grade prefix TFC puts on the item id.
DRESSABLE_VARIETIES = ["hematite", "limonite", "magnetite", "cassiterite", "malachite",
                       "native_copper", "native_gold", "native_silver"]

# What one crushed piece is worth, and what the washed batch is worth. A whole lump melts to 10, 15, 25 or 35
# millibuckets by TFC's grade, so the crushing count below is that divided by CRUSHED_MILLIBUCKETS.
CRUSHED_MILLIBUCKETS = 5
CONCENTRATE_MILLIBUCKETS = 10
CRUSHING_BY_GRADE = {"small": 2, "poor": 3, "normal": 5, "rich": 7}

# Five crushed pieces in, two concentrates out, in a hundred millibuckets of water: twenty metal out of
# twenty-five, which is the eightyness a hand jig gets, and the loss is the price of the cleaning.
WASH_INPUT = 5
WASH_OUTPUT = 2
WASH_WATER = 100

POWDER_HEAT_CAPACITY = 0.7

# The ore to borrow art and the melt temperature from, per dressed metal.
REPRESENTATIVE = {"cast_iron": "hematite", "copper": "malachite", "gold": "native_gold",
                  "silver": "native_silver", "tin": "cassiterite"}

# An ore is named for the metal it is dressed into, and nobody calls a rock "cast iron".
DISPLAY = {"cast_iron": "Iron", "copper": "Copper", "gold": "Gold", "silver": "Silver", "tin": "Tin"}


def default_jar():
    base = os.environ.get("APPDATA", "")
    return os.path.join(base, ".minecraft", "versions", "TFC+Millenaire", "mods",
                        "TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar")


def metals_in_code():
    """The dressed metals, read back from the item registrations so the two cannot drift apart."""
    source = open(ITEMS, encoding="utf-8").read()
    row = re.search(r"DRESSABLE_METALS = List\.of\(([^)]*)\)", source)
    if row is None:
        raise SystemExit("IMItems.DRESSABLE_METALS not found")
    return re.findall(r'"(\w+)"', row.group(1))


def write(*path, value):
    full = os.path.join(RES, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, separators=(",", ":"))
        handle.write("\n")


def merge_lang(keys):
    """One language file, several writers: this tool adds its own rows without dropping anybody else's."""
    path = os.path.join(RES, "assets", MOD, "lang", "en_us.json")
    lang = {}
    if os.path.exists(path):
        lang = json.load(open(path, encoding="utf-8"))
    lang.update(keys)
    write("assets", MOD, "lang", "en_us.json", value=dict(sorted(lang.items())))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=default_jar())
    args = parser.parse_args()

    metals = metals_in_code()
    archive = zipfile.ZipFile(args.jar)

    # What each ore variety melts into, straight out of TFC's own recipes: this is where metal -> fluid comes
    # from, so a dressed item never claims a metal TFC would not have given for that rock.
    grades = tuple(CRUSHING_BY_GRADE)
    yields = {}
    for name in archive.namelist():
        if not name.startswith("data/tfc/recipe/heating/ore/") or not name.endswith(".json"):
            continue
        recipe = json.loads(archive.read(name))
        item = recipe.get("ingredient", {}).get("item", "")
        fluid = recipe.get("result_fluid", {}).get("id", "")
        if not item.startswith("tfc:ore/") or not fluid.startswith("tfc:metal/"):
            continue
        base = item[len("tfc:ore/"):]
        grade = next((g for g in grades if base.startswith(g + "_")), None)
        if grade:
            yields[base[len(grade) + 1:]] = (fluid[len("tfc:metal/"):], recipe.get("temperature", 0.0))

    lang = {}
    for metal in metals:
        if metal not in {yields.get(v, ("",))[0] for v in DRESSABLE_VARIETIES}:
            raise SystemExit(f"{metal} is in IMItems.DRESSABLE_METALS but no dressable ore melts to it")
        variety = REPRESENTATIVE[metal]
        _, melt = yields[variety]
        crushed = f"{MOD}:crushed_ore/{metal}"
        concentrate = f"{MOD}:ore_concentrate/{metal}"
        display = DISPLAY[metal]

        for ore in DRESSABLE_VARIETIES:
            if yields.get(ore, ("",))[0] != metal:
                continue
            for grade, count in CRUSHING_BY_GRADE.items():
                write("data", MOD, "recipe", "quern", f"{grade}_{ore}.json", value={
                    "type": "tfc:quern",
                    "ingredient": {"item": f"tfc:ore/{grade}_{ore}"},
                    "result": {"count": count, "id": crushed},
                })

        write("data", MOD, "recipe", "heating", "ore", f"crushed_{metal}.json", value={
            "type": "tfc:heating",
            "ingredient": {"item": crushed},
            "result_fluid": {"amount": CRUSHED_MILLIBUCKETS, "id": f"tfc:metal/{metal}"},
            "temperature": melt,
        })
        write("data", MOD, "recipe", "heating", "ore", f"concentrate_{metal}.json", value={
            "type": "tfc:heating",
            "ingredient": {"item": concentrate},
            "result_fluid": {"amount": CONCENTRATE_MILLIBUCKETS, "id": f"tfc:metal/{metal}"},
            "temperature": melt,
        })
        write("data", MOD, "recipe", "barrel", f"wash_{metal}.json", value={
            "type": "tfc:barrel_instant",
            "input_fluid": {"amount": WASH_WATER, "fluid": "minecraft:water"},
            "input_item": {"count": WASH_INPUT, "item": crushed},
            "output_item": {"count": WASH_OUTPUT, "id": concentrate},
        })

        for folder, item, suffix in (("crushed_ore", crushed, "Crushed {} Ore"), ("ore_concentrate", concentrate, "{} Concentrate")):
            write("data", MOD, "tfc", "item_heat", f"{folder}_{metal}.json", value={
                "ingredient": {"item": item},
                "heat_capacity": POWDER_HEAT_CAPACITY,
            })
            write("assets", MOD, "models", "item", folder, f"{metal}.json", value={
                "parent": "item/generated",
                "textures": {"layer0": f"{MOD}:item/{folder}/{metal}"},
            })
            lang[f"item.{MOD}.{folder}.{metal}"] = suffix.format(display)

    merge_lang(lang)
    print(f"ore dressing written for {len(metals)} metals: {metals}")


if __name__ == "__main__":
    main()
