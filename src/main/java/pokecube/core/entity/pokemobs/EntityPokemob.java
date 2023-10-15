/**
 *
 */
package pokecube.core.entity.pokemobs;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.network.NetworkHooks;
import pokecube.api.PokecubeAPI;
import pokecube.api.data.PokedexEntry;
import pokecube.api.data.PokedexEntry.SpawnData;
import pokecube.api.data.spawns.SpawnBiomeMatcher;
import pokecube.api.data.spawns.SpawnCheck;
import pokecube.api.entity.pokemob.IPokemob;
import pokecube.api.entity.pokemob.PokemobCaps;
import pokecube.api.entity.pokemob.ai.AIRoutine;
import pokecube.api.entity.pokemob.ai.CombatStates;
import pokecube.api.entity.pokemob.ai.GeneralStates;
import pokecube.api.events.pokemobs.FaintEvent;
import pokecube.api.events.pokemobs.SpawnEvent;
import pokecube.api.events.pokemobs.SpawnEvent.SpawnContext;
import pokecube.api.events.pokemobs.SpawnEvent.Variance;
import pokecube.api.moves.Battle;
import pokecube.api.utils.PokeType;
import pokecube.api.utils.TagNames;
import pokecube.api.utils.Tools;
import pokecube.core.PokecubeCore;
import pokecube.core.database.Database;
import pokecube.core.entity.pokemobs.helper.PokemobRidable;
import pokecube.core.eventhandlers.SpawnHandler;
import pokecube.core.handlers.playerdata.PlayerPokemobCache;
import pokecube.core.impl.PokecubeMod;
import pokecube.core.init.Config;
import pokecube.core.init.EntityTypes;
import pokecube.core.items.pokemobeggs.EntityPokemobEgg;
import pokecube.core.items.pokemobeggs.ItemPokemobEgg;
import pokecube.core.utils.PokemobTracker;
import thut.api.AnimatedCaps;
import thut.api.ThutCaps;
import thut.api.entity.IAnimated;
import thut.api.entity.genetics.IMobGenetics;
import thut.api.item.ItemList;
import thut.api.maths.Vector3;
import thut.api.world.mobs.data.Data;
import thut.core.common.world.mobs.data.DataSync_Impl;

public class EntityPokemob extends PokemobRidable
{
    static ResourceLocation WALL_CLIMBERS = new ResourceLocation(PokecubeMod.ID, "wall_climbing");

    private static final EntityDataAccessor<Byte> CLIMBING = SynchedEntityData.defineId(EntityPokemob.class,
            EntityDataSerializers.BYTE);

    public EntityPokemob(final EntityType<? extends TamableAnimal> type, final Level world)
    {
        super(type, world);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(EntityPokemob.CLIMBING, (byte) 0);
    }

    @Override
    public AgeableMob getBreedOffspring(final ServerLevel p_241840_1_, final AgeableMob ageable)
    {
        final IPokemob other = PokemobCaps.getPokemobFor(ageable);
        if (other == null) return null;
        final EntityPokemobEgg egg = EntityTypes.getEgg().create(this.level());
        egg.setStackByParents(this, other);
        return egg;
    }

    @Override
    public boolean isInWall()
    {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater()
    {
        return this.pokemobCap.swims() || this.pokemobCap.canUseDive()
                || this.pokemobCap.isType(PokeType.getType("water"));
    }

    @Override
    protected void tickDeath()
    {
        ++this.deathTime;
        if (!(this.level() instanceof ServerLevel)) return;

        if (this.isVehicle()) this.ejectPassengers();

        int deadTimer = PokecubeCore.getConfig().deadDespawnTimer;
        int reviveTimer = PokecubeCore.getConfig().deadReviveTimer;

        final boolean isTamed = this.pokemobCap.getOwnerId() != null;
        boolean fullHeal = !isTamed;
        boolean despawn = isTamed ? PokecubeCore.getConfig().tameDeadDespawn : PokecubeCore.getConfig().wildDeadDespawn;
        this.setNoGravity(false);
        boolean poofDisabled = false;
        boolean noPoof = this.getPersistentData().getBoolean(TagNames.NOPOOF)
                || (poofDisabled = !this.pokemobCap.isRoutineEnabled(AIRoutine.POOFS));
        if (noPoof)
        {
            fullHeal = true;
            if (poofDisabled) reviveTimer = PokecubeCore.getConfig().noPoofReviveTimer;
        }
        if (this.deathTime >= deadTimer)
        {
            final FaintEvent event = new FaintEvent(this.pokemobCap);
            PokecubeAPI.POKEMOB_BUS.post(event);
            final Result res = event.getResult();
            despawn = res == Result.DEFAULT ? despawn : res == Result.ALLOW;
            if (despawn && !noPoof) this.pokemobCap.onRecall(true);
            Battle battle = Battle.getBattle(this);
            if (battle != null) battle.removeFromBattle(this);
            for (int k = 0; k < 20; ++k)
            {
                final double d2 = this.random.nextGaussian() * 0.02D;
                final double d0 = this.random.nextGaussian() * 0.02D;
                final double d1 = this.random.nextGaussian() * 0.02D;
                this.level.addParticle(ParticleTypes.POOF,
                        this.getX() + this.random.nextFloat() * this.getBbWidth() * 2.0F - this.getBbWidth(),
                        this.getY() + this.random.nextFloat() * this.getBbHeight(),
                        this.getZ() + this.random.nextFloat() * this.getBbWidth() * 2.0F - this.getBbWidth(), d2, d0,
                        d1);
            }
        }
        if (this.deathTime >= reviveTimer && reviveTimer > 0)
        {
            if (this.getPersistentData().contains("pokecube:raid_boss"))
            {
                this.pokemobCap.onRecall(true);
                Battle battle = Battle.getBattle(this);
                if (battle != null) battle.removeFromBattle(this);
                for (int k = 0; k < 20; ++k)
                {
                    final double d2 = this.random.nextGaussian() * 0.02D;
                    final double d0 = this.random.nextGaussian() * 0.02D;
                    final double d1 = this.random.nextGaussian() * 0.02D;
                    this.level.addParticle(ParticleTypes.POOF,
                            this.getX() + this.random.nextFloat() * this.getBbWidth() * 2.0F - this.getBbWidth(),
                            this.getY() + this.random.nextFloat() * this.getBbHeight(),
                            this.getZ() + this.random.nextFloat() * this.getBbWidth() * 2.0F - this.getBbWidth(), d2,
                            d0, d1);
                }
            }
            else
            {
                this.pokemobCap.revive(fullHeal);
                // If we revive naturally, we remove this tag, it only applies
                // for
                // forced revivals
                this.getPersistentData().remove(TagNames.REVIVED);
            }
        }
    }

    @Override
    public void die(final DamageSource cause)
    {
        super.die(cause);
        this.pokemobCap.setCombatState(CombatStates.FAINTED, true);
    }

    @Override
    public void travel(Vec3 dr)
    {
        // Swimming mobs get their own treatment while swimming
        if (this.isControlledByLocalInstance() && this.isInWater() && this.pokemobCap.swims())
        {
            this.moveRelative(this.getSpeed(), dr);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.8D));
        }
        // Otherwise just act like vanilla
        else super.travel(dr);
    }

    @Override
    @Nullable
    protected ResourceLocation getDefaultLootTable()
    {
        if (this.getPersistentData().getBoolean(TagNames.CLONED) && !PokecubeCore.getConfig().clonesDrop) return null;
        if (this.getPersistentData().getBoolean(TagNames.NODROP)) return null;
        if (this.level() instanceof ServerLevel level && Config.Rules.dropLoot(level))
            return this.pokemobCap.getPokedexEntry().lootTable;
        else return null;
    }

    @Override
    public ItemStack getPickedResult(final HitResult target)
    {
        return ItemPokemobEgg.getEggStack(this.pokemobCap);
    }

    @Override
    public SpawnGroupData finalizeSpawn(final ServerLevelAccessor worldIn, final DifficultyInstance difficultyIn,
            final MobSpawnType reason, final SpawnGroupData spawnDataIn, final CompoundTag dataTag)
    {
        final IPokemob pokemob = PokemobCaps.getPokemobFor(this);
        if (pokemob == null || !(worldIn instanceof Level))
            return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
        final PokedexEntry pokeEntry = pokemob.getPokedexEntry();
        final SpawnData entry = pokeEntry.getSpawnData();
        if (entry == null) return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
        final Vector3 loc = new Vector3().set(this);

        SpawnContext context = new SpawnContext(pokemob);
        SpawnCheck checker = new SpawnCheck(loc, worldIn);

        final SpawnBiomeMatcher matcher = entry.getMatcher(context, checker);

        final int orig_override = entry.getLevel(matcher);
        int overrideLevel = orig_override;
        final Variance variance = entry.getVariance(matcher);
        if (variance != null) overrideLevel = variance.apply(overrideLevel);

        if (pokemob != null)
        {
            final long time = System.nanoTime();
            int maxXP = 10;
            int level = 1;
            if (orig_override == -1) level = SpawnHandler.getSpawnLevel(context, variance, overrideLevel);
            else
            {
                final SpawnEvent.PickLevel event = new SpawnEvent.PickLevel(context, overrideLevel, variance);
                PokecubeAPI.POKEMOB_BUS.post(event);
                level = event.getLevel();
            }
            maxXP = Tools.levelToXp(pokemob.getPokedexEntry().getEvolutionMode(), level);
            pokemob.getEntity().getPersistentData().putInt("spawnExp", maxXP);
            final double dt = (System.nanoTime() - time) / 10e3D;
            if (PokecubeCore.getConfig().debug_spawning && dt > 100)
            {
                final String toLog = "location: %1$s took: %2$s\u00B5s to spawn Init for %3$s";
                PokecubeAPI.logInfo(String.format(toLog, loc.getPos(), dt, pokemob.getDisplayName().getString()));
            }
        }
        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
    }

    @Override
    public void readSpawnData(final FriendlyByteBuf data)
    {
        // Read the datasync stuff
        final List<Data<?>> data_list = Lists.newArrayList();
        final byte num = data.readByte();
        if (num > 0)
        {
            for (int i = 0; i < num; i++)
            {
                final int uid = data.readInt();
                try
                {
                    final Data<?> val = DataSync_Impl.makeData(uid);
                    val.read(data);
                    data_list.add(val);
                }
                catch (final Exception e)
                {
                    PokecubeAPI.LOGGER.error("Error reading synced data value", e);
                }
            }
            this.pokemobCap.dataSync().update(data_list);
        }
        this.seatCount = data.readInt();
        final FriendlyByteBuf buffer = new FriendlyByteBuf(data);

        CompoundTag tag = buffer.readNbt();
        final ListTag list = (ListTag) tag.get("g");
        final IMobGenetics genes = this.getCapability(ThutCaps.GENETICS_CAP).orElse(this.pokemobCap.genes);
        genes.deserializeNBT(list);
        this.pokemobCap.read(tag.getCompound("p"));
        this.pokemobCap.onGenesChanged();
        this.canUpdate(tag.getBoolean("u"));
        tag = buffer.readNbt();
        if (!tag.isEmpty()) this.getPersistentData().put("url_model", tag);
    }

    @Override
    public boolean causeFallDamage(final float distance, final float damageMultiplier, final DamageSource source)
    {
        // TODO maybe do something here?
        // Vanilla plays sound and does damage, but only plays the sound if
        // damage occurred, maybe we should just play the sound instead?
        return super.causeFallDamage(distance, damageMultiplier, source);
    }

    @Override
    protected void checkFallDamage(final double y, final boolean onGroundIn, final BlockState state, final BlockPos pos)
    {}

    @Override
    protected void jumpInLiquid(final TagKey<Fluid> fluidTag)
    {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D,
                0.04F * this.getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get()).getValue(), 0.0D));
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.pokemobCap.getSound();
    }

    @Override
    protected float getSoundVolume()
    {
        return (float) PokecubeCore.getConfig().idleSoundVolume;
    }

    @Override
    public SoundSource getSoundSource()
    {
        return SoundSource.HOSTILE;
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return PokecubeCore.getConfig().idleSoundRate;
    }

    @Override
    public void onRemovedFromWorld()
    {
        PokemobTracker.removePokemob(this.pokemobCap);
        if (this.pokemobCap.isPlayerOwned() && this.pokemobCap.getOwnerId() != null)
            PlayerPokemobCache.UpdateCache(this.pokemobCap);
        super.onRemovedFromWorld();
    }

    @Override
    public void writeSpawnData(final FriendlyByteBuf data)
    {
        // Write the dataSync stuff
        final List<Data<?>> data_list = this.pokemobCap.dataSync().getAll();
        final byte num = (byte) data_list.size();
        data.writeByte(num);
        for (int i = 0; i < num; i++)
        {
            final Data<?> val = data_list.get(i);
            data.writeInt(val.getUID());
            val.write(data);
        }
        this.initSeats();
        data.writeInt(this.seatCount);
        this.pokemobCap.updateHealth();
        final IMobGenetics genes = this.getCapability(ThutCaps.GENETICS_CAP).orElse(this.pokemobCap.genes);
        final FriendlyByteBuf buffer = new FriendlyByteBuf(data);
        final ListTag list = genes.serializeNBT();
        CompoundTag nbt = new CompoundTag();
        nbt.put("p", this.pokemobCap.write());
        nbt.put("g", list);
        nbt.putBoolean("u", this.canUpdate());
        buffer.writeNbt(nbt);
        nbt = this.getPersistentData().getCompound("url_model");
        buffer.writeNbt(nbt);
    }

    // Methods for IMobColourable
    @Override
    public int getDyeColour()
    {
        return this.pokemobCap.getDyeColour();
    }

    @Override
    public int[] getRGBA()
    {
        return this.pokemobCap.getRGBA();
    }

    @Override
    public void setDyeColour(final int colour)
    {
        this.pokemobCap.setDyeColour(colour);
    }

    @Override
    public void setRGBA(final int... colours)
    {
        this.pokemobCap.setRGBA(colours);
    }

    @Override
    public boolean checkSpawnRules(final LevelAccessor worldIn, final MobSpawnType spawnReasonIn)
    {
        return true;
    }

    private int despawntimer = 0;

    @Override
    public boolean requiresCustomPersistence()
    {
        if (isPersistenceRequired() || super.requiresCustomPersistence()) return true;
        if (!(level instanceof ServerLevel level)) return true;

        final boolean despawns = Config.Rules.doDespawn(level);
        final boolean culls = Config.Rules.doCull(level);
        final boolean owned = this.pokemobCap.getOwnerId() != null;

        if (owned)
        {
            this.setPersistenceRequired();
            return true;
        }
        if (this.getPersistentData().contains(TagNames.NOPOOF)) return true;
        return !(despawns || culls);
    }

    private boolean cullCheck(double distanceToClosestPlayer)
    {
        if (this.pokemobCap.getOwnerId() != null || !(level instanceof ServerLevel level)) return false;
        final boolean noPoof = this.getPersistentData().getBoolean(TagNames.NOPOOF);
        if (noPoof) return false;
        distanceToClosestPlayer = Math.sqrt(distanceToClosestPlayer);
        if (Config.Rules.doCull(level, distanceToClosestPlayer)) return true;
        if (Config.Rules.doDespawn(level, distanceToClosestPlayer))
        {
            this.despawntimer--;
            if (this.despawntimer <= 0) return true;
            return false;
        }
        this.despawntimer = PokecubeCore.getConfig().despawnTimer;
        return false;
    }

    @Override
    public void checkDespawn()
    {
        if (this.level.getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful())
        {
            this.discard();
        }
        else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()
                && level() instanceof ServerLevel level)
        {
            Entity entity = level.getNearestPlayer(this, -1.0D);
            net.minecraftforge.eventbus.api.Event.Result result = net.minecraftforge.event.ForgeEventFactory
                    .canEntityDespawn(this, level);
            if (result == net.minecraftforge.eventbus.api.Event.Result.DENY)
            {
                noActionTime = 0;
                entity = null;
            }
            else if (result == net.minecraftforge.eventbus.api.Event.Result.ALLOW)
            {
                this.discard();
                entity = null;
            }
            if (entity != null)
            {
                double d0 = entity.distanceToSqr(this);
                if (this.removeWhenFarAway(d0))
                {
                    this.discard();
                }
            }
        }
        else
        {
            this.noActionTime = 0;
        }
    }

    @Override
    public boolean removeWhenFarAway(final double distanceToClosestPlayer)
    {
        return this.cullCheck(distanceToClosestPlayer);
    }

    @Override
    public void readAdditionalSaveData(final CompoundTag compound)
    {
        super.readAdditionalSaveData(compound);
        if (this.pokemobCap.getCustomHolder() != null && this.pokemobCap.getCustomHolder()._entry == Database.missingno)
            this.pokemobCap.getCustomHolder().setEntry(this.pokemobCap.getPokedexEntry());
        if (compound.contains("OwnerUUID")) try
        {
            final UUID id = UUID.fromString(compound.getString("OwnerUUID"));
            if (id != null)
            {
                this.setOwnerUUID(id);
                this.setTame(true);
            }
        }
        catch (final Exception e)
        {
            PokecubeAPI.LOGGER.error("Error recovering old owner!");
        }
    }

    private int climbDelay = 0;

    /**
     * Called to update the entity's position/logic.
     */
    @Override
    public void tick()
    {
        super.tick();
        if (!this.isOrderedToSit())
        {
            this.stopRiding();
        }
        if (!this.level.isClientSide)
        {
            boolean climb = this.horizontalCollision;
            if (climb && climbDelay-- < 0)
            {
                final Path p = this.getNavigation().getPath();
                climb = p.getNextNodePos().getY() >= this.getY();
            }
            else climbDelay = 5;
            this.setBesideClimbableBlock(climb);
        }
    }

    @Override
    public boolean canUpdate()
    {
        if (!super.canUpdate())
        {
            // Lets clear some values, so that we act as a statue, rather than a
            // living mob!
            this.hurtTime = 0;
            // We can use tick counts above this to adjust animations
            if (this.age < 1000) this.age = 1000;
            this.tickCount = this.age;
            // Make rots same as old
            this.xRotO = this.xRot;
            this.yRotO = this.yRot;

            this.xOld = this.xo;
            this.yOld = this.yo;
            this.zOld = this.zo;

            this.walkAnimation.setSpeed(0);
            this.animStepO = this.animStep;

            this.lerpX = 0;
            this.lerpXRot = 0;
            this.lerpY = 0;
            this.lerpYRot = 0;
            this.lerpZ = 0;
            this.lerpHeadSteps = 0;

            this.yBodyRotO = this.yBodyRot;
            this.yHeadRotO = this.yHeadRot;

            // No movement here
            this.setDeltaMovement(0, 0, 0);
            // Max absorption
            this.setAbsorptionAmount(Float.MAX_VALUE);
            // Max HP
            this.setHealth(Float.MAX_VALUE);
            // Clear owner
            this.pokemobCap.setOwner((UUID) null);
            // clear these as well so we don't have the effects
            this.pokemobCap.setGeneralState(GeneralStates.EXITINGCUBE, false);
            this.pokemobCap.setGeneralState(GeneralStates.EVOLVING, false);
            // No fire
            if (this.isOnFire()) this.clearFire();
            // No potion effects either
            this.removeAllEffects();
            return false;
        }
        return true;
    }

    @Override
    public void kill()
    {
        if (this.isInvulnerable())
        {
            PokecubeAPI.logInfo("Not deleting {} from /kill, as is marked as invulnerable!", this);
            return;
        }
        super.kill();
    }

    /**
     * Returns true if this entity should move as if it were on a ladder (either
     * because it's actually on a ladder, or for AI reasons)
     */
    @Override
    public boolean onClimbable()
    {
        return this.isBesideClimbableBlock();
    }

    /**
     * Returns true if the WatchableObject (Byte) is 0x01 otherwise returns
     * false. The WatchableObject is updated using setBesideClimableBlock.
     */
    public boolean isBesideClimbableBlock()
    {
        return (this.entityData.get(EntityPokemob.CLIMBING) & 1) != 0;
    }

    /**
     * Updates the WatchableObject (Byte) created in entityInit(), setting it to
     * 0x01 if par1 is true or 0x00 if it is false.
     */
    public void setBesideClimbableBlock(final boolean climbing)
    {
        // Only do this if tagged accordingly
        if (!ItemList.is(EntityPokemob.WALL_CLIMBERS, this)) return;

        byte b0 = this.entityData.get(EntityPokemob.CLIMBING);
        if (climbing) b0 = (byte) (b0 | 1);
        else b0 = (byte) (b0 & -2);

        this.entityData.set(EntityPokemob.CLIMBING, b0);
    }

    private IAnimated animationHolder;
    private boolean checkedAnim = false;

    @Override
    public boolean isFlying()
    {
        if (!checkedAnim)
        {
            animationHolder = AnimatedCaps.getAnimated(this);
            checkedAnim = true;
        }
        if (animationHolder != null) return animationHolder.getChoices().contains("flying");
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket()
    {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
