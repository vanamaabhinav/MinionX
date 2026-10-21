# MinionX — Complete Guide & Documentation

> **MinionX** is an autonomous minion plugin designed for **Paper, Purpur, and Pufferfish (1.20.4 – 1.21+)**.  
> **Author:** AbhinavVanama  
> **Version:** `v1.0.0`  

---

## Table of Contents
1. [Overview](#1-overview)
2. [Interactive Minion Dashboard](#2-interactive-minion-dashboard)
3. [Automated Shipping & Economy System](#3-automated-shipping--economy-system)
4. [External Minion Storage Chests](#4-external-minion-storage-chests)
5. [Upgrades, Compression & Accessories](#5-upgrades-compression--accessories)
6. [Fuel System (Temporary & Permanent)](#6-fuel-system-temporary--permanent)
7. [Cosmetic Minion Skins & Armor Sets](#7-cosmetic-minion-skins--armor-sets)
8. [Per-Minion Tier Customization](#8-per-minion-tier-customization)
9. [Multi-Drop Engine & Custom Items](#9-multi-drop-engine--custom-items)
10. [Offline Production & Catch-Up](#10-offline-production--catch-up)
11. [RPG Skill Rewards & Collections](#11-rpg-skill-rewards--collections)
12. [Dialogue & Visual Displays](#12-dialogue--visual-displays)
13. [In-Game Creator Studios](#13-in-game-creator-studios)
14. [Operator Control Center (`/minionsx`)](#14-the-operator-control-center-minionsx)
15. [Configuration Files Guide](#15-configuration-files-guide)
16. [Land Claim & Protection Integrations](#16-land-claim--protection-integrations)
17. [Security & Emergency Quarantine](#17-security--emergency-quarantine)
18. [Commands & Permissions Reference](#18-commands--permissions-reference)
19. [Frequently Asked Questions (FAQ)](#19-frequently-asked-questions-faq)

---

## 1. Overview

MinionX brings automated resource gathering to your server, inspired by classic skyblock minion mechanics while offering modern visual entities, full in-game creator menus, and extensible configurations.

### Key Capabilities:
* **Included Minion Types**: Comes with over 30 pre-configured minions across mining, farming, foraging, and combat categories (such as Cobblestone, Iron, Diamond, Wheat, Oak, Zombie, and Blaze).
* **Flexible Customization**: Server administrators can add brand-new minions, multi-drop loot tables, custom working delays, and unique item requirements either through configuration files or directly in-game.
* **Optimized Architecture**: Uses modern text display entities for floating nametags, non-ticking armor stands for minion models, a bounded generational priority queue scheduler, and offline catch-up simulation for unloaded chunks.
* **Extensive Compatibility**: Natively supports major land claim systems, economy providers, skill plugins, and custom item frameworks.

---

## 2. Interactive Minion Dashboard

When a player places a minion, it appears in the world with appropriate tools, armor, and floating status information, and begins working within its designated area.

Right-clicking the minion opens its **54-slot interactive control menu**:

```
+----+----+----+----+----+----+----+----+----+
| -- | -- | -- |TORC|HEAD|GOLD| -- | -- | -- |  Row 1: Information & Tier Status
+----+----+----+----+----+----+----+----+----+
| -- |SKIN| -- | -- | -- | -- | -- | -- | -- |  Row 2: Cosmetic Skin Slot
+----+----+----+----+----+----+----+----+----+
| -- |FUEL| -- | [1]  [2]  [3]  [4]  [5] | -- |  Row 3: Fuel Socket + Storage Grid
+----+----+----+----+----+----+----+----+----+
| -- |HOPR| -- | [6]  [7]  [8]  [9] [10] | -- |  Row 4: Automated Shipping + Storage Grid
+----+----+----+----+----+----+----+----+----+
| -- |UPG1| -- |[11] [12] [13] [14] [15] | -- |  Row 5: Upgrade Slot 1 + Storage Grid
+----+----+----+----+----+----+----+----+----+
| -- |UPG2| -- |COLT| -- |QUCK| -- | -- |BEDR|  Row 6: Upgrade Slot 2 + Quick Actions
+----+----+----+----+----+----+----+----+----+
```

### Dashboard Slot Guide:
* **Top Row (Information & Layout)**:
  * **Slot 3 (Redstone Torch) — Ideal Layout**: Provides a visual guide showing the required working area around the minion. Base minions work in a 5×5 area (radius 2), which expands to 7×7 with 1 Minion Expander, or 9×9 with 2 Minion Expanders.
  * **Slot 4 (Minion Head) — Minion Status**: Displays current tier, action cooldown, storage capacity, and total items harvested.
  * **Slot 5 (Gold Ingot) — Next Tier Info**: Displays the required crafting materials needed to upgrade the minion to its next tier.
* **Left Column (Modifiers & Automation)**:
  * **Slot 10 — Minion Skin**: Allows players to apply cosmetic skins that change head textures, armor materials, and dye colors.
  * **Slot 19 — Fuel**: Accepts temporary fuels (such as Coal or Enchanted Charcoal) or permanent fuels (like the Enchanted Lava Bucket) to reduce action delays.
  * **Slot 28 — Automated Shipping**: Accepts Budget or Enchanted Hoppers to automatically sell excess items when storage is full.
  * **Slot 37 — Upgrade Slot 1**: Holds functional upgrades such as the Super Compactor 3000 or Auto-Smelter.
  * **Slot 46 — Upgrade Slot 2**: Holds a secondary upgrade, such as Diamond Spreading, Minion Expander, or Flycatcher.
* **Center Grid (Internal Storage — 15 Slots)**:
  * Slots `21–25`, `30–34`, and `39–43` represent the minion's internal inventory.
  * Unlocked slots hold harvested resources.
  * Higher slots unlock progressively as the minion is leveled up. Locked slots show the required tier needed to unlock them.
* **Bottom Row (Quick Actions)**:
  * **Slot 48 (Chest) — Collect All**: Moves all stored items from both internal storage and linked external chests into the player's inventory, and claims any accumulated shipping coins.
  * **Slot 50 (Diamond) — Quick-Upgrade**: Instantly upgrades the minion to the next tier if the player has the required resources in their inventory.
  * **Slot 53 (Bedrock) — Pickup Minion**: Safely picks up the minion, returning the minion item, upgrades, active fuel, skins, and stored resources to the player.

---

## 3. Automated Shipping & Economy System

To prevent minions from halting when their storage fills up, Automated Shipping hoppers can be equipped to sell overflow items directly to the server economy.

### How It Operates:
1. Place a shipping hopper into **Slot 28**.
2. Items fill the minion's internal inventory first.
3. Once the internal inventory is full, items overflow into any adjacent **linked minion storage chest**.
4. When both internal storage and linked chests are full, the equipped hopper converts further overflow items into coins based on prices defined in `prices.yml`.
5. Earned coins accumulate safely in the hopper slot. Left-clicking **Slot 28** deposits the coins directly into the player's economy account (via Vault or compatible economy providers).
6. Clicking **Collect All (Slot 48)** also claims accumulated coins automatically.

### Included Hoppers:
| Hopper | Sell Rate | Function |
|---|---|---|
| **Budget Hopper** | **50% of NPC Price** | Sells overflow items at half of their base server value. |
| **Enchanted Hopper** | **90% of NPC Price** | Higher-tier hopper selling overflow items at 90% value. |

*Note: Unpriced items (items with a value of 0 in `prices.yml`) will not be sold or discarded; the minion will safely pause until storage space is freed.*

---

## 4. External Minion Storage Chests

Players can expand a minion's holding capacity by placing a dedicated Minion Storage chest directly adjacent to the minion.

```
       [CHEST]  <-- Placed adjacent to minion
         [M]    <-- Minion automatically links!
```

### Storage Chest Tiers:
* **Small Storage**: Adds **3 extra slots** (1 row of 3).
* **Medium Storage**: Adds **9 extra slots** (3×3 centered grid).
* **Large Storage**: Adds **15 extra slots** (3×5 centered grid).
* **Max Storage**: Adds **27 extra slots** (full standard chest capacity).

### Security & Protection Features:
* **Owner Protection**: Only the minion owner or server operators can open or remove a linked storage chest.
* **Withdrawal-Only Mode**: Minion chests are designed for withdrawal only; players cannot accidentally insert unwanted items or drag external items into them.
* **Hopper Siphoning Prevention**: Vanilla hoppers, droppers, and hopper minecarts are prevented from pulling items out of or pushing items into minion chests.
* **Break Safety**: Breaking the chest safely drops all stored contents and the storage chest item on the ground.

---

## 5. Upgrades, Compression & Accessories

Minions can be equipped with up to two functional upgrades in **Slots 37 & 46**:

### Built-in Upgrades:
* **Super Compactor 3000**:
  * Automatically compresses 160 base items into their enchanted equivalents (for example, 160 Cobblestone → 1 Enchanted Cobblestone, 160 Diamonds → 1 Enchanted Diamond).
  * Significantly increases effective storage capacity by compressing items as they are harvested.
* **Compactor**:
  * Automatically compacts 9 raw items into their basic 3×3 block forms (such as 9 Iron Ingots → 1 Iron Block).
* **Auto-Smelter**:
  * Smelts harvested ores and materials automatically (Cobblestone → Stone, Raw Iron → Iron Ingot, Sand → Glass).
* **Diamond Spreading**:
  * Provides a 10% chance to generate a bonus diamond alongside standard harvest actions.
* **Minion Expander**:
  * Expands the minion's working area by +1 block in all directions (+1 adds a 7×7 area; equipping two expands to 9×9) and grants a small +5% speed bonus.
* **Flycatcher**:
  * Grants a passive +20% action speed increase.

Server owners can also create custom upgrades with unique speed buffs, area increases, or custom behaviors using the in-game studio or `upgrades.yml`.

---

## 6. Fuel System (Temporary & Permanent)

Fuels can be placed into **Slot 19** of the Minion GUI to reduce action delays and accelerate harvest rates.

### Fuel Types:
* **Temporary Fuels**: Provide a speed increase for a limited duration and are consumed over time.
  * **Coal**: +5% speed for 30 minutes.
  * **Enchanted Bread**: +5% speed for 2 hours.
  * **Enchanted Charcoal**: +20% speed for 36 hours.
  * *Stacking*: Adding more of the same temporary fuel extends the existing burn duration.
* **Permanent Fuels**: Provide a continuous speed buff that does not expire.
  * **Enchanted Lava Bucket**: +25% speed bonus permanently.
  * **Magma Bucket**: +30% speed bonus permanently.
  * **Plasma Bucket**: +35% speed bonus permanently.

*Configurable setting: `settings.destroy-temporary-fuel-on-removal` controls whether actively burning temporary fuel is consumed or returned when manually taken out of the socket.*

---

## 7. Cosmetic Minion Skins & Armor Sets

Cosmetic skins allow players to customize the appearance of their minions without altering their harvest mechanics.

* **Custom Skull Textures**: Supports custom head textures via Base64 textures or standard player usernames.
* **Complete Armor Sets**: Equipping a skin dresses the minion in matching armor (chestplate, leggings, and boots).
* **Leather Armor Dyeing**: Supports custom Hex color codes (`#RRGGBB`), RGB values, or standard color names to give leather armor distinct color palettes.
* **Metallic Armor Styles**: Supports metallic armor sets including Chainmail, Iron, Gold, Diamond, and Netherite.
* **Handheld Items**: Allows the minion to hold custom tools or items in its main hand (swords, pickaxes, axes, or novelty items).
* **Equipping**: Placed in **Slot 10** of the Minion GUI. Clicking an active skin with an empty cursor returns it safely to the player's inventory.

---

## 8. Per-Minion Tier Customization

Different minion types can be configured with distinct maximum tiers, action delays, storage capacities, and upgrade costs.

* **Global Defaults**: Defined in `tiers.yml` for Tiers 1 through 12+.
* **Individual Overrides**: Minions can have custom `max-tier` caps (such as capping a specialized minion at Tier 5 or Tier 10) and custom tier progression tables in `minions.yml`.
* **GUI Integration**: When a minion reaches its individual maximum tier, the upgrade button in its menu displays a maximum tier indicator.

---

## 9. Multi-Drop Engine & Custom Items

MinionX supports multi-drop tables and custom item frameworks for both harvest yields and compression recipes.

### Drop Capabilities:
* **Multiple Drops per Harvest**: A minion can produce primary and secondary items simultaneously (such as a Blaze minion yielding Blaze Rods at 100% and Magma Cream at 50%).
* **Drop Probabilities**: Each drop supports a decimal percentage chance (`1.0` = 100%, `0.33` = 33%).
* **Custom Item Attributes**: Supports custom display names, lore lines, CustomModelData numbers, enchantment glints, and PersistentDataContainer (PDC) keys.
* **Plugin Integrations**: Directly references items from plugins such as ItemsAdder (`itemsadder:ruby`), MMOItems (`mmoitems:TYPE_ID`), or Oraxen.
* **Metadata Protection**: The compression engine strictly respects custom item metadata, preventing vanilla recipes from accidentally consuming items that have custom names or models.

---

## 10. Offline Production & Catch-Up

When chunks unload or the server restarts, minions calculate their production using an offline catch-up calculation upon reload:

* **Time-Segmented Calculation**: The engine evaluates elapsed time since the minion's last action, accounting for fuel expiration boundaries during the offline window.
* **Capacity Saturation**: Completed production cycles are simulated sequentially. If storage reaches capacity, all completed cycles that fit are credited, the minion transitions to the `FULL` state, and the remaining offline duration does not accumulate unearned backlogs.
* **Overflow Handling**: If an Automated Shipping hopper is equipped, overflow items from filled storage convert into coins at the configured sell ratio.
* **Offline Limit**: The maximum simulated offline period can be capped in `config.yml` (default: 720 hours / 30 days).

---

## 11. RPG Skill Rewards & Collections

Collecting resources from minions can reward players with skill experience and advance server-wide collections.

### Skill Integrations:
* **Supported Plugins**: Hooks into **AuraSkills**, **EcoSkills**, and **mcMMO**.
* **Experience Rewards**: When players claim items using "Collect All" or by clicking storage slots, skill experience is granted based on the minion type (such as Mining XP for ore minions, Foraging XP for wood minions, or Farming XP for crop minions).
* **Configuration**: Experience values and associated skill names are customizable in `config.yml` under `skill-rewards`.

### Collection Integrations:
* **EcoCollections & SuperiorSkyblock2**: Automatically updates player and island collection statistics upon resource collection.
* **Custom Command Rewards**: Configurable commands can be triggered when players harvest resources (defined in `config.yml` under `collection-rewards`).

---

## 12. Dialogue & Visual Displays

Minions feature dynamic visual cues and floating status displays to provide feedback to players.

### Hologram Nametags:
* Rendered using lightweight modern display entities.
* Displays minion tier (in Roman numerals), name, and current status (`Idle`, `Generating`, `Full`, etc.).
* **Display Modes** (`config.yml`):
  * `ALWAYS`: Nametag remains visible at all times.
  * `TARGETED`: Nametag displays when a player looks toward the minion within interaction range.
  * `DISABLED`: Hides floating nametags.

### Speech Dialogues:
* Minions can occasionally display dialogue messages in a speech bubble above their head when working or interacting with their owner.
* Configurable dialogue text, appearance chances, and display durations are managed under `dialogues` in `config.yml`.

---

## 13. In-Game Creator Studios

MinionX provides interactive in-game menus allowing administrators to configure content without manual file editing:

* **Minion Creator (`/minion create`)**: Configure new minion types, target blocks or mobs, held items, primary/secondary drops, and tier limits.
* **Upgrade Creator (`/minion create upgrade`)**: Create custom automated shipping hoppers, compactors, speed catalysts, and accessories with adjustable sell ratios and speed boosts.
* **Fuel Creator (`/minion create fuel`)**: Create fuels with custom durations, speed multipliers, and glint effects.
* **Skin Creator (`/minion create skin`)**: Design cosmetic skins with custom skull textures, leather dye colors, and held equipment.
* **Storage Creator (`/minion create storage`)**: Create custom minion storage chests from 1 to 27 slots.
* **Tier Editor (`/minion tier` or `/minion tier <id>`)**: Adjust action delays, storage slot counts, and upgrade costs live. Supports creating custom tiers beyond Tier XII.

---

## 14. The Operator Command Center (`/minionsx`)

The Operator Command Center (`/minionsx` or `/mxop`) provides an administrative dashboard:

* **Minions Catalog (Slot 20)**: Inspect registered minions and claim test copies.
* **Upgrades Catalog (Slot 22)**: Browse all upgrades and launch the Upgrade Creator Studio.
* **Fuels Vault (Slot 24)**: Browse temporary and permanent fuels and launch the Fuel Creator Studio.
* **Skins Catalog (Slot 28)**: Browse available skins with direct item delivery.
* **Storages Catalog (Slot 34)**: Browse external storage chest models.
* **Emergency Quarantine (Slot 51)**: Quickly toggle quarantine mode across the server.

---

## 15. Configuration Files Guide

All settings are neatly separated across dedicated files in `plugins/MinionX/`:

| File | Purpose |
|---|---|
| `config.yml` | General server settings, limits, storage backends (YAML, SQLite, single-instance MySQL), display options, and skill hooks. |
| `tiers.yml` | Default action delays, storage slot counts, and upgrade costs per tier. |
| `minions.yml` | Minion types, target blocks/mobs, drop tables, and per-minion tier overrides. |
| `fuels.yml` | Temporary and permanent fuel definitions and speed multipliers. |
| `upgrades.yml` | Functional accessories, compactors, auto-smelters, and shipping hoppers. |
| `skins.yml` | Cosmetic skin definitions, armor colors, and held items. |
| `storages.yml` | Minion storage chest models and capacities. |
| `prices.yml` | Base NPC prices used by shipping hoppers to calculate sell earnings. |
| `recipes.yml` | Compression recipes used by the Super Compactor 3000. |
| `messages.yml` | Configurable chat messages, prefixes, and minion dialogue lines. |

---

## 16. Land Claim & Protection Integrations

MinionX checks placement and interaction permissions against major protection plugins:

* **WorldGuard**: Checks build and block placement permissions.
* **GriefPrevention**: Respects claim trust permissions.
* **Towny**: Verifies resident plot build permissions.
* **Lands**: Respects container and placement flags.
* **BentoBox & SuperiorSkyblock2**: Respects island member and visitor permissions.

---

## 17. Security, Escrow Durability & Emergency Quarantine

MinionX includes safety controls designed to protect player data, prevent item/XP duplication, and ensure 100% crash recovery:

* **Thread-Safe Data Handling**: Persistence flushes occur on a dedicated worker while snapshotting occurs on the primary server thread with atomic revisions.
* **Container Isolation**: Storage chests cannot be accessed by vanilla hoppers, droppers, or minecarts.
* **Atomic Pickup & Durable Escrow**:
  * Minion pickup is executed in a multi-phase atomic sequence: the minion and state are committed to a durable escrow operation (`minion_pending_operations`) before world entities are removed.
  * Stored XP is embedded into the durable payload (`XP:<xp>;;;PAYLOAD:::<items>`) from Phase 1.
* **Crash-Safety Boundary & Zero-Leftover XP Invariant**:
  * Both `/minion claim` and `/minion recover` write and positively confirm `DELIVERY_ATTEMPTED` on disk before modifying player inventory or granting XP.
  * If a server crashes or halts mid-delivery, the operation reboots in `DELIVERY_ATTEMPTED` (or `UNKNOWN`), strictly blocking automatic player re-claims and ordinary recovery.
  * Stored XP is **only** awarded when inventory delivery has **zero leftovers**. If inventory fills up, unclaimed XP is safely preserved alongside leftover items.
  * If persisting leftovers fails in `/minion claim` or `/minion recover`, the operation is fenced to `AMBIGUOUS_PARTIAL_DELIVERY` with a canonical single-XP emergency audit ledger to prevent duplicate replay.
  * Ordinary recovery (`/minion recover resolve <opId> deliver`) strictly rejects ambiguous operations (`DELIVERY_ATTEMPTED`, `UNKNOWN`, `AMBIGUOUS_PARTIAL_DELIVERY`, `COMPLETION_UNCONFIRMED`).
  * If items are delivered under `deliver-force-all` while XP remains unresolved, the XP-only ledger update is verified durably, retaining pending XP until explicitly audited (`deliver-force-all-with-xp` or `complete`), and fencing on persistence failure to prevent stale item replay.
  * **Ambiguous Leftover State Preservation & `XP_AMBIGUOUS` Defense**:
    * If an administrator uses `deliver-force-all` on an ambiguous operation and inventory fills up, leftover state selection is strictly decoupled from XP presence: any operation with ambiguous delivery history preserves `DELIVERY_ATTEMPTED` (never downgraded to `QUARANTINED_LEFTOVERS`), even if the operation has no XP or its XP was already delivered.
    * Both `/minion claim` and ordinary recovery (`deliver` / `deliver-force-all`) strictly withhold `XP_AMBIGUOUS` XP—even if the stored state is nominally safe (`QUARANTINED_LEFTOVERS`)—ensuring automatic claims can never award ambiguous XP. Only the explicit administrative override `/minion recover resolve <opId> deliver-force-all-with-xp` can grant it.
    * When items are claimed under `/minion claim` while XP remains ambiguous, updating the durable ledger to the XP-only payload is strictly verified; on persistence failure, the operation is immediately fenced to `AMBIGUOUS_PARTIAL_DELIVERY` with empty leftovers (`LEFTOVER:::`) to prevent stale item replay.
    * **Malformed XP Payload Retention & Quarantine Invariant**: If an operation's XP amount in `XP_AMBIGUOUS:`, `XP:`, `XP_GRANTED:`, or `XP_DELIVERED:` is malformed or invalid (e.g. non-numeric string or integer overflow), `xpParseFailed` is set to `true`. Parsing failure independently requires escrow retention (`xpRemainsUnresolved = xpParseFailed || ...`), guaranteeing that malformed records are NEVER deleted even if marked as already delivered (`XP_GRANTED:` / `XP_DELIVERED:`). Automatic XP grants are suppressed, the raw token is preserved (`XP_AMBIGUOUS:<rawToken>;;;PAYLOAD:::`) under `DELIVERY_ATTEMPTED` in the escrow ledger, and the operation is held for manual administrative audit.
    * **Missing Delimiter & Negative XP Detection**: Recognized XP prefixes (`XP_DELIVERED:`, `XP_GRANTED:`, `XP_AMBIGUOUS:`, `XP_PENDING:`, `XP:`) are detected before delimiter validation. If the `;;;PAYLOAD:::` delimiter is absent or invalid, or if the parsed XP amount is negative (`parsed < 0`), parsing is rejected (`xpParseFailed = true`, `xpToGrant = 0`), automatic XP grants are prohibited, and the record is held for manual audit.
    * **Emergency Fallback Write Verification**: Both primary ledger persistence and emergency fallback writes (`AMBIGUOUS_PARTIAL_DELIVERY`) strictly inspect boolean return values. If an emergency fallback write fails, the operation remains locked in the last confirmed ambiguous fence state, automatic item/XP claims remain blocked, and an alert is logged to warn administrators of an unconfirmed ledger update.
    * **Item Delimiter Collision Escaping**: All serialized YAML item payloads are escaped via `MinionPayloadUtil.escapeItemPayload`, percent-encoding `%` and reserved delimiters (`;;;ITEM;;;`, `;;;PAYLOAD:::`, `;;;LEFTOVER:::`). Item stacks with arbitrary display names, lores, or metadata can never corrupt ledger delimiter splitting upon deserialization.
    * **Automated Shipping Currency Overflow Protection**: In `Minion.java`, incoming coin values are checked with `Double.isFinite(coins) && coins >= 0.0001`. In-memory micro-coin addition uses `Math.addExact(this.shippingCoinsMicros, addMicros)` and catches `ArithmeticException`, safely rejecting overflowing credits and logging an audit warning without mutating the balance or manufacturing `Long.MAX_VALUE`.
* **Unresolved Payout Quarantine Fencing**:
  * Any automated shipping payout with an uncertain or interrupted outcome (`ATTEMPTED` or `PAYOUT_AMBIGUOUS`) is quarantined across restarts. Payout locks are automatically re-established in memory, and automatic Vault retries or rollbacks are forbidden.
* **Emergency Quarantine Mode (`/minion quarantine on`)**:
  * Temporarily freezes minion item collection, placement, and pickup across the server.
  * Allows administrators to perform maintenance or investigations safely.
  * Run `/minion quarantine off` to return minions to normal operation.

---

## 18. Commands & Permissions Reference

### Commands:
| Command | Permission | Description |
|---|---|---|
| `/minionsx` or `/mxop` | `minionx.admin` | Opens the Operator Command Center. |
| `/minion create [type]` | `minionx.admin` | Opens the in-game creator studio (`minion`, `fuel`, `skin`, `upgrade`, `storage`). |
| `/minion tier [id]` | `minionx.admin` | Opens the Global or Per-Minion Tier Studio. |
| `/minion give <player> <item> [tier]` | `minionx.admin` | Gives a minion head, fuel, upgrade, skin, or storage item. |
| `/minion limit <check\|set\|add> <player>` | `minionx.admin` | Manages player placement limits. |
| `/minion quarantine [on\|off\|status]` | `minionx.admin` | Toggles emergency quarantine mode. |
| `/minion list` | `minionx.use` | Lists a player's placed minions and locations. |
| `/minion claim` | `minionx.use` | Recovers verified escrow items and stored XP if the player's inventory was full during a previous pickup. Blocks ambiguous operations. |
| `/minion recover list` | `minionx.admin` | Lists all pending, quarantined, or ambiguous operations in the escrow ledger. |
| `/minion recover resolve <opId> complete [reason]` | `minionx.admin` | Marks an operation as COMPLETE and clears it from the ledger with an audit entry. |
| `/minion recover resolve <opId> deliver [reason]` | `minionx.admin` | Delivers verified escrow items and safe XP for unambiguous operations (rejects ambiguous operations to prevent duplication). |
| `/minion recover resolve <opId> deliver-force-all [reason]` | `minionx.admin` | Force-delivers items from an ambiguous operation (withholding unresolved XP in ledger until audited). |
| `/minion recover resolve <opId> deliver-force-all-with-xp [reason]` | `minionx.admin` | Audited administrative override that forces delivery of both items and stored XP after manual log verification. |
| `/minion killall` | `minionx.admin` | Removes all active minion entities across the world. |
| `/minion reload` | `minionx.admin` | Reloads all configuration files and item registries. |

### Permissions:
* `minionx.use`: Allows players to place, interact with, and view their minions (Default: `true`).
* `minionx.admin`: Access to administrative commands, studios, and `/minionsx` (Default: `op`).
* `minionx.bypass.limit`: Bypasses the placed minion limit.
* `minionx.limit.<number>`: Sets a custom placed minion limit for a player or permission group.

---

## 19. Frequently Asked Questions (FAQ)

**Q: How does Automated Shipping sell items when storage fills up?**  
A: When both the minion's internal inventory and any linked storage chest are full, equipped hoppers automatically sell further harvested items using prices in `prices.yml`. Earned coins accumulate in the hopper slot and can be collected at any time.

**Q: Can players take items from another player's minion chest?**  
A: No. Storage chests are protected by owner verification. Only the minion's owner or an operator can open the chest, and items can only be withdrawn.

**Q: How does offline catch-up work?**  
A: When an unloaded chunk reloads or the server restarts, MinionX computes elapsed downtime cycle-by-cycle across fuel boundaries, applying compaction recipes, and stopping gracefully if storage reaches capacity without dropping or deleting unpriced items.

**Q: Can custom minions have different maximum tiers?**  
A: Yes. You can set `max-tier` in `minions.yml` or through `/minion create` to limit specific minions to any tier you prefer.
