package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public class AdminProtectorBlockEntity extends ProtectionCoreBlockEntity {

    public AdminProtectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADMIN_PROTECTOR_BE.get(), pos, state);
    }

    public boolean canBuild = false;

    @Override
    public int getRadius() {
        return this.adminRadius;
    }

    @Override
    public boolean isTrusted(Player player) {
        if (player.getUUID().equals(this.getOwnerUUID()) || player.hasPermissions(2)) return true;

        PlayerPermissions perms = this.permissionsMap.get(player.getUUID());
        if (perms != null && perms.canBuild) return true;

        if (this.level instanceof ServerLevel serverLevel && this.clanName != null && !this.clanName.isEmpty()) {
            ClanSavedData.ClanInstance clan = ClanSavedData.get(serverLevel).getClanByMember(player.getUUID());
            if (clan != null && clan.name.equalsIgnoreCase(this.clanName)) return true;
        }

        return false;
    }

    @Override
    public boolean isAdmin() {
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("CanBuild", this.canBuild);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.canBuild = tag.getBoolean("CanBuild");
    }

    @Override
    public boolean getFlag(String flag) {
        if (flag.equals("break")) {
            return super.getFlag("build");
        }
        return super.getFlag(flag);
    }

    @Override
    public void setAdminRadius(int newRadius) {
        super.setAdminRadius(newRadius);
        if (this.level instanceof ServerLevel serverLevel) {
            ProtectionDataManager.get(serverLevel).addCore(this.worldPosition, getOwnerUUID(), newRadius);
        }
    }
}
