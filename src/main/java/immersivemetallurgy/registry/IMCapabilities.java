package immersivemetallurgy.registry;

import net.dries007.tfc.common.capabilities.ItemCapabilities;
import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.common.component.mold.Mold;
import net.dries007.tfc.common.items.MoldItem;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Saying out loud that the two vessels this mod adds are vessels.
 *
 * TFC's mold table, its casting recipes and its heat all reach a mould through the {@link IMold} capability rather
 * than by checking what class the item is, so a {@link MoldItem} the game was never told about is, to every one of
 * those systems, a stick: the table answers a pour into it with the same refusal it gives a full mould, the slag
 * plate holds nothing, and a tooltip has no volume to print. That is what this registration is for, and it is the
 * whole reason the furnace's tap could never fill its own mould.
 */
public final class IMCapabilities {
    /** Every vessel this mod adds, all of them TFC's own {@link MoldItem}. */
    private static final net.minecraft.world.item.Item[] VESSELS = {
            IMItems.SLAG_PLATE.get(), IMItems.REFINING_MOLD.get(), IMItems.CLAY_REFINING_MOLD.get(),
    };

    private IMCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerItem(ItemCapabilities.MOLD, IMCapabilities::moldOf,
                VESSELS);
        event.registerItem(ItemCapabilities.HEAT, (stack, context) -> moldOf(stack, context).heatContainer(), VESSELS);
        // The mold table fills a vessel through the *fluid handler* capability, not through the mold one: asking
        // only for MOLD left our trays readable but unreachable, so a tap opened on them and delivered nothing.
        event.registerItem(Capabilities.FluidHandler.ITEM, IMCapabilities::moldOf, VESSELS);
    }

    private static Mold moldOf(net.minecraft.world.item.ItemStack stack, Object context) {
        return new Mold(stack, ((MoldItem) stack.getItem()).containerInfo());
    }
}
