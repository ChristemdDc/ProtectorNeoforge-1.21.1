package com.tumod.protectormod.network;

import com.tumod.protectormod.client.AdminPanelClientState;
import com.tumod.protectormod.client.AllianceClientState;
import com.tumod.protectormod.client.ProtectionClientTracker;
import com.tumod.protectormod.client.screen.AdminPanelScreen;
import com.tumod.protectormod.client.screen.AllianceScreen;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Handlers de los payloads S2C. Solo se cargan/ejecutan en el cliente
 * (se invocan desde lambdas diferidas en {@link ModNetworking}).
 */
public final class ClientPayloadHandlers {

    private ClientPayloadHandlers() {}

    public static void handleShowArea(ModPayloads.ShowAreaClientPayload payload) {
        ProtectionClientTracker.showArea(payload.pos(), payload.radius());
    }

    public static void handleSyncProtection(ModPayloads.SyncProtectionPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        Map<BlockPos, ProtectionDataManager.CoreEntry> received = new HashMap<>();
        for (ModPayloads.CoreData data : payload.cores()) {
            received.put(data.pos(), new ProtectionDataManager.CoreEntry(data.pos(), data.owner(), data.radius()));
        }

        ProtectionDataManager manager = ProtectionDataManager.get(client.level);
        manager.replaceAll(received);
    }

    public static void handleAllianceData(ModPayloads.AllianceDataPayload payload) {
        AllianceClientState.set(payload);
        if (Minecraft.getInstance().screen instanceof AllianceScreen screen) {
            screen.onDataUpdated();
        }
    }

    public static void handleAdminPanelData(ModPayloads.AdminPanelDataPayload payload) {
        AdminPanelClientState.set(payload);
        Minecraft mc = Minecraft.getInstance();
        // Si ya está abierta, refresca; si no (respuesta al comando), la abre.
        if (mc.screen instanceof AdminPanelScreen screen) {
            screen.onDataUpdated();
        } else {
            mc.setScreen(new AdminPanelScreen());
        }
    }
}
