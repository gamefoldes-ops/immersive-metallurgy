"""Minimal PNG read/write with the standard library only, enough for Minecraft's 8-bit item textures.

Supports the colour types Minecraft ships item art in (grey, grey+alpha, RGB, RGBA, palette) and all five
scanline filters, and writes 8-bit RGBA with no filtering, which is what the game reads back fine.
"""

import struct
import zlib

_FILTERS = ("none", "sub", "up", "average", "paeth")


def _paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    return b if pb <= pc else c


def _unfilter(scan, previous, bpp):
    kind = scan[0]
    data = bytearray(scan[1:])
    if kind == 0:
        return bytes(data)
    if kind > 4:
        raise ValueError(f"unknown PNG filter {kind}")
    for i, value in enumerate(data):
        left = data[i - bpp] if i >= bpp else 0
        up = previous[i]
        upper_left = previous[i - bpp] if i >= bpp else 0
        if kind == 1:
            raw = value + left
        elif kind == 2:
            raw = value + up
        elif kind == 3:
            raw = value + ((left + up) >> 1)
        else:
            raw = value + _paeth(left, up, upper_left)
        data[i] = raw & 0xFF
    return bytes(data)


def read_rgba(path_or_bytes):
    """Returns (width, height, rows) where rows is a list of width many (r, g, b, a) tuples."""
    data = path_or_bytes if isinstance(path_or_bytes, bytes) else open(path_or_bytes, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG")

    position = 8
    idat = bytearray()
    header = palette = transparency = None
    while position < len(data):
        (length,) = struct.unpack(">I", data[position:position + 4])
        kind = data[position + 4:position + 8]
        body = data[position + 8:position + 8 + length]
        if kind == b"IHDR":
            header = struct.unpack(">IIBBBBB", body)
        elif kind == b"PLTE":
            palette = body
        elif kind == b"tRNS":
            transparency = body
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
        position += 12 + length

    width, height, depth, colour, _compression, _filter, _interlace = header
    if depth != 8 or _interlace:
        raise ValueError(f"only 8-bit non-interlaced PNGs are supported (depth {depth}, interlace {_interlace})")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    previous = bytes(stride)
    scanlines = []
    for y in range(height):
        scan = raw[y * (stride + 1):(y + 1) * (stride + 1)]
        line = _unfilter(scan, previous, channels)
        scanlines.append(line)
        previous = line

    pixels = []
    for line in scanlines:
        row = []
        for x in range(width):
            sample = line[x * channels:(x + 1) * channels]
            if colour == 0:
                grey = sample[0]
                row.append((grey, grey, grey, 255))
            elif colour == 2:
                row.append((sample[0], sample[1], sample[2], 255))
            elif colour == 3:
                index = sample[0] * 3
                alpha = transparency[sample[0]] if transparency and sample[0] < len(transparency) else 255
                row.append((palette[index], palette[index + 1], palette[index + 2], alpha))
            elif colour == 4:
                row.append((sample[0], sample[1], sample[2], sample[3]))
            else:
                row.append((sample[0], sample[1], sample[2], sample[3]))
        pixels.append(row)
    return width, height, pixels


def write_rgba(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + bytes(channel for pixel in row for channel in pixel) for row in pixels)

    def chunk(kind, body):
        return (struct.pack(">I", len(body)) + kind + body
                + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF))

    with open(path, "wb") as handle:
        handle.write(b"\x89PNG\r\n\x1a\n")
        handle.write(chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)))
        handle.write(chunk(b"IDAT", zlib.compress(raw, 9)))
        handle.write(chunk(b"IEND", b""))
