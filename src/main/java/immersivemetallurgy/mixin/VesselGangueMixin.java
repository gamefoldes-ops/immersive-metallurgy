package immersivemetallurgy.mixin;

import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.recipe.OreFouling;
import immersivemetallurgy.registry.IMFluids;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import net.dries007.tfc.common.component.mold.Vessel;
import net.dries007.tfc.common.component.mold.VesselComponent;
import net.dries007.tfc.util.FluidAlloy;
import net.dries007.tfc.util.Helpers;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A ceramic pot melts without a skim and without flux, so the gangue the charge carried stays in the pour and
 * the pot wall and the open fire add more to it. TFC knows no slag at all, so a pot and a crucible would pour
 * the same ingot from the same charge. This holds the pot's melt down to a slag floor as the melt is written
 * back: dirt that has no room of its own takes metal with it, which is what a fire loss is, and which keeps the
 * ceiling true even when the pot is filled to its capacity.
 */
@Mixin(Vessel.class)
public class VesselGangueMixin {
    @Shadow
    private Vessel.ContainerInfo containerInfo;

    @Shadow
    private void updateWith(VesselComponent vessel) {
    }

    /** Only a melt fouls the pot: this call is reached exactly when a slot turned from item to fluid. */
    @Redirect(method = "updateInventoryOnMelt()V",
            at = @At(value = "INVOKE", target = "Lnet/dries007/tfc/common/component/mold/Vessel.updateWith(Lnet/dries007/tfc/common/component/mold/VesselComponent;)V"))
    private void immersiveMetallurgy$foulTheMelt(Vessel self, VesselComponent melted) {
        immersiveMetallurgy$holdToFloor(melted.fluidContent());
        updateWith(melted);
    }

    @Unique
    private void immersiveMetallurgy$holdToFloor(FluidAlloy alloy) {
        OreFouling fouling = OreFouling.of(Helpers.getUnsafeRecipeManager());
        float floor = fouling == null ? 0F : fouling.vesselSlagFloor();
        int total = alloy.getAmount();
        if (floor <= 0F || total <= 0) {
            return;
        }
        Object2DoubleMap<Fluid> content = alloy.getContent();
        double slag = content.getDouble(IMFluids.SLAG.fluid());
        double metal = total - slag;
        int extra = Math.min((int) Math.ceil(floor * total) - (int) Math.floor(slag), (int) Math.floor(metal));
        MeltComposition container = (MeltComposition) (Object) alloy;
        int taken = 0;
        for (Fluid fluid : content.keySet().toArray(new Fluid[0])) {
            if (fluid != IMFluids.SLAG.fluid()) {
                taken += container.immersiveMetallurgyTake(fluid, (int) Math.floor(extra * content.getDouble(fluid) / metal));
            }
        }
        // Proportional cuts round down every time, and a pot that ends a millilitre short of the floor is a pot
        // that lets one clean ingot through, so the remainder is taken from whatever metal is still in the way.
        for (Fluid fluid : content.keySet().toArray(new Fluid[0])) {
            if (taken >= extra) {
                break;
            }
            if (fluid != IMFluids.SLAG.fluid()) {
                taken += container.immersiveMetallurgyTake(fluid, extra - taken);
            }
        }
        if (taken > 0) {
            alloy.fill(new FluidStack(IMFluids.SLAG.fluid(), taken), IFluidHandler.FluidAction.EXECUTE, this.containerInfo);
        }
    }
}
