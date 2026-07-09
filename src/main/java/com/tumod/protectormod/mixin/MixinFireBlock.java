package com.tumod.protectormod.mixin;

import com.tumod.protectormod.util.FireSpreadContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marca la ventana de propagación de fuego: mientras {@code FireBlock.tick()} se ejecuta, se registra
 * la posición del fuego que tiquea en {@link FireSpreadContext}. Todos los {@code setBlock} de fuego
 * que ocurren dentro (propagación adyacente vía checkBurnOut y a distancia) quedan así identificados
 * como "propagación" y el mixin de {@code Level.setBlock} los bloquea si la flag "fire-spread" está OFF.
 * Las colocaciones directas (flint&steel, dispensador) no pasan por aquí → se permiten.
 */
@Mixin(FireBlock.class)
public class MixinFireBlock {

    @Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"))
    private void protector_fireTickHead(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        FireSpreadContext.enter(pos);
    }

    @Inject(method = "tick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("RETURN"))
    private void protector_fireTickReturn(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        FireSpreadContext.exit();
    }
}
