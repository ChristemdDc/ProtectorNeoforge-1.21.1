package com.tumod.protectormod.integration;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/**
 * Anti-robo de naves (F9): decide si un jugador puede ensamblar/desensamblar un Physics Assembler
 * (Create Aeronautics) en una posición dada. Lo usa {@code MixinAssemblePacket} para cancelar el
 * paquete de ensamblado igual que hace AeroClaims con sus claims, pero decidiendo con NUESTRAS
 * protecciones — así una nave aparcada dentro de una protección (sin aeroclaim) no puede ser
 * desarmada por un ajeno para robar sus bloques.
 *
 * <p>Regla: (des)ensamblar = modificar bloques → requiere <b>permiso de construcción</b> en el
 * núcleo. Se exige en TODOS los núcleos que cubren el ensamblador (el más restrictivo manda): si
 * el jugador no tiene build en alguno, se bloquea. Así se respetan los permisos B/I/C por-núcleo:
 * un miembro con build OFF en ese núcleo NO puede desensamblar, aunque en otro tenga build ON.
 *
 * <p>A diferencia del resto del mod (que cede dentro de ships vía {@code ShipGuard}), aquí SÍ se
 * guarda aunque el ensamblador esté en un ship: el objetivo es precisamente proteger la nave.
 */
public final class AssemblyGuard {

    private AssemblyGuard() {}

    /** @return true si el jugador puede (des)ensamblar en {@code pos}; false si debe bloquearse. */
    public static boolean canModifyAt(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (player == null || pos == null) return true;
        if (player.hasPermissions(2)) return true; // OP

        ProtectionDataManager pdm = ProtectionDataManager.get(level);

        // 1) Comprobación directa por la pos del ensamblador (nave EN REPOSO → coords del mundo).
        for (ProtectionDataManager.CoreEntry entry : pdm.getCoresAt(pos)) {
            if (!canBuildInCore(level, entry, player)) return false;
        }

        // 2) Nave ENSAMBLADA: el ensamblador vive en el sublevel (pos con coords enormes). Obtenemos
        //    el footprint de la nave EN EL MUNDO y bloqueamos si solapa una protección sin permiso.
        ShipGuard.ShipBox box = ShipGuard.worldBoundsOfShipAt(level, pos);
        if (box != null) {
            for (ProtectionDataManager.CoreEntry entry : pdm.getAllCores().values()) {
                if (overlapsXZ(box, entry) && !canBuildInCore(level, entry, player)) return false;
            }
        }
        return true;
    }

    /** ¿El footprint de la nave (X/Z mundo) solapa el cuadrado de la protección? */
    private static boolean overlapsXZ(ShipGuard.ShipBox b, ProtectionDataManager.CoreEntry e) {
        int cx = e.pos().getX(), cz = e.pos().getZ(), r = e.radius();
        return b.maxX() >= cx - r && b.minX() <= cx + r
                && b.maxZ() >= cz - r && b.minZ() <= cz + r;
    }

    /** ¿El jugador tiene permiso de construcción en este núcleo concreto? */
    private static boolean canBuildInCore(ServerLevel level, ProtectionDataManager.CoreEntry entry, ServerPlayer player) {
        UUID me = player.getUUID();
        if (me.equals(entry.owner())) return true; // dueño del núcleo

        BlockEntity be = level.getBlockEntity(entry.pos());
        if (be instanceof ProtectionCoreBlockEntity core) {
            // isTrusted = permiso de construcción efectivo en ESTE núcleo: dueño/OP, miembro del clan
            // (override por-núcleo o full por defecto), o aliado con build. Respeta el B/I/C por-núcleo.
            return core.isTrusted(player);
        }
        // Núcleo distante (sin BE): no podemos leer el override por-núcleo. Permitir a los miembros
        // del clan dueño (mejor que bloquear a todo el equipo); los ajenos quedan bloqueados.
        ServerLevel overworld = level.getServer().overworld();
        ClanSavedData.ClanInstance ownerClan = ClanSavedData.get(overworld).getClanByMember(entry.owner());
        return ownerClan != null && ownerClan.members.contains(me);
    }
}
