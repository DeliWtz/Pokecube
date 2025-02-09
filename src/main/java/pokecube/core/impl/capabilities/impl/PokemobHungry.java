package pokecube.core.impl.capabilities.impl;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import pokecube.api.data.PokedexEntry;
import pokecube.api.entity.pokemob.Nature;
import pokecube.api.entity.pokemob.ai.CombatStates;
import pokecube.api.entity.pokemob.ai.GeneralStates;
import pokecube.api.events.pokemobs.SpawnEvent.SpawnContext;
import pokecube.api.items.IPokemobUseable;
import pokecube.core.PokecubeCore;
import pokecube.core.eventhandlers.SpawnHandler;
import pokecube.core.items.berries.ItemBerry;
import thut.api.item.ItemList;
import thut.api.maths.Vector3;
import thut.core.common.ThutCore;

public abstract class PokemobHungry extends PokemobMoves
{
    public static final ResourceLocation LEPPABERRY = new ResourceLocation(PokecubeCore.MODID, "berry_leppa");

    @SuppressWarnings("unchecked")
    <T> T cast(final Object o)
    {
        return (T) o;
    }

    @Override
    public <T> T eat(T e)
    {
        int hungerValue = PokecubeCore.getConfig().pokemobLifeSpan / 4;
        ItemStack item = e instanceof ItemStack ? (ItemStack) e : ItemStack.EMPTY;
        if (e instanceof ItemEntity) item = ((ItemEntity) e).getItem();
        if (!item.isEmpty())
        {
            final IPokemobUseable usable = IPokemobUseable.getUsableFor(item);
            if (usable != null)
            {
                final InteractionResultHolder<ItemStack> result = usable.onUse(this, item, this.getEntity());
                if (e instanceof ItemEntity) ((ItemEntity) e).setItem(result.getObject());
                else e = this.cast(result.getObject());
            }
            if (ItemList.is(PokemobHungry.LEPPABERRY, item)) hungerValue *= 2;
            if (item.getItem() instanceof ItemBerry berry)
            {
                int weight = Nature.getBerryWeight(berry.type.index, this.getNature());
                final int current = this.getHappiness();
                final HappinessType type = HappinessType.BERRY;
                if (current < 100) weight *= type.low / 10f;
                else if (current < 200) weight *= type.mid / 10f;
                else weight *= type.high / 10f;
                this.addHappiness(weight);
            }
        }
        this.applyHunger(-hungerValue);
        this.hungerCooldown = 0;
        this.getEntity().playSound(ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("entity.generic.eat")), 1, 1);
        this.setCombatState(CombatStates.HUNTING, false);
        if (!this.getEntity().isAlive()) return null;
        final float missingHp = this.getMaxHealth() - this.getHealth();
        final float toHeal = this.getHealth() + Math.max(1, missingHp * 0.25f);
        this.setHealth(Math.min(toHeal, this.getMaxHealth()));
        // Make wild pokemon level up naturally to their cap, to allow wild
        // hatches
        if (!this.getGeneralState(GeneralStates.TAMED))
        {
            final int exp = SpawnHandler.getSpawnXp(new SpawnContext(this));
            if (this.getExp() < exp)
            {
                final int n = ThutCore.newRandom().nextInt(exp - this.getExp()) / 3 + 1;
                this.setExp(this.getExp() + n, true);
            }
        }
        return e;
    }

    @Override
    public boolean eatsBerries()
    {
        return this.getPokedexEntry().foods[5];
    }

    @Override
    public boolean filterFeeder()
    {
        return this.getPokedexEntry().foods[6];
    }

    @Override
    public int getFlavourAmount(final int index)
    {
        return this.dataSync().get(this.params.FLAVOURS[index]);
    }

    @Override
    public int getHungerCooldown()
    {
        return this.hungerCooldown;
    }

    @Override
    public int getHungerTime()
    {
        return this.dataSync().get(this.params.HUNGERDW);
    }

    private float _last_size = 0;

    @Override
    public Vector3 getMobSizes()
    {
        float size = this.getSize();
        if (size != _last_size)
        {
            _last_size = size;
            PokedexEntry entry = this.getPokedexEntry();
            this.sizes.set(entry.width, entry.height, entry.length);
            this.sizes.scalarMultBy(size);
        }
        return this.sizes;
    }

    /** @return does this pokemon hunt for food */
    @Override
    public boolean isCarnivore()
    {
        return this.getPokedexEntry().hasPrey();
    }

    @Override
    public boolean isElectrotroph()
    {
        return this.getPokedexEntry().foods[2];
    }

    /** @return Does this pokemon eat grass */
    @Override
    public boolean isHerbivore()
    {
        return this.getPokedexEntry().foods[3];
    }

    @Override
    public boolean isLithotroph()
    {
        return this.getPokedexEntry().foods[1];
    }

    @Override
    public boolean isPhototroph()
    {
        return this.getPokedexEntry().foods[0];
    }

    @Override
    public boolean neverHungry()
    {
        return this.getPokedexEntry().foods[4];
    }

    @Override
    public void noEat(final Object e)
    {
        if (e != null) this.addHappiness(-10);
    }

    @Override
    public void setFlavourAmount(final int index, final int amount)
    {
        this.dataSync().set(this.params.FLAVOURS[index], amount);
    }

    @Override
    public void setHungerCooldown(final int hungerCooldown)
    {
        this.hungerCooldown = hungerCooldown;
    }

    @Override
    public void setHungerTime(final int hungerTime)
    {
        this.dataSync().set(this.params.HUNGERDW, hungerTime);
    }
}
