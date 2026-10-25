package com.gamefoldes.immersivemetallurgy.registry;

import java.util.List;

/**
 * The alloys this mod gives a flawed form to, and the only place their identities are spelled out: the
 * molten fluids, the ingot items, and the {@code rules.json} entries all hang off this list, so adding an
 * alloy is one row here plus one entry in the tolerance file.
 *
 * Temperatures are deliberately not here — TFC reads them from {@code tfc:fluid_heat} and
 * {@code tfc:item_heat} data, which {@code tools/generate_flawed_data.mjs} writes.
 */
public record IMAlloys(String key, String display, int tint, boolean doubleIngot) {
    /** TFC's nine crucible alloys. The three weak steels have no double ingot in TFC either. */
    public static final List<IMAlloys> ALL = List.of(
            new IMAlloys("bronze", "Bronze", 0xFF9B7A44, true),
            new IMAlloys("bismuth_bronze", "Bismuth Bronze", 0xFF7F8A76, true),
            new IMAlloys("black_bronze", "Black Bronze", 0xFF4E4A55, true),
            new IMAlloys("brass", "Brass", 0xFF9A8A4E, true),
            new IMAlloys("rose_gold", "Rose Gold", 0xFFA87B6E, true),
            new IMAlloys("sterling_silver", "Sterling Silver", 0xFF8C8F93, true),
            new IMAlloys("weak_steel", "Weak Steel", 0xFF5B6470, false),
            new IMAlloys("weak_blue_steel", "Weak Blue Steel", 0xFF4F6373, false),
            new IMAlloys("weak_red_steel", "Weak Red Steel", 0xFF6E5348, false));

    public String flawedFluidPath() {
        return "metal/flawed_" + key;
    }

    public String flawedIngotPath() {
        return "metal/ingot/flawed_" + key;
    }

    public String flawedDoubleIngotPath() {
        return "metal/double_ingot/flawed_" + key;
    }
}
