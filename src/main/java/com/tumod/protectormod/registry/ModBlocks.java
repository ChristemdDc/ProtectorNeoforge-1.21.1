package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.block.AdminProtectorBlock;
import com.tumod.protectormod.block.MechanicalProtectorBlock;
import com.tumod.protectormod.block.ProtectionCoreBlock;
import net.minecraft.resources.ResourceLocation;
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

    // Proteccion EXTRA con render animado de AnimaSlime. Misma funcionalidad que PROTECTION_CORE
    // (hereda de ProtectionCoreBlock); solo cambia el aspecto. Mismas propiedades que el nucleo
    // clasico para que el comportamiento (dureza, luz, noOcclusion) sea identico.
    public static final DeferredBlock<MechanicalProtectorBlock> MECHANICAL_PROTECTOR = BLOCKS.registerBlock(
            "mechanical_protector",
            MechanicalProtectorBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 1200.0f)
                    .noOcclusion() // el modelo no llena la celda: sin esto se ven caras recortadas
                    .lightLevel(state -> 10)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .pushReaction(PushReaction.BLOCK)
    );

    // Modelo animado del Protector Mecanico (AnimaSlime). Lo pinta el motor via
    // ProtectorAnimaSlimeModels. Ruta: assets/protectormod/animaslime/prote_mecanico.animaslime
    public static final ResourceLocation PROTE_MECANICO_MODEL =
            ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "animaslime/prote_mecanico.animaslime");

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
