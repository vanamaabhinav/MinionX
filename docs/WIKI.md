# MinionX — Complete Guide & Documentation

> **MinionX** is an autonomous minion plugin designed for **Paper, Purpur, and Pufferfish (1.20.4 – 1.21+)**.  
> **Author:** AbhinavVanama  
> **Version:** `v1.0.1`  

---

## Table of Contents
1. [Overview](#1-overview)
2. [Interactive Minion Dashboard](#2-interactive-minion-dashboard)
3. [Minion Work Cycles & Mechanics](#3-minion-work-cycles--mechanics)
4. [Automated Shipping & Economy System](#4-automated-shipping--economy-system)
5. [External Minion Storage Chests](#5-external-minion-storage-chests)
6. [Upgrades, Compression & Accessories](#6-upgrades-compression--accessories)
7. [Fuel System (Temporary & Permanent)](#7-fuel-system-temporary--permanent)
8. [Cosmetic Skins & Per-Tier Visual Styles](#8-cosmetic-skins--per-tier-visual-styles)
9. [Per-Minion Tier Customization](#9-per-minion-tier-customization)
10. [Multi-Drop Engine & Custom Items](#10-multi-drop-engine--custom-items)
11. [Offline Production & Catch-Up](#11-offline-production--catch-up)
12. [RPG Skill Rewards & Collections](#12-rpg-skill-rewards--collections)
13. [Dialogue & Visual Displays](#13-dialogue--visual-displays)
14. [In-Game Creator Studios](#14-in-game-creator-studios)
15. [Operator Control Center (`/minionx`)](#15-the-operator-control-center-minionx)
16. [Configuration Files Guide](#16-configuration-files-guide)
17. [Plugin Integrations & Setup Guide](#17-plugin-integrations--setup-guide)
18. [Anti-Theft Security & Emergency Quarantine](#18-anti-theft-security--emergency-quarantine)
19. [Commands & Permissions Reference](#19-commands--permissions-reference)
20. [Frequently Asked Questions (FAQ)](#20-frequently-asked-questions-faq)

---

## 1. Overview

MinionX brings automated resource gathering to your server, inspired by classic skyblock minion mechanics while offering modern visual entities, full in-game creator menus, and extensible configurations.

### Key Capabilities:
* **Included Minion Types**: Comes with over 30 pre-configured minions across mining, farming, foraging, and combat categories (such as Cobblestone, Iron, Diamond, Wheat, Oak, Zombie, and Blaze).
* **Flexible Customization**: Server administrators can add brand-new minions, multi-drop loot tables, custom working delays, and unique item requirements either through configuration files or directly in-game.
* **Optimized Architecture**: Uses modern text display entities for floating nametags, non-ticking armor stands for minion models, optimized action scheduling, and offline catch-up simulation for unloaded chunks.
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

## 3. Minion Work Cycles & Mechanics

All minions in MinionX operate on a predictable, two-phase work cycle within a designated working radius:

### Operational Cycle:
1. **Generation Phase**:
   * Depending on the minion archetype, the minion places its target block (Mining), plants a crop (Farming), places a sapling or tree (Foraging), or spawns its target mob entity (Combat & Animal Husbandry) within its working area.
2. **Harvest Phase**:
   * Once targets are ready (ores generated, crops mature, logs grown, or mobs present), the minion harvests the target, collecting raw resources into its internal storage.

### Working Radius & Expansion:
* **Default Boundary**: Base minions work within a 5×5 block area centered on their placement location.
* **Expansions**: Placing 1 Minion Expander upgrade increases the working area to 7×7, and 2 Minion Expanders increase it to 9×9.

### Target Scanning & Status Feedback:
* Minions actively check their working area before performing a harvest.
* If targets wander or are pushed outside the working radius, the minion pauses and displays a status indicating it is waiting for targets within range.
* Targets outside the working area remain tracked to prevent excessive spawning.

### Entity Safety & Player Interaction:
* **Daylight Protection**: Undead mobs spawned by combat minions are protected from daylight combustion through event interception, allowing them to remain active in daylight without wearing helmets.
* **Player Kills & Gathering**: If a player slays minion-spawned mobs or harvests minion blocks manually, standard vanilla drops and experience are granted naturally.
* **Pickup Safety**: Picking up a minion safely leaves existing blocks or spawned entities in the world for players to handle.

---

## 4. Automated Shipping & Economy System

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

## 5. External Minion Storage Chests

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
* **Co-op Team Protection**: Island teammates cannot break linked chests owned by another player unless `allow-team-break-storage: true` is set in `config.yml`.
* **Withdrawal-Only Mode**: Minion chests are designed for withdrawal only; players cannot accidentally insert unwanted items or drag external items into them.
* **Hopper Siphoning Prevention**: Vanilla hoppers, droppers, and hopper minecarts are prevented from pulling items out of or pushing items into minion chests.
* **Break Safety**: Breaking the chest safely drops all stored contents and the storage chest item on the ground.

---

## 6. Upgrades, Compression & Accessories

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

## 7. Fuel System (Temporary & Permanent)

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

## 8. Cosmetic Skins & Per-Tier Visual Styles

Minion appearance can be customized through cosmetic skins or per-tier visual progression:

* **Cosmetic Skins**:
  * **Custom Skull Textures**: Supports custom head textures via Base64 textures or standard player usernames.
  * **Complete Armor Sets**: Equipping a skin dresses the minion in matching armor (chestplate, leggings, and boots).
  * **Leather Armor Dyeing**: Supports custom Hex color codes (`#RRGGBB`), RGB values, or standard color names to give leather armor distinct color palettes.
  * **Metallic Armor Styles**: Supports metallic armor sets including Chainmail, Iron, Gold, Diamond, and Netherite.
  * **Handheld Items**: Allows the minion to hold custom tools or items in its main hand (swords, pickaxes, axes, or novelty items).
  * **Equipping**: Placed in **Slot 10** of the Minion GUI. Clicking an active skin with an empty cursor returns it safely to the player's inventory.
* **Per-Tier Visual Styles (`tier-styles:`)**:
  * In addition to cosmetic skins, minions can display unique visual styles for each tier configured in `minions.yml`.
  * Each tier can define a distinct custom head texture (Base64 or player name), armor material, leather dye color, and held tool/block as the minion levels up.

---

## 9. Per-Minion Tier Customization

Different minion types can be configured with distinct maximum tiers, action delays, storage capacities, and upgrade costs.

* **Global Defaults**: Defined in `tiers.yml` for Tiers 1 through 12+.
* **Individual Overrides**: Minions can have custom `max-tier` caps (such as capping a specialized minion at Tier 5 or Tier 10) and custom tier progression tables in `minions.yml`.
* **GUI Integration**: When a minion reaches its individual maximum tier, the upgrade button in its menu displays a maximum tier indicator.

---

## 10. Multi-Drop Engine & Custom Items

MinionX supports multi-drop tables and custom item frameworks for both harvest yields and compression recipes.

### Drop Capabilities:
* **Multiple Drops per Harvest**: A minion can produce primary and secondary items simultaneously (such as a Blaze minion yielding Blaze Rods at 100% and Magma Cream at 50%).
* **Drop Probabilities**: Each drop supports a decimal percentage chance (`1.0` = 100%, `0.33` = 33%).
* **Custom Item Attributes**: Supports custom display names, lore lines, CustomModelData numbers, enchantment glints, and custom NBT or plugin tags.
* **Plugin Integrations**: Directly references items from plugins such as ItemsAdder (`itemsadder:ruby`), MMOItems (`mmoitems:TYPE_ID`), or Oraxen.
* **Metadata Protection**: The compression engine strictly respects custom item metadata, preventing vanilla recipes from accidentally consuming items that have custom names or models.

---

## 11. Offline Production & Catch-Up

When chunks unload or the server restarts, minions calculate their production using an offline catch-up calculation upon reload:

* **Time-Segmented Calculation**: The engine evaluates elapsed time since the minion's last action, accounting for fuel expiration boundaries during the offline window.
* **Capacity Saturation**: Completed production cycles are simulated sequentially. If storage reaches capacity, all completed cycles that fit are credited, the minion transitions to the `FULL` state, and the remaining offline duration does not accumulate unearned backlogs.
* **Overflow Handling**: If an Automated Shipping hopper is equipped, overflow items from filled storage convert into coins at the configured sell ratio.
* **Offline Limit**: The maximum simulated offline period can be capped in `config.yml` (default: 720 hours / 30 days).

---

## 12. RPG Skill Rewards & Collections

Collecting resources from minions can reward players with skill experience and advance server-wide collections.

### Skill Integrations:
* **Supported Plugins**: Hooks into **AuraSkills**, **EcoSkills**, and **mcMMO**.
* **Experience Rewards**: When players claim items using "Collect All" or by clicking storage slots, skill experience is granted based on the minion type (such as Mining XP for ore minions, Foraging XP for wood minions, or Farming XP for crop minions).
* **Configuration**: Experience values and associated skill names are customizable in `config.yml` under `skill-rewards`.

### Collection Integrations:
* **EcoCollections & SuperiorSkyblock2**: Automatically updates player and island collection statistics upon resource collection.
* **Custom Command Rewards**: Configurable commands can be triggered when players harvest resources (defined in `config.yml` under `collection-rewards`).

---

## 13. Dialogue & Visual Displays

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

## 14. In-Game Creator Studios

MinionX provides interactive in-game menus allowing administrators to configure content without manual file editing:

* **Minion Creator (`/minion create`)**: Configure new minion types, target blocks or mobs, held items, primary/secondary drops, and tier limits.
* **Held Tool & Weapon Studio Slot (Slot 24)**: Customize the minion's held equipment directly. Click Slot 24 with any item on your cursor to equip it instantly, or click with an empty cursor to cycle through default tools.
* **Upgrade Creator (`/minion create upgrade`)**: Create custom automated shipping hoppers, compactors, speed catalysts, and accessories with adjustable sell ratios and speed boosts.
* **Fuel Creator (`/minion create fuel`)**: Create fuels with custom durations, speed multipliers, and glint effects.
* **Skin Creator (`/minion create skin`)**: Design cosmetic skins with custom skull textures, leather dye colors, and held equipment.
* **Storage Creator (`/minion create storage`)**: Create custom minion storage chests from 1 to 27 slots.
* **Tier Editor (`/minion tier` or `/minion tier <id>`)**: Adjust action delays, storage slot counts, and upgrade costs live. Supports creating custom tiers beyond Tier XII.

---

## 15. Operator Control Center (`/minionx`)

The Operator Control Center (`/minionx` or aliases `/minion op`, `/minionsx`, `/mxop`) provides an administrative dashboard:

* **Minions Catalog (Slot 20)**: Inspect registered minions and claim test copies.
* **Upgrades Catalog (Slot 22)**: Browse all upgrades and launch the Upgrade Creator Studio.
* **Fuels Vault (Slot 24)**: Browse temporary and permanent fuels and launch the Fuel Creator Studio.
* **Skins Catalog (Slot 28)**: Browse available skins with direct item delivery.
* **Storages Catalog (Slot 34)**: Browse external storage chest models.
* **Emergency Quarantine (Slot 51)**: Quickly toggle quarantine mode across the server.

---

## 16. Configuration Files Guide

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

## 17. Plugin Integrations & Setup Guide

MinionX natively interfaces with popular server plugins, providing automatic hook detection, island claim enforcement, custom item resolution, and progression rewards.

### 1. MythicMobs Integration
Combat minions can be configured to spawn and slay custom MythicMobs:

* **Configuration in `minions.yml`**:
  Set `target-mob` to your MythicMobs internal ID:
  ```yaml
  custom-minions:
    skeletal_knight_minion:
      display-name: "<gold>Skeletal Knight Minion"
      archetype: COMBAT
      target-mob: "SkeletalKnight" # MythicMobs mob ID
      head-texture: "eyJ0ZXh0dXJlcyI6..."
      hand-tool: "DIAMOND_SWORD"
      drops:
        - material: "BONE"
          amount: 2
          chance: 1.0
        - item: "mmoitems:SWORD:BONE_DAGGER" # Optional custom item
          amount: 1
          chance: 0.15
  ```
* **In-Game Configuration (`/minion create`)**:
  1. Open `/minion create` and select the `COMBAT` archetype.
  2. Right-click Slot 9 (`Target Mob`) or type the mob ID into chat when prompted (e.g., `SkeletalKnight`).
  3. Pick up any item onto your cursor and place it into Slot 12 (Drops) or Slot 24 (Held Tool/Weapon) to set custom equipment.
* **Entity Management**:
  * MinionX calls `io.lumine.mythic.bukkit.MythicBukkit.get().getMobManager().spawnMob()` automatically when MythicMobs is present.
  * Tagged with `minion_spawned_mob` and `minion_owner_id` PDC keys and preserved from despawning (`setRemoveWhenFarAway(false)`).
  * Minions harvest mobs within their 5×5 area into internal storage. If a player attacks the mob directly, MythicMobs calculates damage and grants standard rewards.

### 2. SuperiorSkyblock2 & Island Networks (BentoBox)
Designed specifically for SkyBlock networks:

* **Island Claim Protection**:
  * Minions can only be placed within the boundaries of islands where the placing player is an active member or has build permissions.
  * Checked via `SuperiorSkyblockAPI.getPlayer(player).getIsland()` and BentoBox `IslandsManager.getIslandAt(location)`.
* **Automatic Island Collections**:
  * When players harvest items or click "Collect All", MinionX automatically detects SuperiorSkyblock2 and updates island collections via `island.handleBlockBreak(material, amount)`.
  * No extra commands or configuration required.
* **Co-op Anti-Theft Security**:
  * Island team members can interact with minion menus and collect stored items by default (`security.access-mode: TEAM_ACCESS`).
  * Only the original owner can pick up their minion (`security.allow-team-pickup: false`). Co-op teammates cannot steal or dismantle another player's minion.
  * Linked storage chests cannot be broken by teammates unless `settings.allow-team-break-storage: true` is enabled in `config.yml`.
* **Island Limits**:
  * Minion placement limits can be enforced per island or player rank using `minionx.limit.<number>` permissions or `/minion limit set <player> <amount>`.

### 3. Custom Item Plugins (ItemsAdder, MMOItems, Oraxen, EcoItems)
MinionX supports custom items seamlessly across all drops, recipes, and equipment:

* **Supported Identifiers**:
  * `itemsadder:<namespace>:<id>` or `itemsadder:<id>` (e.g. `itemsadder:ruby`)
  * `mmoitems:<TYPE>:<ID>` (e.g. `mmoitems:SWORD:FIRE_KATANA`)
  * `ecoitems:<id>` (e.g. `ecoitems:enchanted_iron`)
  * Vanilla materials (e.g. `DIAMOND`)
* **In-Game Item Cloning**:
  * Inside `/minion create`, pick up any custom item from your inventory onto your cursor and click Slot 12 (Drop Table) or Slot 24 (Hand Weapon/Tool).
  * MinionX clones all CustomModelData, display names, hex lore, and PersistentDataContainer tags directly.
* **Super Compactor Support (`recipes.yml`)**:
  * Configure custom recipes that convert raw items into compacted custom items.
  * Vanilla compression recipes strictly reject items with CustomModelData or custom names, preventing accidental consumption of valuable RPG items.

### 4. Economy Integrations (Vault & RoyaleEconomy)
* Automated Shipping hoppers sell overflow items into the server economy when minion storage fills up.
* **Budget Hopper**: Sells excess items at 50% of the price defined in `prices.yml`.
* **Enchanted Hopper**: Sells excess items at 90% of base price.
* Coins accumulate safely in the hopper slot and deposit directly into the player's account via Vault when collected.

### 5. RPG Skill Integrations (AuraSkills, EcoSkills, mcMMO)
* Players receive skill experience when claiming items from minions.
* Configured in `config.yml` under `skill-rewards`:
  ```yaml
  skill-rewards:
    cobblestone:
      skill: "mining"
      xp-per-item: 1.0
    wheat:
      skill: "farming"
      xp-per-item: 2.0
    zombie:
      skill: "combat"
      xp-per-item: 3.5
  ```

### 6. Land Protection Integrations (WorldGuard, GriefPrevention, Towny, Lands)
* **WorldGuard**: Checks build and block placement permissions.
* **GriefPrevention**: Checks claim ownership and trust level (`allowBuild` for placement, `allowContainers` for access).
* **Towny**: Verifies resident plot build permissions via `TownyAPI`.
* **Lands**: Checks land area flags via `LandsIntegration` (`canBuild` and `canAccess`).

### 7. Bedrock Edition (GeyserMC & Floodgate)
* Automatically detects Bedrock players and adapts menu navigation with symmetrical, touch-friendly slot layouts for mobile and console players.

---

## 18. Anti-Theft Security & Emergency Quarantine

MinionX is built with safety controls to protect player inventories, prevent duplication exploits, and ensure zero item loss during server restarts or crashes.

### Security Highlights:
* **Anti-Duplication Locks**:
  * All player interactions, storage transfers, and shipping hopper coin claims use internal action locks to prevent click-spamming, inventory glitching, or packet injection exploits.
* **Co-op Anti-Theft & Container Protection**:
  * Minion chests cannot be accessed by vanilla hoppers, droppers, or hopper minecarts.
  * On SkyBlock servers (SuperiorSkyblock2, BentoBox), only the player who placed the minion can pick it up. Teammates cannot steal or dismantle another player's minion.
  * Teammates are also prevented from breaking another player's linked minion storage chest unless `allow-team-break-storage: true` is explicitly enabled in `config.yml`.
* **Zero Item Loss on Pickup (`/minion claim`)**:
  * When a player picks up a minion, all stored items, upgrades, active fuel, and experience are safely secured before the minion is removed from the world.
  * If the player's inventory fills up during pickup, the remaining items and experience are held safely in a secure pending queue. The player can collect them at any time using `/minion claim` once inventory space is cleared.
* **Crash Recovery & Item Protection (`/minion recover`)**:
  * If a server restarts or unexpectedly stops mid-pickup or during an economy payout, items and experience are never deleted or duplicated.
  * Pending recoveries are safely preserved across server reboots. Administrators can inspect and resolve pending recoveries at any time with `/minion recover`.
* **Emergency Quarantine Mode (`/minion quarantine on`)**:
  * If server staff suspect an exploit or need to conduct maintenance, running `/minion quarantine on` immediately freezes all placed minion actions, pickups, and GUI interactions network-wide without needing to kick players or take the server offline.
  * Running `/minion quarantine off` instantly restores normal minion operations.

---

## 19. Commands & Permissions Reference

### Commands:
| Command | Permission | Description |
|---|---|---|
| `/minionx` | `minionx.admin` | Opens the Operator Command Center (Aliases: `/minion op`, `/minionsx`, `/mxop`). |
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
* `minionx.admin`: Access to administrative commands, studios, and `/minionx` (Default: `op`).
* `minionx.bypass.limit`: Bypasses the placed minion limit.
* `minionx.limit.<number>`: Sets a custom placed minion limit for a player or permission group.

---

## 20. Frequently Asked Questions (FAQ)

**Q: How does Automated Shipping sell items when storage fills up?**  
A: When both the minion's internal inventory and any linked storage chest are full, equipped hoppers automatically sell further harvested items using prices in `prices.yml`. Earned coins accumulate in the hopper slot and can be collected at any time.

**Q: Can players take items from another player's minion chest?**  
A: No. Storage chests are protected by owner verification. Only the minion's owner or an operator can open the chest, and items can only be withdrawn. Furthermore, co-op team members cannot break linked storage chests unless `allow-team-break-storage` is explicitly set to `true`.

**Q: How does offline catch-up work?**  
A: When an unloaded chunk reloads or the server restarts, MinionX computes elapsed downtime cycle-by-cycle across fuel boundaries, applying compaction recipes, and stopping gracefully if storage reaches capacity without dropping or deleting unpriced items.

**Q: Can custom minions have different maximum tiers?**  
A: Yes. You can set `max-tier` in `minions.yml` or through `/minion create` to limit specific minions to any tier you prefer.
