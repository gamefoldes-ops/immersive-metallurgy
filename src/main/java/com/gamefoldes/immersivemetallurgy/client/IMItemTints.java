package com.gamefoldes.immersivemetallurgy.client;

import com.gamefoldes.immersivemetallurgy.ImmersiveMetallurgy;
import com.gamefoldes.immersivemetallurgy.component.AlloyRecord;
import com.gamefoldes.immersivemetallurgy.component.DefectProfile;
import com.gamefoldes.immersivemetallurgy.recipe.FlawProfiles;
import com.gamefoldes.immersivemetallurgy.registry.IMAlloys;
import com.gamefoldes.immersivemetallurgy.registry.IMDataComponents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Colours flawed metal by what it is mostly made of. The item textures are neutral grey and multiplied by
 * this, so a tin-heavy bronze reads pale and cold while a copper-heavy one reads warm: the same information
 * the crucible list spells out in numbers, and unlike a texture per melt it also reaches tools, whose item
 * ids are TFC's own.
 *
 * The palette is read from the flaw profile recipe, so it is client synced and reloadable. A stack without
 * a composition gets white, which leaves every clean object exactly as TFC drew it.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public final class IMItemTints {
    private static final int NO_TINT = 0xFFFFFF;
    /** Half a second is long enough that a chest full of flawed metal costs nothing per frame. */
    private static final long PALETTE_TTL_MILLIS = 500;

    private static final Map<String, Integer> PALETTE = new HashMap<>();
    private static long paletteReadAt;
    private static float lean = 0.6F;
    private static float defectDarken = 0.35F;

    private IMItemTints() {
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        ItemColor tint = (stack, layer) -> layer == 0 ? tintOf(stack) : NO_TINT;
        event.register(tint, flawableItems().toArray(new Item[0]));
    }

    /** TFC's own art is already coloured, so a full tint there reads as mud rather than as a lean. */
    private static final float FOREIGN_TEXTURE_SOFTEN = 0.35F;

    private static int tintOf(ItemStack stack) {
        AlloyRecord record = stack.get(IMDataComponents.ALLOY_RECORD.get());
        if (record == null || record.isEmpty()) {
            return NO_TINT;
        }
        refreshPalette();

        int red = 0;
        int green = 0;
        int blue = 0;
        int total = record.totalParts();
        for (AlloyRecord.FluidParts part : record.parts()) {
            Integer color = PALETTE.get(BuiltInRegistries.FLUID.getKey(part.fluid()).toString());
            if (color == null) {
                continue;
            }
            float share = (float) part.parts() / total;
            red += Math.round(((color >> 16) & 0xFF) * share);
            green += Math.round(((color >> 8) & 0xFF) * share);
            blue += Math.round((color & 0xFF) * share);
        }
        if (red + green + blue == 0) {
            return NO_TINT;
        }

        float strength = lean;
        DefectProfile defect = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        if (defect != null) {
            float worst = Math.max(defect.brittle(), Math.max(defect.soft(), Math.max(defect.edgeWear(), defect.flowy())));
            strength = Math.min(1F, strength + worst * defectDarken);
        }
        if (!ImmersiveMetallurgy.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())) {
            strength *= FOREIGN_TEXTURE_SOFTEN;
        }
        return towardTarget(255, 255, 255, red, green, blue, strength);
    }

    private static int towardTarget(int fromRed, int fromGreen, int fromBlue, int toRed, int toGreen, int toBlue, float strength) {
        return (Math.round(fromRed + (toRed - fromRed) * strength) << 16)
                | (Math.round(fromGreen + (toGreen - fromGreen) * strength) << 8)
                | Math.round(fromBlue + (toBlue - fromBlue) * strength);
    }

    private static void refreshPalette() {
        long now = System.currentTimeMillis();
        if (now - paletteReadAt < PALETTE_TTL_MILLIS) {
            return;
        }
        paletteReadAt = now;
        if (Minecraft.getInstance().level == null) {
            return;
        }
        FlawProfiles profiles = FlawProfiles.of(Minecraft.getInstance().level.getRecipeManager());
        if (profiles == null) {
            return;
        }
        PALETTE.clear();
        for (FlawProfiles.MetalColor color : profiles.palette().colors()) {
            PALETTE.put(BuiltInRegistries.FLUID.getKey(color.fluid()).toString(), color.color());
        }
        lean = profiles.palette().lean();
        defectDarken = profiles.palette().defectDarken();
    }

    /** The items that can hold flawed metal: our own flawed ones, and TFC's parts and tools of supported alloys. */
    private static List<Item> flawableItems() {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (ImmersiveMetallurgy.MODID.equals(id.getNamespace())) {
                if (id.getPath().contains("flawed_")) {
                    items.add(item);
                }
            } else if ("tfc".equals(id.getNamespace())) {
                for (IMAlloys alloy : IMAlloys.ALL) {
                    if (id.getPath().endsWith("/" + alloy.key())) {
                        items.add(item);
                        break;
                    }
                }
            }
        }
        return items;
    }
}
