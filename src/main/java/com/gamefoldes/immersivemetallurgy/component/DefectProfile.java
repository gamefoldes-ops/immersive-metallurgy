package com.gamefoldes.immersivemetallurgy.component;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * How bad each kind of defect is in one piece of metal, from 0 to 1, computed once when the melt leaves the
 * crucible and then carried by everything made from it. The four kinds are the deficit archetypes: too much
 * of the hardener makes metal brittle, too little makes it soft, inclusions wear an edge, and a melt that is
 * off in several elements at once does not pour well.
 *
 * {@code durabilityFactor} is worked out at the same moment, from the tuning file, and travels with the
 * severities because the places where an item is created have no recipe access to look it up again.
 */
public record DefectProfile(float brittle, float soft, float edgeWear, float flowy, float slagShare, float durabilityFactor) {

    public static final Codec<DefectProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("brittle").forGetter(DefectProfile::brittle),
            Codec.FLOAT.fieldOf("soft").forGetter(DefectProfile::soft),
            Codec.FLOAT.fieldOf("edge_wear").forGetter(DefectProfile::edgeWear),
            Codec.FLOAT.fieldOf("flowy").forGetter(DefectProfile::flowy),
            Codec.FLOAT.fieldOf("slag_share").forGetter(DefectProfile::slagShare),
            Codec.FLOAT.fieldOf("durability_factor").forGetter(DefectProfile::durabilityFactor)
    ).apply(instance, DefectProfile::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DefectProfile> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DefectProfile::brittle,
            ByteBufCodecs.FLOAT, DefectProfile::soft,
            ByteBufCodecs.FLOAT, DefectProfile::edgeWear,
            ByteBufCodecs.FLOAT, DefectProfile::flowy,
            ByteBufCodecs.FLOAT, DefectProfile::slagShare,
            ByteBufCodecs.FLOAT, DefectProfile::durabilityFactor,
            DefectProfile::new);

    public static final DefectProfile CLEAN = new DefectProfile(0, 0, 0, 0, 0, 1);

    public boolean isClean() {
        return brittle <= 0 && soft <= 0 && edgeWear <= 0 && flowy <= 0;
    }

    public float severity(Archetype archetype) {
        return switch (archetype) {
            case BRITTLE -> brittle;
            case SOFT -> soft;
            case EDGE_WEAR -> edgeWear;
            case FLOWY -> flowy;
        };
    }

    /** The defect a player would name the tool by: the worst one it has. */
    public Archetype dominant() {
        Archetype worst = Archetype.BRITTLE;
        float best = -1;
        for (Archetype archetype : Archetype.values()) {
            float value = severity(archetype);
            if (value > best) {
                best = value;
                worst = archetype;
            }
        }
        return worst;
    }

    public enum Archetype {
        BRITTLE("brittle"),
        SOFT("soft"),
        EDGE_WEAR("edge_wear"),
        FLOWY("flowy");

        private final String key;

        Archetype(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        public static Archetype byKey(String key) {
            for (Archetype archetype : values()) {
                if (archetype.key.equals(key)) {
                    return archetype;
                }
            }
            return null;
        }
    }
}
