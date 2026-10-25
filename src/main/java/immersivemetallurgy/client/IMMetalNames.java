package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.common.fluids.FluidHolder;
import net.dries007.tfc.common.fluids.TFCFluids;
import net.dries007.tfc.util.Metal;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What each molten fluid is called when it is not actually molten, because TFC only ever names the fluid
 * form ("Molten Bronze") and a crucible that cooled down is holding metal, not melt. Also keeps the list
 * of fluids whose name hides what they are made of, which is every flawed metal and junk.
 */
public final class IMMetalNames {
    private static final Map<Fluid, Component> METAL_NAMES = new HashMap<>();
    private static final Set<Fluid> OPAQUE = new HashSet<>();

    private IMMetalNames() {
    }

    public static Optional<Component> metalName(Fluid fluid) {
        ensureLoaded();
        return Optional.ofNullable(METAL_NAMES.get(fluid));
    }

    public static Component name(Fluid fluid) {
        ensureLoaded();
        Component metal = METAL_NAMES.get(fluid);
        return metal != null ? metal : fluid.getFluidType().getDescription();
    }

    public static boolean hidesItsComponents(Fluid fluid) {
        ensureLoaded();
        return OPAQUE.contains(fluid);
    }

    private static void ensureLoaded() {
        if (!METAL_NAMES.isEmpty()) {
            return;
        }
        for (Metal metal : Metal.values()) {
            FluidHolder<?> holder = TFCFluids.METALS.get(metal);
            if (holder != null) {
                METAL_NAMES.put(holder.getSource(), Component.translatable("metal.tfc." + metal.getSerializedName()));
            }
        }
        for (IMFluids.Molten molten : IMFluids.all()) {
            METAL_NAMES.put(molten.fluid(), Component.translatable("metal." + ImmersiveMetallurgy.MODID + "." + molten.langName()));
            OPAQUE.add(molten.fluid());
        }
    }
}
