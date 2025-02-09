package pokecube.core.commands;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import pokecube.api.PokecubeAPI;
import pokecube.api.data.PokedexEntry;
import pokecube.api.entity.pokemob.IPokemob;
import pokecube.api.entity.pokemob.PokemobCaps;
import pokecube.api.utils.TagNames;
import pokecube.api.utils.Tools;
import pokecube.core.PokecubeCore;
import pokecube.core.handlers.playerdata.PlayerPokemobCache;
import pokecube.core.items.pokecubes.PokecubeManager;
import pokecube.core.utils.Permissions;
import thut.api.util.PermNodes;
import thut.api.util.PermNodes.DefaultPermissionLevel;
import thut.core.common.ThutCore;
import thut.core.common.handlers.PlayerDataHandler;
import thut.lib.TComponent;

public class Restore
{

    public static void register(final LiteralArgumentBuilder<CommandSourceStack> command)
    {
        final String perm = "command.pokecube.restore";
        PermNodes.registerBooleanNode(PokecubeCore.MODID, perm, DefaultPermissionLevel.OP,
                "Is the player allowed use the restore feature to recover mobs");

        final LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("restore")
                .requires(Permissions.hasPerm(perm));

        // This one does the actual restoration
        final LiteralArgumentBuilder<CommandSourceStack> restore = Commands.literal("restore")
                .then(Commands.argument("uuid", StringArgumentType.string())
                        .then(Commands.argument("id", IntegerArgumentType.integer())
                                .executes(ctx -> Restore.execute_give(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "uuid"),
                                        IntegerArgumentType.getInteger(ctx, "id")))));

        final LiteralArgumentBuilder<CommandSourceStack> check = Commands.literal("check")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> Restore.execute(ctx.getSource(),
                                GameProfileArgument.getGameProfiles(ctx, "player"), false, false, false, "")));
        final LiteralArgumentBuilder<CommandSourceStack> check_pc = Commands.literal("check_pc")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> Restore.execute(ctx.getSource(),
                                GameProfileArgument.getGameProfiles(ctx, "player"), false, true, false, "")));
        final LiteralArgumentBuilder<CommandSourceStack> check_deleted = Commands.literal("check_deleted")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> Restore.execute(ctx.getSource(),
                                GameProfileArgument.getGameProfiles(ctx, "player"), false, false, true, "")));

        final LiteralArgumentBuilder<CommandSourceStack> give = Commands.literal("give")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> Restore.execute(ctx.getSource(),
                                GameProfileArgument.getGameProfiles(ctx, "player"), true, false, false, "")));
        final LiteralArgumentBuilder<CommandSourceStack> give_pc = Commands.literal("give_pc")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> Restore.execute(ctx.getSource(),
                                GameProfileArgument.getGameProfiles(ctx, "player"), true, true, false, "")));
        final LiteralArgumentBuilder<CommandSourceStack> give_deleted = Commands.literal("give_deleted")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> Restore.execute(ctx.getSource(),
                                GameProfileArgument.getGameProfiles(ctx, "player"), true, false, true, "")));

        final LiteralArgumentBuilder<CommandSourceStack> check_spec = Commands.literal("check")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> Restore.execute(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "player"), false, false, false,
                                        StringArgumentType.getString(ctx, "name")))));
        final LiteralArgumentBuilder<CommandSourceStack> check_pc_spec = Commands.literal("check_pc")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> Restore.execute(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "player"), false, true, false,
                                        StringArgumentType.getString(ctx, "name")))));

        final LiteralArgumentBuilder<CommandSourceStack> give_spec = Commands.literal("give")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> Restore.execute(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "player"), true, false, false,
                                        StringArgumentType.getString(ctx, "name")))));
        final LiteralArgumentBuilder<CommandSourceStack> give_pc_spec = Commands.literal("give_pc")
                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("name", StringArgumentType.string())
                                .executes(ctx -> Restore.execute(ctx.getSource(),
                                        GameProfileArgument.getGameProfiles(ctx, "player"), true, true, false,
                                        StringArgumentType.getString(ctx, "name")))));

        final LiteralArgumentBuilder<CommandSourceStack> clear = Commands.literal("clear")
                .then(Commands.argument("player", GameProfileArgument.gameProfile()).executes(ctx -> Restore
                        .execute_clear(ctx.getSource(), GameProfileArgument.getGameProfiles(ctx, "player"))));

        root.then(check);
        root.then(check_pc);
        root.then(check_deleted);
        root.then(give);
        root.then(give_pc);
        root.then(give_deleted);

        root.then(check_spec);
        root.then(check_pc_spec);
        root.then(give_spec);
        root.then(give_pc_spec);

        root.then(clear);

        // This one does the actual restoration
        root.then(restore);

        command.then(root);
    }

    private static int execute_give(final CommandSourceStack source, final String uuid, final int id)
            throws CommandSyntaxException
    {
        final ServerPlayer user = source.getPlayerOrException();
        final PlayerPokemobCache pokemobCache = PlayerDataHandler.getInstance().getPlayerData(UUID.fromString(uuid))
                .getData(PlayerPokemobCache.class);
        final Map<Integer, ItemStack> cache = pokemobCache.cache;
        final ItemStack stack = cache.getOrDefault(id, ItemStack.EMPTY);
        Tools.giveItem(user, stack.copy());
        PokecubeAPI.logInfo("{} Restored {}", user.getDisplayName().getString(), stack.getHoverName().getString());
        return 0;
    }

    private static int execute_clear(final CommandSourceStack source, final Collection<GameProfile> players)
    {
        for (final GameProfile profile : players)
        {
            final PlayerPokemobCache pokemobCache = PlayerDataHandler.getInstance().getPlayerData(profile.getId())
                    .getData(PlayerPokemobCache.class);
            // This clears the internal lists.
            pokemobCache.readFromNBT(new CompoundTag());
            PlayerDataHandler.getInstance().save(profile.getId().toString(), pokemobCache.getIdentifier());
        }
        return 0;
    }

    private static int execute(final CommandSourceStack source, final Collection<GameProfile> players,
            final boolean give, final boolean pc, final boolean deleted, String toMatch) throws CommandSyntaxException
    {
        if (players.size() != 1)
        {
            source.sendFailure(TComponent.translatable("pokecube.command.restore_only_one"));
            return 1;
        }
        toMatch = ThutCore.trim(toMatch);

        final ServerPlayer user = source.getPlayerOrException();

        final GameProfile profile = players.iterator().next();
        final PlayerPokemobCache pokemobCache = PlayerDataHandler.getInstance().getPlayerData(profile.getId())
                .getData(PlayerPokemobCache.class);
        final Map<Integer, ItemStack> cache = pokemobCache.cache;
        MutableComponent message = TComponent.literal("Pokemobs: ");
        thut.lib.ChatHelper.sendSystemMessage(user, message);
        message = TComponent.literal("");
        for (final Entry<Integer, ItemStack> entry : cache.entrySet())
        {
            final Integer id = entry.getKey();
            final boolean inPC = pokemobCache._in_pc_.contains(id);
            final boolean wasDeleted = pokemobCache._dead_.contains(id);
            // If it is in the PC, but we dont care, continue
            if (pc != inPC) continue;
            if (deleted != wasDeleted) continue;

            final ItemStack stack = entry.getValue();

            if (!toMatch.isEmpty())
            {
                final Entity mob = PokecubeManager.itemToMob(stack, user.getLevel());
                if (mob == null) continue;
                final IPokemob pokemob = PokemobCaps.getPokemobFor(mob);
                if (pokemob != null)
                {
                    final PokedexEntry pentry = pokemob.getPokedexEntry();
                    if (!pentry.getTrimmedName().equalsIgnoreCase(toMatch)) continue;
                }
            }

            final ListTag nbttaglist = stack.getTag().getCompound(TagNames.POKEMOB).getList("Pos", 6);
            final double posX = nbttaglist.getDouble(0);
            final double posY = nbttaglist.getDouble(1);
            final double posZ = nbttaglist.getDouble(2);
            String command;
            if (!give) command = "/tp " + posX + " " + posY + " " + posZ;
            else command = "/pokecube restore restore " + profile.getId() + " " + id;

            CompoundTag tag = stack.getTag().copy();
            tag.remove(TagNames.POKEMOB);
            final ItemStack copy = stack.copy();
            copy.setTag(tag);
            tag = copy.save(new CompoundTag());
            final ClickEvent click = new ClickEvent(Action.RUN_COMMAND, command);
            final MutableComponent sub = (MutableComponent) stack.getDisplayName();
            sub.setStyle(sub.getStyle().withClickEvent(click));
            sub.append(" ");
            message.append(sub);
            final int size = message.toString().getBytes().length;
            if (size > 32000)
            {
                thut.lib.ChatHelper.sendSystemMessage(user, message);
                message = TComponent.literal("");
            }
        }
        thut.lib.ChatHelper.sendSystemMessage(user, message);
        return 0;
    }
}
