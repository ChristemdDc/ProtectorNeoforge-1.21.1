package com.tumod.protectormod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.*;

/**
 * Marca y verifica bloques especiales de Redstone y Create que pertenecen a
 * zonas de protección. El HashMap PROTECTED_BLOCKS es la fuente de verdad en runtime;
 * la persistencia la lleva {@link ProtectedBlockData} (SavedData/NBT dentro del mundo).
 *
 * <p>Historial: antes se usaba SQLite (JNI nativo) — reemplazado por SavedData para eliminar
 * binarios nativos, agrupar escrituras en el guardado del mundo y ser coherente con backups.
 */
public class ProtectedBlockMarker {

    private static final Map<String, ProtectionData> PROTECTED_BLOCKS = new HashMap<>();

    public static class ProtectionData {
        public UUID owner;
        public BlockPos corePos;
        public long timestamp;

        public ProtectionData(UUID owner, BlockPos corePos, long timestamp) {
            this.owner = owner;
            this.corePos = corePos;
            this.timestamp = timestamp;
        }
    }

    static String getCacheKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** Carga la persistencia al arrancar (el SavedData puebla el cache vía {@link #loadFromDisk}). */
    public static void init(ServerLevel overworld) {
        PROTECTED_BLOCKS.clear();
        ProtectedBlockData.get(overworld); // dispara load() → loadFromDisk, o crea un almacén vacío
    }

    // ── Puente con ProtectedBlockData (persistencia NBT) ──

    /** Rellena el cache desde disco (llamado por ProtectedBlockData.load). */
    static void loadFromDisk(Map<String, ProtectionData> map) {
        PROTECTED_BLOCKS.clear();
        PROTECTED_BLOCKS.putAll(map);
    }

    /** Vista del cache para serializar (llamado por ProtectedBlockData.save). */
    static Map<String, ProtectionData> snapshot() {
        return PROTECTED_BLOCKS;
    }

    public static boolean isSpecialBlock(Block block) {
        return isRedstoneBlock(block) || isCreateBlock(block);
    }

    private static final Set<String> REDSTONE_BLOCK_IDS = Set.of(
        "minecraft:dispenser",
        "minecraft:dropper",
        "minecraft:piston",
        "minecraft:sticky_piston",
        "minecraft:observer",
        "minecraft:hopper",
        "minecraft:comparator",
        "minecraft:repeater",
        "minecraft:redstone_wire",
        "minecraft:redstone_torch",
        "minecraft:redstone_wall_torch",
        "minecraft:redstone_block",
        "minecraft:redstone_lamp",
        "minecraft:daylight_detector",
        "minecraft:target",
        "minecraft:lever",
        "minecraft:tripwire_hook",
        "minecraft:trapped_chest",
        "minecraft:tnt",
        "minecraft:note_block",
        "minecraft:sculk_sensor",
        "minecraft:calibrated_sculk_sensor"
    );

    public static boolean isRedstoneBlock(Block block) {
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        return REDSTONE_BLOCK_IDS.contains(id);
    }

    public static boolean isCreateBlock(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).toString();
        return name.startsWith("create:");
    }

    /**
     * Detecta si un Player es un FakePlayer (Create, mods de automatización).
     * Los FakePlayer no están en la lista de jugadores reales del servidor.
     */
    public static boolean isFakePlayer(Player player) {
        if (player == null) return false;
        if (!(player instanceof ServerPlayer sp)) return true;
        if (sp.getServer() == null) return true;
        return sp.getServer().getPlayerList().getPlayer(sp.getUUID()) == null;
    }

    /** Marca un bloque como protegido (cache en memoria + persistencia NBT). */
    public static void markBlockAsProtected(ServerLevel level, BlockPos pos, UUID owner, BlockPos corePos) {
        PROTECTED_BLOCKS.put(getCacheKey(pos), new ProtectionData(owner, corePos, System.currentTimeMillis()));
        ProtectedBlockData.markDirty();
    }

    public static ProtectionData getProtectionData(BlockPos pos) {
        return PROTECTED_BLOCKS.get(getCacheKey(pos));
    }

    /** Limpia un bloque del cache de protección y de la persistencia. */
    public static void unmarkBlockAsProtected(BlockPos pos) {
        if (PROTECTED_BLOCKS.remove(getCacheKey(pos)) != null) {
            ProtectedBlockData.markDirty();
        }
    }

    public static int getCacheSize() {
        return PROTECTED_BLOCKS.size();
    }

    public static boolean hasMarkedBlocksForCore(BlockPos corePos) {
        for (ProtectionData data : PROTECTED_BLOCKS.values()) {
            if (data.corePos.equals(corePos)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasMarkedBlockNearby(BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = center.offset(x, y, z);
                    if (getProtectionData(check) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Bypass de FakePlayer: solo si hay un bloque marcado en/junto a su posición
     * (radio 1 para máquinas multi-bloque).
     */
    public static boolean shouldFakePlayerBypass(Player player, BlockPos actionPos, Level level) {
        return hasMarkedBlockNearby(player.blockPosition(), 1);
    }
}
