"""Runs every generator in the order the data needs, in one command.

The files under `src/main/resources` that describe metals, ores and their numbers are written by the scripts
in this folder from two sources of truth: the lists in the Java code (`IMAlloys.ALL`, `IMItems.ORE_VARIETIES`)
and TFC's own data inside its jar. Nothing here decides a value — this only says which script has to run
before which, because part recipes are cloned out of the tags the metal data writes, and both of them read
the same language file one row at a time.

    python tools/regenerate_all.py
    python tools/regenerate_all.py --jar /path/to/TerraFirmaCraft-....jar

Run it after changing a generator's table, after updating TFC, and before committing anything that looks like
it should have been generated.
"""

import argparse
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))

# Order matters: metals and their fluids first, then the recipes that name their tags, then the art that hangs
# off both, then the ore chain, which reads the metal list back out of the Java. The third field says whether
# the script can be pointed at a jar: the metal table reads nothing out of TFC, so it takes no arguments.
GENERATORS = [
    ("metals: fluids, ingots, double ingots, slag dust, heat and tags", "generate_flawed_data.py", False),
    ("parts: anvil and casting recipes cloned for every flawed metal", "generate_flawed_part_recipes.py", True),
    ("flawed art: TFC's own ingot sprites, degraded", "generate_flawed_textures.py", True),
    ("ore fouling: how dirty each ore and each dust is", "generate_ore_fouling.py", True),
    ("ore dressing: quern, barrel, washing, grinding", "generate_ore_dressing.py", True),
    ("dressed ore art: pieces of the same rock, in the metal's colour", "generate_ore_textures.py", True),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--jar", help="TFC jar to read ore and metal data from; each generator has its own default")
    args = parser.parse_args()

    failed = []
    for label, script, takes_jar in GENERATORS:
        command = [sys.executable, os.path.join(HERE, script)]
        if args.jar and takes_jar:
            command += ["--jar", args.jar]
        print(f"== {label}", flush=True)
        if subprocess.run(command, cwd=HERE).returncode != 0:
            failed.append(script)

    if failed:
        print(f"\nFAILED: {', '.join(failed)}", file=sys.stderr)
        return 1
    print("\nall generators finished")
    return 0


if __name__ == "__main__":
    sys.exit(main())
