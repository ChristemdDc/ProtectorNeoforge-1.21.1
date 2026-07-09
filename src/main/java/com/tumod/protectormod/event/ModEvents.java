package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.command.ClanCommands;
import com.tumod.protectormod.config.InteractWhitelistConfig;
import com.tumod.protectormod.network.ModNetworking;
import com.tumod.protectormod.util.PlayerConfigData;
import com.tumod.protectormod.util.ProtectedBlockMarker;
import com.tumod.protectormod.util.ProtectionDataManager;
import com.tumod.protectormod.util.ProtectorTeamData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID)
public class ModEvents {

    private static final Map<UUID, BlockPos> PLAYER_CORE_CACHE = new HashMap<>();

    // ── Comandos ──
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ClanCommands.register(event.getDispatcher());
    }

    // ── Arranque del servidor ──
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Carga la persistencia de bloques marcados (SavedData/NBT dentro del mundo).
        ProtectedBlockMarker.init(event.getServer().overworld());

        // La integración con AeroClaims ya NO se registra aquí: vive en mixins (MixinAero*) que
        // inyectan los clanes de ProtectorMod como party y los slots por config. AeroClaims corre
        // sin modificar y sin depender de OPAC/FTB.
    }

    // ── Re-registrar cores al cargar el chunk (reemplaza BLOCK_ENTITY_LOAD de Fabric) ──
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor level = event.getLevel();
        if (level.isClientSide()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        var chunk = event.getChunk();
        for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
            if (chunk.getBlockEntity(bePos) instanceof ProtectionCoreBlockEntity core) {
                ProtectionDataManager.get(serverLevel).addOrUpdateCore(core.getBlockPos(), core.getOwnerUUID(), core.getRadius());
                // Backfill autocurativo del índice central de party (invitados existentes).
                UUID owner = core.getRawOwnerUUID();
                if (owner != null) {
                    for (UUID trusted : core.getTrustedUUIDs()) {
                        ProtectorTeamData.get(serverLevel).addTrust(owner, trusted);
                    }
                }
            }
        }
    }

    // ── Comercio con aldeanos ──
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Level world = event.getLevel();
        if (world.isClientSide) return;
        Player player = event.getEntity();
        if (event.getTarget() instanceof Villager) {
            ProtectionCoreBlockEntity core = findCoreAt(world, event.getTarget().blockPosition());
            if (core != null && !core.getFlag("villager-trade") && !core.isTrusted(player)) {
                player.displayClientMessage(Component.literal("§c[!] Tradeo con aldeanos desactivado."), true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        }
    }

    // ── Interacción / colocación con bloques ──
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level world = event.getLevel();
        if (world.isClientSide) return;

        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        InteractionHand hand = event.getHand();
        ServerLevel sLevel = (ServerLevel) world;

        // FakePlayer (máquinas de Create, etc.) → solo permite si tiene bypass
        if (ProtectedBlockMarker.isFakePlayer(player)) {
            ProtectionCoreBlockEntity coreCheck = findCoreAt(sLevel, pos);
            if (coreCheck != null && !ProtectedBlockMarker.shouldFakePlayerBypass(player, pos, world)) {
                deny(event);
            }
            return;
        }

        ItemStack item = player.getItemInHand(hand);

        // Encendedor (fuego)
        if (item.is(Items.FLINT_AND_STEEL) || item.is(Items.FIRE_CHARGE)) {
            BlockPos targetPos = pos.relative(event.getFace());
            ProtectionCoreBlockEntity core = findCoreAt(sLevel, targetPos);
            if (core != null && !core.getFlag("lighter") && !core.isTrusted(player)) {
                player.displayClientMessage(Component.literal("§c[!] El uso de fuego está desactivado aquí."), true);
                deny(event);
                return;
            }
        }

        // NOTA: los cubos (vaciar líquido) se gestionan en MixinBucketItem.emptyContents,
        // porque cancelar este evento NO detiene la colocación real del fluido. Los
        // dispensers vanilla se gestionan en MixinDispenserBlock.

        // Clic sobre el propio bloque núcleo → no bloquear (abre su GUI; el acceso lo valida el bloque).
        if (world.getBlockState(pos).getBlock() instanceof com.tumod.protectormod.block.ProtectionCoreBlock) return;

        BlockEntity be = world.getBlockEntity(pos);

        // Abrir contenedores → permiso "chests" (el más restrictivo entre los núcleos que cubren pos).
        if (be instanceof net.minecraft.world.Container) {
            DenyInfo deny = deniedByAnyCore(sLevel, pos, player, "chests");
            if (deny != null) {
                player.displayClientMessage(Component.literal("§c[!] No puedes abrir contenedores aquí."), true);
                deny(event);
            }
            return;
        }

        // Interacción con bloque (botón/palanca/puerta...). La COLOCACIÓN de bloques la
        // gestiona onEntityPlace (permiso "build"); si el jugador lleva un bloque en la mano
        // asumimos colocación y no aplicamos el check de "interact".
        ItemStack stack = player.getItemInHand(hand);
        boolean holdingBlock = !stack.isEmpty() && stack.getItem() instanceof BlockItem;
        if (holdingBlock) return;

        DenyInfo deny = deniedByAnyCore(sLevel, pos, player, "interact");
        if (deny != null) {
            // Whitelist admin para forasteros (puertas, camas, botones...).
            if (deny.core() != null && !deny.core().isMemberOrInvited(player) && deny.core().isAdmin()) {
                String blockId = BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock()).toString();
                if (InteractWhitelistConfig.get().isWhitelisted(blockId)) return;
            }
            player.displayClientMessage(Component.literal("§c[!] No puedes interactuar aquí."), true);
            deny(event);
        }
    }

    /** Permiso efectivo: miembro/invitado → su permiso por-tipo; forastero → flag de zona. */
    private static boolean allowed(ProtectionCoreBlockEntity core, Player player, String type) {
        if (core.isMemberOrInvited(player)) return core.hasPermission(player, type);
        return core.getFlag(type);
    }

    /** Resultado de una denegación: el núcleo que deniega (o null si es un núcleo distante sin BE). */
    private record DenyInfo(ProtectionCoreBlockEntity core, boolean distant) {}

    /**
     * Evalúa la acción contra TODOS los núcleos que cubren pos: el MÁS RESTRICTIVO manda. Si varios
     * núcleos (p.ej. dos del mismo clan con distintos permisos por-jugador) solapan su área, basta
     * que UNO deniegue para bloquear — así el permiso B/I/C por-núcleo se respeta aunque las zonas
     * se superpongan. Ignora ships (los núcleos normales ceden; los admin sobre ships se tratan
     * aparte en cada evento).
     *
     * @return el núcleo/estado que DENIEGA la acción, o null si todos la permiten.
     */
    private static DenyInfo deniedByAnyCore(ServerLevel level, BlockPos pos, Player player, String type) {
        if (com.tumod.protectormod.integration.ShipGuard.isInsideShip(level, pos)) return null;
        for (ProtectionDataManager.CoreEntry entry : ProtectionDataManager.get(level).getCoresAt(pos)) {
            BlockEntity be = level.getBlockEntity(entry.pos());
            if (be instanceof ProtectionCoreBlockEntity core) {
                if (!allowed(core, player, type)) return new DenyInfo(core, false);
            } else if (!player.getUUID().equals(entry.owner()) && !player.hasPermissions(2)) {
                return new DenyInfo(null, true); // núcleo distante (sin BE): solo dueño/OP
            }
        }
        return null;
    }

    private static String denyMessage(DenyInfo deny, String verb) {
        if (deny.distant()) return "§c[!] Zona protegida (núcleo distante).";
        String owner = (deny.core() != null && deny.core().isAdmin()) ? "la Administración"
                : (deny.core() != null ? deny.core().getOwnerName() : "otro jugador");
        return "§c[!] No puedes " + verb + " en la zona de " + owner;
    }

    /**
     * Admin sobre ships: devuelve el núcleo ADMIN que cubre una posición de ship (o null).
     * Los núcleos normales ceden ante ships (ShipGuard en findCoreAt), pero los admin mantienen
     * prioridad para romper/colocar/explotar (los ships se pueden USAR, no modificar, en zonas admin).
     */
    private static ProtectionCoreBlockEntity adminCoreOverShip(ServerLevel level, BlockPos pos) {
        if (!com.tumod.protectormod.integration.ShipGuard.isInsideShip(level, pos)) return null;
        ProtectionDataManager.CoreEntry entry = ProtectionDataManager.get(level).getCoreAt(pos);
        if (entry == null) return null;
        if (level.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity core
                && !core.isRemoved() && core.isAdmin()) {
            return core;
        }
        return null;
    }

    private static void deny(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    /**
     * Red de seguridad para "núcleo distante": devuelve la entrada de un núcleo que cubre {@code pos}
     * cuyo BLOQUE-núcleo no se pudo resolver (chunk del núcleo descargado). Como el mapa de núcleos
     * ({@link ProtectionDataManager}) es persistente e independiente de la carga de chunks, la ZONA
     * siempre se conoce; si además no podemos leer el BE (flags/permisos), protegemos por defecto.
     * Excluye posiciones dentro de un ship (allí manda AeroClaims). null si no aplica.
     */
    private static ProtectionDataManager.CoreEntry distantCoreAt(ServerLevel level, BlockPos pos) {
        ProtectionDataManager.CoreEntry entry = ProtectionDataManager.get(level).getCoreAt(pos);
        if (entry == null) return null;
        if (com.tumod.protectormod.integration.ShipGuard.isInsideShip(level, pos)) return null;
        return (level.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity) ? null : entry;
    }

    // ── Romper bloques ──
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sLevel)) return;
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();

        if (ProtectedBlockMarker.isFakePlayer(player)) {
            ProtectionCoreBlockEntity coreCheck = findCoreAt(sLevel, pos);
            if (coreCheck != null && !ProtectedBlockMarker.shouldFakePlayerBypass(player, pos, sLevel)) {
                event.setCanceled(true);
            }
            return;
        }

        // El más restrictivo entre TODOS los núcleos que cubren pos (respeta B/I/C por-núcleo aunque solapen).
        DenyInfo deny = deniedByAnyCore(sLevel, pos, player, "build");
        if (deny != null) {
            player.displayClientMessage(Component.literal(denyMessage(deny, "construir")), true);
            event.setCanceled(true);
            return;
        }

        // Admin sobre ships: no romper bloques de ships en zona admin (salvo OP).
        if (!player.hasPermissions(2) && adminCoreOverShip(sLevel, pos) != null) {
            player.displayClientMessage(Component.literal("§c[!] No puedes modificar ships dentro de una zona de administración."), true);
            event.setCanceled(true);
        }
    }

    // ── Daño (PvP, caída, fuego) ──
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.level() instanceof ServerLevel sLevel)) return;
        ProtectionCoreBlockEntity core = findCoreAt(sLevel, entity.blockPosition());
        if (core == null) return;

        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Player && entity instanceof Player && !core.getFlag("pvp")) {
            event.setCanceled(true);
            return;
        }
        if (source.is(DamageTypes.FALL) && !core.getFlag("fall-damage")) {
            event.setCanceled(true);
            return;
        }
        if ((source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.LAVA)) && !core.getFlag("fire-damage")) {
            entity.setRemainingFireTicks(0);
            event.setCanceled(true);
        }
    }

    // ── Explosiones (antes MixinExplosion) ──
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;
        event.getAffectedBlocks().removeIf(pos -> {
            ProtectionCoreBlockEntity core = findCoreAt(level, pos);
            if (core != null) {
                if (!core.getFlag("explosions")) return true;
            } else if (level instanceof ServerLevel sld && distantCoreAt(sld, pos) != null) {
                return true; // núcleo distante: proteger por defecto (no podemos leer su flag)
            }
            // Admin sobre ships: proteger bloques de ship de explosiones en zona admin.
            if (level instanceof ServerLevel sl) {
                ProtectionCoreBlockEntity ac = adminCoreOverShip(sl, pos);
                if (ac != null && !ac.getFlag("explosions")) return true;
            }
            return false;
        });
    }

    // ── Pisar cultivos (antes MixinFarmBlock) ──
    @SubscribeEvent
    public static void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sLevel)) return;
        if (event.getEntity() instanceof Player player) {
            ProtectionCoreBlockEntity core = findCoreAt(sLevel, event.getPos());
            if (core != null && !core.getFlag("crop-trample") && !core.isTrusted(player)) {
                event.setCanceled(true);
            }
        }
    }

    // ── Tirar ítems (antes MixinServerPlayer.drop) ──
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;
        ProtectionCoreBlockEntity core = findCoreAt(player.level(), player.blockPosition());
        if (core != null && !core.getFlag("item-drop") && !core.isTrusted(player)) {
            player.displayClientMessage(Component.literal("§c[!] No puedes tirar ítems aquí."), true);
            ItemEntity itemEntity = event.getEntity();
            event.setCanceled(true);
            player.getInventory().add(itemEntity.getItem());
        }
    }

    // ── Recoger ítems (antes MixinItemEntity) ──
    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        if (player.level().isClientSide) return;
        ItemEntity item = event.getItemEntity();
        ProtectionCoreBlockEntity core = findCoreAt(player.level(), item.blockPosition());
        if (core != null && !core.getFlag("item-pickup") && !core.isTrusted(player)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    // ── Spawn de mobs (antes MixinNaturalSpawner) ──
    @SubscribeEvent
    public static void onMobSpawn(FinalizeSpawnEvent event) {
        ServerLevel level = event.getLevel().getLevel();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        ProtectionCoreBlockEntity core = findCoreAt(level, pos);
        if (core != null && !core.getFlag("mob-spawn")) {
            event.setSpawnCancelled(true);
        }
    }

    // ── Perlas de Ender (flag "enderpearl") ──
    // Se intercepta la perla al aparecer (cubre lanzamiento con clic al aire, a un bloque o desde
    // dispensador). Si el lanzador no es de confianza y la flag está OFF, se elimina la perla y se
    // reembolsa (si no es creativo). Se comprueba la posición del LANZADOR (usar perlas "aquí").
    @SubscribeEvent
    public static void onEnderPearlThrown(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sLevel)) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.projectile.ThrownEnderpearl pearl)) return;
        if (!(pearl.getOwner() instanceof Player player)) return;
        ProtectionCoreBlockEntity core = findCoreAt(sLevel, player.blockPosition());
        if (core != null && !core.getFlag("enderpearl") && !core.isTrusted(player)) {
            player.displayClientMessage(Component.literal("§c[!] No puedes usar perlas de ender en esta zona."), true);
            event.setCanceled(true);
            if (!player.getAbilities().instabuild) {
                player.getInventory().add(new ItemStack(Items.ENDER_PEARL));
            }
        }
    }

    // ── Grief de mobs (flag "mob-grief"): endermen recogiendo bloques, etc. ──
    @SubscribeEvent
    public static void onMobGriefing(net.neoforged.neoforge.event.entity.EntityMobGriefingEvent event) {
        net.minecraft.world.entity.Entity entity = event.getEntity();
        if (entity == null || !(entity.level() instanceof ServerLevel sLevel)) return;
        ProtectionCoreBlockEntity core = findCoreAt(sLevel, entity.blockPosition());
        if (core != null && !core.getFlag("mob-grief")) {
            event.setCanGrief(false);
        }
    }

    // ── Colocar bloques: denegar + marcar máquinas (antes MixinBlockItem) ──
    @SubscribeEvent
    public static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel sLevel)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        BlockPos pos = event.getPos();

        // Denegar colocación con el más restrictivo entre TODOS los núcleos que cubren pos.
        DenyInfo deny = deniedByAnyCore(sLevel, pos, player, "build");
        if (deny != null) {
            player.displayClientMessage(Component.literal(denyMessage(deny, "construir")), true);
            event.setCanceled(true);
            return;
        }

        // Admin sobre ships: no colocar/extender bloques de ships en zona admin (salvo OP).
        if (!player.hasPermissions(2) && adminCoreOverShip(sLevel, pos) != null) {
            player.displayClientMessage(Component.literal("§c[!] No puedes modificar ships dentro de una zona de administración."), true);
            event.setCanceled(true);
            return;
        }

        // Marcar bloques especiales (Redstone/Create) colocados por miembros confiables,
        // para que sus máquinas (FakePlayers) tengan bypass. Usa el núcleo que cubre pos.
        ProtectionCoreBlockEntity core = findCoreAt(sLevel, pos);
        if (core != null && player instanceof ServerPlayer && !ProtectedBlockMarker.isFakePlayer(player)) {
            Block placedBlock = event.getPlacedBlock().getBlock();
            if (ProtectedBlockMarker.isSpecialBlock(placedBlock) && core.isTrusted(player)) {
                ProtectedBlockMarker.markBlockAsProtected(sLevel, pos, player.getUUID(), core.getBlockPos());
            }
        }
    }

    // ── Tick del servidor ──
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            onPlayerTick(player);
        }
    }

    private static void onPlayerTick(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        ServerLevel level = player.serverLevel();
        ProtectionCoreBlockEntity core = findCoreAt(level, player.blockPosition());

        updateEntryMessage(player, core);

        if (core != null) {
            if (!core.getFlag("hunger")) player.getFoodData().setFoodLevel(20);
            // No expulsar (teletransportar) si va en un ship o en el aire: teletransportar
            // cerca de un sublevel de Aeronautics puede causar bugs. Se le expulsará en
            // cuanto pise el suelo normal de la zona.
            if (!core.getFlag("entry") && !canBypass(player, core) && !isOnShipOrAirborne(player)) {
                ejectPlayer(player, core);
            }
        }

        if (PlayerConfigData.get(level).isVisualizerEnabled(player.getUUID()) && core != null) {
            ModNetworking.sendShowAreaToClient(player, core.getBlockPos(), core.getRadius());
        }
    }

    public static ProtectionCoreBlockEntity findCoreAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sLevel)) return null;
        ProtectionDataManager.CoreEntry entry = ProtectionDataManager.get(sLevel).getCoreAt(pos);
        if (entry != null) {
            // Fase 4: dentro de un ship, ProtectorMod se retira (AeroClaims manda ahí).
            // Se comprueba solo cuando un núcleo ya cubre la posición → coste despreciable.
            if (com.tumod.protectormod.integration.ShipGuard.isInsideShip(sLevel, pos)) {
                return null;
            }
            BlockEntity be = sLevel.getBlockEntity(entry.pos());
            if (be instanceof ProtectionCoreBlockEntity core) {
                return core;
            } else if (sLevel.isLoaded(entry.pos())) {
                // Ghost core: hay datos de protección pero no existe el bloque núcleo → limpiar.
                ProtectionDataManager.get(sLevel).removeCore(entry.pos());
                return null;
            }
        }
        return null;
    }

    private static void updateEntryMessage(Player player, @Nullable ProtectionCoreBlockEntity core) {
        UUID uuid = player.getUUID();
        BlockPos lastCorePos = PLAYER_CORE_CACHE.get(uuid);
        BlockPos currentCorePos = (core != null) ? core.getBlockPos() : null;

        if (!Objects.equals(lastCorePos, currentCorePos)) {
            if (currentCorePos != null) {
                String displayName = core.isAdmin() ? "§d§lAdministración" : "§b" + core.getOwnerName();
                if (!player.getUUID().equals(core.getOwnerUUID())) {
                    player.displayClientMessage(Component.literal("§e§l[!] §fEntrando a la zona de: " + displayName), true);
                }
            } else if (lastCorePos != null) {
                player.displayClientMessage(Component.literal("§cHas salido de la zona protegida"), true);
            }
            PLAYER_CORE_CACHE.put(uuid, currentCorePos);
        }
    }

    private static void ejectPlayer(Player player, ProtectionCoreBlockEntity core) {
        Vec3 coreCenter = Vec3.atCenterOf(core.getBlockPos());
        double targetDistance = core.getRadius() + 15.0;

        Vec3 direction = player.position().subtract(coreCenter).normalize();
        if (direction.lengthSqr() < 0.0001) {
            direction = new Vec3(1, 0, 0);
        }

        Vec3 targetPos = coreCenter.add(direction.scale(targetDistance));

        Level level = player.level();
        int x = (int) targetPos.x;
        int z = (int) targetPos.z;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

        if (y < level.getMinBuildHeight()) y = level.getSeaLevel();

        player.teleportTo(targetPos.x, y + 1, targetPos.z);
        player.displayClientMessage(Component.literal("§c§l[!] Entrada restringida. (Teletransportado a zona segura)"), true);
    }

    /** ¿El jugador va montado, en el aire, o sobre/dentro de un ship? (para no teletransportarlo). */
    private static boolean isOnShipOrAirborne(ServerPlayer player) {
        if (player.isPassenger() || !player.onGround()) return true;
        BlockPos pos = player.blockPosition();
        return com.tumod.protectormod.integration.ShipGuard.isInsideShip(player.level(), pos)
                || com.tumod.protectormod.integration.ShipGuard.isInsideShip(player.level(), pos.below());
    }

    private static boolean canBypass(Player player, ProtectionCoreBlockEntity core) {
        if (player.level() instanceof ServerLevel serverLevel) {
            boolean isTestMode = PlayerConfigData.get(serverLevel).isTestMode(player.getUUID());
            if (isTestMode) return false;
        }
        return player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2);
    }
}
