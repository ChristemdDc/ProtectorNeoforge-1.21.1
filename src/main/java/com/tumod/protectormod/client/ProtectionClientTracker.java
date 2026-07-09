package com.tumod.protectormod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ProtectionClientTracker {

    private static final List<ProtectionAreaEffect> activeEffects = new ArrayList<>();

    public static void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null) return;
        activeEffects.removeIf(effect -> !effect.tick(client.level));
    }

    public static void showArea(BlockPos pos, int radius) {
        activeEffects.add(new ProtectionAreaEffect(pos, radius, 100));
    }
}
