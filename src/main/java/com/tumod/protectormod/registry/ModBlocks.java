package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.block.AdminProtectorBlock;
import com.tumod.protectormod.block.ProtectionCoreBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProtectorMod.MOD_ID);

    public static final DeferredBlock<ProtectionCoreBlock> PROTECTION_CORE = BLOCKS.registerBlock(
            "protection_core",
            ProtectionCoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 1200.0f)
                    .noOcclusion()
                    .lightLevel(state -> 10)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .pushReaction(PushReaction.BLOCK)
    );

    public static final DeferredBlock<AdminProtectorBlock> ADMIN_PROTECTOR = BLOCKS.registerBlock(
            "admin_protector",
            AdminProtectorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(-1.0f, 3600000.0f)
                    .noOcclusion()
                    .lightLevel(state -> 15)
                    .pushReaction(PushReaction.BLOCK)
    );

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
