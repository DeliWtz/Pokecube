package pokecube.core.database.moves;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import pokecube.api.PokecubeAPI;
import pokecube.api.moves.MoveEntry;
import pokecube.core.database.moves.json.Moves.Animation;
import pokecube.core.database.moves.json.Moves.Move;
import pokecube.core.database.moves.json.Moves.MoveHolder;
import pokecube.core.database.moves.json.Parsers;
import pokecube.core.database.resources.PackFinder;
import thut.api.util.JsonUtil;

public class MovesDatabases
{
    public static final String DATABASES = "database/moves/";

    public static void preInitLoad()
    {
        final String moves_path = "database/moves/entries/";
        final String anims_path = "database/moves/animations/";

        Map<ResourceLocation, Resource> resources = PackFinder.getJsonResources(moves_path);
        Map<String, List<Move>> movesToLoad = Maps.newHashMap();
        resources.forEach((l, r) -> {
            try
            {
                final InputStreamReader reader = new InputStreamReader(PackFinder.getStream(r));
                Move database = JsonUtil.gson.fromJson(reader, Move.class);
                reader.close();

                movesToLoad.compute(database.name, (key, list) -> {
                    var ret = list;
                    if (ret == null) ret = Lists.newArrayList();
                    ret.add(database);
                    return ret;
                });
            }
            catch (Exception e)
            {
                PokecubeAPI.LOGGER.error("Error with move file {}", l, e);
            }
        });

        resources = PackFinder.getJsonResources(anims_path);
        Map<String, List<Animation>> animsToLoad = Maps.newHashMap();
        resources.forEach((l, r) -> {
            try
            {
                final InputStreamReader reader = new InputStreamReader(PackFinder.getStream(r));
                Animation database = JsonUtil.gson.fromJson(reader, Animation.class);
                reader.close();

                animsToLoad.compute(database.name, (key, list) -> {
                    var ret = list;
                    if (ret == null) ret = Lists.newArrayList();
                    ret.add(database);
                    return ret;
                });
            }
            catch (Exception e)
            {
                PokecubeAPI.LOGGER.error("Error with move animation file {}", l, e);
            }
        });

        PokecubeAPI.LOGGER.info("Loaded {} moves and {} animations", movesToLoad.size(), animsToLoad.size());

        List<Move> loadedMoves = new ArrayList<>();

        movesToLoad.forEach((k, v) -> {
            v.sort(null);
            Move e = null;
            for (Move e1 : v)
            {
                if (e == null) e = e1;
                else
                {
                    e = e.mergeFrom(e1);
                }
            }
            loadedMoves.add(e);
        });
        loadedMoves.sort(null);

        Map<String, Animation> loadedAnimations = new HashMap<>();

        animsToLoad.forEach((k, v) -> {
            v.sort(null);
            Animation e = null;
            for (Animation e1 : v)
            {
                if (e == null) e = e1;
                else
                {
                    e = e.mergeFrom(e1);
                }
            }
            loadedAnimations.put(e.name, e);
        });

        // Now we process the loaded moves.

        for (Move json : loadedMoves)
        {
            // If flagged as to remove, skip it.
            if (json.remove) continue;
            // Start by making a move entry for it.
            MoveEntry entry = new MoveEntry(json.name);

            MoveHolder holder = new MoveHolder();
            holder.move = json;
            holder.animation = loadedAnimations.get(json.name);

            // Initialises preset/cleans up text.
            holder.preParse();

            // Create and assign a root entry.
            entry.root_entry = holder;

            var parser = Parsers.getParser(json.move_category);
            if (parser == null)
            {
                PokecubeAPI.LOGGER.error("Warning, no parser for {} {}", json.name, json.move_category);
            }
            // Process the move values.
            else parser.process(entry);

            // Register the move entry.
            MoveEntry.movesNames.put(entry.name, entry);
        }

        PokecubeAPI.LOGGER.info("Registered {} moves", loadedMoves.size());

    }
}
