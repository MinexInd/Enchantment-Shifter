package net.minex.enchant.mixin;

import net.minex.enchant.configs.EnchantmentShifterConfigs;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilLogicModifier {
	@Shadow
	@Final
	private Property levelCost;

	// 0: no transfer, 1: book transfer, 2: item-to-item transfer
	private int transferType = 0;
	private ItemStack modifiedSource = ItemStack.EMPTY;
	private Set<Enchantment> transferredEnchantments = new HashSet<>();

	@ModifyVariable(at = @At("STORE"), method = "updateResult()V", ordinal = 1)
	private ItemStack changeIS(ItemStack itemStack2) {
		// Get input items from anvil slots
		Object[] input = ((AnvilScreenHandler) (Object) this).getStacks().toArray();
		ItemStack sourceItem = (ItemStack) input[0]; // Slot 0: Source item
		ItemStack targetItem = (ItemStack) input[1]; // Slot 1: Target item or book

		// Conditions for transfer types
		boolean bookTransfer = !sourceItem.getEnchantments().isEmpty() &&
				targetItem.isOf(Items.BOOK) &&
				targetItem.getCount() == 1;
		boolean itemToItemTransfer = !sourceItem.getEnchantments().isEmpty() &&
				!targetItem.isOf(Items.BOOK) &&
				!targetItem.isOf(Items.ENCHANTED_BOOK);

		// Reset fields for this update
		this.transferType = 0;
		this.modifiedSource = ItemStack.EMPTY;
		this.transferredEnchantments.clear();

		if (bookTransfer) {
			// Book Transfer Logic
			this.transferType = 1;
			this.modifiedSource = sourceItem.copy();
			Map<Enchantment, Integer> enchantments = EnchantmentHelper.get(sourceItem);
			ItemStack result = Items.ENCHANTED_BOOK.getDefaultStack();

			if (EnchantmentShifterConfigs.limit == 0) {
				EnchantmentHelper.set(enchantments, result);
			} else {
				NbtList nbtList = new NbtList();
				int count = 0;
				for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
					if (count >= EnchantmentShifterConfigs.limit) break;
					Enchantment enchantment = entry.getKey();
					int level = entry.getValue();
					nbtList.add(EnchantmentHelper.createNbt(EnchantmentHelper.getEnchantmentId(enchantment), level));
					EnchantedBookItem.addEnchantment(result, new EnchantmentLevelEntry(enchantment, level));
					count++;
				}
				if (!nbtList.isEmpty()) {
					result.setSubNbt("StoredEnchantments", nbtList);
				}
			}

			this.levelCost.set(Math.max((int) (enchantments.size() * EnchantmentShifterConfigs.costFactor), 1));
			if (EnchantmentShifterConfigs.fixedCost != 1000) {
				this.levelCost.set(EnchantmentShifterConfigs.fixedCost);
			}
			return result;

		} else if (itemToItemTransfer) {
			// Item-to-Item Transfer Logic
			Map<Enchantment, Integer> sourceEnchants = EnchantmentHelper.get(sourceItem);
			List<EnchantmentLevelEntry> transferable = new ArrayList<>();

			// Filter compatible enchantments
			for (Map.Entry<Enchantment, Integer> entry : sourceEnchants.entrySet()) {
				Enchantment ench = entry.getKey();
				int level = entry.getValue();
				if (ench.isAcceptableItem(targetItem)) {
					transferable.add(new EnchantmentLevelEntry(ench, level));
				}
			}

			// Apply limit if configured
			if (EnchantmentShifterConfigs.limit > 0 && transferable.size() > EnchantmentShifterConfigs.limit) {
				transferable = transferable.subList(0, EnchantmentShifterConfigs.limit);
			}

			// Combine with target item's enchantments
			Map<Enchantment, Integer> targetEnchants = new HashMap<>(EnchantmentHelper.get(targetItem));
			Set<Enchantment> transferred = new HashSet<>();

			for (EnchantmentLevelEntry entry : transferable) {
				Enchantment ench = entry.enchantment;
				int level = entry.level;
				boolean canApply = true;

				// Check compatibility with existing enchantments
				for (Enchantment existing : targetEnchants.keySet()) {
					if (!existing.canCombine(ench)) {
						canApply = false;
						break;
					}
				}

				if (canApply) {
					transferred.add(ench);
					if (targetEnchants.containsKey(ench)) {
						int existingLevel = targetEnchants.get(ench);
						if (level > existingLevel) {
							targetEnchants.put(ench, level);
						} else if (level == existingLevel && level < ench.getMaxLevel()) {
							targetEnchants.put(ench, level + 1);
						}
					} else {
						targetEnchants.put(ench, level);
					}
				}
			}

			// Set result only if enchantments were transferred
			if (!transferred.isEmpty()) {
				ItemStack result = targetItem.copy();
				EnchantmentHelper.set(targetEnchants, result);
				this.transferredEnchantments = transferred;
				this.transferType = 2;
				this.modifiedSource = sourceItem.copy();
				this.levelCost.set(Math.max((int) (transferred.size() * EnchantmentShifterConfigs.costFactor), 1));
				if (EnchantmentShifterConfigs.fixedCost != 1000) {
					this.levelCost.set(EnchantmentShifterConfigs.fixedCost);
				}
				return result;
			}
			return ItemStack.EMPTY;

		} else {
			// No transfer operation
			return itemStack2;
		}
	}

	@ModifyVariable(at = @At("STORE"), method = "updateResult()V", ordinal = 0)
	private boolean changeBL(boolean bl) {
		Object[] input = ((AnvilScreenHandler) (Object) this).getStacks().toArray();
		ItemStack sourceItem = (ItemStack) input[0];
		ItemStack targetItem = (ItemStack) input[1];

		boolean defaultLogic = targetItem.isOf(Items.ENCHANTED_BOOK) &&
				!EnchantedBookItem.getEnchantmentNbt(targetItem).isEmpty();
		boolean bookTransfer = !sourceItem.getEnchantments().isEmpty() &&
				targetItem.isOf(Items.BOOK);
		boolean itemToItemTransfer = !sourceItem.getEnchantments().isEmpty() &&
				!targetItem.isOf(Items.BOOK) &&
				!targetItem.isOf(Items.ENCHANTED_BOOK);

		return bl || defaultLogic || bookTransfer || itemToItemTransfer;
	}

	@Inject(at = @At("TAIL"), method = "onTakeOutput")
	private void setVanillaItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (this.transferType == 1 || this.transferType == 2) {
			if (this.transferType == 1) {
				// Book transfer: remove all enchantments
				EnchantmentHelper.set(Map.of(), this.modifiedSource);
			} else if (this.transferType == 2) {
				// Item-to-item transfer: remove only transferred enchantments
				Map<Enchantment, Integer> sourceEnchants = EnchantmentHelper.get(this.modifiedSource);
				for (Enchantment ench : this.transferredEnchantments) {
					sourceEnchants.remove(ench);
				}
				EnchantmentHelper.set(sourceEnchants, this.modifiedSource);
			}

			if (EnchantmentShifterConfigs.returnItem == 1) {
				player.giveItemStack(this.modifiedSource);
			}
		}
	}
}

