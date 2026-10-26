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

from tfc_ore_data import REPRESENTATIVE, metals_in_code

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, os.pardir, "src", "main", "resources", "assets", "immersive_metallurgy")
OUT = os.path.join(ASSETS, "textures", "item", "metal")
# An ingot pile is drawn from the metal's smooth block surface, not from the item art, so the pile needs its
# own degraded copy of that sheet. It lands under textures/block/ so the block atlas picks it up by directory.
OUT_SMOOTH = os.path.join(ASSETS, "textures", "block", "metal", "smooth")

# The metals are IMAlloys' business, not this script's: reading them back keeps the art from lagging the list.
SLAG_SOURCE = "assets/tfc/textures/item/powder/graphite.png"

ALLOY_TINTS = re.findall(r'new IMAlloys\("(\w+)", ".+?", 0x([0-9A-Fa-f]{6,8})',
                         open(os.path.join(HERE, os.pardir, "src", "main", "java", "immersivemetallurgy",
                                           "registry", "IMAlloys.java"), encoding="utf-8").read())
ALLOY_KEYS = [key for key, _ in ALLOY_TINTS]


def default_jar():
    base = os.environ.get("APPDATA", "")
    return os.path.join(base, ".minecraft", "versions", "TFC+Millenaire", "mods",
                        "TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar")


# Our slag is a warm dark grey-brown, while the powder art borrowed from TFC is plain graphite grey.
# Multiplying grey by the slag colour gives a dust that reads as the stuff it came off, and nothing else.
SLAG_TINT = (0x5C, 0x54, 0x49)

# How much of a dust's colour is the metal it was skimmed off. Real slag takes its colour from the metal's
# oxides, but it is still mostly slag, so the bath only leans the grey rather than replacing it.
DUST_METAL_SHARE = 0.35


def tint_from_hex(digits):
    value = int(digits, 16)
    if len(digits) == 8:
        value &= 0xFFFFFF
    return (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF


def mix(left, right, share):
    return tuple(int(l * (1 - share) + r * share) for l, r in zip(left, right))


def brighten(pixel, lift):
    return tuple(min(255, int(channel * lift)) for channel in pixel)


def tint_grey(pixel, colour=SLAG_TINT):
    red, green, blue, alpha = pixel
    if alpha == 0:
        return pixel
    grey = 0.299 * red + 0.587 * green + 0.114 * blue
    peak = max(colour)
    return tuple(min(255, int(grey * channel / peak * 1.55)) for channel in colour) + (alpha,)


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

        sheet = f"assets/tfc/textures/block/metal/smooth/{alloy}.png"
        if sheet in archive.namelist():
            convert(archive.read(sheet), os.path.join(OUT_SMOOTH, f"flawed_{alloy}.png"), knobs, False)
            written += 1

    # The non-metal items of ours borrow TFC's powder art the same way, so that slag dust looks like it
    # belongs in the same game as the metal it came off - and like the particular metal it came off.
    dust = SLAG_SOURCE
    if dust in archive.namelist():
        _, _, rows = png.read_rgba(archive.read(dust))
        item_dir = os.path.join(OUT, os.pardir)
        png.write_rgba(os.path.join(item_dir, "slag_dust.png"),
                       [[tint_grey(pixel) for pixel in row] for row in rows])
        written += 1
        per_metal = os.path.join(item_dir, "slag_dust")
        os.makedirs(per_metal, exist_ok=True)
        for key, digits in ALLOY_TINTS:
            colour = mix(SLAG_TINT, tint_from_hex(digits), DUST_METAL_SHARE)
            png.write_rgba(os.path.join(per_metal, f"{key}.png"),
                           [[tint_grey(pixel, colour) for pixel in row] for row in rows])
            written += 1

    # A concentrate keeps the shape of the powder it was washed out of, because by then the rock is gone and
    # what is left is the same pile of ground mineral with the silica out of it: only the colour moves toward
    # the metal, and the pile catches a little more light.
    for metal in metals_in_code():
        variety = REPRESENTATIVE[metal]
        source = f"assets/tfc/textures/item/powder/{variety}.png"
        if source not in archive.namelist():
            continue
        _, _, pixels = png.read_rgba(archive.read(source))
        colour = tint_from_hex(dict(ALLOY_TINTS)[metal])
        share = config["dressing"]["concentrate"]["share"]
        lift = config["dressing"]["concentrate"]["lift"]
        drawn = [[brighten(mix(pixel[:3], colour, share), lift) + (pixel[3],) if pixel[3] else pixel
                  for pixel in row] for row in pixels]
        target = os.path.join(ASSETS, "textures", "item", "ore_concentrate", f"{metal}.png")
        os.makedirs(os.path.dirname(target), exist_ok=True)
        png.write_rgba(target, drawn)
        written += 1

    print(f"flawed textures written: {written}")


if __name__ == "__main__":
    main()
