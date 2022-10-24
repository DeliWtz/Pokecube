package pokecube.core.moves.zmoves;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.google.common.collect.Maps;

import pokecube.api.entity.pokemob.IPokemob;
import pokecube.api.entity.pokemob.ai.CombatStates;
import pokecube.api.moves.MoveEntry;
import pokecube.api.moves.MoveEntry.PowerProvider;
import pokecube.core.database.tags.Tags;
import pokecube.core.moves.MovesUtils;
import pokecube.core.moves.templates.D_Move_Damage;
import pokecube.core.moves.templates.Z_Move_Damage;

public class GZMoveManager
{
    public static Map<String, String> zmoves_map = Maps.newHashMap();
    public static Map<String, List<String>> z_sig_moves_map = Maps.newHashMap();
    private static Map<String, String> gmoves_map = Maps.newHashMap();
    private static Map<String, String> g_max_moves_map = Maps.newHashMap();

    static final Pattern GMAXENTRY = Pattern.compile("(that_gigantamax_)(\\w+)(_use)");

    public static boolean isGZDMove(final MoveEntry entry)
    {
        return GZMoveManager.isZMove(entry) || GZMoveManager.isGMove(entry) || GZMoveManager.isDMove(entry);
    }

    public static boolean isZMove(final MoveEntry entry)
    {
        return Tags.MOVE.isIn("z_move", entry.name);
    }

    public static boolean isDMove(final MoveEntry entry)
    {
        return Tags.MOVE.isIn("d_move", entry.name);
    }

    public static boolean isGMove(final MoveEntry entry)
    {
        return Tags.MOVE.isIn("g_move", entry.name);
    }

    public static PowerProvider getPowerProvider(MoveEntry e)
    {
        return isZMove(e) ? Z_Move_Damage.INSTANCE : D_Move_Damage.INSTANCE;
    }

    public static void init(final MoveEntry moves)
    {
        // TODO re-do this G-Z move stuff to work with the updated moves
        // formatting.
    }

    /**
     * Returns a Z move based on the current selected attack of the user, if no
     * zmove available, or not able to use one (ie no crystal held), or the mob
     * is on cooldown for zmoves, then this will return null.
     *
     * @param user
     * @param index - move index to check
     * @return
     */
    public static String getZMove(final IPokemob user, final String base_move)
    {
        if (base_move == null) return null;
        final MoveEntry move = MovesUtils.getMove(base_move);
        if (move == null) return null;
        final ZPower checker = CapabilityZMove.get(user.getEntity());
        if (!checker.canZMove(user, base_move)) return null;

        final String name = user.getPokedexEntry().getTrimmedName();

        // Check if a valid signature Z-move is available.
        if (GZMoveManager.z_sig_moves_map.containsKey(name))
        {
            final List<String> moves = GZMoveManager.z_sig_moves_map.get(name);
            for (final String zmove : moves) if (checker.canZMove(user, zmove)) return zmove;
        }
        // Otherwise just pick the one from the map.
        return GZMoveManager.zmoves_map.get(base_move);
    }

    /**
     * Returns a Z move based on the current selected attack of the user, if no
     * zmove available, or not able to use one (ie no crystal held), or the mob
     * is on cooldown for zmoves, then this will return null.
     *
     * @param user
     * @param gigant
     * @param index  - move index to check
     * @return
     */
    public static String getGMove(final IPokemob user, final String base_move, boolean gigant)
    {
        if (base_move == null) return null;
        if (!user.getCombatState(CombatStates.DYNAMAX)) return null;
        final MoveEntry move = MovesUtils.getMove(base_move);
        if (move == null) return null;
        gigant = gigant && GZMoveManager.g_max_moves_map.containsKey(base_move);
        return gigant ? GZMoveManager.g_max_moves_map.get(base_move) : GZMoveManager.gmoves_map.get(base_move);
    }
}
