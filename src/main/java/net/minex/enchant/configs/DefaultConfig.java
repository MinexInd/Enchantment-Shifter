package net.minex.enchant.configs;

public interface DefaultConfig {
    String get(String var1);

    static String empty(String namespace) {
        return "";
    }
}