package immersivemetallurgy.menu;

import immersivemetallurgy.block.entity.RefiningFurnaceBlockEntity;
import immersivemetallurgy.recipe.RefiningFurnaceStats;
import immersivemetallurgy.registry.IMMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The numbers a furnace is willing to tell: how full the bath is, how much of it is dirt, how much reagent is left
 * in the fire, how thick the lining has grown, what phase the firing is in and how long is left on it, how high the
 * stack was built and where its channel sits, and what stands — or fails to stand — at each of its two outlets.
 *
 * What is deliberately missing is any name. The bath may be bronze or steel or a mix nobody can call anything,
 * and as long as that could be read out of the window the GUI would be a mixing table, which is what the
 * crucible is for and what this furnace is not.
 */
public class RefiningFurnaceMenu extends AbstractContainerMenu {
    public static final int VOLUME = 0;
    public static final int CAPACITY = 1;
    public static final int SLAG_PERCENT = 2;
    public static final int REAGENT_UNITS = 3;
    public static final int LINING = 4;
    public static final int PHASE = 5;
    public static final int TICKS_LEFT = 6;
    public static final int SLAG_OUTLET_UNITS = 7;
    public static final int MODULES = 8;
    public static final int DRAIN_THRESHOLD = 9;
    public static final int SLAG_CAPACITY = 10;
    public static final int FAULT = 11;
    public static final int TEMPERATURE = 12;
    public static final int CHARGE_LEFT = 13;
    public static final int SLAG_MB = 14;
    public static final int POUR_MB = 15;
    public static final int METAL_MB = 16;
    public static final int MOLD_IN_TAP = 17;
    public static final int SLAG_OUTLET_FROZEN = 18;
    public static final int TAP_LEFT = 19;
    public static final int SLAG_OUTLET_BLOCKED = 20;
    public static final int BATH_FLUID = 21;
    public static final int POUR_HEAT = 22;
    public static final int FIRE_CEILING = 23;
    public static final int FIRE_SECONDS = 24;

    private static final int ENTRIES = 25;

    private final ContainerData data;
    private final BlockPos hearth;

    /** The client's copy: it knows nothing but the numbers the server chose to send. */
    public RefiningFurnaceMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainerData(ENTRIES), BlockPos.ZERO);
    }

    public RefiningFurnaceMenu(int id, Inventory inventory, RefiningFurnaceBlockEntity furnace) {
        this(id, inventory, new ContainerData() {
            @Override
            public int getCount() {
                return ENTRIES;
            }

            @Override
            public int get(int index) {
                RefiningFurnaceStats stats = RefiningFurnaceStats.of(furnace.getLevel().getRecipeManager());
                return switch (index) {
                    case VOLUME -> furnace.volume();
                    case CAPACITY -> furnace.vesselCapacity();
                    case SLAG_PERCENT -> furnace.slagSharePercent();
                    case REAGENT_UNITS -> stats == null ? 0 : furnace.reagentUnits(stats);
                    case LINING -> furnace.lining();
                    case PHASE -> furnace.phase().ordinal();
                    case TICKS_LEFT -> furnace.ticksLeft();
                    case SLAG_OUTLET_UNITS -> furnace.slagOutletUnits(stats);
                    case MODULES -> furnace.shape() == null ? 0 : furnace.shape().modules();
                    case DRAIN_THRESHOLD -> stats == null || furnace.shape() == null
                            ? 0
                            : stats.drainThreshold(furnace.shape().modules());
                    case SLAG_CAPACITY -> stats == null ? 0 : stats.slagPlateCapacity();
                    case FAULT -> furnace.faultOrdinal();
                    case TEMPERATURE -> furnace.temperature();
                    case CHARGE_LEFT -> furnace.chargeLeft();
                    case SLAG_MB -> furnace.slagMb();
                    case POUR_MB -> stats == null ? 0 : stats.pourVolume();
                    case METAL_MB -> furnace.metalInBath();
                    case MOLD_IN_TAP -> furnace.tapHasMold() ? 1 : 0;
                    case SLAG_OUTLET_FROZEN -> furnace.slagOutletFrozen() ? 1 : 0;
                    case TAP_LEFT -> furnace.tapRemaining();
                    case SLAG_OUTLET_BLOCKED -> furnace.slagOutletBlocked() ? 1 : 0;
                    case BATH_FLUID -> furnace.bathFluidId();
                    case POUR_HEAT -> stats == null ? 0 : Math.round(stats.pourTemperature());
                    case FIRE_CEILING -> stats == null || furnace.shape() == null
                            ? 0
                            : Math.round(stats.fireTemperature(furnace.shape().layers()));
                    case FIRE_SECONDS -> stats == null
                            ? 0
                            : Math.min(Short.MAX_VALUE, furnace.fireSecondsLeft(stats));
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
            }
        }, furnace.getBlockPos());
    }

    private RefiningFurnaceMenu(int id, Inventory inventory, ContainerData data, BlockPos hearth) {
        super(IMMenus.REFINING_FURNACE.get(), id);
        this.data = data;
        this.hearth = hearth;
        addDataSlots(data);
    }

    public int volume() {
        return data.get(VOLUME);
    }

    public int capacity() {
        return data.get(CAPACITY);
    }

    public int slagPercent() {
        return data.get(SLAG_PERCENT);
    }

    public int reagentUnits() {
        return data.get(REAGENT_UNITS);
    }

    public int lining() {
        return data.get(LINING);
    }

    public int phase() {
        return data.get(PHASE);
    }

    public int ticksLeft() {
        return data.get(TICKS_LEFT);
    }

    public int slagOutletUnits() {
        return data.get(SLAG_OUTLET_UNITS);
    }

    /** Whether the slag outlet has frozen over, which is the readout that says bring a pickaxe. */
    public boolean slagOutletFrozen() {
        return data.get(SLAG_OUTLET_FROZEN) != 0;
    }

    /** Whether the slag can leave the bath at all right now, which is the readout that says why it cannot. */
    public boolean slagOutletBlocked() {
        return data.get(SLAG_OUTLET_BLOCKED) != 0;
    }

    /** The metal standing in the bath, as a registry id, because a tank is drawn in the sprite of what fills it. */
    public int bathFluidId() {
        return data.get(BATH_FLUID);
    }

    /** How hot the bath must be before it will run out of the tap, and how hot this chimney can ever get. */
    public int pourHeat() {
        return data.get(POUR_HEAT);
    }

    public int fireCeiling() {
        return data.get(FIRE_CEILING);
    }

    /** How many seconds of fuel this fire still has in front of it. */
    public int fireSeconds() {
        return data.get(FIRE_SECONDS);
    }

    /** How much of an open tap has not reached the mould yet, which is the number that says do not click again. */
    public int tapRemaining() {
        return data.get(TAP_LEFT);
    }

    /** Whether a mold is standing on the table the tap runs into, which is what makes a pour possible at all. */
    public boolean hasMold() {
        return data.get(MOLD_IN_TAP) != 0;
    }

    public int modules() {
        return data.get(MODULES);
    }

    /** Where the top of the bath has to come up to before its slag can leave by itself. */
    public int drainThreshold() {
        return data.get(DRAIN_THRESHOLD);
    }

    /** How many units of dust the outlet will hold — in the plate, or frozen where the plate should be. */
    public int slagCapacity() {
        return data.get(SLAG_CAPACITY);
    }

    /** What the hearth found missing, which is the only way an unformed stack explains itself. */
    public int fault() {
        return data.get(FAULT);
    }

    /** What the fire and the bath stand at, in the same degrees TFC's recipes answer melt questions in. */
    public int temperature() {
        return data.get(TEMPERATURE);
    }

    /** How many pieces of the charge are still waiting to melt. */
    public int chargeLeft() {
        return data.get(CHARGE_LEFT);
    }

    /** The volume of slag the bath is carrying, which is why it stands larger than the metal it promises. */
    public int slagMb() {
        return data.get(SLAG_MB);
    }

    /** How much metal one tap is allowed to take — the number the tuning sheet is built on. */
    public int pourMb() {
        return data.get(POUR_MB);
    }

    /** The metal in the bath, without the gangue riding in it. */
    public int metalMb() {
        return data.get(METAL_MB);
    }

    public boolean formed() {
        return modules() > 0;
    }

    /** Nothing in this window can be shift-clicked, because nothing in it is an inventory. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        Level level = player.level();
        return level.getBlockEntity(hearth) instanceof RefiningFurnaceBlockEntity;
    }
}
