package immersivemetallurgy.furnace;

import net.dries007.tfc.common.blockentities.CrucibleBlockEntity;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.dries007.tfc.common.component.mold.IMold;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * Where a tap or a slag run ends: either TFC's mold table, holding a vessel the metal is cast into, or a crucible
 * standing a block below the launder, which simply takes the metal in. The furnace moves the fluid itself, so a
 * sink only has to answer for its inventory and for the few table-only courtesies (a mould to heat, a source to
 * draw the spout from); a crucible answers those with nothing.
 */
public interface TapSink {
    IFluidHandler inventory(Direction side);

    /** The vessel on a table, or empty for a crucible, which holds metal directly. */
    ItemStack moldStack();

    /** Whether a pour here needs a mould at all; a crucible takes metal without one. */
    boolean needsMold();

    void setSource(BlockPos from, Fluid fluid, Pair<Direction, Byte> link);

    void finishFlow();

    void heatMold(float temperature);

    @Nullable
    static TapSink of(@Nullable BlockEntity entity) {
        if (entity instanceof MoldTableBlockEntity table) {
            return new Table(table);
        }
        if (entity instanceof CrucibleBlockEntity crucible) {
            return new Crucible(crucible);
        }
        return null;
    }

    record Table(MoldTableBlockEntity table) implements TapSink {
        @Override
        public IFluidHandler inventory(Direction side) {
            return table.getSidedFluidInventory(side);
        }

        @Override
        public ItemStack moldStack() {
            return table.getMoldStack();
        }

        @Override
        public boolean needsMold() {
            return true;
        }

        @Override
        public void setSource(BlockPos from, Fluid fluid, Pair<Direction, Byte> link) {
            table.setSource(from, fluid, link);
            // TFC's own crucible syncs the table right after handing it a source; setSource alone writes no
            // packet, so without this the client never learns the tap is open and draws no stream over the mould.
            table.markForSync();
        }

        @Override
        public void finishFlow() {
            table.finishFlow();
        }

        @Override
        public void heatMold(float temperature) {
            // A clay mould is cold and metal poured into a cold mould freezes where it lands; TFC's own drip path
            // heats the receiving mould as it fills, so a tap that did not do this assembled a quarter of a cast.
            IMold mold = IMold.get(table.getMoldStack());
            if (mold != null) {
                mold.setTemperature(temperature);
                table.setAndUpdateSlots(MoldTableBlockEntity.MOLD_SLOT);
            }
        }
    }

    record Crucible(CrucibleBlockEntity crucible) implements TapSink {
        @Override
        public IFluidHandler inventory(Direction side) {
            return crucible.getSidedFluidInventory(side);
        }

        @Override
        public ItemStack moldStack() {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean needsMold() {
            return false;
        }

        @Override
        public void setSource(BlockPos from, Fluid fluid, Pair<Direction, Byte> link) {
        }

        @Override
        public void finishFlow() {
        }

        @Override
        public void heatMold(float temperature) {
        }
    }
}