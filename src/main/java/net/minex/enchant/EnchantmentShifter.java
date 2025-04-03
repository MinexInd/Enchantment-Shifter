package net.minex.enchant;

import net.minex.enchant.configs.EnchantmentShifterConfigs;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantmentShifter implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("enchantment-transfer");
	public static final String MOD_ID = "enchantment-shifter";

	public EnchantmentShifter() {
	}

	public void onInitialize() {
		EnchantmentShifterConfigs.registerConfigs();
	}
}
