package immersivemetallurgy.mixin;

import immersivemetallurgy.client.IMVesselShapes;

import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeping the table's block model from drawing our vessels a second time.
 *
 * A mold table asks for the baked model of `<item namespace>:item/<item path>` and appends its quads to the board,
 * which is how TFC draws the mould standing on it. Our vessels answer that address with their inventory icons — a
 * `tfc:fluid_container` sprite whose whole job is to be painted with a fill level — so the table laid a flat picture
 * of the icon over the tray it was already drawing, and the icon's pour region, read from the item rather than from
 * the table's tank, came out pale. The tray is drawn by our block-entity renderer instead, in the table's own
 * sixteenths, so the block path is handed nothing for these three items.
 */
@Mixin(MoldTableBlockEntity.class)
public class MoldTableItemShapeMixin {

    @Inject(method = "getModelData()Lnet/neoforged/neoforge/client/model/data/ModelData;", at = @At("HEAD"),
            cancellable = true, require = 0)
    private void immersiveMetallurgy$hideOurIcon(CallbackInfoReturnable<ModelData> ci) {
        MoldTableBlockEntity table = (MoldTableBlockEntity) (Object) this;
        if (IMVesselShapes.isOurs(table.getMoldStack())) {
            ci.setReturnValue(ModelData.EMPTY);
        }
    }
}
