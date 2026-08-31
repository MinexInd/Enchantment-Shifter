package net.minex.enchant.mixin;

import net.minex.enchant.configs.EnchantmentShifterConfigs;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilLogicModifier {

	@Shadow
	@Final
	private Property levelCost;

	@Shadow
	protected Inventory input;

	@Shadow
	protected CraftingResultInventory output;

	@Shadow
	private boolean keepSecondSlot;

	@Shadow
	private int repairItemUsage;

	// 0 = none, 1 = item to book, 2 = item to item
	private int transferType = 0;
	private ItemStack modifiedSource = ItemStack.EMPTY;
	private Set<RegistryEntry<Enchantment>> transferredEnchantments = new HashSet<>();

	@Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
	private void onUpdateResult(CallbackInfo ci) {
		ItemStack sourceItem = this.input.getStack(0);
		ItemStack targetItem = this.input.getStack(1);

		ItemEnchantmentsComponent sourceEnchants = sourceItem.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		if (sourceEnchants.isEmpty()) {
			return;
		}

		boolean bookTransfer = targetItem.isOf(Items.BOOK) && targetItem.getCount() == 1;
		boolean itemTransfer = !targetItem.isOf(Items.BOOK) && !targetItem.isOf(Items.ENCHANTED_BOOK);

		this.transferType = 0;
		this.modifiedSource = ItemStack.EMPTY;
		this.transferredEnchantments.clear();

		if (bookTransfer) {
			ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
			int count = 0;
			for (var entry : sourceEnchants.getEnchantmentEntries()) {
				if (EnchantmentShifterConfigs.limit > 0 && count >= EnchantmentShifterConfigs.limit) break;
				builder.add(entry.getKey(), entry.getIntValue());
				count++;
			}

			ItemStack result = Items.ENCHANTED_BOOK.getDefaultStack();
			result.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());

			this.transferType = 1;
			this.modifiedSource = sourceItem.copy();
			this.keepSecondSlot = false;
			this.repairItemUsage = 0;
			this.levelCost.set(Math.max((int) (sourceEnchants.getSize() * EnchantmentShifterConfigs.costFactor), 1));
			if (EnchantmentShifterConfigs.fixedCost >= 0) {
				this.levelCost.set(EnchantmentShifterConfigs.fixedCost);
			}

			this.output.setStack(0, result);
			((ScreenHandler)(Object)this).sendContentUpdates();
			ci.cancel();
			return;
		}

		if (itemTransfer) {
			ItemEnchantmentsComponent targetEnchants = targetItem.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
			ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(targetEnchants);
			Set<RegistryEntry<Enchantment>> transferred = new HashSet<>();
			int transferredCount = 0;

			for (var entry : sourceEnchants.getEnchantmentEntries()) {
				if (EnchantmentShifterConfigs.limit > 0 && transferredCount >= EnchantmentShifterConfigs.limit) break;

				RegistryEntry<Enchantment> enchantment = entry.getKey();
				int level = entry.getIntValue();

				if (!enchantment.value().isAcceptableItem(targetItem)) continue;

				boolean compatible = true;
				for (var existing : targetEnchants.getEnchantmentEntries()) {
					if (!Enchantment.canBeCombined(enchantment, existing.getKey())) {
						compatible = false;
						break;
					}
				}
				if (!compatible) continue;

				transferred.add(enchantment);
				int existingLevel = targetEnchants.getLevel(enchantment);
				if (existingLevel > 0) {
					if (level > existingLevel) {
						builder.add(enchantment, level);
					} else if (level == existingLevel && level < enchantment.value().getMaxLevel()) {
						builder.add(enchantment, level + 1);
					}
				} else {
					builder.add(enchantment, level);
				}
				transferredCount++;
			}

			if (!transferred.isEmpty()) {
				ItemStack result = targetItem.copy();
				result.set(DataComponentTypes.ENCHANTMENTS, builder.build());

				this.transferredEnchantments = transferred;
				this.transferType = 2;
				this.modifiedSource = sourceItem.copy();
				this.keepSecondSlot = false;
				this.repairItemUsage = 0;
				this.levelCost.set(Math.max((int) (transferred.size() * EnchantmentShifterConfigs.costFactor), 1));
				if (EnchantmentShifterConfigs.fixedCost >= 0) {
					this.levelCost.set(EnchantmentShifterConfigs.fixedCost);
				}

				this.output.setStack(0, result);
				((ScreenHandler)(Object)this).sendContentUpdates();
				ci.cancel();
				return;
			}
		}
	}

	@Inject(at = @At("HEAD"), method = "onTakeOutput")
	private void onTakeOutput(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (this.transferType == 1 || this.transferType == 2) {
			if (this.transferType == 1) {
				this.modifiedSource.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
			} else if (this.transferType == 2) {
				ItemEnchantmentsComponent sourceEnchants = this.modifiedSource.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
				ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
				for (var entry : sourceEnchants.getEnchantmentEntries()) {
					if (!this.transferredEnchantments.contains(entry.getKey())) {
						builder.add(entry.getKey(), entry.getIntValue());
					}
				}
				this.modifiedSource.set(DataComponentTypes.ENCHANTMENTS, builder.build());
			}

			if (EnchantmentShifterConfigs.returnItem == 1) {
				this.input.getStack(0).setCount(0);
				player.giveItemStack(this.modifiedSource);
			}
		}
	}
}
