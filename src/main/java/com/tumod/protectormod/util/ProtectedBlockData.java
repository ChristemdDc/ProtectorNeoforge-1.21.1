package com.tumod.protectormod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistencia (NBT, dentro del guardado del mundo) de los bloques marcados de
 * {@link ProtectedBlockMarker}. Reemplaza a la antigua base SQLite ({@code ProtectedBlockDatabase}).
 *
 * <p>El {@code HashMap} en memoria de {@link ProtectedBlockMarker} sigue siendo la fuente de verdad
 * en runtime; este {@link SavedData} solo lo carga al arrancar y lo vuelca cuando el mundo guarda.
 * Ventajas frente a SQLite: sin binarios nativos (JNI), sin escrituras síncronas por cada marca
 * (se agrupan en el guardado del mundo), y coherencia con backups/rollbacks del mundo.
 *
 * <p>Se guarda en el overworld (un único almacén global), igual que {@code ClanSavedData}.
 */
public class ProtectedBlockData extends SavedData {

    private static final String NAME = "protector_marked_blocks";
    private static ProtectedBlockData INSTANCE;

    public static final Factory<ProtectedBlockData> FACTORY =
            new Factory<>(ProtectedBlockData::new, ProtectedBlockData::load, null);

    /** Carga (o crea) el almacén en el overworld; su carga puebla el cache de ProtectedBlockMarker. */
    public static ProtectedBlockData get(ServerLevel level) {
        INSTANCE = level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, NAME);
        return INSTANCE;
    }

    /** Marca el almacén como sucio (para que se guarde) tras un cambio en ProtectedBlockMarker. */
    static void markDirty() {
        if (INSTANCE != null) INSTANCE.setDirty();
    }

    public static ProtectedBlockData load(CompoundTag tag, HolderLookup.Provider registries) {
        ProtectedBlockData data = new ProtectedBlockData();
        Map<String, ProtectedBlockMarker.ProtectionData> map = new HashMap<>();
        ListTag list = tag.getList("Blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            String key = c.getString("Key");
            UUID owner = c.hasUUID("Owner") ? c.getUUID("Owner") : null;
            BlockPos corePos = BlockPos.of(c.getLong("Core"));
            long ts = c.getLong("Ts");
            map.put(key, new ProtectedBlockMarker.ProtectionData(owner, corePos, ts));
        }
        ProtectedBlockMarker.loadFromDisk(map);
        INSTANCE = data;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, ProtectedBlockMarker.ProtectionData> e : ProtectedBlockMarker.snapshot().entrySet()) {
            ProtectedBlockMarker.ProtectionData d = e.getValue();
            CompoundTag c = new CompoundTag();
            c.putString("Key", e.getKey());
            if (d.owner != null) c.putUUID("Owner", d.owner);
            c.putLong("Core", d.corePos.asLong());
            c.putLong("Ts", d.timestamp);
            list.add(c);
        }
        tag.put("Blocks", list);
        return tag;
    }
}
