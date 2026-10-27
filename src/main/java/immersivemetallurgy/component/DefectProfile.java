package immersivemetallurgy.component;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import io.netty.buffer.ByteBuf;

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
 * {@code grade} is that file's coarse verdict on the worst severity — light, medium or heavy — and it is
 * stored rather than recomputed because a pile of ingots has to agree on one grade, and the code that
 * compares two stacks is static and has no recipes to ask.
 */
public record DefectProfile(float brittle, float soft, float edgeWear, float flowy, float slagShare,
                            float durabilityFactor, int grade) {

    public static final int GRADE_COUNT = 3;
    private static final String[] GRADE_KEYS = {"light", "medium", "heavy"};

    public static final Codec<DefectProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("brittle").forGetter(DefectProfile::brittle),
            Codec.FLOAT.fieldOf("soft").forGetter(DefectProfile::soft),
            Codec.FLOAT.fieldOf("edge_wear").forGetter(DefectProfile::edgeWear),
            Codec.FLOAT.fieldOf("flowy").forGetter(DefectProfile::flowy),
            Codec.FLOAT.fieldOf("slag_share").forGetter(DefectProfile::slagShare),
            Codec.FLOAT.fieldOf("durability_factor").forGetter(DefectProfile::durabilityFactor),
            Codec.intRange(0, GRADE_COUNT - 1).optionalFieldOf("grade", 0).forGetter(DefectProfile::grade)
    ).apply(instance, DefectProfile::new));

    // Seven components, one past what StreamCodec.composite reaches.
    public static final StreamCodec<ByteBuf, DefectProfile> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public static final DefectProfile CLEAN = new DefectProfile(0, 0, 0, 0, 0, 1, 0);

    /**
     * Severity is a quotient of melt proportions, so each millilitre poured shifts it in the last bits.
     * A mold only merges a new drip when its fluid stack is component-equal to the one inside, so the
     * floats have to be coarse enough that two drips of the same melt agree.
     */
    public DefectProfile {
        brittle = quantize(brittle);
        soft = quantize(soft);
        edgeWear = quantize(edgeWear);
        flowy = quantize(flowy);
        slagShare = quantize(slagShare);
        durabilityFactor = quantize(durabilityFactor);
    }

    private static float quantize(float value) {
        return Math.round(value * 250F) / 250F;
    }

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

    /** How wrong the metal is overall, regardless of in which direction. */
    public float worst() {
        return Math.max(brittle, Math.max(soft, Math.max(edgeWear, flowy)));
    }

    /**
     * Two melts that reached the same bath are wrong together the way the worse of them was, and a joint is
     * weak wherever either half was. The cheaper severities are thrown away rather than averaged, because
     * averaging would let a bad pour be made look better by diluting it with a good one.
     */
    public DefectProfile worseThan(DefectProfile other) {
        if (other == null) {
            return this;
        }
        return new DefectProfile(Math.max(brittle, other.brittle), Math.max(soft, other.soft),
                Math.max(edgeWear, other.edgeWear), Math.max(flowy, other.flowy),
                Math.max(slagShare, other.slagShare), Math.min(durabilityFactor, other.durabilityFactor),
                Math.max(grade, other.grade));
    }

    public String gradeKey() {
        return GRADE_KEYS[Math.clamp(grade, 0, GRADE_COUNT - 1)];
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
