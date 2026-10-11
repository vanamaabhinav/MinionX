package com.skyblock.minions.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility for formatting chat colors and text formatting using '&' color codes.
 */
public class ColorUtil {

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
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