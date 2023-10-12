package pokecube.legends.handlers;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import pokecube.api.entity.pokemob.IPokemob;
import pokecube.api.entity.pokemob.ai.CombatStates;
import pokecube.api.moves.MoveEntry;
import pokecube.core.gimmicks.zmoves.CapabilityZMove;
import pokecube.core.gimmicks.zmoves.ZPower;
import pokecube.core.moves.MovesUtils;
import pokecube.legends.items.zmove.ItemZCrystal;

public class ZPowerHandler implements ZPower, ICapabilityProvider
{

    private final LazyOptional<ZPower> holder = LazyOptional.of(() -> this);

    public ZPowerHandler()
    {
    }

    @Override
    public boolean canZMove(final IPokemob pokemob, final String moveIn)
    {
        if (pokemob.getCombatState(CombatStates.USEDZMOVE)) return false;
        final MoveEntry move = MovesUtils.getMove(moveIn);
        if (move == null) return false;
        final ItemStack held = pokemob.getHeldItem();
        if (held.isEmpty()) return false;
        if (!(held.getItem() instanceof ItemZCrystal)) return false;
        final ItemZCrystal zcrys = (ItemZCrystal) held.getItem();
        if (zcrys.type != move.getType(pokemob)) return false;
        return true;
    }

    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> cap, final Direction side)
    {
        return CapabilityZMove.CAPABILITY.orEmpty(cap, this.holder);
    }

}
