package com.tumod.protectormod.integration.aeroclaims;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.IClaimProvider;
import com.tumod.protectormod.config.ProtectorConfig;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Proveedor de slots de claim por CONFIG, sin OPAC ni FTB. Se inyecta vía mixin en
 * {@code AeroClaimManager.buildProvider}.
 *
 * <p>Cada jugador tiene un cupo fijo ({@code aeroMaxClaimsPerPlayer} en la config de ProtectorMod).
 * "Banco disponible" = max − migrados; reclamar un ship migra slots al almacén de AeroClaims y
 * liberarlo los devuelve. Reutiliza el mismo modelo de slots migrados de {@link AeroClaimSavedData}
 * que usaba OPAC, por lo que el resto de AeroClaims funciona igual.
 */
public class ProtectorAeroClaimProvider implements IClaimProvider {

    @Override
    public AeroClaimManager.TransferResult transferToAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;
        if (getFreeClaims(player) < amount) return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

        AeroClaimSavedData.get(player.serverLevel()).addMigratedSlots(player.getUUID(), amount);
        return AeroClaimManager.TransferResult.SUCCESS;
    }

    @Override
    public AeroClaimManager.TransferResult transferFromAero(ServerPlayer player, int amount) {
        if (amount <= 0) return AeroClaimManager.TransferResult.API_ERROR;

        AeroClaimSavedData data = AeroClaimSavedData.get(player.serverLevel());
        UUID id = player.getUUID();
        if (data.getFreeSlots(id) < amount) return AeroClaimManager.TransferResult.NOT_ENOUGH_FREE;

        data.setMigratedSlots(id, data.getMigratedSlots(id) - amount);
        return AeroClaimManager.TransferResult.SUCCESS;
    }

    @Override
    public int getFreeClaims(ServerPlayer player) {
        int max = ProtectorConfig.AERO_MAX_CLAIMS_PER_PLAYER.get();
        int migrated = AeroClaimSavedData.get(player.serverLevel()).getMigratedSlots(player.getUUID());
        return Math.max(0, max - migrated);
    }
}
