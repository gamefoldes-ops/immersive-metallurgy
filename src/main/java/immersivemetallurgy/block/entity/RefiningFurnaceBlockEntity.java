package immersivemetallurgy.block.entity;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.component.AlloyRecord;
import immersivemetallurgy.block.RefiningHearthBlock;
import immersivemetallurgy.component.MeltComposition;
import immersivemetallurgy.component.MeltCarries;
import immersivemetallurgy.furnace.Casts;
import immersivemetallurgy.furnace.FurnaceShape;
import immersivemetallurgy.menu.RefiningFurnaceMenu;
import immersivemetallurgy.recipe.RefiningFurnaceStats;
import immersivemetallurgy.registry.IMBlockEntities;
import immersivemetallurgy.registry.IMBlocks;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMItems;

import net.dries007.tfc.common.blockentities.ChannelBlockEntity;
import net.dries007.tfc.common.blockentities.MoldTableBlockEntity;
import net.dries007.tfc.common.blocks.TFCBlocks;
import net.dries007.tfc.common.component.fluid.FluidContainerInfo;
import net.dries007.tfc.common.component.heat.HeatCapability;
import net.dries007.tfc.common.component.mold.IMold;
import net.dries007.tfc.common.recipes.HeatingRecipe;
import net.dries007.tfc.util.FluidAlloy;
import net.dries007.tfc.util.calendar.Calendars;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * The furnace's whole state, and the only block of the build that holds anything: the bath is molten and lives
 * here, the charge, the fuel and the reagent are thrown in here, and the slag that had nowhere to go is left on
 * the walls here.
 *
 * A firing is three things in order. The fire heats the charge, and nothing is timed while it does: a piece runs
 * into the bath the moment it reaches the temperature its own heating recipe asks for. Then the bath boils for
 * {@code boil_ticks}, which is the only while the channel lets slag past and the only while reagent is spent.
 * Then the metal may be drawn at any moment it stands liquid enough, and that pour runs until the mould is full
 * or the bath is. When the fire dies the bath sets, and whatever slag it was still holding plates onto the walls
 * — that is the lining, and it is the whole price of filling a tall furnace with a small load: the dirt does not
 * leave, it just moves from the metal to the brick, and comes back into the next heat.
 *
 * The furnace owns no output block, and that is the design. Metal leaves through TFC's own channel into TFC's own
 * mold table, and the cast is finished by TFC's casting recipe; slag leaves into the plate standing on the table
 * at the other outlet, or freezes in the open ground where nothing stands, and is broken out with a pickaxe.
 */
public class RefiningFurnaceBlockEntity extends BlockEntity implements net.minecraft.world.MenuProvider {
    public enum Phase { IDLE, HEATING, BOILING, COOLING }

    /** What the furnace wants out of a click, and the reason it said no. */
    public enum Refusal { NONE, UNFORMED, SETTING, FUEL_FULL, POWDER_FULL, NOT_A_CHARGE }

    /** How many of the held item went in, and what stopped it when nothing did. */
    public record Loaded(int took, Refusal refusal) {
    }

    /** Nine slots apiece, because a charge is thrown in by hand and a stack of sixty-four must fit in one click. */
    public static final int SLOTS = 9;

    /** What a fire falls back toward with no fuel left, in the same degrees as everything else in here. */
    private static final float AMBIENT = 20F;

    /**
     * TFC's own pouring rate, which is what its crucible drips into a mold and what a channel carries: one
     * millilitre every other tick. The furnace keeps that rather than inventing a faster one, so a tap and a
     * crucible pour the same metal at the same speed and a bath of twelve hours is worth the wait it costs.
     */
    /**
     * Two millilitres a tick. TFC's crucible drips one every other tick and the furnace deliberately does not
     * keep that pace: a module of metal is eight hundred millilitres, and at the crucible's rate a single tap
     * costs a minute and a half of standing in front of the machine — which is a cost the shaft furnace was never
     * meant to add, its whole bargain being volume for time already spent elsewhere.
     */
    private static final int TAP_MB_PER_TICK = 2;

    /** How far a channel run may travel from the tap and still be one line this furnace will feed. */
    private static final int MAX_TAP_RUN = 8;

    /**
     * One TFC day of catch-up (28800 player ticks, its default day), which is more than any sleep or
     * {@code /time add} a player has a reason to type. The cap is there so one mistyped command cannot hold the
     * server tick for a minute.
     */
    private static final long MAX_CATCHUP_TICKS = 28_800;

    private static final String BATH_KEY = "immersive_metallurgy:bath";
    private static final String CHARGE_KEY = "immersive_metallurgy:charge";
    private static final String FUEL_KEY = "immersive_metallurgy:fuel";
    private static final String REAGENT_KEY = "immersive_metallurgy:reagent";

    private final NonNullList<ItemStack> charge = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> fuel = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> reagent = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final FluidAlloy bath = FluidAlloy.empty();
    private final Container container = new Container();

    private Phase phase = Phase.IDLE;
    private int timer;
    private int lining;
    private double reagentCredit;
    private int slagWaiting;
    /** The slag lifted out of the bath since the metal hanging in it was last paid for. */
    private float slagDragged;
    /**
     * Slag that left the bath and froze in the outlet cell because nothing stood there to catch it, and one dust's
     * worth of it kept as the pattern for what comes back out, so a heap of bronze slag breaks into bronze dust
     * even after the bath it came from has been tapped dry.
     */
    private int slagPiled;
    private ItemStack heapSeed = ItemStack.EMPTY;
    /**
     * What an open tap still owes the table at the end of its channel run, and which side of that table it is
     * arriving at. This is the only flow the furnace starts itself, and it dies the moment the mould will take no
     * more, so a forgotten tap cannot drain a bath onto the floor.
     */
    private int pourLeft;
    private BlockPos pourTable = BlockPos.ZERO;
    private Direction pourSide = Direction.NORTH;
    private FurnaceShape shape;
    private FurnaceShape.Fault fault = FurnaceShape.Fault.NONE;
    private int rescanCooldown;
    private int pickupCooldown;
    /**
     * What the fire and everything in it stand at, in the same degrees TFC's own heating recipes answer melt
     * questions in, so an item in this furnace warms by TFC's rules rather than by a timer of ours.
     */
    private float temperature;
    private int fuelTicks;
    /** Whether a shovel has dropped this fire, which holds the fuel off until the hearth is lit again. */
    private boolean doused;
    /**
     * The metal that stood above the hearth and went over the dam, kept here in millilitres and by the
     * fluid it was when it left, because what runs off a cupola's notch is the bath's own mix and not
     * the metal alone. It is collected as skimmings at the slag table, the same gesture that pays out
     * the dust, and it goes back into the throat rather than being thrown away.
     */
    private ItemStack skimming = ItemStack.EMPTY;
    /**
     * Where this fire last stood on the two clocks the world keeps: TFC's calendar, which is what a night's sleep
     * and {@code /tfc time add} advance, and the world's day time, which is what vanilla {@code /time add} moves
     * before TFC writes it back. The firing is measured against those rather than against a count of its own, the
     * way TFC measures its charcoal piles and composters — otherwise a skipped night leaves a fire that has been
     * "warming" for six hours and burned nothing.
     */
    private long lastCalendarTick = -1;
    private long lastDayTime;

    public RefiningFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(IMBlockEntities.HEARTH.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RefiningFurnaceBlockEntity self) {
        RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
        if (stats == null) {
            ImmersiveMetallurgy.LOGGER.warn("The refining furnace has no tuning sheet: /reload it, or the fire "
                    + "will not be lit and nothing will be taken as a charge");
            return;
        }
        if (self.rescanCooldown-- <= 0) {
            self.rescanCooldown = 40;
            self.scan(level, state);
            if (self.shape == null && self.phase != Phase.IDLE) {
                // The stack came apart while the fire was in it. Nothing is rescued: the metal stays in the
                // ruins and whatever slag was floating in it goes onto what is left of the walls.
                self.abort();
            }
        }
        if (self.pickupCooldown-- <= 0) {
            self.pickupCooldown = 5;
            self.takeThrownIn(stats);
        }
        self.tick(stats);
    }

    /**
     * Whatever is dropped into the open throat is a charge, so the shaft takes it out of the air: fuel, reagent
     * or meltable metal, sorted exactly as a right-click would have sorted it. What the furnace does not want
     * keeps falling rather than being quietly eaten — a stone thrown in stays a stone.
     */
    private void takeThrownIn(RefiningFurnaceStats stats) {
        if (shape == null || level == null || phase == Phase.COOLING) {
            return;
        }
        BlockPos low = shape.center().above(1);
        BlockPos high = shape.center().above(shape.layers());
        AABB shaft = new AABB(low.getX(), low.getY(), low.getZ(), high.getX() + 1, high.getY() + 1, high.getZ() + 1);
        for (ItemEntity thrown : level.getEntitiesOfClass(ItemEntity.class, shaft)) {
            ItemStack held = thrown.getItem();
            if (held.isEmpty()) {
                continue;
            }
            boolean metal = !stats.isFuel(held) && stats.mbOfSlagPer(held) <= 0;
            if (load(held, stats).took() > 0) {
                if (held.isEmpty()) {
                    thrown.discard();
                }
                // New charge in a bath that was already boiling: it has to melt, and the dirt it brought has to
                // be boiled off from here, so the heat goes back to melting rather than finishing a boil that no
                // longer describes what is in the vessel.
                if (metal && phase == Phase.BOILING) {
                    phase = Phase.HEATING;
                    timer = 0;
                    setChanged();
                }
            }
        }
    }

    /** Puts the fire out at once, for a furnace that stopped being a furnace. */
    private void abort() {
        pourLeft = 0;
        phase = Phase.IDLE;
        timer = 0;
        setChanged();
        setLit(false);
    }

    /** The stack as it stands, or null when the hearth is alone in the world. */
    public FurnaceShape shape() {
        return shape;
    }

    /** Ask for the stack to be measured again soon, for whatever stood next to it just changed. */
    public void requestRescan() {
        rescanCooldown = 0;
    }

    public boolean formed() {
        return shape != null;
    }

    /** How many modules of metal this stack was built to hold, zero before it is a furnace. */
    public int modules() {
        return shape == null ? 0 : shape.modules();
    }

    /**
     * The metal this stack promises, with the lining taking its share of the room away: the number the window
     * shows and the number a charge is measured against.
     */
    public int capacity() {
        RefiningFurnaceStats stats = stats();
        return shape == null || stats == null ? 0 : Math.max(0, stats.capacity(shape.modules()) - lining);
    }

    /**
     * How much more metal will fit. Slag is not on that account, because the vessel has headroom of its own for
     * the gangue a melt brings with it: a bath of 800 mB of metal really holds more than 800 millilitres, and
     * the difference is the dirt that the boil then takes back out.
     */
    public int room() {
        return Math.max(0, capacity() - metalInBath());
    }

    /** Millilitres of metal in the bath, which is what was promised; the slag riding in it is extra volume. */
    public int metalInBath() {
        return (int) Math.max(0, bath.getAmount() - slagInBath());
    }

    /** How much of the bath is slag rather than metal, in millilitres. */
    public int slagMb() {
        return (int) Math.round(slagInBath());
    }

    /**
     * The registry id of what the bath currently is, sent to the window so the tank can be drawn in the sprite of
     * the metal actually standing in it. Zero is `minecraft:empty`, which is the honest answer for a cold vessel.
     */
    public int bathFluidId() {
        FluidStack result = bath.getResult(level);
        return result.isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(result.getFluid());
    }

    /** How much more of anything — metal or slag — will still go into the vessel. */
    public int vesselRoom() {
        return Math.max(0, vesselCapacity() - bath.getAmount());
    }

    /** What the vessel itself can hold, metal and slag together, with the lining's share taken off it. */
    public int vesselCapacity() {
        RefiningFurnaceStats stats = stats();
        return shape == null || stats == null ? 0 : Math.max(0, stats.vesselCapacity(shape.modules()) - lining);
    }

    /** How hot the fire and the bath stand, which is what decides what can melt in here at all. */
    public int temperature() {
        return Math.round(temperature);
    }

    /** How many pieces of the charge are still waiting to melt. */
    public int chargeLeft() {
        return chargeCount();
    }

    public int volume() {
        return bath.getAmount();
    }

    public int lining() {
        return lining;
    }

    public Phase phase() {
        return phase;
    }

    public int ticksLeft() {
        return timer;
    }

    /** How much of what is in the bath is dirt, which is the one number the furnace is willing to show. */
    public int slagSharePercent() {
        int amount = bath.getAmount();
        return amount <= 0 ? 0 : (int) Math.round(100D * slagInBath() / amount);
    }

    public int reagentUnits(RefiningFurnaceStats stats) {
        int units = 0;
        for (ItemStack stack : reagent) {
            if (stats.mbOfSlagPer(stack) > 0) {
                units += stack.getCount();
            }
        }
        return units;
    }

    public boolean isLit() {
        return phase != Phase.IDLE;
    }

    /** Whether the bath is still liquid enough to run out of the tap, whatever phase the firing is in. */
    public boolean isPourable(RefiningFurnaceStats stats) {
        return phase != Phase.IDLE && temperature >= stats.pourTemperature();
    }

    /**
     * Puts the held stack into the furnace: fuel, reagent or metal, decided by what the tuning sheet says each
     * of them is. Metal is anything TFC knows how to melt into a fluid, so a player is never told to sort their
     * charge before they can light the fire. A shaft furnace is fed *through* the blast — that is what a cupola
     * is — so the only phase that refuses anything is the one where the bath is already setting.
     */
    public Loaded load(ItemStack held, RefiningFurnaceStats stats) {
        if (held.isEmpty()) {
            return new Loaded(0, Refusal.NONE);
        }
        if (shape == null) {
            return new Loaded(0, Refusal.UNFORMED);
        }
        if (phase == Phase.COOLING) {
            return new Loaded(0, Refusal.SETTING);
        }
        if (stats.isFuel(held)) {
            return take(fuel, held, fuelRequired(stats) - fuelCount(), Refusal.FUEL_FULL);
        }
        if (stats.mbOfSlagPer(held) > 0) {
            return take(reagent, held, stats.reagentCap(shape.modules()) - reagentUnits(stats), Refusal.POWDER_FULL);
        }
        if (held.is(IMItems.SKIMMINGS.get())) {
            return takeSkimmings(held);
        }
        int per = meltYield(held);
        if (per <= 0) {
            return new Loaded(0, Refusal.NOT_A_CHARGE);
        }
        // A charge is not measured against the vessel at all. Everything that is thrown down the throat melts,
        // and what will not stand in the hearth runs off over the dam — so the answer to "how much can I put in"
        // is "as much as you can carry", and the answer to the excess is a cast on the table rather than a refusal.
        return take(charge, held, held.getCount(), Refusal.NONE);
    }

    /**
     * Melting skimmings back into the bath. They are not a heating recipe — they are the bath's own mix, kept
     * exactly as it left, and the furnace is the vessel that made them, so the furnace is the vessel that takes
     * them back.
     */
    private Loaded takeSkimmings(ItemStack held) {
        String fluid = held.get(IMDataComponents.SKIMMING_FLUID.get());
        Integer mb = held.get(IMDataComponents.SKIMMING_MB.get());
        if (fluid == null || mb == null || mb <= 0) {
            return new Loaded(0, Refusal.NOT_A_CHARGE);
        }
        FluidStack melt = new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(fluid)), mb);
        if (melt.isEmpty()) {
            return new Loaded(0, Refusal.NOT_A_CHARGE);
        }
        // The composition has to come back with the metal. A record-less fluid in a standing bath is exactly the
        // junk case the tolerant matcher refuses, and the whole heat turns to unknown behind it.
        AlloyRecord record = held.get(IMDataComponents.ALLOY_RECORD.get());
        if (record == null) {
            return new Loaded(0, Refusal.NOT_A_CHARGE);
        }
        melt.set(IMDataComponents.ALLOY_RECORD.get(), record);
        held.shrink(1);
        bath.fill(melt, IFluidHandler.FluidAction.EXECUTE, container);
        setChanged();
        return new Loaded(1, Refusal.NONE);
    }

    private Loaded take(NonNullList<ItemStack> slots, ItemStack held, int room, Refusal whenFull) {
        if (room <= 0) {
            return new Loaded(0, whenFull);
        }
        int before = held.getCount();
        add(slots, held, room);
        return new Loaded(before - held.getCount(), Refusal.NONE);
    }

    /**
     * What one item of a charge will pour when the fire reaches it, or zero for anything that has no melt at all.
     * Read per single item rather than per stack, because TFC's recipe answers for one and the stack size is the
     * furnace's own arithmetic.
     */
    private int meltYield(ItemStack stack) {
        HeatingRecipe recipe = HeatingRecipe.getRecipe(stack);
        if (recipe == null) {
            return 0;
        }
        FluidStack one = recipe.assembleFluid(stack.copyWithCount(1));
        return one == null || one.isEmpty() ? 0 : one.getAmount();
    }

    /** How much of the bath the charge waiting in here will already take up, so the furnace cannot be overfed. */
    public int chargeMb(RefiningFurnaceStats stats) {
        int mb = 0;
        for (ItemStack stack : charge) {
            if (!stack.isEmpty()) {
                mb += meltYield(stack) * stack.getCount();
            }
        }
        return mb;
    }

    /**
     * Why a firestarter does nothing here yet, in the few words the hearth can say out loud.
     *
     * An empty hearth lights. A shaft furnace is brought up to heat before anything is thrown in — that is what
     * the refractory is for — and refusing the click until a player fed it metal would leave no way to preheat
     * it, which is the one thing the long burn is good for.
     */
    public String lightRefusal(RefiningFurnaceStats stats) {
        if (shape == null) {
            return "shape";
        }
        if (phase != Phase.IDLE) {
            return "lit";
        }
        return fuelCount() >= fuelRequired(stats) ? "none" : "fuel";
    }

    public boolean canLight(RefiningFurnaceStats stats) {
        return lightRefusal(stats).equals("none");
    }

    public int fuelRequired(RefiningFurnaceStats stats) {
        return shape == null ? 0 : stats.fuelPerModule() * shape.modules();
    }

    /**
     * Lights the fire. The fuel stays in the hearth and burns down through the firing, because a furnace that
     * ate its charcoal at once could not be refuelled halfway through a long heat.
     */
    public boolean light(RefiningFurnaceStats stats) {
        doused = false;
        if (!canLight(stats)) {
            return false;
        }
        phase = Phase.HEATING;
        timer = 0;
        fuelTicks = 0;
        reagentCredit = 0;
        slagWaiting = 0;
        // Whatever the walls caught from the last firing comes back into the bath this one melts into: plating
        // slag out of a freeze does not destroy it, it only moves it, and this is where it moves out from.
        redissolveLining(stats);
        setChanged();
        setLit(true);
        return true;
    }

    /**
     * What a broken hearth gives up: the charge, the fuel and the reagent it was holding, and the slag that had
     * plated onto the walls as dust. The bath is not on that list, because molten metal has nowhere to go when
     * the thing holding it stops existing — which is the other half of why the lining is scraped rather than
     * smashed, and why the metal still in a tapped-out furnace is worth hurrying back for.
     */
    public void eject(RefiningFurnaceStats stats) {
        if (level == null || level.isClientSide) {
            return;
        }
        dropAll(charge);
        dropAll(fuel);
        dropAll(reagent);
        if (stats != null && slagPiled > 0) {
            int units = slagPiled / Math.max(1, stats.mbSlagPerFlux());
            slagPiled = 0;
            ItemStack heap = heapSeed.isEmpty() ? Casts.dust(bath.getResult(level), units) : heapSeed.copyWithCount(units);
            for (ItemStack piece : Casts.split(heap)) {
                Block.popResource(level, worldPosition, piece);
            }
            heapSeed = ItemStack.EMPTY;
            if (shape != null) {
                // The lump itself goes with the furnace that made it; the account is already paid above, so the
                // block's own removal finds nothing left to give and the slag is never dropped twice.
                level.removeBlock(shape.slagOutlet(), false);
            }
        }
        if (stats != null && lining > 0) {
            int units = Math.max(1, lining / stats.mbSlagPerFlux());
            ItemStack dust = Casts.dust(bath.getResult(level), Math.min(units, 64));
            for (ItemStack piece : Casts.split(dust)) {
                Block.popResource(level, worldPosition, piece);
            }
            lining = 0;
        }
        setChanged();
    }

    /**
     * The line the metal travels. It is walked out of the build on every question rather than stored, so a
     * channel pulled out mid-firing means the next click finds no line — the furnace does not pour into a gap,
     * and nothing has to be repaired in somebody else's block entity.
     */
    public record TapLine(MoldTableBlockEntity table, BlockPos tablePos, List<BlockPos> channels, Direction intoTable) {
    }

    /**
     * Where the metal of a tap is going: the mold table standing in the tap's outlet cell, whether it is there
     * itself or reached by a run of casting channel walked outward, straight and level. TFC's channels do not
     * climb, so neither do we, and a run that turns a corner is not a tap line — the player hears that rather
     * than watching metal disappear into a wall.
     */
    public TapLine tapLine() {
        if (shape == null || level == null) {
            return null;
        }
        BlockPos outlet = shape.tapOutlet();
        Direction ahead = FurnaceShape.direction(shape.tapWall(), outlet);
        if (level.getBlockEntity(outlet) instanceof MoldTableBlockEntity table) {
            return new TapLine(table, outlet, List.of(), ahead);
        }
        if (!level.getBlockState(outlet).is(TFCBlocks.CHANNEL.get())) {
            return null;
        }
        List<BlockPos> channels = new ArrayList<>();
        BlockPos here = outlet;
        for (int step = 0; step < MAX_TAP_RUN; step++) {
            channels.add(here);
            BlockPos next = here.relative(ahead);
            if (level.getBlockEntity(next) instanceof MoldTableBlockEntity table) {
                return new TapLine(table, next, channels, ahead);
            }
            if (!level.getBlockState(next).is(TFCBlocks.CHANNEL.get())) {
                return null;
            }
            here = next;
        }
        return null;
    }

    /**
     * Opens the tap. The click starts a flow rather than delivering a pour, because that is what a tap hole does:
     * it runs until the mould is full or the bath is. The rate is TFC's own — a millilitre every other tick — and
     * the stream is TFC's drawing too, because the links and the table's source are written through their public
     * API. The table's own tick then leaves the flow alone, since its source is a furnace rather than a crucible,
     * and everything that happens to the metal once it is in the mould is the ordinary casting recipe our defect
     * record already rides along on.
     */
    public String startTap(RefiningFurnaceStats stats, BlockPos clicked) {
        return startTap(stats, clicked, stats.pourVolume());
    }

    /**
     * Opening the tap for a stated amount rather than for a whole module, which is what the overflow needs: a
     * player who over-filled the shaft wants the excess off, not a second module of metal on top of it.
     */
    private String startTap(RefiningFurnaceStats stats, BlockPos clicked, int offered) {
        String why = tapRefusal(stats, clicked);
        if (!why.equals("none")) {
            return why;
        }
        TapLine line = tapLine();
        FluidStack offer = bath.drain(level, offered, IFluidHandler.FluidAction.SIMULATE);
        Direction side = line.intoTable().getOpposite();
        int wanted = line.table().getSidedFluidInventory(side).fill(offer, IFluidHandler.FluidAction.SIMULATE);
        if (wanted <= 0) {
            // A table standing tight against the duct is fed through the face that touches the furnace, and a run
            // of channel through the face the channel says it came from - but which face the table itself will
            // take is its own business, so the tap asks all six rather than insisting on the one that seems
            // obvious. A pour that found nowhere to go was reported as a full mould for a whole session before
            // this was tried from the other side.
            for (Direction candidate : Direction.values()) {
                if (candidate == side) {
                    continue;
                }
                int also = line.table().getSidedFluidInventory(candidate).fill(offer, IFluidHandler.FluidAction.SIMULATE);
                if (also > 0) {
                    side = candidate;
                    wanted = also;
                    break;
                }
            }
        }
        if (wanted <= 0) {
            // Three different things make a mould refuse a pour, and the player does a different one of three
            // chores in each case: lift a finished cast, empty a vessel holding another metal, or go and find out
            // why the table is not holding a vessel at all. One word for all three sent people to empty a table
            // that had nothing on it.
            IMold vessel = IMold.get(line.table().getMoldStack());
            if (vessel == null || vessel.getTankCapacity(0) <= 0) {
                return "novessel";
            }
            return vessel.getFluidInTank(0).isEmpty() ? "full" : "other";
        }
        pourLeft = wanted;
        pourTable = line.tablePos();
        pourSide = side;
        ResourceLocation fluid = BuiltInRegistries.FLUID.getKey(offer.getFluid());
        BlockPos from = shape.tapWall();
        for (int step = 0; step < line.channels().size(); step++) {
            BlockPos channel = line.channels().get(step);
            if (level.getBlockEntity(channel) instanceof ChannelBlockEntity duct) {
                // Only a channel that stands behind another channel is connected to one: the first of the run is
                // fed by the tap hole itself, and TFC draws that end differently from a joint.
                duct.setLinkProperties(Pair.of(FurnaceShape.direction(channel, from), (byte) 1), step > 0, 1, fluid);
            }
            from = channel;
        }
        // The pair a table carries points back along the stream, at the cell the metal came out of: TFC's own
        // isFlowing() looks for a channel there, and the renderer draws the spout from it.
        line.table().setSource(from, offer.getFluid(), Pair.of(pourSide, (byte) 1));
        setChanged();
        return "none";
    }

    /** The flow's share of the tick: what the rate allows out of the bath and into the table, and no further. */
    private void tickTap(RefiningFurnaceStats stats) {
        if (pourLeft <= 0) {
            return;
        }
        if (!(level.getBlockEntity(pourTable) instanceof MoldTableBlockEntity table)) {
            pourLeft = 0;
            return;
        }
        if (!isPourable(stats)) {
            // The bath fell below tapping heat with a tap still open: whatever is already in the mould is the
            // player's, and what stayed in the bath stays there until the fire is fed again.
            closeTap(table);
            return;
        }
        FluidStack pour = bath.drain(level, Math.min(TAP_MB_PER_TICK, pourLeft), IFluidHandler.FluidAction.EXECUTE);
        if (pour.isEmpty()) {
            closeTap(table);
            return;
        }
        int filled = table.getSidedFluidInventory(pourSide).fill(pour, IFluidHandler.FluidAction.EXECUTE);
        if (filled > 0) {
            // A clay mould is cold, and metal poured into a cold mould freezes where it lands. TFC's own drip
            // path heats the receiving mould as it fills — without that the tap assembled a quarter of a cast,
            // the table then held it, and every further millilitre was refused as "already full".
            IMold mold = IMold.get(table.getMoldStack());
            if (mold != null) {
                mold.setTemperature(temperature);
                table.setAndUpdateSlots(MoldTableBlockEntity.MOLD_SLOT);
            }
        }
        if (filled < pour.getAmount()) {
            // The mould filled up under the stream, so what did not fit goes back into the bath rather than out
            // of the world, and the tap closes on the metal that is still liquid and still in here.
            bath.fill(pour.copyWithAmount(pour.getAmount() - filled), IFluidHandler.FluidAction.EXECUTE, container);
        }
        pourLeft -= filled;
        if (filled == 0 || pourLeft <= 0) {
            closeTap(table);
        }
        setChanged();
    }

    /**
     * Stops a flow and takes the drawn stream away with it. The table's own {@code finishFlow} walks back down
     * the link it was given and breaks each channel's link in turn, which is the only way a tap stops looking
     * open: TFC draws the metal sitting in a channel straight off that link.
     */
    private void closeTap(MoldTableBlockEntity table) {
        pourLeft = 0;
        table.finishFlow();
    }

    /** How much of an open tap has not reached the mould yet, which is why a second click is refused. */
    public int tapRemaining() {
        return pourLeft;
    }

    /**
     * What actually stands on the tap table, in the two numbers that tell a full mould from one that was never a
     * vessel at all. A refusal that says only "full" sends a player to empty a table that has nothing in it, so
     * the machine says what it saw instead of what it assumed.
     */
    public Component tapContents() {
        TapLine line = tapLine();
        ItemStack vessel = line == null ? ItemStack.EMPTY : line.table().getMoldStack();
        if (vessel.isEmpty()) {
            return Component.literal("нет сосуда");
        }
        IMold mold = IMold.get(vessel);
        return Component.literal(BuiltInRegistries.ITEM.getKey(vessel.getItem()).getPath() + " "
                + (mold == null ? "?" : mold.getFluidInTank(0).getAmount()) + "/"
                + (mold == null ? "?" : mold.getTankCapacity(0)) + " mB");
    }

    /**
     * Why the tap will not start, in the one word a channel can say out loud, in the order a player can act in:
     * is this the tap's own channel, is there a run to a table, is there a mould on that table, is a pour already
     * running, is the bath liquid at all, is there any metal left in it.
     */
    public String tapRefusal(RefiningFurnaceStats stats, BlockPos clicked) {
        if (shape == null || !clicked.equals(shape.tapOutlet())) {
            return "stranger";
        }
        TapLine line = tapLine();
        if (line == null) {
            return "nochannel";
        }
        if (line.table().getMoldStack().isEmpty()) {
            return "mold";
        }
        if (line.table().getMoldStack().is(IMItems.SLAG_PLATE.get())) {
            // The furnace's own slag dish standing on the tap table is the same object in the wrong cell: it holds
            // slag and nothing else, so a pour into it is a pour into a vessel that will never take metal.
            return "slagdish";
        }
        if (pourLeft > 0) {
            return "busy";
        }
        if (!isPourable(stats)) {
            return "cold";
        }
        return bath.isEmpty() ? "empty" : "none";
    }

    /**
     * Scrapes plated-out slag off the walls with a pickaxe. It is slow and it costs the tool, which is the
     * point: the furnace is cleaned by hand only because filling it properly was cheaper.
     */
    public int scrapeLining(Player player, ItemStack tool, RefiningFurnaceStats stats) {
        if (lining <= 0 || isLit()) {
            return 0;
        }
        int removed = Math.min(lining, stats.liningScrapeMb());
        lining -= removed;
        int dustUnits = Math.max(1, removed / stats.mbSlagPerFlux());
        ItemStack dust = Casts.dust(bath.getResult(level), dustUnits);
        for (ItemStack piece : Casts.split(dust)) {
            if (!player.getInventory().add(piece)) {
                player.drop(piece, false);
            }
        }
        tool.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        setChanged();
        return removed;
    }

    /**
     * The fire is paid every tick of world time that has passed since it was last asked, so a skipped night costs
     * it the charge it should have burned rather than going unnoticed.
     */
    private void tick(RefiningFurnaceStats stats) {
        long steps = elapsedTicks();
        if (shape == null || phase == Phase.IDLE) {
            // A hearth standing alone is a hot object in cold air too, and saying so is the only way the window
            // stops reporting a temperature for a furnace that stopped being one.
            coolToAmbient(steps);
            return;
        }
        for (long step = 0; step < steps; step++) {
            advance(stats);
            if (phase == Phase.IDLE) {
                coolToAmbient(steps - step - 1);
                break;
            }
        }
        setChanged();
    }

    /**
     * How many ticks of world time this fire has not been paid yet. Both clocks are asked and the larger answer
     * wins, because sleeping moves TFC's calendar while vanilla {@code /time add} moves the day time TFC then
     * writes back — and a machine that follows only one of them ignores the command that moved the other.
     */
    private long elapsedTicks() {
        long calendar = Calendars.get(level).getTicks();
        long dayTime = level.getDayTime();
        long passed = lastCalendarTick < 0 ? 1
                : Math.max(calendar - lastCalendarTick, Math.max(0, dayTime - lastDayTime));
        lastCalendarTick = calendar;
        lastDayTime = dayTime;
        return Math.min(passed, MAX_CATCHUP_TICKS);
    }

    /** One tick of the firing, as the phase machine sees it. */
    private void advance(RefiningFurnaceStats stats) {
        burnFuel(stats);
        tickTap(stats);
        // A fed fire climbs toward what its own chimney can hold; a starved one falls back toward the outside air.
        temperature = HeatCapability.adjustTempTowards(temperature,
                fuelTicks > 0 ? stats.fireTemperature(shape.layers()) : AMBIENT);

        if (phase == Phase.HEATING) {
            heatCharge(stats);
            if (!hasCharge()) {
                phase = Phase.BOILING;
                timer = stats.boilTicks();
            } else if (fuelTicks <= 0 && fuelCount() == 0) {
                // The fire died with charge still in it: the rest does not melt, and no slag drains out of it.
                phase = Phase.COOLING;
            }
        } else if (phase == Phase.BOILING) {
            // The boil is the reaction, not the draining. Nothing leaves the bath while it runs, and what the
            // powder took out of the metal runs off through the channel afterwards, while the bath is still
            // liquid enough to give it up.
            if (--timer <= 0) {
                phase = Phase.COOLING;
            }
        } else if (phase == Phase.COOLING) {
            drainSlag(stats);
            if (temperature < stats.pourTemperature() && (doused || fuelCount() == 0)) {
                coolDown();
            }
        }
        spill(stats);
    }

    /**
     * What a bath standing above its own capacity does with the excess: runs it off over the dam and into whatever
     * mould waits on the tap table, the way a cupola's cinder notch leaves the hearth at a fixed depth no matter
     * how much is charged above it. The metal is not lost and not punished for being poured — it comes out as the
     * cast the table is standing there to take — and the bath that stays behind is the whole heat's average,
     * which is the point of filling a shaft furnace rather than a crucible.
     */
    private void spill(RefiningFurnaceStats stats) {
        if (shape == null || !isPourable(stats)) {
            return;
        }
        // What the hearth is sized to hold is so much *metal*, not so much of anything: a bath of 800 mB of metal
        // with 120 of slag floating in it stands above its own capacity, and spilling on the total would take
        // clean bronze out of the notch for dirt that has not reached it yet.
        // The number a module promises is the number it keeps: the lining eats capacity for real, but it is not
        // what the player read on the JEI page when they built the stack, and spilling to 799 of a promised 800
        // reads as a machine with a hidden tax rather than as a machine with dirty walls.
        int keep = ratedCapacity();
        int over = (bath.getAmount() - slagMb()) - keep;
        if (over <= 0) {
            return;
        }
        // Whatever stands above the vessel's own rim leaves at once, because nothing can melt into a cell that is
        // already full; what is between the module's promise and that rim runs off at the notch's own rate.
        int off = bath.getAmount() > vesselCapacity() ? over : Math.min(TAP_MB_PER_TICK, over);
        FluidStack drawn = bath.drain(level, Math.min(off, over), IFluidHandler.FluidAction.EXECUTE);
        if (drawn.isEmpty()) {
            return;
        }
        if (skimming.isEmpty()) {
            skimming = new ItemStack(IMItems.SKIMMINGS.get());
            skimming.set(IMDataComponents.SKIMMING_FLUID,
                    BuiltInRegistries.FLUID.getKey(drawn.getFluid()).toString());
            skimming.set(IMDataComponents.SKIMMING_MB, 0);
        }
        // The composition rides the same component the melt itself carries. A piece without it is worse than no
        // piece at all: the bath it comes back to has no record for the fluid and calls the whole heat unknown.
        AlloyRecord record = drawn.get(IMDataComponents.ALLOY_RECORD.get());
        if (record != null) {
            skimming.set(IMDataComponents.ALLOY_RECORD.get(), record);
        }
        skimming.set(IMDataComponents.SKIMMING_MB, skimmingMb() + drawn.getAmount());
        setChanged();
    }

    /** What the stack's modules promise, before the walls' lining is taken off it. */
    private int ratedCapacity() {
        RefiningFurnaceStats stats = stats();
        return shape == null || stats == null ? 0 : stats.capacity(shape.modules());
    }

    /** How much of the last heat has gone over the dam and is still waiting to be lifted off. */
    public int skimmingMb() {
        Integer held = skimming.get(IMDataComponents.SKIMMING_MB.get());
        return held == null ? 0 : held;
    }

    /**
     * Taking the skimmings off the hearth in whole ingot-sized pieces, each carrying the fluid, the amount and the
     * composition of what actually ran over, so a piece is a record of the heat rather than a token for it.
     */
    public int collectSkimmings(net.minecraft.world.entity.player.Player player) {
        int pieces = skimmingMb() / 100;
        if (pieces <= 0 || level == null || level.isClientSide) {
            return 0;
        }
        for (int piece = 0; piece < pieces; piece++) {
            ItemStack lump = skimming.copyWithCount(1);
            lump.set(IMDataComponents.SKIMMING_MB, 100);
            if (!player.getInventory().add(lump)) {
                player.drop(lump, false);
            }
        }
        skimming.set(IMDataComponents.SKIMMING_MB, skimmingMb() - pieces * 100);
        if (skimmingMb() <= 0) {
            skimming = ItemStack.EMPTY;
        }
        setChanged();
        return pieces;
    }


    /**
     * What the walls give back to a fresh melt: a share of the plated-out slag comes off the brick and into the
     * bath, so a furnace that was left dirty makes the next pour dirty as well. It is a transfer rather than a
     * copy — whatever is not drained out of the channel by the end of the firing goes straight back onto the
     * walls — which is why the only real ways out are to load the next heat properly or to scrape.
     */
    private void redissolveLining(RefiningFurnaceStats stats) {
        int back = Math.min(lining, Math.round(lining * stats.liningReturn()));
        if (back <= 0 || vesselRoom() < back) {
            return;
        }
        lining -= back;
        bath.fill(new FluidStack(IMFluids.SLAG.fluid(), back), IFluidHandler.FluidAction.EXECUTE, container);
    }

    /**
     * Warms every piece of the charge the way TFC's own crucible does - toward the fire's temperature, at the
     * rate that fire and the item's own heat capacity give - and melts whatever has reached the temperature its
     * own heating recipe asks for. This is why a heat takes as long as it takes, and why a short chimney cannot
     * refine iron at all: the items never come near their melt temperature, and no amount of waiting changes that.
     */
    private void heatCharge(RefiningFurnaceStats stats) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = charge.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            HeatCapability.addTemp(HeatCapability.get(stack), temperature, 2.0F + temperature * 0.0025F);
            HeatingRecipe recipe = HeatingRecipe.getRecipe(stack);
            if (recipe == null || !recipe.isValidTemperature(HeatCapability.getTemperature(stack))) {
                continue;
            }
            FluidStack melt = recipe.assembleFluid(stack.copyWithCount(1));
            if (melt == null || melt.isEmpty()) {
                continue;
            }
            // Nothing here refuses a melt for want of room. A shaft furnace that is fed more than its hearth holds
            // does not stop melting — it runs the excess over the dam, which is what `spill` below is for — and
            // that is what lets a charge of nine copper and one tin come out as ninety-and-ten bronze rather than
            // as a furnace that has quietly stopped taking the tenth ingot.

            stack.shrink(1);
            bath.fill(MeltCarries.keepTheFlaw(stack, melt), IFluidHandler.FluidAction.EXECUTE, container);
            return;
        }
    }

    /** One piece of fuel keeps the fire for its own burn time, and the fire is only fed while that lasts. */
    private void burnFuel(RefiningFurnaceStats stats) {
        if (doused) {
            return;
        }
        fuelTicks--;
        if (fuelTicks <= 0 && fuelCount() > 0) {
            int burn = stats.burnTicks(nextFuel());
            consumeOne(fuel);
            fuelTicks += burn;
        }
    }

    /** The next piece the fire will eat, whose own duration decides how long this stretch of burning lasts. */
    private ItemStack nextFuel() {
        for (ItemStack stack : fuel) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * How much longer this fire can last: what the burning piece has left, plus every piece still lying in the
     * box at its own burn time. The window counts this down, because "cooling" over a furnace with ten minutes of
     * charcoal in it is not a reading of the machine but a lie about it.
     */
    public int fireSecondsLeft(RefiningFurnaceStats stats) {
        int ticks = Math.max(0, fuelTicks);
        for (ItemStack stack : fuel) {
            if (!stack.isEmpty()) {
                ticks += stack.getCount() * stats.burnTicks(stack);
            }
        }
        return ticks / 20;
    }

    /**
     * Dropping the fire with a shovel, which is how a shaft furnace is really stopped: the blast is cut and the
     * fire dies where it stands. The fuel is not taken out and not destroyed — it is simply no longer burning, and
     * the next lighting starts from what is still in the hearth.
     */
    public boolean quench() {
        if (level == null || level.isClientSide || phase == Phase.IDLE) {
            return false;
        }
        doused = true;
        fuelTicks = 0;
        if (phase == Phase.HEATING || phase == Phase.BOILING) {
            phase = Phase.COOLING;
        }
        setChanged();
        return true;
    }

    /**
     * Lets slag past the channel while three things hold: the bath is deep enough to reach the hole, the reagent
     * in the fire still buys more of it, and the outlet can take it — the slag plate standing on the mold table
     * there, or the freezing heap in the cell when no plate is set. Anything that fails leaves the slag in the
     * bath, and the bath will not keep it.
     */
    private void drainSlag(RefiningFurnaceStats stats) {
        Fluid slag = IMFluids.SLAG.fluid();
        double present = bath.getContent().getDouble(slag);
        if (present <= 0 || bath.getAmount() < stats.drainThreshold(shape.modules())) {
            return;
        }
        if (reagentCredit <= 0) {
            ItemStack used = takeReagent(stats);
            if (used == null) {
                return;
            }
            reagentCredit = stats.mbOfSlagPer(used);
        }
        int mb = (int) Math.min(stats.slagDrainMbPerTick(), Math.min(reagentCredit, present));
        if (mb <= 0) {
            return;
        }
        IMold plate = ensureSlagPlate();
        if (plate == null) {
            freezeSlag(mb, stats);
            return;
        }
        int offered = plate.fill(new FluidStack(slag, mb), IFluidHandler.FluidAction.SIMULATE);
        if (offered <= 0) {
            // The plate is full to its rim, which is the readout the window gives and the reason the bath keeps
            // its dirt: cleaning the slag plate is the one standing errand of a firing.
            return;
        }
        MeltComposition composition = (MeltComposition) (Object) bath;
        int taken = composition.immersiveMetallurgyTake(slag, Math.min(mb, offered));
        if (taken <= 0) {
            return;
        }
        reagentCredit -= taken;
        loseMetal(stats, taken);
        int filled = plate.fill(new FluidStack(slag, taken), IFluidHandler.FluidAction.EXECUTE);
        if (filled < taken) {
            // The plate answered the real fill with a different number than it answered the question. Whatever it
            // did not take goes back into the bath rather than out of the world.
            bath.fill(new FluidStack(slag, taken - filled), IFluidHandler.FluidAction.EXECUTE, container);
        }
        plate.setTemperature(temperature);
        MoldTableBlockEntity table = slagTable();
        if (table != null) {
            table.setAndUpdateSlots(MoldTableBlockEntity.MOLD_SLOT);
        }
        setChanged();
    }

    /** The table standing in the slag outlet, whether or not anything is on it. */
    private MoldTableBlockEntity slagTable() {
        if (shape == null || level == null) {
            return null;
        }
        return level.getBlockEntity(shape.slagOutlet()) instanceof MoldTableBlockEntity table ? table : null;
    }

    /**
     * The plate the furnace keeps in its slag outlet, or null when nothing in that cell will take slag: no table
     * at all, or somebody's metal mould standing on one. Reading is kept apart from laying, because the window
     * asks this question every couple of ticks and asking it must not change the world.
     */
    private IMold slagPlate() {
        MoldTableBlockEntity table = slagTable();
        if (table == null) {
            return null;
        }
        ItemStack vessel = table.getMoldStack();
        return vessel.is(IMItems.SLAG_PLATE.get()) ? IMold.get(vessel) : null;
    }

    /**
     * The dish the slag channel runs into, which is the part of the furnace the player does not have to think
     * about: a mold table standing in the slag outlet is set with a slag plate by the furnace itself. Taking the
     * dirt out of a bath is already the reason the machine exists, so the catcher that makes that possible must
     * not be a thing that can be forgotten, lost or left at home.
     */
    private IMold ensureSlagPlate() {
        IMold plate = slagPlate();
        if (plate != null) {
            return plate;
        }
        MoldTableBlockEntity table = slagTable();
        if (level == null || level.isClientSide || table == null) {
            return null;
        }
        if (!table.getMoldStack().isEmpty()) {
            // Something else is standing in the furnace's slag outlet. It goes back out as an item rather than
            // being destroyed, and the plate takes its place, because the outlet is the one cell the machine
            // cannot run without and a player who put a mould there meant it as a catcher.
            ItemStack foreign = table.getMoldStack();
            Containers.dropItemStack(level, table.getBlockPos().getX() + 0.5, table.getBlockPos().getY() + 0.6,
                    table.getBlockPos().getZ() + 0.5, foreign);
            table.setMoldStack(ItemStack.EMPTY);
            table.setAndUpdateSlots(MoldTableBlockEntity.MOLD_SLOT);
        }
        table.setMoldStack(new ItemStack(IMItems.SLAG_PLATE.get()));
        table.setAndUpdateSlots(MoldTableBlockEntity.MOLD_SLOT);
        setChanged();
        return IMold.get(table.getMoldStack());
    }

    /** How much slag the plate on the table can still take, for the window's fill line. */
    public int slagPlateMb() {
        IMold plate = slagPlate();
        if (plate == null) {
            return 0;
        }
        return plate.getFluidInTank(0).getAmount();
    }

    /**
     * What an empty hand on the slag table does: the plate gives up everything it holds as dust of the metal the
     * bath is made of, and the furnace goes back to draining. TFC's own click cannot do this, because slag has no
     * casting recipe and a table with nothing to hand back hands nothing back — so the paying out is ours, and it
     * answers only a table some furnace claims as its slag outlet.
     */
    public int cleanSlagPlate(Player player) {
        IMold plate = slagPlate();
        if (plate == null || level == null || level.isClientSide) {
            return 0;
        }
        RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
        int mb = plate.getFluidInTank(0).getAmount();
        int units = stats == null ? 0 : mb / Math.max(1, stats.mbSlagPerFlux());
        if (units <= 0) {
            return 0;
        }
        plate.drainIgnoringTemperature(units * stats.mbSlagPerFlux(), IFluidHandler.FluidAction.EXECUTE);
        MoldTableBlockEntity table = slagTable();
        if (table != null) {
            table.setAndUpdateSlots(MoldTableBlockEntity.MOLD_SLOT);
        }
        ItemStack dust = Casts.dust(bath.getResult(level), units);
        for (ItemStack piece : Casts.split(dust)) {
            if (!player.getInventory().add(piece)) {
                player.drop(piece, false);
            }
        }
        setChanged();
        return units;
    }

    /**
     * Slag with no plate to run into freezes in the outlet cell instead. It is paid out in the same units the plate
     * would have been, so the choice between a plate and a pickaxe is a choice of when to pay, not of how much.
     */
    private void freezeSlag(int mb, RefiningFurnaceStats stats) {
        if (!heapHasRoom()) {
            // Something stands in the outlet that the slag cannot freeze onto — a bare table, a stone, anything.
            // The bath keeps its dirt and the window says why, rather than paying into a heap nobody can break.
            return;
        }
        slagWaiting += mb;
        int units = slagWaiting / stats.mbSlagPerFlux();
        if (units <= 0) {
            return;
        }
        int room = stats.slagPlateCapacity() - slagPiled / stats.mbSlagPerFlux();
        int placed = Math.min(units, Math.max(0, room));
        if (placed <= 0) {
            return;
        }
        MeltComposition composition = (MeltComposition) (Object) bath;
        int taken = composition.immersiveMetallurgyTake(IMFluids.SLAG.fluid(), placed * stats.mbSlagPerFlux());
        if (taken <= 0) {
            return;
        }
        slagWaiting -= taken;
        reagentCredit -= taken;
        loseMetal(stats, taken);
        slagPiled += taken;
        heapSeed = Casts.dust(bath.getResult(level), 1);
        freezeHeap();
        setChanged();
    }

    /** Whether the outlet is ground the slag can freeze onto: empty, or the heap a previous freeze already left. */
    private boolean heapHasRoom() {
        if (level == null || shape == null) {
            return false;
        }
        BlockState outlet = level.getBlockState(shape.slagOutlet());
        return outlet.is(IMBlocks.SLAG_HEAP.get()) || outlet.canBeReplaced();
    }

    /** Draws the lump of slag that had nowhere else to go in the outlet cell, so it can be broken out. */
    private void freezeHeap() {
        if (level == null || shape == null || level.isClientSide) {
            return;
        }
        BlockPos outlet = shape.slagOutlet();
        if (level.getBlockState(outlet).canBeReplaced()) {
            level.setBlock(outlet, IMBlocks.SLAG_HEAP.get().defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Whether this cell is the furnace's slag outlet, plate, table, heap or nothing standing in it. */
    public boolean ownsSlagOutlet(BlockPos pos) {
        return shape != null && pos.equals(shape.slagOutlet());
    }

    /**
     * What breaking the frozen heap gives back: every unit of dust that went into it, in the metal it was lifted
     * off — held in {@code heapSeed} precisely so that a bath since tapped dry cannot make bronze slag come out as
     * nameless dirt. The account is cleared here, which is why the heap pays once and never twice.
     */
    public void harvestSlagHeap(BlockPos pos) {
        if (!ownsSlagOutlet(pos) || level == null || level.isClientSide) {
            return;
        }
        RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
        int per = stats == null ? 25 : stats.mbSlagPerFlux();
        int units = slagPiled / Math.max(1, per);
        slagPiled = 0;
        if (units > 0) {
            ItemStack dust = heapSeed.isEmpty() ? Casts.dust(bath.getResult(level), units) : heapSeed.copyWithCount(units);
            for (ItemStack piece : Casts.split(dust)) {
                Block.popResource(level, pos, piece);
            }
        }
        heapSeed = ItemStack.EMPTY;
        setChanged();
    }

    /**
     * What the slag takes with it: the metal hanging in it, which comes back out of the dust when that is melted,
     * so the bath loses it and the world does not. Counted in fractions because one unit of slag drags less than a
     * millibucket, and a furnace that rounded it away would lose nothing at all.
     */
    private void loseMetal(RefiningFurnaceStats stats, int slagMb) {
        slagDragged += slagMb * stats.metalDrag();
        int drag = (int) slagDragged;
        if (drag > 0 && bath.getAmount() > drag) {
            bath.drain(level, drag, IFluidHandler.FluidAction.EXECUTE);
            slagDragged -= drag;
        }
    }

    /**
     * A firing that ended is still a hot object standing in cold air. Without this the bath kept whatever
     * temperature it happened to freeze at — the window then read "Cold: 999 °C" for a furnace that had been
     * dead for a day, and the only way to notice was that it never went away.
     */
    private void coolToAmbient(long ticks) {
        if (temperature <= AMBIENT) {
            return;
        }
        for (long step = 0; step < ticks && temperature > AMBIENT; step++) {
            float next = HeatCapability.adjustTempTowards(temperature, AMBIENT);
            // The step shrinks as the gap does, so the last fraction of a degree is settled rather than crawled
            // toward forever, and a machine that is cold says so and stops marking itself dirty.
            temperature = next - AMBIENT < 1F ? AMBIENT : next;
        }
        setChanged();
    }

    /**
     * The fire is out, the bath is freezing, and the slag still floating in it has nowhere left to go but onto
     * the walls.
     */
    private void coolDown() {
        Fluid slag = IMFluids.SLAG.fluid();
        int left = (int) Math.floor(bath.getContent().getDouble(slag));
        if (left > 0) {
            lining += ((MeltComposition) (Object) bath).immersiveMetallurgyTake(slag, left);
        }
        phase = Phase.IDLE;
        timer = 0;
        reagentCredit = 0;
        slagWaiting = 0;
        setChanged();
        setLit(false);
    }

    /**
     * Puts the fire on the block itself, so the light, the particles and what the hearth looks like all follow
     * the state without any of them having to ask the block entity.
     */
    public void setLit(boolean lit) {
        if (level == null || level.isClientSide || getBlockState().getValue(RefiningHearthBlock.LIT) == lit) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(RefiningHearthBlock.LIT, lit), Block.UPDATE_ALL);
    }

    public Direction facing() {
        return getBlockState().getValue(RefiningHearthBlock.FACING);
    }

    private void scan(Level level, BlockState state) {
        RefiningFurnaceStats stats = RefiningFurnaceStats.of(level.getRecipeManager());
        if (stats == null) {
            return;
        }
        FurnaceShape.Result result = FurnaceShape.scan(level, worldPosition, state.getValue(RefiningHearthBlock.FACING),
                stats.maxModules(), stats.channelOffsetFromTop());
        FurnaceShape before = shape;
        shape = result.shape();
        fault = result.fault();
        if (shape != null && (before == null || !shape.slagOutlet().equals(before.slagOutlet()))) {
            // The slag dish is laid the moment the furnace knows where its outlet is, not the first time slag
            // reaches it: a player who sets a table in the notch wants to see the machine take it, and a catcher
            // that only appears once the bath is already dirty reads as a bug in the table.
            ensureSlagPlate();
        }
        setChanged();
    }

    /** Which missing part stopped the stack from being a furnace, as a number the window can carry. */
    public int faultOrdinal() {
        return fault.ordinal();
    }


    private RefiningFurnaceStats stats() {
        return level == null ? null : RefiningFurnaceStats.of(level.getRecipeManager());
    }

    private double slagInBath() {
        return bath.getContent().getDouble(IMFluids.SLAG.fluid());
    }

    private ItemStack takeReagent(RefiningFurnaceStats stats) {
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = reagent.get(slot);
            if (!stack.isEmpty() && stats.mbOfSlagPer(stack) > 0) {
                return stack.split(1);
            }
        }
        return null;
    }

    public int chargeCount() {
        int count = 0;
        for (ItemStack stack : charge) {
            count += stack.getCount();
        }
        return count;
    }

    private boolean hasCharge() {
        return chargeCount() > 0;
    }

    public int fuelCount() {
        int count = 0;
        for (ItemStack stack : fuel) {
            count += stack.getCount();
        }
        return count;
    }

    private void dropAll(NonNullList<ItemStack> slots) {
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (!stack.isEmpty()) {
                Block.popResource(level, worldPosition, stack.copy());
                slots.set(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void consumeOne(NonNullList<ItemStack> slots) {
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (!stack.isEmpty()) {
                stack.shrink(1);
                return;
            }
        }
    }

    /** Puts as much of the held stack into these slots as the furnace has room for, and no more than fits. */
    private static void add(NonNullList<ItemStack> slots, ItemStack held, int room) {
        for (int slot = 0; slot < slots.size() && !held.isEmpty() && room > 0; slot++) {
            ItemStack here = slots.get(slot);
            if (here.isEmpty()) {
                int moved = Math.min(room, Math.min(held.getCount(), held.getMaxStackSize()));
                slots.set(slot, held.split(moved));
                room -= moved;
            } else if (ItemStack.isSameItemSameComponents(here, held) && here.getCount() < here.getMaxStackSize()) {
                int moved = Math.min(room, Math.min(here.getMaxStackSize() - here.getCount(), held.getCount()));
                here.grow(moved);
                held.shrink(moved);
                room -= moved;
            }
        }
    }

    /** The bath's own container rules: molten metal and slag only, and room for what the stack is built to hold. */
    private final class Container implements FluidContainerInfo {
        /**
         * The vessel's own room for metal plus slag, not the metal figure the window quotes: TFC compares what is
         * already in the bath plus what is arriving against this, so it is the whole capacity of the stack with
         * the lining off it — never the free space, which would count the metal already in there twice and fill
         * the furnace at half its size.
         */
        @Override
        public int fluidCapacity() {
            return vesselCapacity();
        }

        @Override
        public boolean canContainFluid(Fluid fluid) {
            if (IMFluids.isOurMolten(fluid)) {
                return true;
            }
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            return "tfc".equals(id.getNamespace()) && id.getPath().startsWith("metal/");
        }
    }

    /** How much slag the outlet is holding, in dust units: what is in the plate, or what has frozen in the cell. */
    public int slagOutletUnits(RefiningFurnaceStats stats) {
        if (stats == null) {
            return 0;
        }
        return (slagPlateMb() + slagPiled) / Math.max(1, stats.mbSlagPerFlux());
    }

    /** Whether the slag outlet has frozen over, which is the readout that tells the player to bring a plate. */
    public boolean slagOutletFrozen() {
        return slagPiled > 0;
    }

    /**
     * Whether the slag has nowhere to go at all: no plate standing on the table, and no ground free enough for a
     * heap either. It is the one fault the window can name that the player can walk over and put right at once.
     */
    public boolean slagOutletBlocked() {
        // A bare mold table is not a blockage: the furnace sets its own plate in it the first time slag
        // reaches the channel, so the only outlets that truly have nowhere to put anything are those with
        // no table at all and no ground the slag could freeze onto.
        return slagTable() == null && !heapHasRoom();
    }

    /** Whether a mold stands on the table the tap would run into, for the readout that says why it would not. */
    public boolean tapHasMold() {
        TapLine line = tapLine();
        return line != null && !line.table().getMoldStack().isEmpty();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RefiningFurnaceMenu(id, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container." + ImmersiveMetallurgy.MODID + ".refining_hearth");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("phase", phase.name());
        tag.putInt("timer", timer);
        tag.putInt("lining", lining);
        tag.putDouble("reagent_credit", reagentCredit);
        tag.putInt("slag_waiting", slagWaiting);
        tag.putInt("slag_piled", slagPiled);
        tag.putInt("pour_left", pourLeft);
        tag.putLong("pour_table", pourTable.asLong());
        tag.putInt("pour_side", pourSide.get3DDataValue());
        if (!heapSeed.isEmpty()) {
            tag.put("heap_seed", heapSeed.save(registries));
        }
        tag.putFloat("temperature", temperature);
        tag.putInt("fuel_ticks", fuelTicks);
        tag.putBoolean("doused", doused);
        if (!skimming.isEmpty()) {
            tag.put("skimming", skimming.save(registries));
        }
        tag.putLong("last_calendar_tick", lastCalendarTick);
        tag.putLong("last_day_time", lastDayTime);
        tag.put(CHARGE_KEY, ContainerHelper.saveAllItems(new CompoundTag(), charge, registries));
        tag.put(FUEL_KEY, ContainerHelper.saveAllItems(new CompoundTag(), fuel, registries));
        tag.put(REAGENT_KEY, ContainerHelper.saveAllItems(new CompoundTag(), reagent, registries));
        tag.put(BATH_KEY, bath.serializeNBT());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        phase = Phase.valueOf(tag.getString("phase"));
        timer = tag.getInt("timer");
        lining = tag.getInt("lining");
        reagentCredit = tag.getDouble("reagent_credit");
        slagWaiting = tag.getInt("slag_waiting");
        slagPiled = tag.getInt("slag_piled");
        pourLeft = tag.getInt("pour_left");
        pourTable = BlockPos.of(tag.getLong("pour_table"));
        pourSide = Direction.from3DDataValue(tag.getInt("pour_side"));
        heapSeed = tag.contains("heap_seed") ? ItemStack.parseOptional(registries, tag.getCompound("heap_seed")) : ItemStack.EMPTY;
        temperature = tag.getFloat("temperature");
        fuelTicks = tag.getInt("fuel_ticks");
        doused = tag.getBoolean("doused");
        skimming = tag.contains("skimming")
                ? ItemStack.parseOptional(registries, tag.getCompound("skimming")) : ItemStack.EMPTY;
        // A furnace saved before the fire kept a clock has no answer, and the honest one is "start from now":
        // guessing at the missing span would burn a firing that was never lit.
        lastCalendarTick = tag.contains("last_calendar_tick") ? tag.getLong("last_calendar_tick") : -1;
        lastDayTime = tag.getLong("last_day_time");
        ContainerHelper.loadAllItems(tag.getCompound(CHARGE_KEY), charge, registries);
        ContainerHelper.loadAllItems(tag.getCompound(FUEL_KEY), fuel, registries);
        ContainerHelper.loadAllItems(tag.getCompound(REAGENT_KEY), reagent, registries);
        bath.deserializeNBT(tag.getCompound(BATH_KEY));
    }
}
