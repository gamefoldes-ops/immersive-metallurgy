package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMItems;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
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

    /**
     * The tap's spout: TFC's own channel, drawn rather than placed.
     *
     * A launder hung over a table wants to look like the trough it is, and TFC already drew one — including the
     * variant with a hole in its bottom for the metal to leave through. So the furnace borrows its pieces
     * (`base`, the `connection` reaching to the duct, and a `stop` on every side that is not) rather than keeping
     * a copy of its own. Nothing is placed and nothing is stored: one cell holds one block and that cell belongs
     * to the mold table, the duct behind the table carries which line it runs on, and this is the renderer that
     * already draws things in that cell.
     */
    public static final ModelResourceLocation CHANNEL_BASE = standalone("tfc:block/channel_base");
    public static final ModelResourceLocation CHANNEL_CONNECTION = standalone("tfc:block/channel_connection");
    public static final ModelResourceLocation CHANNEL_STOP = standalone("tfc:block/channel_stop");

    private IMVesselShapes() {
    }

    /** Whether the table holds a vessel of ours, which is the one case where it must not draw the item's model. */
    public static boolean isOurs(ItemStack vessel) {
        Item item = vessel.getItem();
        return item == IMItems.SLAG_PLATE.get() || item == IMItems.REFINING_MOLD.get()
                || item == IMItems.CLAY_REFINING_MOLD.get();
    }

    /**
     * Whether the table holds one of the two eight-ingot trays, as opposed to the slag dish. Only the trays stand
     * a pixel above the board, and they are the only ones whose pour has to follow them up there.
     */
    public static boolean isTray(ItemStack vessel) {
        Item item = vessel.getItem();
        return item == IMItems.REFINING_MOLD.get() || item == IMItems.CLAY_REFINING_MOLD.get();
    }

    @SubscribeEvent
    public static void registerShapes(ModelEvent.RegisterAdditional event) {
        event.register(TRAY);
        event.register(CHANNEL_BASE);
        event.register(CHANNEL_CONNECTION);
        event.register(CHANNEL_STOP);
    }

    /**
     * One of TFC's channel pieces, or null when the resource pack did not give it — which reads as no launder,
     * the metal still being painted by TFC itself.
     */
    @Nullable
    public static BakedModel channel(ModelResourceLocation piece) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(piece);
        return model == Minecraft.getInstance().getModelManager().getMissingModel() ? null : model;
    }

    /**
     * How much to turn a channel piece about the cell's centre so it reaches the given side. TFC's multipart
     * states give these angles as `y` rotations, which run the other way round from the pose stack's, so this
     * hands back the negation and the renderer can pass it straight to the axis.
     */
    public static float pieceYaw(Direction side) {
        return switch (side) {
            case EAST -> 0F;
            case SOUTH -> -90F;
            case WEST -> 180F;
            case NORTH -> -270F;
            default -> 0F;
        };
    }

    /** A model file of another mod, asked for by the very path its own blockstate uses. */
    private static ModelResourceLocation standalone(String path) {
        return ModelResourceLocation.standalone(ResourceLocation.parse(path));
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
