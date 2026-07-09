package com.tumod.protectormod.network;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.InviteManager;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModNetworking {

    public static void register(IEventBus modBus) {
        modBus.addListener(ModNetworking::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // La version del registrar actua como comprobacion de compatibilidad de red entre
        // cliente y servidor (reemplaza al antiguo VersionCheck de Fabric). Por defecto los
        // payloads son "required": un cliente sin el mod no podra conectarse.
        PayloadRegistrar registrar = event.registrar("2");

        // ── C2S ──
        registrar.playToServer(ModPayloads.UpgradeCorePayload.TYPE, ModPayloads.UpgradeCorePayload.STREAM_CODEC, ModNetworking::handleUpgradeCore);
        registrar.playToServer(ModPayloads.ShowAreaPayload.TYPE, ModPayloads.ShowAreaPayload.STREAM_CODEC, ModNetworking::handleShowArea);
        registrar.playToServer(ModPayloads.ChangePermissionPayload.TYPE, ModPayloads.ChangePermissionPayload.STREAM_CODEC, ModNetworking::handleChangePermission);
        registrar.playToServer(ModPayloads.UpdateAdminCorePayload.TYPE, ModPayloads.UpdateAdminCorePayload.STREAM_CODEC, ModNetworking::handleUpdateAdminCore);
        registrar.playToServer(ModPayloads.CreateClanPayload.TYPE, ModPayloads.CreateClanPayload.STREAM_CODEC, ModNetworking::handleCreateClan);
        registrar.playToServer(ModPayloads.UpdateFlagPayload.TYPE, ModPayloads.UpdateFlagPayload.STREAM_CODEC, ModNetworking::handleUpdateFlag);
        registrar.playToServer(ModPayloads.OpenFlagsPayload.TYPE, ModPayloads.OpenFlagsPayload.STREAM_CODEC, ModNetworking::handleOpenFlags);
        registrar.playToServer(ModPayloads.DisbandClanPayload.TYPE, ModPayloads.DisbandClanPayload.STREAM_CODEC, ModNetworking::handleDisbandClan);
        registrar.playToServer(ModPayloads.OpenAlliancePayload.TYPE, ModPayloads.OpenAlliancePayload.STREAM_CODEC, ModNetworking::handleOpenAlliance);
        registrar.playToServer(ModPayloads.AllianceActionPayload.TYPE, ModPayloads.AllianceActionPayload.STREAM_CODEC, ModNetworking::handleAllianceAction);
        registrar.playToServer(ModPayloads.AllyPermPayload.TYPE, ModPayloads.AllyPermPayload.STREAM_CODEC, ModNetworking::handleAllyPerm);

        // ── S2C (se difieren a la clase cliente vía lambda para no cargarla en el servidor) ──
        registrar.playToClient(ModPayloads.ShowAreaClientPayload.TYPE, ModPayloads.ShowAreaClientPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.handleShowArea(payload)));
        registrar.playToClient(ModPayloads.SyncProtectionPayload.TYPE, ModPayloads.SyncProtectionPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.handleSyncProtection(payload)));
        registrar.playToClient(ModPayloads.AllianceDataPayload.TYPE, ModPayloads.AllianceDataPayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> ClientPayloadHandlers.handleAllianceData(payload)));
    }

    // ─────────────────────────── Handlers C2S ───────────────────────────

    private static void handleUpgradeCore(ModPayloads.UpgradeCorePayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ctx.enqueueWork(() -> {
            if (player.containerMenu instanceof ProtectionCoreMenu menu) {
                ProtectionCoreBlockEntity core = menu.getCore();
                if (core != null && !core.isRemoved()) {
                    boolean isOwner = player.getUUID().equals(core.getOwnerUUID());
                    boolean isOP = player.hasPermissions(2);

                    if (isOwner || isOP) {
                        core.upgrade(player);
                    } else {
                        player.displayClientMessage(Component.literal("§c[!] No eres el dueño de este núcleo."), true);
                    }
                }
            }
        });
    }

    private static void handleShowArea(ModPayloads.ShowAreaPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ctx.enqueueWork(() -> PacketDistributor.sendToPlayer(player, new ModPayloads.ShowAreaClientPayload(payload.pos(), payload.radius())));
    }

    private static void handleChangePermission(ModPayloads.ChangePermissionPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        BlockPos pos = payload.pos();
        String playerName = payload.playerName();
        String permissionType = payload.permissionType();
        boolean value = payload.value();

        ctx.enqueueWork(() -> {
            Level level = player.level();
            if (!(level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core)) return;

            java.util.UUID coreClanId = core.getClanId();

            // ── Núcleo de CLAN: solo el líder (u OP) gestiona; opera sobre el roster del clan ──
            if (coreClanId != null && level instanceof ServerLevel sLevel) {
                ClanSavedData data = ClanSavedData.get(sLevel);
                ClanSavedData.ClanInstance clan = data.getClanById(coreClanId);
                if (clan == null) return;

                if (!clan.isLeader(player.getUUID()) && !player.hasPermissions(2)) {
                    player.displayClientMessage(Component.literal("§c[!] Solo el líder del clan puede gestionar miembros."), true);
                    return;
                }

                java.util.UUID memberId = data.getMemberIdByName(clan, playerName);

                if (permissionType.equals("remove")) {
                    if (memberId == null) {
                        player.displayClientMessage(Component.literal("§c[!] Ese jugador no está en el clan."), true);
                        return;
                    }
                    if (clan.isLeader(memberId)) {
                        player.displayClientMessage(Component.literal("§c[!] No puedes expulsar al líder."), true);
                        return;
                    }
                    data.kickMember(clan, memberId);
                    core.removePlayerPermissions(playerName); // limpiar override residual en este núcleo
                    player.displayClientMessage(Component.literal("§c[!] §b" + playerName + "§f expulsado del clan."), true);
                    core.markDirtyAndUpdate();
                    return;
                }

                if (memberId != null) {
                    // Override de permisos de ESTE núcleo (no afecta a otros núcleos del clan).
                    core.setClanMemberPerm(memberId, playerName, permissionType, value);
                    player.displayClientMessage(Component.literal("§7[§6Clan§7] §fPermisos de §b" + playerName + "§f en este núcleo actualizados."), true);
                    return;
                }

                // No es miembro todavía → invitar al clan.
                if (clan.members.size() >= clan.maxMembers) {
                    player.displayClientMessage(Component.literal("§c[!] El clan alcanzó su límite de miembros (" + clan.maxMembers + ")."), true);
                    return;
                }
                ServerPlayer targetClan = level.getServer().getPlayerList().getPlayerByName(playerName);
                if (targetClan == null) {
                    player.displayClientMessage(Component.literal("§c[!] El jugador no está en línea."), true);
                    return;
                }
                sendInviteOnce(targetClan, player, pos);
                return;
            }

            // ── Núcleo SIN clan (clanless): permisos individuales del núcleo (clásico) ──
            if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {
                if (permissionType.equals("remove")) {
                    core.removePlayerPermissions(playerName);
                    player.displayClientMessage(Component.literal("§c[!] §fJugador §b" + playerName + "§f eliminado."), true);
                    core.markDirtyAndUpdate();
                    return;
                }

                if (core.getPlayersWithAnyPermission().contains(playerName)) {
                    core.updatePermission(playerName, permissionType, value);
                    player.displayClientMessage(Component.literal("§7[§6Protector§7] §fPermisos de §b" + playerName + "§f actualizados."), true);
                    core.markDirtyAndUpdate();
                    return;
                }

                if (core.getPlayersWithAnyPermission().size() >= 8) {
                    player.displayClientMessage(Component.literal("§c[!] El núcleo ya alcanzó el límite de 8 invitados."), true);
                    return;
                }

                ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(playerName);
                if (target == null) {
                    player.displayClientMessage(Component.literal("§c[!] El jugador no está en línea."), true);
                    return;
                }
                sendInviteOnce(target, player, pos);
            }
        });
    }

    /**
     * Envía la invitación pero SIN spamear: si ya hay una invitación pendiente de este jugador
     * para ESTE núcleo, solo refresca la expiración y no reenvía el mensaje al chat. Así pulsar
     * varias veces B/I/C (o el botón añadir) no llena el chat del objetivo de solicitudes.
     */
    private static void sendInviteOnce(ServerPlayer target, ServerPlayer from, BlockPos pos) {
        InviteManager.PendingInvite existing = InviteManager.getInvite(target.getUUID());
        boolean alreadyPendingHere = existing != null && existing.corePos().equals(pos);
        InviteManager.addInvite(target.getUUID(), pos, from.getUUID());
        if (alreadyPendingHere) {
            from.displayClientMessage(Component.literal("§7Invitación a §b" + target.getName().getString()
                    + "§7 ya pendiente (esperando respuesta)."), true);
        } else {
            sendInviteMessage(target, from);
        }
    }

    private static void sendInviteMessage(ServerPlayer target, ServerPlayer from) {
        Component msg = Component.literal("\n§6§l[Protector] §fInvitación de §b" + from.getName().getString() + "\n")
                .append(Component.literal("§a§l[ACEPTAR] ")
                        .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/protector accept"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Unirse al clan/núcleo")))))
                .append(Component.literal("  "))
                .append(Component.literal("§c§l[RECHAZAR]")
                        .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/protector deny"))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Ignorar invitación")))));
        target.sendSystemMessage(msg);
        target.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void handleUpdateAdminCore(ModPayloads.UpdateAdminCorePayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        BlockPos pos = payload.pos();
        int radius = payload.radius();
        boolean canBuild = payload.canBuild();
        boolean pvp = payload.pvp();
        boolean explosions = payload.explosions();

        ctx.enqueueWork(() -> {
            Level level = player.level();
            if (player.hasPermissions(2) && level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                core.setAdminRadius(radius);
                core.setFlag("break", canBuild);
                core.setFlag("build", canBuild);
                core.setFlag("interact", canBuild);
                core.setFlag("chests", canBuild);
                core.setFlag("pvp", pvp);
                core.setFlag("explosions", explosions);

                ProtectionDataManager.get(level).addOrUpdateCore(pos, core.getOwnerUUID(), radius);
                if (level instanceof ServerLevel sLevel) {
                    sLevel.getChunkSource().blockChanged(pos);
                    ProtectionDataManager.get(level).syncToAll(sLevel);
                }
                core.setChanged();
                level.sendBlockUpdated(pos, core.getBlockState(), core.getBlockState(), 3);
                player.displayClientMessage(Component.literal("§d[Admin]§a Flags de construcción y cofres actualizadas."), true);
            }
        });
    }

    private static void handleCreateClan(ModPayloads.CreateClanPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        BlockPos pos = payload.pos();
        String clanName = payload.clanName().trim();

        ctx.enqueueWork(() -> {
            ServerLevel serverLevel = player.serverLevel();
            if (serverLevel.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                ClanSavedData data = ClanSavedData.get(serverLevel);

                if (clanName.length() < 3 || clanName.length() > 16) {
                    player.displayClientMessage(Component.literal("§c[!] El nombre debe tener entre 3 y 16 caracteres."), true);
                    return;
                }

                if (data.getClanByLeader(player.getUUID()) != null) {
                    player.displayClientMessage(Component.literal("§c[!] Ya eres líder de un clan. Disuélvelo primero."), true);
                    return;
                }

                if (data.getClan(clanName) != null) {
                    player.displayClientMessage(Component.literal("§c[!] El nombre '" + clanName + "' ya está en uso."), true);
                    return;
                }

                boolean creado = data.tryCreateClan(clanName, player.getUUID(), player.getName().getString(), pos);

                if (creado) {
                    ClanSavedData.ClanInstance newClan = data.getClan(clanName);
                    core.setClan(clanName, newClan != null ? newClan.clanId : null);
                    core.setOwner(player.getUUID(), player.getName().getString());
                    // El núcleo donde se funda el clan pasa a ser una protección del clan.
                    if (newClan != null) {
                        newClan.protectionsUsed++;
                        data.setDirty();
                    }

                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§6§l[Clan] §f" + player.getName().getString() + " ha fundado: §b" + clanName),
                            false
                    );
                    core.markDirtyAndUpdate();
                }
            }
        });
    }

    private static void handleUpdateFlag(ModPayloads.UpdateFlagPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        BlockPos pos = payload.pos();
        String flag = payload.flag();

        ctx.enqueueWork(() -> {
            Level level = player.level();
            if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                if (core.canPlayerEditFlag(player, flag)) {
                    boolean newValue = !core.getFlag(flag);
                    core.setFlag(flag, newValue);
                    core.setChanged();
                    level.sendBlockUpdated(pos, core.getBlockState(), core.getBlockState(), 3);

                    String status = newValue ? "§aHABILITADO" : "§cDESHABILITADO";
                    player.displayClientMessage(Component.literal("§6[Core] §f" + flag + " §7➜ " + status), true);
                    player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
                } else {
                    player.displayClientMessage(Component.literal("§c[!] Nivel insuficiente o sin permisos para: " + flag), true);
                }
            }
        });
    }

    private static void handleOpenFlags(ModPayloads.OpenFlagsPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        BlockPos pos = payload.pos();
        ctx.enqueueWork(() -> {
            Level level = player.level();
            if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {
                    player.openMenu(core, core.getBlockPos());
                }
            }
        });
    }

    private static void handleDisbandClan(ModPayloads.DisbandClanPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ctx.enqueueWork(() -> {
            ServerLevel level = player.serverLevel();
            ClanSavedData data = ClanSavedData.get(level);
            ClanSavedData.ClanInstance clan = data.getClanByLeader(player.getUUID());

            if (clan == null) {
                if (data.getClanByMember(player.getUUID()) != null) {
                    player.displayClientMessage(Component.literal("§c[!] Solo el líder puede disolver el clan."), true);
                } else {
                    player.displayClientMessage(Component.literal("§c[!] No tienes un clan que disolver."), true);
                }
                return;
            }

            String name = clan.name;
            data.deleteClan(player.getUUID());
            player.displayClientMessage(Component.literal("§eClan §b" + name + " §edisuelto correctamente."), true);
        });
    }

    // ─────────────────────────── Handlers de Alianzas ───────────────────────────

    private static void handleOpenAlliance(ModPayloads.OpenAlliancePayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ctx.enqueueWork(() -> sendAllianceData(player, payload.pos()));
    }

    private static void handleAllianceAction(ModPayloads.AllianceActionPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ctx.enqueueWork(() -> {
            ServerLevel level = player.serverLevel();
            ClanSavedData data = ClanSavedData.get(level);
            ClanSavedData.ClanInstance myClan = data.getClanByMember(player.getUUID());
            if (myClan == null) {
                player.displayClientMessage(Component.literal("§c[!] No perteneces a ningún clan."), true);
                return;
            }
            if (!myClan.isLeader(player.getUUID())) {
                player.displayClientMessage(Component.literal("§c[!] Solo el líder gestiona las alianzas."), true);
                return;
            }
            ClanSavedData.ClanInstance target = data.getClanById(payload.targetClanId());
            String err = null;
            switch (payload.action()) {
                case "propose" -> {
                    if (target == null) { err = "Ese clan ya no existe."; break; }
                    err = data.proposeAlliance(myClan, target);
                    if (err == null) player.displayClientMessage(Component.literal("§a[Alianza] Solicitud enviada a §b" + target.name), true);
                }
                case "accept" -> {
                    err = data.acceptAlliance(myClan, payload.targetClanId());
                    if (err == null) player.displayClientMessage(Component.literal("§a[Alianza] Ahora eres aliado de §b" + (target != null ? target.name : "?")), true);
                }
                case "reject" -> data.rejectAlliance(myClan, payload.targetClanId());
                case "cancel" -> data.cancelRequest(myClan, payload.targetClanId());
                case "break" -> {
                    data.breakAlliance(myClan, payload.targetClanId());
                    player.displayClientMessage(Component.literal("§e[Alianza] Alianza rota con §b" + (target != null ? target.name : "?")), true);
                }
            }
            if (err != null) player.displayClientMessage(Component.literal("§c[!] " + err), true);
            sendAllianceData(player, payload.pos());
        });
    }

    private static void handleAllyPerm(ModPayloads.AllyPermPayload payload, IPayloadContext ctx) {
        ServerPlayer player = (ServerPlayer) ctx.player();
        ctx.enqueueWork(() -> {
            ServerLevel level = player.serverLevel();
            ClanSavedData data = ClanSavedData.get(level);
            ClanSavedData.ClanInstance myClan = data.getClanByMember(player.getUUID());
            if (myClan == null || !myClan.isLeader(player.getUUID())) {
                player.displayClientMessage(Component.literal("§c[!] Solo el líder gestiona los permisos de aliados."), true);
                return;
            }
            data.setAllyPerm(myClan, payload.allyClanId(), payload.permType(), payload.value());
            sendAllianceData(player, payload.pos());
        });
    }

    /** Construye y envía al jugador el estado de alianzas de SU clan (basado en su membresía). */
    private static void sendAllianceData(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        ClanSavedData data = ClanSavedData.get(level);
        ClanSavedData.ClanInstance myClan = data.getClanByMember(player.getUUID());

        if (myClan == null) {
            PacketDistributor.sendToPlayer(player, new ModPayloads.AllianceDataPayload(
                    false, false, "", com.tumod.protectormod.config.ProtectorConfig.MAX_ALLIANCES.get(),
                    List.of(), List.of(), List.of(), List.of()));
            return;
        }

        boolean isLeader = myClan.isLeader(player.getUUID());

        List<ModPayloads.AllyEntry> allies = new ArrayList<>();
        myClan.allies.forEach((id, ap) -> {
            ClanSavedData.ClanInstance ally = data.getClanById(id);
            if (ally != null) allies.add(new ModPayloads.AllyEntry(id, ally.name, ap.build, ap.interact, ap.chests));
        });

        List<ModPayloads.ClanRef> incoming = new ArrayList<>();
        for (java.util.UUID id : myClan.incomingRequests) {
            ClanSavedData.ClanInstance c = data.getClanById(id);
            if (c != null) incoming.add(new ModPayloads.ClanRef(id, c.name));
        }

        List<ModPayloads.ClanRef> outgoing = new ArrayList<>();
        for (java.util.UUID id : myClan.outgoingRequests) {
            ClanSavedData.ClanInstance c = data.getClanById(id);
            if (c != null) outgoing.add(new ModPayloads.ClanRef(id, c.name));
        }

        // Candidatos: clanes distintos al mío, no aliados aún, sin solicitud pendiente en ninguna dirección.
        List<ModPayloads.ClanRef> candidates = new ArrayList<>();
        for (ClanSavedData.ClanInstance c : data.getAllClans()) {
            if (c.clanId.equals(myClan.clanId)) continue;
            if (myClan.allies.containsKey(c.clanId)) continue;
            if (myClan.outgoingRequests.contains(c.clanId)) continue;
            if (myClan.incomingRequests.contains(c.clanId)) continue;
            candidates.add(new ModPayloads.ClanRef(c.clanId, c.name));
        }

        PacketDistributor.sendToPlayer(player, new ModPayloads.AllianceDataPayload(
                isLeader, true, myClan.name,
                com.tumod.protectormod.config.ProtectorConfig.MAX_ALLIANCES.get(),
                allies, incoming, outgoing, candidates));
    }

    // ─────────────────────────── Senders S2C ───────────────────────────

    public static void sendSyncProtection(ServerPlayer player, Map<BlockPos, ProtectionDataManager.CoreEntry> cores) {
        List<ModPayloads.CoreData> list = new ArrayList<>(cores.size());
        cores.forEach((pos, entry) -> list.add(new ModPayloads.CoreData(pos, entry.owner(), entry.radius())));
        PacketDistributor.sendToPlayer(player, new ModPayloads.SyncProtectionPayload(list));
    }

    public static void sendShowAreaToClient(ServerPlayer player, BlockPos pos, int radius) {
        PacketDistributor.sendToPlayer(player, new ModPayloads.ShowAreaClientPayload(pos, radius));
    }

    // ─────────────────────────── Senders C2S (llamados desde el cliente) ───────────────────────────

    public static void sendUpgradeCore(BlockPos pos) {
        PacketDistributor.sendToServer(new ModPayloads.UpgradeCorePayload(pos));
    }

    public static void sendShowArea(BlockPos pos, int radius) {
        PacketDistributor.sendToServer(new ModPayloads.ShowAreaPayload(pos, radius));
    }

    public static void sendChangePermission(BlockPos pos, String playerName, String type, boolean value) {
        PacketDistributor.sendToServer(new ModPayloads.ChangePermissionPayload(pos, playerName, type, value));
    }

    public static void sendUpdateAdminCore(BlockPos pos, int radius, boolean canBuild, boolean pvp, boolean explosions) {
        PacketDistributor.sendToServer(new ModPayloads.UpdateAdminCorePayload(pos, radius, canBuild, pvp, explosions));
    }

    public static void sendCreateClan(BlockPos pos, String name) {
        PacketDistributor.sendToServer(new ModPayloads.CreateClanPayload(pos, name));
    }

    public static void sendUpdateFlag(BlockPos pos, String flag) {
        PacketDistributor.sendToServer(new ModPayloads.UpdateFlagPayload(pos, flag));
    }

    public static void sendOpenFlags(BlockPos pos) {
        PacketDistributor.sendToServer(new ModPayloads.OpenFlagsPayload(pos));
    }

    public static void sendDisbandClan(BlockPos pos) {
        PacketDistributor.sendToServer(new ModPayloads.DisbandClanPayload(pos));
    }

    public static void sendOpenAlliance(BlockPos pos) {
        PacketDistributor.sendToServer(new ModPayloads.OpenAlliancePayload(pos));
    }

    public static void sendAllianceAction(BlockPos pos, String action, java.util.UUID targetClanId) {
        PacketDistributor.sendToServer(new ModPayloads.AllianceActionPayload(pos, action, targetClanId));
    }

    public static void sendAllyPerm(BlockPos pos, java.util.UUID allyClanId, String type, boolean value) {
        PacketDistributor.sendToServer(new ModPayloads.AllyPermPayload(pos, allyClanId, type, value));
    }
}
