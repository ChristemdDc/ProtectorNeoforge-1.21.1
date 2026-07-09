package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.AdminProtectorBlockEntity;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProtectorMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProtectionCoreBlockEntity>> PROTECTION_CORE_BE =
            BLOCK_ENTITIES.register("protection_core_be",
                    () -> BlockEntityType.Builder.of(ProtectionCoreBlockEntity::new, ModBlocks.PROTECTION_CORE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AdminProtectorBlockEntity>> ADMIN_PROTECTOR_BE =
            BLOCK_ENTITIES.register("admin_protector_be",
                    () -> BlockEntityType.Builder.of(AdminProtectorBlockEntity::new, ModBlocks.ADMIN_PROTECTOR.get()).build(null));

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
