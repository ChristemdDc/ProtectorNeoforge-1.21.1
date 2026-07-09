package com.tumod.protectormod.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Plugin de mixins de ProtectorMod. Todos los mixins se aplican sin condición.
 *
 * <p>NOTA: {@code MixinAssemblePacket} se aplica SIEMPRE (como hace AeroClaims). No se puede gatear
 * con {@code ModList.isLoaded} porque {@code shouldApplyMixin} para el AssemblePacket se evalúa
 * demasiado pronto (antes de que la lista de mods esté completa) → se omitía el mixin y el guard
 * de (des)ensamblado nunca se aplicaba. El pack objetivo siempre incluye Create Aeronautics.
 */
public class ProtectorMixinPlugin implements IMixinConfigPlugin {

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
