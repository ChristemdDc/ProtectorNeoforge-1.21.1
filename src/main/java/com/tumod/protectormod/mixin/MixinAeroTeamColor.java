package com.tumod.protectormod.mixin;

import com.mapter.aeroclaims.util.TeamColorHelper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Integración standalone con AeroClaims: el color de "team" de un claim se calcula sin OPAC/FTB.
 * El {@code getTeamColor} original ramifica según el partyProvider (OPAC/FTB) y tocaría clases
 * ausentes → aquí devolvemos un color estable por dueño, evitando esas dependencias.
 */
@Mixin(TeamColorHelper.class)
public class MixinAeroTeamColor {

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void protector$getTeamColor(ServerPlayer player, UUID owner, CallbackInfoReturnable<Integer> cir) {
        // Color ARGB opaco derivado del UUID del dueño (estable y distinto por jugador).
        cir.setReturnValue(0xFF000000 | (owner.hashCode() & 0xFFFFFF));
    }
}
