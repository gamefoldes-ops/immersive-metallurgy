package immersivemetallurgy.registry;

import immersivemetallurgy.ImmersiveMetallurgy;

import net.dries007.tfc.common.blocks.MoltenFluidBlock;
import net.dries007.tfc.common.fluids.MoltenFluid;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Molten fluids that TFC cannot describe: the inert slag term used as the purity denominator,
 * and one fluid per flawed alloy. They exist so that composition survives a cast, because TFC
 * keys both its alloys and its casting recipes on a single {@link Fluid}.
 */
public final class IMFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ImmersiveMetallurgy.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, ImmersiveMetallurgy.MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ImmersiveMetallurgy.MODID);

    private static final List<Molten> ALL = new ArrayList<>();

    public static final Molten SLAG = register("slag", "slag", 0xFF5C5449);

    static {
        for (IMAlloys alloy : IMAlloys.ALL) {
            register(alloy.flawedFluidPath(), "flawed_" + alloy.key(), alloy.tint());
        }
    }

    private IMFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
        BLOCKS.register(modEventBus);
    }

    public static List<Molten> all() {
        return ALL;
    }

    private static Molten register(String path, String langName, int tint) {
        Molten molten = new Molten(path, langName, tint);
        molten.type = FLUID_TYPES.register(path, () -> new FluidType(moltenLike(langName)));
        molten.source = FLUIDS.register(path, () -> new MoltenFluid.Source(molten.properties()));
        molten.flowing = FLUIDS.register(path + "_flowing", () -> new MoltenFluid.Flowing(molten.properties()));
        molten.block = BLOCKS.register("fluid/" + path, () -> new MoltenFluidBlock(molten::sourceFluid, blockLike()));
        ALL.add(molten);
        return molten;
    }

    private static FluidType.Properties moltenLike(String langName) {
        return FluidType.Properties.create()
                .descriptionId("fluid." + ImmersiveMetallurgy.MODID + "." + langName)
                .temperature(1200)
                .density(3000)
                .viscosity(6000)
                .motionScale(0.014)
                .fallDistanceModifier(0F)
                .canExtinguish(true)
                .canHydrate(false)
                .supportsBoating(false)
                .canSwim(false)
                .canDrown(false)
                .lightLevel(4);
    }

    private static BlockBehaviour.Properties blockLike() {
        return BlockBehaviour.Properties.of().noCollission().instabreak().noLootTable();
    }

    public static final class Molten {
        private final String path;
        private final String langName;
        private final int tint;
        private DeferredHolder<FluidType, FluidType> type;
        private DeferredHolder<Fluid, MoltenFluid.Source> source;
        private DeferredHolder<Fluid, MoltenFluid.Flowing> flowing;
        private DeferredHolder<Block, MoltenFluidBlock> block;

        private Molten(String path, String langName, int tint) {
            this.path = path;
            this.langName = langName;
            this.tint = tint;
        }

        private BaseFlowingFluid.Properties properties() {
            return new BaseFlowingFluid.Properties(type, () -> source.get(), () -> flowing.get())
                    .block(() -> block.get())
                    .explosionResistance(100F);
        }

        public MoltenFluid.Source sourceFluid() {
            return source.get();
        }

        public Fluid fluid() {
            return source.get();
        }

        public FluidStack stack(int amount) {
            return new FluidStack(source.get(), amount);
        }

        public ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(ImmersiveMetallurgy.MODID, path);
        }

        public String langName() {
            return langName;
        }

        public int tint() {
            return tint;
        }

        public DeferredHolder<FluidType, FluidType> typeHolder() {
            return type;
        }
    }
}
