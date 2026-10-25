package com.gamefoldes.immersivemetallurgy.recipe;

import com.gamefoldes.immersivemetallurgy.component.DefectProfile;
import com.gamefoldes.immersivemetallurgy.component.DefectProfile.Archetype;
import com.gamefoldes.immersivemetallurgy.registry.IMRecipes;

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
 * The tuning sheet that turns a bad melt into named defects: how much each kind of deviation feeds each
 * archetype, and what an archetype then costs a tool. Like the tolerance rules it ships as a recipe,
 * because the profile is computed on the client too and only recipes are both synced and reloadable.
 */
public record FlawProfiles(List<Defect> defects, float durabilityFloor, Wear wear, Palette palette) implements Recipe<NoopInput> {

    public static final MapCodec<FlawProfiles> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Defect.CODEC.listOf().fieldOf("defects").forGetter(FlawProfiles::defects),
            Codec.FLOAT.fieldOf("durability_floor").forGetter(FlawProfiles::durabilityFloor),
            Wear.CODEC.fieldOf("wear").forGetter(FlawProfiles::wear),
            Palette.CODEC.fieldOf("palette").forGetter(FlawProfiles::palette)
    ).apply(instance, FlawProfiles::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlawProfiles> STREAM_CODEC = StreamCodec.composite(
            Defect.STREAM_CODEC.apply(ByteBufCodecs.list()), FlawProfiles::defects,
            ByteBufCodecs.FLOAT, FlawProfiles::durabilityFloor,
            Wear.STREAM_CODEC, FlawProfiles::wear,
            Palette.STREAM_CODEC, FlawProfiles::palette,
            FlawProfiles::new);

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
     * @param shortWeight   how strongly a component missing its band feeds it
     * @param overWeight    how strongly a component exceeding its band feeds it
     * @param slagWeight    how strongly slag feeds it
     * @param spreadWeight  how strongly having several components out of band at once feeds it
     * @param maxDamage     what a full dose of this defect leaves of the tool's structural durability
     * @param damage        attack damage multiplier at a full dose
     * @param miningSpeed   mining speed multiplier at a full dose
     * @param dulling       how much faster an edge goes blunt at a full dose
     * @param chipChance    chance per hit to lose extra durability at a full dose
     */
    public record Defect(String key,
                         float shortWeight,
                         float overWeight,
                         float slagWeight,
                         float spreadWeight,
                         float maxDamage,
                         float damage,
                         float miningSpeed,
                         float dulling,
                         float chipChance) {

        public static final Codec<Defect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("key").forGetter(Defect::key),
                Codec.FLOAT.fieldOf("short_weight").forGetter(Defect::shortWeight),
                Codec.FLOAT.fieldOf("over_weight").forGetter(Defect::overWeight),
                Codec.FLOAT.fieldOf("slag_weight").forGetter(Defect::slagWeight),
                Codec.FLOAT.fieldOf("spread_weight").forGetter(Defect::spreadWeight),
                Codec.FLOAT.fieldOf("max_damage").forGetter(Defect::maxDamage),
                Codec.FLOAT.fieldOf("damage").forGetter(Defect::damage),
                Codec.FLOAT.fieldOf("mining_speed").forGetter(Defect::miningSpeed),
                Codec.FLOAT.fieldOf("dulling").forGetter(Defect::dulling),
                Codec.FLOAT.fieldOf("chip_chance").forGetter(Defect::chipChance)
        ).apply(instance, Defect::new));

        // StreamCodec.composite only reaches six fields, and this row has ten.
        public static final StreamCodec<ByteBuf, Defect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

        public Archetype archetype() {
            return Archetype.byKey(key);
        }
    }

    /**
     * The numbers that are not about the melt but about use: how fast an edge goes blunt, how much a blunt
     * edge costs, and what a whetstone gives back and takes away.
     *
     * @param sharpnessPerBlock     sharpness lost per block mined
     * @param sharpnessPerHit       sharpness lost per hit landed
     * @param dullDamageFloor       damage multiplier of an edge at zero sharpness
     * @param dullSpeedFloor        mining speed multiplier of an edge at zero sharpness
     * @param whetstoneRestore      sharpness one whetstone stroke adds
     * @param whetstoneCeiling      the best a whetstone can leave an edge at
     * @param whetstoneStructureCost share of the tool's max durability one stroke eats
     */
    public record Wear(float sharpnessPerBlock,
                       float sharpnessPerHit,
                       float dullDamageFloor,
                       float dullSpeedFloor,
                       float whetstoneRestore,
                       float whetstoneCeiling,
                       float whetstoneStructureCost) {

        public static final Codec<Wear> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("sharpness_per_block").forGetter(Wear::sharpnessPerBlock),
                Codec.FLOAT.fieldOf("sharpness_per_hit").forGetter(Wear::sharpnessPerHit),
                Codec.FLOAT.fieldOf("dull_damage_floor").forGetter(Wear::dullDamageFloor),
                Codec.FLOAT.fieldOf("dull_speed_floor").forGetter(Wear::dullSpeedFloor),
                Codec.FLOAT.fieldOf("whetstone_restore").forGetter(Wear::whetstoneRestore),
                Codec.FLOAT.fieldOf("whetstone_ceiling").forGetter(Wear::whetstoneCeiling),
                Codec.FLOAT.fieldOf("whetstone_structure_cost").forGetter(Wear::whetstoneStructureCost)
        ).apply(instance, Wear::new));

        // Seven fields is one more than StreamCodec.composite reaches.
        public static final StreamCodec<ByteBuf, Wear> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
    }

    /**
     * What a melt should look like. A flawed object is drawn from a neutral grey base and multiplied by
     * this, so the colour it leans to is whichever metal its composition has most of: a tin-heavy bronze
     * reads pale and cold, a copper-heavy one reads reddish. {@code lean} is how far from untouched the
     * colour goes at a full imbalance, and {@code defectDarken} dulls whatever is left as the melt gets
     * worse overall.
     */
    public record Palette(List<MetalColor> colors, float lean, float defectDarken) {

        public static final Codec<Palette> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MetalColor.CODEC.listOf().fieldOf("colors").forGetter(Palette::colors),
                Codec.FLOAT.fieldOf("lean").forGetter(Palette::lean),
                Codec.FLOAT.fieldOf("defectDarken").forGetter(Palette::defectDarken)
        ).apply(instance, Palette::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Palette> STREAM_CODEC = StreamCodec.composite(
                MetalColor.STREAM_CODEC.apply(ByteBufCodecs.list()), Palette::colors,
                ByteBufCodecs.FLOAT, Palette::lean,
                ByteBufCodecs.FLOAT, Palette::defectDarken,
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

    /** Severity of one archetype, clamped so that a wildly wrong melt cannot score above 1. */
    public float severity(Archetype archetype, float shortSum, float overSum, float slagSeverity, float spread) {
        Defect defect = byArchetype().get(archetype);
        if (defect == null) {
            return 0;
        }
        return Mth.clamp(defect.shortWeight() * shortSum
                + defect.overWeight() * overSum
                + defect.slagWeight() * slagSeverity
                + defect.spreadWeight() * spread, 0F, 1F);
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
