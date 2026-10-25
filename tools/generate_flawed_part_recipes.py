"""Clones TFC's own part recipes so a flawed ingot or a flawed pour can become parts and tools.

Two rules drive the whole thing:
  * anvil recipes that eat `c:ingots/<alloy>` or `c:double_ingots/<alloy>` get a twin that eats the flawed
    tag, producing the very same TFC part item - the defect lives on the stack, not in a new item id;
  * casting recipes that pour `tfc:metal/<alloy>` get a twin that pours our flawed fluid into the same mold.

Whatever fluid tags list the clean alloy (or its parent tag) get the flawed fluid added too, which is how a
mold comes to accept it without us guessing mold names: TFC's ingot mold lists `#tfc:molten_metals`, and
the tool head molds list each castable metal by hand.

TFC's jar is read from the launcher instance; override with `--jar <path>`.
Run `python tools/generate_flawed_part_recipes.py` after TFC updates or ALLOYS changes.
"""

import argparse
import json
import os
import shutil
import re
import zipfile

MOD = "immersive_metallurgy"

# TFC assembles every tool onto this tag of wooden rods, which is the sixteen wood twigs and nothing else.
WOODEN_ROD_TAGS = {"c:rods/wooden"}
BONE = "minecraft:bone"
RES = os.path.join(os.path.dirname(__file__), os.pardir, "src", "main", "resources")

ALLOY_KEYS = re.findall(r'new IMAlloys\("(\w+)"',
                        open(os.path.join(os.path.dirname(__file__), os.pardir, "src", "main", "java",
                                          "immersivemetallurgy", "registry", "IMAlloys.java"),
                             encoding="utf-8").read())


def default_jar():
    base = os.environ.get("APPDATA", "")
    return os.path.join(base, ".minecraft", "versions", "TFC+Millenaire", "mods",
                        "TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar")


def read_json(archive, name):
    try:
        return json.loads(archive.read(name).decode("utf-8"))
    except KeyError:
        return None


def write(*path, value):
    full = os.path.join(RES, *path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(value, handle, separators=(",", ":"))
        handle.write("\n")


def fluid_tags_of(archive, alloy):
    """Every fluid tag that already mentions this clean alloy, so a mold that takes bronze takes flawed bronze."""
    tags = {}
    clean = f"tfc:metal/{alloy}"
    parent = "#tfc:molten_metals"
    for name in archive.namelist():
        if not name.startswith("data/tfc/tags/fluid/") or not name.endswith(".json"):
            continue
        data = read_json(archive, name)
        if data is None:
            continue
        values = json.dumps(data)
        if clean in values or parent in values:
            tag = os.path.splitext(os.path.basename(name))[0]
            tags.setdefault(tag, []).append(clean in values)
    return set(tags)


def assemble_recipes(archive):
    """Teach TFC's own tool-assembly recipes to pass the melt on, and give flawed parts their own assembly.

    The event route cannot work: the crafting event is handed a copy of the result, so components written
    there never reach the player. TFC already solves this for the forging bonus with an output modifier, so
    we add ours to exactly the recipes that consume a metal part of one of our metals, and write the file
    back at the same path, which replaces TFC's recipe by id.

    Then, for each of those recipes, a second recipe under our own id whose metal ingredients are the
    flawed parts. A flawed head has to be able to become a tool at all, and the handle in TFC is the tag
    of wooden rods, which is sixteen wood twigs; bone is not in it, so the flawed versions add it.
    """
    # any metal item of one of our metals: "tfc:metal/pickaxe_head/bronze" ends with the metal name
    count = 0
    for name in archive.namelist():
        if not name.startswith("data/tfc/recipe/crafting/") or not name.endswith(".json"):
            continue
        recipe = read_json(archive, name)
        if recipe is None:
            continue
        referenced = set()
        for entry in list(recipe.get("key", {}).values()) + list(recipe.get("ingredients", [])):
            for item in items_of(entry):
                referenced.add(item)
        metals = [item for item in referenced
                  if item.startswith("tfc:metal/") and item.rsplit("/", 1)[-1] in ALLOY_KEYS]
        if not metals:
            continue
        result = recipe.get("result")
        if not isinstance(result, dict):
            continue
        modifiers = list(result.get("modifiers", []))
        if not any(modifier.get("type") == "tfc:copy_forging_bonus" for modifier in modifiers):
            continue
        modifiers.append({"type": f"{MOD}:copy_melt"})
        twin = json.loads(json.dumps(recipe))
        widen_handles(twin)
        twin["result"] = dict(result)
        twin["result"]["modifiers"] = modifiers
        write("data", *os.path.relpath(name, "data").split(os.sep), value=twin)
        count += 1

    return count


def items_of(entry):
    """The item ids one ingredient mentions, whether it is one object, a tag, or a list of alternatives."""
    if isinstance(entry, list):
        for inner in entry:
            yield from items_of(inner)
    elif isinstance(entry, dict):
        if isinstance(entry.get("item"), str):
            yield entry["item"]
        elif isinstance(entry.get("tag"), str):
            yield "#" + entry["tag"]


def widen_handles(recipe):
    """A bone shaft is a plausible handle and TFC's wooden rod tag does not contain one."""
    for key in ("key", "ingredients"):
        entries = recipe.get(key)
        if isinstance(entries, dict):
            for name, entry in list(entries.items()):
                entries[name] = add_bone(entry)
        elif isinstance(entries, list):
            recipe[key] = [add_bone(entry) for entry in entries]


def add_bone(entry):
    if isinstance(entry, dict) and entry.get("tag") in WOODEN_ROD_TAGS:
        return [entry, {"item": BONE}]
    return entry


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", default=default_jar())
    args = parser.parse_args()

    archive = zipfile.ZipFile(args.jar)
    anvil_files = [n for n in archive.namelist() if n.startswith("data/tfc/recipe/anvil/") and n.endswith(".json")]
    casting_files = [n for n in archive.namelist()
                     if n.startswith("data/tfc/recipe/casting/") and n.endswith(".json")]

    out_root = os.path.join(RES, "data", MOD, "recipe")
    # These two folders belong to this script alone. Ingots are generated by generate_flawed_data.py into
    # recipe/casting, and cloning TFC's own ingot recipes here would give one mold two different results.
    for sub in ("anvil", "casting_part"):
        folder = os.path.join(out_root, sub)
        if os.path.isdir(folder):
            shutil.rmtree(folder)

    anvil_count = 0
    casting_count = 0
    mold_tags = set()

    for alloy in ALLOY_KEYS:
        flawed_fluid = f"{MOD}:metal/flawed_{alloy}"
        for tag in fluid_tags_of(archive, alloy):
            mold_tags.add((tag, alloy))

        for name in anvil_files:
            if "ingot" in name:
                continue
            recipe = read_json(archive, name)
            if recipe is None or recipe.get("type") != "tfc:anvil":
                continue
            ingredient = recipe.get("ingredient", {})
            original_tag = ingredient.get("tag", "")
            for family in ("ingots", "double_ingots"):
                if original_tag != f"c:{family}/{alloy}":
                    continue
                twin = dict(recipe)
                twin["ingredient"] = {"tag": f"c:{family}/flawed_{alloy}"}
                rel = os.path.relpath(name, "data/tfc/recipe/anvil")
                folder, file = os.path.split(rel)
                write("data", MOD, "recipe", "anvil", folder, f"flawed_{file}", value=twin)
                anvil_count += 1

        for name in casting_files:
            if "ingot" in name:
                continue
            recipe = read_json(archive, name)
            if recipe is None or recipe.get("type") != "tfc:casting":
                continue
            fluid = recipe.get("fluid", {})
            if fluid.get("fluid") != f"tfc:metal/{alloy}":
                continue
            twin = dict(recipe)
            twin["fluid"] = dict(fluid)
            twin["fluid"]["fluid"] = flawed_fluid
            folder, file = os.path.split(os.path.relpath(name, "data/tfc/recipe/casting"))
            write("data", MOD, "recipe", "casting_part", folder, f"flawed_{file}", value=twin)
            casting_count += 1

    by_tag = {}
    for tag, alloy in sorted(mold_tags):
        by_tag.setdefault(tag, []).append(f"{MOD}:metal/flawed_{alloy}")
    for tag, fluids in by_tag.items():
        # TFC's own definition of the tag has to survive: a mold tag written from scratch would take our
        # flawed metals and quietly drop every clean one TFC listed there.
        name = f"data/tfc/tags/fluid/{tag}.json"
        original = read_json(archive, name) or {"values": []}
        values = list(original.get("values", []))
        values.extend(fluid for fluid in fluids if fluid not in values)
        write("data", "tfc", "tags", "fluid", f"{tag}.json", value={"values": values})

    assembled = assemble_recipes(archive)

    print(f"anvil twins: {anvil_count}, casting twins: {casting_count}, mold tags extended: {len(by_tag)}")
    print("tags:", ", ".join(sorted(by_tag)))
    print(f"assembly recipes rewritten to pass the melt on and take a bone shaft: {assembled}")


if __name__ == "__main__":
    main()
