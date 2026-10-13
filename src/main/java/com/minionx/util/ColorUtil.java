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
        if (com.minionx.MinionX.getInstance() != null) {
            String p = com.minionx.MinionX.getInstance().getMessage("prefix", PREFIX);
            if (p != null) return color(p);
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

    public static org.bukkit.Color parseColor(Object value) {
        if (value == null) return null;
        if (value instanceof org.bukkit.Color c) return c;
        if (value instanceof Number n) {
            return org.bukkit.Color.fromRGB(n.intValue() & 0xFFFFFF);
        }
        String str = value.toString().trim();
        if (str.isEmpty()) return null;

        if (str.startsWith("#")) {
            str = str.substring(1);
        } else if (str.toLowerCase().startsWith("0x")) {
            str = str.substring(2);
        }

        if (str.contains(",")) {
            String[] parts = str.split(",");
            if (parts.length >= 3) {
                try {
                    int r = Math.clamp(Integer.parseInt(parts[0].trim()), 0, 255);
                    int g = Math.clamp(Integer.parseInt(parts[1].trim()), 0, 255);
                    int b = Math.clamp(Integer.parseInt(parts[2].trim()), 0, 255);
                    return org.bukkit.Color.fromRGB(r, g, b);
                } catch (NumberFormatException ignored) {}
            }
        }

        if (str.length() == 6) {
            try {
                int rgb = Integer.parseInt(str, 16);
                return org.bukkit.Color.fromRGB(rgb);
            } catch (NumberFormatException ignored) {}
        } else if (str.length() == 3) {
            try {
                int r = Integer.parseInt(str.substring(0, 1) + str.substring(0, 1), 16);
                int g = Integer.parseInt(str.substring(1, 2) + str.substring(1, 2), 16);
                int b = Integer.parseInt(str.substring(2, 3) + str.substring(2, 3), 16);
                return org.bukkit.Color.fromRGB(r, g, b);
            } catch (NumberFormatException ignored) {}
        }

        return switch (str.toUpperCase()) {
            case "WHITE" -> org.bukkit.Color.WHITE;
            case "SILVER" -> org.bukkit.Color.SILVER;
            case "GRAY", "GREY" -> org.bukkit.Color.GRAY;
            case "BLACK" -> org.bukkit.Color.BLACK;
            case "RED" -> org.bukkit.Color.RED;
            case "MAROON" -> org.bukkit.Color.MAROON;
            case "YELLOW" -> org.bukkit.Color.YELLOW;
            case "OLIVE" -> org.bukkit.Color.OLIVE;
            case "LIME" -> org.bukkit.Color.LIME;
            case "GREEN" -> org.bukkit.Color.GREEN;
            case "AQUA", "CYAN" -> org.bukkit.Color.AQUA;
            case "TEAL" -> org.bukkit.Color.TEAL;
            case "BLUE" -> org.bukkit.Color.BLUE;
            case "NAVY" -> org.bukkit.Color.NAVY;
            case "FUCHSIA", "MAGENTA" -> org.bukkit.Color.FUCHSIA;
            case "PURPLE" -> org.bukkit.Color.PURPLE;
            case "ORANGE" -> org.bukkit.Color.ORANGE;
            case "GOLD" -> org.bukkit.Color.fromRGB(255, 170, 0);
            case "PINK" -> org.bukkit.Color.fromRGB(255, 105, 180);
            case "BROWN" -> org.bukkit.Color.fromRGB(139, 69, 19);
            default -> null;
        };
    }

    public static String toHexString(org.bukkit.Color color) {
        if (color == null) return "#FFFFFF";
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }
}