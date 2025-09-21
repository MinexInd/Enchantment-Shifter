package net.minex.enchant.mixin;

import net.minex.enchant.configs.EnchantmentShifterConfigs;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.entry.RegistryEntry;
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
	private Set<RegistryEntry<Enchantment>> transferredEnchantments = new HashSet<>();

	@ModifyVariable(at = @At("STORE"), method = "updateResult()V", ordinal = 1)
	private ItemStack changeIS(ItemStack itemStack2) {
		// Get input items from anvil slots
		Object[] input = ((AnvilScreenHandler) (Object) this).getStacks().toArray();
		ItemStack sourceItem = (ItemStack) input[0]; // Slot 0: Source item
		ItemStack targetItem = (ItemStack) input[1]; // Slot 1: Target item or book

		// Conditions for transfer types
		ItemEnchantmentsComponent sourceEnchants = sourceItem.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		boolean bookTransfer = !sourceEnchants.isEmpty() &&
				targetItem.isOf(Items.BOOK) &&
				targetItem.getCount() == 1;
		boolean itemToItemTransfer = !sourceEnchants.isEmpty() &&
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
			ItemStack result = Items.ENCHANTED_BOOK.getDefaultStack();
			
			ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
			int count = 0;
			
			for (var entry : sourceEnchants.getEnchantmentEntries()) {
				if (EnchantmentShifterConfigs.limit > 0 && count >= EnchantmentShifterConfigs.limit) break;
				builder.add(entry.getKey(), entry.getIntValue());
				count++;
			}
			
			result.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());

			this.levelCost.set(Math.max((int) (sourceEnchants.getSize() * EnchantmentShifterConfigs.costFactor), 1));
			if (EnchantmentShifterConfigs.fixedCost != 1000) {
				this.levelCost.set(EnchantmentShifterConfigs.fixedCost);
			}
			return result;

		} else if (itemToItemTransfer) {
			// Item-to-Item Transfer Logic
			ItemEnchantmentsComponent targetEnchants = targetItem.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
			ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(targetEnchants);
			Set<RegistryEntry<Enchantment>> transferred = new HashSet<>();
			int transferredCount = 0;

			// Transfer compatible enchantments
			for (var entry : sourceEnchants.getEnchantmentEntries()) {
				if (EnchantmentShifterConfigs.limit > 0 && transferredCount >= EnchantmentShifterConfigs.limit) break;
				
				RegistryEntry<Enchantment> enchantment = entry.getKey();
				int level = entry.getIntValue();
				
				// Check if enchantment is acceptable for target item
				if (enchantment.value().isAcceptableItem(targetItem)) {
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
			}

			// Set result only if enchantments were transferred
			if (!transferred.isEmpty()) {
				ItemStack result = targetItem.copy();
				result.set(DataComponentTypes.ENCHANTMENTS, builder.build());
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

		ItemEnchantmentsComponent targetStoredEnchants = targetItem.getOrDefault(DataComponentTypes.STORED_ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		ItemEnchantmentsComponent sourceEnchants = sourceItem.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		
		boolean defaultLogic = targetItem.isOf(Items.ENCHANTED_BOOK) && !targetStoredEnchants.isEmpty();
		boolean bookTransfer = !sourceEnchants.isEmpty() && targetItem.isOf(Items.BOOK);
		boolean itemToItemTransfer = !sourceEnchants.isEmpty() &&
				!targetItem.isOf(Items.BOOK) &&
				!targetItem.isOf(Items.ENCHANTED_BOOK);

		return bl || defaultLogic || bookTransfer || itemToItemTransfer;
	}

	@Inject(at = @At("TAIL"), method = "onTakeOutput")
	private void setVanillaItem(PlayerEntity player, ItemStack stack, CallbackInfo ci) {
		if (this.transferType == 1 || this.transferType == 2) {
			if (this.transferType == 1) {
				// Book transfer: remove all enchantments
				this.modifiedSource.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
			} else if (this.transferType == 2) {
				// Item-to-item transfer: remove only transferred enchantments
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
				player.giveItemStack(this.modifiedSource);
			}
		}
	}
}

