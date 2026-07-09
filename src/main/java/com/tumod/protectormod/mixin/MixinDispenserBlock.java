package com.tumod.protectormod.mixin;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.event.ModEvents;
import com.tumod.protectormod.util.ProtectedBlockMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Permite que un dispenser MARCADO (colocado por un miembro de confianza) dispense
 * fluidos ignorando la bandera "use-buckets". Un dispenser sin marcar respeta la
 * bandera del destino: si "use-buckets" está OFF en la zona destino, no dispensa.
 *
 * <p>Firma 1.21: {@code getDispenseMethod(Level, ItemStack)}; {@code BlockSource} es un
 * record ({@code .level()}, {@code .pos()}, {@code .state()}).
 */
@Mixin(DispenserBlock.class)
public class MixinDispenserBlock {

    @Inject(
        method = "getDispenseMethod(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/core/dispenser/DispenseItemBehavior;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void protector_onGetDispenseMethod(net.minecraft.world.level.Level level, ItemStack stack,
                                               CallbackInfoReturnable<DispenseItemBehavior> cir) {
        // Solo interceptamos items de fluido (cubos / contenedores dispensables).
        if (!(stack.getItem() instanceof BucketItem) && !(stack.getItem() instanceof DispensibleContainerItem)) {
            return;
        }

        DispenseItemBehavior original = cir.getReturnValue();

        cir.setReturnValue((source, itemStack) -> {
            ServerLevel sLevel = source.level();
            BlockPos dispenserPos = source.pos();

            // Dispenser marcado (colocado por miembro de confianza) → permitir.
            if (ProtectedBlockMarker.getProtectionData(dispenserPos) != null) {
                return original.dispense(source, itemStack);
            }

            // Dispenser sin marcar → comprobar el destino.
            BlockPos targetPos = dispenserPos.relative(source.state().getValue(DispenserBlock.FACING));
            ProtectionCoreBlockEntity core = ModEvents.findCoreAt(sLevel, targetPos);
            if (core != null && !core.getFlag("use-buckets")) {
                return itemStack; // bloqueado: devuelve el item sin dispensar
            }

            return original.dispense(source, itemStack);
        });
    }
}
