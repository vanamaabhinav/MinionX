package com.skyblock.minions.gui;

import com.skyblock.minions.SkyBlockMinions;
import com.skyblock.minions.api.MinionArchetype;
import com.skyblock.minions.api.MinionDefinition;
import com.skyblock.minions.api.MinionRegistry;
import com.skyblock.minions.item.MinionItemFactory;
import com.skyblock.minions.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles interactions in the Minion Creator Studio GUI and captures chat input prompts.
 */
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
            Material.OAK_LOG
    };

    private static final String[] COMBAT_MOBS = {
            "ZOMBIE",
            "SKELETON",
            "SPIDER",
            "CAVE_SPIDER",
            "BLAZE",
            "CREEPER",
            "ENDERMAN",
            "SLIME",
            "MAGMA_CUBE"
    };

    private static final String[] SKULL_PRESETS = {
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTRhMjQzZTU1OWU5OWExMGJmMWFlNGU2YmFmYmY0MTA1ODhhMTVhM2Q2YzVjOGVmMjljMWE4YmRiNWYzMWFjYyJ9fX0=", // Cobble
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZlOWFlMTVmNTRkOWI5ZTEyZGIzOWZhZTZlODlhMWY4MmU1YzlmOTBhOTRkZjljYzg0MjM1YWM3NzFmIn19fQ==", // Iron
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTRmODcxMThjZGEyMmQ0OWU4OTU1ZTZiYWY4Nzg2MzIxMzAxYWI5N2M3YjU3Mzg4YTFhMjAxYjU2YTUxZiJ9fX0=", // Diamond
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTZmYzg1NGJiODRjZjRiNDcxN2UxNTQ4MDU1NmU3NzM4ZTExYzcxM2NkMzlmYTkyOTAyMTUzNzYzMTIzMzEifX19", // Zombie
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2JmNTM0ZWRhNTJlMzEwODk0ZWVhNzU1ZThkYWE2MWRmZDdhODFjNmUxYWRlZmYzYjVjODhhYmFiZDA5OSJ9fX0="  // Skeleton
    };

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MinionCreatorHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        MinionCreatorSession session = holder.getSession();
        int slot = event.getRawSlot();
        ItemStack cursor = player.getItemOnCursor();

        // 1. Minion Identity
        if (slot == 2) {
            promptChat(player, session, "Enter new minion ID and Display Name (e.g. 'ruby &dRuby Minion'):", input -> {
                String[] parts = input.split(" ", 2);
                if (parts.length >= 1 && !parts[0].isBlank()) {
                    session.setId(parts[0].trim().toLowerCase());
                }
                if (parts.length >= 2 && !parts[1].isBlank()) {
                    session.setDisplayName(ColorUtil.color(parts[1].trim()));
                } else {
                    session.setDisplayName(ColorUtil.color("&a" + session.getId() + " Minion"));
                }
            });
            return;
        }

        // 2. Archetype Toggle
        if (slot == 10) {
            if (session.getArchetype() == MinionArchetype.MINER) {
                session.setArchetype(MinionArchetype.COMBAT);
                session.setHandItem(new ItemStack(Material.IRON_SWORD));
                session.setExactDropItem(new ItemStack(Material.ROTTEN_FLESH));
            } else {
                session.setArchetype(MinionArchetype.MINER);
                session.setHandItem(new ItemStack(Material.STONE_PICKAXE));
                session.setExactDropItem(new ItemStack(Material.COBBLESTONE));
            }
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
                player.sendMessage(ColorUtil.color("&6[MinionX] &aCopied skull texture from held head."));
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
                player.sendMessage(ColorUtil.color("&6[MinionX] &aApplied custom leather color from cursor."));
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
                player.sendMessage(ColorUtil.color("&6[MinionX] &aEquipped " + cursor.getType().name() + " in minion's hand."));
                player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
            }
            return;
        }

        // 6. Target (Block / Mob)
        if (slot == 28) {
            if (session.getArchetype() == MinionArchetype.MINER) {
                if (cursor != null && cursor.getType().isBlock() && cursor.getType().isSolid()) {
                    session.setTargetBlock(cursor.getType());
                    session.setExactDropItem(new ItemStack(cursor.getType()));
                    player.sendMessage(ColorUtil.color("&6[MinionX] &aSet mining target to " + cursor.getType().name() + "."));
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
                session.setExactDropItem(new ItemStack(MINER_ORES[next]));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                MinionCreatorGUI.open(player, session);
            } else {
                if (event.isRightClick()) {
                    promptChat(player, session, "Enter MythicMob ID or custom EntityType (e.g. 'SkeletalKnight' or 'WITHER_SKELETON'):", input -> {
                        session.setTargetMob(input.trim());
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
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                    MinionCreatorGUI.open(player, session);
                }
            }
            return;
        }

        // 7. Exact Drop Item
        if (slot == 30) {
            if (cursor != null && cursor.getType() != Material.AIR) {
                session.setExactDropItem(cursor.clone());
                player.sendMessage(ColorUtil.color("&6[MinionX] &aSet exact drop item clone with all custom attributes!"));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.4f);
                MinionCreatorGUI.open(player, session);
            }
            return;
        }

        // 8. Speed, Storage & XP Tuning
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

        // 9. Tier Customization Mode
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

        // 10. Cancel & Return
        if (slot == 48) {
            player.closeInventory();
            OperatorGUI.openMain(player);
            return;
        }

        // 11. Save & Deploy (Tier I)
        if (slot == 49) {
            saveAndDeploy(player, session, 1);
            return;
        }

        // 12. Deploy Maxed (Tier XI)
        if (slot == 50) {
            saveAndDeploy(player, session, 11);
        }
    }

    private void saveAndDeploy(Player player, MinionCreatorSession session, int tier) {
        player.closeInventory();
        MinionDefinition def = session.buildDefinition();
        MinionRegistry.saveCustomMinion(def);

        ItemStack item = MinionItemFactory.createCustomMinionItem(def, tier, 0);
        player.getInventory().addItem(item);

        player.sendMessage(ColorUtil.color("&6[MinionX] &aCreated custom minion &f" + def.getDisplayName() + " &aand saved to minions.yml!"));
        player.sendMessage(ColorUtil.color("&6[MinionX] &7Received Tier " + tier + " spawn item. Right-click to place!"));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }

    private void promptChat(Player player, MinionCreatorSession session, String message, Consumer<String> action) {
        player.closeInventory();
        player.sendMessage(ColorUtil.color("&6[MinionX] &e" + message));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        PENDING_PROMPTS.put(player.getUniqueId(), input -> {
            if (!"cancel".equalsIgnoreCase(input.trim())) {
                action.accept(input);
            }
            Bukkit.getScheduler().runTask(SkyBlockMinions.getInstance(), () -> {
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
}
