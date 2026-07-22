package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.item.ProtectionUpgradeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProtectorMod.MOD_ID);

    public static final DeferredItem<BlockItem> PROTECTION_CORE_ITEM = ITEMS.registerItem(
            "protection_core",
            props -> new BlockItem(ModBlocks.PROTECTION_CORE.get(), props),
            new Item.Properties().stacksTo(1).fireResistant()
    );

    public static final DeferredItem<BlockItem> ADMIN_PROTECTOR_ITEM = ITEMS.registerItem(
            "admin_protector",
            props -> new BlockItem(ModBlocks.ADMIN_PROTECTOR.get(), props),
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)
    );

    public static final DeferredItem<ProtectionUpgradeItem> PROTECTION_UPGRADE = ITEMS.registerItem(
            "protection_upgrade",
            ProtectionUpgradeItem::new,
            new Item.Properties().stacksTo(16).rarity(Rarity.RARE)
    );

    /** Item del Protector Mecanico (proteccion extra con render animado). Ver ModBlocks.MECHANICAL_PROTECTOR. */
    public static final DeferredItem<BlockItem> MECHANICAL_PROTECTOR_ITEM = ITEMS.registerItem(
            "mechanical_protector",
            props -> new BlockItem(ModBlocks.MECHANICAL_PROTECTOR.get(), props),
            new Item.Properties().stacksTo(1).fireResistant()
    );

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
