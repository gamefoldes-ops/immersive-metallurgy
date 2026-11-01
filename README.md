# Immersive Metallurgy

A NeoForge 1.21.1 addon for [TerraFirmaCraft](https://terrafirmacraft.com/) 4.2 that puts the dirt back into
metalworking: ore carries gangue, a melt that is off-proportion still pours instead of burning away, and what
comes out is flawed metal with a defect profile of its own — brittle, soft, cold-shut or flowy — which then costs
the tool made from it edge, damage and structure. The one thing that takes the dirt back out is a steel-age
refining furnace, which pours from below the floating layer and only works at the height it was built for.

## Where the numbers are

Everything that decides how the systems behave ships as data, and no value meant to be retuned lives in the code.
The four tables below are recipe types only because a recipe is the one datapack structure Minecraft syncs to
the client and reloads with `/reload`, and the crucible's result is worked out on the client as well. One file
per system, a list inside it.

All under `src/main/resources/data/immersive_metallurgy/recipe/`:

| File | Type | What it decides |
| --- | --- | --- |
| `alloy_tolerance/rules.json` | `immersive_metallurgy:alloy_tolerance` | One row per alloy. `tolerance` is how far a single component may miss TFC's band and still pour this metal — bronze 0.06, the steels 0.0, so a steel melt is helped on its slag and not on its proportions. `slag_tolerance` is the share still called clean: 2%, and 0.5% for the steels, because ground inclusions are exactly what ruins an edge. `max_slag` is the ceiling past which the melt is not this metal at all (0.25). `flawed_result` names the fluid a melt pours as when it only fits inside the widened bands. |
| `flaw_profile/profiles.json` | `immersive_metallurgy:flaw_profile` | `defects` — the four archetypes and what a full dose of each costs a tool: attack damage, mining speed, how fast the edge dulls, the chance per hit to chip, and how much of the fault slag and a multi-element miss each feed. `roles` — per metal what running over and running short do, which is the reason too much tin and too little tin are not the same flaw. `grades` — the severity bands that make Minor, Major and Severe (0.25 and 0.5); that grade is also what splits piles of ingots, so the ladder is visible in a chest without opening anything. `durability_floor` 0.25, `unmeasured_flaw` 0.2 (the flaw assumed for a flawed thing that carries no history), `weld_merge_divisor` 2.0 (so two equal halves cost exactly what either one did), `wear` (edge lost per block mined and per hit landed, the floors a blunt tool keeps, honing's restore, ceiling, structure cost and seconds held), and `palette` (the measured ingot colours the flawed tint is mixed from, with `lean`, `defect_darken`, `hue_span`). |
| `ore_fouling/ores.json` | `immersive_metallurgy:ore_fouling` | Per raw thing: `slag_ratio`, the dirt it brings per unit of metal it yields, and `slag_spread`, how much that varies from batch to batch. 99 rows: TFC's 48 ore grades (rich 0.07 through small 0.19), 12 crushed scoops (0.12 — crushing frees the mineral from the rock and takes nothing out of it), 12 washed pieces (0.03, or 0.05 when the mineral is a sulfide, because water moves grit and not sulfur), and 27 slag dusts (0.6, so recovery is a consolation rather than a source of metal). `vessel_slag_floor` 0.10 is what a ceramic pot always holds back, however clean what went into it was. |
| `refining_furnace/furnace.json` | `immersive_metallurgy:refining_furnace` | The furnace, in sections. `geometry` — what one layer of the shaft is worth (800 mB, which is also exactly one tap), how many metal-carrying layers a stack may have (5, so 4000 mB), how far below the top of the stack the slag channel sits (1), and the `headroom` added to every layer (1.15, so a layer really holds 920 mB and filling one is not a minigame). `fire` — the heat, which is what decides how long a melting takes instead of any timer: `base_temperature` 1350 and `fuel_burn_ticks` 1800 are TFC's own charcoal numbers from `data/tfc/tfc/fuel/charcoal.json`, `draught_per_layer` 120 is the chimney's pull and the only reason a three-layer stack can refine iron (1535°) where a one-layer stack cannot, `pour_temperature` 1000 is where the bath stops running out of the tap, and `boil_ticks` 4800 — four game hours at TFC's 1200 player ticks an hour — is the single phase in which slag passes the channel, at `slag_drain_mb_per_tick`. Every one of those ticks is a tick of **world time**: the fire is paid the difference between TFC's calendar (and the world's day time, which vanilla `/time add` moves) and the last moment it was asked, so sleeping through the night or skipping an hour advances and burns a firing instead of pausing it. `slag` — mB one unit of reagent lifts (25), what the dust costs in metal carried off with it (0.015), the slag plate's capacity in units of dust (`plate_capacity`, 64 — and the plate holds it as molten slag, so the vessel's own size is this times `mb_per_flux`) and `share_ceiling` 0.25, the dirtiest bath the furnace will still call metal, which is what caps the powder worth stocking at `capacity × ceiling / mb_per_flux`. `casting` — the ceiling of one tap: how much the bath is offered to the mold on the table (8 × 100 = 800 mB), which a TFC mold usually takes far less of; the mold's own capacity, not this number, decides what one click actually pours. `lining` — mB a pickaxe swing gets back (100) and the share of the lining that dissolves into the next bath (0.25). `charge` — charcoal demanded at ignition per module (8). `reagents` — each powder and what it is worth, so flux 1.0, lime 1.25, soda ash 1.0, wood ash and sylvite 0.6, the rest a poor fraction. |

Two lists are deliberately code rather than data, because they are identities and not tunables: the metals
(`src/main/java/immersivemetallurgy/registry/IMAlloys.java`, whose keys, tints and double-ingot flags everything
else is derived from) and the ore varieties (`.../registry/IMItems.java`). Every item, fluid, recipe and texture
that exists for a metal was generated from those two lists, so the data in this repo cannot drift from the metals
the mod registers.

## Values baked into the generated files

Most of what the game reads at runtime is in the four tables above. A few more values belong to the art or to the
shape of a chain rather than to a rule the game weighs at runtime, and those are baked into the generated JSON and
PNG files they wrote — which are committed, and are what you edit if you want a different mod:

- ore dressing — 5 mB to a scoop, 4 scoops and 100 mB of water to a washed piece of 20 mB (four rather than five
  so a stack of scoops divides without a remainder left sitting in the barrel), 5 powder scoops ground out of a
  washed piece, and the dressed pieces' heat capacity.
- ore fouling — the four grade ratios and their spread, crushed 0.12, washed 0.03 and 0.05, slag dust 0.6, the
  pot's floor. The washed spread is kept tighter on purpose: a washed melt must not be able to roll under a
  rule's `slag_tolerance` and pour clean, because the residue is the reason to build a furnace, not a lottery.
- metals and their art — 100 mB to an ingot and 200 to a double, the 4 mB of metal one slag dust gives back, the
  break chance assumed for a mold whose recipe does not state one, the heat capacities, and how TFC's own ingot
  sprites were degraded into the flawed art (desaturation, darkening, pitting, roughness) so that it agrees with
  the `palette` row above.
- the furnace's pack-side half — its block art, models, crafting, loot, the refractory tag, the knapping patterns
  of the refining mould and of the slag plate, and its names. The tap's speed belongs here rather than to the
  table: it runs at TFC's own channel rate of one mB every second tick, because the stream a player is watching is
  drawn by TFC and a faster pour would be a faster-looking stream than a crucible pour anywhere else in the game.
  The furnace's numbers are not part of that: they stay hand-written in `refining_furnace/furnace.json`.

## Building

    ./gradlew build      # compile and jar
    ./gradlew deploy     # build and copy the jar into the dev instance's mods folder

Before opening the game, `./gradlew runServer` starts a headless server with the same runtime and is worth two
minutes: a recipe naming an item that was never registered, a tag that fails to load, or a codec that rejects a
tuning table only says so in the log, and the log is written whether or not anyone is looking at a screen.

A large part of the content — the flawed metals' items, fluids, heat records, part recipes, tags, art, and the
furnace's models — is written by scripts rather than by hand, and it is committed. **The scripts themselves are
not in this repository**: they stay with the author, because a published mod should not carry the workshop. What
this repo carries is the output, and the output is the truth — edit any generated JSON or PNG directly and it
behaves like every other file here. (A texture you draw by hand is safe even from the scripts: they write art
only where no file exists, and a `name.png.keep` sidecar makes that permanent.)

After a TFC update the same rule applies in reverse: the generated data has to be re-derived, which is what the
local scripts are for — `regenerate_all.py` runs them in the order the data needs, since part recipes are cloned
out of the tags the metal data writes and several of them add rows to the one language file.

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
