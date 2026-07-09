package com.tumod.protectormod.integration;

import com.mapter.aeroclaims.api.AeroClaimsAPI;
import com.tumod.protectormod.ProtectorMod;
import net.minecraft.server.MinecraftServer;

/**
 * Puente opcional hacia AeroClaims. Esta clase SOLO debe cargarse/llamarse cuando el
 * mod "aeroclaims" está presente (referencia clases de su API). El llamador debe
 * guardar la invocación con {@code ModList.get().isLoaded("aeroclaims")}.
 */
public final class AeroClaimsIntegration {

    private AeroClaimsIntegration() {}

    public static void register(MinecraftServer server) {
        AeroClaimsAPI.setPartyResolver(new ProtectorClanResolver(server));
        ProtectorMod.LOGGER.info("[ProtectorMod] Registrado ProtectorClanResolver en AeroClaims (clan = party).");
    }
}
