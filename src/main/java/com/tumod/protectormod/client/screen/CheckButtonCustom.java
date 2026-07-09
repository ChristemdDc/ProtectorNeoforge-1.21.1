package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CheckButtonCustom extends Button {
    private static final ResourceLocation TEXTURE_CHECK = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/boton_check.png");

    public CheckButtonCustom(int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        int vOffset = !this.active ? 24 : (this.isHoveredOrFocused() ? 16 : 0);

        graphics.blit(TEXTURE_CHECK, this.getX(), this.getY(), 0, vOffset, 32, 16, 32, 48);
    }
}
