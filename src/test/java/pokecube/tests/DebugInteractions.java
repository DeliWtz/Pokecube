package pokecube.tests;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.feature.ConfiguredStructureFeature;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import pokecube.api.PokecubeAPI;
import pokecube.gimmicks.builders.builders.BuilderManager;
import pokecube.gimmicks.builders.builders.BuilderManager.BuildContext;
import pokecube.gimmicks.builders.builders.IBlocksBuilder;
import pokecube.gimmicks.builders.builders.IBlocksBuilder.PlaceInfo;
import pokecube.gimmicks.builders.builders.IBlocksClearer;
import thut.api.ThutCaps;
import thut.api.Tracker;
import thut.api.maths.Vector3;
import thut.api.world.IWorldTickListener;
import thut.api.world.WorldTickManager;
import thut.lib.TComponent;

@Mod.EventBusSubscriber
public class DebugInteractions
{
    public static class BuilderClearer implements IWorldTickListener
    {
        IBlocksClearer clearer;
        IBlocksBuilder builder;

        public BuilderClearer(IBlocksClearer c, IBlocksBuilder b)
        {
            this.clearer = c;
            this.builder = b;
        }

        @Override
        public void onTickEnd(ServerLevel level)
        {
            if (clearer != null)
            {
                clearer.setCreative(true);
                clearer.update(level);
            }
            builder.setCreative(true);
            builder.update(level);

            if (!builder.validBuilder())
            {
                WorldTickManager.removeWorldData(level.dimension(), this);
                PokecubeAPI.LOGGER.info("terminated structure!");
                return;
            }

            long end = System.currentTimeMillis() + 100;
            boolean ended = false;
            while (System.currentTimeMillis() < end)
            {
                // Check if we need to remove invalid blocks, do that first.
                if (!clearer.tryClear(level, (i) -> {})) continue;
                // Then check if we can place blocks.
                PlaceInfo placement = builder.getNextPlacement(level, null);
                if (!(ended = builder.tryPlace(placement, level, null))) continue;
                if (ended) break;
            }
            if (builder.validBuilder()) return;

            WorldTickManager.removeWorldData(level.dimension(), this);
            PokecubeAPI.LOGGER.info("Finished structure!");
        }
    }

    @SubscribeEvent
    public static void onBlockRightClick(final PlayerInteractEvent.RightClickBlock evt)
    {
        if (!(evt.getPlayer() instanceof ServerPlayer player)
                || !(evt.getPlayer().getLevel() instanceof ServerLevel level))
            return;
        boolean isStructureMaker = evt.getItemStack().getDisplayName().getString().contains("structure_maker");

        var te = level.getBlockEntity(evt.getPos());

        long tick = Tracker.instance().getTick();
        if (player.getPersistentData().getLong("__debug_interaction__") == tick) return;
        player.getPersistentData().putLong("__debug_interaction__", tick);

        if (te instanceof ChestBlockEntity chest && isStructureMaker)
        {
            IItemHandlerModifiable itemSource = (IItemHandlerModifiable) ThutCaps.getInventory(chest);
            BlockPos origin = evt.getPos();
            ItemStack key = itemSource.getStackInSlot(0);
            if (key.hasTag() && key.getOrCreateTag().get("pages") instanceof ListTag)
            {
                try
                {
                    var context = new BuildContext(level, origin);
                    var build = BuilderManager.fromInstructions(key, context);
                    if (build != null) WorldTickManager.addWorldData(level.dimension(),
                            new BuilderClearer(build.clearer(), build.builder()));
                }
                catch (Exception e)
                {
                    PokecubeAPI.LOGGER.error(e);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemRightClick(final PlayerInteractEvent.RightClickItem evt)
    {
        if (!(evt.getPlayer() instanceof ServerPlayer player)
                || !(evt.getPlayer().getLevel() instanceof ServerLevel level))
            return;
        boolean isStructureDebug = evt.getItemStack().getDisplayName().getString().contains("structure_debug");
        Vector3 v = new Vector3().set(player);
        if (isStructureDebug)
        {
            var registry = level.registryAccess().registryOrThrow(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY);
            var list = registry.stream().toList();
            List<ResourceLocation> found = Lists.newArrayList();
            List<ResourceLocation> not_found = Lists.newArrayList();
            Map<ResourceLocation, Pair<Integer, BlockPos>> found_map = Maps.newHashMap();
            thut.lib.ChatHelper.sendSystemMessage(player, TComponent.literal("Searching for Structures!"));
            for (var feature : list)
            {
                var name = registry.getKey(feature);
                if (name.toString().startsWith("pokecube"))
                {
                    thut.lib.ChatHelper.sendSystemMessage(player, TComponent.literal("Checking " + name));
                    final ResourceKey<ConfiguredStructureFeature<?, ?>> structure = ResourceKey
                            .create(Registry.CONFIGURED_STRUCTURE_FEATURE_REGISTRY, name);
                    var holder = registry.getHolderOrThrow(structure);
                    HolderSet<ConfiguredStructureFeature<?, ?>> holderset = HolderSet.direct(holder);
                    Pair<BlockPos, Holder<ConfiguredStructureFeature<?, ?>>> thing = level.getChunkSource()
                            .getGenerator().findNearestMapFeature(level, holderset, v.getPos(), 100, false);
                    if (thing != null)
                    {
                        found.add(name);
                        found_map.put(name,
                                Pair.of((int) Math.sqrt(thing.getFirst().distSqr(v.getPos())), thing.getFirst()));
                    }
                    else
                    {
                        not_found.add(name);
                    }
                }
            }
            thut.lib.ChatHelper.sendSystemMessage(player, TComponent.literal("Search Complete"));
            found.sort(null);
            not_found.sort(null);
            PokecubeAPI.LOGGER.info("Structures Found:");
            for (var name : found)
            {
                PokecubeAPI.LOGGER.info("{}\t{}\t{}", found_map.get(name).getFirst(), found_map.get(name).getSecond(),
                        name);
            }
            PokecubeAPI.LOGGER.info("Structures Missing:");
            for (var name : not_found)
            {
                PokecubeAPI.LOGGER.info(name);
            }
        }
    }
}
