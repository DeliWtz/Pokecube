package thut.api.level.terrain;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import thut.api.maths.Vector3;
import thut.core.common.ThutCore;

public class TerrainSegment
{

    public static class DefaultChecker implements ISubBiomeChecker
    {
        @Override
        public BiomeType getSubBiome(final LevelAccessor world, final Vector3 v, final TerrainSegment segment,
                final boolean caveAdjusted)
        {
            if (caveAdjusted)
            {
                // Do not return this for cave worlds
                if (world.dimensionType().hasCeiling()) return BiomeType.NONE;
                boolean sky = false;
                final Vector3 temp1 = new Vector3();
                final int x0 = segment.chunkX * 16, y0 = segment.chunkY * 16, z0 = segment.chunkZ * 16;
                final int dx = (v.intX() - x0) / TerrainSegment.GRIDSIZE * TerrainSegment.GRIDSIZE;
                final int dy = (v.intY() - y0) / TerrainSegment.GRIDSIZE * TerrainSegment.GRIDSIZE;
                final int dz = (v.intZ() - z0) / TerrainSegment.GRIDSIZE * TerrainSegment.GRIDSIZE;
                final int x1 = x0 + dx, y1 = y0 + dy, z1 = z0 + dz;

                // Check if this location can see the sky.
                outer:
                for (int i = x1; i < x1 + TerrainSegment.GRIDSIZE; i++)
                    for (int j = y1; j < y1 + TerrainSegment.GRIDSIZE; j++)
                        for (int k = z1; k < z1 + TerrainSegment.GRIDSIZE; k++)
                {
                    temp1.set(i, j, k);
                    if (segment.isInTerrainSegment(temp1.x, temp1.y, temp1.z))
                    {
                        final double y = temp1.getMaxY(world);
                        sky = y <= temp1.y;
                    }
                    if (sky) break outer;
                }
                if (sky) return BiomeType.NONE;

                // If not can see sky, if there is water, it is cave_water,
                // otherwise it is cave.
                if (!sky && TerrainSegment.count(world, Blocks.WATER, v, 1) > 2) return BiomeType.CAVE_WATER;
                else if (!sky) return BiomeType.CAVE;
            }
            else
            {
                BiomeType biome = BiomeType.NONE;

                final Holder<Biome> b = v.getBiomeHolder(world);

                // Do not define lakes on watery biomes.
                final boolean notLake = this.isWatery(b);
                if (!notLake)
                {
                    // If it isn't a water biome, define it as a lake if more
                    // than a certain amount of water.
                    final int water = TerrainSegment.count(world, Blocks.WATER, v, 3);
                    if (water > 4)
                    {
                        biome = BiomeType.LAKE;
                        return biome;
                    }
                }
                // Check nearby villages, and if in one, define as village type.
                if (world instanceof ServerLevel level)
                {
                    final BlockPos pos = v.getPos();
                    if (level.isVillage(pos)) biome = BiomeType.VILLAGE;
                }

                return biome;
            }
            return BiomeType.NONE;
        }
    }

    public static interface ISubBiomeChecker
    {
        public static final TagKey<Biome> WATERY = TagKey.create(Registry.BIOME_REGISTRY,
                new ResourceLocation("thutcore:is_watery"));

        /**
         * This should return -1 if it is not a relevant biome for this biome
         * checker.
         *
         * @param world
         * @param v
         * @param segment
         * @param chunk
         * @param caveAdjusted
         * @return
         */
        BiomeType getSubBiome(LevelAccessor world, Vector3 v, TerrainSegment segment, boolean caveAdjusted);

        default boolean isWatery(final Holder<Biome> b)
        {
            return b.is(WATERY);
        }
    }

    public static interface ITerrainEffect
    {
        /**
         * Called when the terrain effect is assigned to the terrain segment
         *
         * @param x chunkX of terrainsegment
         * @param y chunkY of terrainsegment
         * @param z chunkZ of terrainsegement
         */
        void bindToTerrain(int x, int y, int z);

        void doEffect(LivingEntity entity, boolean firstEntry);

        String getIdentifier();

        void readFromNBT(CompoundTag nbt);

        void writeToNBT(CompoundTag nbt);
    }

    public static final int GRIDSIZE = 4;

    public static final int YSHIFT = TerrainSegment.GRIDSIZE;
    public static final int ZSHIFT = TerrainSegment.YSHIFT * TerrainSegment.GRIDSIZE;
    public static final int TOTAL = TerrainSegment.ZSHIFT * TerrainSegment.GRIDSIZE;

    public static ISubBiomeChecker defaultChecker = new DefaultChecker();
    public static List<ISubBiomeChecker> biomeCheckers = Lists.newArrayList();

    private static Set<Class<? extends ITerrainEffect>> terrainEffectClasses = Sets.newHashSet();

    public static void registerTerrainEffect(Class<? extends ITerrainEffect> effect)
    {
        terrainEffectClasses.add(effect);
    }

    public static boolean noLoad = false;

    //@formatter:off
    public static Predicate<BiomeType> saveChecker = (i) -> i.shouldSave();
    //@formatter:on

    public static int count(final LevelAccessor world, final Block b, final Vector3 v, final int range)
    {
        final Vector3 temp = new Vector3();
        temp.set(v);
        int ret = 0;
        for (int i = -range; i <= range; i++)
            for (int j = -range; j <= range; j++) for (int k = -range; k <= range; k++)
        {

            boolean bool = true;
            final int i1 = Mth.floor(v.intX() + i) >> 4;
            final int k1 = Mth.floor(v.intZ() + i) >> 4;

            bool = i1 == v.intX() >> 4 && k1 == v.intZ() >> 4;

            if (bool)
            {
                temp.set(v).addTo(i, j, k);
                final BlockState state = world.getBlockState(temp.getPos());
                if (state.getBlock() == b || b == null && state.getBlock() == null) ret++;
            }
        }
        return ret;
    }

    public static int toLocal(final int globalPos)
    {
        return (globalPos & 15) / TerrainSegment.GRIDSIZE;
    }

    public static int toGlobal(final int localPos, final int chunkPos)
    {
        return (chunkPos << 4) + (localPos << 4) / TerrainSegment.GRIDSIZE;
    }

    public static int globalToIndex(final int x, final int y, final int z)
    {
        final int relX = TerrainSegment.toLocal(x);
        final int relY = TerrainSegment.toLocal(y);
        final int relZ = TerrainSegment.toLocal(z);
        return TerrainSegment.localToIndex(relX, relY, relZ);
    }

    public static int localToIndex(final int x, final int y, final int z)
    {
        return x + TerrainSegment.YSHIFT * y + TerrainSegment.ZSHIFT * z;
    }

    public static boolean isInTerrainColumn(final Vector3 t, final Vector3 point)
    {
        boolean ret = true;
        final int i = point.intX() >> 4;
        final int k = point.intZ() >> 4;

        ret = i == t.intX() && k == t.intZ();
        return ret;
    }

    public static void readFromNBT(final TerrainSegment t, final CompoundTag nbt)
    {
        if (TerrainSegment.noLoad) return;
        final int[] biomes = nbt.getIntArray("biomes");
        t.toSave = nbt.getBoolean("toSave");
        t.init = t.toSave;
        boolean replacements = false;
        if (t.idReplacements != null)
            for (int i = 0; i < biomes.length; i++) if (t.idReplacements.containsKey(biomes[i]))
        {
            biomes[i] = t.idReplacements.get(biomes[i]);
            replacements = true;
        }
        if (nbt.contains("effects"))
        {
            CompoundTag effects = nbt.getCompound("effects");
            for (var key : effects.getAllKeys())
            {
                CompoundTag load = effects.getCompound(key);
                ITerrainEffect effect = t.effects.get(key);
                if (effect != null)
                {
                    effect.readFromNBT(load);
                }
            }
        }
        if (replacements && ThutCore.conf.debug)
            ThutCore.LOGGER.info("Replacement subbiomes found for " + t.chunkX + " " + t.chunkY + " " + t.chunkZ);
        t.setBiomes(biomes);
    }

    public Int2IntMap idReplacements;

    public final int chunkX;

    public final int chunkY;

    public final int chunkZ;

    public final BlockPos pos;

    public ChunkAccess chunk;

    public boolean toSave = false;

    public boolean isSky = false;

    public boolean init = true;

    // This is true if this was loaded from the capability, false if during
    // worldgen
    public boolean real = false;

    Vector3 temp = new Vector3();

    Vector3 temp1 = new Vector3();

    Vector3 mid = new Vector3();

    protected int[] biomes = new int[TerrainSegment.TOTAL];

    HashMap<String, ITerrainEffect> effects = new HashMap<>();
    public final ITerrainEffect[] effectArr;

    /**
     * @param pos in chunk coordinates, not block coordinates.
     */
    public TerrainSegment(final BlockPos pos)
    {
        this(pos.getX(), pos.getY(), pos.getZ());
    }

    public TerrainSegment(final int x, final int y, final int z)
    {
        this.chunkX = x;
        this.chunkY = y;
        this.chunkZ = z;
        this.pos = new BlockPos(x, y, z);
        Arrays.fill(this.biomes, -1);
        this.mid.set(this.chunkX * 16 + 8, this.chunkY * 16 + 8, this.chunkZ * 16 + 8);
        for (final Class<? extends ITerrainEffect> clas : TerrainSegment.terrainEffectClasses) try
        {
            final ITerrainEffect effect = clas.getConstructor().newInstance();
            this.addEffect(effect, effect.getIdentifier());
        }
        catch (final Exception e)
        {
            e.printStackTrace();
        }
        final List<ITerrainEffect> toSort = Lists.newArrayList(this.effects.values());
        toSort.sort(Comparator.comparing(ITerrainEffect::getIdentifier));
        this.effectArr = toSort.toArray(new ITerrainEffect[0]);
    }

    private void addEffect(final ITerrainEffect effect, final String name)
    {
        effect.bindToTerrain(this.chunkX, this.chunkY, this.chunkZ);
        this.effects.put(name, effect);
    }

    public BiomeType adjustedCaveBiome(final LevelAccessor world, final Vector3 v)
    {
        return this.getBiome(world, v, true);
    }

    public BiomeType adjustedNonCaveBiome(final LevelAccessor world, final Vector3 v)
    {
        return this.getBiome(world, v, false);
    }

    void checkToSave()
    {
        for (final int i : this.biomes) if (TerrainSegment.saveChecker.test(BiomeType.getType(i)))
        {
            this.toSave = true;
            return;
        }
        this.toSave = false;
    }

    @Override
    public boolean equals(final Object o)
    {
        boolean ret = false;
        if (o instanceof TerrainSegment seg)
            ret = seg.chunkX == this.chunkX && seg.chunkY == this.chunkY && seg.chunkZ == this.chunkZ;
        return ret;
    }

    public double getAverageSlope(final Level world, final Vector3 point, final int range)
    {
        double slope = 0;

        final double prevY = point.getMaxY(world);

        double dy = 0;
        double dz = 0;
        this.temp1.set(this.temp);
        this.temp.set(point);
        int count = 0;
        for (int i = -range; i <= range; i++)
        {
            dz = 0;
            for (int j = -range; j <= range; j++)
            {
                if (TerrainSegment.isInTerrainColumn(point, this.temp.addTo(i, 0, j)))
                    dy += Math.abs(point.getMaxY(world, point.intX() + i, point.intZ() + j) - prevY);
                dz++;
                count++;
                this.temp.set(point);
            }
            slope += dy / dz;
        }
        this.temp.set(this.temp1);

        return slope / count;
    }

    public BiomeType getBiome(final int x, final int y, final int z)
    {
        int ret = 0;
        final int index = TerrainSegment.globalToIndex(x, y, z);
        if (index < TerrainSegment.TOTAL) ret = this.biomes[index];
        return BiomeType.getType(ret);
    }

    public BiomeType getBiome(final Vector3 v)
    {
        return this.getBiome(v.intX(), v.intY(), v.intZ());
    }

    private BiomeType getBiome(final LevelAccessor world, final Vector3 v, final boolean caveAdjust)
    {
        if (!this.real) return BiomeType.NONE;
        if (this.chunk == null)
        {
            Thread.dumpStack();
            return BiomeType.NONE;
        }
        if (!TerrainSegment.biomeCheckers.isEmpty()) for (final ISubBiomeChecker checker : TerrainSegment.biomeCheckers)
        {
            final BiomeType biome = checker.getSubBiome(world, v, this, caveAdjust);
            if (!biome.isNone()) return biome;
        }
        return TerrainSegment.defaultChecker.getSubBiome(world, v, this, caveAdjust);
    }

    public Vector3 getCentre()
    {
        return this.mid;
    }

    public BlockPos getChunkCoords()
    {
        return this.pos;
    }

    public Collection<ITerrainEffect> getEffects()
    {
        return this.effects.values();
    }

    public ITerrainEffect geTerrainEffect(final String name)
    {
        return this.effects.get(name);
    }

    @Override
    public int hashCode()
    {
        return this.chunkX + this.chunkZ << 8 << 8 + this.chunkY;
    }

    public void initBiomes(final LevelAccessor world)
    {
        if (this.init && world instanceof ServerLevel)
        {
            this.init = false;
            this.refresh(world);
        }
    }

    public boolean isInTerrainSegment(final double x, final double y, final double z)
    {
        boolean ret = true;
        final int i = Mth.floor(x) >> 4;
        final int j = Mth.floor(y) >> 4;
        final int k = Mth.floor(z) >> 4;
        ret = i == this.chunkX && k == this.chunkZ && j == this.chunkY;
        return ret;
    }

    public void refresh(final LevelAccessor world)
    {
        final long time = System.nanoTime();
        if (!this.real)
        {
            this.init = true;
            return;
        }
        for (int x = 0; x < TerrainSegment.GRIDSIZE; x++)
            for (int y = 0; y < TerrainSegment.GRIDSIZE; y++) for (int z = 0; z < TerrainSegment.GRIDSIZE; z++)
        {
            // This is the index in biomes of our current location.
            final int index = TerrainSegment.localToIndex(x, y, z);
            // Check if this segment is already a custom choice, if so,
            // then we don't want to overwrite it, unless we are not
            // allowed to load saved subbiomes.
            if (TerrainSegment.saveChecker.test(BiomeType.getType(this.biomes[index])) && !TerrainSegment.noLoad)
                continue;

            // Conver to block coordinates.
            this.temp.set(TerrainSegment.toGlobal(x, this.chunkX), TerrainSegment.toGlobal(y, this.chunkY),
                    TerrainSegment.toGlobal(z, this.chunkZ));

            // Check to see what our various detectors pick for this
            // location.
            BiomeType biome = this.adjustedCaveBiome(world, this.temp);
            // Only check non-adjusted if adjusted fails.
            if (biome.isNone()) biome = this.adjustedNonCaveBiome(world, this.temp);
            // Both failed, skip.
            if (biome.isNone()) continue;
            // Flag if we are a not-trivial biome.
            if (TerrainSegment.saveChecker.test(biome)) this.toSave = true;
            // Put it in the array.
            this.biomes[index] = biome.getType();
        }
        final double dt = (System.nanoTime() - time) / 10e9;
        // Don't let us take too long!
        if (dt > 0.001) ThutCore.LOGGER.debug("subBiome refresh took " + dt);
    }

    public void saveToNBT(final CompoundTag nbt)
    {
        if (!this.toSave) return;
        nbt.putIntArray("biomes", this.biomes);
        nbt.putInt("x", this.chunkX);
        nbt.putInt("y", this.chunkY);
        nbt.putInt("z", this.chunkZ);
        nbt.putBoolean("toSave", this.toSave);

        if (!this.effects.isEmpty())
        {
            CompoundTag effects = new CompoundTag();
            for (var entry : this.effects.entrySet())
            {
                CompoundTag tag = new CompoundTag();
                entry.getValue().writeToNBT(tag);
                if (!tag.isEmpty()) effects.put(entry.getKey(), tag);
            }
            if (!effects.isEmpty()) nbt.put("effects", effects);
        }
    }

    public void setBiome(final BlockPos p, final BiomeType type)
    {
        this.setBiome(p.getX(), p.getY(), p.getZ(), type);
    }

    public void setBiome(final int x, final int y, final int z, final BiomeType biome)
    {
        final int index = TerrainSegment.globalToIndex(x, y, z);
        this.biomes[index] = biome.getType();
        if (TerrainSegment.saveChecker.test(biome)) this.toSave = true;
    }

    public void setBiomes(final int[] biomes)
    {
        if (biomes.length == this.biomes.length) this.biomes = biomes;
        else for (int i = 0; i < biomes.length; i++)
        {
            if (i >= this.biomes.length) return;
            this.biomes[i] = biomes[i];
        }
    }

    public void setBiome(final Vector3 v, final BiomeType i)
    {
        this.setBiome(v.intX(), v.intY(), v.intZ(), i);
    }

    @Override
    public String toString()
    {
        String ret = "Terrian Segment " + this.chunkX + "," + this.chunkY + "," + this.chunkZ + " Centre:"
                + this.getCentre();
        final String eol = System.getProperty("line.separator");
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++)
        {
            String line = "[";
            for (int z = 0; z < 4; z++)
            {
                line = line + this.biomes[TerrainSegment.localToIndex(x, y, z)];
                if (z != 3) line = line + ", ";
            }
            line = line + "]";
            ret = ret + eol + line;
        }

        return ret;
    }
}
