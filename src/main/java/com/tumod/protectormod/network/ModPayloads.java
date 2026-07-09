package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Todos los payloads (C2S y S2C) del mod, portados al sistema de red de NeoForge 1.21.
 */
public final class ModPayloads {

    private ModPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> makeType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, path));
    }

    // ─────────────────────────── C2S ───────────────────────────

    public record UpgradeCorePayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<UpgradeCorePayload> TYPE = makeType("upgrade_core");
        public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeCorePayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, UpgradeCorePayload::pos, UpgradeCorePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ShowAreaPayload(BlockPos pos, int radius) implements CustomPacketPayload {
        public static final Type<ShowAreaPayload> TYPE = makeType("show_area");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowAreaPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ShowAreaPayload::pos,
                        ByteBufCodecs.VAR_INT, ShowAreaPayload::radius,
                        ShowAreaPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ChangePermissionPayload(BlockPos pos, String playerName, String permissionType, boolean value) implements CustomPacketPayload {
        public static final Type<ChangePermissionPayload> TYPE = makeType("change_permission");
        public static final StreamCodec<RegistryFriendlyByteBuf, ChangePermissionPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ChangePermissionPayload::pos,
                        ByteBufCodecs.STRING_UTF8, ChangePermissionPayload::playerName,
                        ByteBufCodecs.STRING_UTF8, ChangePermissionPayload::permissionType,
                        ByteBufCodecs.BOOL, ChangePermissionPayload::value,
                        ChangePermissionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateAdminCorePayload(BlockPos pos, int radius, boolean canBuild, boolean pvp, boolean explosions) implements CustomPacketPayload {
        public static final Type<UpdateAdminCorePayload> TYPE = makeType("update_admin_core");
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateAdminCorePayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, UpdateAdminCorePayload::pos,
                        ByteBufCodecs.VAR_INT, UpdateAdminCorePayload::radius,
                        ByteBufCodecs.BOOL, UpdateAdminCorePayload::canBuild,
                        ByteBufCodecs.BOOL, UpdateAdminCorePayload::pvp,
                        ByteBufCodecs.BOOL, UpdateAdminCorePayload::explosions,
                        UpdateAdminCorePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CreateClanPayload(BlockPos pos, String clanName) implements CustomPacketPayload {
        public static final Type<CreateClanPayload> TYPE = makeType("create_clan");
        public static final StreamCodec<RegistryFriendlyByteBuf, CreateClanPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, CreateClanPayload::pos,
                        ByteBufCodecs.STRING_UTF8, CreateClanPayload::clanName,
                        CreateClanPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdateFlagPayload(BlockPos pos, String flag) implements CustomPacketPayload {
        public static final Type<UpdateFlagPayload> TYPE = makeType("update_flag");
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFlagPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, UpdateFlagPayload::pos,
                        ByteBufCodecs.STRING_UTF8, UpdateFlagPayload::flag,
                        UpdateFlagPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record OpenFlagsPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenFlagsPayload> TYPE = makeType("open_flags");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenFlagsPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenFlagsPayload::pos, OpenFlagsPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Disolver el clan del jugador (solo el líder). pos = núcleo desde el que se abrió la GUI. */
    public record DisbandClanPayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<DisbandClanPayload> TYPE = makeType("disband_clan");
        public static final StreamCodec<RegistryFriendlyByteBuf, DisbandClanPayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, DisbandClanPayload::pos, DisbandClanPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─────────────────────────── S2C ───────────────────────────

    public record ShowAreaClientPayload(BlockPos pos, int radius) implements CustomPacketPayload {
        public static final Type<ShowAreaClientPayload> TYPE = makeType("show_area_client");
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowAreaClientPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, ShowAreaClientPayload::pos,
                        ByteBufCodecs.VAR_INT, ShowAreaClientPayload::radius,
                        ShowAreaClientPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Una entrada de núcleo para el sync S2C. */
    public record CoreData(BlockPos pos, UUID owner, int radius) {
        public static final StreamCodec<RegistryFriendlyByteBuf, CoreData> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, CoreData::pos,
                        UUIDUtil.STREAM_CODEC, CoreData::owner,
                        ByteBufCodecs.VAR_INT, CoreData::radius,
                        CoreData::new);
    }

    public record SyncProtectionPayload(List<CoreData> cores) implements CustomPacketPayload {
        public static final Type<SyncProtectionPayload> TYPE = makeType("sync_protection");
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncProtectionPayload> STREAM_CODEC =
                StreamCodec.composite(
                        CoreData.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncProtectionPayload::cores,
                        SyncProtectionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ─────────────────────────── Alianzas ───────────────────────────

    /** Un clan aliado con los permisos que sus miembros tienen en MIS protecciones. */
    public record AllyEntry(UUID clanId, String clanName, boolean build, boolean interact, boolean chests) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AllyEntry> STREAM_CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, AllyEntry::clanId,
                        ByteBufCodecs.STRING_UTF8, AllyEntry::clanName,
                        ByteBufCodecs.BOOL, AllyEntry::build,
                        ByteBufCodecs.BOOL, AllyEntry::interact,
                        ByteBufCodecs.BOOL, AllyEntry::chests,
                        AllyEntry::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, List<AllyEntry>> LIST_CODEC =
                STREAM_CODEC.apply(ByteBufCodecs.list());
    }

    /** Referencia ligera a un clan (para solicitudes y candidatos). */
    public record ClanRef(UUID clanId, String clanName) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ClanRef> STREAM_CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, ClanRef::clanId,
                        ByteBufCodecs.STRING_UTF8, ClanRef::clanName,
                        ClanRef::new);
        public static final StreamCodec<RegistryFriendlyByteBuf, List<ClanRef>> LIST_CODEC =
                STREAM_CODEC.apply(ByteBufCodecs.list());
    }

    /** C2S: el cliente pide (re)cargar los datos de alianzas del núcleo. */
    public record OpenAlliancePayload(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenAlliancePayload> TYPE = makeType("open_alliance");
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenAlliancePayload> STREAM_CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenAlliancePayload::pos, OpenAlliancePayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: acción de alianza. action ∈ {propose, accept, reject, cancel, break}. */
    public record AllianceActionPayload(BlockPos pos, String action, UUID targetClanId) implements CustomPacketPayload {
        public static final Type<AllianceActionPayload> TYPE = makeType("alliance_action");
        public static final StreamCodec<RegistryFriendlyByteBuf, AllianceActionPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, AllianceActionPayload::pos,
                        ByteBufCodecs.STRING_UTF8, AllianceActionPayload::action,
                        UUIDUtil.STREAM_CODEC, AllianceActionPayload::targetClanId,
                        AllianceActionPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: el líder ajusta un permiso B/I/C de un clan aliado. */
    public record AllyPermPayload(BlockPos pos, UUID allyClanId, String permType, boolean value) implements CustomPacketPayload {
        public static final Type<AllyPermPayload> TYPE = makeType("ally_perm");
        public static final StreamCodec<RegistryFriendlyByteBuf, AllyPermPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, AllyPermPayload::pos,
                        UUIDUtil.STREAM_CODEC, AllyPermPayload::allyClanId,
                        ByteBufCodecs.STRING_UTF8, AllyPermPayload::permType,
                        ByteBufCodecs.BOOL, AllyPermPayload::value,
                        AllyPermPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: snapshot completo del estado de alianzas para la AllianceScreen. */
    public record AllianceDataPayload(boolean isLeader, boolean hasClan, String ownClanName, int maxAlliances,
                                      List<AllyEntry> allies, List<ClanRef> incoming,
                                      List<ClanRef> outgoing, List<ClanRef> candidates) implements CustomPacketPayload {
        public static final Type<AllianceDataPayload> TYPE = makeType("alliance_data");
        public static final StreamCodec<RegistryFriendlyByteBuf, AllianceDataPayload> STREAM_CODEC =
                StreamCodec.of(
                        (buf, p) -> {
                            buf.writeBoolean(p.isLeader());
                            buf.writeBoolean(p.hasClan());
                            buf.writeUtf(p.ownClanName());
                            buf.writeVarInt(p.maxAlliances());
                            AllyEntry.LIST_CODEC.encode(buf, p.allies());
                            ClanRef.LIST_CODEC.encode(buf, p.incoming());
                            ClanRef.LIST_CODEC.encode(buf, p.outgoing());
                            ClanRef.LIST_CODEC.encode(buf, p.candidates());
                        },
                        buf -> new AllianceDataPayload(
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readUtf(),
                                buf.readVarInt(),
                                AllyEntry.LIST_CODEC.decode(buf),
                                ClanRef.LIST_CODEC.decode(buf),
                                ClanRef.LIST_CODEC.decode(buf),
                                ClanRef.LIST_CODEC.decode(buf)));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
