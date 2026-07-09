package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CloseButtonCustom extends Button {
    private static final ResourceLocation TEXTURE_X = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/boton_x.png");

    public CloseButtonCustom(int x, int y, int width, int height, Component title, OnPress onPress) {
        super(x, y, width, height, title, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        int vOffset = !this.active ? 32 : (this.isHoveredOrFocused() ? 16 : 0);

        graphics.blit(TEXTURE_X, this.getX(), this.getY(), 0, vOffset, 16, 16, 16, 48);
    }
}
