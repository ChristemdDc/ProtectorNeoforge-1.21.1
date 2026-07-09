package com.tumod.protectormod.mixin;

import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.permission.ClaimPermissionResolver;
import com.tumod.protectormod.integration.aeroclaims.ProtectorAeroPermissionResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integración standalone con AeroClaims (sin OPAC/FTB): fuerza que la resolución de permisos de
 * claims use los clanes/party de ProtectorMod, sustituyendo la selección por config de AeroClaims
 * (que solo conoce OPAC/FTB). AeroClaims queda intacto; todo vive en ProtectorMod.
 */
@Mixin(ClaimManager.class)
public class MixinAeroPartyResolver {

    @Inject(method = "buildResolver", at = @At("HEAD"), cancellable = true, remap = false)
    private static void protector$buildResolver(CallbackInfoReturnable<ClaimPermissionResolver> cir) {
        cir.setReturnValue(new ProtectorAeroPermissionResolver());
    }
}
