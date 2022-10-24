package pokecube.mobs.abilities.simple;

import pokecube.api.data.abilities.Ability;
import pokecube.api.entity.pokemob.IPokemob;
import pokecube.api.moves.MoveEntry;
import pokecube.api.moves.utils.IMoveConstants;
import pokecube.api.moves.utils.MoveApplication;
import thut.core.common.ThutCore;

public class EffectSpore extends Ability
{
    @Override
    public void preMoveUse(final IPokemob mob, final MoveApplication move)
    {
        if (!areWeTarget(mob, move)) return;
        final MoveEntry attack = move.getMove();
        final IPokemob attacker = move.getUser();
        if (move.hit && attack.getAttackCategory(attacker) == IMoveConstants.CATEGORY_CONTACT
                && Math.random() > 0.7)
        {
            final int num = ThutCore.newRandom().nextInt(30);
            if (num < 9) attacker.setStatus(IMoveConstants.STATUS_PSN);
            if (num < 19) attacker.setStatus(IMoveConstants.STATUS_PAR);
            else attacker.setStatus(IMoveConstants.STATUS_SLP);
        }
    }
}
