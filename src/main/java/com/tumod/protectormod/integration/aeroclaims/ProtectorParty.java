package com.tumod.protectormod.integration.aeroclaims;

import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.ProtectorTeamData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Noción de "party" de ProtectorMod para la integración con AeroClaims (standalone, sin SPI).
 *
 * <p>Dos jugadores son de la misma party si comparten clan o confianza de núcleo. {@code areAllies}
 * consulta las alianzas entre clanes. Los clanes/confianza viven en el overworld
 * ({@link ClanSavedData#get} / {@link ProtectorTeamData#get}), así que se resuelven siempre contra
 * {@code server.overworld()} sin importar la dimensión del claim.
 */
public final class ProtectorParty {

    private ProtectorParty() {}

    public static boolean sameParty(MinecraftServer server, UUID a, UUID b) {
        if (server == null || a == null || b == null) return false;
        if (a.equals(b)) return true;

        ServerLevel overworld = server.overworld();

        // 1) Confianza de núcleo (invitados) — A invitó a B a su protección.
        if (ProtectorTeamData.get(overworld).sameTeam(a, b)) return true;

        // 2) Mismo clan de ProtectorMod.
        ClanSavedData data = ClanSavedData.get(overworld);
        ClanSavedData.ClanInstance clanA = data.getClanByMember(a);
        return clanA != null && clanA == data.getClanByMember(b);
    }

    public static boolean areAllies(MinecraftServer server, UUID owner, UUID other) {
        if (server == null || owner == null || other == null) return false;
        if (owner.equals(other)) return true;

        ServerLevel overworld = server.overworld();
        ClanSavedData data = ClanSavedData.get(overworld);
        ClanSavedData.ClanInstance clanOwner = data.getClanByMember(owner);
        ClanSavedData.ClanInstance clanOther = data.getClanByMember(other);
        if (clanOwner == null || clanOther == null) return false;
        if (clanOwner == clanOther) return true;

        // Alianza mutua: el clan del owner tiene como aliado al clan del otro.
        return clanOwner.allies.containsKey(clanOther.clanId);
    }
}
