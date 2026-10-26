# Immersive Metallurgy

A NeoForge 1.21.1 addon for [TerraFirmaCraft](https://terrafirmacraft.com/) 4.2 that puts the dirt back into
metalworking: ore carries gangue, a melt that is off-proportion still pours instead of burning away, and what
comes out is flawed metal with a defect profile of its own — brittle, soft, cold-shut or flowy — which then costs
the tool made from it edge, damage and structure.

Everything that decides those numbers ships as data, not as code:

| Recipe type | What it tunes |
| --- | --- |
| `immersive_metallurgy:alloy_tolerance` | how far a melt may drift from an alloy's bands and still take a metal |
| `immersive_metallurgy:ore_fouling` | how much slag each ore, powder and dust brings into a bath, and the ceramic pot's slag floor |
| `immersive_metallurgy:slag_skimming` | what a bowl lifts off the top, by hand and with flux |
| `immersive_metallurgy:flaw_profile` | which fault each metal is prone to, and what each fault costs a tool |

## Building

    ./gradlew build      # compile and jar
    ./gradlew deploy     # build and copy the jar into the dev instance's mods folder

The generated content is not hand-written. `generate_flawed_data.py`, `generate_flawed_textures.py`,
`generate_flawed_part_recipes.py`, `generate_ore_fouling.py`, `generate_ore_dressing.py` and
`generate_ore_textures.py` (all in `tools/`) rebuild the flawed metals, their art and recipes, the ore-dressing
chain and the fouling table from `src/main/java/immersivemetallurgy/registry/IMAlloys.java`, `IMItems.java` and
the TFC jar itself, so the data cannot drift from the metals the mod registers. Run them after changing those
lists or updating TFC.

## Licence

Copyright (c) 2026 gamefoldes-ops

Licensed under the EUPL, Version 1.2 (the "Licence"). A copy of the Licence is in [LICENSE](LICENSE); the full
text in every official language is published by the European Commission at
<https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32017D0863>.

You may obtain and use the Work under its terms; any use not authorised by the Licence is prohibited. The Work is
provided "as is", and the Licensor accepts no liability for damage arising from its use. A Modified Work you
distribute must carry a notice stating the change and its date, and it stays under the EUPL — although Article 5
lets it travel under one of the compatible licences listed in the Appendix instead, which includes LGPL-3.0, the
terms NeoForge itself is under. Minecraft's own terms are untouched by this Licence.

The repository started from the NeoForge MDK template, whose files remain under their own terms
(see [TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt)), and the official Mojang mapping names it compiles against are
covered separately: <https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
