package pokecube.core.ai.pathing;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;

public class WalkPathNavi extends GroundPathNavigation
{

    public WalkPathNavi(final Mob entitylivingIn, final Level worldIn)
    {
        super(entitylivingIn, worldIn);
    }

    @Override
    protected PathFinder createPathFinder(final int range)
    {
        this.nodeEvaluator = new AmphibiousNodeEvaluator(false);
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, range);
    }

}
