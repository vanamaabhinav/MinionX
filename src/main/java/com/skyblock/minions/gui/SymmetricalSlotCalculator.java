package com.skyblock.minions.gui;

import com.skyblock.minions.SkyBlockMinions;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;

/**
 * Calculates symmetrical 0-indexed slot layouts in a 27-slot chest (3 rows x 9 columns)
 * centered at Slot 13 (row 1, column 4).
 * Overridable from config.yml under 'storage-layouts'.
 */
public class SymmetricalSlotCalculator {

    private static final Map<Integer, List<Integer>> BUILTIN_LAYOUTS = new HashMap<>();

    static {
        // Pre-defined symmetrical progressions centered around Slot 13 (indices 0 to 26)
        BUILTIN_LAYOUTS.put(1, List.of(13));
        BUILTIN_LAYOUTS.put(2, List.of(12, 14));
        BUILTIN_LAYOUTS.put(3, List.of(12, 13, 14)); // Hypixel Small Storage standard
        BUILTIN_LAYOUTS.put(4, List.of(11, 12, 14, 15));
        BUILTIN_LAYOUTS.put(5, List.of(11, 12, 13, 14, 15));
        BUILTIN_LAYOUTS.put(6, List.of(10, 11, 12, 14, 15, 16));
        BUILTIN_LAYOUTS.put(7, List.of(10, 11, 12, 13, 14, 15, 16));
        BUILTIN_LAYOUTS.put(8, List.of(3, 5, 11, 12, 14, 15, 21, 23));
        BUILTIN_LAYOUTS.put(9, List.of(3, 4, 5, 12, 13, 14, 21, 22, 23)); // Hypixel Medium Storage 3x3 square
        BUILTIN_LAYOUTS.put(10, List.of(3, 4, 5, 11, 12, 13, 14, 15, 21, 22, 23));
        BUILTIN_LAYOUTS.put(11, List.of(2, 3, 4, 5, 6, 12, 13, 14, 20, 21, 22, 23, 24));
        BUILTIN_LAYOUTS.put(12, List.of(3, 4, 5, 10, 11, 12, 14, 15, 16, 21, 22, 23));
        BUILTIN_LAYOUTS.put(13, List.of(3, 4, 5, 10, 11, 12, 13, 14, 15, 16, 21, 22, 23));
        BUILTIN_LAYOUTS.put(14, List.of(2, 3, 4, 5, 6, 11, 12, 14, 15, 20, 21, 22, 23, 24));
        BUILTIN_LAYOUTS.put(15, List.of(2, 3, 4, 5, 6, 11, 12, 13, 14, 15, 20, 21, 22, 23, 24)); // Hypixel Large Storage 3x5 grid
        BUILTIN_LAYOUTS.put(16, List.of(2, 3, 4, 5, 6, 10, 11, 12, 14, 15, 16, 20, 21, 22, 23, 24));
        BUILTIN_LAYOUTS.put(17, List.of(2, 3, 4, 5, 6, 10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24));
        BUILTIN_LAYOUTS.put(18, List.of(1, 2, 3, 4, 5, 6, 7, 11, 12, 14, 15, 19, 20, 21, 22, 23, 24, 25));
        BUILTIN_LAYOUTS.put(19, List.of(1, 2, 3, 4, 5, 6, 7, 11, 12, 13, 14, 15, 19, 20, 21, 22, 23, 24, 25));
        BUILTIN_LAYOUTS.put(20, List.of(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25));
        BUILTIN_LAYOUTS.put(21, List.of(1, 2, 3, 4, 5, 6, 7, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25));
        BUILTIN_LAYOUTS.put(22, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26));
        BUILTIN_LAYOUTS.put(23, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 11, 12, 13, 14, 15, 18, 19, 20, 21, 22, 23, 24, 25, 26));
        BUILTIN_LAYOUTS.put(24, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 14, 15, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26));
        BUILTIN_LAYOUTS.put(25, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 18, 19, 20, 21, 22, 23, 24, 25, 26));
        BUILTIN_LAYOUTS.put(26, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26));
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < 27; i++) all.add(i);
        BUILTIN_LAYOUTS.put(27, Collections.unmodifiableList(all));
    }

    public static List<Integer> getSlots(int capacity) {
        int clamped = Math.clamp(capacity, 1, 27);

        // Check config override
        try {
            if (SkyBlockMinions.getInstance() != null) {
                FileConfiguration cfg = SkyBlockMinions.getInstance().getConfig();
                if (cfg != null && cfg.isList("storage-layouts." + clamped)) {
                    List<Integer> custom = cfg.getIntegerList("storage-layouts." + clamped);
                    if (!custom.isEmpty() && custom.size() == clamped) {
                        return custom;
                    }
                }
            }
        } catch (Throwable ignored) {}

        List<Integer> builtin = BUILTIN_LAYOUTS.get(clamped);
        if (builtin != null) {
            return builtin;
        }

        // Fallback default
        List<Integer> fallback = new ArrayList<>();
        for (int i = 0; i < clamped; i++) fallback.add(i);
        return fallback;
    }

    public static boolean isSlotUnlocked(int slotIndex, int capacity) {
        return getSlots(capacity).contains(slotIndex);
    }
}
