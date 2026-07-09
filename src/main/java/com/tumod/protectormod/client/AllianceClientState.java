package com.tumod.protectormod.client;

import com.tumod.protectormod.network.ModPayloads;

/**
 * Guarda el último snapshot de alianzas recibido del servidor. La {@code AllianceScreen}
 * lee de aquí y se refresca cuando llega un nuevo {@link ModPayloads.AllianceDataPayload}.
 */
public final class AllianceClientState {

    private static ModPayloads.AllianceDataPayload current = null;

    private AllianceClientState() {}

    public static void set(ModPayloads.AllianceDataPayload data) {
        current = data;
    }

    public static ModPayloads.AllianceDataPayload get() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
