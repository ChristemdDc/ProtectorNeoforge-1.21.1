package com.tumod.protectormod.integration;

import com.mapter.aeroclaims.api.ExternalPartyResolver;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.ProtectorTeamData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Expone los clanes de ProtectorMod a AeroClaims como "party".
 *
 * <p>Dos jugadores son de la misma party si pertenecen al mismo clan
 * ({@link ClanSavedData.ClanInstance}). {@code areAllies} consulta las alianzas entre
 * clanes: los miembros de dos clanes aliados se consideran aliados.
 *
 * <p>Los clanes se guardan en el overworld (ver {@link ClanSavedData#get}), así que
 * se resuelven siempre contra {@code server.overworld()} independientemente de la
 * dimensión donde esté el claim.
 */
public class ProtectorClanResolver implements ExternalPartyResolver {

    private final MinecraftServer server;

    public ProtectorClanResolver(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public boolean sameParty(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;

        ServerLevel overworld = server.overworld();

        // 1) Confianza de núcleo (invitados) — el caso habitual: A invitó a B a su protección.
        if (ProtectorTeamData.get(overworld).sameTeam(a, b)) return true;

        // 2) Clan — si comparten el mismo clan de ProtectorMod.
        ClanSavedData data = ClanSavedData.get(overworld);
        ClanSavedData.ClanInstance clanA = data.getClanByMember(a);
        if (clanA != null && clanA == data.getClanByMember(b)) return true;

        return false;
    }

    @Override
    public boolean areAllies(UUID owner, UUID other) {
        if (owner == null || other == null) return false;
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
