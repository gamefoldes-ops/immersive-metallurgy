package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.menu.RefiningFurnaceMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The furnace's one window. It is registered rather than drawn by hand because the hearth has to be openable
 * from a block entity, and a menu is the only thing that gets numbers to the client and back.
 */
public final class IMMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ImmersiveMetallurgy.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<RefiningFurnaceMenu>> REFINING_FURNACE =
            MENU_TYPES.register("refining_hearth", () -> new MenuType<>(RefiningFurnaceMenu::new, FeatureFlagSet.of()));

    private IMMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENU_TYPES.register(modEventBus);
    }
}
