package com.tumod.protectormod.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config de ProtectorMod (tipo SERVER). Límites de colocación de protecciones.
 */
public class ProtectorConfig {

    public static final ModConfigSpec SPEC;

    /** Protecciones que un clan puede colocar por defecto (override por clan con /clan protections). */
    public static final ModConfigSpec.IntValue DEFAULT_CLAN_PROTECTIONS;
    /** Protecciones que un jugador SIN clan puede colocar. */
    public static final ModConfigSpec.IntValue CLANLESS_PROTECTIONS;
    /** Máximo de alianzas que un clan puede tener a la vez. */
    public static final ModConfigSpec.IntValue MAX_ALLIANCES;
    /** Slots de claim de AeroClaims por jugador (integración standalone, sin OPAC/FTB). */
    public static final ModConfigSpec.IntValue AERO_MAX_CLAIMS_PER_PLAYER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("protections");
        DEFAULT_CLAN_PROTECTIONS = builder
                .comment("Número de protecciones (núcleos) que un clan puede colocar por defecto.",
                         "Un admin puede ajustarlo por clan con /clan protections <clan> <n>. Default: 5")
                .defineInRange("defaultClanProtections", 5, 0, Integer.MAX_VALUE);
        CLANLESS_PROTECTIONS = builder
                .comment("Número de protecciones que un jugador SIN clan puede colocar. Default: 1")
                .defineInRange("clanlessProtections", 1, 0, Integer.MAX_VALUE);
        MAX_ALLIANCES = builder
                .comment("Máximo de alianzas que un clan puede tener a la vez. Default: 2")
                .defineInRange("maxAlliances", 2, 0, Integer.MAX_VALUE);
        builder.pop();

        builder.push("aeroclaims");
        AERO_MAX_CLAIMS_PER_PLAYER = builder
                .comment("Integración con Aeronautics Claims (standalone, sin OPAC/FTB):",
                         "slots de claim que tiene cada jugador para reclamar ships. Default: 20")
                .defineInRange("aeroMaxClaimsPerPlayer", 20, 0, Integer.MAX_VALUE);
        builder.pop();

        SPEC = builder.build();
    }

    private ProtectorConfig() {}
}
