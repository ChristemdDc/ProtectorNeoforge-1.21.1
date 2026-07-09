package com.tumod.protectormod.protection;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Busca si existe un nucleo de proteccion que cubra la posicion dada.
 *
 * <p>Version 100% Java: el antiguo motor nativo C++ (ProtectorBridge / ProtectorEngineWrapper /
 * src/main/cpp) fue eliminado en la migracion a NeoForge 1.21.1. El indice espacial vive en
 * {@link ProtectionDataManager}, que ya ofrece una busqueda O(n) sobre los nucleos del nivel.
 */
public class ProtectionUtils {

    public static ProtectionCoreBlockEntity getCoreAt(LevelAccessor accessor, BlockPos pos) {
        if (!(accessor instanceof ServerLevel sLevel)) {
            return null;
        }

        ProtectionDataManager manager = ProtectionDataManager.get(sLevel);
        ProtectionDataManager.CoreEntry entry = manager.getCoreAt(pos);
        if (entry == null) {
            return null;
        }

        BlockEntity be = sLevel.getBlockEntity(entry.pos());
        if (be instanceof ProtectionCoreBlockEntity core && !core.isRemoved() && core.isInside(pos)) {
            return core;
        }
        return null;
    }
}
