package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.AdminProtectorBlockEntity;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ProtectionCoreBlock extends Block implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 5);

    public ProtectionCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH)
                .setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, LEVEL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return null;

        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            return new AdminProtectorBlockEntity(pos, state);
        }
        return new ProtectionCoreBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player player)) return;
        ServerLevel sLevel = (ServerLevel) level;

        // Fase 4: no permitir protecciones dentro de un ship (AeroClaims manda ahí).
        if (com.tumod.protectormod.integration.ShipGuard.isInsideShip(level, pos)) {
            cancelarColocacion(level, pos, player, stack, "§c[!] No puedes colocar protecciones dentro de un ship. Usa Aeronautics Claims.");
            return;
        }

        if (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced()) {
            cancelarColocacion(level, pos, player, stack, "§c[!] No hay espacio suficiente.");
            return;
        }

        boolean isAdminCore = state.is(ModBlocks.ADMIN_PROTECTOR.get());
        ProtectionDataManager manager = ProtectionDataManager.get(sLevel);

        // Fase 7: límite por clan (pool) o personal (clanless).
        ClanSavedData clanData = ClanSavedData.get(sLevel);
        ClanSavedData.ClanInstance clan = clanData.getClanByMember(player.getUUID());

        if (!isAdminCore && !player.hasPermissions(2)) {
            if (clan != null) {
                if (!clan.hasProtectionSpace()) {
                    cancelarColocacion(level, pos, player, stack,
                            "§c[!] Tu clan alcanzó su límite de protecciones (" + clan.maxProtections + ").");
                    return;
                }
            } else {
                int clanlessLimit = com.tumod.protectormod.config.ProtectorConfig.CLANLESS_PROTECTIONS.get();
                long ownedCores = manager.getAllCores().values().stream()
                        .filter(entry -> entry.owner().equals(player.getUUID()))
                        .count();
                if (ownedCores >= clanlessLimit) {
                    cancelarColocacion(level, pos, player, stack,
                            "§c[!] Límite personal alcanzado (" + clanlessLimit + "). Crea o únete a un clan para colocar más.");
                    return;
                }
            }
        }

        if (!isAdminCore && !player.hasPermissions(2)) {
            int miRadioInicial = 10;
            boolean overlaps = manager.getAllCores().entrySet().stream()
                    .anyMatch(entry -> !entry.getKey().equals(pos) &&
                            Math.sqrt(pos.distSqr(entry.getKey())) < (miRadioInicial + entry.getValue().radius()));

            if (overlaps) {
                cancelarColocacion(level, pos, player, stack, "§c[!] El área choca con otra zona protegida.");
                return;
            }
        }

        BlockState upperState = state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(FACING, state.getValue(FACING));
        level.setBlock(pos.above(), upperState, 3);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ProtectionCoreBlockEntity core) {
            // Reset total: evita que un item con block_entity_data (pick-block de otro núcleo)
            // arrastre owner/clan/permisos ajenos. El colocador es siempre el nuevo dueño.
            core.resetForPlacement(player.getUUID(), player.getName().getString());
            // Fase 7: si el placer está en un clan, la protección pertenece al clan
            // (auto-permiso a todos los miembros por clanId) y consume del pool.
            if (clan != null && !isAdminCore) {
                core.setClan(clan.name, clan.clanId);
                clan.protectionsUsed++;
                clanData.setDirty();
            }
            manager.addCore(pos, player.getUUID(), core.getRadius());
            manager.syncToAll(sLevel);
        }

        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide && level instanceof ServerLevel sLevel) {
            BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;

            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockEntity be = level.getBlockEntity(basePos);
                if (be instanceof ProtectionCoreBlockEntity core) {
                    ProtectionDataManager.get(sLevel).removeCore(basePos);
                    ProtectionDataManager.get(sLevel).syncToAll(sLevel);

                    // Fase 7: romper una protección del clan libera un espacio del pool
                    // (NO disuelve el clan; eso es explícito con /clan delete).
                    java.util.UUID cid = core.getClanId();
                    if (cid != null) {
                        ClanSavedData clanData = ClanSavedData.get(sLevel);
                        ClanSavedData.ClanInstance owningClan = clanData.getClanById(cid);
                        if (owningClan != null && owningClan.protectionsUsed > 0) {
                            owningClan.protectionsUsed--;
                            clanData.setDirty();
                        }
                    }
                }
            }

            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(null, 2001, otherPos, Block.getId(otherState));
            }
        }

        if (state.hasBlockEntity() && (!state.is(newState.getBlock()) || !newState.hasBlockEntity())) {
            level.removeBlockEntity(pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos targetPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof ProtectionCoreBlockEntity core)) return InteractionResult.PASS;

        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            if (player.hasPermissions(2)) {
                player.openMenu(core, core.getBlockPos());
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.literal("§c[!] Solo administradores pueden configurar esto."), true);
            return InteractionResult.CONSUME;
        }

        if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2) || core.isTrusted(player) || core.isClanMember(player)) {
            core.markDirtyAndUpdate(); // refrescar el snapshot del clan sincronizado antes de abrir la GUI
            player.openMenu(core, core.getBlockPos());
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(Component.literal("§c[!] No tienes permisos de acceso."), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(LEVEL, 1);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private void cancelarColocacion(Level level, BlockPos pos, Player player, ItemStack stack, String mensaje) {
        player.displayClientMessage(Component.literal(mensaje), true);
        if (!player.getAbilities().instabuild) {
            ItemStack item = stack.copy();
            item.setCount(1);
            if (!player.getInventory().add(item)) player.drop(item, false);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            preventCreativeDropFromBottomPart(level, pos, state, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);

        // Dropear el item tanto si se rompe desde arriba como desde abajo (solo 1 vez, no duplicar)
        if (!level.isClientSide && !player.isCreative()) {
            popResource(level, pos, new ItemStack(this.asItem()));
        }
    }

    protected static void preventCreativeDropFromBottomPart(Level level, BlockPos pos, BlockState state, Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (half == DoubleBlockHalf.UPPER) {
            BlockPos blockpos = pos.below();
            BlockState blockstate = level.getBlockState(blockpos);
            if (blockstate.is(state.getBlock()) && blockstate.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, blockpos, Block.getId(blockstate));
            }
        }
    }
}
