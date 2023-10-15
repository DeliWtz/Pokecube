package pokecube.api.data.pokedex;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import pokecube.api.PokecubeAPI;
import pokecube.api.data.PokedexEntry;
import pokecube.api.data.pokedex.conditions.AtLeastLevel;
import pokecube.api.data.pokedex.conditions.AtLocation;
import pokecube.api.data.pokedex.conditions.HasHeldItem;
import pokecube.api.data.pokedex.conditions.HasMove;
import pokecube.api.data.pokedex.conditions.IsEntry;
import pokecube.api.data.pokedex.conditions.IsHappy;
import pokecube.api.data.pokedex.conditions.IsSexe;
import pokecube.api.data.pokedex.conditions.IsTraded;
import pokecube.api.data.pokedex.conditions.PokemobCondition;
import pokecube.api.data.pokedex.conditions.RandomChance;
import pokecube.api.data.spawns.SpawnRule;
import pokecube.api.data.spawns.matchers.Time;
import pokecube.api.data.spawns.matchers.WeatherMatch;
import pokecube.api.entity.pokemob.IPokemob.FormeHolder;
import pokecube.core.PokecubeItems;
import pokecube.core.database.Database;
import pokecube.core.database.pokedex.PokedexEntryLoader;
import pokecube.core.database.pokedex.PokedexEntryLoader.Drop;
import thut.api.util.JsonUtil;
import thut.core.common.ThutCore;

public class InteractsAndEvolutions
{

    public static class Evolution
    {
        public Boolean clear;

        // Below are conditions
        public Integer level; // AtLeastLevel
        public SpawnRule location; // AtLocation
        public String time;// AtLocation
        public Boolean rain;// AtLocation
        public Drop item; // HeldItem
        public String item_preset; // HeldItem
        public Boolean trade;// IsTraded
        public Boolean happy;// IsHappy
        public String sexe;// IsSexe
        public String move;// HasMove
        public Float chance;// RandomChance
        public String form_from = null; // IsEntry

        public JsonElement condition;

        // Below are results

        /**
         * The result of the evolution
         */
        public String name;
        /**
         * The mob to evolve from, optional if this is part of the
         * JsonPokedexEntry
         */
        public String user;
        /**
         * Animation FX related
         */
        public String animation;
        /**
         * List of moves to learn on evo
         */
        public String evoMoves;
        /**
         * Custom model to apply after evo.
         */
        protected DefaultFormeHolder model = null;

        public FormeHolder getForme(final PokedexEntry baseEntry)
        {
            if (this.model != null) return this.model.getForme(baseEntry);
            return null;
        }

        public PokemobCondition toCondition()
        {
            PokemobCondition result = null;
            AtLocation locationTest = null;
            List<PokemobCondition> bits = new ArrayList<>();

            // First check if we have a loadable condition, if so, just use
            // that, the rest will be left as description information!
            if (condition != null)
            {
                result = PokemobCondition.makeFromElement(condition);
                result.init();
                return result;
            }

            if (level != null)
            {
                AtLeastLevel res = new AtLeastLevel();
                res.level = level;
                bits.add(res);
                result = res;
            }
            if (location != null)
            {
                locationTest = new AtLocation();
                bits.add(locationTest);
                locationTest.location = location;
                if (result == null) result = locationTest;
                else result = result.and(locationTest);
            }
            if (time != null)
            {
                Time match = new Time();
                match.preset = time;
                if (locationTest == null)
                {
                    locationTest = new AtLocation();
                    locationTest.location = new SpawnRule();
                    bits.add(locationTest);
                }
                locationTest.location.matchers.put("time", JsonUtil.gson.toJsonTree(match));
            }
            if (rain != null)
            {
                WeatherMatch match = new WeatherMatch();
                match.type = rain ? "rain" : "sun";
                if (locationTest == null)
                {
                    locationTest = new AtLocation();
                    locationTest.location = new SpawnRule();
                    bits.add(locationTest);
                }
                locationTest.location.matchers.put("weather", JsonUtil.gson.toJsonTree(match));
            }
            if (item != null)
            {
                var res = new HasHeldItem();
                bits.add(res);
                res.initFromDrop(item, false);
                if (result == null) result = res;
                else result = result.and(res);
            }
            if (item_preset != null)
            {
                var res = new HasHeldItem();
                if (item_preset.contains("#")) res.tag = item_preset.replace("#", "");
                else
                {
                    JsonObject obj = new JsonObject();
                    String item = item_preset;
                    if (!item.contains(":")) item = "pokecube:" + item;
                    ResourceLocation loc = new ResourceLocation(item);
                    if (!ForgeRegistries.ITEMS.containsKey(loc))
                    {
                        res.tag = item;
                    }
                    else
                    {
                        obj.addProperty("item", item);
                        res.item = obj;
                    }
                }
                bits.add(res);
                if (result == null) result = res;
                else result = result.and(res);
            }
            if (trade != null)
            {
                var bit = new IsTraded();
                bits.add(bit);
                if (result == null) result = bit;
                else result = result.and(bit);
            }
            if (happy != null)
            {
                var bit = new IsHappy();
                bits.add(bit);
                if (result == null) result = bit;
                else result = result.and(bit);
            }
            if (sexe != null)
            {
                var bit = new IsSexe();
                bit.sexe = sexe;
                bits.add(bit);
                if (result == null) result = bit;
                else result = result.and(bit);
            }
            if (move != null)
            {
                var bit = new HasMove();
                bit.move = move;
                bits.add(bit);
                if (result == null) result = bit;
                else result = result.and(bit);
            }
            if (chance != null)
            {
                var bit = new RandomChance();
                bit.chance = chance;
                bits.add(bit);
                if (result == null) result = bit;
                else result = result.and(bit);
            }
            if (form_from != null)
            {
                var bit = new IsEntry();
                bit.entry = form_from;
                bits.add(bit);
                if (result == null) result = bit;
                else result = result.and(bit);
            }
            bits.forEach(c -> c.init());
            return result;
        }

        @Override
        public boolean equals(final Object obj)
        {
            if (super.equals(obj)) return true;
            if (obj instanceof Evolution)
            {
                for (final Field f : this.getClass().getFields()) try
                {
                    final Object ours = f.get(this);
                    final Object theirs = f.get(obj);
                    if (ours != null && !ours.equals(theirs)) return false;
                    if (theirs != null && !theirs.equals(ours)) return false;
                    if (ours == null && theirs != null) return false;
                    if (theirs == null && ours != null) return false;
                }
                catch (final Exception e)
                {
                    e.printStackTrace();
                    return false;
                }
                return true;
            }

            return super.equals(obj);
        }

        @Override
        public String toString()
        {
            return PokedexEntryLoader.gson.toJson(this);
        }
    }

    public static class Interact
    {
        // Conditions
        public Boolean male = true;
        public Boolean female = true;
        public Boolean isTag = false;
        public Drop key;

        public JsonElement condition;

        // Results
        public Integer cooldown = 50;
        public Integer variance = 100;
        public Integer baseHunger = 100;
        public Action action;

        private String _ser_cache;

        @Override
        public boolean equals(Object obj)
        {
            if (obj == this) return true;
            if (obj instanceof Interact a)
            {
                if (this._ser_cache == null) this._ser_cache = JsonUtil.gson.toJson(this);
                if (a._ser_cache == null) a._ser_cache = JsonUtil.gson.toJson(this);
                return this._ser_cache.equals(a._ser_cache);
            }
            return false;
        }

        public PokemobCondition toCondition()
        {
            PokemobCondition result = null;
            List<PokemobCondition> bits = new ArrayList<>();
            if (condition != null)
            {
                result = PokemobCondition.makeFromElement(condition);
                bits.add(result);
            }

            if (male != null && !male)
            {
                var res = new IsSexe();
                bits.add(res);
                res.sexe = "male";
                if (result == null) result = res.not();
                else result = result.and(res.not());
            }

            if (female != null && !female)
            {
                var res = new IsSexe();
                bits.add(res);
                res.sexe = "female";
                if (result == null) result = res.not();
                else result = result.and(res.not());
            }
            bits.forEach(c -> c.init());
            if (result == null) result = e -> true;
            return result;
        }
    }

    public static class Action
    {
        public Map<String, String> values = Maps.newHashMap();
        public String tag;
        public String lootTable;
        public List<Drop> drops = Lists.newArrayList();
    }

    public static class DyeInfo implements Consumer<PokedexEntry>
    {
        // Colour for the normal mob
        public String base;
        // Colour for the shiny mob
        public String shiny;
        // If this is populated, only the listed dyes will work!
        public List<String> dyes = Lists.newArrayList();

        @Override
        public void accept(PokedexEntry entry)
        {
            final List<String> opts = dyes;
            entry.dyeable = true;
            // Parse base colour
            base = ThutCore.trim(base);
            for (final DyeColor dye : DyeColor.values()) if (ThutCore.trim(dye.name()).equals(base))
            {
                entry.defaultSpecial = dye.getId();
                break;
            }
            // Parse shiny colour
            shiny = ThutCore.trim(shiny);
            for (final DyeColor dye : DyeColor.values()) if (ThutCore.trim(dye.name()).equals(shiny))
            {
                entry.defaultSpecials = dye.getId();
                break;
            }
            entry.validDyes.clear();
            // Parse any limits on colours
            for (String s : opts)
            {
                s = ThutCore.trim(s);
                for (final DyeColor dye : DyeColor.values()) if (ThutCore.trim(dye.name()).equals(s))
                {
                    entry.validDyes.add(dye);
                    break;
                }
            }
        }
    }

    public static class FormeItem
    {
        protected String item;
        protected String forme;

        protected DefaultFormeHolder model = null;

        private PokedexEntry _entry = null;
        private ItemStack _itemstack = ItemStack.EMPTY;
        private FormeHolder _forme = null;

        public FormeHolder getForme(final PokedexEntry baseEntry)
        {
            if (this.model != null)
            {
                if (this._forme == null)
                {
                    this._forme = this.model.getForme(baseEntry);
                    if (this._forme != null)
                    {
                        this._forme._is_item_forme = true;
                    }
                    else
                    {
                        PokecubeAPI.LOGGER.error("No forme found for model: {}", model.key);
                        this.model = null;
                    }
                }
                return this._forme;
            }
            return this._forme;
        }

        public PokedexEntry getOutput()
        {
            if (_entry == null) _entry = Database.getEntry(forme);
            return _entry;
        }

        public ItemStack getKey()
        {
            if (_itemstack.isEmpty()) _itemstack = PokecubeItems.getStack(new ResourceLocation(item));
            return _itemstack;
        }
    }
}
