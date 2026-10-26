"""Writes the ore dressing chain: crush a lump on the quern, wash the scoops in a barrel, grind the washed ore
back into TFC's own ore powder.

The three states are ours except the last one. `crushed_ore/<ore>` is what the quern makes of a lump, and TFC's
own quern recipes are overridden at TFC's own paths on purpose: two recipes for the same input is not a choice
but a race, and TFC won that race the first time this chain shipped its own quern file. The scoop counts are read
out of TFC's recipes rather than invented, so a crushed scoop is worth what a powder scoop always was and a lump
holds the same metal whichever way the player grinds it - the grade surviving as a count.

`washed_ore/<ore>` is what a barrel of water makes of four scoops, and it is clean: it has no row in the fouling
table at all, which is how the game reads it, and that is the whole payoff of building a barrel. Four rather
than five so a full stack of scoops divides without a remainder left sitting in the barrel, and five pieces of
washed ore then fill one mold exactly. Gravity sorting is the honest reason for the oxides and the native metals;
for the sulfides the barrel stands in for the hand-picking and the roasting a real plant would have spent, which
is the one place this chain is a game rather than a furnace.

Grinding washed ore on the quern gives back `tfc:powder/<ore>` - TFC's own powder, still the pigment and the
glass colour it always was, and nothing else. It is deliberately not meltable: metal worth keeping is worth
keeping as washed ore, and a powder that also smelted would make the wash a detour rather than a step.

Run `python tools/generate_ore_dressing.py` after changing the numbers below or updating TFC, then
`python tools/generate_flawed_textures.py` for the art and `python tools/generate_ore_fouling.py` for the dirt
the crushed state still carries into a bath.
"""

import argparse
import json
import os
import shutil
import zipfile

import tfc_ore_data as ores

MOD = "immersive_metallurgy"
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, os.pardir, "src", "main", "resources")

# One scoop of anything is worth this much metal, so four of them wash into one piece carrying twenty
# millibuckets. Nothing in the chain spends metal: the barrel only spends water and the quern stroke.
MILLIBUCKETS_PER_SCOOP = 5
WASH_SCOOPS = 4
WASHED_MILLIBUCKETS = MILLIBUCKETS_PER_SCOOP * WASH_SCOOPS
WASH_WATER = 100
HEAT_CAPACITY = 0.7


def write(*path, value):
    full = os.path.join(RES, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, separators=(",", ":"))
        handle.write("\n")


def merge_lang(keys, varieties):
    """One language file, several writers: this tool adds its own rows, keeps everybody else's, and drops the
    names of items it no longer makes - including the ones it only ever renamed."""
    path = os.path.join(RES, "assets", MOD, "lang", "en_us.json")
    lang = {}
    if os.path.exists(path):
        renamed = f"item.{MOD}.ore_concentrate."
        live = tuple(f"item.{MOD}.{folder}." for folder in ("crushed_ore", "washed_ore"))
        lang = {name: value for name, value in json.load(open(path, encoding="utf-8")).items()
                if not name.startswith(renamed)
                and not (any(name.startswith(prefix) for prefix in live) and name.rsplit(".", 1)[-1] not in varieties)}
    lang.update(keys)
    write("assets", MOD, "lang", "en_us.json", value=dict(sorted(lang.items())))


def sweep():
    """What this chain owned in its earlier shapes, so a redesign leaves no data nobody can reach."""
    for folder in (("data", MOD, "recipe", "quern"),
                   ("data", MOD, "recipe", "barrel"),
                   ("data", "tfc", "recipe", "quern"),
                   ("data", MOD, "recipe", "heating", "crushed_ore"),
                   ("data", MOD, "recipe", "heating", "washed_ore"),
                   ("data", MOD, "recipe", "heating", "ore_concentrate"),
                   ("data", MOD, "recipe", "heating", "ore"),
                   ("assets", MOD, "models", "item", "crushed_ore"),
                   ("assets", MOD, "models", "item", "washed_ore"),
                   ("assets", MOD, "models", "item", "ore_concentrate"),
                   ("assets", MOD, "textures", "item", "crushed_ore"),
                   ("assets", MOD, "textures", "item", "washed_ore"),
                   ("assets", MOD, "textures", "item", "ore_concentrate")):
        shutil.rmtree(os.path.join(RES, *folder), ignore_errors=True)
    for folder in (os.path.join(RES, "data", MOD, "tfc", "item_heat"),
                   os.path.join(RES, "data", MOD, "recipe", "heating", "powder")):
        if not os.path.isdir(folder):
            continue
        for entry in os.scandir(folder):
            if entry.name.startswith(("crushed_ore_", "washed_ore_", "ore_concentrate_", "powder_", "ore_")):
                os.remove(entry.path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=ores.default_jar())
    args = parser.parse_args()

    varieties = ores.varieties_in_code()
    archive = zipfile.ZipFile(args.jar)
    yields = ores.ore_yields(archive)
    counts = ores.quern_counts(archive)
    names = ores.variety_names(archive)
    sweep()

    lang = {}
    overridden = 0

    for variety in varieties:
        if variety not in yields:
            raise SystemExit(f"{variety} is in IMItems.ORE_VARIETIES but TFC has no heating recipe for its ore")
        if variety not in counts:
            raise SystemExit(f"{variety} is dressed but TFC grinds no powder from it, so there is no scoop to copy")
        metal, melt = yields[variety]
        crushed = f"{MOD}:crushed_ore/{variety}"
        washed = f"{MOD}:washed_ore/{variety}"

        # TFC's own file, at TFC's own path, so there is exactly one recipe for the input and it is this one:
        # same ore going in, same number of scoops coming out, only what they are named for changes.
        for grade, (scoops, path) in sorted(counts[variety].items()):
            write("data", *os.path.relpath(path, "data").split(os.sep), value={
                "type": "tfc:quern",
                "ingredient": {"item": f"tfc:ore/{grade}_{variety}"},
                "result": {"count": scoops, "id": crushed},
            })
            overridden += 1

        write("data", MOD, "recipe", "quern", f"washed_{variety}.json", value={
            "type": "tfc:quern",
            "ingredient": {"item": washed},
            "result": {"count": WASH_SCOOPS, "id": f"tfc:powder/{variety}"},
        })
        write("data", MOD, "recipe", "barrel", f"wash_{variety}.json", value={
            "type": "tfc:barrel_instant",
            "input_fluid": {"amount": WASH_WATER, "fluid": "minecraft:water"},
            "input_item": {"count": WASH_SCOOPS, "item": crushed},
            "output_item": {"count": 1, "id": washed},
        })

        for folder, item, amount, pattern in (("crushed_ore", crushed, MILLIBUCKETS_PER_SCOOP, "Crushed {}"),
                                              ("washed_ore", washed, WASHED_MILLIBUCKETS, "Washed {}")):
            write("data", MOD, "recipe", "heating", folder, f"{variety}.json", value={
                "type": "tfc:heating",
                "ingredient": {"item": item},
                "result_fluid": {"amount": amount, "id": f"tfc:metal/{metal}"},
                "temperature": melt,
            })
            write("data", MOD, "tfc", "item_heat", f"{folder}_{variety}.json", value={
                "ingredient": {"item": item},
                "heat_capacity": HEAT_CAPACITY,
            })
            write("assets", MOD, "models", "item", folder, f"{variety}.json", value={
                "parent": "item/generated",
                "textures": {"layer0": f"{MOD}:item/{folder}/{variety}"},
            })
            lang[f"item.{MOD}.{folder}.{variety}"] = pattern.format(names.get(variety, variety.replace("_", " ").title()))

    merge_lang(lang, set(varieties))
    print(f"ore dressing written: {overridden} quern overrides, {len(varieties)} crushed, {len(varieties)} washed")


if __name__ == "__main__":
    main()
