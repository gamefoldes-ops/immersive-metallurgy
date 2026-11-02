package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The furnace's vessels as solid objects, for the mold table to stand on its board.
 *
 * A tray is a vessel, so its item is a flat sprite the pour is painted onto, and that is right everywhere except
 * on the table: the table draws the thing it holds, and beds printed on a plate do not read as the pits metal is
 * poured into. So the tray gets a shape of its own — authored in the table's own sixteenths and drawn in
 * `tfc:block/mold`, the texture TFC shapes its own moulds from — registered as a standalone model rather than hung
 * on the item, because the item's sprite has to stay the pour's canvas. The slag plate needs no such treatment: it
 * is never carried and shows no fill level in an icon, so its shape is simply its own item model.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public final class IMVesselShapes {
    /** One tray shape for both clays, as TFC draws one ingot mould shape for fire clay and common clay alike. */
    private static final ModelResourceLocation TRAY =
            ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                    ImmersiveMetallurgy.MODID, "item/refining_tray"));

    private IMVesselShapes() {
    }

    /** Whether the table holds a vessel of ours, which is the one case where it must not draw the item's model. */
    public static boolean isOurs(ItemStack vessel) {
        Item item = vessel.getItem();
        return item == IMItems.SLAG_PLATE.get() || item == IMItems.REFINING_MOLD.get()
                || item == IMItems.CLAY_REFINING_MOLD.get();
    }

    @SubscribeEvent
    public static void registerShapes(ModelEvent.RegisterAdditional event) {
        event.register(TRAY);
    }

    /**
     * The shape a vessel is drawn with, or null for an item the table has no shape for and for a shape the resource
     * pack did not give us — which the table reads as "draw nothing", the metal still being painted by TFC itself.
     */
    @Nullable
    public static BakedModel baked(ItemStack vessel) {
        Item item = vessel.getItem();
        if (item == IMItems.SLAG_PLATE.get()) {
            return Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(vessel);
        }
        if (item != IMItems.REFINING_MOLD.get() && item != IMItems.CLAY_REFINING_MOLD.get()) {
            return null;
        }
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(TRAY);
        return model == Minecraft.getInstance().getModelManager().getMissingModel() ? null : model;
    }
}
