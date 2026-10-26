"""Writes the ore fouling table (data/immersive_metallurgy/recipe/ore_fouling/ores.json) from TFC's own ore recipes.

Raw ore is dirt: TFC knows only how much metal an ore yields and treats every yield as pure, so this takes the
amounts straight out of TFC's 48 heating recipes and pairs each one with the gangue that must come with it.
The variant is what decides the fouling, because that is the part of the ore a player cannot see or choose —
rich, normal, poor and small are TFC's own four grades.

Run `python tools/generate_ore_fouling.py` after changing the ratios or updating TFC.
"""

import argparse
import json
import os
import zipfile

MOD = "immersive_metallurgy"
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, os.pardir, "src", "main", "resources")

# Slag added per unit of metal the ore yields, by TFC's grade. The worst grade tops out just under 20% of the
# melt (share = ratio / (1 + ratio)), which stays comfortably under the 0.25 max_slag of the tolerance rules so
# a crucible filled with ore still pours, while sitting above slag_tolerance so smelting from rock is flawed.
RATIOS = {
    "rich": 0.07,
    "normal": 0.12,
    "poor": 0.16,
    "small": 0.19,
}

# How much a batch of that grade can differ, as a fraction of its own ratio, so the dirtiest ore still tops
# out under 20% slag in the melt.
SPREAD = 0.3

# The share of slag a ceramic melting pot is held to. A pot has no skim and no flux and no lid, so a pour from
# one is never purer than 1 - this: 0.1 is a 90% ceiling, above every rule's slag_tolerance so a pot can never
# pour clean metal, and low enough that the worst a clean-proportioned pot pour can be is the light grade -
# the slag band that stays under grades.medium (0.4) is about 15%, because edge_wear weights slag at 0.7.
VESSEL_SLAG_FLOOR = 0.1


def default_jar():
    base = os.environ.get("APPDATA", "")
    return os.path.join(base, ".minecraft", "versions", "TFC+Millenaire", "mods",
                        "TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=default_jar())
    args = parser.parse_args()

    archive = zipfile.ZipFile(args.jar)
    entries = []
    unknown = []
    for name in sorted(archive.namelist()):
        if not name.startswith("data/tfc/recipe/heating/ore/") or not name.endswith(".json"):
            continue
        recipe = json.loads(archive.read(name))
        item = recipe.get("ingredient", {}).get("item")
        if not item or recipe.get("result_fluid", {}).get("amount", 0) <= 0:
            continue
        variant = os.path.basename(name)[:-5].split("_", 1)[0]
        if variant not in RATIOS:
            unknown.append(name)
            continue
        entries.append({"ingredient": {"item": item}, "slag_ratio": RATIOS[variant],
                        "slag_spread": round(RATIOS[variant] * SPREAD, 3)})

    full = os.path.join(RES, "data", MOD, "recipe", "ore_fouling", "ores.json")
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as handle:
        json.dump({"type": f"{MOD}:ore_fouling", "vessel_slag_floor": VESSEL_SLAG_FLOOR, "entries": entries},
                  handle, separators=(",", ":"))
        handle.write("\n")

    print(f"ore fouling written for {len(entries)} ores" + (f", unmatched variants: {unknown}" if unknown else ""))


if __name__ == "__main__":
    main()
