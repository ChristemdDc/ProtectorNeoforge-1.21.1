package com.tumod.protectormod.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Índice central (overworld) de "party por confianza de núcleo": para cada dueño de
 * núcleo, el conjunto de UUIDs que ha invitado/confiado. Es la fuente de verdad que
 * AeroClaims consulta para decidir si dos jugadores son de la misma party.
 *
 * <p>¿Por qué central y no leer el permissionsMap de la BlockEntity? Porque la BE solo
 * está disponible con su chunk cargado; con el dueño offline (habitual en 50+ jugadores)
 * no podríamos resolver el permiso. Este SavedData vive en el overworld y siempre está.
 *
 * <p>Se rellena/actualiza desde {@code ProtectionCoreBlockEntity} al conceder/quitar
 * permisos, y se autocura al cargar chunks de núcleos (ver ModEvents.onChunkLoad).
 */
public class ProtectorTeamData extends SavedData {

    private static final String DATA_NAME = "protector_teams";

    /** owner UUID -> conjunto de miembros de confianza. */
    private final Map<UUID, Set<UUID>> trust = new HashMap<>();

    public static final SavedData.Factory<ProtectorTeamData> FACTORY =
            new SavedData.Factory<>(ProtectorTeamData::new, ProtectorTeamData::load, null);

    public static ProtectorTeamData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void addTrust(UUID owner, UUID member) {
        if (owner == null || member == null || owner.equals(member)) return;
        if (trust.computeIfAbsent(owner, k -> new HashSet<>()).add(member)) {
            setDirty();
        }
    }

    public void removeTrust(UUID owner, UUID member) {
        Set<UUID> members = trust.get(owner);
        if (members != null && members.remove(member)) {
            if (members.isEmpty()) trust.remove(owner);
            setDirty();
        }
    }

    /** True si a y b comparten party (uno confía en el otro, o ambos confiados por el mismo dueño). */
    public boolean sameTeam(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;

        Set<UUID> sa = trust.get(a);
        if (sa != null && sa.contains(b)) return true;

        Set<UUID> sb = trust.get(b);
        if (sb != null && sb.contains(a)) return true;

        // Caso borde: dos invitados del mismo dueño.
        for (Set<UUID> members : trust.values()) {
            if (members.contains(a) && members.contains(b)) return true;
        }
        return false;
    }

    public static ProtectorTeamData load(CompoundTag tag, HolderLookup.Provider registries) {
        ProtectorTeamData data = new ProtectorTeamData();
        if (tag.contains("Teams")) {
            CompoundTag teams = tag.getCompound("Teams");
            for (String ownerStr : teams.getAllKeys()) {
                UUID owner = UUID.fromString(ownerStr);
                Set<UUID> members = new HashSet<>();
                ListTag list = teams.getList(ownerStr, Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    members.add(UUID.fromString(list.getString(i)));
                }
                if (!members.isEmpty()) data.trust.put(owner, members);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag teams = new CompoundTag();
        trust.forEach((owner, members) -> {
            ListTag list = new ListTag();
            for (UUID m : members) list.add(StringTag.valueOf(m.toString()));
            teams.put(owner.toString(), list);
        });
        tag.put("Teams", teams);
        return tag;
    }
}
