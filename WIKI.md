# MinionX Official Documentation & Server Owner's Manual

> **MinionX** — Autonomous, High-Performance Minion Plugin for **Paper / Purpur / Pufferfish 1.20.4 – 1.21+**  
> **Author:** AbhinavVanama  
> **Current Version:** `v0.2.0` (Pre-Release)

---

## Table of Contents
1. [Overview & Quick Start Guide](#1-overview--quick-start-guide)
2. [How Minions Work (Core Gameplay Mechanics)](#2-how-minions-work-core-gameplay-mechanics)
3. [In-Game Creator Studios (No YAML Editing Needed)](#3-in-game-creator-studios-no-yaml-editing-needed)
4. [Operator Control Panel (`/minionsx`)](#4-operator-control-panel-minionsx)
5. [Complete Minion Archetypes & Types](#5-complete-minion-archetypes--types)
6. [Upgrades System](#6-upgrades-system)
7. [Fuel System (Temporary vs Permanent)](#7-fuel-system-temporary-vs-permanent)
8. [Storage Chest Expansions](#8-storage-chest-expansions)
9. [Cosmetic Minion Skins](#9-cosmetic-minion-skins)
10. [Player Limits & Slot Management](#10-player-limits--slot-management)
11. [Supported Integrations & Protection Hooks](#11-supported-integrations--protection-hooks)
12. [Anti-Dupe Security & Emergency Quarantine Mode](#12-anti-dupe-security--emergency-quarantine-mode)
13. [Commands Reference & Syntax](#13-commands-reference--syntax)
14. [Permissions Reference Matrix](#14-permissions-reference-matrix)
15. [Configuration Guide (`config.yml`)](#15-configuration-guide-configyml)
16. [Server Owner FAQ & Troubleshooting](#16-server-owner-faq--troubleshooting)
17. [Developer API Reference (For Developers)](#17-developer-api-reference-for-developers)
18. [Changelog & Documentation Maintenance](#18-changelog--documentation-maintenance)

---

## 1. Overview & Quick Start Guide

Welcome to **MinionX**! MinionX brings fully autonomous, upgradeable, and highly optimized minions to your Minecraft server. Minions automate resource gathering (mining, farming, foraging, and mob hunting) even when players are offline, all while running cleanly at a solid 20 TPS.

### Requirements:
* **Server Software:** Paper, Purpur, or Pufferfish (1.20.4 – 1.21+).
* **Java Version:** Java 21 or higher.
* **Optional Hooks:** Vault (for economy/hoppers), WorldGuard/GriefPrevention/Towny/Lands/BentoBox (for land claims), MythicMobs (for custom combat mobs).

### Installation:
1. Place `MinionX-0.2.0.jar` (or `MinionX-0.2.0-SNAPSHOT.jar`) into your server's `plugins/` folder.
2. Restart your server (or load it using a plug-man alternative).
3. The default configurations and registries will automatically generate in `plugins/MinionX/`.

### Placing Your First Minion (In 3 Simple Steps):
1. Give yourself a minion:
   ```text
   /minion give <your_username> diamond 1
   ```
2. Place the minion item onto any solid block in the world.
3. Watch it wake up! It will spawn its tools, set up its nametag, and start generating and harvesting resources within its 5×5 working area.

---

## 2. How Minions Work (Core Gameplay Mechanics)

```
        [5x5 WORKING ZONE]
      .  .  .  .  .
      .  .  .  .  .
      .  . [M] .  .    <-- Minion placed in center
      .  .  .  .  .
      .  .  .  .  .
```

### 1. The 5×5 Working Zone
Every minion operates in a 5×5 block area centered on itself:
* **Mining Minions**: Place ores/blocks on the ground, wait for their action delay, then break and collect them.
* **Farming Minions**: Till farmland, plant crops (wheat, carrots, potatoes, melon/pumpkin stems), let them grow, harvest them when mature, and immediately replant.
* **Foraging Minions**: Plant saplings, grow full trees with natural foliage, fell the logs, collect drops, and replant.
* **Combat Minions**: Spawn their designated mobs within the 5×5 area (up to a configurable cap) and battle them to harvest mob drops and combat items.

### 2. Flexible Leveling & Per-Minion Max Tiers
Every minion can be upgraded from **Tier I** up to its individually configured maximum tier (Default: **Tier XII**, or custom caps such as Tier V, X, or XI):
* **Action Speed**: Higher tiers harvest significantly faster.
* **Internal Storage**: Tiers expand internal capacity from 1 slot up to 15+ slots.
* **Per-Minion Max Tier Cap**: Different minions can have different maximum tier caps configured in `minions.yml` (`max-tier: <number>`) or via the In-Game Minion Creator Studio. Once a minion reaches its individual maximum tier, the upgrade button locks with a Bedrock indicator preventing further upgrades.
* **Upgrading**: Players can upgrade their minion directly through the Minion GUI by providing the required materials and clicking the Upgrade button.

| Tier | Default Delay | Storage Slots | Example Upgrade Cost |
| :---: | :---: | :---: | :---: |
| **I** | 14.0s | 1 slot | Crafted Base |
| **II** | 14.0s | 3 slots | 80x Base Material |
| **III** | 12.5s | 3 slots | 160x Base Material |
| **IV** | 12.5s | 6 slots | 320x Base Material |
| **V** | 11.0s | 6 slots | 512x Base Material |
| **VI** | 11.0s | 9 slots | 8x Enchanted Form |
| **VII** | 9.5s | 9 slots | 16x Enchanted Form |
| **VIII** | 9.5s | 12 slots | 32x Enchanted Form |
| **IX** | 8.0s | 12 slots | 64x Enchanted Form |
| **X** | 8.0s | 15 slots | 128x Enchanted Form |
| **XI** | 6.5s | 15 slots | 256x Enchanted Form |
| **XII** | 5.0s | 15 slots | 512x Enchanted Form |

### 3. Floating Holograms & Live Dialogues
* MinionX uses modern **Paper `TextDisplay` entities** with `Billboard.CENTER`. Text always faces nearby players smoothly without packet flicker or entity lag.
* Displays the minion's Roman numeral tier, archetype name, status (`Working...`, `Inventory Full!`, `Area Blocked!`), and harvest stats.
* **Interactive Speech**: Minions periodically chat with their owners using floating speech bubbles when clicked or when completing milestone harvests!

### 4. Offline Progress Engine
When a chunk unloads or players log off, MinionX does not lag the server with ghost ticking. Instead, upon chunk reload:
* Calculates the exact elapsed downtime.
* Simulates production based on minion tier speed, active fuel multipliers, and compactor upgrades.
* Fills inventory and storage chests up to their limits.
* If an automated hopper is installed, liquidates overflow resources straight into the owner's bank account via Vault!
* Configurable max offline simulation cap (default: 720 hours / 30 days).

---

## 3. In-Game Creator Studios (No YAML Editing Needed)

MinionX features built-in in-game graphical studios so administrators and server owners can create and customize content on the fly without touching configuration files or rebooting the server.

### 1. Minion Creator Studio (`/minion create`)
Allows you to create an entirely custom minion archetype in 30 seconds:
1. Run `/minion create`.
2. Enter an ID (e.g., `amethyst`) and display name (`&dAmethyst Minion`).
3. Select the minion's archetype: `MINING`, `FARMING`, `FORAGING`, or `COMBAT`.
4. Pick the block/crop to place, tool to hold in hand, and primary/secondary item drops.
5. Set custom base delay and skull texture base64.
6. Click **Confirm & Save** — the minion is immediately registered and ready to use with `/minion give`!

### 2. Skin Creator Studio (`/minion create skin`)
Design cosmetic visual skins for minions:
1. Run `/minion create skin`.
2. Input a skin ID (e.g., `cyber_knight`) and display name (`&bCyber Knight`).
3. Set custom head texture (Base64 skull value).
4. Customize armor: Choose Leather, Iron, Gold, Diamond, or Netherite, and pick any RGB leather dye color.
5. Pick a held weapon/tool (e.g., Diamond Sword, Netherite Axe).
6. Click **Save** — automatically saved to `skins.yml` and added to the skins catalog.

### 3. Storage Creator Studio (`/minion create storage`)
Create custom storage expansion chest models:
1. Run `/minion create storage`.
2. Define storage capacity (e.g., 9 slots, 18 slots, 27 slots).
3. Set custom item icon, display name, and lore.
4. Save directly into `storages.yml` with instant in-game availability.

### 4. Tier Studio (`/minion tier` or `/minion tier <minion_id>`)
Configure and customize minion progression in real time without restarting:
1. **Commands & Access**:
   * Run `/minion tier` to open the **Global Tier Studio** (affects default tiers saved to `tiers.yml`).
   * Run `/minion tier <minion_id>` or click **Slot 35 (Per-Minion Tier Studio)** in `/minion create` to open the **Per-Minion Tier Studio** (configures individual tiers, speed, capacity, upgrade cost, and max tier for that minion saved to `minions.yml`).
2. **Overview Menu**: Browse all active tiers (Default I through XII, plus any custom tiers). In per-minion mode, view the active max tier with interactive `+` and `-` adjustment buttons.
3. **Edit Tier**: Click any tier to adjust:
   * **Action Delay** (±0.5s or ±5.0s buttons).
   * **Storage Slots** (from 1 to 27 slots with automatic capacity calculation).
   * **Upgrade Material Cost** (±1,000 or ±10,000 resources).
4. **Create Custom Tiers**: Click **Create New Tier** to add Tier XIII, XIV, etc. with automatic dynamic Roman numeral conversion!
5. **Delete Custom Tiers**: Right-click any custom tier (>12) to delete it. Core 12 tiers are protected against accidental deletion.
6. **Dual Persistence**: Global edits save instantly to `tiers.yml`. Per-minion edits write directly to the minion's `tiers:` map in `minions.yml`, hot-reloading across all active placed minions.

---

## 4. Operator Control Panel (`/minionsx`)

The **MinionX Operator Interface** (command `/minionsx` or `/mxop`) is an all-in-one administrative test bench and item dispenser.

```
+-------------------------------------------------------------+
|                     MinionX Operator Hub                    |
+-------------------------------------------------------------+
| [Minions Catalog]  [Upgrades Hub]  [Fuels Vault]  [Skins]   |
|   Browse all 1-12    Compact,       Temp & Perm    Cosmetics|
|   Tier Minions       Hoppers, etc.  Fuels          Catalog  |
+-------------------------------------------------------------+
| [Tier Studio]     [Emergency Quarantine]     [Reload]       |
|  Dynamic Tiers     Anti-Dupe Lockdown         Configs       |
+-------------------------------------------------------------+
```

* **Minions Catalog**: View every registered minion in the game. Left-click to inspect, right-click to receive Tier 1, or Shift-click to receive max Tier XII!
* **Upgrades Hub**: Click to immediately receive Auto-Smelters, Super Compactors, Diamond Spreadings, Flycatchers, and Hoppers.
* **Fuels Vault**: Instantly grab stacks of Enchanted Coal, Lava Buckets, Plasma Buckets, and Solar Panels.
* **Skins Catalog**: Browse all custom and built-in skins and receive skin tokens with one click.
* **Tier Studio (Beacon)**: Open the real-time In-Game Tier Studio to configure action delays, storage sizes, and upgrade costs.
* **Emergency Quarantine Switch**: Toggle global server anti-dupe freeze instantly.

---

## 5. Complete Minion Archetypes & Types

MinionX comes pre-packaged with 30+ default minion types across 4 distinct archetypes:

### Mining Archetype
Mining minions place a block directly in front of them or in their 5×5 area, wait for their action cooldown, break the block with pickaxe particles and sound, and store the harvest:
* **Cobblestone**, **Coal**, **Iron**, **Gold**, **Diamond**, **Lapis**, **Redstone**, **Emerald**, **Nether Quartz**, **Obsidian**, **Glowstone**.

### Farming Archetype
Farming minions till dirt into farmland, plant seeds/crops, wait for natural or accelerated maturity, harvest, and automatically replant:
* **Wheat**, **Carrot**, **Potato**, **Pumpkin**, **Melon**, **Nether Wart**, **Sugar Cane**, **Cocoa**.

### Foraging Archetype
Foraging minions place tree saplings on grass or dirt, grow them into full trees, harvest the wood logs, and replant saplings:
* **Oak**, **Birch**, **Spruce**, **Dark Oak**, **Acacia**, **Jungle**, **Mangrove**, **Cherry**.
* Secondary drops include Apples, Saplings, and Sticks.

### Combat Archetype
Combat minions summon their respective mob in their 5×5 zone (governed by the `max-mobs-per-minion` cap), draw their weapons, and defeat them for mob loot:
* **Zombie**, **Skeleton**, **Spider**, **Cave Spider**, **Creeper**, **Enderman**, **Slime**, **Magma Cube**, **Blaze**, **Ghast**, **Witch**, **Piglin**.
* **MythicMobs Support**: Can be configured to summon custom MythicMobs entities using their internal mob IDs.
* **Performance Setting**: Server owners can enable `disable-mob-wandering: true` in `config.yml` to disable AI movement on spawned mobs, keeping them neatly in place and saving server CPU ticks!

---

## 6. Upgrades System

Every minion features **2 Upgrade Slots** inside its GUI (Slots 21 and 23):

```
+-------------------------------------------------------+
|  [Slot 21: Upgrade 1]     [M]      [Slot 23: Upgrade 2]|
+-------------------------------------------------------+
```

### Supported Built-in Upgrades:
1. **Auto Smelter**:
   * Automatically smelts harvested ores and items (Iron Ore -> Iron Ingot, Cobblestone -> Stone, Clay -> Terracotta, Sand -> Glass).
2. **Compactor**:
   * Automatically crafts 9 harvested items into their condensed block form (e.g., 9 Coal -> 1 Coal Block, 9 Iron -> 1 Iron Block).
3. **Super Compactor (3000)**:
   * **Advanced condensation**: Crafts harvested items into Enchanted forms (e.g., 160 Diamond -> 1 Enchanted Diamond, 160 Cobblestone -> 1 Enchanted Cobblestone).
   * **Custom Items Support**: Full support for custom items! Both `minions.yml` (drops) and `recipes.yml` (super compactor inputs/outputs) share the identical unified custom item schema (`material`/`item`, `amount`/`input-amount`, `display-name`, `custom-model-data`, `lore`, `glow`/`enchanted`, `pdc-key`, `pdc-value`, and hook items `itemsadder:`, `mmoitems:`, etc.).
     * **Input Matching**: Supports matching by `custom-model-data`, custom `display-name`, and `pdc-key` + `pdc-value`.
     * **Output Custom Data**: Generated output items can have custom display names, multi-line colored lore, custom model data, enchantment glint, and persistent data tags.
     * **Anti-Swallowing Protection**: Vanilla recipes strictly ignore custom items (items with custom display names or custom model data), preventing players' rare custom RPG items from being accidentally destroyed or swallowed by vanilla compression recipes!
   * **Example `recipes.yml` Configuration:**
     ```yaml
     compression-recipes:
       # Compressing a custom item into an enchanted custom item
       enchanted_custom_ruby:
         input:
           material: REDSTONE
           amount: 160
           display-name: "&cRaw Ruby"
           custom-model-data: 1001
           pdc-key: "minionx:item_id"
           pdc-value: "raw_ruby"
         output:
           material: REDSTONE_BLOCK
           amount: 1
           display-name: "&c&lEnchanted Ruby Block"
           custom-model-data: 1002
           glow: true
           pdc-key: "minionx:item_id"
           pdc-value: "enchanted_ruby"
           lore:
             - "&7A condensed, glowing ruby jewel."
             - "&e&lEPIC ITEM"
     ```
4. **Diamond Spreading**:
   * Grants a chance to produce 1 extra Diamond whenever the minion completes any harvest action (works on all mining, farming, and foraging minions).
5. **Flycatcher**:
   * Speeds up the minion by a flat +20% action speed boost.
6. **Minion Expander**:
   * Expands the minion's working zone from 5×5 blocks to 7×7 blocks.
7. **Budget Hopper**:
   * Automatically sells excess harvested items when internal storage and linked chests are 100% full at **50% of server sell price** into the player's balance via Vault.
8. **Enchanted Hopper**:
   * Sells overflow items when storage is full at **90% of server sell price** via Vault.

---

## 7. Fuel System (Temporary vs Permanent)

Every minion features a dedicated **Fuel Slot** (Slot 19):

### Temporary Fuels
Temporary fuels burn over a set duration. Once expired, they are consumed.
* **Coal / Charcoal**: +5% Speed boost for 30 minutes.
* **Block of Coal**: +5% Speed boost for 5 hours.
* **Enchanted Bread**: +5% Speed boost for 12 hours.
* **Enchanted Coal**: +10% Speed boost for 24 hours.
* **Hyper Catalyst**: +300% (4x) Output boost for 6 hours.

> [!NOTE]
> If `destroy-temporary-fuel-on-removal: true` is set in `config.yml`, players who withdraw active burning temporary fuel will forfeit the remainder of that fuel item.

### Permanent Fuels
Permanent fuels never expire and provide continuous buffs until manually removed by the player:
* **Solar Panel**: +25% Speed boost during daytime in the world.
* **Enchanted Lava Bucket**: Permanent +20% Speed boost.
* **Magma Bucket**: Permanent +30% Speed boost.
* **Plasma Bucket**: Permanent +35% Speed boost.

---

## 8. Storage Chest Expansions

To prevent minions from filling up rapidly, players can link an external **Minion Storage Chest** directly adjacent to the minion.

```
       +--------------+  +--------------+
       | Minion Armor |  | Linked Chest |
       |    Stand     |  | (Expansion)  |
       +--------------+  +--------------+
```

### Storage Sizes:
* **Small Minion Storage**: Expands capacity by **+3 extra slots** (1 row).
* **Medium Minion Storage**: Expands capacity by **+9 extra slots** (1 full 9-slot row).
* **Large Minion Storage**: Expands capacity by **+15 extra slots** (multi-row pagination).

### How to Use:
1. Craft or obtain a Minion Storage item.
2. Place the storage chest directly next to (adjacent to) the minion armor stand.
3. The minion will automatically detect the linked chest, update its nametag, and route all overflow harvested items directly into the chest!
4. Shift-Right-Clicking or clicking the storage icon inside the Minion GUI opens the storage container directly.

---

## 9. Cosmetic Minion Skins

Server owners and players can transform their minions' appearance with custom skins!

* **What Skins Change**: Custom player head texture (via Base64/Mojang skin), leather armor dye colors, armor materials (Leather, Iron, Gold, Diamond, Netherite), and held tool/weapon.
* **Where to Equip**: Open the Minion GUI and place any Minion Skin token into **Slot 37** (the cosmetic skin slot).
* **Built-in Skins**: Includes authentic high-quality skins loaded from `minion_skins.json`.
* **Custom Skins**: Add custom skins via `skins.yml` or using the in-game `/minion create skin` studio.

---

## 10. Player Limits & Slot Management

To maintain top server performance and balance economies, MinionX features a flexible limit system.

### How Limits Work:
1. **Base Server Limit**: Configured in `config.yml` via `max-minions-per-player: 5`.
2. **Permission-based Limits**: Grant players higher caps via permissions:
   * `minionx.limit.10` -> Grants 10 minion slots.
   * `minionx.limit.25` -> Grants 25 minion slots.
   * `minionx.bypass.limit` -> Unlimited minion placements.
3. **Purchasable / Bonus Slots**: Admins can award permanent bonus slots to players (e.g., via store ranks or quest rewards):
   ```text
   /minion limit add <player> 2
   ```

### Checking & Managing Limits:
* Check a player's limit: `/minion limit check <player>`
* Add bonus slots: `/minion limit add <player> <amount>`
* Remove bonus slots: `/minion limit remove <player> <amount>`
* Set bonus slots: `/minion limit set <player> <amount>`

---

## 11. Supported Integrations & Protection Hooks

MinionX comes with out-of-the-box compatibility with the most popular server plugins:

### 1. Land & Island Protection Plugins
Minions will **never** place, break, or interact with blocks inside protected areas unless the placing player has build permissions there:
* **WorldGuard**
* **GriefPrevention**
* **Towny**
* **Lands**
* **BentoBox**
* **IridiumSkyblock**
* **SuperiorSkyblock2**

### 2. Economy & Vault
* When a minion uses a **Budget Hopper** or **Enchanted Hopper**, sold items deposit funds directly into the minion owner's Vault economy balance.

### 3. Combat & Custom Entities
* **MythicMobs**: Fully supported. Combat minions can spawn custom MythicMobs entities with custom drops and levels.

### 4. Skill Plugins
* **mcMMO**, **AureliumSkills**, and **EcoSkills**: When players collect resources from their minions, skill harvest XP can be credited to their character stats.

### 5. Bedrock Edition (GeyserMC & Floodgate)
* Bedrock players can interact seamlessly with minions.
* Toggle `geyser-touch-friendly: true` in `config.yml` for touch-friendly GUI slot alignment.

---

## 12. Anti-Dupe Security & Emergency Quarantine Mode

MinionX was built from the ground up with military-grade dupe protection:

```
+-----------------------------------------------------------------+
|                    MINIONX ANTI-DUPE ENGINE                     |
+-----------------------------------------------------------------+
| 1. Atomic Disk Writes: minions.yml.tmp -> ATOMIC MOVE           |
| 2. Atomic Unregister: Unregistered from RAM BEFORE dropping item|
| 3. Armor Stand Exploit Armor: Drop chances set to 0.0%          |
| 4. Death Drops Cleared: Death event explicitly wiped            |
| 5. Global Emergency Quarantine Switch: Instant lockdown         |
+-----------------------------------------------------------------+
```

### 1. Atomic Disk Persistence
* All minion inventories and state changes are snapshotted on the main server thread.
* File saves are written to `minions.yml.tmp` and swapped using `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`. Even if your physical server loses power mid-save, data is never corrupted.

### 2. Double-Click & Pickup Dupe Protection
* When a player picks up a minion, it is **unregistered from server memory FIRST**. Only after successful memory deregistration are items placed into the player's inventory.
* If a player's inventory is full, remaining items safely drop at their feet using `world.dropItemNaturally()` instead of disappearing or cloning.

### 3. Armor Stand Security
* Visual ArmorStand entity drop chances are permanently locked to `0.0f`.
* Entity death listeners wipe all drops and experience to prevent armor or custom skull harvesting exploits.

### 4. Emergency Quarantine Mode (`/minion quarantine`)
If a server admin suspects an economy exploit or dupe bug, they can trigger global quarantine:
```text
/minion quarantine on
```
* **Instant Freeze**: All minion ticking tasks, block placing, breaking, and GUI transactions freeze immediately across the entire server.
* **Zero Data Loss**: Minions remain safely in the world without being deleted.
* **Restore**: Once the server admin finishes investigating, run `/minion quarantine off` to resume normal operation.

---

## 13. Commands Reference & Syntax

### Primary Command: `/minion`
*(Aliases: `/minions`, `/mx`, `/minionx`)*

| Command Syntax | Permission | Description |
| :--- | :--- | :--- |
| `/minion list` | `minionx.use` | Displays a list of all active placed minions owned by you with coordinates and current status. |
| `/minion tier [list|edit <tier>]` | `minionx.admin` | Opens the In-Game Tier Studio GUI or directly edits an action delay/slots/cost for a tier. |
| `/minion create` | `minionx.admin` | Opens the In-Game Minion Creator Studio to design a new minion archetype. |
| `/minion create skin` | `minionx.admin` | Opens the In-Game Skin Creator Studio to design a custom minion skin. |
| `/minion create storage` | `minionx.admin` | Opens the In-Game Storage Creator Studio to configure custom chests. |
| `/minion give <player> <type> [tier]` | `minionx.admin` | Gives a specific minion item (Tier 1–12) to a target player. |
| `/minion give <player> <fuel>` | `minionx.admin` | Gives a custom or built-in fuel item to a player. |
| `/minion give <player> <upgrade>` | `minionx.admin` | Gives an upgrade item (Compactor, Smelter, etc.) to a player. |
| `/minion give <player> skin <skin_id>` | `minionx.admin` | Gives a cosmetic minion skin token to a player. |
| `/minion give <player> storage <storage_id>` | `minionx.admin` | Gives a storage chest expansion item to a player. |
| `/minion limit check [player]` | `minionx.use` | Checks your own or another player's minion slot limit and placed count. |
| `/minion limit add <player> <amount>` | `minionx.admin` | Adds permanent bonus minion placement slots to a player. |
| `/minion limit remove <player> <amount>` | `minionx.admin` | Deducts bonus minion placement slots from a player. |
| `/minion limit set <player> <amount>` | `minionx.admin` | Sets the exact bonus minion placement slots for a player. |
| `/minion quarantine [on\|off\|status]` | `minionx.admin` | Manages the global emergency anti-dupe quarantine lockdown. |
| `/minion killall` | `minionx.admin` | Emergency admin command: Kills and despawns all active minions and holograms. |
| `/minion reload` | `minionx.admin` | Reloads `config.yml`, `minions.yml`, `fuels.yml`, `upgrades.yml`, and `skins.yml`. |

### Operator Command: `/minionsx`
*(Alias: `/mxop`)*

| Command Syntax | Permission | Description |
| :--- | :--- | :--- |
| `/minionsx` | `minionx.admin` | Opens the comprehensive visual MinionX Operator Panel. |

---

## 14. Permissions Reference Matrix

| Permission Node | Default | Description |
| :--- | :---: | :--- |
| `minionx.use` | `true` | Allows placing, breaking, interacting with, and viewing your own minions. |
| `minionx.admin` | `op` | Full administrative control: `/minion give`, `/minion create`, `/minionsx`, reload, and quarantine. |
| `minionx.bypass.limit` | `op` | Allows placing unlimited minions, ignoring the server's maximum player limit. |
| `minionx.limit.<number>` | `op` | Sets the maximum placed minion cap for a player/group (e.g., `minionx.limit.10`). |

---

## 15. Modular Configuration Architecture

MinionX separates bulky configurations into dedicated YAML files for maximum clarity and effortless editing. Each sub-config can be modified individually and hot-reloaded with `/minion reload`.

| File | Purpose | Default Contents |
| :--- | :--- | :--- |
| `config.yml` | Core engine, offline simulation, limits, visual toggles, combat caps. | Performance & system engine settings. |
| `tiers.yml` | Tier definitions (delays, storage slots, upgrade costs) from 1 to N. | Default 12 tiers + any studio tiers. |
| `minions.yml` | Custom minion definitions with multi-drop schema & drop chances. | Custom mining, farming, and combat definitions. |
| `fuels.yml` | Temporary and permanent fuel multipliers and durations. | Coal, Lava, Plasma, Solar Panels, etc. |
| `upgrades.yml` | Automated production upgrades & item modifiers. | Auto-Smelter, Super Compactor, Flycatcher, etc. |
| `recipes.yml` | Super Compactor 3000 automated compression recipes. | 160:1 and 9:1 compression recipes. |
| `messages.yml` | Global chat prefix, all user messages, and NPC speech dialogues. | Fully customizable alerts & minion dialogues. |

### Core Engine Settings (`config.yml`):

```yaml
settings:
  # Destroys temporary fuels (Coal, Bread, etc.) if removed from the slot while burning.
  # Permanent fuels (Lava Buckets, Solar Panels) are never destroyed.
  destroy-temporary-fuel-on-removal: true

  # Optimizes menu layouts for mobile/touch users on Bedrock via GeyserMC.
  geyser-touch-friendly: true

  # Minion AI task tick interval (20 ticks = 1 check per second). Recommended: 20.
  tick-interval: 20

  # Automatic data save interval in ticks (6000 ticks = 5 minutes).
  auto-save-interval: 6000

  # Maximum minions allowed per player. Overridden by minionx.limit.<number>
  max-minions-per-player: 5

  # Maximum offline simulation time in hours (720 hours = 30 days).
  max-offline-hours: 720

  # Visual flair: set to false on massive 500+ player networks for pure performance.
  enable-animations: true
  enable-sounds: true
  enable-particles: true

  # Emergency anti-dupe lockdown switch. Keep false for normal server play.
  quarantine-mode: false

  combat:
    # Max live mobs spawned in a 5x5 zone per combat minion.
    max-mobs-per-minion: 5

    # If true, disables AI wandering on mobs spawned by combat minions.
    disable-mob-wandering: false

nametags:
  mode: ALWAYS
  format: "&e[Lv.%tier_roman%] &a%minion_name% Minion &7(%status%&7)"
```

### Custom Minion Schema with Multi-Drop Chances (`minions.yml`):

```yaml
custom-minions:
  spider_example:
    display-name: "&cSpider Hunter Minion"
    archetype: COMBAT
    target-mob: SPIDER
    held-tool: IRON_SWORD
    drops:
      - item: STRING
        amount: 2
        chance: 1.0   # 100% guarantee
      - item: SPIDER_EYE
        amount: 1
        chance: 0.5   # 50% chance
```

---

## 16. Server Owner FAQ & Troubleshooting

### Q: Why does a minion say "Area Blocked!" on its nametag?
**A:** Minions require clear space in their 5×5 area to place blocks, till soil, grow trees, or spawn mobs. Make sure:
* There are no solid foreign blocks obstructing crops or tree growth.
* The minion is not placed against a wall where all 5×5 blocks are obstructed.
* The ground beneath the minion matches the required soil (e.g., Dirt for crops/saplings).

### Q: Why is my minion not doing anything?
**A:** Check the following:
1. Is its inventory full? Empty its internal storage or attach a Minion Storage Chest!
2. Is the server in Quarantine Mode? Run `/minion quarantine status` to check.
3. Is land claim protection blocking it? If you are using WorldGuard or Lands, make sure the player owns the claim.

### Q: Can players steal items from other players' minions?
**A:** **No.** Minions can only be opened, upgraded, or picked up by their owner or server operators. Furthermore, MinionX integrates with claim plugins to protect the minion entity itself.

### Q: How do I give VIP ranks more minion slots?
**A:** Simply grant the permission `minionx.limit.<amount>` in your permissions plugin (e.g., LuckPerms):
```text
/lp group vip permission set minionx.limit.8
/lp group mvp permission set minionx.limit.15
```

---

## 17. Developer API Reference (For Developers)

MinionX exposes a clean and robust Java API for third-party plugin integration.

### Maven Dependency
Add MinionX to your `pom.xml`:
```xml
<dependency>
    <groupId>com.minionx</groupId>
    <artifactId>MinionX</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Accessing the API & Registries
```java
import com.minionx.MinionX;
import com.minionx.api.*;
import com.minionx.core.Minion;
import com.minionx.core.MinionManager;

// Get MinionX Instance
MinionX minionX = MinionX.getInstance();
MinionManager manager = minionX.getMinionManager();

// Fetch all placed minions belonging to a player
UUID playerUUID = player.getUniqueId();
List<Minion> playerMinions = manager.getMinionsByOwner(playerUUID);

for (Minion minion : playerMinions) {
    player.sendMessage("Found: " + minion.getDisplayName() + " Tier: " + minion.getTier());
}
```

### Accessing the Static Helper API (`MinionXAPI`)
```java
import com.minionx.api.MinionXAPI;
import com.minionx.core.Minion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

// Query player's placed minions & placement capacity
int placedCount = MinionXAPI.getPlacedMinions(player).size();
int maxLimit = MinionXAPI.getMaxMinions(player);
boolean allowedToPlace = MinionXAPI.canPlaceMinion(player);

// Inspect minion occupying a block location
Location loc = player.getLocation().getBlock().getLocation();
Minion minionAtLoc = MinionXAPI.getMinionAt(loc);

// Generate a valid physical minion item to give or use in shops
ItemStack diamondMinionX = MinionXAPI.createMinionItem("diamond", 11);
```

### Custom Bukkit Events (`com.minionx.api.event.*`)
Other plugins can listen to lifecycle and economy events using standard `@EventHandler` methods:

| Event | Cancellable | Description |
| :--- | :---: | :--- |
| `MinionPlaceEvent` | Yes | Fired when a player places a minion block item into the world. |
| `MinionPickupEvent` | Yes | Fired when an owner or admin breaks/picks up a placed minion. |
| `MinionHarvestEvent` | Yes | Fired every time a minion harvests resources (modify drops & exp!). |
| `MinionUpgradeEvent` | Yes | Fired when a minion upgrades to a higher tier (`getPreviousTier()`, `getNewTier()`). |
| `MinionCollectionEvent` | Yes | Fired when a player claims items or exp from a minion's GUI. |
| `MinionFuelConsumeEvent` | Yes | Fired when an active fuel burns out and is consumed. |
| `MinionStorageLinkEvent` | Yes | Fired when a storage chest expands or detaches from a minion. |
| `MinionQuarantineEvent` | Yes | Fired when global emergency quarantine mode is toggled. |

#### Example: Modifying Drops via `MinionHarvestEvent`
```java
import com.minionx.api.event.MinionHarvestEvent;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class CustomDropsListener implements Listener {
    @EventHandler
    public void onMinionHarvest(MinionHarvestEvent event) {
        // Double cobblestone drops during weekend server events!
        if (event.getMinion().getType().getId().equalsIgnoreCase("cobblestone")) {
            event.getDrops().add(new ItemStack(Material.COBBLESTONE, 2));
            event.setXpEarned(event.getXpEarned() * 2);
        }
    }
}
```

### Registering a Custom Minion Skin Programmatically
```java
import com.minionx.api.MinionSkin;
import com.minionx.api.MinionSkinRegistry;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

MinionSkin customSkin = new MinionSkin(
    "paladin",
    "&ePaladin Guardian",
    "eyJ0ZXh0dXJlcyI6ey...Base64Texture...",
    Color.fromRGB(255, 215, 0),
    Material.GOLDEN_CHESTPLATE,
    new ItemStack(Material.GOLDEN_SWORD),
    List.of("&7Holy warrior of the realm.")
);

MinionSkinRegistry.register(customSkin);
```

### Registering Custom Fuels & Upgrades
```java
import com.minionx.api.CustomFuel;
import com.minionx.api.FuelRegistry;

// Create a custom 2-hour 50% speed boost fuel
CustomFuel dragonBreathFuel = new CustomFuel(
    "dragon_flame",
    "&5Dragon Flame",
    1.50, // 50% faster
    7200, // 2 hours in seconds
    false // temporary
);
FuelRegistry.register(dragonBreathFuel);
```

---

## 18. Changelog & Documentation Maintenance

| Version | Date | Changes / Notes |
| :---: | :---: | :--- |
| **0.2.0** | 2026-10-12 | **Phase 2 Expansion (Current Pre-Release)**:<br>• **Unified Custom Items Schema**: Unified custom items handling between `minions.yml` (drops) and `recipes.yml` (super compactor input/output) supporting `material`/`item`, `amount`/`input-amount`, `chance`, `display-name`, `custom-model-data`, `lore`, `glow`/`enchanted`, `pdc-key`, `pdc-value`, and hook items (`itemsadder:`, `mmoitems:`, etc.) with strict anti-swallowing isolation.<br>• **Per-Minion Tier Studio & Customization**: Different minions can now have independent max tiers, upgrade costs, action delays (speeds), and storage capacities. Fully editable in `minions.yml` (`tiers:`) and in-game via `/minion tier <minion_id>` and Creator Studio Slot 35.<br>• **Pre-Release Semantic Versioning**: Locked versioning to `v0.x.x` pre-release format starting from `v0.0.0`. |
| **0.1.3** | 2026-10-12 | **Phase 1 Modular Architecture & Developer API**:<br>• **Dynamic Tier Studio**: In-game GUI (`/minion tier` & Operator Beacon) with arbitrary Roman numerals.<br>• **Modular YAML Files**: Split into `tiers.yml`, `minions.yml`, `fuels.yml`, `upgrades.yml`, `recipes.yml`, and `messages.yml`.<br>• **Multi-Drop Engine**: Support for multiple items with individual drop chance percentages.<br>• **Developer API**: `MinionXAPI` plus 8 custom Bukkit events (`MinionPlaceEvent`, `MinionHarvestEvent`, etc.).<br>• **Commercial Proprietary License**: Standardized EULA authored by AbhinavVanama. |
| **0.1.2** | 2026-10-12 | **MinionX Rebrand & Paper Optimizations**:<br>• Rebrand to MinionX by AbhinavVanama.<br>• Paper native `setCanTick(false)` performance boost, modern `TextDisplay` billboard holograms, thread-safe atomic saves, anti-dupe hardening.<br>• Comprehensive `WIKI.md` documentation guide for server owners and developers. |
| **0.1.1** | 2026-10-12 | **Minion Storages & Emergency Quarantine**:<br>• Added Small (+3), Medium (+9), and Large (+15) storage chests with adjacent detection and GUI access.<br>• In-Game Skin Creator Studio to design custom skins.<br>• Emergency Quarantine Mode (`/minion quarantine`) to halt all minion actions during emergencies.<br>• Occasional floating NPC speech bubbles and owner interactions. |
| **0.1.0** | 2026-10-12 | **Authentic Minion Heads & Tier XII**:<br>• Bundled head textures for all 37 built-in minions across all tiers (Tiers I–XII).<br>• Added Tier XII progression with 5.0s delay and 15 storage slots.<br>• Minion Skin cosmetic system with 51 custom skins, dedicated GUI slot 37, and operator browser. |
| **0.0.9** | 2026-10-12 | **Operator Properties Editing & Entity AI Fixes**:<br>• Live editing of minion properties directly in `/minionsx`.<br>• Non-adjacent sapling spacing, farming animal archetype refinements.<br>• Added `/minion killall` purge and desync recovery tools. |
| **0.0.8** | 2026-10-12 | **Armor Sets & Duplicate Prevention**:<br>• Full armor set styling, grid-filling priority (debris/canes), mob gravity/AI defaults.<br>• Anti-stacking protection preventing multiple minions in the same block. |
| **0.0.7** | 2026-10-12 | **Farming & Foraging Enhancements**:<br>• 1-by-1 farmland tilling, perimeter tree growth, full tree felling, sugarcane hoe fix, authentic minion heads. |
| **0.0.6** | 2026-10-12 | **Geyser & Bedrock Support**:<br>• Touch screen optimization and Floodgate support.<br>• Stackable fuel duration calculation and one-time use fuel toggle. |
| **0.0.5** | 2026-10-12 | **Foraging & Farming Archetypes**:<br>• Woodchopping and crop harvesting automation with animal husbandry support.<br>• In-Game Minion Creator Studio GUI upgrades and categorized operator catalog. |
| **0.0.4** | 2026-10-12 | **Database Persistence & Integrations**:<br>• SQLite & MySQL storage support with thread-safe data synchronization.<br>• Land claim hooks: WorldGuard, GriefPrevention, Towny, Lands, SuperiorSkyblock.<br>• Economy & Skills hooks: Vault, RoyaleEconomy, AuraSkills, and EcoSkills integration.<br>• Deterministic O(1) offline production calculation. |
| **0.0.3** | 2026-10-12 | **In-Game Minion Creator & MythicMobs**:<br>• In-Game Minion Creator Studio to create custom minions dynamically.<br>• MythicMobs custom combat archetype integration.<br>• BentoBox island protection hooks and per-player minion limits. |
| **0.0.2** | 2026-10-12 | **5x5 Working Zone Mechanics**:<br>• Fill-before-mine mechanics, Y-1 elevation, anti-exploit protection.<br>• Categorized operator hub and toggleable nametags. |
| **0.0.1** | 2026-10-12 | **Operator Interface**:<br>• `/minionsx` panel with minion, fuel, and upgrade browsers. |
| **0.0.0** | 2026-10-12 | **Initial Core Engine**:<br>• Autonomous mining and combat archetypes, armor stand rendering, two-action cycle. |

> [!TIP]
> **Documentation Maintenance Rule Active**: Any future feature additions, command expansions, or configuration changes in MinionX will automatically be updated directly into this `WIKI.md` file.
