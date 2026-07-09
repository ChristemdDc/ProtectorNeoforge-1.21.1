package com.tumod.protectormod.integration.aeroclaims;

import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.permission.ClaimPermissionResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Resolver de permisos de AeroClaims basado en los clanes/party de ProtectorMod.
 *
 * <p>Reemplaza a los resolvers OPAC/FTB del AeroClaims oficial: se inyecta vía mixin en
 * {@code ClaimManager.buildResolver} para que el acceso a los claims de sublevel use las
 * relaciones de ProtectorMod (clan, confianza de núcleo, alianzas), sin depender de OPAC ni FTB.
 *
 * <p>Respeta los flags del claim: {@code allowOthers}, {@code allowParty}, {@code allowAllies}.
 */
public class ProtectorAeroPermissionResolver implements ClaimPermissionResolver {

    @Override
    public boolean canAccess(ServerPlayer player, Claim claim) {
        try {
            UUID playerUuid = player.getUUID();
            UUID ownerUuid = claim.getOwner();

            if (playerUuid.equals(ownerUuid)) return true;
            if (player.hasPermissions(2)) return true;      // OP / admin
            if (claim.isAllowOthers()) return true;

            MinecraftServer server = player.getServer();
            if (server == null) return false;

            if (claim.isAllowParty() && ProtectorParty.sameParty(server, playerUuid, ownerUuid)) return true;
            if (claim.isAllowAllies() && ProtectorParty.areAllies(server, ownerUuid, playerUuid)) return true;

            return false;
        } catch (Exception e) {
            return false; // ante cualquier fallo, denegar (lado seguro)
        }
    }
}
