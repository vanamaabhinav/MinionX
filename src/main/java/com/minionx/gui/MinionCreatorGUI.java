package com.minionx.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.minionx.api.MinionArchetype;
import com.minionx.util.ColorUtil;
import org.bukkit.Bukkit;
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

        // Slot 2: Minion Identity (ID & Display Name)
        inv.setItem(2, createNamedItem(Material.NAME_TAG, ColorUtil.color("&eMinion Identity"),
                List.of(ColorUtil.color("&7ID: &f" + session.getId()),
                        ColorUtil.color("&7Display Name: &f" + session.getDisplayName()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to rename / change ID via chat"))));

        // Slot 4: Minion Preview & Summary
        inv.setItem(4, createPreviewHead(session));

        // Slot 10: Minion Archetype (MINER, COMBAT, FORAGING, FARMING)
        Material archMat;
        switch (session.getArchetype()) {
            case COMBAT -> archMat = Material.IRON_SWORD;
            case FORAGING -> archMat = Material.IRON_AXE;
            case FARMING -> archMat = Material.DIAMOND_HOE;
            default -> archMat = Material.DIAMOND_PICKAXE;
        }
        inv.setItem(10, createNamedItem(archMat, ColorUtil.color("&6Minion Archetype"),
                List.of(ColorUtil.color("&7Current: &e" + session.getArchetype().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7- &fMINER: &7Generates & mines blocks in 5x5"),
                        ColorUtil.color("&7- &fCOMBAT: &7Spawns & slays mobs (Vanilla/MythicMobs)"),
                        ColorUtil.color("&7- &fFORAGING: &7Plants saplings & chops trees (Logs)"),
                        ColorUtil.color("&7- &fFARMING: &7Tills soil, grows crops/stems/canes/animals"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle Archetype"))));

        // Slot 12: Skull Texture / Head
        inv.setItem(12, createNamedItem(Material.PLAYER_HEAD, ColorUtil.color("&bSkull Texture"),
                List.of(ColorUtil.color("&7Skin: &f" + (session.getSkullTexture() != null ? "Custom Skin Configured" : "Default")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &fEnter skin name/Base64 in chat"),
                        ColorUtil.color("&eRight-Click: &fCycle preset heads"),
                        ColorUtil.color("&dClick with Player Head on cursor to copy!"))));

        // Slot 14: Armor & Color
        inv.setItem(14, createArmorItem(session));

        // Slot 16: Hand Tool / Weapon
        ItemStack hand = session.getHandItem().clone();
        ItemMeta hMeta = hand.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName(ColorUtil.color("&eHand Tool / Weapon"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&7Current: &f" + session.getHandItem().getType().name()));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&eClick with ANY tool/weapon on cursor to equip!"));
            hMeta.setLore(lore);
            hand.setItemMeta(hMeta);
        }
        inv.setItem(16, hand);

        // Slot 28: Target (Block, Mob, Wood, Crop, Animal)
        if (session.getArchetype() == MinionArchetype.MINER) {
            Material targetMat = session.getTargetBlock() != null ? session.getTargetBlock() : Material.COBBLESTONE;
            inv.setItem(28, createNamedItem(targetMat, ColorUtil.color("&aTarget Block (Miner)"),
                    List.of(ColorUtil.color("&7Target Block: &f" + targetMat.name()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aClick with any solid block on cursor to set target!"),
                            ColorUtil.color("&eOr click to cycle common ores"))));
        } else if (session.getArchetype() == MinionArchetype.COMBAT) {
            inv.setItem(28, createNamedItem(Material.SPAWNER, ColorUtil.color("&cTarget Mob (Combat)"),
                    List.of(ColorUtil.color("&7Target Mob: &f" + session.getTargetMob()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aLeft-Click: &fCycle vanilla mobs"),
                            ColorUtil.color("&dRight-Click: &fEnter MythicMob ID in chat"))));
        } else if (session.getArchetype() == MinionArchetype.FORAGING) {
            Material woodMat = session.getTargetBlock() != null ? session.getTargetBlock() : Material.OAK_LOG;
            inv.setItem(28, createNamedItem(woodMat, ColorUtil.color("&2Target Wood (Foraging)"),
                    List.of(ColorUtil.color("&7Wood Log: &f" + woodMat.name()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aClick with any wood log on cursor to set target!"),
                            ColorUtil.color("&eOr click to cycle vanilla log types"))));
        } else {
            if (session.getTargetMob() != null) {
                inv.setItem(28, createNamedItem(Material.LEAD, ColorUtil.color("&eTarget Animal (Husbandry)"),
                        List.of(ColorUtil.color("&7Animal Mob: &f" + session.getTargetMob()),
                                ColorUtil.color("&7"),
                                ColorUtil.color("&aLeft-Click: &fCycle farm animals & crops"),
                                ColorUtil.color("&dRight-Click: &fEnter custom entity in chat"))));
            } else {
                Material cropMat = session.getTargetBlock() != null ? session.getTargetBlock() : Material.WHEAT;
                inv.setItem(28, createNamedItem(cropMat, ColorUtil.color("&eTarget Crop (Farming)"),
                        List.of(ColorUtil.color("&7Crop / Plant: &f" + cropMat.name()),
                                ColorUtil.color("&7"),
                                ColorUtil.color("&aClick with crop/seed on cursor to set target!"),
                                ColorUtil.color("&eOr click to cycle crops & animals"))));
            }
        }

        // Slot 30: Exact Primary Drop Item
        ItemStack drop = session.getExactDropItem().clone();
        ItemMeta dMeta = drop.getItemMeta();
        String dropDispName = dMeta != null && dMeta.hasDisplayName() ? dMeta.getDisplayName() : drop.getType().name();
        List<String> dLore = new ArrayList<>();
        dLore.add(ColorUtil.color("&7Primary Drop: &f" + dropDispName));
        dLore.add(ColorUtil.color("&7Amount: &fx" + drop.getAmount()));
        dLore.add(ColorUtil.color("&7"));
        dLore.add(ColorUtil.color("&eClick with ANY custom item on cursor to set!"));
        dLore.add(ColorUtil.color("&7Preserves lore, NBT, enchants & custom model data."));
        if (dMeta != null) {
            dMeta.setLore(dLore);
            drop.setItemMeta(dMeta);
        }
        inv.setItem(30, drop);

        // Slot 31: Secondary Drop Item (Optional)
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
            sLore.add(ColorUtil.color("&aLeft-Click: &fCycle Drop Chance (5%, 10%, 25%, 50%, 100%)"));
            sLore.add(ColorUtil.color("&cRight-Click: &fRemove Secondary Drop"));
            sLore.add(ColorUtil.color("&dClick with item on cursor to replace!"));
            if (secMeta != null) {
                secMeta.setLore(sLore);
                secClone.setItemMeta(secMeta);
            }
            inv.setItem(31, secClone);
        } else {
            inv.setItem(31, createNamedItem(Material.GRAY_DYE, ColorUtil.color("&7Secondary Drop (None)"),
                    List.of(ColorUtil.color("&7Status: &fDisabled / No secondary drop"),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&eClick with ANY item on cursor to add!"),
                            ColorUtil.color("&7Examples: Apples/Saplings for Wood, Seeds for Wheat, Leather for Cow."))));
        }

        // Slot 32: Speed, Storage & Harvest XP
        inv.setItem(32, createNamedItem(Material.EXPERIENCE_BOTTLE, ColorUtil.color("&aSpeed, XP & Storage"),
                List.of(ColorUtil.color("&7Action Delay: &e" + String.format("%.1f", session.getActionDelay()) + "s"),
                        ColorUtil.color("&7Harvest XP: &a+" + session.getXpPerHarvest() + " XP"),
                        ColorUtil.color("&7Storage Slots: &e" + session.getStorageSlots() + " slots"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &f-1.0s Delay &7(Faster)"),
                        ColorUtil.color("&cRight-Click: &f+1.0s Delay &7(Slower)"),
                        ColorUtil.color("&eShift-Left-Click: &f+1 Harvest XP"),
                        ColorUtil.color("&6Shift-Right-Click: &f+1 Storage Slot"))));

        // Slot 34: Tier Style Customization
        inv.setItem(34, createNamedItem(Material.ANVIL, ColorUtil.color("&bTier Style Configuration"),
                List.of(ColorUtil.color("&7Mode: &f" + (session.isPerTierMode() ? "&bPer-Tier Overrides" : "&aAll Tiers Identical")),
                        ColorUtil.color("&7Selected Tier: &eTier " + session.getSelectedTier()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &fToggle Mode (Identical <-> Per-Tier)"),
                        ColorUtil.color("&eRight-Click: &fCycle Selected Tier (1..11)"))));

        // Slot 48: Cancel / Exit
        inv.setItem(48, createNamedItem(Material.BARRIER, ColorUtil.color("&cCancel & Return"),
                List.of(ColorUtil.color("&7Discard changes and return to Operator Hub."))));

        // Slot 49: Save & Deploy (Tier I)
        inv.setItem(49, createNamedItem(Material.EMERALD_BLOCK, ColorUtil.color("&aSave & Deploy Minion"),
                List.of(ColorUtil.color("&7Saves definition to minions.yml, registers"),
                        ColorUtil.color("&7into registry, and gives you a Tier I item!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to Save & Deploy"))));

        // Slot 50: Deploy Maxed (Tier XI)
        inv.setItem(50, createNamedItem(Material.NETHER_STAR, ColorUtil.color("&bDeploy Maxed (Tier XI)"),
                List.of(ColorUtil.color("&7Saves and gives you a maxed Tier XI"),
                        ColorUtil.color("&7minion item for immediate testing."),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to Deploy Tier XI"))));

        player.openInventory(inv);
    }

    private static ItemStack createPreviewHead(MinionCreatorSession session) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            String texture = session.getSkullTexture();
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
        ItemStack armor = new ItemStack(session.getArmorMaterial());
        if (armor.getItemMeta() instanceof LeatherArmorMeta lMeta) {
            lMeta.setColor(session.getArmorColor());
            lMeta.setDisplayName(ColorUtil.color("&dArmor & Color"));
            lMeta.setLore(List.of(
                    ColorUtil.color("&7Material: &e" + session.getArmorMaterial().name()),
                    ColorUtil.color("&7Color: &cR:" + session.getArmorColor().getRed() + " &aG:" + session.getArmorColor().getGreen() + " &bB:" + session.getArmorColor().getBlue()),
                    ColorUtil.color("&7"),
                    ColorUtil.color("&aLeft-Click: &fCycle Armor (Leather / Iron / Diamond / Netherite)"),
                    ColorUtil.color("&eRight-Click: &fCycle Color Palette"),
                    ColorUtil.color("&dClick with Leather Chestplate or Dye on cursor to apply exact color!")
            ));
            armor.setItemMeta(lMeta);
        } else {
            ItemMeta meta = armor.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtil.color("&dArmor Material"));
                meta.setLore(List.of(
                        ColorUtil.color("&7Material: &e" + session.getArmorMaterial().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aLeft-Click: &fCycle Armor (Leather / Iron / Diamond / Netherite)")
                ));
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
