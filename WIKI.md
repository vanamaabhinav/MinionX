# MinionX — Complete Server Owner's Manual & Showcase Guide

> **MinionX** — The Premier Autonomous Minion Engine for **Paper / Purpur / Pufferfish 1.20.4 – 1.21+**  
> **Author:** AbhinavVanama  
> **Version:** `v0.2.0` (Production Release)

---

## Table of Contents
1. [Introduction & Vision](#1-introduction--vision)
2. [The Player Experience & Interactive Minion Dashboard](#2-the-player-experience--interactive-minion-dashboard)
3. [Automated Shipping & Economy System](#3-automated-shipping--economy-system)
4. [External Minion Storage Chests](#4-external-minion-storage-chests)
5. [Upgrades, Compression & Accessories](#5-upgrades-compression--accessories)
6. [Fuel System (Temporary vs. Permanent)](#6-fuel-system-temporary-vs-permanent)
7. [Cosmetic Minion Skins & Armor Sets](#7-cosmetic-minion-skins--armor-sets)
8. [The 6 In-Game Visual Studios (Zero YAML Editing)](#8-the-6-in-game-visual-studios-zero-yaml-editing)
9. [The Operator Command Center (`/minionsx`)](#9-the-operator-command-center-minionsx)
10. [Configuration Files Master Guide (Plain English)](#10-configuration-files-master-guide-plain-english)
    * [`config.yml` (Main Configuration)](#configyml-main-configuration)
    * [`prices.yml` (Economy & Custom Item Prices)](#pricesyml-economy--custom-item-prices)
    * [`tiers.yml` (Global Speeds, Slots & Costs)](#tiersyml-global-speeds-slots--costs)
    * [`upgrades.yml` (Custom Upgrades & Hoppers)](#upgradesyml-custom-upgrades--hoppers)
    * [`fuels.yml` (Fuels & Speed Boosts)](#fuelsyml-fuels--speed-boosts)
    * [`skins.yml` (Cosmetic Skins & Armor Sets)](#skinsyml-cosmetic-skins--armor-sets)
    * [`storages.yml` (Storage Expansion Capacities)](#storagesyml-storage-expansion-capacities)
    * [`minions.yml` (Minion Types & Drops)](#minionsyml-minion-types--drops)
    * [`recipes.yml` (Super Compactor Recipes)](#recipesyml-super-compactor-recipes)
11. [Supported Land Claim Integrations](#11-supported-land-claim-integrations)
12. [Anti-Dupe Security & Emergency Quarantine Mode](#12-anti-dupe-security--emergency-quarantine-mode)
13. [Commands & Permissions Reference](#13-commands--permissions-reference)
14. [Frequently Asked Questions (FAQ)](#14-frequently-asked-questions-faq)

---

## 1. Introduction & Vision

Welcome to **MinionX**! Inspired by the classic minion automation of Hypixel SkyBlock, MinionX provides a turnkey, commercial-grade minion engine engineered for survival networks, skyblock realms, prison servers, and custom RPG gamemodes.

### The Best of Both Worlds
* **Turnkey Out-of-the-Box Setup**: Ships pre-loaded with over 30 familiar minions (Cobblestone, Iron, Diamond, Pumpkin, Melon, Oak, Zombie, etc.), iconic accessories (Super Compactor 3000, Auto-Smelter, Diamond Spreading), shipping hoppers (Budget & Enchanted Hoppers), fuels (Enchanted Charcoal, Enchanted Lava Bucket), and cosmetic skins for immediate commercial deployment.
* **Limitless Customization Engine**: Server owners are never locked in. Effortlessly create custom minions, unique drops, MythicMobs combat encounters, custom working speeds, tailor-made fuels, and custom accessories both live in-game and through clean configuration files.
* **Rock-Solid Performance (20.0 TPS)**: Armor stands have ticking disabled (`setCanTick(false)`), floating holograms use modern display entities, and chunk loading uses O(1) mathematical catch-up instead of lagging chunk loads.

---

## 2. The Player Experience & Interactive Minion Dashboard

When a player places a minion, it wakes up with tools in hand, dresses in matching armor, spawns floating status text, and begins working within its 5×5 area.

Right-clicking the minion opens its **54-slot interactive control dashboard**:

```
+----+----+----+----+----+----+----+----+----+
| -- | -- | -- |TORC|HEAD|GOLD| -- | -- | -- |  Row 1: Requirements & Information
+----+----+----+----+----+----+----+----+----+
| -- |SKIN| -- | -- | -- | -- | -- | -- | -- |  Row 2: Skin Slot
+----+----+----+----+----+----+----+----+----+
| -- |FUEL| -- | [1]  [2]  [3]  [4]  [5] | -- |  Row 3: Fuel + Storage Grid
+----+----+----+----+----+----+----+----+----+
| -- |HOPR| -- | [6]  [7]  [8]  [9] [10] | -- |  Row 4: Shipping Hopper + Storage Grid
+----+----+----+----+----+----+----+----+----+
| -- |UPG1| -- |[11] [12] [13] [14] [15] | -- |  Row 5: Upgrade 1 + Storage Grid
+----+----+----+----+----+----+----+----+----+
| -- |UPG2| -- |COLT| -- |QUCK| -- | -- |BEDR|  Row 6: Upgrade 2 + Actions (Pickup)
+----+----+----+----+----+----+----+----+----+
```

### Dashboard Slot Guide:
* **Top Row (Requirements & Information)**:
  * **Slot 3 (Redstone Torch) — Ideal Layout**: Displays the exact block setup and environment required around the minion for it to spawn items or mobs efficiently.
  * **Slot 4 (Minion Head) — Minion Status**: Shows current tier, action speed cooldown, internal storage limit, and total items harvested.
  * **Slot 5 (Gold Ingot) — Next Tier**: Displays the exact crafting recipe and ingredients needed to craft the minion's next level.
* **Left Column (Modifiers & Automation)**:
  * **Slot 10 (Lime Stained Glass) — Minion Skin**: Equip cosmetic skins to transform the minion's visual appearance and armor.
  * **Slot 19 (Orange Stained Glass) — Fuel**: Place temporary fuels (Coal, Enchanted Charcoal) or permanent fuels (Enchanted Lava Bucket) to boost working speed.
  * **Slot 28 (Yellow Stained Glass) — Automated Shipping**: Equip Budget or Enchanted Hoppers to auto-sell overflow resources and collect accumulated coins.
  * **Slot 37 (Blue Stained Glass) — Minion Upgrade 1**: Socket functional upgrades like the Super Compactor 3000 or Auto-Smelter.
  * **Slot 46 (Blue Stained Glass) — Minion Upgrade 2**: Socket a secondary upgrade like Diamond Spreading or Minion Expander.
* **Center Grid (Internal Storage — 15 Slots)**:
  * Slots `21-25`, `30-34`, and `39-43`.
  * Unlocked slots hold harvested items.
  * Locked slots are indicated by Red Stained Glass titled `&cLocked Slot &7(Tier X)` showing the exact tier required to unlock that slot.
* **Bottom Row (Quick Actions)**:
  * **Slot 48 (Chest) — Collect All**: Instantly transfers all harvested items from internal storage AND linked storage chests into the player's inventory, and claims any shipping coins.
  * **Slot 50 (Diamond) — Quick-Upgrade**: Instantly levels up the minion if the player has the required upgrade materials in their inventory.
  * **Slot 53 (Bedrock) — Pickup Minion**: Safely dismantles the minion and returns the minion item, upgrades, fuel, and storage contents to the player.

---

## 3. Automated Shipping & Economy System

When minions run for days, their storage can eventually fill up. Automated Shipping ensures production never halts by selling excess items directly to the server economy.

### How Automated Shipping Works:
1. Place a shipping hopper (such as a **Budget Hopper** or **Enchanted Hopper**) into **Slot 28**.
2. As the minion works, items fill its internal inventory first.
3. If internal inventory is full, items automatically overflow into any **linked storage chest**.
4. When both internal inventory and the storage chest are completely full, the hopper automatically liquidates further overflow into server coins.
5. Coins accumulate inside the hopper slot. Left-clicking **Slot 28** claims the coins directly to your Vault economy balance!
6. Clicking **Collect All (Slot 48)** also automatically sweeps all shipping coins into your account.

### Pre-Configured Shipping Hoppers:
| Hopper | Sell Rate | Description |
|---|---|---|
| **Budget Hopper** | **50% NPC Price** | Sells items for 50% of their base server value when storage is full. |
| **Enchanted Hopper** | **90% NPC Price** | Premium high-efficiency hopper selling items for 90% of their base value. |

Server owners can also design completely custom hoppers (e.g. Plasma Hopper @ 95%) with custom sell percentages using the in-game studio or `upgrades.yml`.

---

## 4. External Minion Storage Chests

Players can expand their minion's holding capacity by placing an external Minion Storage chest directly adjacent to the minion.

```
       [CHEST]  <-- Placed next to minion
         [M]    <-- Minion automatically links!
```

### Storage Sizes:
* **Small Storage**: Expands capacity by **3 slots** (1 row of 3).
* **Medium Storage**: Expands capacity by **9 slots** (3×3 centered grid).
* **Large Storage**: Expands capacity by **15 slots** (3×5 centered grid).
* **Max Storage**: Expands capacity by **27 slots** (full chest).

### Storage Security & Protections:
* **Owner Verification**: Only the minion's owner (or administrators with `minionx.admin`) can open or break a minion storage chest. Other players receive an access denied message.
* **Withdrawal-Only Safety**: Minion storages are strictly withdrawal-only. Players cannot deposit personal items, shift-click junk in, or drag items into the storage chest.
* **Anti-Hopper Protection (Both Ways)**: Vanilla hoppers, droppers, and hopper minecarts are completely blocked from pulling items OUT of minion storages OR dropping/pushing items INTO minion storages.
* **Automatic Pickup**: Breaking the minion storage drops all held items and the storage item safely onto the ground.

---

## 5. Upgrades, Compression & Accessories

Minions can be customized with functional upgrades socketed into **Slots 37 & 46**:

### Built-in Upgrades:
* **Super Compactor 3000**:
  * Automatically compresses 160 base items into their enchanted forms (e.g., 160 Cobblestone → 1 Enchanted Cobblestone, 160 Diamonds → 1 Enchanted Diamond).
  * Drastically multiplies effective storage capacity by 160x.
* **Auto-Smelter**:
  * Automatically smelts harvested ores and materials (Cobblestone → Stone, Iron Ore → Iron Ingot, Gold Ore → Gold Ingot, Sand → Glass).
* **Diamond Spreading**:
  * Gives a 10% chance to generate an extra Diamond alongside the minion's standard harvest.
* **Minion Expander**:
  * Increases the minion's effective working radius by 1 block in every direction.

Administrators can create unlimited custom upgrades with custom speed boosts, bonus drops, or custom behaviors in-game or via `upgrades.yml`.

---

## 6. Fuel System (Temporary vs. Permanent)

Fuel items boost minion action speed when placed into **Slot 19**.

### Fuel Categories:
* **Temporary Fuels**: Burn over a duration and disappear once exhausted.
  * **Coal**: +5% speed for 30 minutes.
  * **Enchanted Bread**: +5% speed for 2 hours.
  * **Enchanted Charcoal**: +20% speed for 36 hours.
* **Permanent Fuels**: Provide an infinite speed boost that never runs out.
  * **Enchanted Lava Bucket**: Permanent +25% speed boost.
  * **Magma Bucket**: Permanent +30% speed boost.
  * **Plasma Bucket**: Permanent +35% speed boost.

---

## 7. Cosmetic Minion Skins & Armor Sets

Minion skins transform both the head texture and the armor color/material of minions on your server.

* **Custom Skull Textures**: Authentic offline-bundled heads give each minion a distinctive personality.
* **Full-Body Armor Sets & Color Tinting**: Equipping a skin styles the ArmorStand with a complete armor set (Chestplate, Leggings, and Boots). When an armor color is specified (e.g. `#D32F2F` or named colors), matching leather armor is dyed with that exact hex/RGB color.
* **Metallic Armor Materials**: Skins can also equip full metal armor sets (`DIAMOND`, `GOLD`, `IRON`, `NETHERITE`, or `CHAINMAIL`).
* **Custom Held Items**: Skins can equip weapons, tools, or special items in the minion's main hand (e.g. Swords, Sugar Cane, Emeralds).
* **Configurable in `skins.yml`**: Define custom server skins directly in `plugins/MinionX/skins.yml` or create them in-game via the Skin Creator Studio. Hot-reload with `/minion reload`.
* **Skin Socketing**: Equipped through **Slot 10** in the Minion GUI. Left-click with a skin on cursor to apply, or click empty-handed on an active skin to retrieve it safely.

---

## 8. The 6 In-Game Visual Studios (Zero YAML Editing)

MinionX provides six graphical studios allowing server staff to create, balance, and modify content directly in-game without touching configuration files or reloading the server:

### 1. Minion Creator Studio (`/minion create`)
* Configure custom minion archetypes in seconds.
* Set minion name, held tool, target block/crop/mob, primary and secondary drops, and drop chances.
* Configure per-minion maximum tier caps (e.g. Tier V, X, or XII).

### 2. Upgrade & Hopper Creator Studio (`/minion create upgrade` or `/minionsx` Slot 50)
* Create custom automated shipping hoppers, speed catalysts, and accessories live.
* Select item icon and customize display name.
* Cycle functions: `AUTO_SELLER` (hoppers), `AUTO_SMELTER`, `COMPACTOR`, `SUPER_COMPACTOR`, `DIAMOND_SPREADING`, or `SPEED_BUFF`.
* Tune sell ratios (+5% / -5%) and speed percentages with interactive click controls.
* Test your new upgrade immediately with a sample item and save directly to `upgrades.yml`.

### 3. Fuel Creator Studio (`/minion create fuel`)
* Configure fuels with custom speed boosts, temporary burn durations, or permanent mode.
* Toggle enchantment glint and save directly to `fuels.yml`.

### 4. Skin Creator Studio (`/minion create skin`)
* Create cosmetic minion skins with custom head textures and RGB leather armor colors.
* Assign custom held weapons and tools.

### 5. Storage Creator Studio (`/minion create storage`)
* Create custom chest storage expansions with capacities from 1 to 27 slots.
* Customize display name, lore, and icon.

### 6. Tier Studio (`/minion tier` or `/minion tier <minion_id>`)
* Interactive tier editor for action delays, storage capacities, and upgrade material costs.
* Add brand new custom tiers (Tier XIII+) with automatic Roman numeral generation.

---

## 9. The Operator Command Center (`/minionsx`)

The Operator Command Center (`/minionsx` or `/mxop`) is an administrative management hub:

* **Minions Catalog (Slot 20)**: Browse all registered minions. Left-click to inspect, right-click to receive Tier I, or Shift-click to receive max Tier XII.
* **Upgrades Catalog (Slot 22)**: Browse all functional upgrades and shipping hoppers. Click Slot 50 to launch the Upgrade Creator Studio.
* **Fuels Vault (Slot 24)**: Browse all temporary and permanent fuels. Click Slot 50 to launch the Fuel Creator Studio.
* **Skins Catalog (Slot 28)**: Browse all cosmetic skins. Click Slot 50 to launch the Skin Creator Studio.
* **Storages Catalog (Slot 34)**: Browse all storage chest expansion models.
* **Emergency Quarantine Switch (Slot 51)**: Toggle network-wide anti-dupe lockdown with one click.

---

## 10. Configuration Files Master Guide (Plain English)

All configuration files are located in `plugins/MinionX/`. Changes can be made directly in text editors or automatically via the In-Game Studios.

### `config.yml` (Main Configuration)
Controls core plugin behavior:
```yaml
# How often minions perform their work cycle (in server ticks: 20 ticks = 1 second)
tick-interval: 20

# Maximum number of minions a single player can place by default
default-player-limit: 5

# Storage database type: YAML, SQLITE, or MYSQL
storage:
  type: YAML

# Protection hooks automatically detected and respected
protection:
  worldguard: true
  griefprevention: true
  towny: true
  lands: true
  bentobox: true
```

### `prices.yml` (Economy & Custom Item Prices)
Defines base NPC sell prices used by Automated Shipping hoppers when storage is full. Supports vanilla materials, compressed enchanted items, and full custom items:
```yaml
default-price: 1.0

# Base NPC prices per single item
prices:
  COBBLESTONE: 1.0
  COAL: 2.0
  IRON_INGOT: 3.0
  GOLD_INGOT: 4.0
  DIAMOND: 8.0
  EMERALD: 6.0
  OBSIDIAN: 12.0
  PUMPKIN: 4.0
  MELON: 1.0
  OAK_LOG: 2.0
  ROTTEN_FLESH: 1.0
  
  # Compressed / Enchanted item prices (Super Compactor 3000)
  ENCHANTED_COBBLESTONE: 160.0
  ENCHANTED_DIAMOND: 1280.0
  ENCHANTED_IRON: 480.0

# Custom items pricing table (supports ItemsAdder, MMOItems, custom display names, CMD, and PDC)
custom-prices:
  ruby_gem:
    item: "itemsadder:ruby"
    price: 50.0
  mythic_crystal:
    material: EMERALD
    custom-model-data: 1001
    price: 250.0
  custom_named_drop:
    display-name: "&aEnchanted Cobblestone"
    price: 160.0
```

### `tiers.yml` (Global Speeds, Slots & Costs)
Defines the speed cooldown, storage slots, and upgrade costs for Tiers 1 through 12+:
```yaml
tiers:
  1:
    speed: 14.0       # Delay in seconds between actions
    storage-slots: 1  # Inventory slots unlocked
    upgrade-cost: 0
  5:
    speed: 11.0
    storage-slots: 6
    upgrade-cost: 512
  11:
    speed: 6.5
    storage-slots: 15
    upgrade-cost: 256
```

### `upgrades.yml` (Custom Upgrades & Hoppers)
Defines functional accessories and automated shipping hoppers:
```yaml
upgrades:
  budget_hopper:
    display-name: "&fBudget Hopper"
    material: HOPPER
    type: AUTO_SELLER
    sell-ratio: 0.50   # Sells for 50% of NPC price
    glow: false
  enchanted_hopper:
    display-name: "&aEnchanted Hopper"
    material: HOPPER
    type: AUTO_SELLER
    sell-ratio: 0.90   # Sells for 90% of NPC price
    glow: true
  auto_smelter:
    display-name: "&aAuto-Smelter"
    material: FURNACE
    type: AUTO_SMELTER
  super_compactor_3000:
    display-name: "&dSuper Compactor 3000"
    material: DROPPER
    type: SUPER_COMPACTOR
```

### `fuels.yml` (Fuels & Speed Boosts)
Defines temporary and permanent fuels:
```yaml
fuels:
  enchanted_charcoal:
    display-name: "&aEnchanted Charcoal"
    material: CHARCOAL
    speed-buff: 0.20   # +20% faster
    duration: 36h      # Burns for 36 hours
    glow: true
  enchanted_lava_bucket:
    display-name: "&aEnchanted Lava Bucket"
    material: LAVA_BUCKET
    speed-buff: 0.25   # +25% faster
    duration: -1       # -1 means permanent (infinite)
    glow: true
```

### `skins.yml` (Cosmetic Skins & Armor Sets)
Defines cosmetic skins with custom skull textures, armor colors, materials, and held tools:
```yaml
skins:
  santa:
    display-name: "&cSanta Minion Skin"
    skull-texture: "<base64_texture>"
    armor-color: "#D32F2F"          # Hex (#RRGGBB), RGB (255,85,85), or Name (RED, BLUE)
    armor-material: LEATHER         # Sets armor material (LEATHER for full dyed armor)
    hand-item: SUGAR_CANE           # Optional held weapon/tool
    description:
      - "&7Ho ho ho! A festive Christmas"
      - "&7skin for your hardworking minion."

  golden_king:
    display-name: "&6Golden King Skin"
    skull-texture: "<base64_texture>"
    armor-material: GOLD            # Metallic armor (GOLD, IRON, DIAMOND, NETHERITE)
    hand-item: GOLDEN_SWORD
    description:
      - "&7A royal skin adorned with"
      - "&7gleaming gold and majesty."
```

### `storages.yml` (Storage Expansion Capacities)
Defines external storage expansion chest models:
```yaml
storages:
  small_storage:
    display-name: "&aSmall Minion Storage"
    capacity: 3
    material: CHEST
  large_storage:
    display-name: "&5Large Minion Storage"
    capacity: 15
    material: CHEST
```

### `minions.yml` (Minion Types & Drops)
Defines each minion type, target block/mob, and drops:
```yaml
minions:
  cobblestone:
    display-name: "&aCobblestone"
    archetype: MINER
    target-block: COBBLESTONE
    max-tier: 12
    primary-drop:
      material: COBBLESTONE
      amount: 1
```

### `recipes.yml` (Super Compactor Recipes)
Defines compression rules for the Super Compactor 3000:
```yaml
compression-recipes:
  enchanted_cobblestone:
    input: COBBLESTONE
    input-amount: 160
    output:
      material: COBBLESTONE
      display-name: "&aEnchanted Cobblestone"
      glow: true
```

---

## 11. Supported Land Claim Integrations

MinionX natively hooks into all major protection and claim plugins:
* **WorldGuard**: Requires `build: allow` and `block-place: allow` flags.
* **GriefPrevention**: Requires claim trust permission.
* **Towny**: Requires resident building permission in the plot.
* **Lands**: Requires placement and container access flags.
* **BentoBox**: Respects island member permissions.

---

## 12. Anti-Dupe Security & Emergency Quarantine Mode

MinionX includes enterprise security safeguards:
* **Atomic Persistence**: Minion inventory updates and transactions are atomic.
* **Anti-Glitch Hopper Movement**: Storage chests cannot be accessed or siphoned by hoppers.
* **Emergency Quarantine Switch**:
  * Run `/minion quarantine on` (or click Slot 51 in `/minionsx`).
  * Instantly freezes all minion interactions, harvesting, item withdrawals, and placements across the entire server.
  * Run `/minion quarantine off` to restore normal operation once maintenance is complete.

---

## 13. Commands & Permissions Reference

### Commands:
| Command | Permission | Description |
|---|---|---|
| `/minionsx` or `/mxop` | `minionx.admin` | Opens the Operator Command Center. |
| `/minion create` | `minionx.admin` | Opens the Minion Creator Studio. |
| `/minion create upgrade` | `minionx.admin` | Opens the Upgrade & Hopper Creator Studio. |
| `/minion create fuel` | `minionx.admin` | Opens the Fuel Creator Studio. |
| `/minion create skin` | `minionx.admin` | Opens the Skin Creator Studio. |
| `/minion create storage` | `minionx.admin` | Opens the Storage Creator Studio. |
| `/minion tier` | `minionx.admin` | Opens the Global Tier Studio. |
| `/minion tier <id>` | `minionx.admin` | Opens the Per-Minion Tier Studio. |
| `/minion give <player> <item> [tier]` | `minionx.admin` | Gives a minion, fuel, upgrade, or storage item. |
| `/minion quarantine [on\|off\|status]` | `minionx.admin` | Toggles emergency quarantine lockdown. |
| `/minion limit <check\|set\|add> <player>` | `minionx.admin` | Manages player placement limits. |
| `/minion reload` | `minionx.admin` | Reloads all configuration files and registries. |

### Permissions:
* `minionx.use`: Allows players to place and manage their own minions (Default: `true`).
* `minionx.admin`: Complete administrative access to all studios, `/minionsx`, and commands (Default: `op`).

---

## 14. Frequently Asked Questions (FAQ)

**Q: Do minions cause server lag with large player bases?**  
A: No. Minion armor stands do not tick (`setCanTick(false)`), holograms use display entities, and unloaded minions use mathematical catch-up upon chunk reload.

**Q: How does Automated Shipping sell items when the storage is full?**  
A: Equipped Budget (50%) or Enchanted (90%) hoppers calculate sell earnings from `prices.yml` and accumulate coins directly in the hopper slot. Players click the hopper or "Collect All" to claim coins.

**Q: Can other players steal items from my minion storage chest?**  
A: No. Minion storage chests are protected by owner verification and are strictly withdrawal-only. Vanilla hoppers cannot siphon items from them.

**Q: Can I create custom minions with custom speeds and unique drops?**  
A: Yes! Use the in-game studio (`/minion create`) or edit `minions.yml` to define any minion with any custom drops, tools, and tier caps.
