package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.block.ProtectionCoreBlock;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.registry.ModItems;
import com.tumod.protectormod.registry.ModMenus;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.PlayerConfigData;
import com.tumod.protectormod.util.ProtectionDataManager;
import com.tumod.protectormod.util.ProtectorTeamData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ProtectionCoreBlockEntity extends BlockEntity implements MenuProvider {

    private int coreLevel = 1;
    private int range = 10;
    public int adminRadius = 128;
    private UUID ownerUUID;
    protected String clanName = "";
    protected UUID clanId = null; // Fase 7: id estable del clan dueño de esta protección (null = sin clan)
    private String ownerName = "Protector";

    private final Map<String, Boolean> flags = new HashMap<>();

    protected final Map<UUID, PlayerPermissions> permissionsMap = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();

    // Snapshot del roster del clan dueño (líder + miembros) sincronizado al cliente para la GUI.
    // Los permisos B/I/C NO van aquí: son por-núcleo (permissionsMap del propio núcleo).
    public record ClanMemberView(UUID id, String name) {}
    private UUID clanLeaderUuid = null;
    private final List<ClanMemberView> clanMembers = new ArrayList<>();

    private final SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ProtectionCoreBlockEntity.this.setChanged();
            ProtectionCoreBlockEntity.this.markDirtyAndUpdate();
        }
    };

    public void initializeDefaultFlags() {
        this.flags.clear();
        for (String f : getAllFlagKeys()) {
            if (f.equals("entry") || f.equals("hunger") || f.equals("fire-spread") || f.equals("item-pickup")) {
                flags.put(f, true);
            } else {
                flags.put(f, false);
            }
        }
    }

    public List<String> getAllFlagKeys() {
        List<String> all = new ArrayList<>();
        all.addAll(BASIC_FLAGS);
        all.addAll(ADMIN_FLAGS);
        return all;
    }

    public ProtectionCoreBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    public ProtectionCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        initializeDefaultFlags();
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            ProtectionDataManager data = ProtectionDataManager.get(serverLevel);
            data.removeCore(this.worldPosition);
            data.syncToAll(serverLevel);
        }
        super.setRemoved();
    }

    public void setRadius(int newRadius) {
        if (isAdmin()) {
            this.adminRadius = newRadius;
        } else {
            this.range = newRadius;
        }
        this.markDirtyAndUpdate();
    }

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    public boolean isInside(BlockPos targetPos) {
        return this.worldPosition.distSqr(targetPos) <= (double) (this.range * this.range);
    }

    public int getCoreLevel() {
        return this.coreLevel;
    }

    public String getClanName() {
        return this.clanName != null ? this.clanName : "";
    }

    public void setClanName(String name) {
        this.clanName = name;
        this.markDirtyAndUpdate();
    }

    public UUID getClanId() {
        return this.clanId;
    }

    /**
     * Reinicia TODA la identidad/permisos de este núcleo y lo asigna al colocador. Se llama al
     * colocar la protección para evitar que un item con {@code block_entity_data} (p.ej. obtenido
     * con pick-block de otro núcleo ya colocado) arrastre el owner/clan/permisos del anterior.
     */
    public void resetForPlacement(UUID owner, String ownerName) {
        this.ownerUUID = owner;
        this.ownerName = ownerName;
        this.clanId = null;
        this.clanName = "";
        this.clanLeaderUuid = null;
        this.clanMembers.clear();
        this.permissionsMap.clear();
        this.nameCache.clear();
        initializeDefaultFlags();
        markDirtyAndUpdate();
    }

    /** Asocia esta protección a un clan (nombre para display + id estable para permisos). */
    public void setClan(String name, UUID id) {
        this.clanName = name;
        this.clanId = id;
        this.markDirtyAndUpdate();
    }

    /** Snapshot de miembros del clan dueño (sincronizado desde el servidor, para la GUI). */
    public List<ClanMemberView> getClanMembers() {
        return this.clanMembers;
    }

    /** UUID del líder del clan dueño (para saber si el que mira es el líder). null si no hay clan. */
    public UUID getClanLeaderUuid() {
        return this.clanLeaderUuid;
    }

    /** ¿El jugador es miembro del clan dueño de esta protección? (para poder abrir/ver el núcleo). */
    public boolean isClanMember(Player player) {
        if (this.clanId == null || !(this.level instanceof ServerLevel sl)) return false;
        ClanSavedData.ClanInstance clan = ClanSavedData.get(sl).getClanById(this.clanId);
        return clan != null && clan.memberInfo.containsKey(player.getUUID());
    }

    public void setCoreLevelClient(int level) {
        this.coreLevel = level;
    }

    /**
     * Si el jugador pertenece a un clan ALIADO del clan dueño de este núcleo, devuelve los permisos
     * que ese clan aliado tiene aquí (tier entre miembro y forastero). null si no aplica.
     */
    private ClanSavedData.AllyPerms getAllyPermsFor(Player player) {
        if (this.clanId == null || !(this.level instanceof ServerLevel sl)) return null;
        ClanSavedData data = ClanSavedData.get(sl);
        ClanSavedData.ClanInstance coreClan = data.getClanById(this.clanId);
        if (coreClan == null || coreClan.allies.isEmpty()) return null;
        if (coreClan.members.contains(player.getUUID())) return null; // miembro propio: no es aliado
        ClanSavedData.ClanInstance playerClan = data.getClanByMember(player.getUUID());
        if (playerClan == null) return null;
        return coreClan.allies.get(playerClan.clanId);
    }

    public boolean isTrusted(Player player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            boolean isTestMode = PlayerConfigData.get(serverLevel).isTestMode(player.getUUID());
            if (isTestMode) return false;
        }

        if (player.getUUID().equals(this.getOwnerUUID()) || player.hasPermissions(2)) return true;

        if (this.clanId != null) {
            // Núcleo de clan: solo miembros. Permiso = override de ESTE núcleo o full por defecto.
            if (this.level instanceof ServerLevel serverLevel) {
                ClanSavedData.ClanInstance clan = ClanSavedData.get(serverLevel).getClanById(this.clanId);
                if (clan != null && clan.members.contains(player.getUUID())) {
                    PlayerPermissions ov = permissionsMap.get(player.getUUID());
                    return ov != null ? ov.canBuild : true;
                }
            }
            // Clan aliado: acceso configurable (build).
            ClanSavedData.AllyPerms ally = getAllyPermsFor(player);
            return ally != null && ally.build;
        }

        // Núcleo de clan antiguo (clanName pero sin clanId): SOLO miembros. Sin fallback
        // a permisos individuales (evita que un expulsado con entrada vieja mantenga acceso).
        if (this.clanName != null && !this.clanName.isEmpty()) {
            if (this.level instanceof ServerLevel sl2) {
                ClanSavedData.ClanInstance playerClan = ClanSavedData.get(sl2).getClanByMember(player.getUUID());
                if (playerClan != null && this.clanName.equalsIgnoreCase(playerClan.name)) return true;
            }
            return false;
        }

        // Núcleo sin clan: permisos individuales.
        PlayerPermissions perms = this.permissionsMap.get(player.getUUID());
        return perms != null && perms.canBuild;
    }

    public boolean hasPermission(Player player, String type) {
        if (player.level() instanceof ServerLevel serverLevel) {
            boolean isTestMode = PlayerConfigData.get(serverLevel).isTestMode(player.getUUID());
            if (isTestMode) return false;
        }

        if (player.getUUID().equals(ownerUUID) || player.hasPermissions(2)) return true;

        // Núcleo de clan: miembro → override de ESTE núcleo para ese tipo, o full por defecto.
        if (this.clanId != null) {
            if (this.level instanceof ServerLevel serverLevel) {
                ClanSavedData.ClanInstance clan = ClanSavedData.get(serverLevel).getClanById(this.clanId);
                if (clan != null && clan.members.contains(player.getUUID())) {
                    PlayerPermissions ov = permissionsMap.get(player.getUUID());
                    if (ov == null) return true;
                    return switch (type.toLowerCase()) {
                        case "build" -> ov.canBuild;
                        case "interact" -> ov.canInteract;
                        case "chests" -> ov.canOpenChests;
                        default -> false;
                    };
                }
            }
            // Clan aliado: permiso configurable por tipo.
            ClanSavedData.AllyPerms ally = getAllyPermsFor(player);
            return ally != null && ally.get(type);
        }

        // Núcleo de clan antiguo (clanName sin clanId): solo miembros, sin fallback individual.
        if (this.clanName != null && !this.clanName.isEmpty()) {
            if (this.level instanceof ServerLevel serverLevel) {
                ClanSavedData.ClanInstance clan = ClanSavedData.get(serverLevel).getClanByMember(player.getUUID());
                if (clan != null && this.clanName.equalsIgnoreCase(clan.name)) return true;
            }
            return false;
        }

        PlayerPermissions perms = permissionsMap.get(player.getUUID());
        if (perms == null) return false;

        return switch (type.toLowerCase()) {
            case "build" -> perms.canBuild;
            case "interact" -> perms.canInteract;
            case "chests" -> perms.canOpenChests;
            default -> false;
        };
    }

    /**
     * ¿El jugador está relacionado con este núcleo (dueño/OP/miembro del clan/invitado individual)?
     * Se usa para decidir si sus permisos B/I/C son autoritativos (miembro) o si rige el flag de
     * zona (forastero).
     */
    public boolean isMemberOrInvited(Player player) {
        if (player.getUUID().equals(getOwnerUUID()) || player.hasPermissions(2)) return true;
        if (this.clanId != null) {
            if (this.level instanceof ServerLevel sl) {
                ClanSavedData.ClanInstance clan = ClanSavedData.get(sl).getClanById(this.clanId);
                if (clan != null && clan.members.contains(player.getUUID())) return true;
            }
            // Miembro de clan aliado: sus permisos aliados son autoritativos (no rige el flag de zona).
            return getAllyPermsFor(player) != null;
        }
        if (this.clanName != null && !this.clanName.isEmpty()) {
            if (this.level instanceof ServerLevel sl) {
                ClanSavedData.ClanInstance pc = ClanSavedData.get(sl).getClanByMember(player.getUUID());
                return pc != null && this.clanName.equalsIgnoreCase(pc.name);
            }
            return false;
        }
        return permissionsMap.containsKey(player.getUUID());
    }

    public void updatePermission(UUID playerUUID, String playerName, String type, boolean value) {
        PlayerPermissions perms = permissionsMap.computeIfAbsent(playerUUID, k -> new PlayerPermissions());
        nameCache.put(playerUUID, playerName);

        switch (type) {
            case "build" -> perms.canBuild = value;
            case "interact" -> perms.canInteract = value;
            case "chests" -> perms.canOpenChests = value;
        }
        // Registrar en el índice central de party (para AeroClaims / dueños offline).
        if (this.level instanceof ServerLevel sl && this.ownerUUID != null) {
            ProtectorTeamData.get(sl).addTrust(this.ownerUUID, playerUUID);
        }
        markDirtyAndUpdate();
    }

    /**
     * Ajusta el permiso de un miembro del clan EN ESTE núcleo (override por-núcleo). Si no había
     * override, se crea desde "acceso completo" (que es el default de un miembro del clan).
     */
    public void setClanMemberPerm(UUID uuid, String name, String type, boolean value) {
        PlayerPermissions perms = permissionsMap.get(uuid);
        if (perms == null) {
            perms = new PlayerPermissions(true, true, true);
            permissionsMap.put(uuid, perms);
        }
        nameCache.put(uuid, name);
        switch (type) {
            case "build" -> perms.canBuild = value;
            case "interact" -> perms.canInteract = value;
            case "chests" -> perms.canOpenChests = value;
        }
        markDirtyAndUpdate();
    }

    /** UUID del dueño sin fallback aleatorio (null si no asignado). */
    public UUID getRawOwnerUUID() {
        return this.ownerUUID;
    }

    /** Conjunto de UUIDs con algún permiso en este núcleo (invitados/confianza). */
    public java.util.Set<UUID> getTrustedUUIDs() {
        return new java.util.HashSet<>(permissionsMap.keySet());
    }

    /** Nombre cacheado de un invitado (para migrar invitados a miembros de clan). */
    public String getTrustedName(UUID uuid) {
        return nameCache.getOrDefault(uuid, "?");
    }

    public List<String> getTrustedNames() {
        return new ArrayList<>(nameCache.values());
    }

    public void upgrade(ServerPlayer player) {
        int siguienteNivel = this.coreLevel + 1;
        if (siguienteNivel > 5) return;

        int radioFuturo = obtenerRadioPorNivel(siguienteNivel);

        if (this.level instanceof ServerLevel sLevel) {
            if (!isAdmin()) {
                boolean overlaps = ProtectionDataManager.get(sLevel).getAllCores().entrySet().stream()
                        .filter(entry -> !entry.getKey().equals(this.worldPosition))
                        .anyMatch(entry -> {
                            double dist = Math.sqrt(this.worldPosition.distSqr(entry.getKey()));
                            return dist < (radioFuturo + entry.getValue().radius());
                        });

                if (overlaps) {
                    player.displayClientMessage(Component.literal("§c[!] No hay espacio. Choca con otra zona."), true);
                    player.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
                    return;
                }
            }

            if (!canUpgrade()) {
                player.displayClientMessage(Component.literal("§c[!] No tienes los materiales necesarios."), true);
                return;
            }

            this.inventory.removeItem(0, 1);
            int cantidadAConsumir = (this.coreLevel == 1) ? 64 : 32;
            this.inventory.removeItem(1, cantidadAConsumir);

            this.coreLevel++;
            this.range = radioFuturo;
            actualizarEstadosBloque();

            ProtectionDataManager manager = ProtectionDataManager.get(sLevel);
            manager.addCore(this.worldPosition, getOwnerUUID(), this.range);
            manager.syncToAll(sLevel);

            efectosMejora();
            this.markDirtyAndUpdate();

            player.displayClientMessage(Component.literal("§a[!] ¡Núcleo mejorado al nivel " + this.coreLevel + "!"), true);
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
        }
    }

    public PlayerPermissions getPermissionsFor(String playerName) {
        return permissionsMap.entrySet().stream()
                .filter(entry -> nameCache.getOrDefault(entry.getKey(), "").equalsIgnoreCase(playerName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new PlayerPermissions());
    }

    public List<String> getPlayersWithAnyPermission() {
        return new ArrayList<>(this.nameCache.values());
    }

    public void updatePermission(String playerName, String permission, boolean value) {
        UUID targetUUID = nameCache.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(playerName))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (this.level instanceof ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayerByName(playerName);
            if (player != null) {
                targetUUID = player.getUUID();
            }
        }

        if (targetUUID != null) {
            this.updatePermission(targetUUID, playerName, permission, value);
        }
    }

    public void removePlayerPermissions(String playerName) {
        UUID targetUUID = nameCache.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(playerName))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (targetUUID != null) {
            this.permissionsMap.remove(targetUUID);
            this.nameCache.remove(targetUUID);
            if (this.level instanceof ServerLevel sl && this.ownerUUID != null) {
                ProtectorTeamData.get(sl).removeTrust(this.ownerUUID, targetUUID);
            }
            this.markDirtyAndUpdate();
        }
    }

    public boolean canPlayerEditFlag(Player player, String flagId) {
        if (player.level() instanceof ServerLevel serverLevel) {
            boolean isTestMode = PlayerConfigData.get(serverLevel).isTestMode(player.getUUID());
            if (isTestMode) return false;
        }
        if (player.hasPermissions(2)) return true;
        return player.getUUID().equals(this.getOwnerUUID());
    }

    private void actualizarEstadosBloque() {
        if (this.level == null || this.level.isClientSide) return;

        BlockState estadoBase = this.level.getBlockState(this.worldPosition);

        if (estadoBase.getBlock() instanceof ProtectionCoreBlock) {
            BlockState nuevoEstadoLower = estadoBase.setValue(ProtectionCoreBlock.LEVEL, this.coreLevel);
            this.level.setBlock(this.worldPosition, nuevoEstadoLower, 3);

            BlockPos upperPos = this.worldPosition.above();
            BlockState estadoUpper = this.level.getBlockState(upperPos);

            if (estadoUpper.getBlock() instanceof ProtectionCoreBlock &&
                    estadoUpper.getValue(ProtectionCoreBlock.HALF) == DoubleBlockHalf.UPPER) {

                this.level.setBlock(upperPos, estadoUpper.setValue(ProtectionCoreBlock.LEVEL, this.coreLevel), 3);
            }
        }

        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, estadoBase, estadoBase, 3);
    }

    public static final List<String> BASIC_FLAGS = List.of("pvp", "build", "chests", "interact", "villager-trade", "fire-damage", "hunger");
    public static final List<String> ADMIN_FLAGS = List.of("explosions", "mob-spawn", "entry", "fall-damage", "fire-spread", "lighter", "item-pickup", "mob-grief", "use-buckets", "item-drop", "crop-trample", "enderpearl");

    public void setFlag(String flag, boolean value) {
        flags.put(flag, value);
        markDirtyAndUpdate();
    }

    public boolean getFlag(String flag) {
        if (flags.containsKey(flag)) {
            return flags.get(flag);
        }
        return flag.equals("entry") || flag.equals("hunger") || flag.equals("fire-spread");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CoreLevel", this.coreLevel);
        tag.putInt("AdminRadius", this.adminRadius);
        tag.putString("ClanName", this.clanName);
        if (this.clanId != null) {
            tag.putUUID("ClanId", this.clanId);
            // Resolver y sincronizar el snapshot del clan (líder + miembros con permisos).
            if (this.level instanceof ServerLevel sl) {
                ClanSavedData.ClanInstance clan = ClanSavedData.get(sl).getClanById(this.clanId);
                if (clan != null) {
                    tag.putUUID("ClanLeader", clan.leaderUUID);
                    ListTag members = new ListTag();
                    for (Map.Entry<UUID, ClanSavedData.ClanMember> e : clan.memberInfo.entrySet()) {
                        CompoundTag mt = new CompoundTag();
                        mt.putUUID("Id", e.getKey());
                        mt.putString("Name", e.getValue().name);
                        members.add(mt);
                    }
                    tag.put("ClanMembers", members);
                }
            }
        }
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putString("OwnerName", this.ownerName);

        CompoundTag flagsTag = new CompoundTag();
        flags.forEach(flagsTag::putBoolean);
        tag.put("CoreFlags", flagsTag);

        ListTag permsList = new ListTag();
        permissionsMap.forEach((uuid, perms) -> {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", uuid);
            pTag.putString("name", nameCache.getOrDefault(uuid, "Unknown"));
            pTag.putBoolean("build", perms.canBuild);
            pTag.putBoolean("interact", perms.canInteract);
            pTag.putBoolean("chests", perms.canOpenChests);
            permsList.add(pTag);
        });
        tag.put("PermissionsList", permsList);

        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            items.set(i, inventory.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, items, registries);

        tag.putInt("Range", this.range);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.coreLevel = tag.getInt("CoreLevel");
        this.adminRadius = tag.getInt("AdminRadius");
        this.clanName = tag.getString("ClanName");
        this.clanId = tag.hasUUID("ClanId") ? tag.getUUID("ClanId") : null;
        this.clanLeaderUuid = tag.hasUUID("ClanLeader") ? tag.getUUID("ClanLeader") : null;
        this.clanMembers.clear();
        if (tag.contains("ClanMembers")) {
            ListTag members = tag.getList("ClanMembers", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < members.size(); i++) {
                CompoundTag mt = members.getCompound(i);
                this.clanMembers.add(new ClanMemberView(mt.getUUID("Id"), mt.getString("Name")));
            }
        }
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.ownerName = tag.getString("OwnerName");

        this.flags.clear();
        CompoundTag flagsTag = tag.getCompound("CoreFlags");
        for (String key : flagsTag.getAllKeys()) this.flags.put(key, flagsTag.getBoolean(key));

        this.permissionsMap.clear();
        this.nameCache.clear();
        ListTag permsList = tag.getList("PermissionsList", 10);
        for (int i = 0; i < permsList.size(); i++) {
            CompoundTag pTag = permsList.getCompound(i);
            UUID uuid = pTag.getUUID("uuid");
            this.nameCache.put(uuid, pTag.getString("name"));
            this.permissionsMap.put(uuid, new PlayerPermissions(pTag.getBoolean("build"), pTag.getBoolean("interact"), pTag.getBoolean("chests")));
        }

        inventory.clearContent();
        NonNullList<ItemStack> items = NonNullList.withSize(inventory.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        for (int i = 0; i < items.size(); i++) {
            inventory.setItem(i, items.get(i));
        }

        this.range = tag.getInt("Range");
        if (this.level != null && !this.level.isClientSide) {
            markDirtyAndUpdate();
        }
    }

    public void markDirtyAndUpdate() {
        this.setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private int obtenerRadioPorNivel(int nivel) {
        return switch (nivel) {
            case 1 -> 8; case 2 -> 16; case 3 -> 32; case 4 -> 64; case 5 -> 128;
            default -> 8;
        };
    }

    public UUID getOwnerUUID() { return this.ownerUUID != null ? this.ownerUUID : UUID.randomUUID(); }
    public String getOwnerName() { return (clanName != null && !clanName.isEmpty()) ? clanName : ownerName; }
    public void setOwner(UUID uuid, String name) { this.ownerUUID = uuid; this.ownerName = name; markDirtyAndUpdate(); }
    public int getRadius() { return isAdmin() ? adminRadius : obtenerRadioPorNivel(coreLevel); }
    public boolean isAdmin() { return getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get()); }

    public void setAdminRadius(int newRadius) {
        this.adminRadius = newRadius;
        this.markDirtyAndUpdate();
    }

    private void efectosMejora() {
        if (this.level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX()+0.5, worldPosition.getY()+1, worldPosition.getZ()+0.5, 30, 0.5, 0.5, 0.5, 0.15);
        }
    }

    public boolean canUpgrade() {
        if (isAdmin() || coreLevel >= 5) return false;

        ItemStack up = inventory.getItem(0);
        ItemStack mat = inventory.getItem(1);

        if (!up.is(ModItems.PROTECTION_UPGRADE.get())) return false;

        return switch (coreLevel) {
            case 1 -> mat.is(Items.IRON_INGOT) && mat.getCount() >= 64;
            case 2 -> mat.is(Items.GOLD_INGOT) && mat.getCount() >= 32;
            case 3 -> mat.is(Items.DIAMOND) && mat.getCount() >= 32;
            case 4 -> mat.is(Items.NETHERITE_INGOT) && mat.getCount() >= 32;
            default -> false;
        };
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new ProtectionCoreMenu(
                isAdmin() ? ModMenus.ADMIN_CORE_MENU.get() : ModMenus.PROTECTION_CORE_MENU.get(),
                id, inv, this.inventory, this);
    }

    @Override
    public Component getDisplayName() { return Component.literal("Protection Core"); }

    public static class PlayerPermissions {
        public boolean canBuild = false;
        public boolean canInteract = false;
        public boolean canOpenChests = false;

        public PlayerPermissions() {}

        public PlayerPermissions(boolean build, boolean interact, boolean chests) {
            this.canBuild = build;
            this.canInteract = interact;
            this.canOpenChests = chests;
        }
    }
}
