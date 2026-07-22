package com.tumod.protectormod.client;

import com.tumod.protectormod.network.ModPayloads;

/**
 * Guarda el último snapshot del panel admin recibido del servidor. La {@code AdminPanelScreen}
 * lee de aquí y se refresca cuando llega un nuevo {@link ModPayloads.AdminPanelDataPayload}
 * (tras cada acción, el servidor reenvía el snapshot).
 */
public final class AdminPanelClientState {

    private static ModPayloads.AdminPanelDataPayload current = null;

    private AdminPanelClientState() {}

    public static void set(ModPayloads.AdminPanelDataPayload data) {
        current = data;
    }

    public static ModPayloads.AdminPanelDataPayload get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
