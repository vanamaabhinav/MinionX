package com.skyblock.minions.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.skyblock.minions.api.MinionArchetype;
import com.skyblock.minions.util.ColorUtil;
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

        // Slot 10: Minion Archetype (MINER vs COMBAT)
        Material archMat = session.getArchetype() == MinionArchetype.COMBAT ? Material.IRON_SWORD : Material.DIAMOND_PICKAXE;
        inv.setItem(10, createNamedItem(archMat, ColorUtil.color("&6Minion Archetype"),
                List.of(ColorUtil.color("&7Current: &e" + session.getArchetype().name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&7- &fMINER: &7Generates & mines blocks in 5x5"),
                        ColorUtil.color("&7- &fCOMBAT: &7Spawns & slays mobs (Vanilla/MythicMobs)"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to toggle Archetype"))));

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

        // Slot 28: Target Block / Mob
        if (session.getArchetype() == MinionArchetype.MINER) {
            inv.setItem(28, createNamedItem(session.getTargetBlock(), ColorUtil.color("&aTarget Block (Miner)"),
                    List.of(ColorUtil.color("&7Target Block: &f" + session.getTargetBlock().name()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aClick with any solid block on cursor to set target!"),
                            ColorUtil.color("&eOr click to cycle common ores"))));
        } else {
            inv.setItem(28, createNamedItem(Material.SPAWNER, ColorUtil.color("&cTarget Mob (Combat)"),
                    List.of(ColorUtil.color("&7Target Mob: &f" + session.getTargetMob()),
                            ColorUtil.color("&7"),
                            ColorUtil.color("&aLeft-Click: &fCycle vanilla mobs"),
                            ColorUtil.color("&dRight-Click: &fEnter MythicMob ID in chat"))));
        }

        // Slot 30: Exact Drop Item
        ItemStack drop = session.getExactDropItem().clone();
        ItemMeta dMeta = drop.getItemMeta();
        String dropDispName = dMeta != null && dMeta.hasDisplayName() ? dMeta.getDisplayName() : drop.getType().name();
        List<String> dLore = new ArrayList<>();
        dLore.add(ColorUtil.color("&7Configured Drop: &f" + dropDispName));
        dLore.add(ColorUtil.color("&7Amount: &fx" + drop.getAmount()));
        dLore.add(ColorUtil.color("&7"));
        dLore.add(ColorUtil.color("&eClick with ANY custom item on cursor to set!"));
        dLore.add(ColorUtil.color("&7Preserves lore, NBT, enchants & custom model data."));
        if (dMeta != null) {
            dMeta.setLore(dLore);
            drop.setItemMeta(dMeta);
        }
        inv.setItem(30, drop);

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

            String targetDesc = session.getArchetype() == MinionArchetype.MINER
                    ? session.getTargetBlock().name()
                    : session.getTargetMob();

            ItemStack drop = session.getExactDropItem();
            String dropName = drop != null && drop.hasItemMeta() && drop.getItemMeta().hasDisplayName()
                    ? drop.getItemMeta().getDisplayName()
                    : (drop != null ? drop.getType().name() : "None");

            meta.setLore(List.of(
                    ColorUtil.color("&7Archetype: &e" + session.getArchetype().name()),
                    ColorUtil.color("&7Target: &e" + targetDesc),
                    ColorUtil.color("&7Exact Drop: &e" + dropName + " &7(x" + (drop != null ? drop.getAmount() : 1) + ")"),
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
