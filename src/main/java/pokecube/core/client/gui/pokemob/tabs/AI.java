package pokecube.core.client.gui.pokemob.tabs;

import java.util.List;
import java.util.Locale;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.gui.components.AbstractSelectionList.Entry;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import pokecube.api.entity.pokemob.IPokemob;
import pokecube.api.moves.IMoveConstants.AIRoutine;
import pokecube.core.client.Resources;
import pokecube.core.client.gui.helper.ScrollGui;
import pokecube.core.client.gui.pokemob.GuiPokemob;
import pokecube.core.client.gui.pokemob.GuiPokemobHelper;
import pokecube.core.network.pokemobs.PacketAIRoutine;
import pokecube.core.network.pokemobs.PacketPokemobGui;
import thut.lib.TComponent;

public class AI extends Tab
{
    private static final ResourceLocation CHECK_TEX = new ResourceLocation("textures/gui/checkbox.png");

    private static record AIButton(Button button, AIRoutine routine)
    {
    }

    private static class AIEntry extends Entry<AIEntry>
    {
        final IPokemob pokemob;
        final AIButton[] buttons;
        final Screen parent;
        int top;

        public AIEntry(final Screen parent, final IPokemob pokemob, AIButton... buttons)
        {
            this.buttons = buttons;
            this.pokemob = pokemob;
            for (var button : buttons)
            {
                button.button().visible = false;
                button.button().active = false;
            }
            this.top = buttons[0].button().y;
            this.parent = parent;
        }

        @Override
        public void render(final PoseStack mat, final int slotIndex, final int y, final int x, final int listWidth,
                final int slotHeight, final int mouseX, final int mouseY, final boolean isSelected,
                final float partialTicks)
        {
            int dx = 0;
            int texW = 10;
            for (var holder : this.buttons)
            {
                var button = holder.button();
                AIRoutine routine = holder.routine();
                button.visible = false;
                button.active = false;

                if (y > this.top && y < this.top + 50)
                {
                    button.x = x + dx;
                    button.y = y;
                    button.visible = true;
                    button.active = true;
                    dx += button.getWidth();
                    final boolean state = this.pokemob.isRoutineEnabled(routine);

                    RenderSystem.setShader(GameRenderer::getPositionTexShader);
                    RenderSystem.setShaderTexture(0, CHECK_TEX);

                    mat.pushPose();
                    float s = 10f / 80f;
                    int sx = x + dx;
                    int sy = y + 0;
                    mat.translate(sx, sy, 0);
                    mat.scale(s, s, s);
                    mat.translate(-sx, -sy, 0);
                    int tx = 0;
                    int ty = state ? 80 : 0;
                    parent.blit(mat, x + dx, y, tx, ty, 80, 80);
                    mat.popPose();
                    dx += texW;
                }
                else
                {
                    button.visible = false;
                    button.active = false;
                }
            }
        }

    }

    ScrollGui<AIEntry> list;

    public AI(GuiPokemob parent)
    {
        super(parent, "ai");
        this.icon = Resources.TAB_ICON_AI;
    }

    @Override
    public void setEnabled(boolean active)
    {
        super.setEnabled(active);
        if (!active)
        {
            this.parent.children.remove(this.list);
        }
        else
        {
            this.parent.children.add(this.list);
            this.menu.setMode(PacketPokemobGui.AI);
        }
    }

    @Override
    public void init()
    {
        int xOffset = this.width / 2 - 10;
        int yOffset = this.height / 2 - 77;

        IPokemob pokemob = this.menu.pokemob;

        yOffset += 8;
        xOffset -= 17;
        this.list = new ScrollGui<>(this.parent, this.parent.minecraft, 110, 50, 10, xOffset, yOffset);

        this.list.scrollBarDx = 2;
        this.list.scrollBarDy = 4;

        this.list.smoothScroll = false;

        List<AIButton> thisRow = Lists.newArrayList();

        for (int i = 0; i < AIRoutine.values().length; i++)
        {
            AIRoutine routine = AIRoutine.values()[i];
            String name = routine.toString();
            if (!routine.isAllowed(pokemob)) continue;
            Component tooltip = TComponent.translatable("pokemob.gui.ai." + name.toLowerCase(Locale.ROOT));
            if (name.length() > 6) name = name.substring(0, 6);
            final Button button = new Button(xOffset, yOffset, 40, 10, TComponent.literal(name), b -> {
                final boolean state = !pokemob.isRoutineEnabled(routine);
                pokemob.setRoutineState(routine, state);
                PacketAIRoutine.sentCommand(pokemob, routine, state);
            }, (b, pose, x, y) -> {
                parent.renderTooltip(pose, tooltip, x, y);
            });
            AIButton toAdd = new AIButton(button, routine);
            thisRow.add(toAdd);
            if (thisRow.size() == 2)
            {
                this.list.addEntry(new AIEntry(parent, pokemob, thisRow.toArray(new AIButton[2])));
                thisRow.clear();
            }
            this.addRenderableWidget(button);
        }
        if (!thisRow.isEmpty())
        {
            this.list.addEntry(new AIEntry(parent, pokemob, thisRow.toArray(new AIButton[0])));
        }
    }

    @Override
    public void render(PoseStack mat, int x, int y, float f)
    {
        for (AbstractWidget button : ours) button.visible = false;
        this.list.render(mat, x, y, f);
    }

    @Override
    public void renderBg(PoseStack mat, float partialTicks, int mouseX, int mouseY)
    {
        final int k = (this.width - this.imageWidth) / 2 - 17;
        final int l = (this.height - this.imageHeight) / 2;
        // Render the black box to hold the pokemob
        parent.blit(mat, k + 24, l + 16, 0, this.imageHeight, 55, 55);

        // Render the box around where the inventory slots/buttons go.
        parent.blit(mat, k + 79, l + 16, 145, this.imageHeight, 110, 55);

        if (this.menu.pokemob != null)
        {
            Mob mob = this.menu.pokemob.getEntity();

            float f = 30;
            float yBodyRot = mob.yBodyRot;
            float yBodyRotO = mob.yBodyRotO;
            float yHeadRot = mob.yHeadRot;
            float yHeadRotO = mob.yHeadRotO;

            mob.yBodyRot = mob.yBodyRotO = 180.0F + f * 20.0F;
            mob.yHeadRot = mob.yHeadRotO = mob.yBodyRot;

            GuiPokemobHelper.renderMob(mat, mob, k, l, 0, 0, 0, 0, 1, partialTicks);

            mob.yBodyRot = yBodyRot;
            mob.yBodyRotO = yBodyRotO;
            mob.yHeadRot = yHeadRot;
            mob.yHeadRotO = yHeadRotO;
            RenderSystem.setShaderTexture(0, Resources.GUI_POKEMOB);
        }
    }

}
