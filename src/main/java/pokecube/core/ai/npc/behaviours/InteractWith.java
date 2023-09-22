package pokecube.core.ai.npc.behaviours;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InteractWith
{
    public static <T extends LivingEntity> BehaviorControl<LivingEntity> of(Predicate<T> targetMatch, int range,
            MemoryModuleType<T> memory, float speed, int max_range)
    {
        return of(targetMatch, range, (user) -> {
            return true;
        }, (target) -> {
            return true;
        }, memory, speed, max_range);
    }

    @SuppressWarnings("unchecked")
    public static <E extends LivingEntity, T extends LivingEntity> BehaviorControl<E> of(Predicate<T> targetMatch,
            int range, Predicate<E> matcher_1, Predicate<T> matcher_2, MemoryModuleType<T> memory, float speed,
            int maxrange)
    {
        int i = range * range;
        var final_target_match = targetMatch.and(matcher_2);
        Predicate<LivingEntity> predicate = (target) -> {
            return final_target_match.test((T) target);
        };
        return BehaviorBuilder.create((mob) -> {
            return mob
                    .group(mob.registered(memory), mob.registered(MemoryModuleType.LOOK_TARGET),
                            mob.absent(MemoryModuleType.WALK_TARGET),
                            mob.present(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES))
                    .apply(mob, (memory_access, position_access, walk_access, visible_access) ->
                    {
                        return (level, user, timestamp) -> {
                            NearestVisibleLivingEntities nearestvisiblelivingentities = mob.get(visible_access);
                            if (matcher_1.test(user) && nearestvisiblelivingentities.contains(predicate))
                            {
                                Optional<LivingEntity> optional = nearestvisiblelivingentities.findClosest((target) -> {
                                    return target.distanceToSqr(user) <= (double) i && predicate.test(target);
                                });
                                optional.ifPresent((target) -> {
                                    memory_access.set((T) target);
                                    position_access.set(new EntityTracker(target, true));
                                    walk_access.set(new WalkTarget(new EntityTracker(target, false), speed, maxrange));
                                });
                                return true;
                            }
                            else
                            {
                                return false;
                            }
                        };
                    });
        });
    }
}