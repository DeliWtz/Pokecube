package pokecube.legends.blocks.normalblocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pokecube.legends.init.ParticleInit;

public class InfectedTorchWall extends WallTorchBlock
{
    protected final ParticleOptions particle;
    protected final ParticleOptions smokeParticle;
    public InfectedTorchWall(ParticleOptions particle, ParticleOptions smokeParticle, BlockBehaviour.Properties properties)
    {
        super(properties, particle);
        this.particle = particle;
        this.smokeParticle = smokeParticle;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource rand)
    {
        Direction direction = state.getValue(FACING);
        double d0 = pos.getX() + 0.5D;
        double d1 = pos.getY() + 0.75D;
        double d2 = pos.getZ() + 0.5D;
        Direction direction1 = direction.getOpposite();
        world.addParticle(this.smokeParticle, d0 + 0.27D * direction1.getStepX(), d1 + 0.22D,
                d2 + 0.27D * direction1.getStepZ(), 0.0D, 0.0D, 0.0D);
        world.addParticle(ParticleInit.INFECTED_FIRE_FLAME.get(), d0 + 0.27D * direction1.getStepX(),
                d1 + 0.22D, d2 + 0.27D * direction1.getStepZ(), 0.0D, 0.0D, 0.0D);
    }
}
