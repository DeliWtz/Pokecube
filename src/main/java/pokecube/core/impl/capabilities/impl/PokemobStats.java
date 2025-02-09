package pokecube.core.impl.capabilities.impl;

import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import pokecube.api.PokecubeAPI;
import pokecube.api.entity.pokemob.IPokemob;
import pokecube.api.entity.pokemob.stats.StatModifiers;
import pokecube.api.events.pokemobs.LevelUpEvent;
import pokecube.api.utils.PokeType;
import pokecube.api.utils.Tools;
import pokecube.core.network.pokemobs.PacketNickname;
import pokecube.core.network.pokemobs.PacketSyncExp;
import thut.core.common.ThutCore;
import thut.lib.TComponent;

public abstract class PokemobStats extends PokemobGenes
{
    @Override
    public void addHappiness(final int toAdd)
    {
        this.bonusHappiness += toAdd;
        this.dataSync().set(this.params.HAPPYDW, Integer.valueOf(this.bonusHappiness));
    }

    @Override
    public int getExp()
    {
        return this.getMoveStats().exp;
    }

    @Override
    public int getHappiness()
    {
        this.bonusHappiness = this.dataSync().get(this.params.HAPPYDW);
        this.bonusHappiness = Math.max(this.bonusHappiness, -this.getPokedexEntry().getHappiness());
        this.bonusHappiness = Math.min(this.bonusHappiness, 255 - this.getPokedexEntry().getHappiness());
        return this.bonusHappiness + this.getPokedexEntry().getHappiness();
    }

    @Override
    public StatModifiers getModifiers()
    {
        return this.modifiers;
    }

    @Override
    public String getPokemonNickname()
    {
        return this.dataSync().get(this.params.NICKNAMEDW);
    }

    @Override
    public int getRNGValue()
    {
        if (this.personalityValue == 0) this.personalityValue = ThutCore.newRandom().nextInt();
        return this.personalityValue;
    }

    /**
     * Returns 1st type.
     *
     * @see PokeType
     * @return the byte type
     */
    @Override
    public PokeType getType1()
    {
        final PokeType type = PokeType.getType(this.dataSync().get(this.params.TYPE1DW));
        return type != PokeType.unknown ? type : super.getType1();
    }

    /**
     * Returns 2nd type.
     *
     * @see PokeType
     * @return the byte type
     */
    @Override
    public PokeType getType2()
    {
        final PokeType type = PokeType.getType(this.dataSync().get(this.params.TYPE2DW));
        return type != PokeType.unknown ? type : super.getType2();
    }

    @Override
    public boolean isShadow()
    {
        final boolean isShadow = this.getPokedexEntry().isShadowForme;
        if (isShadow && !this.wasShadow) this.wasShadow = true;
        return isShadow;
    }

    @Override
    public IPokemob setExp(int exp, final boolean notifyLevelUp)
    {
        Mob mob = this.getEntity();
        if (!mob.isAlive()) return this;
        final int old = this.getMoveStats().exp;
        this.getMoveStats().oldLevel = this.getLevel();
        final int lvl100xp = Tools.maxXPs[this.getExperienceMode()];
        exp = Math.min(lvl100xp, exp);
        this.getMoveStats().exp = exp;
        final int newLvl = Tools.xpToLevel(this.getExperienceMode(), exp);
        final int oldLvl = Tools.xpToLevel(this.getExperienceMode(), old);
        IPokemob ret = this;
        if (oldLvl != newLvl)
        {
            // Fire event to allow others to interfere
            final LevelUpEvent lvlup = new LevelUpEvent(this, newLvl, this.getMoveStats().oldLevel);
            PokecubeAPI.POKEMOB_BUS.post(lvlup);
            if (!lvlup.isCanceled())
            {
                if (notifyLevelUp)
                {
                    this.updateHealth();
                    final ItemStack held = this.getHeldItem();
                    if (mob.isAlive() && (this.canEvolve(ItemStack.EMPTY) || this.canEvolve(held)))
                    {
                        this.levelUp(newLvl);
                        final IPokemob evo = this.evolve(true, false, held);
                        if (evo != null) ret = evo;
                    }
                    ret.levelUp(newLvl);
                    if (mob.isAddedToWorld() && ret.getOwner() instanceof Player
                            && mob.getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
                            && !mob.getLevel().isClientSide)
                        mob.getLevel().addFreshEntity(
                                new ExperienceOrb(mob.getLevel(), mob.getX(), mob.getY(), mob.getZ(), 1));
                }
            }
            else this.getMoveStats().exp = old;
        }
        PacketSyncExp.sendUpdate(ret);
        return ret;
    }

    @Override
    public IPokemob setForSpawn(final int exp, final boolean evolve)
    {
        final int level = Tools.xpToLevel(this.getExperienceMode(), exp);
        this.getMoveStats().oldLevel = 0;
        this.getMoveStats().exp = exp;
        IPokemob ret = this.levelUp(level);
        final ItemStack held = this.getHeldItem();
        if (evolve) while (ret.canEvolve(held))
        {
            final IPokemob temp = ret.evolve(false, true, held);
            if (temp == null) break;
            ret = temp;
            ret.getMoveStats().exp = exp;
            ret.levelUp(level);
        }
        return ret;
    }

    @Override
    public void setPokemonNickname(final String nickname)
    {
        final boolean oldName = this.getPokedexEntry().getName().equals(nickname) || nickname.trim().isEmpty();
        if (!this.getEntity().isEffectiveAi())
        {
            if (!nickname.equals(this.getPokemonNickname()) && this.getEntity().isAddedToWorld())
                PacketNickname.sendPacket(this.getEntity(), nickname);
        }
        else if (oldName) this.dataSync().set(this.params.NICKNAMEDW, "");
        else this.dataSync().set(this.params.NICKNAMEDW, nickname);
        if (this.getEntity().isAddedToWorld())
            this.getEntity().setCustomName(oldName ? null : TComponent.literal(nickname));
    }

    @Override
    public void setRNGValue(int value)
    {
        if (value == 0) value = ThutCore.newRandom().nextInt();
        this.personalityValue = value;
    }

    @Override
    public void setType1(final PokeType type1)
    {
        if (type1 == this.getType1()) return;
        final String name = type1 == null || type1 == PokeType.unknown ? "" : type1.name;
        this.dataSync().set(this.params.TYPE1DW, name);
    }

    @Override
    public void setType2(final PokeType type2)
    {
        if (type2 == this.getType2()) return;
        final String name = type2 == null || type2 == PokeType.unknown ? "" : type2.name;
        this.dataSync().set(this.params.TYPE2DW, name);
    }

}
