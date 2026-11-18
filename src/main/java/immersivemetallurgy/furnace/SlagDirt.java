package immersivemetallurgy.furnace;

/**
 * Slag baked onto a conduit or a crucible, added by mixin to TFC's own block entities so the count lives in their
 * saved tag and travels on their item. A channel that has taken {@link #CLOG_AT} runs is clogged for good: it
 * passes nothing and can only be thrown away, which is the price of running slag somewhere other than the plate.
 */
public interface SlagDirt {
    int CLOG_AT = 3;

    int slagDirt();

    void slagDirt(int dirt);

    default boolean clogged() {
        return slagDirt() >= CLOG_AT;
    }

    default void addSlagDirt() {
        slagDirt(Math.min(CLOG_AT, slagDirt() + 1));
    }
}