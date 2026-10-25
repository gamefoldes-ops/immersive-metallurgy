package com.gamefoldes.immersivemetallurgy.recipe;

import com.gamefoldes.immersivemetallurgy.component.DefectProfile.Archetype;
import com.gamefoldes.immersivemetallurgy.registry.IMRecipes;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The tuning sheet that turns a bad melt into named defects: how much each kind of deviation feeds each
 * archetype, and what an archetype then costs a tool. Like the tolerance rules it ships as a recipe,
 * because the profile is computed on the client too and only recipes are both synced and reloadable.
 */
public record FlawProfiles(List<Defect> defects, float durabilityFloor) implements Recipe<NoopInput> {

    public static final MapCodec<FlawProfiles> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Defect.CODEC.listOf().fieldOf("defects").forGetter(FlawProfiles::defects),
            com.mojang.serialization.Codec.FLOAT.fieldOf("durability_floor").forGetter(FlawProfiles::durabilityFloor)
    ).apply(instance, FlawProfiles::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlawProfiles> STREAM_CODEC = StreamCodec.composite(
            Defect.STREAM_CODEC.apply(ByteBufCodecs.list()), FlawProfiles::defects,
            ByteBufCodecs.FLOAT, FlawProfiles::durabilityFloor,
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

        public static final com.mojang.serialization.Codec<Defect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                com.mojang.serialization.Codec.STRING.fieldOf("key").forGetter(Defect::key),
                com.mojang.serialization.Codec.FLOAT.fieldOf("short_weight").forGetter(Defect::shortWeight),
                com.mojang.serialization.Codec.FLOAT.fieldOf("over_weight").forGetter(Defect::overWeight),
                com.mojang.serialization.Codec.FLOAT.fieldOf("slag_weight").forGetter(Defect::slagWeight),
                com.mojang.serialization.Codec.FLOAT.fieldOf("spread_weight").forGetter(Defect::spreadWeight),
                com.mojang.serialization.Codec.FLOAT.fieldOf("max_damage").forGetter(Defect::maxDamage),
                com.mojang.serialization.Codec.FLOAT.fieldOf("damage").forGetter(Defect::damage),
                com.mojang.serialization.Codec.FLOAT.fieldOf("mining_speed").forGetter(Defect::miningSpeed),
                com.mojang.serialization.Codec.FLOAT.fieldOf("dulling").forGetter(Defect::dulling),
                com.mojang.serialization.Codec.FLOAT.fieldOf("chip_chance").forGetter(Defect::chipChance)
        ).apply(instance, Defect::new));

        // StreamCodec.composite only reaches six fields, and this row has ten.
        public static final StreamCodec<ByteBuf, Defect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

        public Archetype archetype() {
            return Archetype.byKey(key);
        }
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
