package thut.api.block.flowing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;
import thut.api.item.ItemList;
import thut.api.level.terrain.TerrainChecker;
import thut.api.maths.Vector3;
import thut.core.common.ThutCore;

public interface IFlowingBlock
{
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    public static final BooleanProperty FALLING = BlockStateProperties.FALLING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final IntegerProperty VISCOSITY = IntegerProperty.create("viscosity", 0, 15);

    public static final ResourceLocation DUSTREPLACEABLE = new ResourceLocation("thutcore:dust_replace");
    public static final ResourceLocation NOTDUSTREPLACEABLE = new ResourceLocation("thutcore:no_dust_replace");

    public static VoxelShape[] makeShapes()
    {
        VoxelShape[] SHAPES = new VoxelShape[16];
        for (int i = 0; i < 16; i++)
        {
            SHAPES[i] = Block.box(0.0D, 0.0D, 0.0D, 16.0D, (i + 1), 16.0D);
        }
        return SHAPES;
    }

    public static final VoxelShape[] SHAPES = makeShapes();

    @SuppressWarnings(
    { "unchecked", "rawtypes" })
    public static BlockState copyValidTo(BlockState from, BlockState to)
    {
        for (Property p : from.getProperties())
        {
            if (to.hasProperty(p)) to = to.setValue(p, from.getValue(p));
        }
        return to;
    }

    default Block thisBlock()
    {
        return (Block) this;
    }

    Block getAlternate();

    int getFlowRate();

    int getFallRate();

    default int getSlope(BlockState state)
    {
        return state.hasProperty(VISCOSITY) ? state.getValue(VISCOSITY) : 0;
    }

    default boolean isFullBlock()
    {
        return false;
    }

    default boolean flows()
    {
        return true;
    }

    default boolean flows(BlockState state)
    {
        return flows();
    }

    default boolean isFalling(BlockState state)
    {
        if (!state.hasProperty(FALLING)) return false;
        return state.getValue(FALLING);
    }

    default boolean isStableBelow(BlockState state, BlockPos pos, ServerLevel level)
    {
        int amt = getExistingAmount(state, pos, level);
        if (TerrainChecker.isLeaves(state) || TerrainChecker.isWood(state)) return false;
        if (amt == 16 && state.getBlock() instanceof IFlowingBlock b && b.isFullBlock() && !b.flows(state)) return true;
        return (amt == -1);
    }

    default BlockState makeFalling(BlockState state, boolean falling)
    {
        if (this.isFullBlock())
        {
            BlockState b = falling ? this.getAlternate().defaultBlockState() : thisBlock().defaultBlockState();
            b = copyValidTo(state, b);
            b = this.setAmount(b, 16);
            if (b.hasProperty(FALLING)) b = b.setValue(FALLING, falling);
            return b;
        }
        return state.setValue(FALLING, falling);
    }

    default BlockState setAmount(BlockState state, int amt)
    {
        if (state.getBlock() instanceof IFlowingBlock b && b != this)
        {
            return b.setAmount(state, amt);
        }
        if (!(state.getBlock() instanceof IFlowingBlock)) return state;

        if (amt == 0) return empty(state);;

        if (this.isFullBlock())
        {
            if (amt == 16) return thisBlock().defaultBlockState();
            BlockState b = this.getAlternate().defaultBlockState();
            b = copyValidTo(state, b);
            return b.setValue(LAYERS, amt);
        }
        if (amt == 16 && !isFalling(state))
        {
            BlockState b = this.getAlternate().defaultBlockState();
            b = copyValidTo(state, b);
            return b;
        }
        return state.setValue(LAYERS, amt);
    }

    default BlockState empty(BlockState state)
    {
        if (state.hasProperty(WATERLOGGED) && state.getValue(WATERLOGGED))
            return Fluids.WATER.defaultFluidState().createLegacyBlock();
        return Blocks.AIR.defaultBlockState();
    }

    default void onStableTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        int dust = getExistingAmount(state, pos, level);
        if (dust == 16 && state.hasProperty(LAYERS) && getAlternate() != thisBlock())
        {
            level.setBlock(pos, getAlternate().defaultBlockState(), 2);
        }
    }

    default BlockState tryFall(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        boolean falling = isFalling(state);

        // Try down first;
        int amountHere = getExistingAmount(state, pos, level);

        BlockPos belowPos = pos.below();
        BlockState stateBelow = level.getBlockState(belowPos);
        int amountBelow = getExistingAmount(stateBelow, belowPos, level);
        boolean belowFalling = isFalling(stateBelow);

        if (!this.canReplace(stateBelow))
        {
            FluidState us = state.getFluidState();
            FluidState belowFluid = level.getFluidState(belowPos);
            boolean fluidCheck = us.holder().value() != belowFluid.holder().value();
            fluidCheck &= !level.getFluidState(belowPos).isEmpty();

            boolean shouldBeFalling = belowFalling || fluidCheck || (amountBelow < 16 && amountBelow > 0);
            if (shouldBeFalling && !falling)
            {
                BlockState fall = makeFalling(state, true);
                if (fall != state)
                {
                    level.setBlock(pos, state = fall, 2);
                    level.scheduleTick(pos.immutable(), thisBlock(), getFallRate());
                    return state;
                }
            }
            else if (!shouldBeFalling && falling)
            {
                BlockState fall = makeFalling(state, false);
                if (fall != state) level.setBlock(pos, state = fall, 2);
                return state;
            }
        }

        if (falling || !state.hasProperty(FALLING))
        {
            if ((amountBelow < 0 || amountBelow == 16))
            {
                if (!belowFalling)
                {
                    if (falling)
                    {
                        level.setBlock(pos, state = makeFalling(state, false), 2);
                    }
                    return state;
                }
            }
            else
            {
                int total = amountHere + amountBelow;
                int diff = 16 - amountBelow;
                BlockState newBelow;
                if (total <= 16)
                {
                    newBelow = getFlowResult(setAmount(state, total), stateBelow, belowPos, level);
                    if (newBelow != stateBelow)
                    {
                        state = setAmount(state, 0);
                        // Sanity check:
                        int aB = getAmount(newBelow);
                        int aH = getAmount(state);

                        if (aB + aH != total)
                            ThutCore.LOGGER.error("Error falling down {}, fluid not conserved!", this);

                        level.setBlock(belowPos, newBelow, 2);
                        level.scheduleTick(belowPos, newBelow.getBlock(), getFallRate());
                        level.setBlock(pos, state, 2);
                        return state;
                    }
                }
                else if (amountHere - diff >= 0)
                {
                    BlockState b2 = getAlternate().defaultBlockState();
                    b2 = copyValidTo(state, b2);
                    newBelow = getFlowResult(b2, stateBelow, belowPos, level);

                    if (newBelow != stateBelow)
                    {
                        newBelow = setAmount(newBelow, 16);
                        state = setAmount(state, amountHere - diff);

                        // Sanity check:
                        int aB = getAmount(newBelow);
                        int aH = getAmount(state);

                        if (aB + aH != total)
                            ThutCore.LOGGER.error("Error merging down {}, fluid not conserved!", this);

                        level.setBlock(belowPos, newBelow, 2);
                        level.setBlock(pos, state, 2);
                        level.scheduleTick(pos, thisBlock(), getFallRate());
                        level.scheduleTick(belowPos, newBelow.getBlock(), getFallRate());
                        return state;
                    }
                }
            }
        }
        if (amountBelow >= 0 && amountBelow < 16)
        {
            BlockState fall = makeFalling(state, true);
            if (fall != state)
            {
                level.setBlock(pos, state = fall, 2);
                level.scheduleTick(pos.immutable(), thisBlock(), getFallRate());
            }
        }
        return state;
    }

    default BlockState trySpread(BlockState flowFrom, ServerLevel level, BlockPos pos, RandomSource random)
    {
        int dust = getExistingAmount(flowFrom, pos, level);
        int slope = getSlope(flowFrom);

        if (dust >= slope)
        {
            Vector3 v = new Vector3().set(pos);
            BlockState flowInto = null;
            Direction dir = null;

            int existing = dust;
            int amt = 0;

            int rng = random.nextInt(100);

            for (int i = 0; i < Direction.values().length; i++)
            {
                int index = (i + rng) % Direction.values().length;
                Direction d = Direction.values()[index];
                if (d == Direction.DOWN || d == Direction.UP) continue;
                v.set(d).addTo(pos.getX(), pos.getY(), pos.getZ());
                flowInto = v.getBlockState(level);
                amt = getExistingAmount(flowInto, v.getPos(), level);
                if (amt == -1 || amt > dust - slope) continue;
                existing += amt;
                dir = d;
                break;
            }
            if (dir != null && amt != dust)
            {
                int left = existing;
                int next = 0;

                next = (existing - slope) / 2;
                left = existing - next;

                if (slope == 0 && dust > 1 && left - next == 1)
                {
                    int tmp = left;
                    left = next;
                    next = tmp;
                }

                if (next > 0 && left != dust)
                {
                    BlockState flowRemains = setAmount(flowFrom, left);
                    BlockPos pos2 = v.getPos();

                    BlockState flowState = setAmount(flowFrom, next);
                    BlockState destState = getFlowResult(flowState, flowInto, pos2, level);

                    if (destState != flowInto)
                    {

                        int aB = getAmount(destState);
                        int aH = getAmount(flowRemains);

                        if (aB + aH != existing)
                            ThutCore.LOGGER.error("Error falling down {}, fluid not conserved!", this);

                        level.setBlock(pos, flowRemains, 2);
                        level.setBlock(pos2, destState, 2);
                        level.scheduleTick(pos.immutable(), flowRemains.getBlock(), getFlowRate());
                        level.scheduleTick(pos2, destState.getBlock(), getFlowRate());
                        return flowRemains;
                    }
                }
            }
        }
        return flowFrom;
    }

    default int getExistingAmount(BlockState state, BlockPos pos, ServerLevel level)
    {
        return getAmount(state);
    }

    default int getAmount(BlockState state)
    {
        if (state.getBlock() instanceof IFlowingBlock b)
        {
            if (b != this) return b.getAmount(state);
            if (b.isFullBlock()) return 16;
            return state.hasProperty(LAYERS) ? state.getValue(LAYERS) : b.flows(state) ? 16 : -1;
        }
        return canReplace(state) ? 0 : -1;
    }

    default boolean canReplace(BlockState state, BlockPos pos, ServerLevel level)
    {
        return canReplace(state);
    }

    default boolean canReplace(BlockState state)
    {
        if (state.isAir()) return true;
        if (ItemList.is(NOTDUSTREPLACEABLE, state)) return false;
        if (state.canBeReplaced(Fluids.FLOWING_WATER)) return true;
        return ItemList.is(DUSTREPLACEABLE, state);
    }

    /**
     * 
     * @param flowState - This is the fractional state which would be placed if
     *                  destState is air
     * @param destState - This is the state we are flowing into
     * @param posTo     - Location of the state we are flowing int
     * @param level     - level involved in the slow
     * @return flowState modified based on destState, or destState if no flow
     *         should occur
     */
    default BlockState getFlowResult(BlockState flowState, BlockState destState, BlockPos posTo, ServerLevel level)
    {
        FluidState into = destState.getFluidState();
        // first lets ensure waterlogging is kept if we are flowing into water.
        if ((into.is(Fluids.WATER) || (destState.hasProperty(WATERLOGGED) && destState.getValue(WATERLOGGED)))
                && flowState.hasProperty(WATERLOGGED))
        {
            flowState = flowState.setValue(WATERLOGGED, true);
        }
        if (canFlowInto(flowState, destState, posTo, level)) return flowState;
        return destState;
    }

    default boolean canFlowInto(BlockState here, BlockState other, BlockPos posTo, ServerLevel level)
    {
        return canReplace(other, posTo, level) || other.getBlock() == here.getBlock();
    }

    default void updateNearby(BlockPos centre, ServerLevel level, int tickRate)
    {}

    default void reScheduleTick(BlockState state, ServerLevel level, BlockPos pos)
    {
        if (!level.getBlockTicks().willTickThisTick(pos, state.getBlock()) && state.isRandomlyTicking())
            level.scheduleTick(pos, state.getBlock(), isFalling(state) ? getFallRate() : getFlowRate());
    }

    default void doTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!this.flows(state)) return;
        boolean debug = false;
        if (debug) level.getProfiler().push("flowing_block:" + this.getClass());
        int amt = getAmount(state);
        IFlowingBlock flower = this;

        // Try down first;
        if (debug) level.getProfiler().push("fall_check");
        BlockState rem = flower.tryFall(state, level, pos, random);
        if (rem.getBlock() instanceof IFlowingBlock flow) flower = flow;
        if (debug) level.getProfiler().pop();
        // Next try spreading sideways
        if (debug) level.getProfiler().push("spread_check");
        if (getAmount(rem) > 0) rem = flower.trySpread(rem, level, pos, random);
        if (rem.getBlock() instanceof IFlowingBlock flow) flower = flow;
        // Then apply any checks for if we were stable
        if (debug) level.getProfiler().push("stability_check:" + flower.getClass());
        if (getAmount(rem) == amt) flower.onStableTick(rem, level, pos, random);
        else if (getAmount(rem) > 0) flower.reScheduleTick(rem, level, pos);
        if (debug) level.getProfiler().pop();

        if (debug) level.getProfiler().pop();
    }

}