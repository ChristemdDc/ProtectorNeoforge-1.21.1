package com.tumod.protectormod.mixin;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.claim.IClaimProvider;
import com.tumod.protectormod.integration.aeroclaims.ProtectorAeroClaimProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integración standalone con AeroClaims: fuerza el proveedor de slots de claim de ProtectorMod
 * (cupo por config), sustituyendo la selección OPAC/FTB de AeroClaims. Así no hace falta OPAC ni
 * FTB Chunks para reclamar ships.
 */
@Mixin(AeroClaimManager.class)
public class MixinAeroClaimProvider {

    @Inject(method = "buildProvider", at = @At("HEAD"), cancellable = true, remap = false)
    private static void protector$buildProvider(CallbackInfoReturnable<IClaimProvider> cir) {
        cir.setReturnValue(new ProtectorAeroClaimProvider());
    }
}
