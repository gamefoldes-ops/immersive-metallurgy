package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.furnace.FurnaceShape;
import immersivemetallurgy.menu.RefiningFurnaceMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The furnace's window, and the reason it can be trusted: three wells across the top, filled with what is actually
 * in them, and under them a word or two for what the firing is doing. It carries no millilitres and no clock — the
 * furnace is read by eye and by trial, the way a bloomery is — and it never names the metal, so it cannot be read
 * as a mixing table: the alloy is decided by what went in, and the only way to find out what that made is to tap it.
 */
public class RefiningFurnaceScreen extends AbstractContainerScreen<RefiningFurnaceMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "textures/gui/refining_furnace.png");
    private static final int WIDTH = 176;
    /** TFC blast furnace's own height: machine area on top, the player's inventory grid below it. */
    private static final int HEIGHT = 186;
    private static final int FIRST_LINE = 22;
    private static final int LINE = 10;
    /** The readout lives in the free column right of the meters; the long numbers stay on the hovers. */
    private static final int TEXT_X = 92;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X - 8;
    private static final int TEXT_BOTTOM = 92;

    public RefiningFurnaceScreen(RefiningFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
        inventoryLabelY = 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, WIDTH, HEIGHT);
        if (menu.formed()) {
            FurnaceGauges.draw(graphics, menu, leftPos, topPos);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        int y = FIRST_LINE;
        rows:
        for (Component line : lines()) {
            // The readout shares the machine area with the meters, so it is clipped where the inventory grid begins:
            // warnings come first and a long one that will not fit is dropped rather than drawn over the slots.
            for (FormattedCharSequence wrapped : font.split(line, TEXT_WIDTH)) {
                if (y + LINE > TEXT_BOTTOM) {
                    break rows;
                }
                graphics.drawString(font, wrapped, leftPos + TEXT_X, topPos + y, 0x404040, false);
                y += LINE;
            }
        }
        renderWellTooltip(graphics, mouseX, mouseY);
    }

    private void renderWellTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (overStatus(mouseX, mouseY)) {
            show(graphics, statusTooltip(), mouseX, mouseY);
            return;
        }
        if (!menu.formed()) {
            renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (FurnaceGauges.overHeat(mouseX, mouseY, leftPos, topPos)) {
            show(graphics, FurnaceGauges.heatTooltip(menu), mouseX, mouseY);
        } else if (FurnaceGauges.overCoal(mouseX, mouseY, leftPos, topPos)) {
            show(graphics, FurnaceGauges.coalTooltip(menu), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    /** The status word occupies the first readout line; hovering it is how an unformed stack explains itself. */
    private boolean overStatus(int mouseX, int mouseY) {
        return mouseX >= leftPos + TEXT_X && mouseX < leftPos + TEXT_X + TEXT_WIDTH
                && mouseY >= topPos + FIRST_LINE && mouseY < topPos + FIRST_LINE + LINE;
    }

    private List<Component> statusTooltip() {
        if (menu.formed()) {
            return List.of();
        }
        String fault = FurnaceShape.Fault.values()[menu.fault()].key();
        return List.of(Component.translatable("screen." + ImmersiveMetallurgy.MODID + ".furnace.fault." + fault)
                .withStyle(ChatFormatting.RED));
    }

    private void show(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
        if (lines.isEmpty()) {
            return;
        }
        graphics.renderTooltip(font, lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
    }

    private List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        // One word for whether the stack stands at all; the reasons it does not live on the hover, not in a paragraph.
        lines.add(Component.translatable("screen." + ImmersiveMetallurgy.MODID + ".furnace."
                + (menu.formed() ? "status_formed" : "status_not_formed"))
                .withStyle(menu.formed() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!menu.formed()) {
            return lines;
        }
        // What is actionable — a frozen or blocked slag outlet, a thick lining — is said in words, never in
        // millilitres: the furnace is read by eye and by trial, the way a bloomery is, not by a readout to sum.
        if (menu.slagOutletFrozen()) {
            lines.add(Component.translatable("screen.immersive_metallurgy.furnace.slag_frozen")
                    .withStyle(ChatFormatting.RED));
        } else if (menu.slagOutletBlocked()) {
            lines.add(Component.translatable("screen.immersive_metallurgy.furnace.slag_blocked")
                    .withStyle(ChatFormatting.RED));
        }
        if (menu.lining() > 0) {
            lines.add(Component.translatable("screen.immersive_metallurgy.furnace.lining")
                    .withStyle(ChatFormatting.RED));
        }
        Component state = phase();
        if (state != null) {
            lines.add(state);
        }
        return lines;
    }

    /**
     * The firing's own state as a single word, with no degrees and no clock: the gauges carry the heat and the
     * coal, and the bath carries the rest. While the charge is still going in there is nothing to say at all —
     * the fire climbing and the coal draining are the whole of it, and a "melting" line only repeated them.
     */
    private Component phase() {
        return switch (menu.phase()) {
            case 2 -> Component.translatable("screen.immersive_metallurgy.furnace.boiling")
                    .withStyle(ChatFormatting.GOLD);
            case 3 -> Component.translatable("screen.immersive_metallurgy.furnace.cooling")
                    .withStyle(ChatFormatting.RED);
            case 0 -> Component.translatable("screen.immersive_metallurgy.furnace.cold")
                    .withStyle(ChatFormatting.GRAY);
            default -> null;
        };
    }
}
