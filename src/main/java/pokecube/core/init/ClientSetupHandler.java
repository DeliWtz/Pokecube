package pokecube.core.init;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.model.BoatModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SignRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.registries.RegistryObject;
import pokecube.api.PokecubeAPI;
import pokecube.api.data.PokedexEntry;
import pokecube.api.utils.PokeType;
import pokecube.core.PokecubeCore;
import pokecube.core.PokecubeItems;
import pokecube.core.blocks.signs.GenericSignBlockEntity;
import pokecube.core.client.EventsHandlerClient;
import pokecube.core.client.Resources;
import pokecube.core.client.gui.blocks.Healer;
import pokecube.core.client.gui.blocks.PC;
import pokecube.core.client.gui.blocks.TMs;
import pokecube.core.client.gui.blocks.Trade;
import pokecube.core.client.gui.pokemob.GuiPokemob;
import pokecube.core.client.render.RenderMoves;
import pokecube.core.client.render.mobs.GenericBoatRenderer;
import pokecube.core.client.render.mobs.RenderEgg;
import pokecube.core.client.render.mobs.RenderNPC;
import pokecube.core.client.render.mobs.RenderPokecube;
import pokecube.core.client.render.mobs.RenderPokemob;
import pokecube.core.database.Database;
import pokecube.core.entity.boats.GenericBoat;
import pokecube.core.entity.boats.GenericBoat.BoatType;
import pokecube.core.inventory.healer.HealerContainer;
import pokecube.core.inventory.pc.PCContainer;
import pokecube.core.inventory.tms.TMContainer;
import pokecube.core.inventory.trade.TradeContainer;
import pokecube.core.items.ItemTM;
import pokecube.core.items.berries.BerryManager;
import pokecube.core.items.megastuff.ItemMegawearable;
import pokecube.core.items.pokemobeggs.ItemPokemobEgg;
import pokecube.core.moves.MovesUtils;
import pokecube.nbtedit.NBTEdit;
import pokecube.nbtedit.forge.ClientProxy;
import thut.lib.RegHelper;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD, modid = PokecubeCore.MODID, value = Dist.CLIENT)
public class ClientSetupHandler
{
    public static KeyMapping nextMob;
    public static KeyMapping nextMove;
    public static KeyMapping previousMob;
    public static KeyMapping previousMove;
    public static KeyMapping mobBack;
    public static KeyMapping mobAttack;
    public static KeyMapping mobStance;
    public static KeyMapping mobMegavolve;
    public static KeyMapping noEvolve;
    public static KeyMapping mobMove1;
    public static KeyMapping mobMove2;
    public static KeyMapping mobMove3;
    public static KeyMapping mobMove4;
    public static KeyMapping gzmove;
    public static KeyMapping mobUp;
    public static KeyMapping mobDown;
    public static KeyMapping throttleUp;
    public static KeyMapping throttleDown;
    public static KeyMapping arrangeGui;
    public static KeyMapping animateGui;

    static
    {
        int unk = InputConstants.UNKNOWN.getValue();

        nextMob = new KeyMapping("key.pokemob.next", GLFW.GLFW_KEY_RIGHT, "Pokecube");
        previousMob = new KeyMapping("key.pokemob.prev", GLFW.GLFW_KEY_LEFT, "Pokecube");
        nextMove = new KeyMapping("key.pokemob.move.next", GLFW.GLFW_KEY_DOWN, "Pokecube");
        previousMove = new KeyMapping("key.pokemob.move.prev", GLFW.GLFW_KEY_UP, "Pokecube");
        mobBack = new KeyMapping("key.pokemob.recall", GLFW.GLFW_KEY_R, "Pokecube");
        mobAttack = new KeyMapping("key.pokemob.attack", GLFW.GLFW_KEY_G, "Pokecube");
        mobStance = new KeyMapping("key.pokemob.stance", GLFW.GLFW_KEY_BACKSLASH, "Pokecube");
        mobMegavolve = new KeyMapping("key.pokemob.megaevolve", GLFW.GLFW_KEY_M, "Pokecube");
        noEvolve = new KeyMapping("key.pokemob.b", GLFW.GLFW_KEY_B, "Pokecube");

        mobMove1 = new KeyMapping("key.pokemob.move.1", unk, "Pokecube");
        mobMove2 = new KeyMapping("key.pokemob.move.2", unk, "Pokecube");
        mobMove3 = new KeyMapping("key.pokemob.move.3", unk, "Pokecube");
        mobMove4 = new KeyMapping("key.pokemob.move.4", unk, "Pokecube");

        mobUp = new KeyMapping("key.pokemob.up", GLFW.GLFW_KEY_SPACE, "Pokecube");
        mobDown = new KeyMapping("key.pokemob.down", GLFW.GLFW_KEY_LEFT_CONTROL, "Pokecube");
        throttleUp = new KeyMapping("key.pokemob.speed.up", GLFW.GLFW_KEY_LEFT_BRACKET, "Pokecube");
        throttleDown = new KeyMapping("key.pokemob.speed.down", GLFW.GLFW_KEY_RIGHT_BRACKET, "Pokecube");
        arrangeGui = new KeyMapping("key.pokemob.arrangegui", unk, "Pokecube");
        animateGui = new KeyMapping("key.pokemob.animategui", unk, "Pokecube");
        gzmove = new KeyMapping("key.pokemob.gzmove", unk, "Pokecube");
    }

    @SubscribeEvent
    public static void loaded(final FMLLoadCompleteEvent event)
    {
        RenderPokemob.register();
    }

    private static void registerKey(KeyMapping key, Object event)
    {
        ClientRegistry.registerKeyBinding(key);
    }

    public static void registerKeybinds(Object event)
    {
        PokecubeAPI.LOGGER.debug("Init Keybinds");
        registerKey(ClientSetupHandler.nextMob, event);
        registerKey(ClientSetupHandler.previousMob, event);
        registerKey(ClientSetupHandler.nextMove, event);
        registerKey(ClientSetupHandler.previousMove, event);
        registerKey(ClientSetupHandler.mobBack, event);
        registerKey(ClientSetupHandler.mobAttack, event);
        registerKey(ClientSetupHandler.mobStance, event);
        registerKey(ClientSetupHandler.mobMegavolve, event);
        registerKey(ClientSetupHandler.noEvolve, event);
        registerKey(ClientSetupHandler.mobMove1, event);
        registerKey(ClientSetupHandler.mobMove2, event);
        registerKey(ClientSetupHandler.mobMove3, event);
        registerKey(ClientSetupHandler.mobMove4, event);
        registerKey(ClientSetupHandler.mobUp, event);
        registerKey(ClientSetupHandler.mobDown, event);
        registerKey(ClientSetupHandler.throttleUp, event);
        registerKey(ClientSetupHandler.throttleDown, event);
        registerKey(ClientSetupHandler.arrangeGui, event);
        registerKey(ClientSetupHandler.animateGui, event);
        registerKey(ClientSetupHandler.gzmove, event);

        ClientProxy.NBTEditKey = new KeyMapping("NBTEdit Shortcut", InputConstants.UNKNOWN.getValue(),
                "key.categories.misc");
        ClientRegistry.registerKeyBinding(ClientProxy.NBTEditKey);
    }

    @SubscribeEvent
    public static void setupClient(final FMLClientSetupEvent event)
    {
        PokecubeAPI.LOGGER.debug("Pokecube Client Setup");

        // Register event handlers
        EventsHandlerClient.register();

        // Register keybinds
        registerKeybinds(event);

        // Forward this to PCEdit mod:
        NBTEdit.setupClient(event);

        // Register the gui side of the screens.
        PokecubeAPI.LOGGER.debug("Init Screen Factories");

        MenuScreens.register(MenuTypes.POKEMOB.get(), GuiPokemob::new);
        MenuScreens.register(MenuTypes.HEALER.get(), Healer<HealerContainer>::new);
        MenuScreens.register(MenuTypes.PC.get(), PC<PCContainer>::new);
        MenuScreens.register(MenuTypes.TRADE.get(), Trade<TradeContainer>::new);
        MenuScreens.register(MenuTypes.TMS.get(), TMs<TMContainer>::new);

        // Register mob rendering
        PokecubeAPI.LOGGER.debug("Init Mob Renderers");

        // Register the render layers
        for (final RegistryObject<Block> crop : BerryManager.berryCrops.values())
            ItemBlockRenderTypes.setRenderLayer(crop.get(), RenderType.cutoutMipped());
        for (final RegistryObject<Block> fruit : BerryManager.berryFruits.values())
            ItemBlockRenderTypes.setRenderLayer(fruit.get(), RenderType.cutoutMipped());
        for (final RegistryObject<Block> leaf : ItemGenerator.leaves.values())
            ItemBlockRenderTypes.setRenderLayer(leaf.get(), RenderType.cutoutMipped());
        for (final RegistryObject<Block> trapdoor : ItemGenerator.trapdoors.values())
            ItemBlockRenderTypes.setRenderLayer(trapdoor.get(), RenderType.cutoutMipped());
        for (final RegistryObject<Block> door : ItemGenerator.doors.values())
            ItemBlockRenderTypes.setRenderLayer(door.get(), RenderType.cutoutMipped());
        for (final RegistryObject<Block> berry : BerryManager.pottedBerries.values())
            ItemBlockRenderTypes.setRenderLayer(berry.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(PokecubeItems.NEST.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(PokecubeItems.DYNAMAX.get(), RenderType.cutoutMipped());

        ClientSetupHandler.registerLayerDefinition(ForgeHooksClient::registerLayerDefinition);

        event.enqueueWork(() -> {
            BerriesWoodType.register();
        });

        // FIXME Register config gui
    }

    @SubscribeEvent
    public static void registerRenderers(final RegisterRenderers event)
    {
        for (final PokedexEntry e : Database.getSortedFormes())
        {
            if (!e.stock) continue;
            final EntityType<? extends Mob> t = e.getEntityType();
            event.registerEntityRenderer(t, (manager) -> new RenderPokemob(e, manager));
        }
        event.registerEntityRenderer(EntityTypes.getPokecube(), RenderPokecube::new);
        event.registerEntityRenderer(EntityTypes.getMove(), RenderMoves::new);
        event.registerEntityRenderer(EntityTypes.getNpc(), RenderNPC::new);
        event.registerEntityRenderer(EntityTypes.getEgg(), RenderEgg::new);
        event.registerEntityRenderer(EntityTypes.getBoat(), GenericBoatRenderer::new);

        if (GenericSignBlockEntity.SIGN_TYPE != null)
            event.registerBlockEntityRenderer(GenericSignBlockEntity.SIGN_TYPE.get(), SignRenderer::new);
    }

    public static void registerLayerDefinition(final BiConsumer<ModelLayerLocation, Supplier<LayerDefinition>> consumer)
    {
        for (BoatType value : GenericBoat.getTypes())
        {
            String modid = RegHelper.getKey(value.item().get()).getNamespace();
            consumer.accept(GenericBoatRenderer.createBoatModelName(modid, value), BoatModel::createBodyModel);
        }
    }

    @SubscribeEvent
    public static void colourBlocks(final ColorHandlerEvent.Block event)
    {
        final Block qualotLeaves = BerryManager.berryLeaves.get(23).get();
        event.getBlockColors().register((state, reader, pos, tintIndex) -> {
            return reader != null && pos != null ? BiomeColors.getAverageFoliageColor(reader, pos)
                    : FoliageColor.getDefaultColor();
        }, qualotLeaves);
    }

    @SubscribeEvent
    public static void colourItems(final ColorHandlerEvent.Item event)
    {
        final Block qualotLeaves = BerryManager.berryLeaves.get(23).get();
        event.getItemColors().register((stack, tintIndex) -> {
            final BlockState blockstate = ((BlockItem) stack.getItem()).getBlock().defaultBlockState();
            return event.getBlockColors().getColor(blockstate, null, null, tintIndex);
        }, qualotLeaves);

        event.getItemColors().register((stack, tintIndex) -> {
            final PokeType type = PokeType.unknown;
            final PokedexEntry entry = ItemPokemobEgg.getEntry(stack);
            if (entry != null) return tintIndex == 0 ? entry.getType1().colour : entry.getType2().colour;
            return tintIndex == 0 ? type.colour : 0xFFFFFFFF;
        }, PokecubeItems.EGG.get());

        for (Item i : ItemMegawearable.INSTANCES)
        {
            event.getItemColors().register((stack, tintIndex) -> {
                if (!(stack.getItem() instanceof DyeableLeatherItem item)) return 0xFFFFFFFF;
                return tintIndex == 0 ? item.getColor(stack) : 0xFFFFFFFF;
            }, i);
        }

        event.getItemColors().register((stack, tintIndex) -> {
            String moveName = ItemTM.getMoveFromStack(stack);
            if (moveName == null) return 0xFFFFFFFF;
            var move = MovesUtils.getMove(moveName);
            if (move != null) return move.getType(null).colour; 
            return 0xFFFFFFFF;
        }, PokecubeItems.TM.get());
    }

    @SubscribeEvent
    public static void textureStitch(final TextureStitchEvent.Pre event)
    {
        if (!event.getAtlas().location().toString().equals("minecraft:textures/atlas/blocks.png")) return;
        PokecubeAPI.LOGGER.debug("Registering Pokecube Slot Textures");
        event.addSprite(Resources.SLOT_ICON_CUBE);
        event.addSprite(Resources.SLOT_ICON_TM);
        event.addSprite(Resources.SLOT_ICON_BOOK);
        event.addSprite(Resources.SLOT_ICON_BOTTLE);
        event.addSprite(Resources.SLOT_ICON_DNA);
        event.addSprite(Resources.SLOT_ICON_EGG);
    }
}
