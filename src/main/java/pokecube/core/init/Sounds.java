package pokecube.core.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;
import pokecube.core.PokecubeCore;
import pokecube.core.database.moves.json.Animations.AnimationJson;
import pokecube.core.database.moves.json.Moves;

public class Sounds
{
    public static final RegistryObject<SoundEvent> CAPTURE_SOUND;
    public static final RegistryObject<SoundEvent> HEAL_SOUND;
    public static final RegistryObject<SoundEvent> HEAL_MUSIC;

    static
    {
        CAPTURE_SOUND = PokecubeCore.SOUNDS.register("pokecube_caught",
                () -> new SoundEvent(new ResourceLocation(PokecubeCore.MODID + ":pokecube_caught")));
        HEAL_SOUND = PokecubeCore.SOUNDS.register("pokecenter",
                () -> new SoundEvent(new ResourceLocation(PokecubeCore.MODID + ":pokecenter")));
        HEAL_MUSIC = PokecubeCore.SOUNDS.register("pokecenterloop",
                () -> new SoundEvent(new ResourceLocation(PokecubeCore.MODID + ":pokecenterloop")));
    }

    public static void init()
    {}

    private static void registerIfNotPresent(ResourceLocation sound, SoundEvent event)
    {
        try
        {
            PokecubeCore.SOUNDS.register(sound.getPath(), () -> event);
        }
        catch (IllegalArgumentException e)
        {
            // pass here, it means it was already present!
        }
    }

    public static void initConfigSounds()
    {
        for (final String var : PokecubeCore.getConfig().customSounds)
        {
            final ResourceLocation sound = new ResourceLocation(var);
            final SoundEvent event = new SoundEvent(sound);
            if (!sound.getNamespace().equals("minecraft")) registerIfNotPresent(sound, event);
        }
    }

    public static void initMoveSounds()
    {
        // null as it should have been populated already
        for (final var entry : Moves.ALL_MOVES)
        {
            // Register sound on source
            if (entry.move.sound_effect_source != null)
            {
                final ResourceLocation sound = new ResourceLocation(entry.move.sound_effect_source);
                final SoundEvent event = new SoundEvent(sound);
                if (!sound.getNamespace().equals("minecraft")) registerIfNotPresent(sound, event);
            }
            // Register sound on target
            if (entry.move.sound_effect_target != null)
            {
                final ResourceLocation sound = new ResourceLocation(entry.move.sound_effect_target);
                final SoundEvent event = new SoundEvent(sound);
                if (!sound.getNamespace().equals("minecraft")) registerIfNotPresent(sound, event);
            }
            // Register sounds for the animations
            if (entry.animation.animations != null)
                for (final AnimationJson anim : entry.animation.animations) if (anim.sound != null)
            {
                final ResourceLocation sound = new ResourceLocation(anim.sound);
                final SoundEvent event = new SoundEvent(sound);
                if (!sound.getNamespace().equals("minecraft")) registerIfNotPresent(sound, event);
            }
        }
    }
}
