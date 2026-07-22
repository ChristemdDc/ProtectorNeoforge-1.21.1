package com.tumod.protectormod.menu;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.registry.ModItems;
import com.tumod.protectormod.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProtectionCoreMenu extends AbstractContainerMenu {
    private final ProtectionCoreBlockEntity core;
    private final ContainerLevelAccess access;
    private final Container container;

    public ProtectionCoreMenu(MenuType<?> type, int id, Inventory playerInv, Container container, ProtectionCoreBlockEntity core) {
        super(type, id);
        this.core = core;
        this.container = container;
        this.access = ContainerLevelAccess.create(core.getLevel(), core.getBlockPos());

        checkContainerSize(container, 2);

        this.addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return core.getCoreLevel();
            }

            @Override
            public void set(int value) {
                core.setCoreLevelClient(value);
            }
        });

        boolean isAdminCore = core.isAdmin();

        if (!isAdminCore) {
            this.addSlot(new Slot(container, 0, 15, 105) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.PROTECTION_UPGRADE.get());
                }
            });

            this.addSlot(new Slot(container, 1, 35, 105) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return switch (core.getCoreLevel()) {
                        case 1 -> stack.is(Items.IRON_INGOT);
                        case 2 -> stack.is(Items.GOLD_INGOT);
                        case 3 -> stack.is(Items.DIAMOND);
                        case 4 -> stack.is(Items.NETHERITE_INGOT);
                        default -> false;
                    };
                }
            });

            addPlayerInventory(playerInv, 8, 140);
            addPlayerHotbar(playerInv, 8, 198);
        } else {
            addPlayerInventory(playerInv, 8, 140);
            addPlayerHotbar(playerInv, 8, 198);
        }
    }

    private void addPlayerInventory(Inventory inv, int xStart, int yStart) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, xStart + col * 18, yStart + row * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory inv, int xStart, int yStart) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, xStart + col * 18, yStart));
        }
    }

    // Constructor cliente (extended menu factory de NeoForge).
    public ProtectionCoreMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(ModMenus.PROTECTION_CORE_MENU.get(), id, playerInv, new SimpleContainer(2), getBlockEntity(playerInv, buf));
    }

    public static ProtectionCoreMenu createAdminMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        return new ProtectionCoreMenu(ModMenus.ADMIN_CORE_MENU.get(), id, playerInv, new SimpleContainer(2), getBlockEntity(playerInv, buf));
    }

    public static ProtectionCoreBlockEntity getBlockEntity(Inventory inv, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        var be = inv.player.level().getBlockEntity(pos);

        if (be instanceof ProtectionCoreBlockEntity coreBE) {
            return coreBE;
        }
        throw new IllegalStateException("BlockEntity at " + pos + " is missing!");
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.PROTECTION_CORE.get())
                || stillValid(this.access, player, ModBlocks.ADMIN_PROTECTOR.get())
                || stillValid(this.access, player, ModBlocks.MECHANICAL_PROTECTOR.get());
    }

    public ProtectionCoreBlockEntity getBlockEntity() {
        return this.core;
    }

    public ProtectionCoreBlockEntity getCore() {
        return this.core;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        boolean isAdmin = core.getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get());
        int containerSlots = isAdmin ? 0 : 2;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < containerSlots) {
                if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (containerSlots > 0) {
                    if (!this.moveItemStackTo(stack, 0, containerSlots, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}
