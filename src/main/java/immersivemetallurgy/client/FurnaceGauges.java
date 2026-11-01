package immersivemetallurgy.client;

import immersivemetallurgy.menu.RefiningFurnaceMenu;
import immersivemetallurgy.registry.IMFluids;

import net.dries007.tfc.client.RenderHelpers;
import net.dries007.tfc.common.component.heat.Heat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The four wells the furnace's window is read from, drawn the way TFC draws its own: filled with the sprite of
 * whatever is actually in them, so the bath shows bronze as bronze and slag as slag rather than as a number to look
 * up. Metal sits at the bottom of the tank and the slag floats above it, because that is the order the whole
 * machine depends on, and the height the slag channel sits at is drawn across the tank for the same reason.
 *
 * The coordinates live here and the texture generator writes the frames to the same numbers, because a gauge whose
 * well is drawn somewhere else than it is filled is the kind of bug only a player sees.
 */
public final class FurnaceGauges {
    public static final int BATH = 8;
    public static final int HEAT = 32;
    public static final int PLATE = 48;
    public static final int TAP = 60;
    public static final int WELL_Y = 18;
    public static final int WELL_HEIGHT = 40;
    public static final int BATH_WIDTH = 20;
    public static final int HEAT_WIDTH = 12;
    public static final int PLATE_WIDTH = 8;
    public static final int TAP_WIDTH = 8;
    /** The heat well is taller than the gauges beside it because it is a scale, not a level. */
    public static final int HEAT_Y = 12;
    public static final int HEAT_HEIGHT = 50;

    /**
     * TFC's crucible sheet: the ramp is the temperature scale itself (white at brilliant white down to black at
     * cold), and the 15x5 sprite beside it is the black pointer the game parks at however hot the thing is.
     * Both are drawn as they are, because a furnace that reads as hot as a crucible should look as hot as it.
     */
    private static final ResourceLocation TFC_CRUCIBLE_GUI =
            ResourceLocation.fromNamespaceAndPath("tfc", "textures/gui/crucible.png");
    private static final int RAMP_X = 11;
    private static final int RAMP_V = 83;
    private static final int RAMP_WIDTH = 7;
    private static final int MARK_X = 176;
    private static final int MARK_V = 0;
    private static final int MARK_WIDTH = 15;
    private static final int MARK_HEIGHT = 5;
    /** What {@link Heat#scaleTemperatureForGui} counts to at brilliant white, in its own pixels. */
    private static final int GUI_SCALE = 51;

    private static final int PLATE_FULL = 0xFFB02E2E;
    private static final int PLATE_DUST = 0xFF6B7F3A;
    private static final int TAP_RUN = 0xFFE0A030;

    private FurnaceGauges() {
    }

    public static void draw(GuiGraphics graphics, RefiningFurnaceMenu menu, int left, int top) {
        drawBath(graphics, menu, left, top);
        drawHeat(graphics, menu, left, top);
        drawPlate(graphics, menu, left, top);
        drawTap(graphics, menu, left, top);
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
        graphics.blit(TFC_CRUCIBLE_GUI, left + HEAT + (HEAT_WIDTH - RAMP_WIDTH) / 2, top + HEAT_Y,
                RAMP_X, RAMP_V, RAMP_WIDTH, HEAT_HEIGHT);
        int marked = Math.min(HEAT_HEIGHT - 1,
                Math.round(Heat.scaleTemperatureForGui(menu.temperature()) * (HEAT_HEIGHT - 1) / (float) GUI_SCALE));
        graphics.blit(TFC_CRUCIBLE_GUI, left + HEAT + (HEAT_WIDTH - MARK_WIDTH) / 2,
                top + HEAT_Y + HEAT_HEIGHT - 1 - marked - MARK_HEIGHT / 2,
                MARK_X, MARK_V, MARK_WIDTH, MARK_HEIGHT);
        int pour = Math.min(HEAT_HEIGHT, Math.round(HEAT_HEIGHT * menu.pourHeat() / (float) menu.fireCeiling()));
        if (pour > 0) {
            int y = top + HEAT_Y + HEAT_HEIGHT - pour;
            graphics.fill(left + HEAT - 1, y, left + HEAT + HEAT_WIDTH + 1, y + 1, 0xFF1A1410);
        }
    }

    /** How full the slag outlet is — in the plate if there is one, in the frozen heap if there is not. */
    private static void drawPlate(GuiGraphics graphics, RefiningFurnaceMenu menu, int left, int top) {
        int capacity = menu.slagCapacity();
        if (capacity <= 0) {
            return;
        }
        int filled = Math.min(WELL_HEIGHT, Math.round(WELL_HEIGHT * menu.slagOutletUnits() / (float) capacity));
        if (filled > 0) {
            graphics.fill(left + PLATE, top + WELL_Y + WELL_HEIGHT - filled,
                    left + PLATE + PLATE_WIDTH, top + WELL_Y + WELL_HEIGHT,
                    menu.slagOutletUnits() >= capacity ? PLATE_FULL : PLATE_DUST);
        }
    }

    /** How far the pour in flight has come: the metal that has left the bath and not reached the mould yet. */
    private static void drawTap(GuiGraphics graphics, RefiningFurnaceMenu menu, int left, int top) {
        int offer = Math.max(1, menu.pourMb());
        int gone = offer - menu.tapRemaining();
        int filled = Math.min(WELL_HEIGHT, Math.round(WELL_HEIGHT * gone / (float) offer));
        if (menu.tapRemaining() > 0 && filled > 0) {
            graphics.fill(left + TAP, top + WELL_Y + WELL_HEIGHT - filled,
                    left + TAP + TAP_WIDTH, top + WELL_Y + WELL_HEIGHT, TAP_RUN);
        }
    }

    public static List<Component> bathTooltip(RefiningFurnaceMenu menu) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.immersive_metallurgy.furnace.gauge.bath",
                menu.metalMb(), menu.slagMb(), menu.capacity()));
        lines.add(Component.translatable("screen.immersive_metallurgy.furnace.gauge.channel", menu.drainThreshold())
                .withStyle(menu.volume() >= menu.drainThreshold() ? ChatFormatting.GREEN : ChatFormatting.RED));
        return lines;
    }

    public static List<Component> heatTooltip(RefiningFurnaceMenu menu) {
        return List.of(Component.translatable("screen.immersive_metallurgy.furnace.gauge.heat",
                menu.temperature(), menu.pourHeat(), menu.fireCeiling()));
    }

    public static List<Component> plateTooltip(RefiningFurnaceMenu menu) {
        return List.of(Component.translatable("screen.immersive_metallurgy.furnace.gauge.plate",
                menu.slagOutletUnits(), menu.slagCapacity()));
    }

    public static List<Component> tapTooltip(RefiningFurnaceMenu menu) {
        return List.of(Component.translatable("screen.immersive_metallurgy.furnace.gauge.tap",
                menu.tapRemaining(), menu.pourMb()));
    }

    public static boolean overBath(int mouseX, int mouseY, int left, int top) {
        return inside(mouseX, mouseY, left + BATH, top + WELL_Y, BATH_WIDTH, WELL_HEIGHT);
    }

    public static boolean overHeat(int mouseX, int mouseY, int left, int top) {
        return inside(mouseX, mouseY, left + HEAT, top + HEAT_Y, HEAT_WIDTH, HEAT_HEIGHT);
    }

    public static boolean overPlate(int mouseX, int mouseY, int left, int top) {
        return inside(mouseX, mouseY, left + PLATE, top + WELL_Y, PLATE_WIDTH, WELL_HEIGHT);
    }

    public static boolean overTap(int mouseX, int mouseY, int left, int top) {
        return inside(mouseX, mouseY, left + TAP, top + WELL_Y, TAP_WIDTH, WELL_HEIGHT);
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
