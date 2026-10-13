package com.minionx;

import com.minionx.api.*;
import com.minionx.core.Minion;
import com.minionx.gui.UpgradeCreatorSession;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class AutoSellerAndCustomPricesTest {

    @BeforeEach
    void setup() {
        UpgradeRegistry.load();
        MinionStorageRegistry.load();
        PriceRegistry.load(new YamlConfiguration());
    }

    @Test
    @DisplayName("Should register default Budget and Enchanted hoppers with correct sell ratios")
    void testBudgetAndEnchantedHopperRegistration() {
        CustomUpgrade budgetHopper = UpgradeRegistry.get("budget_hopper");
        assertNotNull(budgetHopper);
        assertTrue(budgetHopper.isAutoSeller());
        assertEquals(0.50, budgetHopper.getSellRatio(), 0.001);

        CustomUpgrade enchantedHopper = UpgradeRegistry.get("enchanted_hopper");
        assertNotNull(enchantedHopper);
        assertTrue(enchantedHopper.isAutoSeller());
        assertEquals(0.90, enchantedHopper.getSellRatio(), 0.001);
    }

    @Test
    @DisplayName("Should configure custom auto seller in UpgradeCreatorSession and export CustomUpgrade")
    void testUpgradeCreatorSessionAutoSeller() {
        UpgradeCreatorSession session = new UpgradeCreatorSession("plasma_hopper");
        session.setDisplayName("&bPlasma Hopper");
        session.setMaterial(Material.HOPPER);
        session.setBehavior(CustomUpgrade.UpgradeBehavior.AUTO_SELLER);
        session.setSellRatio(0.95);
        session.setGlowing(true);

        CustomUpgrade upgrade = session.buildUpgrade();
        assertEquals("plasma_hopper", upgrade.getId());
        assertEquals("&bPlasma Hopper", upgrade.getDisplayName());
        assertEquals(Material.HOPPER, upgrade.getMaterial());
        assertTrue(upgrade.isAutoSeller());
        assertEquals(0.95, upgrade.getSellRatio(), 0.001);
        assertTrue(upgrade.isGlowing());

        UpgradeRegistry.register(upgrade);
        assertEquals(upgrade, UpgradeRegistry.get("plasma_hopper"));
    }

    @Test
    @DisplayName("Should query base prices for vanilla materials in PriceRegistry")
    void testPriceRegistryVanillaPrices() {
        assertEquals(1.0, PriceRegistry.getPrice(Material.COBBLESTONE), 0.001);
        assertEquals(8.0, PriceRegistry.getPrice(Material.DIAMOND), 0.001);
        assertEquals(12.0, PriceRegistry.getPrice(Material.OBSIDIAN), 0.001);

        ItemStack cobble = new ItemStack(Material.COBBLESTONE, 64);
        assertEquals(1.0, PriceRegistry.getPrice(cobble), 0.001);
    }

    @Test
    @DisplayName("Should support custom item prices via CustomItemSpec and custom-prices section")
    void testPriceRegistryCustomItemSupport() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prices.COBBLESTONE", 1.0);
        config.set("prices.DIAMOND", 8.0);
        config.set("prices.ENCHANTED_COBBLESTONE", 160.0);
        config.set("custom-prices.ruby_crystal.material", "EMERALD");
        config.set("custom-prices.ruby_crystal.custom-model-data", 505);
        config.set("custom-prices.ruby_crystal.price", 250.0);

        PriceRegistry.load(config);

        assertEquals(160.0, PriceRegistry.getAllPrices().get("ENCHANTED_COBBLESTONE"), 0.001);
        assertEquals(1, PriceRegistry.getCustomPrices().size());
        assertEquals("ruby_crystal", PriceRegistry.getCustomPrices().getFirst().getId());
        assertEquals(250.0, PriceRegistry.getCustomPrices().getFirst().getPrice(), 0.001);
    }

    @Test
    @DisplayName("Should overflow minion items to Budget Hopper when internal inventory is full")
    void testMinionOverflowToBudgetHopper() {
        Minion minion = new Minion(UUID.randomUUID(), MinionType.COBBLESTONE, 1);
        minion.setShippingId("budget_hopper");
        minion.setShippingCoins(0.0);

        assertTrue(minion.hasAutoSeller());
        assertEquals(0.50, minion.getShippingUpgrade().getSellRatio(), 0.001);

        // Fill single internal slot
        minion.getInventory().add(new ItemStack(Material.COBBLESTONE, 64));

        // When inventory is full, canAccept must still be true because auto seller will sell
        assertTrue(minion.canAccept(new ItemStack(Material.COBBLESTONE, 32)));

        // Adding items when full must sell and accumulate shipping coins
        boolean accepted = minion.addItem(new ItemStack(Material.COBBLESTONE, 32));
        assertTrue(accepted);

        // 32 cobblestone * 1.0 price * 50% ratio = 16.0 coins
        assertEquals(16.0, minion.getShippingCoins(), 0.001);

        // Claiming shipping coins resets accumulated balance
        double claimed = minion.claimShippingCoins();
        assertEquals(16.0, claimed, 0.001);
        assertEquals(0.0, minion.getShippingCoins(), 0.001);
    }

    @Test
    @DisplayName("Should overflow through linked storage and then to Enchanted Hopper when both are full")
    void testMinionOverflowThroughStorageToEnchantedHopper() {
        Minion minion = new Minion(UUID.randomUUID(), MinionType.DIAMOND, 1);
        minion.setShippingId("enchanted_hopper");
        minion.setShippingCoins(0.0);

        // Link small storage (3 slots)
        CustomMinionStorage smallStorage = MinionStorageRegistry.get("small_storage");
        assertNotNull(smallStorage);
        minion.linkStorage(null, "small_storage");

        // Fill internal inventory (1 slot)
        minion.getInventory().add(new ItemStack(Material.DIAMOND, 64));

        // Fill linked storage (3 slots)
        minion.getStorageContents().add(new ItemStack(Material.DIAMOND, 64));
        minion.getStorageContents().add(new ItemStack(Material.DIAMOND, 64));
        minion.getStorageContents().add(new ItemStack(Material.DIAMOND, 64));

        // When both internal and linked storage are full, auto-seller handles incoming items
        assertTrue(minion.canAccept(new ItemStack(Material.DIAMOND, 10)));

        boolean accepted = minion.addItem(new ItemStack(Material.DIAMOND, 10));
        assertTrue(accepted);

        // 10 diamonds * 8.0 price * 90% enchanted hopper ratio = 72.0 coins
        assertEquals(72.0, minion.getShippingCoins(), 0.001);
    }
}
