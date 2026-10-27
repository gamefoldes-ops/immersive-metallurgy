# Immersive Metallurgy

A NeoForge 1.21.1 addon for [TerraFirmaCraft](https://terrafirmacraft.com/) 4.2 that puts the dirt back into
metalworking: ore carries gangue, a melt that is off-proportion still pours instead of burning away, and what
comes out is flawed metal with a defect profile of its own — brittle, soft, cold-shut or flowy — which then costs
the tool made from it edge, damage and structure.

## Where the numbers are

Everything that decides how the systems behave ships as data, and no value meant to be retuned lives in the code.
The three tables below are recipe types only because a recipe is the one datapack structure Minecraft syncs to
the client and reloads with `/reload`, and the crucible's result is worked out on the client as well. One file
per system, a list inside it.

All under `src/main/resources/data/immersive_metallurgy/recipe/`:

| File | Type | What it decides |
| --- | --- | --- |
| `alloy_tolerance/rules.json` | `immersive_metallurgy:alloy_tolerance` | One row per alloy. `tolerance` is how far a single component may miss TFC's band and still pour this metal — bronze 0.06, the steels 0.0, so a steel melt is helped on its slag and not on its proportions. `slag_tolerance` is the share still called clean: 2%, and 0.5% for the steels, because ground inclusions are exactly what ruins an edge. `max_slag` is the ceiling past which the melt is not this metal at all (0.25). `flawed_result` names the fluid a melt pours as when it only fits inside the widened bands. |
| `flaw_profile/profiles.json` | `immersive_metallurgy:flaw_profile` | `defects` — the four archetypes and what a full dose of each costs a tool: attack damage, mining speed, how fast the edge dulls, the chance per hit to chip, and how much of the fault slag and a multi-element miss each feed. `roles` — per metal what running over and running short do, which is the reason too much tin and too little tin are not the same flaw. `grades` — the severity bands that make Minor, Major and Severe (0.25 and 0.5); that grade is also what splits piles of ingots, so the ladder is visible in a chest without opening anything. `durability_floor` 0.25, `unmeasured_flaw` 0.2 (the flaw assumed for a flawed thing that carries no history), `weld_merge_divisor` 2.0 (so two equal halves cost exactly what either one did), `wear` (edge lost per block mined and per hit landed, the floors a blunt tool keeps, honing's restore, ceiling, structure cost and seconds held), and `palette` (the measured ingot colours the flawed tint is mixed from, with `lean`, `defect_darken`, `hue_span`). |
| `ore_fouling/ores.json` | `immersive_metallurgy:ore_fouling` | Per raw thing: `slag_ratio`, the dirt it brings per unit of metal it yields, and `slag_spread`, how much that varies from batch to batch. 99 rows: TFC's 48 ore grades (rich 0.07 through small 0.19), 12 crushed scoops (0.12 — crushing frees the mineral from the rock and takes nothing out of it), 12 washed pieces (0.03, or 0.05 when the mineral is a sulfide, because water moves grit and not sulfur), and 27 slag dusts (0.6, so recovery is a consolation rather than a source of metal). `vessel_slag_floor` 0.10 is what a ceramic pot always holds back, however clean what went into it was. |

Two lists are deliberately code rather than data, because they are identities and not tunables: the metals
(`src/main/java/immersivemetallurgy/registry/IMAlloys.java`, whose keys, tints and double-ingot flags everything
else is generated from) and the ore varieties (`.../registry/IMItems.java`). The generators read both back out of
those files, so the data cannot drift from the metals the mod registers.

## Numbers that live in the generators

A few values belong to the art or to the shape of a chain rather than to a rule the game reads at runtime:

- `tools/generate_ore_dressing.py` — 5 mB to a scoop, 4 scoops and 100 mB of water to a washed piece of 20 mB
  (four rather than five so a stack of scoops divides without a remainder left sitting in the barrel), 5 powder
  scoops ground out of a washed piece, and the dressed pieces' heat capacity.
- `tools/generate_ore_fouling.py` — the four grade ratios and their spread, crushed 0.12, washed 0.03 and 0.05,
  slag dust 0.6, the pot's floor. The washed spread is kept tighter on purpose: a washed melt must not be able to
  roll under a rule's `slag_tolerance` and pour clean, because the residue is the reason to build a furnace, not a
  lottery.
- `tools/generate_flawed_data.py` — 100 mB to an ingot and 200 to a double, the 4 mB of metal one slag dust gives
  back, the break chance assumed for a mold whose recipe does not state one, and the heat capacities.
- `tools/flawed_textures.json` — how TFC's own ingot sprites are degraded into the flawed art: desaturation,
  darkening, pitting and roughness, which has to agree with `palette` above.

## Building

    ./gradlew build      # compile and jar
    ./gradlew deploy     # build and copy the jar into the dev instance's mods folder

The generated content is not hand-written, and the generators have an order: part recipes are cloned out of the
tags the metal data writes, and several of them add rows to the one language file. Run them in one go:

    python tools/regenerate_all.py
    python tools/regenerate_all.py --jar /path/to/TerraFirmaCraft-NeoForge-1.21.1-4.2.12.jar

That runs `generate_flawed_data.py`, `generate_flawed_part_recipes.py`, `generate_flawed_textures.py`,
`generate_ore_fouling.py`, `generate_ore_dressing.py` and `generate_ore_textures.py`, in that order. Run it after
changing a table, after changing those two Java lists, or after updating TFC — and commit what it writes, because
a clone of this repository should build the same mod.

## Licence

Copyright (c) 2026 gamefoldes-ops

Licensed under the EUPL, Version 1.2 (the "Licence"). A copy of the Licence is in [LICENSE](LICENSE); the full
text in every official language is published by the European Commission at
<https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32017D0863>.

You may obtain and use the Work under its terms; any use not authorised by the Licence is prohibited. The Work is
provided "as is", and the Licensor accepts no liability for damage arising from its use. A Modified Work you
distribute must carry a notice stating the change and its date, and it stays under the EUPL — although Article 5
lets it travel under one of the compatible licences listed in the Appendix instead, which includes LGPL-3.0, the
terms NeoForge itself is under. Minecraft's own terms are untouched by this Licence, and so are the official
Mojang mapping names this mod compiles against:
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
