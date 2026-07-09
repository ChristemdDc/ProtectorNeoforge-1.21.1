package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.network.ModNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

public class CreateClanScreen extends Screen {
    private final Screen lastScreen;
    private final BlockEntity core;
    private EditBox clanNameInput;
    private Button disbandBtn;
    private boolean confirmDisband = false;

    public CreateClanScreen(Screen lastScreen, BlockEntity core) {
        super(Component.literal("Crear Nuevo Clan"));
        this.lastScreen = lastScreen;
        this.core = core;
    }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;

        this.clanNameInput = new EditBox(this.font, x - 100, y - 20, 200, 20, Component.literal("Nombre del Clan"));
        this.clanNameInput.setMaxLength(16);
        this.addRenderableWidget(this.clanNameInput);

        this.addRenderableWidget(Button.builder(Component.literal("✅ Confirmar"), b -> {
            String name = this.clanNameInput.getValue().trim();
            if (!name.isEmpty()) {
                ModNetworking.sendCreateClan(core.getBlockPos(), name);
                this.minecraft.setScreen(null);
            }
        }).bounds(x - 105, y + 25, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("❌ Cancelar"), b -> {
            this.minecraft.setScreen(lastScreen);
        }).bounds(x + 5, y + 25, 100, 20).build());

        // Alternativa a /clan delete: disolver el clan desde la GUI (solo el líder; el
        // servidor lo valida). Confirmación de un clic para evitar accidentes.
        this.disbandBtn = Button.builder(Component.literal("§4Disolver mi Clan"), b -> {
            if (!confirmDisband) {
                confirmDisband = true;
                this.disbandBtn.setMessage(Component.literal("§4§l¿Seguro? Clic de nuevo"));
            } else {
                ModNetworking.sendDisbandClan(core.getBlockPos());
                this.minecraft.setScreen(null);
            }
        }).bounds(x - 105, y + 50, 210, 20).build();
        this.addRenderableWidget(this.disbandBtn);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        int centerX = this.width / 2;
        int titleY = this.height / 2 - 50;
        graphics.drawCenteredString(this.font, Component.literal("FUNDAR CLAN").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), centerX, titleY, 0xFFFFFF);

        Component warning = Component.literal("⚠ ¡Este nombre no podrá cambiarse después!");
        int warningX = centerX - (this.font.width(warning) / 2);
        int warningY = this.height / 2 + 5;

        graphics.drawString(this.font, warning, warningX, warningY, 0xFF5555, false);
    }
}
