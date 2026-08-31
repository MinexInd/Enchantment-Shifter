package net.minex.enchant.configs;

import com.mojang.datafixers.util.Pair;

public class EnchantmentShifterConfigs {
    public static SimpleConfig CONFIG;
    private static ModConfigProvider configs;
    public static int fixedCost;
    public static double costFactor;
    public static int limit;
    public static int returnItem;

    public EnchantmentShifterConfigs() {
    }

    public static void registerConfigs() {
        configs = new ModConfigProvider();
        createConfigs();
        CONFIG = SimpleConfig.of("enchantment-transfer-config").provider(configs).request();
        assignConfigs();
    }

	private static void createConfigs() {
		configs.addKeyValuePair(new Pair("cost", -1), "Fixed XP cost for every transfer. Set to -1 to use the factor-based cost instead. Default: -1");
		configs.addKeyValuePair(new Pair("factor", 1.0), "Multiplier for XP cost. 0.5 = half cost, 2.0 = double cost. Default: 1.0");
		configs.addKeyValuePair(new Pair("limit", 0), "Max enchantments to transfer at once. 0 = no limit. Default: 0");
		configs.addKeyValuePair(new Pair("return", 1), "Source item behavior: 0 = vanish, 1 = return with enchantments removed. Default: 1");
	}

    private static void assignConfigs() {
        fixedCost = CONFIG.getOrDefault("cost", -1);
        costFactor = CONFIG.getOrDefault("factor", 1.0);
        limit = CONFIG.getOrDefault("limit", 0);
        returnItem = CONFIG.getOrDefault("return", 1);
    }
}
