package pokecube.core.entity.pokemobs.helper;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntitySize;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.Pose;
import net.minecraft.entity.passive.ShoulderRidingEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.World;
import pokecube.core.PokecubeCore;
import pokecube.core.database.PokedexEntry;
import pokecube.core.database.pokedex.PokedexEntryLoader.BodyNode;
import pokecube.core.database.pokedex.PokedexEntryLoader.BodyPart;
import pokecube.core.interfaces.pokemob.ai.CombatStates;
import thut.api.AnimatedCaps;
import thut.api.entity.IAnimated;
import thut.api.maths.vecmath.Matrix3f;
import thut.api.maths.vecmath.Vector3f;

public abstract class PokemobHasParts extends PokemobCombat
{
    private PokemobPart[] parts;

    float colWidth  = 0;
    float colHeight = 0;

    float last_size = 0;

    final Matrix3f rot = new Matrix3f();
    final Vector3f r   = new Vector3f();

    String effective_pose;

    Map<String, PokemobPart[]> partMap = Maps.newHashMap();

    public PokemobHasParts(final EntityType<? extends ShoulderRidingEntity> type, final World worldIn)
    {
        super(type, worldIn);
        this.initSizes(1);
    }

    private PokemobPart makePart(final BodyPart part, final float size)
    {
        final float dx = (float) (part.__pos__.x * size);
        final float dy = (float) (part.__pos__.y * size);
        final float dz = (float) (part.__pos__.z * size);

        final float sx = (float) (part.__size__.x * size);
        final float sy = (float) (part.__size__.y * size);
        final float sz = (float) (part.__size__.z * size);

        final float dw = Math.max(sx, sz);
        final float dh = sy;

        return new PokemobPart(this, dw, dh, dx, dy, dz);
    }

    private void addPart(final String key, final float size, final BodyNode node)
    {
        final PokemobPart[] parts = new PokemobPart[node.parts.size()];
        for (int i = 0; i < parts.length; i++)
            parts[i] = this.makePart(node.parts.get(i), size);
        this.partMap.put(key, parts);
    }

    protected void initSizes(final float size)
    {
        if (size == this.last_size) return;
        this.last_size = size;
        final PokedexEntry entry = this.pokemobCap.getPokedexEntry();

        if (entry.poseShapes != null)
        {
            this.partMap.clear();
            for (final Entry<String, BodyNode> s : entry.poseShapes.entrySet())
                this.addPart(s.getKey(), size, s.getValue());
        }

        final float maxH = (float) PokecubeCore.getConfig().largeMobForSplit;
        final float maxW = (float) PokecubeCore.getConfig().largeMobForSplit;
        float width = entry.width * size;
        float length = entry.length * size;
        float height = entry.height * size;

        this.colWidth = width;
        this.colHeight = height;

        if (height > maxH || width > maxW || length > maxW)
        {
            final int nx = MathHelper.ceil(width / maxW);
            final int nz = MathHelper.ceil(length / maxH);
            final int ny = MathHelper.ceil(height / maxW);

            final float dx = width / nx;
            final float dy = height / ny;
            final float dz = length / nz;

            this.parts = new PokemobPart[nx * ny * nz];
            final float dw = Math.max(width / nx, length / nz);
            final float dh = dy;
            int i = 0;

            for (int y = 0; y < ny; y++)
                for (int x = 0; x < nx; x++)
                    for (int z = 0; z < nz; z++)
                        this.parts[i++] = new PokemobPart(this, dw, dh, x * dx - nx * dx / 2f, y * dy, z * dz - nz * dz
                                / 2f);

            this.colWidth = Math.min(1, maxW);
            this.colHeight = Math.min(1, maxH);
            this.ignoreFrustumCheck = true;
        }
        else
        {
            this.parts = new PokemobPart[0];
            this.ignoreFrustumCheck = false;
        }
        if (!this.partMap.containsKey("idle")) this.partMap.put("idle", this.parts);

        float minX = 0;
        float minY = 0;
        float minZ = 0;
        float maxX = 0;
        float maxY = 0;
        float maxZ = 0;
        int n = 0;
        for (final PokemobPart[] parts : this.partMap.values())
            for (final PokemobPart part : parts)
            {
                n++;
                minX = Math.min(minX, part.r0.x - part.width);
                minZ = Math.min(minZ, part.r0.z - part.width);
                minY = Math.min(minY, part.r0.y);
                maxX = Math.max(maxX, part.r0.x + part.width);
                maxZ = Math.max(maxZ, part.r0.z + part.width);
                maxY = Math.max(maxY, part.r0.y + part.height);
            }

        if (n != 0)
        {
            height = maxY - minY;
            width = maxX - minX;
            length = maxZ - minZ;
        }
        // This needs the larger bounding box regardless of parts, so that the
        // lookup finds the parts at all for things like projectile impact
        // calculations.
        this.size = EntitySize.fixed(Math.max(width, length), height);
        this.recalculateSize();
    }

    @Override
    public boolean isMultipartEntity()
    {
        return this.parts.length > 0;
    }

    @Override
    public PokemobPart[] getParts()
    {
        // This only does something complex if the parts have changed, otherwise
        // it just ensures their locations are synced to us.
        this.updatePartsPos();
        return this.parts;
    }

    @Override
    public boolean canBePushed()
    {
        return !this.isMultipartEntity() && super.canBePushed();
    }

    @Override
    protected void collideWithNearbyEntities()
    {
        if (!this.isMultipartEntity()) super.collideWithNearbyEntities();
    }

    @Override
    public void applyEntityCollision(final Entity entityIn)
    {
        if (entityIn.isEntityEqual(this)) return;
        if (this.isMultipartEntity())
        {
            for (final PokemobPart part : this.getParts())
                if (part.getBoundingBox().intersects(entityIn.getBoundingBox())) part.applyEntityCollision(entityIn);
        }
        else super.applyEntityCollision(entityIn);
    }

    /**
     * Returns true if Entity argument is equal to this Entity
     */
    @Override
    public boolean isEntityEqual(final Entity entityIn)
    {
        return this == entityIn || entityIn instanceof PokemobPart && ((PokemobPart) entityIn).base == this;
    }

    @Override
    public void livingTick()
    {
        this.updatePartsPos();
        super.livingTick();
    }

    protected void updatePartsPos()
    {
        float size = this.pokemobCap.getSize();
        if (this.pokemobCap.getCombatState(CombatStates.DYNAMAX)) size = 5 / this.pokemobCap.getPokedexEntry().height;
        this.initSizes(size);

        // check if effective_pose needs updating
        final IAnimated holder = AnimatedCaps.getAnimated(this);
        if (holder != null)
        {
            final List<String> anims = holder.getChoices();
            this.effective_pose = "idle";
            for (final String s : anims)
                if (this.partMap.containsKey(s))
                {
                    this.effective_pose = s;
                    break;
                }
            // Update the partmap if we know about this pose.
            if (this.partMap.containsKey(this.effective_pose)) this.parts = this.partMap.get(this.effective_pose);
        }

        if (this.parts.length > 0)
        {
            final Vector3d v = this.getPositionVec();
            this.r.set((float) v.getX(), (float) v.getY(), (float) v.getZ());
            final Vector3d dr = new Vector3d(this.r.x - this.lastTickPosX, this.r.y - this.lastTickPosY, this.r.z
                    - this.lastTickPosZ);
            this.rot.rotY((float) Math.toRadians(180 - this.rotationYaw));
            for (final PokemobPart p : this.parts)
                p.update(this.rot, this.r, dr);
        }
    }

    @Override
    public void move(final MoverType typeIn, final Vector3d pos)
    {
        if (this.parts.length == 0)
        {
            super.move(typeIn, pos);
            return;
        }
        final EntitySize backup = this.size;
        this.size = new EntitySize(this.colWidth, this.colHeight, true);
        this.recalculateSize();
        super.move(typeIn, pos);
        this.size = backup;
        this.recalculateSize();
    }

    @Override
    public float getHeight()
    {
        return this.colHeight;
    }

    @Override
    public float getWidth()
    {
        return this.colWidth;
    }

    // ================= Pose Related =====================

    ImmutableList<Pose> poses = ImmutableList.copyOf(Pose.values());

    @Override
    public ImmutableList<Pose> getAvailablePoses()
    {
        return this.poses;
    }

    @Override
    public AxisAlignedBB getPoseAABB(final Pose pose)
    {
        return super.getPoseAABB(pose);
    }

    @Override
    public void setPose(final Pose poseIn)
    {
        super.setPose(poseIn);
    }

    @Override
    public EntitySize getSize(final Pose poseIn)
    {
        return this.size;
    }
}
