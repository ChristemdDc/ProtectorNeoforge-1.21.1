package com.tumod.protectormod.client;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.client.screen.AdminCoreScreen;
import com.tumod.protectormod.client.screen.ProtectionCoreScreen;
import com.tumod.protectormod.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Configuración del lado cliente (NeoForge 1.21).
 * El {@code bus} se auto-detecta por tipo de evento: RegisterMenuScreensEvent
 * corre en el mod bus, ClientTickEvent en el game bus.
 *
 * Nota: el render layer "cutout" de los bloques se define en el JSON del modelo
 * ("render_type": "minecraft:cutout"), no por código, en 1.21.
 */
@EventBusSubscriber(modid = ProtectorMod.MOD_ID, value = Dist.CLIENT)
public class ProtectorModClient {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PROTECTION_CORE_MENU.get(), ProtectionCoreScreen::new);
        event.register(ModMenus.ADMIN_CORE_MENU.get(), AdminCoreScreen::new);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ProtectionClientTracker.onClientTick(Minecraft.getInstance());
    }
}
