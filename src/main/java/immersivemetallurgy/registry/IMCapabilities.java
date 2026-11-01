package immersivemetallurgy.registry;

import net.dries007.tfc.common.capabilities.ItemCapabilities;
import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.common.component.mold.Mold;
import net.dries007.tfc.common.items.MoldItem;
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
    private IMCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerItem(ItemCapabilities.MOLD, IMCapabilities::moldOf,
                IMItems.SLAG_PLATE.get(), IMItems.REFINING_MOLD.get());
        event.registerItem(ItemCapabilities.HEAT, (stack, context) -> moldOf(stack, context).heatContainer(),
                IMItems.SLAG_PLATE.get(), IMItems.REFINING_MOLD.get());
    }

    private static Mold moldOf(net.minecraft.world.item.ItemStack stack, Object context) {
        return new Mold(stack, ((MoldItem) stack.getItem()).containerInfo());
    }
}
