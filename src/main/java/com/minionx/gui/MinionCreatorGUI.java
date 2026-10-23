package com.minionx.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.minionx.api.MinionArchetype;
import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds the 54-slot In-Game Minion Creator Studio GUI.
 */
@SuppressWarnings("deprecation")
public class MinionCreatorGUI {

    public static final String TITLE = ColorUtil.color("&8Minion Creator Studio");

    public static void open(Player player, MinionCreatorSession session) {
        if (session == null) {
            session = new MinionCreatorSession("custom_minion");
        }

        MinionCreatorHolder holder = new MinionCreatorHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // ==========================================
        // Row 0: Top Header & Identity Cards
        // ==========================================

        // Slot 2: Minion Identity (ID & Display Name)
        inv.setItem(2, createNamedItem(Material.NAME_TAG, ColorUtil.color("&eMinion Identity"),
                List.of(ColorUtil.color("&7Identifier: &f" + session.getId()),
                        ColorUtil.color("&7Display Name: &f" + session.getDisplayName()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Unique configuration key and player-visible title."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to rename & configure ID via chat"))));

        // Slot 4: Live Minion Preview & Summary
        inv.setItem(4, createPreviewHead(session));

        // Slot 6: Minion Archetype (MINER, COMBAT, FORAGING, FARMING)
        Material archMat;
        switch (session.getArchetype()) {
            case COMBAT -> archMat = Material.IRON_SWORD;
            case FORAGING -> archMat = Material.IRON_AXE;
            case FARMING -> archMat = Material.DIAMOND_HOE;
            default -> archMat = Material.DIAMOND_PICKAXE;
        }
        inv.setItem(6, createNamedItem(archMat, ColorUtil.color("&6Minion Archetype"),
                List.of(ColorUtil.color("&7Current: &e" + session.getArchetype().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7- &fMINER: &7Generates & mines blocks in 5x5 area"),
                        ColorUtil.color("&7- &fCOMBAT: &7Spawns & slays mobs (Vanilla/MythicMobs)"),
                        ColorUtil.color("&7- &fFORAGING: &7Plants saplings & chops wood trees"),
                        ColorUtil.color("&7- &fFARMING: &7Tills soil, grows crops & breeds livestock"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle Archetype"))));

        // ==========================================
        // Row 1: Left (Target) | Mid (Drops) | Right (Visuals)
        // ==========================================

        // Slot 10: Target (Block, Mob, Wood, Crop, Animal)
        if (session.getArchetype() == MinionArchetype.MINER) {
            Material targetMat = session.getTargetBlock() != null ? session.getTargetBlock() : Material.COBBLESTONE;
            inv.setItem(10, createNamedItem(targetMat, ColorUtil.color("&aTarget Block (Miner)"),
                    List.of(ColorUtil.color("&7Target Block: &f" + targetMat.name()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&8Block generated and mined within the minion's zone."),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aClick with any solid block on cursor to set target"),
                            ColorUtil.color("&eOr click to cycle common ores"))));
        } else if (session.getArchetype() == MinionArchetype.COMBAT) {
            inv.setItem(10, createNamedItem(Material.SPAWNER, ColorUtil.color("&cTarget Mob (Combat)"),
                    List.of(ColorUtil.color("&7Target Mob: &f" + session.getTargetMob()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&8Hostile mob spawned and slain for loot and combat XP."),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aLeft-Click: &fCycle vanilla mobs"),
                            ColorUtil.color("&dRight-Click: &fEnter MythicMob ID in chat"))));
        } else if (session.getArchetype() == MinionArchetype.FORAGING) {
            Material woodMat = session.getTargetBlock() != null ? session.getTargetBlock() : Material.OAK_LOG;
            inv.setItem(10, createNamedItem(woodMat, ColorUtil.color("&2Target Wood (Foraging)"),
                    List.of(ColorUtil.color("&7Wood Log: &f" + woodMat.name()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&8Tree type harvested and replanted by this minion."),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aClick with any wood log on cursor to set target"),
                            ColorUtil.color("&eOr click to cycle vanilla log types"))));
        } else {
            if (session.getTargetMob() != null) {
                inv.setItem(10, createNamedItem(Material.LEAD, ColorUtil.color("&eTarget Animal (Husbandry)"),
                        List.of(ColorUtil.color("&7Animal Mob: &f" + session.getTargetMob()),
                                ColorUtil.color("&7"),
                                ColorUtil.color("&8Livestock managed and harvested by this minion."),
                                ColorUtil.color("&7"),
                                ColorUtil.color("&aLeft-Click: &fCycle farm animals & crops"),
                                ColorUtil.color("&dRight-Click: &fEnter custom entity in chat"))));
            } else {
                Material cropMat = session.getTargetBlock() != null ? session.getTargetBlock() : Material.WHEAT;
                inv.setItem(10, createNamedItem(cropMat, ColorUtil.color("&eTarget Crop (Farming)"),
                        List.of(ColorUtil.color("&7Crop / Plant: &f" + cropMat.name()),
                                ColorUtil.color("&7"),
                                ColorUtil.color("&8Crop planted, grown, and harvested by this minion."),
                                ColorUtil.color("&7"),
                                ColorUtil.color("&aClick with crop/seed on cursor to set target"),
                                ColorUtil.color("&eOr click to cycle crops & animals"))));
            }
        }

        // Slot 12: Exact Primary Drop Item
        ItemStack drop = session.getExactDropItem().clone();
        ItemMeta dMeta = drop.getItemMeta();
        String dropDispName = dMeta != null && dMeta.hasDisplayName() ? dMeta.getDisplayName() : drop.getType().name();
        List<String> dLore = new ArrayList<>();
        dLore.add(ColorUtil.color("&7Primary Drop: &f" + dropDispName));
        dLore.add(ColorUtil.color("&7Amount: &fx" + drop.getAmount()));
        dLore.add(ColorUtil.color("&7"));
        dLore.add(ColorUtil.color("&8Guaranteed item produced on every minion harvest cycle."));
        dLore.add(ColorUtil.color("&8Preserves lore, NBT tags, enchants & custom model data."));
        dLore.add(ColorUtil.color("&7"));
        dLore.add(ColorUtil.color("&eClick with ANY custom item on cursor to set"));
        if (dMeta != null) {
            dMeta.setLore(dLore);
            drop.setItemMeta(dMeta);
        }
        inv.setItem(12, drop);

        // Slot 13: Secondary Drop Item (Optional)
        ItemStack secDrop = session.getSecondaryDropItem();
        if (secDrop != null && secDrop.getType() != Material.AIR) {
            ItemStack secClone = secDrop.clone();
            ItemMeta secMeta = secClone.getItemMeta();
            String secName = secMeta != null && secMeta.hasDisplayName() ? secMeta.getDisplayName() : secClone.getType().name();
            int pct = (int) Math.round(session.getSecondaryDropChance() * 100);
            List<String> sLore = new ArrayList<>();
            sLore.add(ColorUtil.color("&7Secondary Drop: &f" + secName));
            sLore.add(ColorUtil.color("&7Drop Chance: &e" + pct + "%"));
            sLore.add(ColorUtil.color("&7"));
            sLore.add(ColorUtil.color("&8Bonus drop rolled on each harvest cycle."));
            sLore.add(ColorUtil.color("&7"));
            sLore.add(ColorUtil.color("&aLeft-Click: &fCycle Drop Chance (5%, 10%, 25%, 50%, 100%)"));
            sLore.add(ColorUtil.color("&cRight-Click: &fRemove Secondary Drop"));
            sLore.add(ColorUtil.color("&dClick with item on cursor to replace"));
            if (secMeta != null) {
                secMeta.setLore(sLore);
                secClone.setItemMeta(secMeta);
            }
            inv.setItem(13, secClone);
        } else {
            inv.setItem(13, createNamedItem(Material.GRAY_DYE, ColorUtil.color("&7Secondary Drop &8(None)"),
                    List.of(ColorUtil.color("&7Status: &fDisabled / No secondary drop"),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&8Optional bonus item produced alongside primary drops."),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&eClick with ANY item on cursor to add"),
                            ColorUtil.color("&7Examples: Apples/Saplings for Wood, Seeds for Wheat, Leather for Cow."))));
        }

        // Slot 15: Skull Texture / Head
        int selectedTier = session.getSelectedTier();
        String currentSkull = session.getEffectiveSkullTexture(selectedTier);
        List<String> skullLore = new ArrayList<>();
        skullLore.add(ColorUtil.color("&7Skin: &f" + (currentSkull != null ? "Custom Skin Configured" : "Default")));
        if (session.isPerTierMode()) {
            skullLore.add(ColorUtil.color("&bTarget Tier: &eTier " + selectedTier));
        }
        skullLore.add(ColorUtil.color("&7"));
        skullLore.add(ColorUtil.color("&8Head skin rendered on the minion armor stand."));
        skullLore.add(ColorUtil.color("&7"));
        skullLore.add(ColorUtil.color("&aLeft-Click: &fEnter skin name/Base64 in chat"));
        skullLore.add(ColorUtil.color("&eRight-Click: &fCycle preset heads"));
        skullLore.add(ColorUtil.color("&dClick with Player Head on cursor to copy"));
        if (session.isPerTierMode()) {
            skullLore.add(ColorUtil.color("&3[Per-Tier Mode: Setting head specifically for Tier " + selectedTier + "]"));
        }
        String skullTitle = session.isPerTierMode() ? "&bSkull Texture (Tier " + selectedTier + ")" : "&bSkull Texture";
        inv.setItem(15, createNamedItem(Material.PLAYER_HEAD, ColorUtil.color(skullTitle), skullLore));

        // Slot 16: Armor & Color
        inv.setItem(16, createArmorItem(session));

        // ==========================================
        // Row 2: Left (Speed) | Mid (XP & Storage) | Right (Tool & Style)
        // ==========================================

        // Slot 19: Action Delay / Speed
        inv.setItem(19, createNamedItem(Material.CLOCK, ColorUtil.color("&eAction Delay (Speed)"),
                List.of(ColorUtil.color("&7Current Delay: &e" + String.format("%.1f", session.getActionDelay()) + "s"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Interval between minion spawn/mine actions in seconds."),
                        ColorUtil.color("&8Lower values make the minion act faster."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f-1.0s &7(Faster)"),
                        ColorUtil.color("&cRight-Click: &f+1.0s &7(Slower)"),
                        ColorUtil.color("&eShift-Click: &f±0.1s Fine-tune"))));

        // Slot 21: Harvest XP
        inv.setItem(21, createNamedItem(Material.EXPERIENCE_BOTTLE, ColorUtil.color("&aHarvest XP Reward"),
                List.of(ColorUtil.color("&7XP Per Harvest: &a+" + session.getXpPerHarvest() + " XP"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Vanilla experience awarded directly when player collects."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f+1 XP"),
                        ColorUtil.color("&cRight-Click: &f-1 XP"),
                        ColorUtil.color("&eShift-Left-Click: &f+5 XP"))));

        // Slot 22: Internal Storage Slots
        inv.setItem(22, createNamedItem(Material.CHEST, ColorUtil.color("&6Storage Capacity"),
                List.of(ColorUtil.color("&7Internal Storage: &e" + session.getStorageSlots() + " slots &7(" + (session.getStorageSlots() * 64) + " items)"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Base inventory capacity of this minion without external chests."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f+1 Slot"),
                        ColorUtil.color("&cRight-Click: &f-1 Slot"),
                        ColorUtil.color("&eShift-Click: &fReset to 1 slot"))));

        // Slot 24: Hand Tool / Weapon
        ItemStack hand = session.getEffectiveHandItem(session.getSelectedTier()).clone();
        ItemMeta hMeta = hand.getItemMeta();
        if (hMeta != null) {
            String handTitle = session.isPerTierMode() ? "&eHand Tool / Weapon (Tier " + session.getSelectedTier() + ")" : "&eHand Tool / Weapon";
            hMeta.setDisplayName(ColorUtil.color(handTitle));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&7Current: &f" + hand.getType().name()));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&8Weapon or tool held in the minion's right hand."));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&eClick with ANY tool/weapon on cursor to equip"));
            lore.add(ColorUtil.color("&7Or click with empty hand to cycle weapons/tools"));
            if (session.isPerTierMode()) {
                lore.add(ColorUtil.color("&3[Per-Tier Mode: Setting tool for Tier " + session.getSelectedTier() + "]"));
            }
            hMeta.setLore(lore);
            hand.setItemMeta(hMeta);
        }
        inv.setItem(24, hand);

        // Slot 25: Multi-Tier Style Mode Toggle
        inv.setItem(25, createNamedItem(Material.COMPARATOR, ColorUtil.color("&bMulti-Tier Style Mode"),
                List.of(ColorUtil.color("&7Mode: " + (session.isPerTierMode() ? "&bPer-Tier Overrides" : "&aAll Tiers Identical")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8When enabled, lets you assign distinct head textures,"),
                        ColorUtil.color("&8armor colors, and tools to each tier individually."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to toggle Multi-Tier Style Mode"))));

        // ==========================================
        // Row 3: Tier Studio & Tier Carousel
        // ==========================================

        // Slot 31: Per-Minion Tier Studio
        inv.setItem(31, createNamedItem(Material.DIAMOND_BLOCK, ColorUtil.color("&bPer-Minion Tier Studio"),
                List.of(ColorUtil.color("&7Minion: &e" + session.getId()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Open the detailed Tier Editor to configure custom action"),
                        ColorUtil.color("&8delays, storage capacities, and upgrade costs tier-by-tier."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to open Per-Minion Tier Studio"))));

        // Slot 33: Selected Tier Carousel
        inv.setItem(33, createNamedItem(Material.ANVIL,
                ColorUtil.color("&bTier Carousel: &eTier " + com.minionx.api.MinionTier.toRoman(session.getSelectedTier()) + " &7(" + session.getSelectedTier() + "/" + session.getMaxTier() + ")"),
                List.of(ColorUtil.color("&7Target Tier: &eTier " + session.getSelectedTier()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Selects which tier is affected when editing Head Skin,"),
                        ColorUtil.color("&8Armor, and Hand Tool in Per-Tier Mode."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &fNext Tier (+1)"),
                        ColorUtil.color("&cRight-Click: &fPrevious Tier (-1)"))));

        // Slot 34: Max Tier Cap
        inv.setItem(34, createNamedItem(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, ColorUtil.color("&6Max Tier Cap"),
                List.of(ColorUtil.color("&7Maximum Tier: &eTier " + com.minionx.api.MinionTier.toRoman(session.getMaxTier()) + " &7(" + session.getMaxTier() + ")"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Sets the highest attainable tier for this minion."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f+1 Max Tier"),
                        ColorUtil.color("&cRight-Click: &f-1 Max Tier"),
                        ColorUtil.color("&7Allows this minion to cap at Tier 5, 7, 10, 11, 12, etc."))));

        // ==========================================
        // Row 5: Navigation, Template Reset & Deployment
        // ==========================================

        // Slot 45: Universal Back Button
        inv.setItem(45, createNamedItem(Material.ARROW, ColorUtil.color("&e« Back to Operator Hub"),
                List.of(ColorUtil.color("&7Return to the main Operator Hub menu."),
                        ColorUtil.color("&8Unsaved studio changes will remain active in this session."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to Return"))));

        // Slot 48: Reset Template
        inv.setItem(48, createNamedItem(Material.REDSTONE, ColorUtil.color("&cReset Template"),
                List.of(ColorUtil.color("&7Reset all studio settings back to default values."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&8Clears custom drops, tier overrides, and tuned stats."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&cClick to Reset Template"))));

        // Slot 49: Save & Deploy (Tier I)
        inv.setItem(49, createNamedItem(Material.EMERALD_BLOCK, ColorUtil.color("&aSave & Deploy (Tier I)"),
                List.of(ColorUtil.color("&7Saves definition to minions.yml, registers"),
                        ColorUtil.color("&7into registry, and gives you a Tier I item!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aClick to Save & Deploy Tier I"))));

        // Slot 50: Deploy Maxed
        int maxT = session.getMaxTier();
        inv.setItem(50, createNamedItem(Material.NETHER_STAR, ColorUtil.color("&bDeploy Maxed &7(Tier " + com.minionx.api.MinionTier.toRoman(maxT) + ")"),
                List.of(ColorUtil.color("&7Saves and gives you a maxed Tier " + com.minionx.api.MinionTier.toRoman(maxT)),
                        ColorUtil.color("&7minion item for immediate testing."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&bClick to Deploy Max Tier"))));

        // Slot 53: Studio Guide & Shortcuts
        inv.setItem(53, createNamedItem(Material.BOOK, ColorUtil.color("&6Studio Guide & Shortcuts"),
                List.of(ColorUtil.color("&eQuick Controls:"),
                        ColorUtil.color("&7• Drag & drop items from cursor to equip"),
                        ColorUtil.color("&7• Shift-click to fine-tune delay & XP values"),
                        ColorUtil.color("&7• Toggle Multi-Tier Mode to customize each tier"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to print complete shortcut guide in chat"))));

        player.openInventory(inv);
    }

    private static ItemStack createPreviewHead(MinionCreatorSession session) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            String texture = session.getEffectiveSkullTexture(session.getSelectedTier());
            if (texture != null && !texture.isEmpty()) {
                try {
                    PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                    profile.setProperty(new ProfileProperty("textures", texture));
                    meta.setPlayerProfile(profile);
                } catch (Exception ignored) {}
            }
            meta.setDisplayName(ColorUtil.color(session.getDisplayName()));

            String targetDesc;
            if (session.getArchetype() == MinionArchetype.COMBAT) {
                targetDesc = session.getTargetMob();
            } else if (session.getArchetype() == MinionArchetype.FARMING && session.getTargetMob() != null) {
                targetDesc = session.getTargetMob() + " (Animal)";
            } else {
                targetDesc = session.getTargetBlock() != null ? session.getTargetBlock().name() : "None";
            }

            ItemStack drop = session.getExactDropItem();
            String dropName = drop != null && drop.hasItemMeta() && drop.getItemMeta().hasDisplayName()
                    ? drop.getItemMeta().getDisplayName()
                    : (drop != null ? drop.getType().name() : "None");

            ItemStack secDrop = session.getSecondaryDropItem();
            String secDesc = secDrop != null
                    ? secDrop.getType().name() + " (" + (int) Math.round(session.getSecondaryDropChance() * 100) + "%)"
                    : "None";

            meta.setLore(List.of(
                    ColorUtil.color("&7Archetype: &e" + session.getArchetype().name()),
                    ColorUtil.color("&7Target: &e" + targetDesc),
                    ColorUtil.color("&7Exact Drop: &e" + dropName + " &7(x" + (drop != null ? drop.getAmount() : 1) + ")"),
                    ColorUtil.color("&7Secondary Drop: &e" + secDesc),
                    ColorUtil.color("&7Harvest XP: &a+" + session.getXpPerHarvest() + " XP"),
                    ColorUtil.color("&7Action Delay: &e" + String.format("%.1f", session.getActionDelay()) + "s"),
                    ColorUtil.color("&7Storage: &e" + session.getStorageSlots() + " slots (" + (session.getStorageSlots() * 64) + " items)"),
                    ColorUtil.color("&7Style Mode: &b" + (session.isPerTierMode() ? "Per-Tier (Tier " + session.getSelectedTier() + ")" : "All Tiers Identical"))
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createArmorItem(MinionCreatorSession session) {
        int tier = session.getSelectedTier();
        Material curMat = session.getEffectiveArmorMaterial(tier);
        Color curCol = session.getEffectiveArmorColor(tier);

        ItemStack armor = new ItemStack(curMat);
        List<String> lore = new ArrayList<>();
        lore.add(ColorUtil.color("&7Material: &e" + curMat.name()));
        if (armor.getItemMeta() instanceof LeatherArmorMeta lMeta) {
            lMeta.setColor(curCol);
            lMeta.setDisplayName(ColorUtil.color(session.isPerTierMode() ? "&dArmor & Color (Tier " + tier + ")" : "&dArmor & Color"));
            lore.add(ColorUtil.color("&7Color: &cR:" + curCol.getRed() + " &aG:" + curCol.getGreen() + " &bB:" + curCol.getBlue()));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&aLeft-Click: &fCycle Armor (Leather / Iron / Diamond / Netherite)"));
            lore.add(ColorUtil.color("&eRight-Click: &fCycle Color Palette"));
            lore.add(ColorUtil.color("&dClick with Leather Chestplate or Dye on cursor to apply exact color!"));
            if (session.isPerTierMode()) {
                lore.add(ColorUtil.color("&3[Per-Tier Mode: Editing armor for Tier " + tier + "]"));
            }
            lMeta.setLore(lore);
            armor.setItemMeta(lMeta);
        } else {
            ItemMeta meta = armor.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.color(session.isPerTierMode() ? "&dArmor Material (Tier " + tier + ")" : "&dArmor Material"));
                lore.add(ColorUtil.color("&7"));
                lore.add(ColorUtil.color("&aLeft-Click: &fCycle Armor (Leather / Iron / Diamond / Netherite)"));
                if (session.isPerTierMode()) {
                    lore.add(ColorUtil.color("&3[Per-Tier Mode: Editing armor for Tier " + tier + "]"));
                }
                meta.setLore(lore);
                armor.setItemMeta(meta);
            }
        }
        return armor;
    }

    private static ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createNamedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
