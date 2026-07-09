package com.tumod.protectormod.mixin;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.event.ModEvents;
import com.tumod.protectormod.util.ProtectedBlockMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Protege el uso de items sobre bloques (llaves inglesas de Create, super glue,
 * andesite alloy, etc.) cuando "build" está desactivado, y aplica el bypass de
 * FakePlayers. Cubre rutas de {@code ItemStack.useOn} que no dispara el evento
 * RightClickBlock (p.ej. automatización de Create).
 */
@Mixin(ItemStack.class)
public class MixinItemStack {

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void onUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Player player = context.getPlayer();
        if (player == null || player.level().isClientSide) return;

        BlockPos pos = context.getClickedPos();
        ProtectionCoreBlockEntity core = ModEvents.findCoreAt(player.level(), pos);

        // FakePlayer (máquinas de Create, etc.) → solo permitir si tiene bypass
        if (ProtectedBlockMarker.isFakePlayer(player)) {
            if (core != null) {
                if (ProtectedBlockMarker.shouldFakePlayerBypass(player, pos, player.level())) {
                    return;
                } else {
                    cir.setReturnValue(InteractionResult.FAIL);
                    return;
                }
            }
            return;
        }

        // Admin Core: bloquear andesite alloy sobre troncos pelados (Create)
        if (core != null && core.isAdmin()) {
            ItemStack stack = context.getItemInHand();
            BlockState state = player.level().getBlockState(pos);

            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();

            if (itemId.equals("create:andesite_alloy")) {
                if (blockId.contains("stripped_") && (blockId.contains("_log") || blockId.contains("_wood"))) {
                    if (player instanceof ServerPlayer sp) {
                        sp.displayClientMessage(Component.literal("§c[!] Interacción de Create bloqueada en zona administrativa."), true);
                    }
                    cir.setReturnValue(InteractionResult.FAIL);
                    return;
                }
            }
        }

        if (core != null && !core.isTrusted(player)) {
            if (!core.getFlag("build")) {
                String itemId = BuiltInRegistries.ITEM.getKey(context.getItemInHand().getItem()).toString();
                if (itemId.equals("create:super_glue")) {
                    if (player instanceof ServerPlayer sp) {
                        if (player.tickCount % 20 == 0) {
                            sp.displayClientMessage(Component.literal("§c[!] No puedes usar pegamento aquí."), true);
                        }
                    }
                    cir.setReturnValue(InteractionResult.FAIL);
                    return;
                }

                if (player instanceof ServerPlayer sp) {
                    if (player.tickCount % 20 == 0) {
                        sp.displayClientMessage(Component.literal("§c[!] No puedes usar herramientas de modificación aquí."), true);
                    }
                }
                cir.setReturnValue(InteractionResult.FAIL);
            }
        }
    }
}
