package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMItems;

import net.dries007.tfc.common.component.mold.IMold;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.DynamicFluidContainerModel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * The colour of the metal a vessel is drawn with.
 *
 * A fluid container's icon carries no colour of its own: the pour layer is baked with tint index 1, and the colour
 * is asked at draw time from a registered {@link ItemColor}. TFC registers
 * {@link DynamicFluidContainerModel.Colors} for items of its own namespace only, so our trays drew that index
 * unanswered, which is white on every metal, while the same cast on the mold table was coloured correctly because
 * its renderer reads the fluid's colour itself.
 *
 * The standard handler is not enough even once it is registered, because of how it asks: it calls
 * {@link FluidUtil#getFluidContained}, which measures a container by {@code drain(Integer.MAX_VALUE, SIMULATE)} —
 * what the vessel would give up. A mould will not give up a cast that has set, which is the whole reason TFC's
 * {@link IMold#drainIgnoringTemperature} exists, so the question comes back empty and the answer is white. That is
 * the symptom exactly: a tray in the inventory wore its metal while the metal was molten and washed it out as the
 * metal set, while on the table the same cast was right in both states, because the table reads the tank and not
 * the drain.
 *
 * So we read the tank too. Whatever stands in the mould is drawn in that fluid's colour, molten or set, which is
 * the same claim the table makes about the same cast.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public final class IMItemColours {
    private IMItemColours() {
    }

    @SubscribeEvent
    public static void onRegisterItemColours(RegisterColorHandlersEvent.Item event) {
        event.register(new VesselColour(), IMItems.REFINING_MOLD.get(), IMItems.CLAY_REFINING_MOLD.get());
    }

    /** The pour layer's colour, taken from what the mould holds rather than from what it would hand over. */
    private static final class VesselColour implements ItemColor {
        @Override
        public int getColor(ItemStack stack, int tintIndex) {
            if (tintIndex != 1) {
                return 0xFFFFFFFF;
            }
            IMold mold = IMold.get(stack);
            if (mold == null) {
                return FluidUtil.getFluidContained(stack)
                        .map(fluid -> tint(fluid))
                        .orElse(0xFFFFFFFF);
            }
            return tint(mold.getFluidInTank(0));
        }

        private static int tint(FluidStack contained) {
            return contained.isEmpty() ? 0xFFFFFFFF
                    : IClientFluidTypeExtensions.of(contained.getFluid()).getTintColor(contained);
        }
    }
}
