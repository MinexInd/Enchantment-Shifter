package net.minex.enchant.mixin;

import net.minex.enchant.configs.EnchantmentShifterConfigs;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(AnvilMenu.class)
public abstract class AnvilLogicModifier {

	@Shadow
	@Final
	private DataSlot cost;

	private Container getInput() {
		return ((ForgingAccessor)(Object)this).getInputSlots();
	}

	private ResultContainer getOutput() {
		return ((ForgingAccessor)(Object)this).getResultSlots();
	}

	@Shadow
	private int repairItemCountCost;

	private void setKeepSecondSlot(boolean v) {
		try {
			java.lang.reflect.Field f = AnvilMenu.class.getDeclaredField("keepSecondSlot");
			f.setAccessible(true);
			f.set(this, v);
		} catch (Exception ignored) {
		}
	}

	// 0 = none, 1 = item to book, 2 = item to item
	private int transferType = 0;
	private ItemStack modifiedSource = ItemStack.EMPTY;
	private Set<Holder<Enchantment>> transferredEnchantments = new HashSet<>();

	@Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
	private void onUpdateResult(CallbackInfo ci) {
		ItemStack sourceItem = getInput().getItem(0);
		ItemStack targetItem = getInput().getItem(1);

		ItemEnchantments sourceEnchants = sourceItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (sourceEnchants.isEmpty()) {
			return;
		}

		boolean bookTransfer = targetItem.is(Items.BOOK) && targetItem.getCount() == 1;
		boolean itemTransfer = !targetItem.is(Items.BOOK) && !targetItem.is(Items.ENCHANTED_BOOK);

		this.transferType = 0;
		this.modifiedSource = ItemStack.EMPTY;
		this.transferredEnchantments.clear();

		if (bookTransfer) {
			ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
			int count = 0;
			for (var entry : sourceEnchants.entrySet()) {
				if (EnchantmentShifterConfigs.limit > 0 && count >= EnchantmentShifterConfigs.limit) break;
				builder.upgrade(entry.getKey(), entry.getIntValue());
				count++;
			}

			ItemStack result = Items.ENCHANTED_BOOK.getDefaultInstance();
			result.set(DataComponents.STORED_ENCHANTMENTS, builder.toImmutable());

			this.transferType = 1;
			this.modifiedSource = sourceItem.copy();
			setKeepSecondSlot(false);
			this.repairItemCountCost = 0;
			this.cost.set(Math.max((int) (sourceEnchants.size() * EnchantmentShifterConfigs.costFactor), 1));
			if (EnchantmentShifterConfigs.fixedCost >= 0) {
				this.cost.set(EnchantmentShifterConfigs.fixedCost);
			}

			getOutput().setItem(0, result);
			((AbstractContainerMenu)(Object)this).broadcastChanges();
			ci.cancel();
			return;
		}

		if (itemTransfer) {
			ItemEnchantments targetEnchants = targetItem.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
			ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(targetEnchants);
			Set<Holder<Enchantment>> transferred = new HashSet<>();
			int transferredCount = 0;

			for (var entry : sourceEnchants.entrySet()) {
				if (EnchantmentShifterConfigs.limit > 0 && transferredCount >= EnchantmentShifterConfigs.limit) break;

				Holder<Enchantment> enchantment = entry.getKey();
				int level = entry.getIntValue();

				if (!enchantment.value().canEnchant(targetItem)) continue;

				boolean compatible = true;
				for (var existing : targetEnchants.entrySet()) {
					if (!Enchantment.areCompatible(enchantment, existing.getKey())) {
						compatible = false;
						break;
					}
				}
				if (!compatible) continue;

				transferred.add(enchantment);
				int existingLevel = targetEnchants.getLevel(enchantment);
				if (existingLevel > 0) {
					if (level > existingLevel) {
						builder.upgrade(enchantment, level);
					} else if (level == existingLevel && level < enchantment.value().getMaxLevel()) {
						builder.upgrade(enchantment, level + 1);
					}
				} else {
					builder.upgrade(enchantment, level);
				}
				transferredCount++;
			}

			if (!transferred.isEmpty()) {
				ItemStack result = targetItem.copy();
				result.set(DataComponents.ENCHANTMENTS, builder.toImmutable());

				this.transferredEnchantments = transferred;
				this.transferType = 2;
				this.modifiedSource = sourceItem.copy();
				setKeepSecondSlot(false);
				this.repairItemCountCost = 0;
				this.cost.set(Math.max((int) (transferred.size() * EnchantmentShifterConfigs.costFactor), 1));
				if (EnchantmentShifterConfigs.fixedCost >= 0) {
					this.cost.set(EnchantmentShifterConfigs.fixedCost);
				}

				getOutput().setItem(0, result);
				((AbstractContainerMenu)(Object)this).broadcastChanges();
				ci.cancel();
				return;
			}
		}
	}

	@Inject(at = @At("HEAD"), method = "onTake")
	private void onTakeOutput(Player player, ItemStack stack, CallbackInfo ci) {
		if (this.transferType == 1 || this.transferType == 2) {
			if (this.transferType == 1) {
				this.modifiedSource.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
			} else if (this.transferType == 2) {
				ItemEnchantments sourceEnchants = this.modifiedSource.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
				ItemEnchantments.Mutable builder = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
				for (var entry : sourceEnchants.entrySet()) {
					if (!this.transferredEnchantments.contains(entry.getKey())) {
						builder.upgrade(entry.getKey(), entry.getIntValue());
					}
				}
				this.modifiedSource.set(DataComponents.ENCHANTMENTS, builder.toImmutable());
			}

			if (EnchantmentShifterConfigs.returnItem == 1) {
				getInput().getItem(0).setCount(0);
				player.addItem(this.modifiedSource);
			}
		}
	}
}
