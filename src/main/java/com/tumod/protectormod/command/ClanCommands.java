package com.tumod.protectormod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.event.ModEvents;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.InviteManager;
import com.tumod.protectormod.util.PlayerConfigData;
import com.tumod.protectormod.util.ProtectedBlockMarker;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ClanCommands {
    public static int maxCoresPerPlayer = 3;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("protector")
                .then(Commands.literal("testmode")
                        .requires(s -> s.hasPermission(4))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PlayerConfigData data = PlayerConfigData.get(player.serverLevel());

                            if (data.isTestMode(player.getUUID())) {
                                data.setTestMode(player.getUUID(), false);
                                context.getSource().sendSuccess(() -> Component.literal("§cModo Prueba desactivado. Ahora tienes tus permisos de OP normales."), true);
                            } else {
                                data.setTestMode(player.getUUID(), true);
                                context.getSource().sendSuccess(() -> Component.literal("§aModo Prueba activado. La protección te tratará como un jugador normal."), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("presentation")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .executes(context -> {
                                    Player player = context.getSource().getPlayerOrException();
                                    String state = StringArgumentType.getString(context, "state");
                                    boolean isOn = state.equalsIgnoreCase("on");
                                    if (isOn) player.addTag("ProtectorPresentation");
                                    else player.removeTag("ProtectorPresentation");

                                    context.getSource().sendSuccess(() -> Component.literal(
                                            isOn ? "§aPresentación de zonas activada." : "§cPresentación de zonas desactivada."), true);
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(4))
                                .then(Commands.literal("trust")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    ServerPlayer admin = context.getSource().getPlayerOrException();
                                                    return executeAdminCommand(admin, target, true);
                                                })
                                        )
                                )
                                .then(Commands.literal("untrust")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(context -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                                    ServerPlayer admin = context.getSource().getPlayerOrException();
                                                    return executeAdminCommand(admin, target, false);
                                                })
                                        )
                                )
                )
                .then(Commands.literal("help").executes(context -> showProtectorHelp(context.getSource())))
                .then(Commands.literal("visualizer")
                        .requires(s -> s.hasPermission(4))
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PlayerConfigData data = PlayerConfigData.get(player.serverLevel());

                            if (data.isVisualizerEnabled(player.getUUID())) {
                                data.setVisualizer(player.getUUID(), false);
                                context.getSource().sendSuccess(() -> Component.literal("§cVisualizador desactivado."), true);
                            } else {
                                data.setVisualizer(player.getUUID(), true);
                                context.getSource().sendSuccess(() -> Component.literal("§aVisualizador activado."), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("check")
                        .executes(context -> executeCheckBlock(context.getSource(), null))
                )
                .then(Commands.literal("limit")
                        .requires(s -> s.hasPermission(4))
                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100))
                                .executes(c -> {
                                    int nuevaCant = IntegerArgumentType.getInteger(c, "cantidad");
                                    ServerLevel level = c.getSource().getLevel();
                                    ProtectionDataManager manager = ProtectionDataManager.get(level);
                                    manager.setGlobalLimit(nuevaCant);
                                    c.getSource().sendSuccess(() -> Component.literal("§6[Protector] §aLímite global actualizado a: §f" + nuevaCant), true);
                                    Component notification = Component.literal("§7[Staff] " + c.getSource().getTextName() + " cambió el límite de núcleos a: " + nuevaCant);
                                    level.getServer().getPlayerList().getPlayers().forEach(player -> {
                                        if (player.hasPermissions(4) && player != c.getSource().getEntity()) {
                                            player.displayClientMessage(notification, false);
                                        }
                                    });
                                    return 1;
                                }))
                )
                .then(Commands.literal("list")
                        .requires(s -> s.hasPermission(4))
                        .executes(context -> executeList(context.getSource())))
                .then(Commands.literal("accept").executes(context -> executeAccept(context.getSource())))
                .then(Commands.literal("deny").executes(context -> executeDeny(context.getSource())))
        );

        dispatcher.register(Commands.literal("clan")
                .executes(context -> showClanHelp(context.getSource()))
                .then(Commands.literal("help").executes(context -> showClanHelp(context.getSource())))
                .then(Commands.literal("limit")
                        .requires(s -> s.hasPermission(4))
                        .then(Commands.argument("nombreClan", StringArgumentType.string())
                                .then(Commands.argument("nuevoLimite", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> {
                                            String clanName = StringArgumentType.getString(context, "nombreClan");
                                            int limite = IntegerArgumentType.getInteger(context, "nuevoLimite");
                                            ClanSavedData data = ClanSavedData.get(context.getSource().getLevel());
                                            ClanSavedData.ClanInstance clan = data.getClan(clanName);
                                            if (clan == null) {
                                                context.getSource().sendFailure(Component.literal("§cEl clan '" + clanName + "' no existe."));
                                                return 0;
                                            }
                                            clan.maxMembers = limite;
                                            data.setDirty();
                                            context.getSource().sendSuccess(() -> Component.literal("§aLímite del clan §b" + clanName + " §aactualizado a §f" + limite), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("protections")
                        .requires(s -> s.hasPermission(4))
                        .then(Commands.argument("nombreClan", StringArgumentType.string())
                                .then(Commands.argument("cantidad", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                                        .executes(context -> {
                                            String clanName = StringArgumentType.getString(context, "nombreClan");
                                            int cant = IntegerArgumentType.getInteger(context, "cantidad");
                                            ClanSavedData data = ClanSavedData.get(context.getSource().getLevel());
                                            ClanSavedData.ClanInstance clan = data.getClan(clanName);
                                            if (clan == null) {
                                                context.getSource().sendFailure(Component.literal("§cEl clan '" + clanName + "' no existe."));
                                                return 0;
                                            }
                                            clan.maxProtections = cant;
                                            data.setDirty();
                                            context.getSource().sendSuccess(() -> Component.literal("§aPool de protecciones del clan §b" + clanName + " §aajustado a §f" + cant + " §7(usadas: " + clan.protectionsUsed + ")"), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("delete").executes(context -> executeDelete(context.getSource())))
                .then(Commands.literal("info")
                        .executes(context -> showClanInfo(context.getSource(), null))
                        .then(Commands.argument("nombreClan", StringArgumentType.greedyString())
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> showClanInfo(context.getSource(), StringArgumentType.getString(context, "nombreClan")))))
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(4))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("nombreClan", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String target = StringArgumentType.getString(context, "nombreClan");
                                            ClanSavedData data = ClanSavedData.get(context.getSource().getLevel());
                                            ClanSavedData.ClanInstance clan = data.getClan(target);
                                            if (clan == null) {
                                                context.getSource().sendFailure(Component.literal("§cEl clan '" + target + "' no existe."));
                                                return 0;
                                            }
                                            data.deleteClan(clan.leaderUUID);
                                            context.getSource().sendSuccess(() -> Component.literal("§6[Admin] §eEl clan §b" + target + " §eha sido borrado."), true);
                                            return 1;
                                        }))))
        );
    }

    private static int showClanInfo(CommandSourceStack source, @Nullable String targetClanName) {
        ClanSavedData data = ClanSavedData.get(source.getLevel());
        ClanSavedData.ClanInstance clan;

        if (targetClanName == null) {
            if (source.getEntity() instanceof Player player) {
                clan = data.getClanByMember(player.getUUID());
                if (clan == null) {
                    source.sendFailure(Component.literal("§cNo tienes clan."));
                    return 0;
                }
            } else return 0;
        } else {
            clan = data.getClan(targetClanName);
        }

        if (clan == null) {
            source.sendFailure(Component.literal("§cClan no encontrado."));
            return 0;
        }

        final ClanSavedData.ClanInstance fClan = clan;
        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        source.sendSuccess(() -> Component.literal("§6§lINFO: §b" + fClan.name), false);
        source.sendSuccess(() -> Component.literal("§eLíder: §f" + fClan.leaderName), false);
        source.sendSuccess(() -> Component.literal("§eMiembros: §a" + fClan.members.size() + "/" + fClan.maxMembers), false);
        source.sendSuccess(() -> Component.literal("§eProtecciones: §a" + fClan.protectionsUsed + "§7/§a" + fClan.maxProtections), false);
        source.sendSuccess(() -> Component.literal("§eUbicación Core: §7(" + fClan.corePos.getX() + ", " + fClan.corePos.getY() + ", " + fClan.corePos.getZ() + ")"), false);
        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        return 1;
    }

    private static int executeDelete(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ClanSavedData data = ClanSavedData.get(player.serverLevel());
        ClanSavedData.ClanInstance clan = data.getClanByLeader(player.getUUID());

        if (clan == null) {
            if (data.getClanByMember(player.getUUID()) != null) {
                source.sendFailure(Component.literal("§cSolo el líder puede disolver el clan."));
            } else {
                source.sendFailure(Component.literal("§cAun no tienes un clan que disolver."));
            }
            return 0;
        }

        data.deleteClan(player.getUUID());
        source.sendSuccess(() -> Component.literal("§eClan §b" + clan.name + " §edisuelto correctamente."), true);
        return 1;
    }

    private static int executeAdminCommand(ServerPlayer admin, ServerPlayer target, boolean isTrust) {
        BlockHitResult hit = admin.level().clip(new ClipContext(
                admin.getEyePosition(),
                admin.getEyePosition().add(admin.getViewVector(1.0F).scale(6.0D)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                admin
        ));

        BlockPos hitPos = hit.getBlockPos();
        ServerLevel level = admin.serverLevel();

        ProtectionCoreBlockEntity core = ModEvents.findCoreAt(level, hitPos);

        if (core == null) {
            core = ModEvents.findCoreAt(level, admin.blockPosition());
        }

        if (core != null && core.isAdmin()) {
            if (isTrust) {
                core.updatePermission(target.getUUID(), target.getName().getString(), "build", true);
                admin.sendSystemMessage(Component.literal("§d[Admin] §b" + target.getName().getString() + " §aahora es de confianza en esta zona."));
            } else {
                core.removePlayerPermissions(target.getName().getString());
                admin.sendSystemMessage(Component.literal("§d[Admin] §b" + target.getName().getString() + " §cremovido de la zona."));
            }

            core.markDirtyAndUpdate();
            return 1;
        }

        admin.sendSystemMessage(Component.literal("§c[!] Debes mirar un núcleo de ADMINISTRACIÓN o estar dentro de su zona."));
        return 0;
    }

    public static int executeAccept(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        InviteManager.PendingInvite invite = InviteManager.getInvite(player.getUUID());

        if (invite != null) {
            ServerLevel level = player.serverLevel();
            ClanSavedData data = ClanSavedData.get(level);
            ClanSavedData.ClanInstance clan = data.getClanByLeader(invite.requesterUUID());
            if (clan != null) {
                // Un jugador solo puede estar en un clan a la vez.
                ClanSavedData.ClanInstance existing = data.getClanByMember(player.getUUID());
                if (existing != null && existing != clan) {
                    player.sendSystemMessage(Component.literal("§c[!] Ya perteneces al clan §b" + existing.name + "§c. Sal de él antes de unirte a otro."));
                    InviteManager.removeInvite(player.getUUID());
                    return 0;
                }
                if (clan.members.size() < clan.maxMembers) {
                    data.addMemberToClan(clan, player.getUUID(), player.getName().getString());
                    player.sendSystemMessage(Component.literal("§a✔ Te has unido al clan: §b" + clan.name));
                }
            }

            if (level.getBlockEntity(invite.corePos()) instanceof ProtectionCoreBlockEntity core) {
                // En núcleos de clan el acceso viene por membresía; solo damos permiso
                // individual si el núcleo NO es de un clan (invitación clásica).
                if (core.getClanId() == null) {
                    core.updatePermission(player.getUUID(), player.getName().getString(), "build", true);
                    core.updatePermission(player.getUUID(), player.getName().getString(), "chests", true);
                }
                core.markDirtyAndUpdate();

                player.sendSystemMessage(Component.literal("§a✔ Ahora tienes acceso a §b" + core.getOwnerName()));
                InviteManager.removeInvite(player.getUUID());
                return 1;
            } else {
                player.sendSystemMessage(Component.literal("§c[!] El núcleo ya no existe o no se pudo encontrar."));
                InviteManager.removeInvite(player.getUUID());
                return 0;
            }
        }

        player.sendSystemMessage(Component.literal("§c[!] No tienes invitaciones pendientes."));
        return 0;
    }

    private static int showProtectorHelp(CommandSourceStack source) {
        boolean isAdmin = source.hasPermission(4);
        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        source.sendSuccess(() -> Component.literal("§6§lSISTEMA DE PROTECCIÓN - AYUDA"), false);
        source.sendSuccess(() -> Component.literal(" "), false);
        source.sendSuccess(() -> Component.literal("§b/protector help §7- Muestra este menú."), false);
        source.sendSuccess(() -> Component.literal("§b/protector presentation <on/off> §7- Mensajes de entrada."), false);
        source.sendSuccess(() -> Component.literal("§b/protector accept §7- Aceptar invitación."), false);
        source.sendSuccess(() -> Component.literal("§b/protector deny §7- Rechazar invitación."), false);

        if (isAdmin) {
            source.sendSuccess(() -> Component.literal(" "), false);
            source.sendSuccess(() -> Component.literal("§d§lMODO ADMINISTRADOR:"), false);
            source.sendSuccess(() -> Component.literal("§b/protector debug §7- Info técnica del core."), false);
            source.sendSuccess(() -> Component.literal("§b/protector visualizer §7- Bordes de partículas."), false);
            source.sendSuccess(() -> Component.literal("§b/protector admin trust/untrust §7- Permitir / Remover de un admin core"), false);
            source.sendSuccess(() -> Component.literal("§b/protector list §7- Lista todos los núcleos."), false);
            source.sendSuccess(() -> Component.literal("§b/protector limit <n> §7- Límite global de cores."), false);
        }

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        return 1;
    }

    private static int showClanHelp(CommandSourceStack source) {
        boolean isAdmin = source.hasPermission(4);
        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        source.sendSuccess(() -> Component.literal("§6§lSISTEMA DE CLANES - AYUDA"), false);
        source.sendSuccess(() -> Component.literal(" "), false);
        source.sendSuccess(() -> Component.literal("§b/clan info §7- Ver info de tu clan y miembros."), false);
        source.sendSuccess(() -> Component.literal("§b/clan delete §7- Disuelve tu clan (Solo líder)."), false);

        if (isAdmin) {
            source.sendSuccess(() -> Component.literal(" "), false);
            source.sendSuccess(() -> Component.literal("§d§lMODO ADMINISTRADOR:"), false);
            source.sendSuccess(() -> Component.literal("§b/clan info <nombre> §7- Ver info de cualquier clan."), false);
            source.sendSuccess(() -> Component.literal("§b/clan limit <clan> <n> §7- Cambiar límite de miembros."), false);
            source.sendSuccess(() -> Component.literal("§b/clan admin delete <nombre> §7- Borrar un clan ajeno."), false);
        }

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        return 1;
    }

    private static int executeList(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ProtectionDataManager manager = ProtectionDataManager.get(level);
        ClanSavedData clanData = ClanSavedData.get(level);
        var cores = manager.getAllCores();

        if (cores.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§cNo hay núcleos activos."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6§lAUDITORÍA DE NÚCLEOS:"), false);

        for (var entry : cores.entrySet()) {
            BlockPos pos = entry.getKey();
            UUID ownerUUID = entry.getValue().owner();
            String ownerName = "Desconocido";
            if (ownerUUID != null) {
                var player = level.getServer().getPlayerList().getPlayer(ownerUUID);
                if (player != null) {
                    ownerName = player.getName().getString();
                } else {
                    ownerName = ownerUUID.toString().substring(0, 8) + "...";
                }
            } else {
                ownerName = "§dADMIN";
            }

            String clanSuffix = "";
            var clan = clanData.getClanByMember(ownerUUID);
            if (clan != null) {
                clanSuffix = " §8[§b" + clan.name + "§8]";
            }

            String finalName = ownerName;
            String finalClan = clanSuffix;
            source.sendSuccess(() -> Component.literal("§e- §fPos: §b" + pos.toShortString() +
                    " §8| §eOwner: §7" + finalName + finalClan), false);
        }
        return cores.size();
    }

    private static int executeCheckBlock(CommandSourceStack source, @Nullable BlockPos targetPos) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();

        BlockPos checkPos;

        if (targetPos != null) {
            checkPos = targetPos;
        } else {
            Vec3 eyePos = player.getEyePosition();
            Vec3 lookVec = player.getViewVector(1.0F).scale(6.0D);
            Vec3 endPos = eyePos.add(lookVec);

            ClipContext clipContext = new ClipContext(
                    eyePos, endPos,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
            );

            BlockHitResult hitResult = level.clip(clipContext);
            checkPos = hitResult.getBlockPos();
        }

        BlockState blockState = level.getBlockState(checkPos);
        Block block = blockState.getBlock();
        String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
        boolean isSpecial = ProtectedBlockMarker.isSpecialBlock(block);
        boolean isRedstone = ProtectedBlockMarker.isRedstoneBlock(block);
        boolean isCreate = ProtectedBlockMarker.isCreateBlock(block);

        ProtectionCoreBlockEntity core = ModEvents.findCoreAt(level, checkPos);

        ProtectedBlockMarker.ProtectionData markData = ProtectedBlockMarker.getProtectionData(checkPos);

        source.sendSuccess(() -> Component.literal("§8§m═══════════════════════════════════"), false);
        source.sendSuccess(() -> Component.literal("§6DIAGNÓSTICO DE BLOQUE: §b" + checkPos.toShortString()), false);
        source.sendSuccess(() -> Component.literal("§8§m═══════════════════════════════════"), false);

        source.sendSuccess(() -> Component.literal("§eBloque: §f" + blockId), false);
        source.sendSuccess(() -> Component.literal("§eClase: §7" + block.getClass().getSimpleName()), false);

        if (isSpecial) {
            String tipo = isCreate ? "§dCreate" : isRedstone ? "§cRedstone" : "§eEspecial";
            source.sendSuccess(() -> Component.literal("§aTipo: " + tipo + " §a(Máquina/Mecanismo)"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7Tipo: Bloque normal"), false);
        }

        source.sendSuccess(() -> Component.literal("§8§m---"), false);
        if (markData != null) {
            source.sendSuccess(() -> Component.literal("§a✓ MARCADO §f(colocado por miembro confiable)"), false);
            source.sendSuccess(() -> Component.literal("§e  Propietario: §f" + markData.owner), false);
            source.sendSuccess(() -> Component.literal("§e  Core asociado: §b" + markData.corePos.toShortString()), false);
            source.sendSuccess(() -> Component.literal("§e  Marcado: §7" + new java.util.Date(markData.timestamp)), false);
            source.sendSuccess(() -> Component.literal("§a  → Bypass de banderas ACTIVO"), false);
        } else {
            source.sendSuccess(() -> Component.literal("§c✗ NO MARCADO"), false);
            source.sendSuccess(() -> Component.literal("§7  Sujeto a todas las banderas de protección"), false);
            if (isSpecial) {
                source.sendSuccess(() -> Component.literal("§e  → Rompe y recoloca para marcar"), false);
            }
        }

        source.sendSuccess(() -> Component.literal("§8§m---"), false);
        if (core != null) {
            String ownerName = core.isAdmin() ? "§d§lAdministración" : "§b" + core.getOwnerName();
            source.sendSuccess(() -> Component.literal("§a✓ §fDentro de zona protegida"), false);
            source.sendSuccess(() -> Component.literal("§e  Zona de: " + ownerName), false);
            source.sendSuccess(() -> Component.literal("§e  Core en: §b" + core.getBlockPos().toShortString()), false);
            source.sendSuccess(() -> Component.literal("§e  Banderas: build=" + (core.getFlag("build") ? "§aON" : "§cOFF") +
                "§7, use-buckets=" + (core.getFlag("use-buckets") ? "§aON" : "§cOFF") +
                "§7, interact=" + (core.getFlag("interact") ? "§aON" : "§cOFF")), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7✗ Fuera de cualquier zona protegida"), false);
        }

        source.sendSuccess(() -> Component.literal("§8§m---"), false);
        source.sendSuccess(() -> Component.literal("§7Cache global: §f" + ProtectedBlockMarker.getCacheSize() + " bloques marcados"), false);
        source.sendSuccess(() -> Component.literal("§8§m═══════════════════════════════════"), false);

        return 1;
    }

    public static int executeDeny(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (InviteManager.getInvite(player.getUUID()) != null) {
            InviteManager.removeInvite(player.getUUID());
            player.sendSystemMessage(Component.literal("§eInvitación rechazada."));
            return 1;
        }
        return 0;
    }
}
