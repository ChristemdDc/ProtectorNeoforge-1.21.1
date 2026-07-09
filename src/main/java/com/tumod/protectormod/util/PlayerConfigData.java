package com.tumod.protectormod.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerConfigData extends SavedData {

    private static final String DATA_NAME = "protector_player_config";

    private final Set<UUID> testModePlayers = new HashSet<>();
    private final Set<UUID> visualizerPlayers = new HashSet<>();

    public static final SavedData.Factory<PlayerConfigData> FACTORY =
            new SavedData.Factory<>(PlayerConfigData::new, PlayerConfigData::load, null);

    public static PlayerConfigData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean isTestMode(UUID playerUUID) {
        return testModePlayers.contains(playerUUID);
    }

    public void setTestMode(UUID playerUUID, boolean active) {
        if (active) {
            testModePlayers.add(playerUUID);
        } else {
            testModePlayers.remove(playerUUID);
        }
        setDirty();
    }

    public boolean isVisualizerEnabled(UUID playerUUID) {
        return visualizerPlayers.contains(playerUUID);
    }

    public void setVisualizer(UUID playerUUID, boolean active) {
        if (active) {
            visualizerPlayers.add(playerUUID);
        } else {
            visualizerPlayers.remove(playerUUID);
        }
        setDirty();
    }

    public static PlayerConfigData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlayerConfigData data = new PlayerConfigData();

        if (tag.contains("TestModeList")) {
            ListTag list = tag.getList("TestModeList", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.testModePlayers.add(UUID.fromString(list.getString(i)));
            }
        }

        if (tag.contains("VisualizerList")) {
            ListTag list = tag.getList("VisualizerList", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                data.visualizerPlayers.add(UUID.fromString(list.getString(i)));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag testList = new ListTag();
        for (UUID uuid : testModePlayers) {
            testList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("TestModeList", testList);

        ListTag visList = new ListTag();
        for (UUID uuid : visualizerPlayers) {
            visList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("VisualizerList", visList);

        return tag;
    }
}
