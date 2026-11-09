package immersivemetallurgy.furnace;

import immersivemetallurgy.component.AlloyRecord;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMItems;

import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.common.component.size.IItemSize;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * What the furnace's slag dish holds: the slag the fire made, in the plate's own tank, and the metal that ran
 * over the dam, held beside it as a fluid and a count.
 *
 * They share one volume, because they share one bowl — a dish that has metal in it has less room for dirt, and
 * the readout the player sees is the whole of it. The metal is not put in the tank: the tank answers only to the
 * fluids a slag plate is meant to take, and what comes over the dam is the bath itself, which would then be
 * drained as though it were dirt. So it rides on the plate as the two plain values a piece of skimmings already
 * carries, and comes off as one piece of skimmings, sized by what actually ran over rather than by an ingot.
 */
public final class SlagDish {
    private final ItemStack plate;
    private final IMold mold;

    private SlagDish(ItemStack plate, IMold mold) {
        this.plate = plate;
        this.mold = mold;
    }

    /** The dish a stack of ours forms, or null for anything that is not the furnace's slag plate. */
    @Nullable
    public static SlagDish of(ItemStack stack) {
        if (!stack.is(IMItems.SLAG_PLATE.get())) {
            return null;
        }
        IMold mold = IMold.get(stack);
        return mold == null || mold.getTankCapacity(0) <= 0 ? null : new SlagDish(stack, mold);
    }

    public int capacityMb() {
        return mold.getTankCapacity(0);
    }

    public int slagMb() {
        return mold.getFluidInTank(0).getAmount();
    }

    public int metalMb() {
        Integer held = plate.get(IMDataComponents.SKIMMING_MB.get());
        return held == null || held < 0 ? 0 : held;
    }

    public int roomMb() {
        return Math.max(0, capacityMb() - slagMb() - metalMb());
    }

    /**
     * Whether the dish will take this much of what ran over the dam. All of it or none of it: what goes over is
     * one event of metal and dirt together, and a dish that leaves half of it in the hearth would have to be
     * emptied twice to be worth the walk.
     */
    public boolean takes(int mb) {
        return mb > 0 && mb <= roomMb();
    }

    /** The metal standing in the dish, as the fluid it is and how much of it, or null when there is none. */
    @Nullable
    public FluidStack metal() {
        String id = plate.get(IMDataComponents.SKIMMING_FLUID.get());
        int mb = metalMb();
        if (id == null || mb <= 0) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
        return fluid == null ? null : new FluidStack(fluid, mb);
    }

    public void addMetal(FluidStack drawn) {
        plate.set(IMDataComponents.SKIMMING_FLUID.get(),
                BuiltInRegistries.FLUID.getKey(drawn.getFluid()).toString());
        AlloyRecord record = drawn.get(IMDataComponents.ALLOY_RECORD.get());
        if (record != null) {
            plate.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
        plate.set(IMDataComponents.SKIMMING_MB.get(), metalMb() + drawn.getAmount());
    }

    /**
     * The dirt that came over with the metal. It stays in the dish rather than riding on the skimmings: slag that
     * has already left the hearth is paid out as dust, and that is the end of its journey whether it ran through
     * the channel or was carried on a wave of overflow.
     */
    public void addSlag(int mb) {
        if (mb > 0) {
            mold.fill(new FluidStack(IMFluids.SLAG.fluid(), mb), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** The whole of what the dish holds as one piece of skimmings, carrying the heat's own composition. */
    public ItemStack metalAsItem() {
        ItemStack lump = new ItemStack(IMItems.SKIMMINGS.get());
        String id = plate.get(IMDataComponents.SKIMMING_FLUID.get());
        AlloyRecord record = plate.get(IMDataComponents.ALLOY_RECORD.get());
        if (id != null) {
            lump.set(IMDataComponents.SKIMMING_FLUID.get(), id);
        }
        if (record != null) {
            lump.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
        lump.set(IMDataComponents.SKIMMING_MB.get(), metalMb());
        // The lump weighs what its metal weighs, and TFC only sizes a stack from that weight when it is told to:
        // the size component is settled here, at the one place a piece of skimmings comes into the world.
        ((IItemSize) lump.getItem()).modifyWeight(lump);
        return lump;
    }

    public void clearMetal() {
        plate.remove(IMDataComponents.SKIMMING_MB.get());
        plate.remove(IMDataComponents.SKIMMING_FLUID.get());
        plate.remove(IMDataComponents.ALLOY_RECORD.get());
    }

    /** The plate's own handler, for the slag half of what the dish holds. */
    public IMold mold() {
        return mold;
    }

    /** Taking slag out of the dish, hot or not: the dust it pays for is already decided by the time it is asked. */
    public void drainSlag(int mb) {
        mold.drainIgnoringTemperature(mb, IFluidHandler.FluidAction.EXECUTE);
    }
}
