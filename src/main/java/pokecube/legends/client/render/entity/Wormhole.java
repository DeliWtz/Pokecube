package pokecube.legends.client.render.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.LivingRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import pokecube.legends.Reference;
import pokecube.legends.entity.WormholeEntity;
import thut.api.ModelHolder;
import thut.api.maths.Vector3;
import thut.core.client.render.animation.Animation;
import thut.core.client.render.animation.AnimationLoader;
import thut.core.client.render.animation.CapabilityAnimation.IAnimationHolder;
import thut.core.client.render.animation.IAnimationChanger;
import thut.core.client.render.model.IModel;
import thut.core.client.render.model.IModelRenderer;
import thut.core.client.render.model.ModelFactory;
import thut.core.client.render.texturing.IPartTexturer;
import thut.core.client.render.wrappers.ModelWrapper;

public class Wormhole extends LivingRenderer<WormholeEntity, ModelWrapper<WormholeEntity>> implements
        IModelRenderer<WormholeEntity>
{
    static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ID, "entity/textures/wormhole.png");
    static final ResourceLocation MODEL   = new ResourceLocation(Reference.ID, "entity/models/wormhole.x3d");
    static final ResourceLocation ANIM    = new ResourceLocation(Reference.ID, "entity/animations/wormhole.xml");

    private final HashMap<String, List<Animation>> anims = Maps.newHashMap();

    private IAnimationChanger changer = null;
    private IPartTexturer     texer   = null;
    private IAnimationHolder  holder  = null;

    final Vector3   rotPoint  = Vector3.getNewVector();
    private Vector3 offset    = Vector3.getNewVector();
    private Vector3 scale     = Vector3.getNewVector();
    private Vector5 rotations = new Vector5();

    public Wormhole(final EntityRendererManager manager)
    {
        super(manager, null, 0.0f);
        this.model = this.makeModel();
    }

    private ModelWrapper<WormholeEntity> makeModel()
    {
        final ModelHolder holder = new ModelHolder(Wormhole.MODEL, Wormhole.TEXTURE, Wormhole.ANIM, "ultra_wormhole");
        final ModelWrapper<WormholeEntity> model = new ModelWrapper<>(holder, this);
        ModelFactory.create(model.model, m ->
        {
            model.imodel = m;
            AnimationLoader.parse(holder, model, this);
        });
        return model;
    }

    @Override
    public void render(final WormholeEntity entity, final float p_225623_2_, final float p_225623_3_,
            final MatrixStack p_225623_4_, final IRenderTypeBuffer bufferIn, final int p_225623_6_)
    {
        this.model.setMob(entity, bufferIn, this.getTextureLocation(entity));
        super.render(entity, p_225623_2_, p_225623_3_, p_225623_4_, bufferIn, p_225623_6_);
    }

    @Override
    protected RenderType getRenderType(final WormholeEntity entity, final boolean bool_a, final boolean bool_b,
            final boolean bool_c)
    {
        final RenderType.State rendertype$state = RenderType.State.builder().setTextureState(
                new RenderState.TextureState(this.getTextureLocation(entity), false, false)).setTransparencyState(
                        new RenderState.TransparencyState("translucent_transparency", () ->
                        {
                            RenderSystem.enableBlend();
                            RenderSystem.defaultBlendFunc();
                        }, () ->
                        {
                            RenderSystem.disableBlend();
                        })).setDiffuseLightingState(new RenderState.DiffuseLightingState(true)).setAlphaState(
                                new RenderState.AlphaState(0.003921569F)).setCullState(new RenderState.CullState(false))
                .setLightmapState(new RenderState.LightmapState(true)).setOverlayState(new RenderState.OverlayState(
                        true)).createCompositeState(false);
        return RenderType.create("pokecube_legends:wormhole", DefaultVertexFormats.NEW_ENTITY, GL11.GL_TRIANGLES, 256,
                bool_a, bool_b, rendertype$state);
    }

    @Override
    public ResourceLocation getTextureLocation(final WormholeEntity mob)
    {
        return Wormhole.TEXTURE;
    }

    @Override
    protected boolean shouldShowName(final WormholeEntity entity)
    {
        return false;
    }

    @Override
    public IAnimationChanger getAnimationChanger()
    {
        return this.changer;
    }

    @Override
    public HashMap<String, List<Animation>> getAnimations()
    {
        return this.anims;
    }

    @Override
    public IPartTexturer getTexturer()
    {
        return this.texer;
    }

    @Override
    public boolean hasAnimation(final String phase, final Entity entity)
    {
        return this.getAnimations().containsKey(phase);
    }

    @Override
    public String getAnimation(final Entity entityIn)
    {
        if (entityIn instanceof WormholeEntity)
        {
            final WormholeEntity wormhole = (WormholeEntity) entityIn;
            final String state = wormhole.isIdle() ? "stable"
                    : wormhole.isClosing() ? "closing" : wormhole.isOpening() ? "opening" : "idle";
            return state;
        }

        final IAnimationHolder holder = this.getAnimationHolder();
        if (holder != null)
        {
            String result = holder.getAnimation(entityIn);
            if (result.isEmpty()) result = IModelRenderer.DEFAULTPHASE;
            return result;
        }
        return IModelRenderer.DEFAULTPHASE;
    }

    @Override
    public Vector3 getRotationOffset()
    {
        return this.offset;
    }

    @Override
    public Vector5 getRotations()
    {
        return this.rotations;
    }

    @Override
    public Vector3 getScale()
    {
        return this.scale;
    }

    @Override
    public void scaleEntity(final MatrixStack mat, final Entity entity, final IModel model, final float partialTick)
    {
        final float s = 1;
        float sx = (float) this.getScale().x;
        float sy = (float) this.getScale().y;
        float sz = (float) this.getScale().z;
        sx *= s;
        sy *= s;
        sz *= s;
        this.rotPoint.set(this.getRotationOffset()).scalarMultBy(s);
        model.setOffset(this.rotPoint);
        if (!this.getScale().isEmpty()) mat.scale(sx, sy, sz);
        else mat.scale(s, s, s);
    }

    @Override
    public void setAnimationHolder(final IAnimationHolder holder)
    {
        this.holder = holder;
        if (this.changer != null) this.changer.setAnimationHolder(holder);
        this.model.imodel.setAnimationHolder(holder);
    }

    @Override
    public IAnimationHolder getAnimationHolder()
    {
        return this.holder;
    }

    @Override
    public void setAnimationChanger(final IAnimationChanger changer)
    {
        this.changer = changer;
    }

    @Override
    public void setRotationOffset(final Vector3 offset)
    {
        this.offset = offset;
    }

    @Override
    public void setRotations(final Vector5 rotations)
    {
        this.rotations = rotations;
    }

    @Override
    public void setScale(final Vector3 scale)
    {
        this.scale = scale;
    }

    @Override
    public void setTexturer(final IPartTexturer texturer)
    {
        this.texer = texturer;
    }

    @Override
    public void updateModel(final HashMap<String, ArrayList<Vector5>> phaseList, final ModelHolder model)
    {
    }

}