package immersivemetallurgy.recipe;

import immersivemetallurgy.registry.IMRecipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.dries007.tfc.common.recipes.RecipeHelpers;
import net.dries007.tfc.util.data.Fuel;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * The tuning sheet of the refining furnace: how big a layer of shaft is, how hot the fire burns, what a
 * millilitre of slag costs in reagent, and what becomes of slag that had nowhere to go. It ships as a recipe for
 * the same reason the tolerance rules do — a block entity works out its own geometry and its own slag budget on
 * both sides, and only recipes are synced to the client and swapped by {@code /reload}.
 *
 * The numbers are grouped into sections because a codec cannot reach twenty components at once, and because that
 * is how they are read anyway: the shape of the stack, the heat of the fire, what the slag costs, and what the
 * metal is worth.
 *
 * Two of them are copied from TFC rather than invented: {@code fire_temperature} and {@code fuel_burn_ticks} are
 * charcoal's own {@code burn temperature 1350 / duration 1800} from {@code data/tfc/tfc/fuel/charcoal.json}, and
 * the melt threshold of every item in the charge is TFC's own heating recipe, not a number written here.
 */
public record RefiningFurnaceStats(Geometry geometry, Fire fire, Slag slag, Casting casting, Lining lining,
                                   Charge charge, List<Reagent> reagents) implements Recipe<NoopInput> {

    public static final MapCodec<RefiningFurnaceStats> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Geometry.CODEC.fieldOf("geometry").forGetter(RefiningFurnaceStats::geometry),
            Fire.CODEC.fieldOf("fire").forGetter(RefiningFurnaceStats::fire),
            Slag.CODEC.fieldOf("slag").forGetter(RefiningFurnaceStats::slag),
            Casting.CODEC.fieldOf("casting").forGetter(RefiningFurnaceStats::casting),
            Lining.CODEC.fieldOf("lining").forGetter(RefiningFurnaceStats::lining),
            Charge.CODEC.fieldOf("charge").forGetter(RefiningFurnaceStats::charge),
            Reagent.CODEC.listOf().fieldOf("reagents").forGetter(RefiningFurnaceStats::reagents)
    ).apply(instance, RefiningFurnaceStats::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, RefiningFurnaceStats> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    public static RecipeSerializer<RefiningFurnaceStats> serializer() {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<RefiningFurnaceStats> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, RefiningFurnaceStats> streamCodec() {
                return STREAM_CODEC;
            }
        };
    }

    /**
     * The last sheet this side resolved. Vessels — the slag plate, the furnace's large mould — are asked for their
     * capacity from item code that has no level in hand, and TFC solves that with a config value. Ours stays in
     * the tuning sheet instead: whoever reads a number from it leaves it here, and the vessels ask this. Before any
     * data has been read the answer is {@code null}, and a vessel then holds nothing, which is the honest response
     * to being asked how much an unloaded table can take.
     */
    private static volatile RefiningFurnaceStats resolved;

    public static RefiningFurnaceStats of(RecipeManager recipes) {
        for (RecipeHolder<RefiningFurnaceStats> holder : RecipeHelpers.getRecipes(recipes, IMRecipes.REFINING_FURNACE)) {
            resolved = holder.value();
            return holder.value();
        }
        return null;
    }

    /** What the tuning sheet said last time it was read on this side, or null before it has been read at all. */
    public static RefiningFurnaceStats cached() {
        return resolved;
    }

    /** What one layer of the shaft holds, how high a stack may go, and where in it the slag channel sits. */
    public record Geometry(int mbPerModule, int maxModules, int channelOffsetFromTop, float headroom) {

        public static final Codec<Geometry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64000).fieldOf("mb_per_module").forGetter(Geometry::mbPerModule),
                Codec.intRange(1, 16).fieldOf("max_modules").forGetter(Geometry::maxModules),
                Codec.intRange(0, 16).fieldOf("channel_offset_from_top").forGetter(Geometry::channelOffsetFromTop),
                Codec.floatRange(1F, 2F).optionalFieldOf("headroom", 1.15F).forGetter(Geometry::headroom)
        ).apply(instance, Geometry::new));
    }

    /**
     * How the fire behaves, which is what decides how long a heat takes: nothing here is a timer for the melting,
     * because the charge melts when TFC's own recipes say its metal melts.
     *
     * @param boilTicks          how long the bath boils after the last of the charge has gone in, and the only
     *                           time during which the channel lets slag past
     * @param slagDrainMbPerTick how fast the channel lets slag past while the bath boils
     * @param baseTemperature    the degrees a charcoal fire stands at with no draught to speak of — TFC's own
     *                           burn temperature for charcoal
     * @param draughtPerLayer    how much hotter the fire burns for every layer of shaft above the second one,
     *                           which is the whole reason a tall stack is not merely a big one: a short chimney
     *                           draws no air, and without that air the iron family stays solid in it
     * @param fuelBurnTicks      how long one piece of fuel keeps that heat — TFC's charcoal duration
     * @param pourTemperature    below this the bath is no longer liquid enough to run out of the tap
     */
    public record Fire(int boilTicks, int slagDrainMbPerTick, float baseTemperature, float draughtPerLayer,
                       int fuelBurnTicks, float pourTemperature) {

        public static final Codec<Fire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 20 * 60 * 60).fieldOf("boil_ticks").forGetter(Fire::boilTicks),
                Codec.intRange(1, 1000).optionalFieldOf("slag_drain_mb_per_tick", 1).forGetter(Fire::slagDrainMbPerTick),
                Codec.FLOAT.fieldOf("base_temperature").forGetter(Fire::baseTemperature),
                Codec.FLOAT.optionalFieldOf("draught_per_layer", 120F).forGetter(Fire::draughtPerLayer),
                Codec.intRange(1, 20 * 60 * 60).fieldOf("fuel_burn_ticks").forGetter(Fire::fuelBurnTicks),
                Codec.FLOAT.fieldOf("pour_temperature").forGetter(Fire::pourTemperature)
        ).apply(instance, Fire::new));
    }

    /** What slag costs to get rid of, what it costs in metal, how many units of dust the slag plate will hold,
     * and how much of a bath the furnace will ever agree to hold — which is what caps the powder a firing is
     * worth stocking. */
    public record Slag(int mbPerFlux, float metalDrag, int plateCapacity, float shareCeiling) {

        public static final Codec<Slag> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64000).fieldOf("mb_per_flux").forGetter(Slag::mbPerFlux),
                Codec.floatRange(0F, 1F).fieldOf("metal_drag").forGetter(Slag::metalDrag),
                Codec.intRange(1, 64).fieldOf("plate_capacity").forGetter(Slag::plateCapacity),
                Codec.floatRange(0F, 1F).optionalFieldOf("share_ceiling", 0.25F).forGetter(Slag::shareCeiling)
        ).apply(instance, Slag::new));
    }

    /** The ceiling of one tap, in ingots of what size: the most the bath will offer a mold in one click. The mold
     * on TFC's table decides what it actually takes, so this is a limit rather than a pour size. */
    public record Casting(int ingotsPerPour, int mbPerIngot) {

        public static final Codec<Casting> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64).fieldOf("ingots_per_pour").forGetter(Casting::ingotsPerPour),
                Codec.intRange(1, 1000).fieldOf("mb_per_ingot").forGetter(Casting::mbPerIngot)
        ).apply(instance, Casting::new));
    }

    /** What the plated-out lining costs to remove, and how much of it comes back into the next bath. */
    public record Lining(int scrapeMb, float returnShare) {

        public static final Codec<Lining> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64000).fieldOf("scrape_mb").forGetter(Lining::scrapeMb),
                Codec.floatRange(0F, 1F).fieldOf("return_share").forGetter(Lining::returnShare)
        ).apply(instance, Lining::new));
    }

    public record Charge(int perModule, Ingredient fuel) {

        public static final Codec<Charge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 64).fieldOf("per_module").forGetter(Charge::perModule),
                Ingredient.CODEC.fieldOf("fuel").forGetter(Charge::fuel)
        ).apply(instance, Charge::new));
    }

    /**
     * One powder the bath will accept, and what it is worth. Lime and flux both work; the rest of TFC's powders
     * are listed at a fraction, because a reagent that is not the right one for this slag still does something,
     * just slowly and at a poor yield.
     */
    public record Reagent(Ingredient ingredient, float efficiency) {

        public static final Codec<Reagent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Reagent::ingredient),
                Codec.floatRange(0.01F, 4F).optionalFieldOf("efficiency", 1F).forGetter(Reagent::efficiency)
        ).apply(instance, Reagent::new));
    }

    /** What one layer of the shaft is promised: the module's own metal, plus the headroom it takes to fill it. */
    public int layerCapacity() {
        return Math.round(geometry.mbPerModule() * geometry.headroom());
    }

    public int mbPerModule() {
        return geometry.mbPerModule();
    }

    public int maxModules() {
        return geometry.maxModules();
    }

    public int channelOffsetFromTop() {
        return geometry.channelOffsetFromTop();
    }

    public int boilTicks() {
        return fire.boilTicks();
    }

    public int slagDrainMbPerTick() {
        return fire.slagDrainMbPerTick();
    }

    /**
     * How hot the fire burns in a stack this tall: the draught of the chimney is what lets a charcoal fire reach
     * past the iron family's 1535°, and a two-layer shaft has none of it.
     */
    public float fireTemperature(int openLayers) {
        return fire.baseTemperature() + fire.draughtPerLayer() * Math.max(0, openLayers - 2);
    }

    public float baseTemperature() {
        return fire.baseTemperature();
    }

    public float draughtPerLayer() {
        return fire.draughtPerLayer();
    }

    public int fuelBurnTicks() {
        return fire.fuelBurnTicks();
    }

    public float pourTemperature() {
        return fire.pourTemperature();
    }

    public int mbSlagPerFlux() {
        return slag.mbPerFlux();
    }

    public float metalDrag() {
        return slag.metalDrag();
    }

    /** How many units of dust the slag plate holds, which is also how much a frozen heap may hold. */
    public int slagPlateCapacity() {
        return slag.plateCapacity();
    }

    /** The same capacity in millibuckets, which is the only way a vessel that holds a fluid can be sized. */
    public int slagPlateMb() {
        return slag.plateCapacity() * slag.mbPerFlux();
    }

    public int ingotsPerPour() {
        return casting.ingotsPerPour();
    }

    public int mbPerIngot() {
        return casting.mbPerIngot();
    }

    public int liningScrapeMb() {
        return lining.scrapeMb();
    }

    public float liningReturn() {
        return lining.returnShare();
    }

    public int fuelPerModule() {
        return charge.perModule();
    }

    /** The metal a stack of this height promises: what a charge may be worth and what the window counts. */
    public int capacity(int modules) {
        return mbPerModule() * modules;
    }

    /**
     * What the vessel really holds, which is more than the metal: gangue comes out of the rock along with the
     * melt, so a bath of 800 mB of washed-ore metal also carries a few dozen millilitres of slag. The headroom
     * is that slag's room, and it is why the number a player is shown stays the round one.
     */
    public int vesselCapacity(int modules) {
        return layerCapacity() * modules;
    }

    /**
     * The wall layer the channel sits in, counted from the sole upward. The topmost open layer of the shaft is
     * the throat and holds no metal, so with the offset at one the channel is the highest layer that does.
     */
    public int channelLayer(int modules) {
        return modules + 1 - channelOffsetFromTop();
    }

    /**
     * The least metal that will reach the channel. Slag floats to the top of the bath, so this is simply "the
     * bath has to come up into the channel's own layer": one module of furnace drains from the first millilitre,
     * five modules need 3201 of 4000, which is why height is a promise about the load rather than a free upgrade.
     */
    public int drainThreshold(int modules) {
        return mbPerModule() * channelLayer(modules) + 1;
    }

    /** How many open layers a stack of the largest legal furnace has, the throat included. */
    public int maxOpenLayers() {
        return maxModules() + 1;
    }

    /**
     * How much powder one firing is worth stocking: the dirtiest bath the furnace will call its own metal, at
     * {@code share_ceiling} of what it holds, divided by what one unit of reagent lifts. A five-layer furnace
     * of the dirtiest ore therefore wants forty powders and no more, because the forty-first could never spend
     * itself on slag that cannot exist in it.
     */
    public int reagentCap(int modules) {
        return (int) Math.ceil(capacity(modules) * slag.shareCeiling() / mbSlagPerFlux());
    }

    public int pourVolume() {
        return ingotsPerPour() * mbPerIngot();
    }

    /** What one unit of this reagent is worth of slag, so a wrong powder still works, only poorly. */
    public float mbOfSlagPer(ItemStack stack) {
        for (Reagent reagent : reagents) {
            if (reagent.ingredient().test(stack)) {
                return mbSlagPerFlux() * reagent.efficiency();
            }
        }
        return 0F;
    }

    public boolean isFuel(ItemStack stack) {
        return charge.fuel().test(stack);
    }

    /**
     * How long one piece of this fuel keeps the fire, read out of TFC's own fuel table so charcoal, coal and
     * bituminous coal each burn the time the game already says they do rather than the time this sheet happens to
     * name. The sheet's figure answers for anything TFC has no opinion about.
     */
    public int burnTicks(ItemStack stack) {
        Fuel fuel = stack.isEmpty() ? null : Fuel.get(stack);
        return fuel == null ? fire.fuelBurnTicks() : fuel.duration();
    }

    @Override
    public boolean matches(NoopInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(NoopInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public RecipeSerializer<? extends Recipe<NoopInput>> getSerializer() {
        return IMRecipes.REFINING_FURNACE_SERIALIZER.value();
    }

    @Override
    public RecipeType<? extends Recipe<NoopInput>> getType() {
        return IMRecipes.REFINING_FURNACE.value();
    }
}
