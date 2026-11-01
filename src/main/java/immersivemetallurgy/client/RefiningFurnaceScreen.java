package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.furnace.FurnaceShape;
import immersivemetallurgy.menu.RefiningFurnaceMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * The furnace's window, and the reason it can be trusted: four wells across the top, filled with what is actually
 * in them, and under them the lines that say what the firing is doing. It answers a hover with the numbers too long
 * to draw every frame, and it never names the metal, so it cannot be read as a mixing table — the alloy is decided
 * by what went in, and the only way to find out what that made is to tap it.
 */
public class RefiningFurnaceScreen extends AbstractContainerScreen<RefiningFurnaceMenu> {
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, "textures/gui/refining_furnace.png");
    private static final int WIDTH = 176;
    /** Tall enough for every line the window can have to say at once, each of them wrapped. */
    private static final int HEIGHT = 226;
    private static final int FIRST_LINE = 66;
    private static final int LINE = 10;
    private static final int TEXT_X = 8;
    private static final int TEXT_WIDTH = WIDTH - 16;

    public RefiningFurnaceScreen(RefiningFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = WIDTH;
        imageHeight = HEIGHT;
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
        graphics.drawString(font, title, leftPos + 8, topPos + 6, 0xFFFFFF, true);
        int y = FIRST_LINE;
        for (Component line : lines()) {
            // A sentence is drawn in as many rows as the panel needs: the furnace's warnings are the kind a player
            // reads once and then acts on, and a clipped one is worse than a tall window.
            for (FormattedCharSequence wrapped : font.split(line, TEXT_WIDTH)) {
                graphics.drawString(font, wrapped, leftPos + TEXT_X, topPos + y, 0xCFCFCF, true);
                y += LINE;
            }
        }
        renderWellTooltip(graphics, mouseX, mouseY);
    }

    private void renderWellTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.formed()) {
            renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (FurnaceGauges.overBath(mouseX, mouseY, leftPos, topPos)) {
            show(graphics, FurnaceGauges.bathTooltip(menu), mouseX, mouseY);
        } else if (FurnaceGauges.overHeat(mouseX, mouseY, leftPos, topPos)) {
            show(graphics, FurnaceGauges.heatTooltip(menu), mouseX, mouseY);
        } else if (FurnaceGauges.overPlate(mouseX, mouseY, leftPos, topPos)) {
            show(graphics, FurnaceGauges.plateTooltip(menu), mouseX, mouseY);
        } else if (FurnaceGauges.overTap(mouseX, mouseY, leftPos, topPos)) {
            show(graphics, FurnaceGauges.tapTooltip(menu), mouseX, mouseY);
        } else {
            renderTooltip(graphics, mouseX, mouseY);
        }
    }

    private void show(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
        graphics.renderTooltip(font, lines.stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
    }

    private List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        if (!menu.formed()) {
            String fault = FurnaceShape.Fault.values()[menu.fault()].key();
            lines.add(Component.translatable("screen." + ImmersiveMetallurgy.MODID + ".furnace.fault." + fault)
                    .withStyle(ChatFormatting.RED));
            return lines;
        }
        lines.add(Component.translatable("screen.immersive_metallurgy.furnace.volume",
                menu.volume(), menu.capacity()));
        lines.add(Component.translatable("screen.immersive_metallurgy.furnace.split",
                menu.metalMb(), menu.slagMb())
                .withStyle(menu.slagPercent() > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        lines.add(Component.translatable("screen.immersive_metallurgy.furnace.tap", menu.pourMb()));
        // The mould is the one thing in this window that is not a number, because it is the one thing a player
        // can go and put right without waiting for anything.
        lines.add(Component.translatable("screen.immersive_metallurgy.furnace."
                + (menu.hasMold() ? "mold_set" : "mold_missing"))
                .withStyle(menu.hasMold() ? ChatFormatting.GREEN : ChatFormatting.RED));
        lines.add(Component.translatable("screen.immersive_metallurgy.furnace.reagent", menu.reagentUnits()));
        if (menu.slagOutletFrozen()) {
            lines.add(Component.translatable("screen.immersive_metallurgy.furnace.slag_frozen")
                    .withStyle(ChatFormatting.RED));
        } else if (menu.slagOutletBlocked()) {
            lines.add(Component.translatable("screen.immersive_metallurgy.furnace.slag_blocked")
                    .withStyle(ChatFormatting.RED));
        }
        if (menu.tapRemaining() > 0) {
            lines.add(Component.translatable("screen.immersive_metallurgy.furnace.tapping", menu.tapRemaining())
                    .withStyle(ChatFormatting.GOLD));
        }
        if (menu.lining() > 0) {
            lines.add(Component.translatable("screen.immersive_metallurgy.furnace.lining", menu.lining())
                    .withStyle(ChatFormatting.RED));
        }
        lines.add(phase());
        return lines;
    }

    /**
     * The firing's own state. Time is counted the way TFC counts it — 1000 ticks to a game hour — because the
     * furnace works on game ticks and not on the wall clock, and a laggy server should not make it slower.
     */
    private Component phase() {
        int temperature = menu.temperature();
        ChatFormatting hot = temperature >= 1000 ? ChatFormatting.RED : ChatFormatting.GRAY;
        MutableComponent line = switch (menu.phase()) {
            case 1 -> Component.translatable("screen.immersive_metallurgy.furnace.melting",
                    temperature, menu.chargeLeft()).withStyle(ChatFormatting.GOLD);
            case 2 -> Component.translatable("screen.immersive_metallurgy.furnace.boiling",
                    temperature, clock(menu.ticksLeft())).withStyle(ChatFormatting.GOLD);
            case 3 -> Component.translatable("screen.immersive_metallurgy.furnace.cooling", temperature)
                    .withStyle(hot);
            default -> Component.translatable("screen.immersive_metallurgy.furnace.cold", temperature)
                    .withStyle(ChatFormatting.GRAY);
        };
        // What the phase word describes is the bath. A furnace whose boil is over but which still has ten minutes
        // of charcoal in it is not "cooling" in the sense a player acts on, so the fire says for itself how long
        // it has left beside the phase it is in. And while the charge is still going in, the number a player is
        // deciding against is the boil that has not started yet, so that clock rides on the melting line too.
        if (menu.phase() == 1) {
            line = line.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                    .append(Component.translatable("screen.immersive_metallurgy.furnace.boil_next",
                                    clock(menu.boilTicks())).withStyle(ChatFormatting.GRAY));
        }
        return menu.fireSeconds() > 0
                ? line.append(Component.literal(" · ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("screen.immersive_metallurgy.furnace.fire_left",
                                        clock(menu.fireSeconds() * 20)).withStyle(ChatFormatting.GRAY))
                : line;
    }

    /**
     * What is left of the boil, in the minutes and seconds the player is actually short of. Counted off the world's
     * clock at twenty ticks a second, because that is the unit a person waiting in front of a machine keeps time
     * in — "3.4 hours" asked for an arithmetic nobody does while a tap is running.
     */
    private static String clock(int ticks) {
        int left = Math.max(0, ticks);
        return left / 1200 + ":" + String.format("%02d", (left % 1200) / 20);
    }
}
