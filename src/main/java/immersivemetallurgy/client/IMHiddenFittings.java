package immersivemetallurgy.client;

import immersivemetallurgy.ImmersiveMetallurgy;
import immersivemetallurgy.registry.IMItems;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/**
 * Taking the furnace's slag plate out of every menu there is.
 *
 * It is a fitting the furnace lays in its own slag outlet and takes back out of no hand, so a creative button
 * for it would offer a thing the player cannot use, and a JEI entry would promise a recipe that makes it — the
 * first search would end with a plate in the inventory and a furnace that will not accept it back. Removing it
 * from the tabs is enough for both: JEI builds its ingredient list out of the same creative tab contents, so a
 * thing no menu shows is a thing no recipe viewer lists either.
 */
@EventBusSubscriber(modid = ImmersiveMetallurgy.MODID, value = Dist.CLIENT)
public final class IMHiddenFittings {

    private IMHiddenFittings() {
    }

    @SubscribeEvent
    public static void hideFittings(BuildCreativeModeTabContentsEvent event) {
        // Built here, not in a field: NeoForge loads this class while the mod is still being constructed, before
        // any item exists in the registry, and an ItemStack of a plate asked for that early is a crash.
        event.remove(new ItemStack(IMItems.SLAG_PLATE.get()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
