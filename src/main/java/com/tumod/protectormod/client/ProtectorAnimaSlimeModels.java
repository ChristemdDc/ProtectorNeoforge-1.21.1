package com.tumod.protectormod.client;

import com.cretania.animaslime.engine.block.AnimaSlimeBlockModel;
import com.cretania.animaslime.engine.block.AnimaSlimeBlockRenderers;
import com.cretania.animaslime.engine.item.AnimaSlimeItemModels;
import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.registry.ModItems;

/**
 * Enlace de los modelos {@code .animaslime} de ProtectorMod con el motor AnimaSlime.
 *
 * <p>SOLO CLIENTE: la animación es 100% de cliente y estas clases del motor tocan API de render, así
 * que en un servidor dedicado esta clase no se carga nunca (ver el guarda por {@code Dist} en el
 * constructor de {@link ProtectorMod}).
 *
 * <p><b>Todo se enlaza en el CONSTRUCTOR del mod, con las variantes {@code bindDeferred}.</b> Es
 * obligatorio, no una preferencia de estilo: el {@code BlockEntityRenderer} solo puede registrarse
 * durante {@code EntityRenderersEvent.RegisterRenderers}, que ocurre UNA vez, y el motor engancha ese
 * evento en SU propio bus — como ProtectorMod carga después de AnimaSlime
 * ({@code ordering = "AFTER"}), el motor lo procesa antes. Enlazar más tarde (por ejemplo en
 * {@code FMLClientSetupEvent}) llega tarde y el bloque se ve INVISIBLE. {@code bindDeferred} acepta el
 * holder del {@code DeferredRegister} sin resolver, que es lo que permite llamarlo en el constructor
 * cuando los registros aún están vacíos.
 */
public final class ProtectorAnimaSlimeModels {

    private ProtectorAnimaSlimeModels() {}

    /**
     * Se llama desde el constructor de {@link ProtectorMod}, tras registrar los {@code DeferredRegister}
     * en el bus y bajo un guarda por {@code Dist} para no cargar clases de cliente en un servidor.
     */
    public static void bindEarly() {
        // ── Protector Mecánico (MECHANICAL_PROTECTOR_BE) ──
        // Es una proteccion EXTRA: solo este bloque usa el render animado. El nucleo clasico
        // (PROTECTION_CORE_BE) y el admin (ADMIN_PROTECTOR_BE) NO se enlazan → conservan su modelo JSON.
        // Enlace POR ESTADO aunque hoy los 5 niveles devuelvan el MISMO modelo: cuando se exporten los
        // otros modelos (hierro/acero/nuclear/celestial) basta mapear el LEVEL a cada uno aquí.
        AnimaSlimeBlockModel modelo = AnimaSlimeBlockModel.builder(ModBlocks.PROTE_MECANICO_MODEL)
                // Sin idleClip: el motor usa el PRIMER clip del archivo (el modelo trae uno solo).
                .loop(true)
                .build();
        AnimaSlimeBlockRenderers.bindDeferred(
                ModBlockEntities.MECHANICAL_PROTECTOR_BE,
                state -> modelo); // todos los niveles -> mismo modelo por ahora

        // Icono del ítem = el mismo modelo animado, para que el inventario coincida con el bloque.
        AnimaSlimeItemModels.bindDeferred(ModItems.MECHANICAL_PROTECTOR_ITEM, ModBlocks.PROTE_MECANICO_MODEL);
    }
}
