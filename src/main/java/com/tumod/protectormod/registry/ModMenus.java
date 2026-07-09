package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ProtectorMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ProtectionCoreMenu>> PROTECTION_CORE_MENU =
            MENUS.register("protection_core_menu",
                    () -> IMenuTypeExtension.create(ProtectionCoreMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ProtectionCoreMenu>> ADMIN_CORE_MENU =
            MENUS.register("admin_core_menu",
                    () -> IMenuTypeExtension.create(ProtectionCoreMenu::createAdminMenu));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
