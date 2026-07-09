package com.tumod.protectormod.mixin;

import com.tumod.protectormod.integration.AssemblyGuard;
import dev.simulated_team.simulated.network.packets.AssemblePacket;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Anti-robo de naves (F9): cancela en el servidor el paquete de (des)ensamblado del Physics
 * Assembler si el jugador no tiene permiso de construcción en la posición del ensamblador según
 * las protecciones de ProtectorMod. Espejo de {@code com.mapter.aeroclaims.mixin.MixinAssemblePacket},
 * pero decidiendo con nuestras protecciones (para naves aparcadas en protecciones sin aeroclaim).
 *
 * <p>Solo se aplica si el mod 'simulated' (Create Aeronautics) está presente
 * (ver {@link ProtectorMixinPlugin}).
 */
@Mixin(AssemblePacket.class)
public class MixinAssemblePacket {

    @Shadow
    private BlockPos pos;

    @Inject(method = "handle", remap = false, at = @At("HEAD"), cancellable = true)
    private void protectormod$onAssemble(ServerPacketContext context, CallbackInfo ci) {
        ServerPlayer player = context.player();
        if (player == null || !(player.level() instanceof ServerLevel level)) return;
        if (!AssemblyGuard.canModifyAt(level, this.pos, player)) {
            player.displayClientMessage(
                    Component.literal("§c[!] No puedes ensamblar/desensamblar naves en esta zona protegida."), true);
            ci.cancel();
        }
    }
}
