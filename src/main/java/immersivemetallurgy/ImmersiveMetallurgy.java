package immersivemetallurgy;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import immersivemetallurgy.registry.IMBlocks;
import immersivemetallurgy.registry.IMCapabilities;
import immersivemetallurgy.registry.IMBlockEntities;
import immersivemetallurgy.registry.IMCreativeTab;
import immersivemetallurgy.registry.IMDataComponents;
import immersivemetallurgy.registry.IMFluids;
import immersivemetallurgy.registry.IMItemModifiers;
import immersivemetallurgy.registry.IMItems;
import immersivemetallurgy.registry.IMMenus;
import immersivemetallurgy.registry.IMRecipes;

import net.dries007.tfc.common.blockentities.TFCBlockEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

@Mod(ImmersiveMetallurgy.MODID)
public class ImmersiveMetallurgy {
    public static final String MODID = "immersive_metallurgy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ImmersiveMetallurgy(IEventBus modEventBus) {
        IMBlocks.register(modEventBus);
        IMBlockEntities.register(modEventBus);
        IMCreativeTab.register(modEventBus);
        IMDataComponents.register(modEventBus);
        IMFluids.register(modEventBus);
        IMItemModifiers.register(modEventBus);
        IMItems.register(modEventBus);
        IMMenus.register(modEventBus);
        IMRecipes.register(modEventBus);

        modEventBus.addListener(IMCapabilities::register);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::widenTfcEntityTypes);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Immersive Metallurgy common setup");
    }

    /**
     * The combined cells keep TFC's own block entities — the mold table under the casting table, the channel under
     * the launder-channel — so TFC's flow logic finds them by type and fills them. Each type only accepts TFC's own
     * block by default, and creating its entity on ours throws in validateBlockState, so our blocks are added to the
     * types' valid blocks here, which is the supported way to widen them. The channel entity must stay TFC's own
     * type: TFC spreads flow with getBlockEntity(pos, CHANNEL), which only answers for an entity of that type.
     */
    private void widenTfcEntityTypes(BlockEntityTypeAddBlocksEvent event) {
        event.modify(TFCBlockEntities.MOLD_TABLE.get(), IMBlocks.CASTING_TABLE.get());
        event.modify(TFCBlockEntities.CHANNEL.get(), IMBlocks.LAUNDER_CHANNEL.get());
    }
}
