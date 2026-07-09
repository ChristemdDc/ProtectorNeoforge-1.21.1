package com.tumod.protectormod.util;

import com.tumod.protectormod.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ProtectionDataManager extends SavedData {

    private static final String DATA_NAME = "protection_data";

    private final Map<BlockPos, CoreEntry> allCores = new HashMap<>();
    private int globalLimit = 1;

    /**
     * Índice espacial (no serializado): chunk (ChunkPos.asLong) -> núcleos cuyo cuadrado de radio
     * cubre ese chunk. Convierte {@link #getCoreAt} de O(nº total de núcleos) a O(núcleos que tocan
     * ese chunk) — clave en rutas calientes como spawn de mobs y explosiones con muchos núcleos.
     * Se reconstruye al cargar y se mantiene incremental en cada alta/baja/cambio de radio.
     */
    private final transient Map<Long, Set<BlockPos>> chunkIndex = new HashMap<>();

    public record CoreEntry(BlockPos pos, UUID owner, int radius) {}

    // ── SavedData factory (API 1.21) ──
    public static final SavedData.Factory<ProtectionDataManager> FACTORY =
            new SavedData.Factory<>(ProtectionDataManager::new, ProtectionDataManager::load, null);

    public void addCore(BlockPos pos, UUID owner, int radius) {
        putCore(pos, owner, radius);
        this.setDirty();
    }

    public void addOrUpdateCore(BlockPos pos, UUID owner, int radius) {
        putCore(pos, owner, radius);
        this.setDirty();
    }

    /** Alta o actualización manteniendo el índice espacial (re-indexa si cambió el radio). */
    private void putCore(BlockPos pos, UUID owner, int radius) {
        CoreEntry old = allCores.get(pos);
        if (old != null) indexRemove(pos, old.radius());
        allCores.put(pos, new CoreEntry(pos, owner, radius));
        indexAdd(pos, radius);
    }

    public void removeCore(BlockPos pos) {
        CoreEntry old = allCores.remove(pos);
        if (old != null) {
            indexRemove(pos, old.radius());
            this.setDirty();
        }
    }

    public Map<BlockPos, CoreEntry> getAllCores() {
        return allCores;
    }

    /** Reemplaza todo el conjunto de núcleos (usado por el sync S2C en el cliente). */
    public void replaceAll(Map<BlockPos, CoreEntry> cores) {
        allCores.clear();
        chunkIndex.clear();
        for (CoreEntry e : cores.values()) {
            allCores.put(e.pos(), e);
            indexAdd(e.pos(), e.radius());
        }
    }

    // ── Índice espacial ──

    private void indexAdd(BlockPos pos, int radius) {
        forEachCoveredChunk(pos, radius, key ->
                chunkIndex.computeIfAbsent(key, k -> new HashSet<>()).add(pos));
    }

    private void indexRemove(BlockPos pos, int radius) {
        forEachCoveredChunk(pos, radius, key -> {
            Set<BlockPos> set = chunkIndex.get(key);
            if (set != null) {
                set.remove(pos);
                if (set.isEmpty()) chunkIndex.remove(key);
            }
        });
    }

    private interface ChunkKeyConsumer { void accept(long key); }

    /** Itera las claves de chunk que solapan el cuadrado [x±radius, z±radius] del núcleo. */
    private void forEachCoveredChunk(BlockPos pos, int radius, ChunkKeyConsumer consumer) {
        int minCX = SectionPos.blockToSectionCoord(pos.getX() - radius);
        int maxCX = SectionPos.blockToSectionCoord(pos.getX() + radius);
        int minCZ = SectionPos.blockToSectionCoord(pos.getZ() - radius);
        int maxCZ = SectionPos.blockToSectionCoord(pos.getZ() + radius);
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                consumer.accept(ChunkPos.asLong(cx, cz));
            }
        }
    }

    public CoreEntry getCoreAt(BlockPos targetPos) {
        long key = ChunkPos.asLong(
                SectionPos.blockToSectionCoord(targetPos.getX()),
                SectionPos.blockToSectionCoord(targetPos.getZ()));
        Set<BlockPos> candidates = chunkIndex.get(key);
        if (candidates == null) return null;
        for (BlockPos corePos : candidates) {
            CoreEntry entry = allCores.get(corePos);
            if (entry == null) continue;
            int radius = entry.radius();
            if (Math.abs(targetPos.getX() - corePos.getX()) <= radius
                    && Math.abs(targetPos.getZ() - corePos.getZ()) <= radius) {
                return entry;
            }
        }
        return null;
    }

    /**
     * TODOS los núcleos que cubren la posición (para reglas "el más restrictivo manda" cuando
     * varias protecciones solapan su área). Devuelve lista vacía si ninguna la cubre.
     */
    public java.util.List<CoreEntry> getCoresAt(BlockPos targetPos) {
        long key = ChunkPos.asLong(
                SectionPos.blockToSectionCoord(targetPos.getX()),
                SectionPos.blockToSectionCoord(targetPos.getZ()));
        Set<BlockPos> candidates = chunkIndex.get(key);
        if (candidates == null || candidates.isEmpty()) return java.util.List.of();
        java.util.List<CoreEntry> result = new java.util.ArrayList<>();
        for (BlockPos corePos : candidates) {
            CoreEntry entry = allCores.get(corePos);
            if (entry == null) continue;
            int radius = entry.radius();
            if (Math.abs(targetPos.getX() - corePos.getX()) <= radius
                    && Math.abs(targetPos.getZ() - corePos.getZ()) <= radius) {
                result.add(entry);
            }
        }
        return result;
    }

    public static ProtectionDataManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ProtectionDataManager data = new ProtectionDataManager();

        if (tag.contains("globalLimit")) {
            data.globalLimit = tag.getInt("globalLimit");
        }

        ListTag list = tag.getList("Cores", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            UUID owner = entryTag.getUUID("owner");
            int radius = entryTag.getInt("radius");
            data.allCores.put(pos, new CoreEntry(pos, owner, radius));
            data.indexAdd(pos, radius);
        }
        return data;
    }

    public int getGlobalLimit() { return globalLimit; }

    public void setGlobalLimit(int limit) {
        this.globalLimit = limit;
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        allCores.forEach((pos, entry) -> {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", pos.asLong());
            entryTag.putUUID("owner", entry.owner());
            entryTag.putInt("radius", entry.radius());
            list.add(entryTag);
        });
        tag.put("Cores", list);
        tag.putInt("globalLimit", globalLimit);
        return tag;
    }

    public void syncToAll(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            ModNetworking.sendSyncProtection(player, this.allCores);
        }
    }

    public static ProtectionDataManager get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        } else {
            return ClientData.INSTANCE;
        }
    }

    private static class ClientData {
        private static final ProtectionDataManager INSTANCE = new ProtectionDataManager();
    }
}
