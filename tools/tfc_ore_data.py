"""Shared TFC ore facts for the dressing chain, read out of the TFC jar so nothing here can drift from the game:
which metal an ore variety melts to and at what temperature, which powder the quern grinds it into, and which
of those varieties water can wash.

Used by `generate_ore_dressing.py` (recipes, heat data, models, language), `generate_ore_fouling.py` (how much
gangue each of them brings into a bath) and `generate_flawed_textures.py` (the art).
"""

import os
import re
import json

HERE = os.path.dirname(os.path.abspath(__file__))
ITEMS = os.path.join(HERE, os.pardir, "src", "main", "java", "immersivemetallurgy", "registry", "IMItems.java")

GRADES = ("rich", "normal", "poor", "small")

# The ore varieties gravity separation actually takes, by their name without TFC's grade prefix. Hematite,
# limonite, magnetite, cassiterite, malachite and the three native metals are far heavier than quartz;
# sphalerite, bismuthinite, tetrahedrite and garnierite are not, and no amount of washing cleans them.
DRESSABLE_VARIETIES = ["hematite", "limonite", "magnetite", "cassiterite", "malachite",
                       "native_copper", "native_gold", "native_silver"]

# The variety whose art and melt temperature each dressed metal borrows, and what the player calls it: nobody
# holds a handful of rock and calls it cast iron.
REPRESENTATIVE = {"cast_iron": "hematite", "copper": "malachite", "gold": "native_gold",
                  "silver": "native_silver", "tin": "cassiterite"}
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


def ore_yields(archive):
    """metal and melt temperature per ore variety, out of TFC's own heating recipes for the lump."""
    yields = {}
    for name in archive.namelist():
        if not name.startswith("data/tfc/recipe/heating/ore/") or not name.endswith(".json"):
            continue
        recipe = json.loads(archive.read(name))
        item = recipe.get("ingredient", {}).get("item", "")
        fluid = recipe.get("result_fluid", {}).get("id", "")
        if not item.startswith("tfc:ore/") or not fluid.startswith("tfc:metal/"):
            continue
        variety = re.sub(rf"^({'|'.join(GRADES)})_", "", item[len("tfc:ore/"):])
        yields[variety] = (fluid[len("tfc:metal/"):], recipe.get("temperature", 0.0))
    return yields


def metal_powders(archive):
    """Every powder the quern grinds out of a metal-bearing ore, as powder id -> (metal, variety).

    TFC grinds twelve ores this way and then gives the powder no use at all: it has no item_heat and no heating
    recipe, so a ground lump is normally a dead end. Gems and the non-metal powders are not in here, because
    their ore does not melt to anything.
    """
    yields = ore_yields(archive)
    powders = {}
    for name in archive.namelist():
        if not name.startswith("data/tfc/recipe/quern/powder/") or not name.endswith(".json"):
            continue
        recipe = json.loads(archive.read(name))
        ingredient = recipe.get("ingredient")
        if not isinstance(ingredient, dict):
            continue
        item = ingredient.get("item", "")
        powder = (recipe.get("result") or {}).get("id", "")
        grade_variety = re.sub(rf"^tfc:ore/({'|'.join(GRADES)}_)", "", item)
        if not powder.startswith("tfc:powder/") or grade_variety == item:
            continue
        if grade_variety in yields:
            powders[powder] = (yields[grade_variety][0], grade_variety)
    return powders
