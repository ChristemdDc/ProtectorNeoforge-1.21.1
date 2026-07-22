package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProtectorMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PROTECTOR_TAB =
            TABS.register("protector_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.protector_tab"))
                    .icon(() -> new ItemStack(ModItems.PROTECTION_CORE_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.ADMIN_PROTECTOR_ITEM.get());
                        output.accept(ModItems.PROTECTION_CORE_ITEM.get());
                        output.accept(ModItems.MECHANICAL_PROTECTOR_ITEM.get());
                        output.accept(ModItems.PROTECTION_UPGRADE.get());
                    })
                    .build());

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
