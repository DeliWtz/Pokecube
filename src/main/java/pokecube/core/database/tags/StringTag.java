package pokecube.core.database.tags;

import java.io.FileNotFoundException;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import pokecube.api.PokecubeAPI;
import pokecube.core.database.resources.PackFinder;
import pokecube.core.database.util.DataHelpers;
import pokecube.core.database.util.DataHelpers.IResourceData;
import thut.api.util.UnderscoreIgnore;
import thut.core.common.ThutCore;

public class StringTag<T> implements IResourceData
{
    public static final Gson gson;
    static
    {
        gson = new GsonBuilder().registerTypeAdapter(StringValue.class, StringValueAdaptor.INSTANCE).setPrettyPrinting()
                .disableHtmlEscaping().setExclusionStrategies(UnderscoreIgnore.INSTANCE).create();
    }

    public static class StringValue<T>
    {
        public final String name;
        public T value = null;

        public Object _cached = null;

        public StringValue(String name)
        {
            this.name = name;
        }

        public StringValue<T> setValue(T value)
        {
            this.value = value;
            return this;
        }

        public T getValue()
        {
            return value;
        }
    }

    public static class TagHolder<T>
    {
        boolean replace = false;

        List<StringValue<T>> values = Lists.newArrayList();

        List<TagHolder<T>> _includes = Lists.newArrayList();

        private Map<String, StringValue<T>> _values = Maps.newHashMap();

        void postProcess()
        {
            this.values.replaceAll(s -> {
                boolean tag = false;
                String name = s.name;
                if (tag = name.startsWith("#")) name = name.replace("#", "");
                if (!name.contains(":")) name = "pokecube:" + ThutCore.trim(name);
                if (tag) name = "#" + name;
                if (name.equals(s.name)) return s;
                else return new StringValue<T>(name).setValue(s.getValue());
            });
            this.values.forEach(s -> {
                _values.put(s.name, s);
            });
        }

        public boolean isIn(final String value)
        {
            if (this._values.containsKey(value)) return true;
            for (final TagHolder<T> incl : this._includes) if (incl.isIn(value)) return true;
            return false;
        }

        public void checkIncludes(final StringTag<T> parent, final Set<String> checked)
        {
            // DOLATER possible speedup by adding the included tags to our list,
            // instead of referencing the included tags.

            for (final String s : this._values.keySet()) if (s.startsWith("#"))
            {
                final String tag = s.replace("#", "");
                if (checked.contains(tag))
                {
                    PokecubeAPI.LOGGER.warn("Warning, Recursive tags list! {}", checked);
                    continue;
                }
                final TagHolder<T> incl = parent.tagsMap.get(tag);
                if (incl == null)
                {
                    PokecubeAPI.LOGGER.warn("Warning, Tag not found for {}", s);
                    continue;
                }
                this._includes.add(incl);
            }
        }
    }

    private final Map<String, TagHolder<T>> tagsMap = Maps.newHashMap();

    private final Map<String, Set<String>> reversedTagsMap = Maps.newHashMap();

    private final String tagPath;

    public boolean validLoad = false;

    public StringTag(final String path)
    {
        this.tagPath = path;
        DataHelpers.addDataType(this);
    }

    @Override
    public void reload(final AtomicBoolean valid)
    {
        this.tagsMap.clear();
        this.validLoad = false;
        try
        {
            final String path = new ResourceLocation(this.tagPath).getPath();
            var resources = PackFinder.getAllJsonResources(path);
            this.validLoad = !resources.isEmpty();
            resources.forEach((l, r) -> {
                if (l.toString().contains("//")) l = new ResourceLocation(l.toString().replace("//", "/"));
                final String tag = l.toString().replace(path, "").replace(".json", "");
                this.loadTag(l, r, tag, "");
            });
            this.checkIncludes();
        }
        catch (final Exception e)
        {
            PokecubeAPI.LOGGER.error("Error reloading tags for {}", this.tagPath);
            e.printStackTrace();
        }
        if (this.validLoad) valid.set(true);
    }

    public Set<String> lookupTags(String name)
    {
        if (!name.contains(":")) name = "pokecube:" + ThutCore.trim(name);
        return this.reversedTagsMap.getOrDefault(name, Collections.emptySet());
    }

    public void checkIncludes()
    {
        for (final Entry<String, TagHolder<T>> entry : this.tagsMap.entrySet())
            entry.getValue().checkIncludes(this, Sets.newHashSet(entry.getKey()));
    }

    public boolean isIn(String tag, String toCheck)
    {
        if (!toCheck.contains(":")) toCheck = "pokecube:" + ThutCore.trim(toCheck);
        if (!tag.contains(":")) tag = "pokecube:" + ThutCore.trim(tag);
        // If we have the tag loaded, lets use the value from there.
        if (this.tagsMap.containsKey(tag))
        {
            final TagHolder<T> holder = this.tagsMap.get(tag);
            return holder.isIn(toCheck);
        }
        return false;
    }

    private boolean loadTag(final ResourceLocation tagLoc, List<Resource> resources, final String tag,
            final String toCheck)
    {
        try
        {
            final TagHolder<T> tagged = new TagHolder<T>();
            for (final Resource resource : resources)
            {
                final Reader reader = PackFinder.getReader(resource);
                @SuppressWarnings("unchecked")
                final TagHolder<T> temp = gson.fromJson(reader, TagHolder.class);
                temp.postProcess();
                if (temp.replace) tagged.values.clear();
                temp._values.forEach((k, v) -> {
                    if (!tagged._values.containsKey(k))
                    {
                        tagged.values.add(v);
                        tagged._values.put(k, v);
                    }
                });
                reader.close();
                // If we were replacing, we want to exit here.
                if (temp.replace) break;
            }
            this.tagsMap.put(tag, tagged);
            // Now we update the reversedTagsMap accordingly
            // Iterate over the values in the tag, and put toCheck in their set.
            for (StringValue<T> s : tagged.values)
            {
                String name = s.name;
                if (!name.contains(":")) name = "pokecube:" + ThutCore.trim(name);
                final Set<String> tags = this.reversedTagsMap.getOrDefault(name, Sets.newHashSet());
                tags.add(tag);
                this.reversedTagsMap.put(name, tags);
            }
            // now just return if it was present.
            return tagged.isIn(toCheck);
        }
        catch (final FileNotFoundException e)
        {
            PokecubeAPI.LOGGER.debug("No Tag: {}", tagLoc);
        }
        catch (final Exception e)
        {
            PokecubeAPI.LOGGER.error("Error reading tag " + tagLoc, e);
        }
        return false;
    }

    @Override
    public String getKey()
    {
        return tagPath;
    }

}
