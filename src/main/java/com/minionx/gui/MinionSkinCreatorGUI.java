package com.minionx.gui;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
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
 * 54-slot In-Game Custom Skin Creator Studio GUI.
 */
@SuppressWarnings("deprecation")
public class MinionSkinCreatorGUI {

    public static final String TITLE = ColorUtil.color("&8Minion Skin Creator");

    public static void open(Player player, MinionSkinCreatorSession session) {
        if (session == null) {
            session = new MinionSkinCreatorSession("custom_skin");
        }

        MinionSkinCreatorHolder holder = new MinionSkinCreatorHolder(session);
        Inventory inv = Bukkit.createInventory(holder, 54, TITLE);

        ItemStack border = createPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, border);

        // Slot 2: Skin Identity (ID & Display Name)
        inv.setItem(2, createNamedItem(Material.NAME_TAG, ColorUtil.color("&eSkin Identity"),
                List.of(ColorUtil.color("&7ID: &f" + session.getId()),
                        ColorUtil.color("&7Display Name: &f" + session.getDisplayName()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to change ID / Name via chat"))));

        // Slot 4: Skin Preview Head
        inv.setItem(4, createPreviewHead(session));

        // Slot 10: Head Texture Setup
        inv.setItem(10, createNamedItem(Material.PLAYER_HEAD, ColorUtil.color("&bHead / Skull Texture"),
                List.of(ColorUtil.color("&7Current Texture: &f" + (session.getSkullTexture() != null ? "Set" : "Default")),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick with a Player Head on cursor"),
                        ColorUtil.color("&eto copy its skin texture!"),
                        ColorUtil.color("&7or click to enter Base64 / Head ID in chat."))));

        // Slot 12: Armor Material Selector
        String armorName = session.getArmorMaterial() != null ? session.getArmorMaterial().name() : "LEATHER";
        inv.setItem(12, createNamedItem(session.getArmorMaterial() != null ? session.getArmorMaterial() : Material.LEATHER_CHESTPLATE,
                ColorUtil.color("&aArmor Material: &e" + armorName),
                List.of(ColorUtil.color("&7Material: &f" + armorName),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick to cycle armor types:"),
                        ColorUtil.color("&7Leather -> Chainmail -> Iron -> Gold -> Diamond -> Netherite"))));

        // Slot 14: Leather Color Tint (RGB)
        Color c = session.getLeatherColor() != null ? session.getLeatherColor() : Color.fromRGB(240, 180, 40);
        ItemStack dye = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta meta = dye.getItemMeta();
        if (meta instanceof LeatherArmorMeta lam) {
            lam.setColor(c);
            lam.setDisplayName(ColorUtil.color("&dLeather RGB Tint"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&7RGB: &f(" + c.getRed() + ", " + c.getGreen() + ", " + c.getBlue() + ")"));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&eClick to cycle color presets"));
            lore.add(ColorUtil.color("&eRight-click to input RGB via chat"));
            lam.setLore(lore);
            dye.setItemMeta(lam);
        }
        inv.setItem(14, dye);

        // Slot 16: Custom Hand Tool / Weapon
        ItemStack hand = session.getHandItem();
        Material handMat = hand != null ? hand.getType() : Material.DIAMOND_SWORD;
        inv.setItem(16, createNamedItem(handMat, ColorUtil.color("&6Held Hand Weapon/Tool"),
                List.of(ColorUtil.color("&7Current: &f" + handMat.name()),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&eClick with item on cursor to set"),
                        ColorUtil.color("&eor click to cycle common weapons/tools"))));

        // Slot 45: Cancel / Close
        inv.setItem(45, createNamedItem(Material.ARROW, ColorUtil.color("&cClose Studio"),
                List.of(ColorUtil.color("&7Discard unsaved changes"))));

        // Slot 49: Save & Register Skin
        inv.setItem(49, createNamedItem(Material.EMERALD_BLOCK, ColorUtil.color("&a&lSAVE & REGISTER SKIN"),
                List.of(ColorUtil.color("&7Saves to &fskins.yml &7and"),
                        ColorUtil.color("&7registers globally across all minions!"),
                        ColorUtil.color("&7"),
                        ColorUtil.color("&aClick to save!"))));

        player.openInventory(inv);
    }

    private static ItemStack createPreviewHead(MinionSkinCreatorSession session) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.color(session.getDisplayName() + " &7[Preview]"));
            List<String> lore = new ArrayList<>();
            lore.add(ColorUtil.color("&7ID: &f" + session.getId()));
            lore.add(ColorUtil.color("&7Armor: &f" + (session.getArmorMaterial() != null ? session.getArmorMaterial().name() : "LEATHER")));
            lore.add(ColorUtil.color("&7Hand Item: &f" + (session.getHandItem() != null ? session.getHandItem().getType().name() : "DIAMOND_SWORD")));
            lore.add(ColorUtil.color("&7"));
            lore.add(ColorUtil.color("&aPreview reflects skin settings"));
            meta.setLore(lore);

            if (session.getSkullTexture() != null && !session.getSkullTexture().isEmpty()) {
                applyTexture(meta, session.getSkullTexture());
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    public static void applyTexture(SkullMeta meta, String texture) {
        if (texture == null || texture.isEmpty()) return;
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            meta.setPlayerProfile(profile);
        } catch (Throwable ignored) {}
    }

    private static ItemStack createNamedItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack createPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
