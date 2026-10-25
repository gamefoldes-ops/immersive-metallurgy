"""Redraws TFC's own metal textures into the flawed ones, from the knobs in tools/flawed_textures.json.

Deliberately derived rather than invented: a flawed ingot has to read as the same object cast badly, so the
shape, shading and lighting come from TFC's art and only the surface quality changes - colour pulled toward
grey, overall darkening, a few darker pits where the melt went cold, and a light per-pixel roughness so the
highlight is not perfect any more. The result stays a normal texture, so it also works in JEI, item frames
and on the ground, which a runtime tint cannot promise.

Run `python tools/generate_flawed_textures.py` after editing the JSON or updating TFC.
"""

import argparse
import json
import os
import random
import re
import zipfile

import png

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, os.pardir, "src", "main", "resources", "assets", "immersive_metallurgy", "textures", "item", "metal")

# The metals are IMAlloys' business, not this script's: reading them back keeps the art from lagging the list.
SLAG_SOURCE = "assets/tfc/textures/item/powder/graphite.png"

ALLOY_KEYS = re.findall(r'new IMAlloys\("(\w+)"',
                        open(os.path.join(HERE, os.pardir, "src", "main", "java", "immersivemetallurgy",
                                          "registry", "IMAlloys.java"), encoding="utf-8").read())


def default_jar():
    base = os.environ.get("APPDATA", "")
    return os.path.join(base, ".minecraft", "versions", "TFC+Millenaire", "mods",
                        "TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar")


def degrade(pixel, knobs, rng, pitted):
    red, green, blue, alpha = pixel
    if alpha == 0:
        return pixel

    luminance = 0.299 * red + 0.587 * green + 0.114 * blue
    desaturate, darken = knobs["desaturate"], knobs["darken"]
    red = red + (luminance - red) * desaturate
    green = green + (luminance - green) * desaturate
    blue = blue + (luminance - blue) * desaturate

    loss = darken
    if pitted:
        loss = darken + knobs["pit_darken"]
    elif rng.random() < knobs["roughness"]:
        loss = darken + (rng.random() * 2 - 1) * 0.18

    red = max(0, min(255, red * (1 - loss)))
    green = max(0, min(255, green * (1 - loss)))
    blue = max(0, min(255, blue * (1 - loss)))
    return (int(red), int(green), int(blue), alpha)


def convert(source, target, knobs, double):
    width, height, pixels = png.read_rgba(source)
    rng = random.Random(knobs["seed"] + (7 if double else 3))

    body = [(y, x) for y in range(height) for x in range(width) if pixels[y][x][3] > 0]
    if not body:
        raise ValueError("source texture is empty")
    pits = set(rng.sample(body, min(knobs["pits"], len(body))))

    drawn = [[degrade(pixels[y][x], knobs, rng, (y, x) in pits) for x in range(width)] for y in range(height)]

    os.makedirs(os.path.dirname(target), exist_ok=True)
    png.write_rgba(target, drawn)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=default_jar())
    args = parser.parse_args()

    config = json.load(open(os.path.join(HERE, "flawed_textures.json"), encoding="utf-8"))
    archive = zipfile.ZipFile(args.jar)
    written = 0

    for alloy in ALLOY_KEYS:
        knobs = dict(config["default"])
        knobs.update(config["alloys"].get(alloy, {}))

        for family, suffix in (("ingot", ""), ("double_ingot", "_double")):
            source = f"assets/tfc/textures/item/metal/{family}/{alloy}.png"
            if source not in archive.namelist():
                continue
            target = os.path.join(OUT, family, f"flawed_{alloy}.png")
            convert(archive.read(source), target, knobs, suffix == "_double")
            written += 1

    # The one non-metal item of ours borrows TFC's powder art the same way, so that slag dust looks like it
    # belongs in the same game as the metal it came off.
    dust = f"{SLAG_SOURCE}"
    if dust in archive.namelist():
        convert(archive.read(dust), os.path.join(OUT, os.pardir, "slag_dust.png"), dict(config["default"]), False)
        written += 1

    print(f"flawed textures written: {written}")


if __name__ == "__main__":
    main()
