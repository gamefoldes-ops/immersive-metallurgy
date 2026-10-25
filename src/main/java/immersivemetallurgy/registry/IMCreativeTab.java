package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A creative tab of our own, because without one the flawed metals have no home in the creative menu and
 * read as missing rather than as untabbed. It takes every item our namespace registers,
 * so a new metal appears here the moment it exists.
 */
public final class IMCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ImmersiveMetallurgy.MODID))
                    .icon(() -> new ItemStack(IMItems.FLAWED_INGOTS.get(0).get()))
                    .displayItems((parameters, output) -> {
                        for (Item item : BuiltInRegistries.ITEM) {
                            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                            if (ImmersiveMetallurgy.MODID.equals(id.getNamespace())) {
                                output.accept(new ItemStack(item));
                            }
                        }
                    })
                    .build());

    private IMCreativeTab() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}
