package immersivemetallurgy.registry;

import java.util.List;

/**
 * The metals this mod gives a flawed form to, and the only place their identities are spelled out: the molten
 * fluids, the ingot items, and the {@code rules.json} entries all hang off this list, so adding a metal is one
 * row here plus one entry in the tolerance file.
 *
 * Every metal TFC can hold in a crucible is listed, not just the alloys, because a dirty melt of a single metal
 * is the commonest case in the game: smelting copper ore makes copper with gangue in it, and that has to become
 * flawed copper rather than TFC's unknown junk.
 *
 * Temperatures are deliberately not here — TFC reads them from {@code tfc:fluid_heat} and {@code tfc:item_heat}
 * data, which {@code tools/generate_flawed_data.py} writes. The tints are the average colour of TFC's own ingot
 * art for that metal, so the flawed palette and the clean art cannot disagree.
 */
public record IMAlloys(String key, String display, int tint, boolean doubleIngot) {
    public static final List<IMAlloys> ALL = List.of(
            new IMAlloys("copper", "Copper", 0xFF935A4B, true),
            new IMAlloys("tin", "Tin", 0xFFA6ABB3, true),
            new IMAlloys("zinc", "Zinc", 0xFFA2A1A7, true),
            new IMAlloys("bismuth", "Bismuth", 0xFF637572, true),
            new IMAlloys("silver", "Silver", 0xFF9A9494, true),
            new IMAlloys("gold", "Gold", 0xFFBE9B3E, true),
            new IMAlloys("nickel", "Nickel", 0xFF818172, true),
            new IMAlloys("bronze", "Bronze", 0xFF8F7558, true),
            new IMAlloys("bismuth_bronze", "Bismuth Bronze", 0xFF80A08B, true),
            new IMAlloys("black_bronze", "Black Bronze", 0xFF5A405D, true),
            new IMAlloys("brass", "Brass", 0xFF9D8955, true),
            new IMAlloys("rose_gold", "Rose Gold", 0xFFCA987B, true),
            new IMAlloys("sterling_silver", "Sterling Silver", 0xFFAC9E94, true),
            new IMAlloys("wrought_iron", "Wrought Iron", 0xFF989898, true),
            new IMAlloys("cast_iron", "Cast Iron", 0xFF312724, true),
            new IMAlloys("pig_iron", "Pig Iron", 0xFF716769, false),
            new IMAlloys("steel", "Steel", 0xFF5B6C6F, true),
            new IMAlloys("black_steel", "Black Steel", 0xFF393939, true),
            new IMAlloys("blue_steel", "Blue Steel", 0xFF6A779A, true),
            new IMAlloys("red_steel", "Red Steel", 0xFFA25458, true),
            new IMAlloys("weak_steel", "Weak Steel", 0xFF484D54, false),
            new IMAlloys("weak_blue_steel", "Weak Blue Steel", 0xFF50649B, false),
            new IMAlloys("weak_red_steel", "Weak Red Steel", 0xFF944848, false),
            new IMAlloys("high_carbon_steel", "High Carbon Steel", 0xFF4E6C73, false),
            new IMAlloys("high_carbon_black_steel", "High Carbon Black Steel", 0xFF202020, false),
            new IMAlloys("high_carbon_blue_steel", "High Carbon Blue Steel", 0xFF384E8C, false),
            new IMAlloys("high_carbon_red_steel", "High Carbon Red Steel", 0xFF823030, false));

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
