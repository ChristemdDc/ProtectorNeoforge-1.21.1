package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ProtectorMod.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CORE_UPGRADE =
            SOUNDS.register("core_upgrade",
                    () -> SoundEvent.createVariableRangeEvent(
                            ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "core_upgrade")));

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
