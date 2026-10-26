"""Draws the two dressed states of an ore lump from TFC's own art for that ore.

Both pictures are the same trick on the same silhouette: the lump is cut by two lines through its middle and the
four resulting pieces are pushed a pixel apart, so what you hold is the ore broken up rather than the ore whole -
which is what a quern actually does to it. Crushed ore keeps the rock's own colours, only dulled, because crushing
separates nothing. Washed ore keeps the very same pieces and loses the rock entirely: every pixel is repainted from
the metal's colour, shaded by how bright that pixel was in the ore, so the highlight and the shadow of the original
art survive as a lump of pure mineral.

Run `python tools/generate_ore_textures.py` after changing the knobs below, the ore list in IMItems, or TFC's art.
"""

import argparse
import math
import os
import random
import re
import zipfile
import zlib

import png

import tfc_ore_data as ores

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, os.pardir, "src", "main", "resources", "assets", "immersive_metallurgy", "textures", "item")

ALLOY_TINTS = dict(re.findall(r'new IMAlloys\("(\w+)", ".+?", 0x([0-9A-Fa-f]{6,8})',
                    open(os.path.join(HERE, os.pardir, "src", "main", "java", "immersivemetallurgy",
                                      "registry", "IMAlloys.java"), encoding="utf-8").read()))

# Two cuts, so a lump comes apart into four pieces: enough to read as broken, few enough that the silhouette of
# the ore is still recognisable at 16 pixels. SEPARATION is how far the pieces drift apart - under a pixel, so
# they still belong to one lump rather than turning into confetti.
CUTS = 2
SEPARATION = 0.75
SEAM = 0.5
SPREAD_SEED = 20261026

# Crushed ore is the rock, so the rock's colour stays and only loses a little of its life.
CRUSHED_DESATURATE = 0.18
CRUSHED_DARKEN = 0.12
# Washed ore is the metal: the tint is multiplied by a shade taken from the ore's own lighting, from three
# fifths of it in the shadow to a fifth over it on the highlight.
METAL_SHADOW = 0.6
METAL_HIGHLIGHT = 1.2
# The tints are the average colour of TFC's ingot art, and for iron that is nearly black - fine on a polished
# bar, useless as a 16 pixel lump. Every metal is therefore raised to the same brightness before it is shaded,
# so washed iron reads as metal rather than as a hole in the ground.
METAL_LIGHT = 150
METAL_RAISE_LIMIT = 2.2


def tint_from_hex(digits):
    value = int(digits, 16)
    if len(digits) == 8:
        value &= 0xFFFFFF
    return (value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF


def metal_colour(digits):
    """The metal's average colour, raised until an item cell can show it, with its hue left alone."""
    tint = tint_from_hex(digits)
    raise_to = min(METAL_RAISE_LIMIT, METAL_LIGHT / max(1.0, luminance(tint)))
    return tuple(min(255, int(channel * raise_to)) for channel in tint)


def luminance(pixel):
    return 0.299 * pixel[0] + 0.587 * pixel[1] + 0.114 * pixel[2]


def cut_lines(width, height, body, rng):
    """Two lines through the middle of the lump, each tilted and nudged a little, so no two ores break alike."""
    centre_x = sum(x for x, _ in body) / len(body)
    centre_y = sum(y for _, y in body) / len(body)
    lines = []
    for _ in range(CUTS):
        angle = rng.uniform(0, math.pi)
        normal = (math.cos(angle), math.sin(angle))
        offset = normal[0] * centre_x + normal[1] * centre_y + rng.uniform(-1.5, 1.5)
        lines.append((normal, offset))
    return lines


def shatter(pixels, width, height, lines):
    """Every opaque pixel moved to its own piece, with a seam left where the cuts were."""
    canvas = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]

    for y in range(height):
        for x in range(width):
            pixel = pixels[y][x]
            if pixel[3] == 0:
                continue
            shift_x = shift_y = 0.0
            for normal, offset in lines:
                distance = normal[0] * x + normal[1] * y - offset
                if abs(distance) < SEAM:
                    shift_x = shift_y = None
                    break
                side = 1.0 if distance > 0 else -1.0
                shift_x += side * normal[0] * SEPARATION
                shift_y += side * normal[1] * SEPARATION
            if shift_x is None:
                continue

            target_x, target_y = int(round(x + shift_x)), int(round(y + shift_y))
            if 0 <= target_x < width and 0 <= target_y < height and canvas[target_y][target_x][3] == 0:
                canvas[target_y][target_x] = pixel
    return recenter(canvas)


def recenter(canvas):
    """Slide the broken lump back into the middle of its cell, so a piece drifting off the edge costs pixels."""
    height, width = len(canvas), len(canvas[0])
    filled = [(x, y) for y in range(height) for x in range(width) if canvas[y][x][3] > 0]
    if not filled:
        return canvas
    left, right = min(x for x, _ in filled), max(x for x, _ in filled)
    top, bottom = min(y for _, y in filled), max(y for _, y in filled)
    shift_x = round(((width - 1) - (left + right)) / 2)
    shift_y = round(((height - 1) - (top + bottom)) / 2)
    if not (shift_x or shift_y):
        return canvas

    moved = [[(0, 0, 0, 0) for _ in range(width)] for _ in range(height)]
    for x, y in filled:
        target_x, target_y = x + shift_x, y + shift_y
        if 0 <= target_x < width and 0 <= target_y < height:
            moved[target_y][target_x] = canvas[y][x]
    return moved


def recolour(pieces, light_range, paint):
    """Ask `paint` what each surviving pixel should become, given how bright it was in the ore.

    The piece carries the ore's own pixel with it, so the shading of the original art travels into the new
    picture without a second look at the source; `light_range` is the lump's own brightest and darkest pixel,
    which scales the highlight so a dark ore still gets a full one and a bright one does not blow out.
    """
    lowest, span = light_range
    height, width = len(pieces), len(pieces[0])
    return [[paint(pixel, (luminance(pixel) - lowest) / span) if pixel[3] else pixel
             for pixel in row] for row in pieces]


def crushed_paint(pixel, light):
    red, green, blue, alpha = pixel
    grey = luminance((red, green, blue))
    red = red + (grey - red) * CRUSHED_DESATURATE
    green = green + (grey - green) * CRUSHED_DESATURATE
    blue = blue + (grey - blue) * CRUSHED_DESATURATE
    loss = 1 - CRUSHED_DARKEN
    return tuple(min(255, max(0, int(channel * loss))) for channel in (red, green, blue)) + (alpha,)


def washed_paint(pixel, light, colour):
    """The metal's own colour, modulated by the ore's lighting: the highlight of the original art survives."""
    shade = METAL_SHADOW + (METAL_HIGHLIGHT - METAL_SHADOW) * light
    return tuple(min(255, max(0, int(channel * shade))) for channel in colour) + (pixel[3],)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=ores.default_jar())
    args = parser.parse_args()

    archive = zipfile.ZipFile(args.jar)
    yields = ores.ore_yields(archive)
    written = 0

    for variety in ores.varieties_in_code():
        source = f"assets/tfc/textures/item/ore/normal_{variety}.png"
        if source not in archive.namelist():
            print(f"skipped {variety}: TFC has no item art under that name")
            continue

        width, height, pixels = png.read_rgba(archive.read(source))
        body = [(x, y) for y in range(height) for x in range(width) if pixels[y][x][3] > 0]
        if not body:
            raise SystemExit(f"{source} is empty, so there is nothing to break apart")

        rng = random.Random(zlib.crc32(variety.encode()) + SPREAD_SEED)
        pieces = shatter(pixels, width, height, cut_lines(width, height, body, rng))

        lit = [luminance(pixels[y][x]) for x, y in body]
        lowest, highest = min(lit), max(lit)
        light_range = (lowest, max(1.0, highest - lowest))
        colour = metal_colour(ALLOY_TINTS[yields[variety][0]])
        for folder, paint in (("crushed_ore", crushed_paint),
                              ("washed_ore", lambda pixel, light, colour=colour: washed_paint(pixel, light, colour))):
            target = os.path.join(OUT, folder, f"{variety}.png")
            os.makedirs(os.path.dirname(target), exist_ok=True)
            png.write_rgba(target, recolour(pieces, light_range, paint))
            written += 1

    print(f"ore textures written: {written}")


if __name__ == "__main__":
    main()
