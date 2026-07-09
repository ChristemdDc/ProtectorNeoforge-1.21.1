package com.tumod.protectormod.util;

import net.minecraft.core.BlockPos;

/**
 * Marca cuándo el hilo del servidor está ejecutando {@code FireBlock.tick()} (propagación de fuego).
 * Lo usa el mixin de {@code Level.setBlock} para distinguir:
 * <ul>
 *   <li><b>Colocar fuego directamente</b> (flint&steel, dispensador…): NO pasa por tick →
 *       {@link #current()} es null → se permite.</li>
 *   <li><b>Propagación</b>: el fuego que tiquea intenta poner fuego en una posición NUEVA →
 *       {@link #current()} != null y distinta de la posición destino → se bloquea si la protección
 *       tiene "fire-spread" OFF.</li>
 * </ul>
 * Así las granjas pueden colocar fuego, pero el fuego no se propaga dentro de la zona protegida.
 */
public final class FireSpreadContext {

    private static final ThreadLocal<BlockPos> TICKING_FIRE = new ThreadLocal<>();

    private FireSpreadContext() {}

    public static void enter(BlockPos pos) { TICKING_FIRE.set(pos); }

    public static void exit() { TICKING_FIRE.remove(); }

    /** Posición del fuego que se está propagando ahora mismo, o null si no hay tick de fuego en curso. */
    public static BlockPos current() { return TICKING_FIRE.get(); }
}
