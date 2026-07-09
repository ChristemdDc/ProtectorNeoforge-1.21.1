package com.tumod.protectormod.mixin;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.FireSpreadContext;
import com.tumod.protectormod.util.ProtectedBlockMarker;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin sobre {@code Level.setBlock()}:
 * <ul>
 *   <li>TAIL: limpia la marca (cache + SQLite) cuando un bloque marcado pasa a aire.</li>
 *   <li>HEAD: flag "fire-spread" — si el fuego se está PROPAGANDO (dentro de FireBlock.tick) hacia una
 *       posición nueva dentro de una protección con la flag OFF, se cancela. Colocar fuego a mano o
 *       con dispensador NO pasa por tick → se permite (las granjas siguen funcionando).</li>
 * </ul>
 */
@Mixin(Level.class)
public class MixinLevelBlockModification {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void protector_onSetBlockHead(BlockPos pos, BlockState newState, int flags, int recursionLeft,
                                          CallbackInfoReturnable<Boolean> cir) {
        // Short-circuit barato: la inmensa mayoría de setBlock NO son fuego.
        if (!(newState.getBlock() instanceof BaseFireBlock)) return;

        // Solo bloquear si es PROPAGACIÓN (estamos dentro de FireBlock.tick y el destino es una
        // posición NUEVA, distinta del fuego que tiquea). Colocación directa → current()==null → permitir.
        BlockPos ticking = FireSpreadContext.current();
        if (ticking == null || ticking.equals(pos)) return;

        Level level = (Level) (Object) this;
        if (level.isClientSide || !(level instanceof ServerLevel sLevel)) return;

        ProtectionDataManager.CoreEntry entry = ProtectionDataManager.get(sLevel).getCoreAt(pos);
        if (entry == null) return;
        // Solo leer el BE si su chunk ya está cargado (evita forzar cargas dentro de setBlock).
        if (!sLevel.isLoaded(entry.pos())) return;
        if (sLevel.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity core
                && !core.getFlag("fire-spread")) {
            cir.setReturnValue(false); // no propagar el fuego a esta posición
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("TAIL"))
    private void protector_onSetBlockTail(BlockPos pos, BlockState newState, int flags, int recursionLeft,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;

        Level level = (Level) (Object) this;
        if (level.isClientSide || !(level instanceof ServerLevel)) return;

        if (newState.isAir()) {
            ProtectedBlockMarker.unmarkBlockAsProtected(pos);
        }
    }
}
