package com.minionx;

import com.minionx.api.CustomFuel;
import com.minionx.api.MinionType;
import com.minionx.api.event.*;
import com.minionx.core.Minion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class CustomEventsTest {

    private Player createMockPlayer(UUID uuid) {
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getUniqueId".equals(method.getName())) return uuid;
                    if ("getName".equals(method.getName())) return "EventPlayer";
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Verify MinionPlaceEvent contract and cancellation")
    void testMinionPlaceEvent() {
        Player player = createMockPlayer(UUID.randomUUID());
        Minion minion = new Minion(UUID.randomUUID(), player.getUniqueId(), MinionType.COBBLESTONE, 1, null);
        Location loc = new Location(null, 10, 65, 10);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);

        MinionPlaceEvent event = new MinionPlaceEvent(player, minion, loc, item);
        assertSame(player, event.getPlayer());
        assertSame(minion, event.getMinion());
        assertSame(loc, event.getLocation());
        assertEquals(Material.PLAYER_HEAD, event.getMinionItem().getType());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Verify MinionPickupEvent contract and cancellation")
    void testMinionPickupEvent() {
        Player player = createMockPlayer(UUID.randomUUID());
        Minion minion = new Minion(UUID.randomUUID(), player.getUniqueId(), MinionType.IRON, 1, null);

        MinionPickupEvent event = new MinionPickupEvent(player, minion);
        assertSame(player, event.getPlayer());
        assertSame(minion, event.getMinion());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Verify MinionHarvestEvent drops inspection and modification")
    void testMinionHarvestEvent() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 11, null);
        List<ItemStack> drops = List.of(new ItemStack(Material.DIAMOND, 2));

        MinionHarvestEvent event = new MinionHarvestEvent(minion, drops, 5);
        assertSame(minion, event.getMinion());
        assertEquals(1, event.getDrops().size());
        assertEquals(5, event.getXpGained());

        // Modify drops & XP
        event.setDrops(List.of(new ItemStack(Material.DIAMOND_BLOCK, 1)));
        event.setXpGained(10);
        assertEquals(Material.DIAMOND_BLOCK, event.getDrops().get(0).getType());
        assertEquals(10, event.getXpGained());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Verify MinionUpgradeEvent tier upgrading contract")
    void testMinionUpgradeEvent() {
        Player player = createMockPlayer(UUID.randomUUID());
        Minion minion = new Minion(UUID.randomUUID(), player.getUniqueId(), MinionType.IRON, 1, null);

        MinionUpgradeEvent event = new MinionUpgradeEvent(player, minion, 1, 2);
        assertSame(player, event.getPlayer());
        assertSame(minion, event.getMinion());
        assertEquals(1, event.getOldTier());
        assertEquals(2, event.getNewTier());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Verify MinionStorageLinkEvent chest linking contract")
    void testMinionStorageLinkEvent() {
        Player player = createMockPlayer(UUID.randomUUID());
        Minion minion = new Minion(UUID.randomUUID(), player.getUniqueId(), MinionType.COBBLESTONE, 1, null);
        Location chestLoc = new Location(null, 11, 65, 10);

        MinionStorageLinkEvent event = new MinionStorageLinkEvent(player, minion, chestLoc, true);
        assertSame(player, event.getPlayer());
        assertSame(minion, event.getMinion());
        assertSame(chestLoc, event.getStorageLocation());
        assertTrue(event.isLinking());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Verify MinionQuarantineEvent status and reason contract")
    void testMinionQuarantineEvent() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.IRON, 1, null);

        MinionQuarantineEvent event = new MinionQuarantineEvent(minion, true, "Database timeout");
        assertSame(minion, event.getMinion());
        assertTrue(event.isQuarantined());
        assertEquals("Database timeout", event.getReason());
    }

    @Test
    @DisplayName("Verify MinionFuelConsumeEvent fuel burning contract")
    void testMinionFuelConsumeEvent() {
        Minion minion = new Minion(UUID.randomUUID(), UUID.randomUUID(), MinionType.DIAMOND, 1, null);
        CustomFuel fuel = new CustomFuel("coal_fuel", "Coal Fuel", Material.COAL, 1.2, 3600000L, Collections.emptyList(), false, 0);

        MinionFuelConsumeEvent event = new MinionFuelConsumeEvent(minion, fuel, false);
        assertSame(minion, event.getMinion());
        assertSame(fuel, event.getFuel());
        assertFalse(event.isBurnedOut());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("Verify MinionCollectionEvent collection tracking contract")
    void testMinionCollectionEvent() {
        Player player = createMockPlayer(UUID.randomUUID());
        Minion minion = new Minion(UUID.randomUUID(), player.getUniqueId(), MinionType.DIAMOND, 7, null);
        List<ItemStack> collected = List.of(new ItemStack(Material.DIAMOND, 64));

        MinionCollectionEvent event = new MinionCollectionEvent(player, minion, collected, 12);
        assertSame(player, event.getPlayer());
        assertSame(minion, event.getMinion());
        assertEquals(1, event.getCollectedItems().size());
        assertEquals(Material.DIAMOND, event.getCollectedItems().get(0).getType());
        assertEquals(12, event.getXpCollected());

        event.setXpCollected(24);
        assertEquals(24, event.getXpCollected());

        assertFalse(event.isCancelled());
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }
}
