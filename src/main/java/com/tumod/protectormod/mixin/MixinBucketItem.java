package com.tumod.protectormod.mixin;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.event.ModEvents;
import com.tumod.protectormod.util.ProtectedBlockMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bloquea el vaciado de cubos (colocar líquido) según la bandera "use-buckets".
 *
 * <p>Se inyecta en la sobrecarga de 5 args de {@code emptyContents}, que es la que
 * realmente coloca el fluido (a la que llama {@code BucketItem.use()}). Cancelar el
 * evento RightClickBlock NO detiene esto — por eso hace falta el mixin.
 *
 * <p>Respeta máquinas: si quien vacía es un FakePlayer (brazo desplegador de Create,
 * etc.), solo se permite si la máquina fue colocada por un miembro de confianza
 * (bloque marcado). Los dispensers vanilla los gestiona {@code MixinDispenserBlock}.
 */
@Mixin(BucketItem.class)
public class MixinBucketItem {

    @Inject(
        method = "emptyContents(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;Lnet/minecraft/world/item/ItemStack;)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void protector_onEmptyContents(@Nullable Player player, Level level, BlockPos pos,
                                           @Nullable BlockHitResult hitResult, @Nullable ItemStack container,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide) return;

        ProtectionCoreBlockEntity core = ModEvents.findCoreAt(level, pos);

        // FakePlayer (Create deployer, etc.): permitir solo si la máquina tiene bypass.
        if (player != null && ProtectedBlockMarker.isFakePlayer(player)) {
            if (core != null) {
                if (ProtectedBlockMarker.shouldFakePlayerBypass(player, pos, level)) {
                    return; // bypass permitido
                } else {
                    cir.setReturnValue(false); // bypass denegado
                }
            }
            return;
        }

        // Jugador real.
        if (player instanceof ServerPlayer serverPlayer) {
            if (core != null && !core.getFlag("use-buckets") && !core.isTrusted(player)) {
                serverPlayer.displayClientMessage(Component.literal("§c[!] El uso de cubos está desactivado aquí."), true);
                serverPlayer.containerMenu.sendAllDataToRemote();
                cir.setReturnValue(false);
            }
        }
        // player == null (dispenser vanilla) → lo gestiona MixinDispenserBlock.
    }
}
