package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.menu.RefiningFurnaceMenu;
import immersivemetallurgy.registry.IMFluids;

import com.mojang.blaze3d.systems.RenderSystem;
import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.common.component.heat.Heat;
import net.dries007.tfc.config.ItemTemperatureDisplayStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * The three wells the furnace's window is read from, drawn the way TFC draws its own: filled with the sprite of
 * whatever is actually in them, so the bath shows bronze as bronze and slag as slag rather than as a number to look
 * up. Metal sits at the bottom of the tank and the slag floats above it, because that is the order the whole
 * machine depends on, and the height the slag channel sits at is drawn across the tank for the same reason.
 *
 * The coordinates live here and the texture generator writes the frames to the same numbers, because a gauge whose
 * well is drawn somewhere else than it is filled is the kind of bug only a player sees.
 */
public final class FurnaceGauges {
    public static final int BATH = 42;
    public static final int HEAT = 8;
    public static final int COAL = 64;
    public static final int WELL_Y = 22;
    public static final int WELL_HEIGHT = 66;
    public static final int BATH_WIDTH = 16;
    public static final int HEAT_WIDTH = 14;
    public static final int COAL_WIDTH = 8;
    /** The heat gauge is TFC blast furnace's own left ramp, copied into our sheet; only the pointer is drawn. */
    public static final int HEAT_Y = 18;
    public static final int HEAT_HEIGHT = 70;

    /**
     * Our own sheet, which carries TFC blast furnace's heat ramp in the background and keeps the black 15x5 pointer
     * at (176,0) exactly where TFC keeps it, so the pointer is blitted the same way the blast furnace blits it.
     */
    private static final ResourceLocation SHEET =
            ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "textures/gui/refining_furnace.png");
    private static final int MARK_X = 176;
    private static final int MARK_V = 0;
    private static final int MARK_WIDTH = 15;
    private static final int MARK_HEIGHT = 5;
    /** The ramp's cold end, in panel pixels: the pointer rides up from here as the fire heats. */
    private static final int HEAT_BOTTOM = 76;
    /** What {@link Heat#scaleTemperatureForGui} counts to at brilliant white, in its own pixels. */
    private static final int GUI_SCALE = 51;

    /** The coal gauge is a heap of pieces, drawn as the dark of the fuel itself rather than as a liquid. */
    private static final int COAL_COLOR = 0xFF2E2A26;

    private FurnaceGauges() {
    }

    public static void draw(GuiGraphics graphics, RefiningFurnaceMenu menu, int left, int top) {
        drawBath(graphics, menu, left, top);
        drawHeat(graphics, menu, left, top);
        drawCoal(graphics, menu, left, top);
    }

    /** The bath: metal from the bottom, the slag it carries floating on top of it, and the channel marked across. */
    private static void drawBath(GuiGraphics graphics, RefiningFurnaceMenu menu, int left, int top) {
        int capacity = menu.capacity();
        if (capacity <= 0) {
            return;
        }
        int metal = Math.min(WELL_HEIGHT, px(menu.metalMb(), capacity));
        int slag = Math.min(WELL_HEIGHT - metal, px(menu.slagMb(), capacity));
        if (metal > 0) {
            Fluid fluid = BuiltInRegistries.FLUID.byId(menu.bathFluidId());
            fill(graphics, fluid, left + BATH, top + WELL_Y + WELL_HEIGHT - metal, BATH_WIDTH, metal);
        }
        if (slag > 0) {
            fill(graphics, IMFluids.SLAG.fluid(), left + BATH, top + WELL_Y + WELL_HEIGHT - metal - slag,
                    BATH_WIDTH, slag);
        }
        // TFC's fluid sprite binds the metal's colour into the global shader and never puts it back; left set, it
        // tints every later draw — the frame, the slots, the readout — the colour of whatever is in the bath.
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        // Where the slag channel sits. A bath below this line keeps its dirt, which is the one thing the height of
        // the stack was bought for and the one thing no line of text made obvious at a glance.
        int mark = px(menu.drainThreshold(), capacity);
        if (mark > 0 && mark < WELL_HEIGHT) {
            int y = top + WELL_Y + WELL_HEIGHT - mark;
            graphics.fill(left + BATH - 1, y, left + BATH + BATH_WIDTH + 1, y + 1, 0xFFE6D7A7);
        }
    }

    /**
     * How hot the fire stands, drawn the way the crucible draws it: the whole scale sits in the well at all
     * times, and the black pointer marks where on it this fire is. The column does not grow with the heat,
     * because the scale is absolute — the same pixels mean the same degrees here, in a crucible and beside a
     * glowing bloom — and a filling bar cannot tell you how much room is left above the metal.
     */
    private static void drawHeat(GuiGraphics graphics, RefiningFurnaceMenu menu, int left, int top) {
        int marked = Math.min(GUI_SCALE, Math.round(Heat.scaleTemperatureForGui(menu.temperature())));
        if (marked > 0) {
            graphics.blit(SHEET, left + HEAT, top + HEAT_BOTTOM - marked, MARK_X, MARK_V, MARK_WIDTH, MARK_HEIGHT);
        }
    }

    /**
     * The coal left in the hearth, drawn the way the blast furnace draws its fuel: a meter of pieces against the
     * box's capacity, the piece currently alight counted as the fraction of itself that has not burned yet. It is
     * the only place the remaining fire is shown — no clock beside it.
     */
    private static void drawCoal(GuiGraphics graphics, RefiningFurnaceMenu menu, int left, int top) {
        int capacity = menu.fuelCapacity();
        if (capacity <= 0) {
            return;
        }
        float pieces = menu.fuelCount() + menu.fuelBurnPercent() / 100F;
        int filled = Math.min(WELL_HEIGHT, Math.round(WELL_HEIGHT * pieces / capacity));
        if (filled > 0) {
            graphics.fill(left + COAL, top + WELL_Y + WELL_HEIGHT - filled,
                    left + COAL + COAL_WIDTH, top + WELL_Y + WELL_HEIGHT, COAL_COLOR);
        }
    }

    /** The fire read exactly the way a crucible reads it: TFC's colour word plus its sub-level stars, nothing else. */
    public static List<Component> heatTooltip(RefiningFurnaceMenu menu) {
        Component line = ItemTemperatureDisplayStyle.COLOR.formatColored(menu.temperature());
        return line == null ? List.of() : List.of(line);
    }

    public static List<Component> coalTooltip(RefiningFurnaceMenu menu) {
        return List.of(Component.translatable("screen.immersive_metallurgy.furnace.gauge.coal",
                menu.fuelCount(), menu.fuelCapacity()));
    }

    public static boolean overHeat(int mouseX, int mouseY, int left, int top) {
        return inside(mouseX, mouseY, left + HEAT, top + HEAT_Y, HEAT_WIDTH, HEAT_HEIGHT);
    }

    public static boolean overCoal(int mouseX, int mouseY, int left, int top) {
        return inside(mouseX, mouseY, left + COAL, top + WELL_Y, COAL_WIDTH, WELL_HEIGHT);
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return RenderHelpers.isInside(mouseX, mouseY, x, y, width, height);
    }

    /** Pixels of a 40-pixel well, so a bath that is half metal and half slag reads that way. */
    private static int px(int millilitres, int capacity) {
        if (millilitres <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(WELL_HEIGHT * millilitres / (float) capacity));
    }

    private static void fill(GuiGraphics graphics, Fluid fluid, int x, int y, int width, int height) {
        FluidStack stack = new FluidStack(fluid, 1000);
        RenderHelpers.fillAreaWithSprite(graphics, RenderHelpers.getAndBindFluidSprite(stack),
                x, y, width, height, 16, 16);
    }
}
