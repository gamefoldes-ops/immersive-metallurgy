"""Writes the ore dressing chain: a use for TFC's own ore powder, and the wash that turns it into clean metal.

TFC already grinds every ore on its quern into {@code tfc:powder/<ore>} - seven powders from a rich lump, five
from a normal, three from a poor, two from the small fragments - and each powder is worth five millibuckets,
because those counts divide the lump yields by exactly five. Shipping a rival quern recipe only lost, which is
what it did the first time. What TFC never does is give the powder a way back into metal, so this writes two:
smelted as it is the powder gives up its five millibuckets and brings all of the gangue with it, which is the
price of crushing - powder cannot tell rich from poor any more; washed, it is clean.

The wash is one powder in, one concentrate out, and it costs the same five millibuckets either way, so the
barrel spends nothing but water. Losing metal to a jig is what real dressing does, but here that would only
make the crucible and a bowl the better answer, and then nobody would build the barrel.

Run `python tools/generate_ore_dressing.py` after changing the numbers below or updating TFC, then
`python tools/generate_flawed_textures.py` for the art.
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

MILLIBUCKETS_PER_POWDER = 5

# The wash is a fifth of a lump at a time, so five powders come out as one concentrate carrying all
# twenty-five millibuckets between them. Nothing is spent: this only bundles what the quern divided up. The
# number is here rather than four or one because a concentrate has to be big enough that the crucible melts a
# batch of washed ore in as few slots as it would have spent on the raw lumps.
WASH_POWDERS = 5
CONCENTRATE_MILLIBUCKETS = WASH_POWDERS * MILLIBUCKETS_PER_POWDER
WASH_WATER = 100
POWDER_HEAT_CAPACITY = 0.7


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
        lang = {name: value for name, value in json.load(open(path, encoding="utf-8")).items()
                if not name.startswith(f"item.{MOD}.crushed_ore.")}
    lang.update(keys)
    write("assets", MOD, "lang", "en_us.json", value=dict(sorted(lang.items())))


def sweep():
    """Everything this tool owns, from the earlier shape of the chain, so a redesign leaves no dead data."""
    for folder in (("data", MOD, "recipe", "quern"),
                   ("data", MOD, "recipe", "heating", "ore"),
                   ("data", MOD, "recipe", "heating", "crushed_ore"),
                   ("data", MOD, "recipe", "heating", "ore_concentrate"),
                   ("assets", MOD, "models", "item", "crushed_ore"),
                   ("assets", MOD, "textures", "item", "crushed_ore"),
                   ("assets", MOD, "models", "item", "ore_concentrate")):
        shutil.rmtree(os.path.join(RES, *folder), ignore_errors=True)
    heat = os.path.join(RES, "data", MOD, "tfc", "item_heat")
    barrel = os.path.join(RES, "data", MOD, "recipe", "barrel")
    for folder, prefix in ((heat, "crushed_ore_"), (heat, "ore_concentrate_"), (barrel, "wash_")):
        if not os.path.isdir(folder):
            continue
        for path in os.scandir(folder):
            if path.name.startswith(prefix):
                os.remove(path.path)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=ores.default_jar())
    args = parser.parse_args()

    metals = ores.metals_in_code()
    archive = zipfile.ZipFile(args.jar)
    yields = ores.ore_yields(archive)
    powders = ores.metal_powders(archive)
    sweep()

    lang = {}
    washed = 0
    melted_metals = {metal for metal, _ in powders.values()}

    for powder, (metal, variety) in sorted(powders.items()):
        melt = yields[variety][1]
        # TFC grinds this ore and then has nothing for the powder to do, so the powder gets the use it lacks -
        # dirty, because crushing keeps every bit of the gangue and loses the grade.
        write("data", MOD, "recipe", "heating", "powder", f"ore_{variety}.json", value={
            "type": "tfc:heating",
            "ingredient": {"item": powder},
            "result_fluid": {"amount": MILLIBUCKETS_PER_POWDER, "id": f"tfc:metal/{metal}"},
            "temperature": melt,
        })
        write("data", MOD, "tfc", "item_heat", f"powder_{variety}.json", value={
            "ingredient": {"item": powder},
            "heat_capacity": POWDER_HEAT_CAPACITY,
        })

        if variety not in ores.DRESSABLE_VARIETIES:
            continue
        washed += 1
        write("data", MOD, "recipe", "barrel", f"wash_{variety}.json", value={
            "type": "tfc:barrel_instant",
            "input_fluid": {"amount": WASH_WATER, "fluid": "minecraft:water"},
            "input_item": {"count": WASH_POWDERS, "item": powder},
            "output_item": {"count": 1, "id": f"{MOD}:ore_concentrate/{metal}"},
        })

    for metal in metals:
        variety = ores.REPRESENTATIVE[metal]
        if metal not in melted_metals:
            raise SystemExit(f"{metal} is in IMItems.DRESSABLE_METALS but no ore powder melts to it")
        concentrate = f"{MOD}:ore_concentrate/{metal}"

        write("data", MOD, "recipe", "heating", "ore_concentrate", f"{metal}.json", value={
            "type": "tfc:heating",
            "ingredient": {"item": concentrate},
            "result_fluid": {"amount": CONCENTRATE_MILLIBUCKETS, "id": f"tfc:metal/{metal}"},
            "temperature": yields[variety][1],
        })
        write("data", MOD, "tfc", "item_heat", f"ore_concentrate_{metal}.json", value={
            "ingredient": {"item": concentrate},
            "heat_capacity": POWDER_HEAT_CAPACITY,
        })
        write("assets", MOD, "models", "item", "ore_concentrate", f"{metal}.json", value={
            "parent": "item/generated",
            "textures": {"layer0": f"{MOD}:item/ore_concentrate/{metal}"},
        })
        lang[f"item.{MOD}.ore_concentrate.{metal}"] = f"{ores.DISPLAY[metal]} Concentrate"

    merge_lang(lang)
    print(f"ore dressing written: {len(powders)} powders meltable, {washed} washable, {len(metals)} concentrates")


if __name__ == "__main__":
    main()
