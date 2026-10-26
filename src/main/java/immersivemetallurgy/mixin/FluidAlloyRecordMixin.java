package immersivemetallurgy.mixin;

import immersivemetallurgy.component.AlloyRecord;
import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.dries007.tfc.common.component.fluid.FluidContainerInfo;
import net.dries007.tfc.util.FluidAlloy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code FluidAlloy} holds only fluid-to-amount pairs, so the moment a flawed or junk melt sits in a
 * container the recipe that described it is gone. This keeps a side table of what each such fluid was
 * made of: the container still lists the fluid under its own identity, and only the alloy arithmetic sees
 * the leaf metals. Every fluid that leaves the container is stamped with that composition, which is how a
 * cast ingot can later be melted back into what it really was.
 */
@Mixin(FluidAlloy.class)
public class FluidAlloyRecordMixin implements MeltComposition {
    @Unique
    private static final String COMPOSITIONS_KEY = "immersive_metallurgy:compositions";
    private static final String FLUXED_KEY = "immersive_metallurgy:fluxed";

    @Shadow
    private Object2DoubleMap<Fluid> content;

    @Shadow
    private int amount;

    @Shadow
    private void clearCaches() {
    }

    @Unique
    private Object2ObjectMap<Fluid, AlloyRecord> immersiveMetallurgy$compositions;

    @Unique
    private AlloyRecord immersiveMetallurgy$leafRecord;

    @Unique
    private DefectProfile immersiveMetallurgy$defect;

    @Unique
    private boolean immersiveMetallurgy$fluxed;

    @Override
    public DefectProfile immersiveMetallurgyDefect() {
        return this.immersiveMetallurgy$defect == null ? DefectProfile.CLEAN : this.immersiveMetallurgy$defect;
    }

    @Override
    public void immersiveMetallurgySetDefect(DefectProfile defect) {
        this.immersiveMetallurgy$defect = defect;
    }

    /**
     * Removes one fluid without disturbing the others, which is what skimming needs: the container's own
     * drain takes every fluid in proportion, so a skim would throw away metal with the dirt. The cached
     * result is dropped through TFC's own path so the next lookup recomputes against the new contents.
     */
    @Override
    public int immersiveMetallurgyTake(Fluid fluid, int mB) {
        double present = this.content.getDouble(fluid);
        int taken = (int) Math.min(mB, Math.floor(present));
        if (taken <= 0) {
            return 0;
        }
        double left = present - taken;
        if (left < 1D) {
            this.content.removeDouble(fluid);
        } else {
            this.content.put(fluid, left);
        }
        this.amount -= taken;
        // A skim is the one change that has to forget the ratio: a pour needs it to hold still only while that
        // pour is in flight, and the dirt lifted off the top is no longer in anything poured after it.
        this.immersiveMetallurgy$leafRecord = null;
        clearCaches();
        return taken;
    }

    @Override
    public Object2ObjectMap<Fluid, AlloyRecord> immersiveMetallurgyCompositions() {
        if (this.immersiveMetallurgy$compositions == null) {
            this.immersiveMetallurgy$compositions = new Object2ObjectOpenHashMap<>();
        }
        return this.immersiveMetallurgy$compositions;
    }

    @Override
    public AlloyRecord immersiveMetallurgyRecord() {
        return this.immersiveMetallurgy$leafRecord;
    }

    @Override
    public void immersiveMetallurgySetRecord(AlloyRecord record) {
        this.immersiveMetallurgy$leafRecord = record;
    }

    @Override
    public boolean immersiveMetallurgyFluxed() {
        return this.immersiveMetallurgy$fluxed;
    }

    @Override
    public void immersiveMetallurgySetFluxed(boolean fluxed) {
        this.immersiveMetallurgy$fluxed = fluxed;
    }

    @Override
    public Object2DoubleMap<Fluid> immersiveMetallurgyLeafContent() {
        Object2DoubleMap<Fluid> leaves = new Object2DoubleOpenHashMap<>();
        for (Object2DoubleMap.Entry<Fluid> entry : this.content.object2DoubleEntrySet()) {
            AlloyRecord record = immersiveMetallurgyCompositions().get(entry.getKey());
            if (record == null) {
                leaves.mergeDouble(entry.getKey(), entry.getDoubleValue(), Double::sum);
            } else {
                record.addTo(entry.getDoubleValue(), leaves);
            }
        }
        return leaves;
    }

    /**
     * A melt that arrives remembering its recipe stores that memory here instead of being spread over its
     * components, because a crucible fills a mold one millilitre per tick and splitting every drop rounds
     * the smaller metal away.
     */
    @Inject(method = "fill(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/capability/IFluidHandler$FluidAction;Lnet/dries007/tfc/common/component/fluid/FluidContainerInfo;)I",
            at = @At("TAIL"))
    private void immersiveMetallurgy$rememberComposition(FluidStack stack, IFluidHandler.FluidAction action, FluidContainerInfo capacity, CallbackInfoReturnable<Integer> cir) {
        int filled = cir.getReturnValue();
        if (filled <= 0) {
            return;
        }
        this.immersiveMetallurgy$leafRecord = null;
        AlloyRecord record = stack.get(IMDataComponents.ALLOY_RECORD.get());
        if (record != null && record.parts().size() > 1) {
            immersiveMetallurgyCompositions().put(stack.getFluid(), record);
        }

        // The gangue an ore carried follows the metal into the alloy, so smelting straight from rock makes a
        // dirtier melt than smelting refined metal. Only on the real fill: a simulate must not change content.
        Float ratio = action == IFluidHandler.FluidAction.EXECUTE && stack.getFluid() != IMFluids.SLAG.fluid()
                ? stack.get(IMDataComponents.SLAG_RATIO.get())
                : null;
        if (ratio != null && ratio > 0) {
            ((FluidAlloy) (Object) this).fill(new FluidStack(IMFluids.SLAG.fluid(), Mth.floor(filled * ratio)), action, capacity);
        }
    }

    /**
     * The stamped record is refreshed only when the container gains metal, never while it is being poured
     * out: draining keeps the ratios but rounds them differently every millilitre, and a mold refuses a
     * second drip whose record differs from the first one it took.
     */
    @Inject(method = "getResult(Lnet/minecraft/world/item/crafting/RecipeManager;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("TAIL"))
    private void immersiveMetallurgy$stampComposition(RecipeManager recipes, CallbackInfoReturnable<FluidStack> cir) {
        FluidStack result = cir.getReturnValue();
        if (result.isEmpty() || this.content.isEmpty()) {
            return;
        }
        if (this.immersiveMetallurgy$leafRecord == null) {
            this.immersiveMetallurgy$leafRecord = AlloyRecord.fromContent(immersiveMetallurgyLeafContent());
        }
        AlloyRecord record = this.immersiveMetallurgy$leafRecord;
        if (record != null && record.parts().size() > 1) {
            result.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
        DefectProfile defect = this.immersiveMetallurgy$defect;
        if (defect != null && !defect.isClean()) {
            result.set(IMDataComponents.DEFECT_PROFILE.get(), defect);
        }
    }

    @Inject(method = "clearCaches()V", at = @At("TAIL"))
    private void immersiveMetallurgy$forgetGoneFluids(CallbackInfo ci) {
        this.immersiveMetallurgy$defect = null;
        if (this.immersiveMetallurgy$compositions != null) {
            this.immersiveMetallurgy$compositions.keySet().retainAll(this.content.keySet());
        }
    }

    @Inject(method = "copy()Lnet/dries007/tfc/util/FluidAlloy;", at = @At("TAIL"))
    private void immersiveMetallurgy$carryCompositions(CallbackInfoReturnable<FluidAlloy> cir) {
        MeltComposition copy = (MeltComposition) (Object) cir.getReturnValue();
        copy.immersiveMetallurgyCompositions().putAll(immersiveMetallurgyCompositions());
        copy.immersiveMetallurgySetFluxed(immersiveMetallurgyFluxed());
        copy.immersiveMetallurgySetRecord(immersiveMetallurgyRecord());
    }

    @Inject(method = "copyFrom(Lnet/dries007/tfc/util/FluidAlloy;)V", at = @At("TAIL"))
    private void immersiveMetallurgy$adoptCompositions(FluidAlloy other, CallbackInfo ci) {
        MeltComposition source = (MeltComposition) (Object) other;
        immersiveMetallurgyCompositions().putAll(source.immersiveMetallurgyCompositions());
        this.immersiveMetallurgy$fluxed = source.immersiveMetallurgyFluxed();
        this.immersiveMetallurgy$leafRecord = source.immersiveMetallurgyRecord();
    }

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/Tag;", at = @At("TAIL"))
    private void immersiveMetallurgy$writeCompositions(CallbackInfoReturnable<Tag> cir) {
        if (!(cir.getReturnValue() instanceof CompoundTag tag)) {
            return;
        }
        if (this.immersiveMetallurgy$fluxed) {
            tag.putBoolean(FLUXED_KEY, true);
        }
        if (this.immersiveMetallurgy$compositions == null || this.immersiveMetallurgy$compositions.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (Object2ObjectMap.Entry<Fluid, AlloyRecord> entry : this.immersiveMetallurgy$compositions.object2ObjectEntrySet()) {
            CompoundTag fluidRecord = new CompoundTag();
            fluidRecord.putString("fluid", BuiltInRegistries.FLUID.getKey(entry.getKey()).toString());
            AlloyRecord.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue())
                    .result()
                    .ifPresent(encoded -> fluidRecord.put("record", encoded));
            list.add(fluidRecord);
        }
        tag.put(COMPOSITIONS_KEY, list);
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void immersiveMetallurgy$readCompositions(CompoundTag tag, CallbackInfo ci) {
        this.immersiveMetallurgy$leafRecord = null;
        this.immersiveMetallurgy$fluxed = tag.getBoolean(FLUXED_KEY);
        if (!tag.contains(COMPOSITIONS_KEY, Tag.TAG_LIST)) {
            return;
        }
        Object2ObjectMap<Fluid, AlloyRecord> compositions = immersiveMetallurgyCompositions();
        for (Tag element : tag.getList(COMPOSITIONS_KEY, Tag.TAG_COMPOUND)) {
            if (!(element instanceof CompoundTag fluidRecord)) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(fluidRecord.getString("fluid"));
            if (id == null) {
                continue;
            }
            Fluid fluid = BuiltInRegistries.FLUID.get(id);
            if (fluid == Fluids.EMPTY || !fluidRecord.contains("record")) {
                continue;
            }
            AlloyRecord.CODEC.decode(NbtOps.INSTANCE, fluidRecord.get("record"))
                    .result()
                    .ifPresent(pair -> compositions.put(fluid, pair.getFirst()));
        }
    }
}
