package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AdminCoreScreen extends AbstractContainerScreen<ProtectionCoreMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/admin_core.png");

    private EditBox radiusInput;
    private final ProtectionCoreBlockEntity core;
    private boolean tempCanBuild;

    public AdminCoreScreen(ProtectionCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.core = menu.getBlockEntity();
        this.imageWidth = 176;
        this.imageHeight = 222;
    }

    @Override
    protected void init() {
        super.init();
        this.tempCanBuild = core.getFlag("break");
        this.titleLabelY = -1000;
        this.inventoryLabelY = -94;

        int x = this.leftPos;
        int y = this.topPos;

        this.radiusInput = new EditBox(this.font, x + 80, y + 25, 50, 18, Component.literal("Radio"));
        this.radiusInput.setValue(String.valueOf(core.getRadius()));
        this.radiusInput.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.radiusInput);

        this.addRenderableWidget(Button.builder(Component.literal("🚩 CONFIGURAR FLAGS"), b -> {
            try {
                int r = Integer.parseInt(radiusInput.getValue());
                core.setAdminRadius(r);
            } catch (NumberFormatException ignored) {}

            this.minecraft.setScreen(new FlagsScreen(this, core));
        }).bounds(x + 20, y + 55, 136, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("✅ Aplicar Cambios"), b -> {
            applyChanges();
        }).bounds(x + 20, y + 80, 136, 20).build());
    }

    private void applyChanges() {
        try {
            int newRadius = Integer.parseInt(radiusInput.getValue());

            ModNetworking.sendUpdateAdminCore(
                    core.getBlockPos(),
                    newRadius,
                    core.getFlag("build"),
                    core.getFlag("pvp"),
                    core.getFlag("explosions")
            );

            this.onClose();
        } catch (NumberFormatException e) {
            radiusInput.setValue(String.valueOf(core.getRadius()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, "§4§lADMIN PROTECTOR", 35, 8, 0xFFFFFF, false);
        graphics.drawString(this.font, "Radio:", 35, 30, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }
}
