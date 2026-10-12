package com.minionx.gui;

import com.minionx.MinionX;
import com.minionx.api.MinionArchetype;
import com.minionx.api.MinionDefinition;
import com.minionx.api.MinionRegistry;
import com.minionx.item.MinionItemFactory;
import com.minionx.util.ColorUtil;
import com.minionx.api.CustomFuel;
import com.minionx.api.FuelRegistry;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.minionx.api.CustomMinionStorage;
import com.minionx.api.MinionSkin;
import com.minionx.api.MinionSkinRegistry;
import com.minionx.api.MinionStorageRegistry;


import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles interactions in the Minion Creator Studio GUI and captures chat input prompts.
 */
@SuppressWarnings("deprecation")
public class MinionCreatorListener implements Listener {

    public static final Map<UUID, Consumer<String>> PENDING_PROMPTS = new ConcurrentHashMap<>();

    private static final Material[] ARMOR_PRESETS = {
            Material.LEATHER_CHESTPLATE,
            Material.IRON_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE,
            Material.NETHERITE_CHESTPLATE,
            Material.GOLDEN_CHESTPLATE,
            Material.CHAINMAIL_CHESTPLATE
    };

    private static final Color[] COLOR_PRESETS = {
            Color.fromRGB(120, 120, 120), // Gray
            Color.fromRGB(80, 220, 230),  // Diamond Cyan
            Color.fromRGB(50, 200, 50),   // Emerald Green
            Color.fromRGB(220, 50, 50),   // Crimson Red
            Color.fromRGB(240, 180, 40),  // Gold Yellow
            Color.fromRGB(160, 50, 220),  // Amethyst Purple
            Color.fromRGB(230, 230, 230)  // Iron White
    };

    private static final Material[] MINER_ORES = {
            Material.COBBLESTONE,
            Material.COAL_ORE,
            Material.IRON_ORE,
            Material.GOLD_ORE,
            Material.DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.ANCIENT_DEBRIS,
            Material.OBSIDIAN,
            Material.GLOWSTONE
    };

    private static final String[] COMBAT_MOBS = {
            "ZOMBIE",
            "SKELETON",
            "SPIDER",
            "CAVE_SPIDER",
            "CREEPER",
            "ENDERMAN",
            "BLAZE",
            "SLIME",
            "MAGMA_CUBE",
            "GHAST",
            "WITCH",
            "WITHER_SKELETON"
    };

    private static final Material[] FORAGING_LOGS = {
            Material.OAK_LOG,
            Material.BIRCH_LOG,
            Material.SPRUCE_LOG,
            Material.DARK_OAK_LOG,
            Material.ACACIA_LOG,
            Material.JUNGLE_LOG,
            Material.MANGROVE_LOG,
            Material.CHERRY_LOG
    };

    private static final Material[] FARMING_CROPS = {
            Material.WHEAT,
            Material.CARROT,
            Material.POTATO,
            Material.PUMPKIN,
            Material.MELON,
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.NETHER_WART,
            Material.RED_MUSHROOM,
            Material.BROWN_MUSHROOM
    };

    private static final String[] FARMING_ANIMALS = {
            "COW",
            "PIG",
            "CHICKEN",
            "SHEEP",
            "RABBIT"
    };

    private static final String[] SKULL_PRESETS = {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTRhMjQzZTU1OWU5OWExMGJmMWFlNGU2YmFmYmY0MTA1ODhhMTVhM2Q2YzVjOGVmMjljMWE4YmRiNWYzMWFjYyJ9fX0=", // Cobble
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWY1ODMwNDk0YjI2YjhhOGFmNmYwMmQ2OTM4YzA1Y2JiZWIzNTViNmY4YjY5MWQxODliZjc2MmFiNDY2OWZkIn19fQ==", // Diamond
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTE0ZWU1NmY3YmU0ZjY4NmJmODQ4NTVmNmU0OTZlNDllNDBiNDM2NWY4NmY4NmZkYmVjYjE0YWY5ZjhiMmU1In19fQ==", // Iron
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTY1N2NkNWMyOTg5ZmExNjU3ZjEsNWQ5NDY5OWM3ZjkzNmJjNDUxNzFhMTVlYTc4MDQ5MDEwMWVlODc0OWQifX19", // Zombie
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTRiZTZkMDg4MTY5YmVhOGVhNTI5MmcxODlmNTRiNDY0MmE2MTM5MDQ1YzUxNWNmYmM1NzYxODFiNWFhIn19fQ==", // Oak
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTlhZGNlOWViYjE2ZTY2MTRmMTQ2YzA4NzM5N2E2ZTU5Y2Y4NDM1YWU4NzMzODIxNjhkOTc3Y2RjZTkxZCJ9fX0="  // Wheat
    };

    private static final double[] CHANCE_PRESETS = {0.05, 0.10, 0.25, 0.50, 1.00};

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MinionSkinCreatorHolder skinHolder) {
            handleSkinCreatorClick(event, (Player) event.getWhoClicked(), skinHolder.getSession());
            return;
        }
        if (event.getInventory().getHolder() instanceof MinionStorageCreatorHolder storageHolder) {
            handleStorageCreatorClick(event, (Player) event.getWhoClicked(), storageHolder.getSession());
            return;
        }
        if (event.getInventory().getHolder() instanceof FuelCreatorHolder fuelHolder) {
            handleFuelCreatorClick(event, (Player) event.getWhoClicked(), fuelHolder.getSession());
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MinionCreatorHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        MinionCreatorSession session = holder.getSession();
        ItemStack cursor = event.getCursor();

        // 1. Minion Identity (Name & ID)
        if (slot == 2) {
            promptChat(player, session, "Enter minion ID & Display Name format: 'minion_id &aMy Minion' (or 'cancel'):", input -> {
                String[] parts = input.trim().split(" ", 2);
                if (parts.length > 0 && !parts[0].isEmpty()) {
                    session.setId(parts[0].toLowerCase());
                }
                if (parts.length > 1 && !parts[1].isEmpty()) {
                    session.setDisplayName(ColorUtil.color(parts[1]));
                }
            });
            return;
        }

        // 2. Archetype Cycle (MINER -> COMBAT -> FORAGING -> FARMING -> MINER)
        if (slot == 10) {
            MinionArchetype next;
            switch (session.getArchetype()) {
                case MINER -> {
                    next = MinionArchetype.COMBAT;
                    session.setHandItem(new ItemStack(Material.IRON_SWORD));
                    session.setTargetMob("ZOMBIE");
                    session.setTargetBlock(null);
                    session.setExactDropItem(new ItemStack(Material.ROTTEN_FLESH));
                    session.setSecondaryDropItem(null);
                    session.setSecondaryDropChance(0.0);
                }
                case COMBAT -> {
                    next = MinionArchetype.FORAGING;
                    session.setHandItem(new ItemStack(Material.IRON_AXE));
                    session.setTargetBlock(Material.OAK_LOG);
                    session.setTargetMob(null);
                    session.setExactDropItem(new ItemStack(Material.OAK_LOG));
                    session.setSecondaryDropItem(new ItemStack(Material.APPLE));
                    session.setSecondaryDropChance(0.05);
                }
                case FORAGING -> {
                    next = MinionArchetype.FARMING;
                    session.setHandItem(new ItemStack(Material.DIAMOND_HOE));
                    session.setTargetBlock(Material.WHEAT);
                    session.setTargetMob(null);
                    session.setExactDropItem(new ItemStack(Material.WHEAT));
                    session.setSecondaryDropItem(new ItemStack(Material.WHEAT_SEEDS));
                    session.setSecondaryDropChance(1.0);
                }
                case FARMING -> {
                    next = MinionArchetype.MINER;
                    session.setHandItem(new ItemStack(Material.STONE_PICKAXE));
                    session.setTargetBlock(Material.COBBLESTONE);
                    session.setTargetMob(null);
                    session.setExactDropItem(new ItemStack(Material.COBBLESTONE));
                    session.setSecondaryDropItem(null);
                    session.setSecondaryDropChance(0.0);
                }
                default -> next = MinionArchetype.MINER;
            }
            session.setArchetype(next);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            MinionCreatorGUI.open(player, session);
            return;
        }

        // 3. Skull Texture
        if (slot == 12) {
            if (cursor != null && cursor.getType() == Material.PLAYER_HEAD) {
                SkullMeta sMeta = (SkullMeta) cursor.getItemMeta();
                if (sMeta != null && sMeta.getPlayerProfile() != null) {
                    for (var prop : sMeta.getPlayerProfile().getProperties()) {
                        if ("textures".equalsIgnoreCase(prop.getName())) {
                            session.setSkullTexture(prop.getValue());
                            break;
                        }
                    }
                }
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aCopied skull texture from held head."));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
                return;
            }

            if (event.isRightClick()) {
                int current = 0;
                for (int i = 0; i < SKULL_PRESETS.length; i++) {
                    if (SKULL_PRESETS[i].equals(session.getSkullTexture())) {
                        current = i;
                        break;
                    }
                }
                int next = (current + 1) % SKULL_PRESETS.length;
                session.setSkullTexture(SKULL_PRESETS[next]);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
            } else {
                promptChat(player, session, "Enter Minecraft Player Name or Base64 Skin Texture (or 'cancel'):", input -> {
                    session.setSkullTexture(input.trim());
                });
            }
            return;
        }

        // 4. Armor & Color
        if (slot == 14) {
            if (cursor != null && cursor.getType() == Material.LEATHER_CHESTPLATE && cursor.getItemMeta() instanceof LeatherArmorMeta lm) {
                session.setArmorMaterial(Material.LEATHER_CHESTPLATE);
                session.setArmorColor(lm.getColor());
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aApplied custom leather color from cursor."));
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
                return;
            }

            if (event.isRightClick()) {
                int cur = 0;
                for (int i = 0; i < COLOR_PRESETS.length; i++) {
                    if (COLOR_PRESETS[i].equals(session.getArmorColor())) {
                        cur = i;
                        break;
                    }
                }
                int next = (cur + 1) % COLOR_PRESETS.length;
                session.setArmorColor(COLOR_PRESETS[next]);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            } else {
                int cur = 0;
                for (int i = 0; i < ARMOR_PRESETS.length; i++) {
                    if (ARMOR_PRESETS[i] == session.getArmorMaterial()) {
                        cur = i;
                        break;
                    }
                }
                int next = (cur + 1) % ARMOR_PRESETS.length;
                session.setArmorMaterial(ARMOR_PRESETS[next]);
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1.0f, 1.2f);
            }
            MinionCreatorGUI.open(player, session);
            return;
        }

        // 5. Hand Item
        if (slot == 16) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.setHandItem(cursor.clone());
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aEquipped " + cursor.getType().name() + " in minion's hand."));
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
            }
            return;
        }

        // 6. Target (Block, Mob, Wood, Crop, Animal)
        if (slot == 28) {
            if (session.getArchetype() == MinionArchetype.MINER) {
                if (cursor != null && cursor.getType().isBlock() && cursor.getType().isSolid()) {
                    session.setTargetBlock(cursor.getType());
                    session.setTargetMob(null);
                    session.setExactDropItem(new ItemStack(cursor.getType()));
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSet mining target to " + cursor.getType().name() + "."));
                    player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.2f);
                    MinionCreatorGUI.open(player, session);
                    return;
                }
                int cur = 0;
                for (int i = 0; i < MINER_ORES.length; i++) {
                    if (MINER_ORES[i] == session.getTargetBlock()) {
                        cur = i;
                        break;
                    }
                }
                int next = (cur + 1) % MINER_ORES.length;
                session.setTargetBlock(MINER_ORES[next]);
                session.setTargetMob(null);
                session.setExactDropItem(new ItemStack(MINER_ORES[next]));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
            } else if (session.getArchetype() == MinionArchetype.COMBAT) {
                if (event.isRightClick()) {
                    promptChat(player, session, "Enter MythicMob ID or custom EntityType (e.g. 'SkeletalKnight' or 'WITHER_SKELETON'):", input -> {
                        session.setTargetMob(input.trim());
                        session.setTargetBlock(null);
                    });
                } else {
                    int cur = 0;
                    for (int i = 0; i < COMBAT_MOBS.length; i++) {
                        if (COMBAT_MOBS[i].equalsIgnoreCase(session.getTargetMob())) {
                            cur = i;
                            break;
                        }
                    }
                    int next = (cur + 1) % COMBAT_MOBS.length;
                    session.setTargetMob(COMBAT_MOBS[next]);
                    session.setTargetBlock(null);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    MinionCreatorGUI.open(player, session);
                }
            } else if (session.getArchetype() == MinionArchetype.FORAGING) {
                if (cursor != null && cursor.getType().isBlock()) {
                    session.setTargetBlock(cursor.getType());
                    session.setTargetMob(null);
                    session.setExactDropItem(cursor.clone());
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSet foraging wood target to " + cursor.getType().name() + "."));
                    player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.2f);
                    MinionCreatorGUI.open(player, session);
                    return;
                }
                int cur = 0;
                for (int i = 0; i < FORAGING_LOGS.length; i++) {
                    if (FORAGING_LOGS[i] == session.getTargetBlock()) {
                        cur = i;
                        break;
                    }
                }
                int next = (cur + 1) % FORAGING_LOGS.length;
                session.setTargetBlock(FORAGING_LOGS[next]);
                session.setTargetMob(null);
                session.setExactDropItem(new ItemStack(FORAGING_LOGS[next]));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
            } else {
                // FARMING & HUSBANDRY
                if (cursor != null && cursor.getType() != Material.AIR) {
                    session.setTargetBlock(cursor.getType());
                    session.setTargetMob(null);
                    session.setExactDropItem(cursor.clone());
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSet farming target to " + cursor.getType().name() + "."));
                    player.playSound(player.getLocation(), Sound.BLOCK_GRASS_PLACE, 1.0f, 1.2f);
                    MinionCreatorGUI.open(player, session);
                    return;
                }
                if (event.isRightClick()) {
                    promptChat(player, session, "Enter custom Animal EntityType (e.g. 'COW', 'SHEEP', 'PIG') or 'cancel':", input -> {
                        session.setTargetMob(input.trim().toUpperCase());
                        session.setTargetBlock(null);
                    });
                } else {
                    // Cycle through crops, then animals
                    int totalOptions = FARMING_CROPS.length + FARMING_ANIMALS.length;
                    int curIdx = 0;
                    if (session.getTargetMob() != null) {
                        for (int i = 0; i < FARMING_ANIMALS.length; i++) {
                            if (FARMING_ANIMALS[i].equalsIgnoreCase(session.getTargetMob())) {
                                curIdx = FARMING_CROPS.length + i;
                                break;
                            }
                        }
                    } else if (session.getTargetBlock() != null) {
                        for (int i = 0; i < FARMING_CROPS.length; i++) {
                            if (FARMING_CROPS[i] == session.getTargetBlock()) {
                                curIdx = i;
                                break;
                            }
                        }
                    }
                    int nextIdx = (curIdx + 1) % totalOptions;
                    if (nextIdx < FARMING_CROPS.length) {
                        Material crop = FARMING_CROPS[nextIdx];
                        session.setTargetBlock(crop);
                        session.setTargetMob(null);
                        session.setExactDropItem(new ItemStack(crop));
                        if (crop == Material.WHEAT) {
                            session.setSecondaryDropItem(new ItemStack(Material.WHEAT_SEEDS));
                            session.setSecondaryDropChance(1.0);
                        } else if (crop == Material.POTATO) {
                            session.setSecondaryDropItem(new ItemStack(Material.POISONOUS_POTATO));
                            session.setSecondaryDropChance(0.02);
                        } else {
                            session.setSecondaryDropItem(null);
                            session.setSecondaryDropChance(0.0);
                        }
                    } else {
                        String anim = FARMING_ANIMALS[nextIdx - FARMING_CROPS.length];
                        session.setTargetMob(anim);
                        session.setTargetBlock(null);
                        if ("COW".equalsIgnoreCase(anim)) {
                            session.setExactDropItem(new ItemStack(Material.BEEF));
                            session.setSecondaryDropItem(new ItemStack(Material.LEATHER));
                            session.setSecondaryDropChance(0.5);
                        } else if ("PIG".equalsIgnoreCase(anim)) {
                            session.setExactDropItem(new ItemStack(Material.PORKCHOP));
                            session.setSecondaryDropItem(null);
                            session.setSecondaryDropChance(0.0);
                        } else if ("CHICKEN".equalsIgnoreCase(anim)) {
                            session.setExactDropItem(new ItemStack(Material.CHICKEN));
                            session.setSecondaryDropItem(new ItemStack(Material.FEATHER));
                            session.setSecondaryDropChance(0.5);
                        } else if ("SHEEP".equalsIgnoreCase(anim)) {
                            session.setExactDropItem(new ItemStack(Material.MUTTON));
                            session.setSecondaryDropItem(new ItemStack(Material.WHITE_WOOL));
                            session.setSecondaryDropChance(1.0);
                        } else if ("RABBIT".equalsIgnoreCase(anim)) {
                            session.setExactDropItem(new ItemStack(Material.RABBIT));
                            session.setSecondaryDropItem(new ItemStack(Material.RABBIT_HIDE));
                            session.setSecondaryDropChance(0.5);
                        }
                    }
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    MinionCreatorGUI.open(player, session);
                }
            }
            return;
        }

        // 7. Exact Primary Drop Item
        if (slot == 30) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.setExactDropItem(cursor.clone());
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSet exact primary drop item clone with all custom attributes!"));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.4f);
                MinionCreatorGUI.open(player, session);
            }
            return;
        }

        // 8. Secondary Drop Item & Probability
        if (slot == 31) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.setSecondaryDropItem(cursor.clone());
                if (session.getSecondaryDropChance() <= 0.0) {
                    session.setSecondaryDropChance(0.10);
                }
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSet secondary drop item with " + (int) Math.round(session.getSecondaryDropChance() * 100) + "% chance!"));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.4f);
                MinionCreatorGUI.open(player, session);
                return;
            }

            if (session.getSecondaryDropItem() != null) {
                if (event.isRightClick()) {
                    session.setSecondaryDropItem(null);
                    session.setSecondaryDropChance(0.0);
                    player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&eRemoved secondary drop item."));
                    player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 1.2f);
                } else {
                    int curIdx = 0;
                    for (int i = 0; i < CHANCE_PRESETS.length; i++) {
                        if (Math.abs(CHANCE_PRESETS[i] - session.getSecondaryDropChance()) < 0.01) {
                            curIdx = i;
                            break;
                        }
                    }
                    int nextIdx = (curIdx + 1) % CHANCE_PRESETS.length;
                    session.setSecondaryDropChance(CHANCE_PRESETS[nextIdx]);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                }
                MinionCreatorGUI.open(player, session);
            }
            return;
        }

        // 9. Speed, Storage & XP Tuning
        if (slot == 32) {
            if (event.isShiftClick()) {
                if (event.isLeftClick()) {
                    session.setXpPerHarvest(session.getXpPerHarvest() + 1);
                } else {
                    session.setStorageSlots(session.getStorageSlots() >= 15 ? 1 : session.getStorageSlots() + 1);
                }
            } else {
                if (event.isLeftClick()) {
                    session.setActionDelay(Math.max(2.0, session.getActionDelay() - 1.0));
                } else {
                    session.setActionDelay(Math.min(60.0, session.getActionDelay() + 1.0));
                }
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            MinionCreatorGUI.open(player, session);
            return;
        }

        // 9.5 Max Tier Cap Tuning (Slot 33)
        if (slot == 33) {
            int currentMax = session.getMaxTier();
            if (event.isLeftClick()) {
                session.setMaxTier(Math.min(com.minionx.api.MinionTier.getMaxTier(), currentMax + 1));
            } else if (event.isRightClick()) {
                session.setMaxTier(Math.max(1, currentMax - 1));
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
            MinionCreatorGUI.open(player, session);
            return;
        }

        // 10. Tier Customization Mode
        if (slot == 34) {
            if (event.isRightClick()) {
                session.setSelectedTier(session.getSelectedTier() >= 11 ? 1 : session.getSelectedTier() + 1);
            } else {
                session.setPerTierMode(!session.isPerTierMode());
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            MinionCreatorGUI.open(player, session);
            return;
        }

        // 10.5 Open Per-Minion Tier Studio (Slot 35)
        if (slot == 35) {
            com.minionx.api.MinionDefinition def = com.minionx.api.MinionRegistry.get(session.getId());
            if (def == null) {
                def = session.buildDefinition();
                com.minionx.api.MinionRegistry.register(def);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            TierEditorGUI.openOverview(player, def.getId());
            return;
        }

        // 11. Cancel & Return
        if (slot == 48) {
            player.closeInventory();
            OperatorGUI.openMain(player);
            return;
        }

        // 12. Save & Deploy (Tier I)
        if (slot == 49) {
            saveAndDeploy(player, session, 1);
            return;
        }

        // 13. Deploy Maxed
        if (slot == 50) {
            saveAndDeploy(player, session, session.getMaxTier());
        }
    }

    private void saveAndDeploy(Player player, MinionCreatorSession session, int tier) {
        player.closeInventory();
        MinionDefinition def = session.buildDefinition();
        MinionRegistry.saveCustomMinion(def);

        ItemStack item = MinionItemFactory.createCustomMinionItem(def, tier, 0);
        player.getInventory().addItem(item);

        player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aCreated custom minion &f" + def.getDisplayName() + " &aand saved to minions.yml!"));
        player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&7Received Tier " + tier + " spawn item. Right-click to place!"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void promptChat(Player player, MinionCreatorSession session, String message, Consumer<String> action) {
        player.closeInventory();
        player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&e" + message));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        PENDING_PROMPTS.put(player.getUniqueId(), input -> {
            if (!"cancel".equalsIgnoreCase(input.trim())) {
                action.accept(input);
            }
            Bukkit.getScheduler().runTask(MinionX.getInstance(), () -> {
                MinionCreatorGUI.open(player, session);
            });
        });
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Consumer<String> callback = PENDING_PROMPTS.remove(player.getUniqueId());
        if (callback != null) {
            event.setCancelled(true);
            callback.accept(event.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        PENDING_PROMPTS.remove(event.getPlayer().getUniqueId());
    }

    private void handleSkinCreatorClick(InventoryClickEvent event, Player player, MinionSkinCreatorSession session) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ItemStack cursor = event.getCursor();

        if (slot == 2) {
            promptChat(player, () -> MinionSkinCreatorGUI.open(player, session),
                    "Enter Skin ID & Display Name: 'skin_id &6My Skin' (or 'cancel'):", input -> {
                String[] parts = input.trim().split(" ", 2);
                if (parts.length > 0 && !parts[0].isEmpty()) session.setId(parts[0].toLowerCase());
                if (parts.length > 1 && !parts[1].isEmpty()) session.setDisplayName(ColorUtil.color(parts[1]));
            });
            return;
        }

        if (slot == 10) {
            if (cursor != null && cursor.getType() == Material.PLAYER_HEAD) {
                if (cursor.getItemMeta() instanceof SkullMeta sm && sm.getPlayerProfile() != null) {
                    for (ProfileProperty prop : sm.getPlayerProfile().getProperties()) {
                        if ("textures".equalsIgnoreCase(prop.getName())) {
                            session.setSkullTexture(prop.getValue());
                            player.sendMessage(ColorUtil.color("&aCopied skull texture from held player head!"));
                            MinionSkinCreatorGUI.open(player, session);
                            return;
                        }
                    }
                }
            }
            promptChat(player, () -> MinionSkinCreatorGUI.open(player, session),
                    "Enter Skull Base64 texture value (or 'cancel'):", input -> {
                session.setSkullTexture(input.trim());
            });
            return;
        }

        if (slot == 12) {
            Material[] armors = {
                    Material.LEATHER_CHESTPLATE,
                    Material.CHAINMAIL_CHESTPLATE,
                    Material.IRON_CHESTPLATE,
                    Material.GOLDEN_CHESTPLATE,
                    Material.DIAMOND_CHESTPLATE,
                    Material.NETHERITE_CHESTPLATE
            };
            int curIdx = 0;
            for (int i = 0; i < armors.length; i++) {
                if (armors[i] == session.getArmorMaterial()) {
                    curIdx = i;
                    break;
                }
            }
            session.setArmorMaterial(armors[(curIdx + 1) % armors.length]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            MinionSkinCreatorGUI.open(player, session);
            return;
        }

        if (slot == 14) {
            if (event.isRightClick()) {
                promptChat(player, () -> MinionSkinCreatorGUI.open(player, session),
                        "Enter RGB values 'R G B' (e.g. '255 100 50') or Hex ('#FF6432'):", input -> {
                    try {
                        String clean = input.trim().replace("#", "");
                        if (clean.contains(" ")) {
                            String[] rgb = clean.split("\\s+");
                            session.setLeatherColor(Color.fromRGB(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2])));
                        } else if (clean.length() == 6) {
                            int r = Integer.parseInt(clean.substring(0, 2), 16);
                            int g = Integer.parseInt(clean.substring(2, 4), 16);
                            int b = Integer.parseInt(clean.substring(4, 6), 16);
                            session.setLeatherColor(Color.fromRGB(r, g, b));
                        }
                    } catch (Exception ignored) {}
                });
                return;
            } else {
                int curIdx = 0;
                for (int i = 0; i < COLOR_PRESETS.length; i++) {
                    if (COLOR_PRESETS[i].equals(session.getLeatherColor())) {
                        curIdx = i;
                        break;
                    }
                }
                session.setLeatherColor(COLOR_PRESETS[(curIdx + 1) % COLOR_PRESETS.length]);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                MinionSkinCreatorGUI.open(player, session);
                return;
            }
        }

        if (slot == 16) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.setHandItem(cursor.clone());
                player.sendMessage(ColorUtil.color("&aSet custom held hand tool to: &e" + cursor.getType().name()));
            } else {
                Material[] tools = {
                        Material.DIAMOND_SWORD,
                        Material.NETHERITE_SWORD,
                        Material.DIAMOND_PICKAXE,
                        Material.NETHERITE_PICKAXE,
                        Material.DIAMOND_AXE,
                        Material.NETHERITE_AXE,
                        Material.DIAMOND_SHOVEL,
                        Material.DIAMOND_HOE,
                        Material.BOW,
                        Material.FISHING_ROD
                };
                Material cur = session.getHandItem() != null ? session.getHandItem().getType() : Material.DIAMOND_SWORD;
                int idx = 0;
                for (int i = 0; i < tools.length; i++) {
                    if (tools[i] == cur) {
                        idx = i;
                        break;
                    }
                }
                session.setHandItem(new ItemStack(tools[(idx + 1) % tools.length]));
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            MinionSkinCreatorGUI.open(player, session);
            return;
        }

        if (slot == 45) {
            player.closeInventory();
            return;
        }

        if (slot == 49) {
            MinionSkin skin = new MinionSkin(
                    session.getId(),
                    session.getDisplayName(),
                    session.getSkullTexture(),
                    session.getLeatherColor(),
                    session.getArmorMaterial(),
                    session.getHandItem(),
                    session.getDescription()
            );
            MinionSkinRegistry.saveCustomSkin(skin);
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSaved custom skin: &e" + skin.getDisplayName() + "&a!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.closeInventory();
            return;
        }
    }

    private void handleStorageCreatorClick(InventoryClickEvent event, Player player, MinionStorageCreatorSession session) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (slot == 0) {
            player.closeInventory();
            return;
        }

        if (slot == 2) {
            promptChat(player, () -> MinionStorageCreatorGUI.open(player, session),
                    "Enter Storage ID & Display Name: 'storage_id &6My Storage' (or 'cancel'):", input -> {
                String[] parts = input.trim().split(" ", 2);
                if (parts.length > 0 && !parts[0].isEmpty()) session.setId(parts[0].toLowerCase());
                if (parts.length > 1 && !parts[1].isEmpty()) session.setDisplayName(ColorUtil.color(parts[1]));
            });
            return;
        }

        if (slot == 4) {
            Material[] mats = {Material.CHEST, Material.BARREL, Material.ENDER_CHEST, Material.TRAPPED_CHEST};
            int curIdx = 0;
            for (int i = 0; i < mats.length; i++) {
                if (mats[i] == session.getMaterial()) {
                    curIdx = i;
                    break;
                }
            }
            session.setMaterial(mats[(curIdx + 1) % mats.length]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            MinionStorageCreatorGUI.open(player, session);
            return;
        }

        if (slot == 6) {
            CustomMinionStorage testDef = new CustomMinionStorage(session.getId(), session.getDisplayName(), session.getCapacity(), session.getMaterial(), session.getLore());
            MinionStorageGUI.open(player, null, testDef);
            return;
        }

        if (slot == 19) {
            session.setCapacity(session.getCapacity() - 5);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            MinionStorageCreatorGUI.open(player, session);
            return;
        }

        if (slot == 20) {
            session.setCapacity(session.getCapacity() - 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            MinionStorageCreatorGUI.open(player, session);
            return;
        }

        if (slot == 24) {
            session.setCapacity(session.getCapacity() + 1);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
            MinionStorageCreatorGUI.open(player, session);
            return;
        }

        if (slot == 25) {
            session.setCapacity(session.getCapacity() + 5);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
            MinionStorageCreatorGUI.open(player, session);
            return;
        }

        if (slot == 8) {
            CustomMinionStorage storage = new CustomMinionStorage(
                    session.getId(),
                    session.getDisplayName(),
                    session.getCapacity(),
                    session.getMaterial(),
                    session.getLore()
            );
            MinionStorageRegistry.saveCustomStorage(storage);
            ItemStack stItem = MinionItemFactory.createStorageItem(storage, 1);
            player.getInventory().addItem(stItem);
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSaved custom storage: &e" + storage.getDisplayName() + " &aand gave you 1x item!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.closeInventory();
            return;
        }
    }

    private void handleFuelCreatorClick(InventoryClickEvent event, Player player, FuelCreatorSession session) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ItemStack cursor = event.getCursor();

        if (slot == 45) {
            player.closeInventory();
            return;
        }

        if (slot == 2) {
            promptChat(player, () -> FuelCreatorGUI.open(player, session),
                    "Enter Fuel ID & Display Name: 'fuel_id &eMy Fuel' (or 'cancel'):", input -> {
                        String[] parts = input.trim().split(" ", 2);
                        if (parts.length > 0 && !parts[0].isEmpty()) session.setId(parts[0].toLowerCase());
                        if (parts.length > 1 && !parts[1].isEmpty()) session.setDisplayName(ColorUtil.color(parts[1]));
                    });
            return;
        }

        if (slot == 4) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.setMaterial(cursor.getType());
                player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSet fuel material to: &e" + cursor.getType().name()));
            } else {
                Material[] mats = FuelCreatorGUI.PRESET_MATERIALS;
                int curIdx = 0;
                for (int i = 0; i < mats.length; i++) {
                    if (mats[i] == session.getMaterial()) {
                        curIdx = i;
                        break;
                    }
                }
                session.setMaterial(mats[(curIdx + 1) % mats.length]);
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 6) {
            int cmd = session.getCustomModelData();
            if (event.isShiftClick()) {
                if (event.isLeftClick()) cmd += 100;
                else cmd = 0;
            } else {
                if (event.isLeftClick()) cmd += 1;
                else cmd = Math.max(0, cmd - 1);
            }
            session.setCustomModelData(cmd);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 19) {
            session.setSpeedBuff(session.getSpeedBuff() - 0.10);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 20) {
            session.setSpeedBuff(session.getSpeedBuff() - 0.05);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 24) {
            session.setSpeedBuff(session.getSpeedBuff() + 0.05);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 25) {
            session.setSpeedBuff(session.getSpeedBuff() + 0.10);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 29) {
            session.cycleDuration();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 31) {
            session.setPermanent(!session.isPermanent());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 33) {
            session.setGlow(!session.isGlow());
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.3f);
            FuelCreatorGUI.open(player, session);
            return;
        }

        if (slot == 40 || slot == 53) {
            CustomFuel fuel = session.toCustomFuel();
            ItemStack fuelItem = MinionItemFactory.createFuelItem(fuel);
            player.getInventory().addItem(fuelItem);
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aGave you 1x test copy of: &e" + fuel.getDisplayName()));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            return;
        }

        if (slot == 49) {
            CustomFuel fuel = session.toCustomFuel();
            FuelRegistry.saveFuel(fuel, session.getDurationString());
            ItemStack fuelItem = MinionItemFactory.createFuelItem(fuel);
            player.getInventory().addItem(fuelItem);
            player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&aSaved custom fuel: &e" + fuel.getDisplayName() + " &aand added to &ffuels.yml&a!"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.closeInventory();
            return;
        }
    }

    private void promptChat(Player player, Runnable reopen, String message, Consumer<String> action) {
        player.closeInventory();
        player.sendMessage(ColorUtil.getPrefix() + ColorUtil.color("&e" + message));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        PENDING_PROMPTS.put(player.getUniqueId(), input -> {
            if (!"cancel".equalsIgnoreCase(input.trim())) {
                action.accept(input);
            }
            if (reopen != null) {
                Bukkit.getScheduler().runTask(MinionX.getInstance(), reopen);
            }
        });
    }

}
