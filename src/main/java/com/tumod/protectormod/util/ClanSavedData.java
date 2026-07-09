package com.tumod.protectormod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class ClanSavedData extends SavedData {

    private static final String DATA_NAME = "protector_clans";

    public int serverMaxCores = 3;
    private final Map<String, ClanInstance> clans = new HashMap<>();

    // Fase 6: índice member/leader -> clan para O(1) en getClanByMember (llamado en cada
    // interacción por isTrusted y por el resolver de AeroClaims). Se reconstruye tras
    // cada cambio de clanes (eventos poco frecuentes); no se serializa.
    private final transient Map<UUID, ClanInstance> memberIndex = new HashMap<>();
    private final transient Map<UUID, ClanInstance> clanByIdIndex = new HashMap<>();

    public ClanSavedData() {}

    private void rebuildIndex() {
        memberIndex.clear();
        clanByIdIndex.clear();
        for (ClanInstance clan : clans.values()) {
            clanByIdIndex.put(clan.clanId, clan);
            for (UUID member : clan.members) {
                memberIndex.put(member, clan);
            }
        }
    }

    public static final SavedData.Factory<ClanSavedData> FACTORY =
            new SavedData.Factory<>(ClanSavedData::new, ClanSavedData::load, null);

    /**
     * Un miembro del clan: solo identidad/nombre. Los permisos B/I/C NO viven aquí:
     * son por-núcleo (en el permissionsMap de cada protección). La membresía es clan-wide;
     * un miembro tiene acceso completo por defecto en cada núcleo salvo override de ese núcleo.
     */
    public static class ClanMember {
        public String name;
        public ClanMember(String name) { this.name = name; }
    }

    /** Qué puede hacer un clan aliado en MIS protecciones (default: solo interactuar). */
    public static class AllyPerms {
        public boolean build = false;
        public boolean interact = true;
        public boolean chests = false;

        public boolean get(String type) {
            return switch (type.toLowerCase()) {
                case "build" -> build;
                case "interact" -> interact;
                case "chests" -> chests;
                default -> false;
            };
        }
        public void set(String type, boolean value) {
            switch (type.toLowerCase()) {
                case "build" -> build = value;
                case "interact" -> interact = value;
                case "chests" -> chests = value;
            }
        }
    }

    public static class ClanInstance {
        public String name;
        public UUID leaderUUID;
        public String leaderName;
        public BlockPos corePos;
        public int maxMembers = 8;
        public Set<UUID> members = new HashSet<>();
        public Map<UUID, ClanMember> memberInfo = new HashMap<>(); // uuid -> {nombre, permisos}

        // Fase 7: identidad estable + pool de protecciones del clan.
        public UUID clanId = UUID.randomUUID();
        public int maxProtections = 5;
        public int protectionsUsed = 0;

        // Alianzas: clanId aliado -> qué pueden hacer sus miembros en MIS protecciones.
        public Map<UUID, AllyPerms> allies = new HashMap<>();
        public Set<UUID> incomingRequests = new HashSet<>(); // clanes que me propusieron alianza
        public Set<UUID> outgoingRequests = new HashSet<>();  // clanes a los que propuse

        public ClanInstance(String name, UUID leaderUUID, String leaderName, BlockPos pos) {
            this.name = name;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.corePos = pos;
            this.members.add(leaderUUID);
            this.memberInfo.put(leaderUUID, new ClanMember(leaderName));
        }

        public boolean isLeader(UUID uuid) {
            return leaderUUID.equals(uuid);
        }

        public boolean hasProtectionSpace() {
            return protectionsUsed < maxProtections;
        }
    }

    // ── Mutaciones de miembros (mantienen members, memberInfo e índice sincronizados) ──

    public void addMemberToClan(ClanInstance clan, UUID member, String memberName) {
        clan.members.add(member);
        clan.memberInfo.put(member, new ClanMember(memberName));
        memberIndex.put(member, clan);
        setDirty();
    }

    /** Expulsa un miembro del clan (el líder no se puede expulsar a sí mismo). */
    public boolean kickMember(ClanInstance clan, UUID member) {
        if (member == null || clan.leaderUUID.equals(member)) return false;
        boolean removed = clan.members.remove(member);
        clan.memberInfo.remove(member);
        memberIndex.remove(member);
        if (removed) setDirty();
        return removed;
    }

    /** UUID de un miembro por nombre (para la GUI, que trabaja con nombres). */
    public UUID getMemberIdByName(ClanInstance clan, String name) {
        for (Map.Entry<UUID, ClanMember> e : clan.memberInfo.entrySet()) {
            if (e.getValue().name.equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    public boolean tryCreateClan(String name, UUID owner, String ownerName, BlockPos pos) {
        String lowerName = name.toLowerCase();

        if (this.clans.containsKey(lowerName)) {
            return false;
        }

        if (getClanByLeader(owner) != null) {
            return false;
        }

        ClanInstance newClan = new ClanInstance(name, owner, ownerName, pos);
        newClan.maxProtections = com.tumod.protectormod.config.ProtectorConfig.DEFAULT_CLAN_PROTECTIONS.get();
        this.clans.put(lowerName, newClan);
        clanByIdIndex.put(newClan.clanId, newClan);
        for (UUID member : newClan.members) memberIndex.put(member, newClan);

        setDirty();
        return true;
    }

    /** Busca un clan por su id estable (O(1)). */
    public ClanInstance getClanById(UUID clanId) {
        return clanId == null ? null : clanByIdIndex.get(clanId);
    }

    // ─────────────────────────── Alianzas ───────────────────────────

    /** @return mensaje de error, o null si la solicitud se envió. */
    public String proposeAlliance(ClanInstance from, ClanInstance to) {
        if (from == to) return "No puedes aliarte con tu propio clan.";
        if (from.allies.containsKey(to.clanId)) return "Ya son aliados.";
        if (from.outgoingRequests.contains(to.clanId)) return "Ya enviaste una solicitud a ese clan.";
        int max = com.tumod.protectormod.config.ProtectorConfig.MAX_ALLIANCES.get();
        if (from.allies.size() >= max) return "Tu clan alcanzó el límite de alianzas (" + max + ").";
        // Si ese clan ya te propuso a ti, aceptar directamente.
        if (from.incomingRequests.contains(to.clanId)) {
            return acceptAlliance(from, to.clanId);
        }
        from.outgoingRequests.add(to.clanId);
        to.incomingRequests.add(from.clanId);
        setDirty();
        return null;
    }

    /** @return mensaje de error, o null si se aceptó. */
    public String acceptAlliance(ClanInstance clan, UUID fromClanId) {
        if (!clan.incomingRequests.contains(fromClanId)) return "No hay solicitud de ese clan.";
        ClanInstance from = getClanById(fromClanId);
        if (from == null) { clan.incomingRequests.remove(fromClanId); setDirty(); return "Ese clan ya no existe."; }
        int max = com.tumod.protectormod.config.ProtectorConfig.MAX_ALLIANCES.get();
        if (clan.allies.size() >= max) return "Tu clan alcanzó el límite de alianzas (" + max + ").";
        if (from.allies.size() >= max) return "El otro clan alcanzó su límite de alianzas.";
        clan.incomingRequests.remove(fromClanId);
        from.outgoingRequests.remove(clan.clanId);
        clan.allies.put(fromClanId, new AllyPerms());
        from.allies.put(clan.clanId, new AllyPerms());
        setDirty();
        return null;
    }

    public void rejectAlliance(ClanInstance clan, UUID fromClanId) {
        clan.incomingRequests.remove(fromClanId);
        ClanInstance from = getClanById(fromClanId);
        if (from != null) from.outgoingRequests.remove(clan.clanId);
        setDirty();
    }

    public void cancelRequest(ClanInstance clan, UUID toClanId) {
        clan.outgoingRequests.remove(toClanId);
        ClanInstance to = getClanById(toClanId);
        if (to != null) to.incomingRequests.remove(clan.clanId);
        setDirty();
    }

    public void breakAlliance(ClanInstance clan, UUID allyClanId) {
        clan.allies.remove(allyClanId);
        ClanInstance ally = getClanById(allyClanId);
        if (ally != null) ally.allies.remove(clan.clanId);
        setDirty();
    }

    public void setAllyPerm(ClanInstance clan, UUID allyClanId, String type, boolean value) {
        AllyPerms ap = clan.allies.get(allyClanId);
        if (ap != null) { ap.set(type, value); setDirty(); }
    }

    /** Elimina toda referencia a un clan disuelto en las alianzas/solicitudes de los demás. */
    private void purgeAllianceReferences(UUID clanId) {
        for (ClanInstance other : clans.values()) {
            other.allies.remove(clanId);
            other.incomingRequests.remove(clanId);
            other.outgoingRequests.remove(clanId);
        }
    }

    public void setServerMaxCores(int cantidad) {
        this.serverMaxCores = cantidad;
        this.setDirty();
    }

    public int getPlayerCoreCount(UUID playerUUID) {
        int count = 0;
        for (ClanInstance clan : clans.values()) {
            if (clan.leaderUUID.equals(playerUUID)) {
                count++;
            }
        }
        return count;
    }

    public int getCoresCount(UUID playerUUID) {
        return getPlayerCoreCount(playerUUID);
    }

    public static ClanSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public ClanInstance getClanByLeader(UUID leaderUUID) {
        return clans.values().stream()
                .filter(c -> c.leaderUUID.equals(leaderUUID))
                .findFirst().orElse(null);
    }

    public ClanInstance getClanByMember(UUID playerUUID) {
        // O(1): el líder también está en members (lo añade el constructor), así que el índice lo cubre.
        return memberIndex.get(playerUUID);
    }

    public String getClanOfPlayer(UUID playerUUID) {
        ClanInstance clan = getClanByMember(playerUUID);
        return (clan != null) ? clan.name : "";
    }

    public ClanInstance getClan(String name) {
        return (name == null || name.isEmpty()) ? null : clans.get(name.toLowerCase());
    }

    /** Todos los clanes (solo lectura, para listar candidatos a alianza). */
    public java.util.Collection<ClanInstance> getAllClans() {
        return clans.values();
    }

    public boolean hasClan(UUID playerUUID) {
        return getClanByLeader(playerUUID) != null;
    }

    public void deleteClan(UUID ownerUUID) {
        java.util.List<UUID> removed = new java.util.ArrayList<>();
        clans.entrySet().removeIf(entry -> {
            if (entry.getValue().leaderUUID.equals(ownerUUID)) { removed.add(entry.getValue().clanId); return true; }
            return false;
        });
        for (UUID id : removed) purgeAllianceReferences(id);
        rebuildIndex();
        setDirty();
    }

    public void forceRemovePlayerFromAllClans(UUID playerUUID) {
        java.util.List<UUID> removed = new java.util.ArrayList<>();
        clans.entrySet().removeIf(entry -> {
            if (entry.getValue().leaderUUID.equals(playerUUID)) { removed.add(entry.getValue().clanId); return true; }
            return false;
        });
        for (UUID id : removed) purgeAllianceReferences(id);
        clans.values().forEach(clan -> {
            clan.members.remove(playerUUID);
            clan.memberInfo.remove(playerUUID);
        });
        rebuildIndex();
        setDirty();
    }

    public static ClanSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ClanSavedData data = new ClanSavedData();
        data.serverMaxCores = tag.contains("MaxCoresLimit") ? tag.getInt("MaxCoresLimit") : 3;

        if (tag.contains("ClanList")) {
            CompoundTag list = tag.getCompound("ClanList");
            for (String key : list.getAllKeys()) {
                CompoundTag cTag = list.getCompound(key);
                ClanInstance clan = new ClanInstance(
                        cTag.getString("Name"),
                        cTag.getUUID("Leader"),
                        cTag.getString("LeaderName"),
                        BlockPos.of(cTag.getLong("Pos"))
                );
                clan.maxMembers = cTag.contains("MaxMembers") ? cTag.getInt("MaxMembers") : 8;
                if (cTag.hasUUID("ClanId")) clan.clanId = cTag.getUUID("ClanId");
                clan.maxProtections = cTag.contains("MaxProtections") ? cTag.getInt("MaxProtections")
                        : com.tumod.protectormod.config.ProtectorConfig.DEFAULT_CLAN_PROTECTIONS.get();
                clan.protectionsUsed = cTag.getInt("ProtectionsUsed");
                if (cTag.contains("MembersList2")) {
                    ListTag membersTag = cTag.getList("MembersList2", Tag.TAG_COMPOUND);
                    for (int i = 0; i < membersTag.size(); i++) {
                        CompoundTag m = membersTag.getCompound(i);
                        UUID id = m.getUUID("Id");
                        clan.members.add(id);
                        clan.memberInfo.put(id, new ClanMember(m.getString("Name")));
                    }
                } else if (cTag.contains("MembersList")) {
                    // Formato antiguo (solo UUIDs). Nombre desconocido, acceso completo.
                    ListTag membersTag = cTag.getList("MembersList", Tag.TAG_STRING);
                    for (int i = 0; i < membersTag.size(); i++) {
                        UUID id = UUID.fromString(membersTag.getString(i));
                        clan.members.add(id);
                        clan.memberInfo.put(id, new ClanMember("?"));
                    }
                }
                if (cTag.contains("Allies")) {
                    ListTag alliesTag = cTag.getList("Allies", Tag.TAG_COMPOUND);
                    for (int i = 0; i < alliesTag.size(); i++) {
                        CompoundTag a = alliesTag.getCompound(i);
                        AllyPerms ap = new AllyPerms();
                        ap.build = a.getBoolean("B");
                        ap.interact = a.getBoolean("I");
                        ap.chests = a.getBoolean("C");
                        clan.allies.put(a.getUUID("Id"), ap);
                    }
                }
                if (cTag.contains("IncomingReq")) {
                    ListTag incTag = cTag.getList("IncomingReq", Tag.TAG_COMPOUND);
                    for (int i = 0; i < incTag.size(); i++) clan.incomingRequests.add(incTag.getCompound(i).getUUID("Id"));
                }
                if (cTag.contains("OutgoingReq")) {
                    ListTag outTag = cTag.getList("OutgoingReq", Tag.TAG_COMPOUND);
                    for (int i = 0; i < outTag.size(); i++) clan.outgoingRequests.add(outTag.getCompound(i).getUUID("Id"));
                }
                data.clans.put(key, clan);
            }
        }
        data.rebuildIndex();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("MaxCoresLimit", serverMaxCores);
        CompoundTag list = new CompoundTag();
        clans.forEach((id, clan) -> {
            CompoundTag cTag = new CompoundTag();
            cTag.putString("Name", clan.name);
            cTag.putUUID("Leader", clan.leaderUUID);
            cTag.putString("LeaderName", clan.leaderName);
            cTag.putLong("Pos", clan.corePos.asLong());
            cTag.putInt("MaxMembers", clan.maxMembers);
            cTag.putUUID("ClanId", clan.clanId);
            cTag.putInt("MaxProtections", clan.maxProtections);
            cTag.putInt("ProtectionsUsed", clan.protectionsUsed);

            ListTag membersTag = new ListTag();
            for (UUID memberUUID : clan.members) {
                ClanMember cm = clan.memberInfo.get(memberUUID);
                CompoundTag m = new CompoundTag();
                m.putUUID("Id", memberUUID);
                m.putString("Name", cm != null ? cm.name : "?");
                membersTag.add(m);
            }
            cTag.put("MembersList2", membersTag);

            // Alianzas
            ListTag alliesTag = new ListTag();
            clan.allies.forEach((allyId, ap) -> {
                CompoundTag a = new CompoundTag();
                a.putUUID("Id", allyId);
                a.putBoolean("B", ap.build);
                a.putBoolean("I", ap.interact);
                a.putBoolean("C", ap.chests);
                alliesTag.add(a);
            });
            cTag.put("Allies", alliesTag);

            ListTag incTag = new ListTag();
            for (UUID reqId : clan.incomingRequests) {
                CompoundTag r = new CompoundTag();
                r.putUUID("Id", reqId);
                incTag.add(r);
            }
            cTag.put("IncomingReq", incTag);

            ListTag outTag = new ListTag();
            for (UUID reqId : clan.outgoingRequests) {
                CompoundTag r = new CompoundTag();
                r.putUUID("Id", reqId);
                outTag.add(r);
            }
            cTag.put("OutgoingReq", outTag);

            list.put(id, cTag);
        });
        tag.put("ClanList", list);
        return tag;
    }
}
