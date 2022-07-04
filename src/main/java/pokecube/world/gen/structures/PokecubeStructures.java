package pokecube.world.gen.structures;

import net.minecraft.world.level.levelgen.feature.StructureFeature;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElementType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegistryObject;
import pokecube.world.PokecubeWorld;
import pokecube.world.gen.structures.pool_elements.ExpandedJigsawPiece;
import pokecube.world.gen_old.jigsaw.CustomJigsawPiece;

public class PokecubeStructures
{
    public static final RegistryObject<StructureFeature<?>> SURFACE_STRUCTURES;

    public static final RegistryObject<StructurePoolElementType<ExpandedJigsawPiece>> EXPANDED_POOL_ELEMENT;
    public static final RegistryObject<StructurePoolElementType<CustomJigsawPiece>> TYPE;

    static
    {
        SURFACE_STRUCTURES = PokecubeWorld.STRUCTURE_TYPES.register("generic_surface_jigsaw",
                GenericSurfaceJigsawStructure::new);
        EXPANDED_POOL_ELEMENT = PokecubeWorld.POOL_ELEMENT_TYPES.register("expanded_pool_element",
                () -> () -> ExpandedJigsawPiece.makeCodec());
        TYPE = PokecubeWorld.POOL_ELEMENT_TYPES.register("custom_pool_element",
                () -> () -> CustomJigsawPiece.makeCodec());
    }

    public static void init(final IEventBus bus)
    {

    }
}
