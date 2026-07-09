package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.network.ModNetworking;
import com.tumod.protectormod.util.FlagTranslations;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

public class FlagsScreen extends Screen {
    private final ProtectionCoreBlockEntity core;
    private final Screen lastScreen;

    public FlagsScreen(Screen lastScreen, ProtectionCoreBlockEntity core) {
        super(Component.literal("Configuración de Zona"));
        this.lastScreen = lastScreen;
        this.core = core;
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 145;
        int startY = 45;
        int columnWidth = 150;

        List<String> allFlags = new ArrayList<>();
        allFlags.addAll(ProtectionCoreBlockEntity.BASIC_FLAGS);
        allFlags.addAll(ProtectionCoreBlockEntity.ADMIN_FLAGS);

        List<String> visibleFlags = new ArrayList<>();
        for (String flagId : allFlags) {
            if (flagId.equals("entry") && !core.isAdmin()) {
                continue;
            }
            if (flagId.equals("hunger") && !core.isAdmin() && core.getCoreLevel() < 5) {
                continue;
            }
            visibleFlags.add(flagId);
        }

        for (int i = 0; i < visibleFlags.size(); i++) {
            String flagId = visibleFlags.get(i);

            int column = i % 2;
            int row = i / 2;

            int posX = startX + (column * columnWidth);
            int posY = startY + (row * 22);

            createFlagButton(flagId, posX, posY);
        }

        this.addRenderableWidget(Button.builder(Component.literal("§lVolver"),
                        b -> this.minecraft.setScreen(lastScreen))
                .bounds(this.width / 2 - 50, this.height - 35, 100, 20).build());
    }

    private void createFlagButton(String flagId, int x, int y) {
        boolean active = core.getFlag(flagId);

        boolean isAdminFlag = ProtectionCoreBlockEntity.ADMIN_FLAGS.contains(flagId);
        String prefix = isAdminFlag ? "§4⚙ " : "§6• ";

        boolean isLockedCalc = false;

        if (!core.isAdmin() && isAdminFlag) {
            if (core.getCoreLevel() < 5) {
                isLockedCalc = true;
            }
        }

        final boolean isLocked = isLockedCalc;

        Button.Builder builder = Button.builder(
                Component.literal(prefix + FlagTranslations.getFlagName(flagId) + ": ")
                        .append(isLocked ? Component.literal("§7BLOQUEADO") : (active ? Component.literal("§aON") : Component.literal("§cOFF"))),
                b -> {
                    if (!isLocked) {
                        ModNetworking.sendUpdateFlag(core.getBlockPos(), flagId);
                        core.setFlag(flagId, !active);
                        this.rebuildWidgets();
                    } else {
                        if (this.minecraft.player != null) {
                            this.minecraft.player.playSound(SoundEvents.VILLAGER_NO, 1.0f, 1.0f);
                        }
                    }
                }).bounds(x, y, 140, 20);

        if (isLocked) {
            builder.tooltip(Tooltip.create(Component.literal("§cRequiere Núcleo de Netherita (Nivel 5)")));
        }

        this.addRenderableWidget(builder.build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(this.font, "§b§lCONFIGURACIÓN GLOBAL DE FLAGS", this.width / 2, 25, 0xFFFFFF);
        graphics.drawCenteredString(this.font, "§7§oUsa ⚙ para flags de sistema y • para básicas", this.width / 2, this.height - 55, 0xAAAAAA);
    }
}
