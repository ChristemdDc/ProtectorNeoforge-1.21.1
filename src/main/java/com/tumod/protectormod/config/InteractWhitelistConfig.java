package com.tumod.protectormod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InteractWhitelistConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FMLPaths.CONFIGDIR.get().toFile(), "protectormod-interact-whitelist.json");

    private static InteractWhitelistConfig INSTANCE;

    public Set<String> whitelistedBlocks = new HashSet<>();

    public static InteractWhitelistConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, InteractWhitelistConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                INSTANCE = new InteractWhitelistConfig();
            }
        } else {
            INSTANCE = new InteractWhitelistConfig();
            INSTANCE.addDefaults();
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addDefaults() {
        whitelistedBlocks.addAll(List.of(
                "minecraft:oak_door",
                "minecraft:spruce_door",
                "minecraft:birch_door",
                "minecraft:jungle_door",
                "minecraft:acacia_door",
                "minecraft:dark_oak_door",
                "minecraft:mangrove_door",
                "minecraft:cherry_door",
                "minecraft:bamboo_door",
                "minecraft:crimson_door",
                "minecraft:warped_door",
                "minecraft:iron_door",

                "minecraft:oak_trapdoor",
                "minecraft:spruce_trapdoor",
                "minecraft:birch_trapdoor",
                "minecraft:jungle_trapdoor",
                "minecraft:acacia_trapdoor",
                "minecraft:dark_oak_trapdoor",
                "minecraft:mangrove_trapdoor",
                "minecraft:cherry_trapdoor",
                "minecraft:bamboo_trapdoor",
                "minecraft:crimson_trapdoor",
                "minecraft:warped_trapdoor",
                "minecraft:iron_trapdoor",

                "minecraft:oak_fence_gate",
                "minecraft:spruce_fence_gate",
                "minecraft:birch_fence_gate",
                "minecraft:jungle_fence_gate",
                "minecraft:acacia_fence_gate",
                "minecraft:dark_oak_fence_gate",
                "minecraft:mangrove_fence_gate",
                "minecraft:cherry_fence_gate",
                "minecraft:bamboo_fence_gate",
                "minecraft:crimson_fence_gate",
                "minecraft:warped_fence_gate",

                "minecraft:lever",
                "minecraft:stone_button",
                "minecraft:oak_button",
                "minecraft:spruce_button",
                "minecraft:birch_button",
                "minecraft:jungle_button",
                "minecraft:acacia_button",
                "minecraft:dark_oak_button",
                "minecraft:mangrove_button",
                "minecraft:cherry_button",
                "minecraft:bamboo_button",
                "minecraft:crimson_button",
                "minecraft:warped_button",
                "minecraft:polished_blackstone_button",

                "minecraft:white_bed",
                "minecraft:orange_bed",
                "minecraft:magenta_bed",
                "minecraft:light_blue_bed",
                "minecraft:yellow_bed",
                "minecraft:lime_bed",
                "minecraft:pink_bed",
                "minecraft:gray_bed",
                "minecraft:light_gray_bed",
                "minecraft:cyan_bed",
                "minecraft:purple_bed",
                "minecraft:blue_bed",
                "minecraft:brown_bed",
                "minecraft:green_bed",
                "minecraft:red_bed",
                "minecraft:black_bed",

                "minecraft:crafting_table",
                "minecraft:enchanting_table",
                "minecraft:ender_chest",
                "minecraft:bell"
        ));
    }

    public boolean isWhitelisted(String blockId) {
        return whitelistedBlocks.contains(blockId);
    }
}
