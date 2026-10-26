"""Shared TFC ore facts for the dressing chain, read out of the TFC jar so nothing here can drift from the game:
the metal and melt temperature an ore variety gives, how many scoops its quern recipe yields, and what the
player calls the mineral.

Used by `generate_ore_dressing.py` (recipes, heat data, models, language), `generate_ore_fouling.py` (how much
gangue each state of the rock brings into a bath) and `generate_flawed_textures.py` (the art).
"""

import json
import os
import re

ITEMS = os.path.join(HERE := os.path.dirname(os.path.abspath(__file__)), os.pardir, "src", "main", "java",
                     "immersivemetallurgy", "registry", "IMItems.java")

GRADES = ("rich", "normal", "poor", "small")


def default_jar():
    base = os.environ.get("APPDATA", "")
    return os.path.join(base, ".minecraft", "versions", "TFC+Millenaire", "mods",
                        "TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar")


def varieties_in_code():
    """The dressed ore varieties, read back from the item registrations so the two cannot drift apart."""
    source = open(ITEMS, encoding="utf-8").read()
    row = re.search(r"ORE_VARIETIES = List\.of\(([^)]*)\)", source, re.S)
    if row is None:
        raise SystemExit("IMItems.ORE_VARIETIES not found")
    return re.findall(r'"([\w]+)"', row.group(1))


def ore_yields(archive):
    """variety -> (metal, melt temperature), out of TFC's own heating recipes for the lump."""
    yields = {}
    for name in archive.namelist():
        if not name.startswith("data/tfc/recipe/heating/ore/") or not name.endswith(".json"):
            continue
        recipe = json.loads(archive.read(name))
        item = recipe.get("ingredient", {}).get("item", "")
        fluid = recipe.get("result_fluid", {}).get("id", "")
        if not item.startswith("tfc:ore/") or not fluid.startswith("tfc:metal/"):
            continue
        variety = re.sub(rf"^({'|'.join(GRADES)}_)", "", item[len("tfc:ore/"):])
        yields[variety] = (fluid[len("tfc:metal/"):], recipe.get("temperature", 0.0))
    return yields


def quern_counts(archive):
    """variety -> how many scoops of powder TFC's quern recipe gives per grade.

    The dressing chain borrows these numbers rather than inventing its own: a crushed scoop is worth exactly
    what a TFC powder scoop was, so one normal lump still carries twenty-five millibuckets of metal whichever
    way the player grinds it.
    """
    counts = {}
    for name in archive.namelist():
        if not name.startswith("data/tfc/recipe/quern/") or not name.endswith(".json"):
            continue
        recipe = json.loads(archive.read(name))
        ingredient = recipe.get("ingredient")
        if not isinstance(ingredient, dict) or not ingredient.get("item", "").startswith("tfc:ore/"):
            continue
        item = ingredient["item"][len("tfc:ore/"):]
        grade = next((g for g in GRADES if item.startswith(g + "_")), None)
        powder = (recipe.get("result") or {}).get("id", "")
        if grade and powder.startswith("tfc:powder/"):
            counts.setdefault(powder[len("tfc:powder/"):], {})[grade] = (recipe["result"].get("count", 1), name)
    return counts


def variety_names(archive):
    """variety -> the mineral's own name, from TFC's language file with the grade word taken off it."""
    lang = json.loads(archive.read("assets/tfc/lang/en_us.json"))
    names = {}
    for name in lang:
        match = re.fullmatch(r"item\.tfc\.ore\.(?:rich|normal|poor|small)_(\w+)", name)
        if not match:
            continue
        label = lang[name]
        for grade in ("Rich", "Normal", "Poor", "Small"):
            if label.startswith(grade + " "):
                label = label[len(grade) + 1:]
        names[match.group(1)] = label
    return names
