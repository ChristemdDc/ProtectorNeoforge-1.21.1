package com.tumod.protectormod.integration;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/**
 * Detecta si una posición está dentro de un ship/sublevel de Create Aeronautics (Sable).
 *
 * <p>Fase 4: ProtectorMod NO debe actuar dentro de los ships — AeroClaims es el único
 * dueño de ese espacio. Este guard permite que los handlers de ProtectorMod se retiren
 * cuando la acción ocurre dentro de un ship.
 *
 * <p>Dependencia blanda: si 'sable' no está cargado, siempre devuelve false (ProtectorMod
 * actúa normal). El acceso real a clases de Sable se aísla en {@link SableAccess} para no
 * cargarlas cuando Sable está ausente.
 */
public final class ShipGuard {

    private static final boolean SABLE_LOADED = ModList.get().isLoaded("sable");

    private ShipGuard() {}

    public static boolean isInsideShip(Level level, BlockPos pos) {
        if (!SABLE_LOADED || level == null || pos == null) return false;
        try {
            return SableAccess.isInsideShip(level, pos);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Footprint (AABB en X/Z) de un ship EN EL MUNDO. Usado para saber si una nave ensamblada
     * (cuyos bloques viven en coords de sublevel) solapa una protección del mundo. */
    public record ShipBox(double minX, double minZ, double maxX, double maxZ) {}

    /**
     * Si {@code pos} pertenece a un ship (coords del mundo o del sublevel), devuelve el AABB de esa
     * nave EN COORDENADAS DEL MUNDO; null si no hay ship o Sable no está. Sirve para el anti-robo:
     * al desensamblar una nave ensamblada el ensamblador está en el sublevel, pero su footprint
     * físico en el mundo es lo que hay que comparar contra las protecciones.
     */
    public static ShipBox worldBoundsOfShipAt(Level level, BlockPos pos) {
        if (!SABLE_LOADED || level == null || pos == null) return null;
        try {
            return SableAccess.worldBounds(level, pos);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Aislado para que las clases de Sable solo se carguen si Sable está presente. */
    private static final class SableAccess {
        static boolean isInsideShip(Level level, BlockPos pos) {
            return SableCompanion.INSTANCE.getContaining(level, pos) != null;
        }

        static ShipBox worldBounds(Level level, BlockPos pos) {
            dev.ryanhcode.sable.companion.SubLevelAccess ship = SableCompanion.INSTANCE.getContaining(level, pos);
            if (ship == null) return null;
            dev.ryanhcode.sable.companion.math.BoundingBox3dc box = ship.boundingBox();
            return new ShipBox(box.minX(), box.minZ(), box.maxX(), box.maxZ());
        }
    }
}
