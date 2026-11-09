package immersivemetallurgy.mixin;

import immersivemetallurgy.furnace.Launder;

import net.dries007.tfc.common.blocks.devices.MoldTableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Giving the launder a body.
 *
 * The furnace's trough is drawn inside the mold table's cell, because one cell holds one block and that block is
 * the table's. A thing built of fireclay that a worker walks straight through says the machine is a decal, so the
 * table takes the trough's footprint on top of its own shape whenever a furnace hole faces it — which is the same
 * question the renderer asks, read from the same block state, and answers the same way on both sides.
 */
@Mixin(MoldTableBlock.class)
public class MoldTableLaunderShapeMixin {
    @Inject(method = "getShape(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/"
            + "BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)"
            + "Lnet/minecraft/world/phys/shapes/VoxelShape;", at = @At("RETURN"), require = 0, cancellable = true)
    private void immersiveMetallurgy$standLaunderInShape(BlockState state, BlockGetter level, BlockPos pos,
                                                         CollisionContext context,
                                                         CallbackInfoReturnable<VoxelShape> ci) {
        Direction side = Launder.sideOf(level, pos);
        if (side != null) {
            ci.setReturnValue(Shapes.or(ci.getReturnValue(), Launder.shapeFor(side)));
        }
    }
}
