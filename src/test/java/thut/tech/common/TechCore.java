package thut.tech.common;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import thut.core.common.ThutCore;
import thut.core.common.config.Config;
import thut.core.common.network.PacketHandler;
import thut.core.init.ThutCreativeTabs;
import thut.tech.Reference;
import thut.tech.common.blocks.lift.ControllerBlock;
import thut.tech.common.blocks.lift.ControllerTile;
import thut.tech.common.entity.EntityLift;
import thut.tech.common.handlers.ConfigHandler;
import thut.tech.common.items.ItemLinker;
import thut.tech.common.util.RecipeSerializers;

@Mod(value = Reference.MOD_ID)
public class TechCore
{
    public final static PacketHandler packets = new PacketHandler(new ResourceLocation(Reference.MOD_ID, "comms"),
            Reference.NETVERSION);

    public static final DeferredRegister<Item> ITEMS;
    public static final DeferredRegister<Block> BLOCKS;
    public static final DeferredRegister<EntityType<?>> ENTITY;
    public static final DeferredRegister<BlockEntityType<?>> TILEENTITY;

    public static final RegistryObject<Block> LIFTCONTROLLER;

    public static final RegistryObject<Item> LIFT;
    public static final RegistryObject<Item> LINKER;

    public static final RegistryObject<EntityType<EntityLift>> LIFTTYPE;

    public static final RegistryObject<BlockEntityType<ControllerTile>> CONTROLTYPE;

    public static final ConfigHandler config = new ConfigHandler(Reference.MOD_ID);

    static
    {
        TILEENTITY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Reference.MOD_ID);
        ENTITY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Reference.MOD_ID);
        BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Reference.MOD_ID);
        ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Reference.MOD_ID);

        LIFTTYPE = TechCore.ENTITY.register("lift", () -> new EntityLift.BlockEntityType<>(EntityLift::new));

        CONTROLTYPE = TechCore.TILEENTITY.register("controller",
                () -> BlockEntityType.Builder.of(ControllerTile::new, TechCore.LIFTCONTROLLER.get()).build(null));
        LIFTCONTROLLER = TechCore.BLOCKS.register("controller",
                () -> new ControllerBlock(Block.Properties.of().strength(3.5f).dynamicShape().noOcclusion()));

        LIFT = TechCore.ITEMS.register("lift", () -> new Item(new Item.Properties()));
        LINKER = TechCore.ITEMS.register("linker", () -> new ItemLinker(new Item.Properties()));

        for (final RegistryObject<Block> reg : TechCore.BLOCKS.getEntries())
            TechCore.ITEMS.register(reg.getId().getPath(), () -> new BlockItem(reg.get(), new Item.Properties()));
    }

    public TechCore()
    {
        ThutCore.FORGE_BUS.register(this);
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register recipe serializers
        RecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        TechCore.ITEMS.register(modEventBus);
        TechCore.BLOCKS.register(modEventBus);
        TechCore.TILEENTITY.register(modEventBus);
        TechCore.ENTITY.register(modEventBus);
        modEventBus.addListener(this::addCreative);

        // Register Config stuff
        Config.setupConfigs(TechCore.config, Reference.MOD_ID, Reference.MOD_ID);
    }

    void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey().equals(ThutCreativeTabs.UTILITIES_TAB.getKey()))
        {
            event.accept(LINKER);
            event.accept(LIFT);
            event.accept(LIFTCONTROLLER);
        }

        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES) && ThutCore.getConfig().itemsInCreativeTabs)
        {
            add(event, Items.WARPED_FUNGUS_ON_A_STICK, LINKER.get());
            add(event, LINKER.get(), LIFT.get());
        }

        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS) && ThutCore.getConfig().itemsInCreativeTabs)
        {
            add(event, Items.LODESTONE, LIFT.get());
            add(event, LIFT.get(), LIFTCONTROLLER.get());
        }
    }

    public static void add(BuildCreativeModeTabContentsEvent event, ItemLike afterItem, ItemLike item) {
        event.getEntries().putAfter(new ItemStack(afterItem), new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
