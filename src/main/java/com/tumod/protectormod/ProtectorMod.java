package com.tumod.protectormod;

import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.registry.ModCreativeTabs;
import com.tumod.protectormod.registry.ModItems;
import com.tumod.protectormod.registry.ModMenus;
import com.tumod.protectormod.registry.ModSounds;
import com.tumod.protectormod.network.ModNetworking;
import com.tumod.protectormod.config.ProtectorConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ProtectorMod.MOD_ID)
public class ProtectorMod {

    public static final String MOD_ID = "protectormod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // El constructor recibe el mod event bus y el contenedor via inyeccion de NeoForge.
    public ProtectorMod(IEventBus modBus, ModContainer modContainer) {
        // ── Registros (DeferredRegister) al MOD event bus ──
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);
        ModSounds.register(modBus);

        modBus.addListener(this::commonSetup);

        // Networking: payloads C2S/S2C en RegisterPayloadHandlersEvent (mod bus).
        // Nota: la negociacion de canales de NeoForge exige el mod en el cliente,
        // reemplazando al antiguo VersionCheck de Fabric.
        ModNetworking.register(modBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, ProtectorConfig.SPEC);

        // AnimaSlime: enlaces de modelo que DEBEN hacerse en el constructor (el del item; el del
        // bloque va en FMLClientSetupEvent). El guarda por Dist evita cargar clases de cliente en un
        // servidor dedicado: la clase solo se resuelve al entrar en la rama.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            com.tumod.protectormod.client.ProtectorAnimaSlimeModels.bindEarly();
        }

        //   modContainer.registerConfig(ModConfig.Type.COMMON, ProtectorConfig.SPEC);

        // Los eventos de juego (F1.3 ModEvents) y comandos usan NeoForge.EVENT_BUS via
        // @EventBusSubscriber, por lo que no necesitan registro manual aqui.

        LOGGER.info("[ProtectorMod] Inicializado (NeoForge 1.21.1).");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // TODO(F1.7): InteractWhitelistConfig.load();
        // TODO(F3): si aeroclaims esta cargado, registrar el resolver de clanes:
        //   if (ModList.get().isLoaded("aeroclaims")) ProtectorClanBridge.registerWithAeroClaims();
    }
}
