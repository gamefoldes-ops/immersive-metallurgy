package immersivemetallurgy.mixin;

import immersivemetallurgy.alloy.TolerantAlloyMatcher;
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
    private static final String SLAG_OWED_KEY = "immersive_metallurgy:slag_owed";

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

    /** What this bath's own pour should be called, decided with the record so every drip of it agrees. */
    @Unique
    private TolerantAlloyMatcher.Match immersiveMetallurgy$pourAnswer;

    @Override
    public TolerantAlloyMatcher.Match immersiveMetallurgyPourAnswer() {
        return this.immersiveMetallurgy$pourAnswer;
    }

    @Override
    public void immersiveMetallurgySetPourAnswer(TolerantAlloyMatcher.Match answer) {
        this.immersiveMetallurgy$pourAnswer = answer;
    }

    @Unique
    private double immersiveMetallurgy$slagOwed;

    @Override
    public DefectProfile immersiveMetallurgyDefect() {
        return this.immersiveMetallurgy$defect == null ? DefectProfile.CLEAN : this.immersiveMetallurgy$defect;
    }

    @Override
    public void immersiveMetallurgySetDefect(DefectProfile defect) {
        this.immersiveMetallurgy$defect = defect;
    }

    /**
     * Removes one fluid without disturbing the others, which is what holding dirt back needs: the container's
     * own drain takes every fluid in proportion, so it would throw away metal with the dirt. The cached
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
        // Taking fluid out is the one change that has to forget the ratio: a pour needs it to hold still only
        // while that pour is in flight, and the dirt lifted off the top is no longer in anything poured after it.
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
    public double immersiveMetallurgySlagOwed() {
        return this.immersiveMetallurgy$slagOwed;
    }

    @Override
    public void immersiveMetallurgySetSlagOwed(double mB) {
        this.immersiveMetallurgy$slagOwed = mB;
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

        // A melt can arrive knowing how wrong it is without the bath having asked anything yet — that is what
        // happens when an object is melted rather than an ore smelted. Keep the worse of the two, because a
        // bath is one piece of metal, and the pour that comes out of it has to be as bad as its worst part.
        DefectProfile arriving = stack.get(IMDataComponents.DEFECT_PROFILE.get());
        if (arriving != null && !arriving.isClean()) {
            this.immersiveMetallurgy$defect = this.immersiveMetallurgy$defect == null
                    ? arriving
                    : this.immersiveMetallurgy$defect.worseThan(arriving);
        }

        // The gangue an ore carried follows the metal into the alloy, so smelting straight from rock makes a
        // dirtier melt than smelting refined metal. Only on the real fill: a simulate must not change content.
        Float ratio = action == IFluidHandler.FluidAction.EXECUTE && stack.getFluid() != IMFluids.SLAG.fluid()
                ? stack.get(IMDataComponents.SLAG_RATIO.get())
                : null;
        if (ratio != null && ratio > 0) {
            // Slag only comes in whole millibuckets and a scoop of crushed ore is five of metal, so one melt
            // often owes less than it can pay. The change is kept rather than dropped, or the small melts that
            // dressing makes cheap would arrive in the bath cleaner than the rock they came out of.
            this.immersiveMetallurgy$slagOwed += filled * ratio;
            int owed = (int) this.immersiveMetallurgy$slagOwed;
            if (owed > 0) {
                this.immersiveMetallurgy$slagOwed -= owed;
                int paid = ((FluidAlloy) (Object) this).fill(new FluidStack(IMFluids.SLAG.fluid(), owed), action, capacity);
                this.immersiveMetallurgy$slagOwed += owed - paid;
            }
        }
    }

    /**
     * The stamped record is refreshed only when the container gains metal, never while it is being poured
     * out: draining keeps the ratios but rounds them differently every millilitre, and a mold refuses a
     * second drip whose record differs from the first one it took.
     */
    @Inject(method = "getResult(Lnet/minecraft/world/item/crafting/RecipeManager;)Lnet/neoforged/neoforge/fluids/FluidStack;",
            at = @At("TAIL"), cancellable = true)
    private void immersiveMetallurgy$stampComposition(RecipeManager recipes, CallbackInfoReturnable<FluidStack> cir) {
        FluidStack result = cir.getReturnValue();
        if (result.isEmpty() || this.content.isEmpty()) {
            return;
        }
        if (this.immersiveMetallurgy$leafRecord == null) {
            this.immersiveMetallurgy$leafRecord = AlloyRecord.fromContent(immersiveMetallurgyLeafContent());
            this.immersiveMetallurgy$pourAnswer = whatThisBathPoursAs(recipes);
        }
        TolerantAlloyMatcher.Match answer = this.immersiveMetallurgy$pourAnswer;
        if (answer != null && answer.recipe().result() != result.getFluid()) {
            // Decided once per change and remembered, because every drip of one pour has to arrive as the same
            // fluid with the same components or the mold refuses to merge it into what it already holds.
            result = new FluidStack(answer.recipe().result(), result.getAmount());
            cir.setReturnValue(result);
        }
        AlloyRecord record = this.immersiveMetallurgy$leafRecord;
        if (record != null && record.parts().size() > 1) {
            result.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
        DefectProfile defect = this.immersiveMetallurgy$defect != null
                ? this.immersiveMetallurgy$defect
                : (answer == null ? null : answer.defect());
        if (defect != null && !defect.isClean()) {
            result.set(IMDataComponents.DEFECT_PROFILE.get(), defect);
        }
    }

    /**
     * TFC only ever asks what a melt is when the container holds more than one fluid, so a bath of a single
     * alloy that remembers its own proportions was never looked at: an off-ratio melt sat under the clean
     * fluid's name and poured clean ingots out of it. This asks the question the alloy lookup would have asked,
     * for the one case where nothing else did — and only when the melt really carries a recipe, because a bath
     * of leaf metals goes through TFC's own answer as always.
     */
    @Unique
    private TolerantAlloyMatcher.Match whatThisBathPoursAs(RecipeManager recipes) {
        if (this.immersiveMetallurgy$defect != null || this.immersiveMetallurgy$leafRecord == null
                || this.immersiveMetallurgy$leafRecord.parts().size() <= 1) {
            return null;
        }
        TolerantAlloyMatcher.Match match = TolerantAlloyMatcher.match(recipes, (FluidAlloy) (Object) this);
        return match == null || match.clean() ? null : match;
    }

    @Inject(method = "clearCaches()V", at = @At("TAIL"))
    private void immersiveMetallurgy$forgetGoneFluids(CallbackInfo ci) {
        this.immersiveMetallurgy$defect = null;
        if (this.immersiveMetallurgy$compositions != null) {
            this.immersiveMetallurgy$compositions.keySet().retainAll(this.content.keySet());
        }
    }

    @Inject(method = "clear()V", at = @At("TAIL"))
    private void immersiveMetallurgy$forgetTheChange(CallbackInfo ci) {
        // Nothing is owed to an empty container, and the fraction it was owed by does not survive it.
        this.immersiveMetallurgy$slagOwed = 0;
    }

    @Inject(method = "copy()Lnet/dries007/tfc/util/FluidAlloy;", at = @At("TAIL"))
    private void immersiveMetallurgy$carryCompositions(CallbackInfoReturnable<FluidAlloy> cir) {
        MeltComposition copy = (MeltComposition) (Object) cir.getReturnValue();
        copy.immersiveMetallurgyCompositions().putAll(immersiveMetallurgyCompositions());
        copy.immersiveMetallurgySetRecord(immersiveMetallurgyRecord());
        copy.immersiveMetallurgySetSlagOwed(immersiveMetallurgySlagOwed());
        // The vessel copies the alloy for each operation, so what the pour is called has to travel with it or
        // the copy would fill its mold with the clean name the contents still carry.
        copy.immersiveMetallurgySetPourAnswer(immersiveMetallurgyPourAnswer());
    }

    @Inject(method = "copyFrom(Lnet/dries007/tfc/util/FluidAlloy;)V", at = @At("TAIL"))
    private void immersiveMetallurgy$adoptCompositions(FluidAlloy other, CallbackInfo ci) {
        MeltComposition source = (MeltComposition) (Object) other;
        immersiveMetallurgyCompositions().putAll(source.immersiveMetallurgyCompositions());
        this.immersiveMetallurgy$leafRecord = source.immersiveMetallurgyRecord();
        this.immersiveMetallurgy$slagOwed = source.immersiveMetallurgySlagOwed();
        this.immersiveMetallurgy$pourAnswer = source.immersiveMetallurgyPourAnswer();
    }

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/Tag;", at = @At("TAIL"))
    private void immersiveMetallurgy$writeCompositions(CallbackInfoReturnable<Tag> cir) {
        if (!(cir.getReturnValue() instanceof CompoundTag tag)) {
            return;
        }
        if (this.immersiveMetallurgy$slagOwed > 0) {
            tag.putDouble(SLAG_OWED_KEY, this.immersiveMetallurgy$slagOwed);
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
        this.immersiveMetallurgy$slagOwed = tag.getDouble(SLAG_OWED_KEY);
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
