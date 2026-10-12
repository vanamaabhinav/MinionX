package com.minionx.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for formatting chat colors and text formatting using '&' color codes.
 */
@SuppressWarnings("deprecation")
public class ColorUtil {

    public static final String PREFIX = "&6&l[Minion&c&lX&6&l] &r";
    public static final String RAW_NAME = "&6&lMinion&c&lX&r";

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static net.kyori.adventure.text.Component component(String text) {
        if (text == null) return net.kyori.adventure.text.Component.empty();
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(color(text));
    }

    public static String getPrefix() {
        if (com.minionx.MinionX.getInstance() != null && com.minionx.MinionX.getInstance().getConfig() != null) {
            return color(com.minionx.MinionX.getInstance().getConfig().getString("messages.prefix", PREFIX));
        }
        return color(PREFIX);
    }

    public static List<String> color(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> colored = new ArrayList<>(list.size());
        for (String line : list) {
            colored.add(color(line));
        }
        return colored;
    }
}