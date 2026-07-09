package com.tumod.protectormod.util;

import net.minecraft.client.Minecraft;

/**
 * Traducciones de los flags del sistema de protección (lado cliente).
 */
public class FlagTranslations {

    public static String getFlagName(String flagId) {
        String language = Minecraft.getInstance().options.languageCode;

        if (language.startsWith("es")) {
            return getFlagNameSpanish(flagId);
        }
        return getFlagNameEnglish(flagId);
    }

    private static String getFlagNameSpanish(String flagId) {
        return switch (flagId) {
            case "build" -> "Construir";
            case "pvp" -> "PvP";
            case "chests" -> "Cofres";
            case "interact" -> "Interactuar";
            case "lighter" -> "Encendedor";
            case "villager-trade" -> "Comercio Aldeano";
            case "hunger" -> "Hambre";
            case "crop-trample" -> "Pisar Cultivos";
            case "use-buckets" -> "Usar Cubos";
            case "item-pickup" -> "Recoger Objetos";
            case "item-drop" -> "Soltar Objetos";
            case "entry" -> "Entrada";
            case "mob-spawn" -> "Generación de Mobs";
            case "fire-damage" -> "Daño por Fuego";
            case "fall-damage" -> "Daño por Caída";
            case "explosions" -> "Explosiones";
            case "fire-spread" -> "Propagación de Fuego";
            case "mob-grief" -> "Daño de Mobs";
            case "enderpearl" -> "Perla de Ender";
            default -> capitalize(flagId);
        };
    }

    private static String getFlagNameEnglish(String flagId) {
        return switch (flagId) {
            case "build" -> "Build";
            case "pvp" -> "PvP";
            case "chests" -> "Chests";
            case "interact" -> "Interact";
            case "lighter" -> "Lighter";
            case "villager-trade" -> "Villager Trade";
            case "hunger" -> "Hunger";
            case "crop-trample" -> "Crop Trample";
            case "use-buckets" -> "Use Buckets";
            case "item-pickup" -> "Item Pickup";
            case "item-drop" -> "Item Drop";
            case "entry" -> "Entry";
            case "mob-spawn" -> "Mob Spawn";
            case "fire-damage" -> "Fire Damage";
            case "fall-damage" -> "Fall Damage";
            case "explosions" -> "Explosions";
            case "fire-spread" -> "Fire Spread";
            case "mob-grief" -> "Mob Grief";
            case "enderpearl" -> "Enderpearl";
            default -> capitalize(flagId);
        };
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }
}
