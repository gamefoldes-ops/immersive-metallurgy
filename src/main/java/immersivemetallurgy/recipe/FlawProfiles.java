package immersivemetallurgy.recipe;

import immersivemetallurgy.component.DefectProfile;
import immersivemetallurgy.component.DefectProfile.Archetype;
import immersivemetallurgy.registry.IMRecipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.dries007.tfc.common.recipes.RecipeHelpers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.material.Fluid;
import java.util.function.ToDoubleFunction;

/**
 * The tuning sheet that turns a bad melt into named defects: which metal being over or under pours which
 * kind of fault, and what each fault then costs a tool. Like the tolerance rules it ships as a recipe,
 * because the profile is computed on the client too and only recipes are both synced and reloadable.
 */
public record FlawProfiles(List<Defect> defects, List<Role> roles, float durabilityFloor, Wear wear,
                           Palette palette, Grades grades, float unmeasuredFlaw, float weldMergeDivisor)
        implements Recipe<NoopInput> {

    public static final MapCodec<FlawProfiles> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Defect.CODEC.listOf().fieldOf("defects").forGetter(FlawProfiles::defects),
            Role.CODEC.listOf().fieldOf("roles").forGetter(FlawProfiles::roles),
            Codec.FLOAT.fieldOf("durability_floor").forGetter(FlawProfiles::durabilityFloor),
            Wear.CODEC.fieldOf("wear").forGetter(FlawProfiles::wear),
            Palette.CODEC.fieldOf("palette").forGetter(FlawProfiles::palette),
            Grades.CODEC.fieldOf("grades").forGetter(FlawProfiles::grades),
            Codec.FLOAT.optionalFieldOf("unmeasured_flaw", 0.2F).forGetter(FlawProfiles::unmeasuredFlaw),
            Codec.FLOAT.optionalFieldOf("weld_merge_divisor", 1.5F).forGetter(FlawProfiles::weldMergeDivisor)
    ).apply(instance, FlawProfiles::new));

    // Past six fields StreamCodec.composite does not reach, so the whole table travels as its own codec.
    public static final StreamCodec<RegistryFriendlyByteBuf, FlawProfiles> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());

    /**
     * What a part forged out of metal we know to be flawed but cannot measure starts with: the mild band. An
     * inclusion nobody weighed is not the same finding as one measured at three parts in ten, so it is not
     * allowed to cost as much either.
     */
    public DefectProfile baselineFlaw() {
        float severity = Mth.clamp(unmeasuredFlaw, 0F, 1F);
        float durability = 1F;
        for (Map.Entry<Archetype, Defect> entry : byArchetype().entrySet()) {
            durability *= Mth.lerp(severity, 1F, entry.getValue().maxDamage());
        }
        return new DefectProfile(severity, severity, severity, severity, 0F,
                Mth.clamp(durability, durabilityFloor, 1F), grade(severity));
    }

    /**
     * The coarse band a whole pile of metal is graded by, so that a slightly wrong casting and a badly wrong
     * one can sit in different stacks: one stack cannot hold two verdicts, and a player reads a pile by shape.
     */
    public int grade(float worst) {
        if (worst >= grades.heavy()) {
            return 2;
        }
        return worst >= grades.medium() ? 1 : 0;
    }

    /**
     * @param medium the worst severity at which metal is no longer merely lightly flawed
     * @param heavy  the worst severity at which a casting counts as scrap rather than as flawed stock
     */
    public record Grades(float medium, float heavy) {

        public static final Codec<Grades> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("medium").forGetter(Grades::medium),
                Codec.FLOAT.fieldOf("heavy").forGetter(Grades::heavy)
        ).apply(instance, Grades::new));

        public static final StreamCodec<ByteBuf, Grades> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Grades::medium,
                ByteBufCodecs.FLOAT, Grades::heavy,
                Grades::new);
    }

    public static RecipeSerializer<FlawProfiles> serializer() {
        return new RecipeSerializer<>() {
            @Override
            public MapCodec<FlawProfiles> codec() {
                return CODEC;
            }

            @Override
            public StreamCodec<RegistryFriendlyByteBuf, FlawProfiles> streamCodec() {
                return STREAM_CODEC;
            }
        };
    }

    /**
     * The tuning sheet as it stands right now. Nothing is cached, because a {@code /reload} swaps recipes
     * inside the same RecipeManager instance, and one lookup per mined block is not worth a stale table.
     */
    public static FlawProfiles of(RecipeManager recipes) {
        for (RecipeHolder<FlawProfiles> holder : RecipeHelpers.getRecipes(recipes, IMRecipes.FLAW_PROFILE)) {
            return holder.value();
        }
        return null;
    }

    /**
     * @param key           which archetype this row describes
     * @param slagWeight    how strongly slag feeds it
     * @param spreadWeight  how strongly having several components out of band at once feeds it
     * @param maxDamage     what a full dose of this defect leaves of the tool's structural durability
     * @param damage        attack damage multiplier at a full dose
     * @param miningSpeed   mining speed multiplier at a full dose
     * @param dulling       how much faster an edge goes blunt at a full dose
     * @param chipChance    chance per hit to lose extra durability at a full dose
     */
    public record Defect(String key,
                         float slagWeight,
                         float spreadWeight,
                         float maxDamage,
                         float damage,
                         float miningSpeed,
                         float dulling,
                         float chipChance) {

        public static final Codec<Defect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter(Defect::key),
                Codec.FLOAT.fieldOf("slag_weight").forGetter(Defect::slagWeight),
                Codec.FLOAT.fieldOf("spread_weight").forGetter(Defect::spreadWeight),
                Codec.FLOAT.fieldOf("max_damage").forGetter(Defect::maxDamage),
                Codec.FLOAT.fieldOf("damage").forGetter(Defect::damage),
                Codec.FLOAT.fieldOf("mining_speed").forGetter(Defect::miningSpeed),
                Codec.FLOAT.fieldOf("dulling").forGetter(Defect::dulling),
                Codec.FLOAT.fieldOf("chip_chance").forGetter(Defect::chipChance)
        ).apply(instance, Defect::new));

        // Eight fields is past what StreamCodec.composite reaches, and the row is small enough to ship whole.
        public static final StreamCodec<ByteBuf, Defect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

        public Archetype archetype() {
            return Archetype.byKey(key);
        }
    }

    /**
     * What one metal does to the melt when there is too much or too little of it. This is where the
     * metallurgy lives rather than in arithmetic: tin is the hardener of bronze so over-tin is brittle and
     * under-tin is soft and an edge that will not hold, while bismuth and copper are the hot-short pair that
     * cracks steel while it is still being forged, and nickel is the toughener whose absence makes steel
     * brittle rather than soft.
     */
    public record Role(Fluid metal, Direction over, Direction under) {

        public static final Codec<Role> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("metal").forGetter(Role::metal),
                Direction.CODEC.fieldOf("over").forGetter(Role::over),
                Direction.CODEC.fieldOf("short").forGetter(Role::under)
        ).apply(instance, Role::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Role> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), Role::metal,
                Direction.STREAM_CODEC, Role::over,
                Direction.STREAM_CODEC, Role::under,
                Role::new);
    }

    /** How far one deviation pushes each of the four archetypes. Omitted keys are zero. */
    public record Direction(float brittle, float soft, float edgeWear, float flowy) {

        public static final Codec<Direction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("brittle", 0F).forGetter(Direction::brittle),
                Codec.FLOAT.optionalFieldOf("soft", 0F).forGetter(Direction::soft),
                Codec.FLOAT.optionalFieldOf("edge_wear", 0F).forGetter(Direction::edgeWear),
                Codec.FLOAT.optionalFieldOf("flowy", 0F).forGetter(Direction::flowy)
        ).apply(instance, Direction::new));

        public static final StreamCodec<ByteBuf, Direction> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

        public float of(Archetype archetype) {
            return switch (archetype) {
                case BRITTLE -> brittle;
                case SOFT -> soft;
                case EDGE_WEAR -> edgeWear;
                case FLOWY -> flowy;
            };
        }
    }

    /**
     * The numbers that are not about the melt but about use: how fast an edge goes blunt, how much a blunt
     * edge costs, and what honing gives back and takes away. The station and the portable stone share these,
     * so the two routes differ only in price: one costs time, the other costs the stone.
     *
     * @param sharpnessPerBlock     sharpness lost per block mined
     * @param sharpnessPerHit       sharpness lost per hit landed
     * @param dullDamageFloor       damage multiplier of an edge at zero sharpness
     * @param dullSpeedFloor        mining speed multiplier of an edge at zero sharpness
     * @param honingTicks           how long a session at a honing station takes
     * @param honingRestore         sharpness one honing session adds
     * @param honingCeiling         the best an edge can be brought back to by honing alone
     * @param honingStructureCost   share of the tool's max durability one session eats
     */
    public record Wear(float sharpnessPerBlock,
                       float sharpnessPerHit,
                       float dullDamageFloor,
                       float dullSpeedFloor,
                       int honingTicks,
                       float honingRestore,
                       float honingCeiling,
                       float honingStructureCost) {

        public static final Codec<Wear> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("sharpness_per_block").forGetter(Wear::sharpnessPerBlock),
                Codec.FLOAT.fieldOf("sharpness_per_hit").forGetter(Wear::sharpnessPerHit),
                Codec.FLOAT.fieldOf("dull_damage_floor").forGetter(Wear::dullDamageFloor),
                Codec.FLOAT.fieldOf("dull_speed_floor").forGetter(Wear::dullSpeedFloor),
                Codec.intRange(1, 20 * 60).fieldOf("honing_ticks").forGetter(Wear::honingTicks),
                Codec.FLOAT.fieldOf("honing_restore").forGetter(Wear::honingRestore),
                Codec.FLOAT.fieldOf("honing_ceiling").forGetter(Wear::honingCeiling),
                Codec.FLOAT.fieldOf("honing_structure_cost").forGetter(Wear::honingStructureCost)
        ).apply(instance, Wear::new));

        // Eight fields, and StreamCodec.composite reaches six anyway, so the codec carries it.
        public static final StreamCodec<ByteBuf, Wear> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    /**
     * How a melt should look. The art already carries the metal's own colour, so this only pushes the colour
     * along the melt's deviation from the alloy's own mix: {@code lean} is how far it may be pushed at all,
     * {@code hue_span} is how far the strongest channel is pulled once the lean is applied, and
     * {@code defect_darken} adds to the push as the melt gets worse overall.
     */
    public record Palette(List<MetalColor> colors, float lean, float defectDarken, float hueSpan) {

        public static final Codec<Palette> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MetalColor.CODEC.listOf().fieldOf("colors").forGetter(Palette::colors),
                Codec.FLOAT.fieldOf("lean").forGetter(Palette::lean),
                Codec.FLOAT.fieldOf("defect_darken").forGetter(Palette::defectDarken),
                Codec.FLOAT.fieldOf("hue_span").forGetter(Palette::hueSpan)
        ).apply(instance, Palette::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Palette> STREAM_CODEC = StreamCodec.composite(
                MetalColor.STREAM_CODEC.apply(ByteBufCodecs.list()), Palette::colors,
                ByteBufCodecs.FLOAT, Palette::lean,
                ByteBufCodecs.FLOAT, Palette::defectDarken,
                ByteBufCodecs.FLOAT, Palette::hueSpan,
                Palette::new);

        public Map<Fluid, Integer> byFluid() {
            Map<Fluid, Integer> byFluid = new HashMap<>();
            for (MetalColor color : colors()) {
                byFluid.put(color.fluid(), color.color());
            }
            return byFluid;
        }
    }

    public record MetalColor(Fluid fluid, int color) {

        public static final Codec<MetalColor> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(MetalColor::fluid),
                Codec.INT.fieldOf("color").forGetter(MetalColor::color)
        ).apply(instance, MetalColor::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MetalColor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.registry(Registries.FLUID), MetalColor::fluid,
                ByteBufCodecs.VAR_INT, MetalColor::color,
                MetalColor::new);
    }

    public Map<Archetype, Defect> byArchetype() {
        Map<Archetype, Defect> byKey = new EnumMap<>(Archetype.class);
        for (Defect defect : defects) {
            Archetype archetype = defect.archetype();
            if (archetype != null) {
                byKey.put(archetype, defect);
            }
        }
        return byKey;
    }

    /**
     * Severity of one archetype: every metal that sits outside its band contributes the weight its role row
     * gives it in that direction, and slag and spread sit on top. Clamped, so a wildly wrong melt cannot
     * score above 1.
     *
     * @param excess  how far past its tolerance each metal was poured, 0 to 1
     * @param deficit how far short of its tolerance each metal fell, 0 to 1
     */
    public float severity(Archetype archetype, Map<Fluid, Double> excess, Map<Fluid, Double> deficit,
                          float slagSeverity, float spread) {
        Defect defect = byArchetype().get(archetype);
        if (defect == null) {
            return 0;
        }
        float sum = defect.slagWeight() * slagSeverity + defect.spreadWeight() * spread;
        for (Role role : roles) {
            sum += role.over().of(archetype) * excess.getOrDefault(role.metal(), 0D);
            sum += role.under().of(archetype) * deficit.getOrDefault(role.metal(), 0D);
        }
        return Mth.clamp(sum, 0F, 1F);
    }

    /**
     * One stat out of the profile: every archetype that is present pulls the multiplier away from 1, and
     * they stack, so metal that is both brittle and soft pays for both.
     */
    public float factor(DefectProfile profile, ToDoubleFunction<Defect> byDefect) {
        float result = 1F;
        Map<Archetype, Defect> defects = byArchetype();
        for (Archetype archetype : Archetype.values()) {
            Defect defect = defects.get(archetype);
            if (defect != null) {
                result *= Mth.lerp(profile.severity(archetype), 1F, (float) byDefect.applyAsDouble(defect));
            }
        }
        return result;
    }

    public float damageFactor(DefectProfile profile) {
        return factor(profile, Defect::damage);
    }

    public float speedFactor(DefectProfile profile) {
        return factor(profile, Defect::miningSpeed);
    }

    public float dullFactor(DefectProfile profile) {
        return factor(profile, Defect::dulling);
    }

    /** Chance per strike to lose extra durability, because a chipped edge is the same defect seen from the other side. */
    public float chipChance(DefectProfile profile) {
        float chance = 0;
        Map<Archetype, Defect> defects = byArchetype();
        for (Archetype archetype : Archetype.values()) {
            Defect defect = defects.get(archetype);
            if (defect != null) {
                chance += profile.severity(archetype) * defect.chipChance();
            }
        }
        return Mth.clamp(chance, 0F, 1F);
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
        return IMRecipes.FLAW_PROFILE_SERIALIZER.value();
    }

    @Override
    public RecipeType<? extends Recipe<NoopInput>> getType() {
        return IMRecipes.FLAW_PROFILE.value();
    }
}
