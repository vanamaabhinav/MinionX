# MinionX Administrator Manual & Server Owner Documentation

> **MinionX** - Autonomous Minions Plugin for **Paper / Purpur / Pufferfish 1.20.4 – 1.21+**  
> **Author:** AbhinavVanama  
> **Plugin Version:** `v1.0.1` (Release)  
> **Source Repository:** [https://github.com/vanamaabhinav/MinionX](https://github.com/vanamaabhinav/MinionX)

---

## Table of Contents
1. [System Requirements & Installation](#1-system-requirements--installation)
2. [Quick Start Deployment](#2-quick-start-deployment)
3. [Minion Work Cycles & Mechanics](#3-minion-work-cycles--mechanics)
4. [Modular Configuration Architecture](#4-modular-configuration-architecture)
5. [In-Game Creator Studios](#5-in-game-creator-studios)
   * [Minion Creator Studio (`/minion create`)](#minion-creator-studio)
   * [Upgrade & Hopper Creator Studio (`/minion create upgrade`)](#upgrade--hopper-creator-studio)
   * [Fuel Creator Studio (`/minion create fuel`)](#fuel-creator-studio)
   * [Tier Creator & Editor Studio (`/minion tier`)](#tier-creator--editor-studio)
   * [Skin Creator Studio (`/minion create skin`)](#skin-creator-studio)
   * [Storage Creator Studio (`/minion create storage`)](#storage-creator-studio)
6. [Per-Minion Max Tier Caps & Per-Tier Styles](#6-per-minion-max-tier-caps--per-tier-styles)
7. [Super Compactor 3000 & Custom Items Support](#7-super-compactor-3000--custom-items-support)
8. [Multi-Drop Engine with Chance Percentages](#8-multi-drop-engine-with-chance-percentages)
9. [Operator Control Center (`/minionx`)](#9-operator-control-center-minionx)
10. [Anti-Theft Security & Emergency Quarantine Mode](#10-anti-theft-security--emergency-quarantine-mode)
11. [Plugin Integrations & Protection Hooks](#11-plugin-integrations--protection-hooks)
12. [Complete Commands & Permissions Reference](#12-complete-commands--permissions-reference)
13. [Administrator FAQ & Troubleshooting](#13-administrator-faq--troubleshooting)
14. [Developer API & Events Guide](#14-developer-api--events-guide)

---

## 1. System Requirements & Installation

MinionX is engineered for production Minecraft servers with bounded-budget tick scheduling, non-ticking display entities, and deterministic offline downtime simulation.

### Requirements:
* **Server Software:** Paper, Purpur, or Pufferfish (1.20.4 – 1.21.x). *(Spigot and CraftBukkit are not supported due to modern display entities and asynchronous tick requirements)*.
* **Java Version:** Java 21 LTS or newer.
* **Network:** Optional connection for asynchronous GitHub update checks and anonymous bStats metrics.

### Step-by-Step Installation:
1. Download the latest `MinionX-1.0.1.jar` build.
2. Drop the jar file into your server's `plugins/` folder.
3. Start or restart your server.
4. MinionX will automatically generate its configuration folder: `plugins/MinionX/`.

---

## 2. Quick Start Deployment

Giving and placing minions in MinionX requires zero complex commands:

### Giving a Minion:
```bash
/minion give <player> <type> [tier]
```
*Example:* `/minion give Steve diamond 11` (Gives a Tier XI Diamond Minion).

### Deploying the Minion:
1. Place the minion head item on any solid block in the world.
2. The minion spawns with working animations, billboard text display holograms, and commences its cycle:
   * **Generation & Harvest:** Places, plants, or spawns target resources within its working area, then harvests them into inventory.
   * **Full Mechanics:** Detailed in Section 3 below.
3. Right-click the minion at any time to open its 54-slot interactive control GUI.

---

## 3. Minion Work Cycles & Mechanics

All minions in MinionX operate on a predictable, two-phase work cycle within a designated working radius:

1. **Two-Phase Action Cycle**:
   * **Generation Phase:** Depending on minion archetype, the minion places its target block (Mining), plants a crop (Farming), places a sapling/tree (Foraging), or spawns its target mob entity (Combat & Animal Husbandry) within its working area.
   * **Harvest Phase:** The minion harvests fully generated targets (breaking ores, harvesting mature crops, felling logs, or slaying mobs), depositing the resulting items into its internal storage.

2. **Working Radius & Area Boundaries**:
   * Base minions work within a default 5×5 area centered on their placement location.
   * Equipping Minion Expander upgrades increases the working area to 7×7 (+1 expander) or 9×9 (+2 expanders).

3. **Target Scanning & Status Feedback**:
   * Minions actively scan their designated working boundary before executing a harvest.
   * If all spawned mobs or harvestable blocks wander or are placed outside the boundary, the minion pauses and displays a status indicating it is waiting for targets within range.
   * Entities outside the working area remain tracked to prevent accidental over-spawning.

4. **Entity Safety & Environmental Protection**:
   * Mobs spawned by combat minions are protected from daylight fire through event interception, allowing them to remain active in open-air daylight.
   * When players slay minion-spawned mobs or mine minion-placed blocks manually, standard vanilla drops and experience are granted naturally.
   * Picking up a minion safely leaves any active placed blocks or spawned mobs in the world for players to handle.

5. **Storage, Compaction & Overflow**:
   * Collected resources fill internal inventory slots first, then overflow into linked external storage chests.
   * If equipped with a Super Compactor 3000, items are automatically compacted into condensed or enchanted forms.
   * Once all available storage is saturated, the minion enters a `Full` state unless equipped with an automated shipping hopper to sell overflow directly into the server economy.

---

## 4. Modular Configuration Architecture

To keep settings tidy and avoid monolithic configuration files, MinionX splits its settings into dedicated files:

```
plugins/MinionX/
├── config.yml      # General server settings, limits, storage engine & hooks
├── tiers.yml       # Action delays, storage slots, and upgrade costs per tier
├── minions.yml     # Custom minion definitions, max tier caps & multi-drop tables
├── fuels.yml       # Temporary and permanent fuel items, speed buffs & burn times
├── upgrades.yml    # Accessories, auto-smelters, compactors & hoppers
├── recipes.yml     # Super Compactor 3000 custom and vanilla compression recipes
└── messages.yml    # Broadcasts, action messages, prefix & minion chat dialogues
```

### `config.yml` Core Settings:
```yaml
settings:
  # If true, temporary burning fuel is destroyed upon removal
  destroy-temporary-fuel-on-removal: true
  # Geyser / Bedrock touch-friendly pagination
  geyser-touch-friendly: true
  # Ticking frequency (20 ticks = 1 second)
  tick-interval: 20
  # Asynchronous atomic disk save interval in ticks (6000 ticks = 5 minutes)
  auto-save-interval: 6000
  # Default maximum minions placed per player (-1 for unlimited)
  max-minions-per-player: 5
  # Offline simulation cap in hours (720 = 30 days)
  max-offline-hours: 720
  # Holograms & visuals
  enable-animations: true
  enable-sounds: true
  enable-particles: true

storage:
  type: YAML # Supported: YAML (default), SQLITE, MYSQL
  # NOTE: MySQL is for persistent relational storage on a single server instance.
  # Multiple servers must use separate, isolated database schemas. Cross-server
  # proxy synchronization is not supported.
```

---

## 5. In-Game Creator Studios

MinionX features full-fledged in-game visual GUIs for designing, customizing, and deploying plugin content without touching YAML files.

### Minion Creator Studio
* **Command:** `/minion create`
* **Features:**
  * Define Minion ID and formatted display name.
  * Select archetype: `MINER`, `FORAGING`, `FARMING`, or `COMBAT`.
  * Set primary block/ore to place and harvest.
  * **Slot 12 (Exact Primary Drop):** Pick up any item onto your cursor to capture an exact clone (preserving custom lore, display name, and CustomModelData).
  * **Slot 13 (Secondary Drop):** Optional second drop with custom probability percentage.
  * **Slot 24 (Hand Tool / Weapon):** Pick up any item onto your cursor to equip it directly into the minion's hand, or click with an empty cursor to cycle through standard tools and weapons.
  * **Slot 33 (Max Tier Cap):** Adjust individual maximum tier limit (1 to max tier).
  * **Per-Tier Visual Styles Toggle:** Customize head textures, armor materials, leather dye colors, and held items individually for each tier.
  * Integrate custom MythicMobs internal IDs for combat minions.

### Upgrade & Hopper Creator Studio
* **Command:** `/minion create upgrade` (or click Slot 22 inside `/minionx`)
* **Features:**
  * Configure upgrade ID, display name, and item material.
  * Select upgrade behavior: automated shipping hopper, compaction, auto-smelter, speed boost, or area expander.
  * Configure economy sell ratio (for hoppers), speed multiplier percentages, and radius expansions.
  * Live test item delivery and direct atomic persistence to `upgrades.yml`.

### Fuel Creator Studio
* **Command:** `/minion create fuel` (or click Slot 50 inside `/minionx` Fuel Supplies)
* **Features:**
  * Configure fuel ID and display name via interactive chat prompt.
  * Select fuel material (Blaze Powder, Lava Bucket, Charcoal, Dragon Breath, etc.).
  * Adjust speed multipliers with 5% and 10% step controls.
  * Toggle **Permanent** (never expires) vs **Temporary** (burns down).
  * Cycle duration presets: `30m`, `1h`, `2h`, `6h`, `12h`, `1d`, `3d`, `7d`.
  * Toggle enchantment glint (`glow: true/false`).
  * Live interactive item preview with instant test copy delivery.
  * Direct atomic persistence to `fuels.yml`.

### Tier Creator & Editor Studio
* **Commands:**
  * `/minion tier` — Opens the **Global Tier Studio** (edits default tiers saved to `tiers.yml`).
  * `/minion tier <minion_id>` — Opens the **Per-Minion Tier Studio** (configures individual tiers, speed, capacity, upgrade cost, and max tier for that minion saved to `minions.yml`).
* **Features:**
  * Visual overview of all tiers with Roman numeral calculation (I to XII+).
  * **Per-Minion Max Tier Controls:** Interactive + and - buttons to adjust the minion's maximum tier cap in real time.
  * Click any tier to adjust action speed delay (down to 0.1s increments).
  * Adjust internal storage slots (1 to 27 slots) with automatic max capacity updates.
  * Adjust item upgrade cost requirements.
  * Add arbitrary custom tiers beyond Tier XII on the fly.
  * Dual-mode persistence: Global edits write to `tiers.yml`, while per-minion edits write directly to the minion's `tiers:` map in `minions.yml`.

### Skin Creator Studio
* **Command:** `/minion create skin` (or click Slot 50 in `/minionx`)
* **Features:**
  * Design custom minion skins using player head textures or Base64 skins.
  * Adjust leather armor RGB color tints and held weapons.
  * Saves directly to `skins.yml`.

### Storage Creator Studio
* **Command:** `/minion create storage`
* **Features:**
  * Create custom Minion Storage chest expansions from 1 to 27 slots.
  * Visual symmetrical slot preview centered around Slot 13.
  * Saves directly to `storages.yml`.

---

## 6. Per-Minion Tier Customization & Per-Tier Visual Styles

Different minions can have different maximum tier caps, individual speeds, storage capacities, upgrade costs, and visual styles per tier.
* Standard Ore Minions: Progress up to **Tier XII** using global defaults.
* Specialized Resource Minions: Can be capped at **Tier V** or **Tier X**.
* Each tier for a specific minion can override `action-delay`, `storage-slots`, and `upgrade-cost`. Any tier not explicitly overridden automatically falls back to global settings in `tiers.yml`.
* **Per-Tier Visual Styles (`tier-styles:`)**: Give each tier a unique head texture (Base64 or player name), armor material, leather dye color, and held tool or block as the minion levels up.

### Configuration in `minions.yml`:
```yaml
custom-minions:
  spider_example:
    display-name: "&cSpider Minion"
    archetype: COMBAT
    max-tier: 5  # <-- Minion cannot be upgraded past Tier V!
    tiers:
      1:
        action-delay: 26.0
        storage-slots: 2
        upgrade-cost: 64
      2:
        action-delay: 24.0
        storage-slots: 3
        upgrade-cost: 128
      3:
        action-delay: 22.0
        storage-slots: 4
        upgrade-cost: 256
      4:
        action-delay: 20.0
        storage-slots: 5
        upgrade-cost: 512
      5:
        action-delay: 18.0
        storage-slots: 6
        upgrade-cost: 1024
    drops:
      string:
        material: STRING
        amount: 1
        chance: 1.0
      spider_eye:
        material: SPIDER_EYE
        amount: 1
        chance: 0.33
        display-name: "&cFresh Spider Eye"
        custom-model-data: 1001
        glow: true
```

When a minion reaches its individual `max-tier`, the Upgrade button inside its GUI dynamically transforms into Bedrock with the status:
`&cMaximum Tier Reached (%tier%)`.

---

## 7. Super Compactor 3000 & Unified Custom Items Support

Both `minions.yml` (drops) and `recipes.yml` (compression input/output) share the **identical unified custom item schema**. You can define vanilla items, custom RPG items, or third-party hook items (`itemsadder:`, `mmoitems:`, `oraxen:`, `mythicmobs:`) seamlessly.

### Unified Custom Item Schema:
| Key | Type | Description |
|---|---|---|
| `material` / `item` | String | Bukkit Material or hook syntax (e.g. `itemsadder:ruby`, `mmoitems:SWORD_KATANA`) |
| `amount` / `input-amount` | Integer | Quantity of items |
| `chance` | Double | Drop probability from `0.0` to `1.0` (minion drops only) |
| `display-name` | String | Formatted item title with color codes (`&6`, `&c`, etc.) |
| `custom-model-data` | Integer | CustomModelData integer for Resource Packs |
| `lore` | List<String> | Multiline lore strings |
| `glow` / `enchanted` | Boolean | Adds subtle enchantment glow |
| `pdc-key` | String | PersistentDataContainer namespace key (e.g. `minionx:drop`) |
| `pdc-value` | String | PersistentDataContainer value for strict RPG verification |

### Configuration Example in `recipes.yml`:
```yaml
compression-recipes:
  # Vanilla recipe
  enchanted_diamond:
    input:
      material: DIAMOND
      amount: 160
    output:
      material: DIAMOND
      amount: 1
      display-name: "&9Enchanted Diamond"
      glow: true

  # Custom RPG Item recipe
  enchanted_ruby:
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
```

### Anti-Swallowing Strict Isolation:
The Super Compactor algorithm verifies custom item properties strictly:
* Vanilla recipes require **unmodified** items. An item with CustomModelData, custom display name, or custom PDC tags will **never** be swallowed by a vanilla recipe.
* Custom recipes require exact matching on configured metadata (CustomModelData, display name, and PDC tags), ensuring zero item loss or accidental conversion.


---

## 8. Multi-Drop Engine with Chance Percentages

Minions can produce multiple distinct drop items simultaneously, each with an independent chance percentage:

```yaml
custom-minions:
  blaze_boss_minion:
    display-name: "&6Inferno Minion"
    archetype: COMBAT
    drops:
      blaze_rod:
        material: BLAZE_ROD
        amount: 1
        chance: 100.0   # Guaranteed drop
      magma_cream:
        material: MAGMA_CREAM
        amount: 1
        chance: 50.0    # 50% probability
      fire_charge:
        material: FIRE_CHARGE
        amount: 2
        chance: 15.0    # Rare 15% bonus drop
```

---

## 9. Operator Control Center (`/minionx`)

The Operator Control Center provides administrators with a single, unified control panel:

* **Slot 20 (Minions Catalog):** Browse all built-in and custom minions with tier previews.
* **Slot 22 (Fuel Supplies):** Browse all fuels and launch the **Fuel Creator Studio** (Slot 50).
* **Slot 24 (Upgrades Catalog):** Access Auto Smelters, Compactors, Super Compactors, Diamond Spreading, and Hoppers.
* **Slot 28 (Skins Catalog):** Browse 51 built-in minion skins with 1-click delivery.
* **Slot 30 (Admin Starter Kits):** Instant test setups (Cobblestone Starter, Iron Automaton, Diamond Master).
* **Slot 32 (Minion Creator Studio):** Launch in-game minion creation.
* **Slot 34 (Storages Catalog):** Browse Small, Medium, Large, and custom storage chests.
* **Slot 49 (Tier Studio):** Live tier configuration and Roman numeral editor.
* **Slot 50 (Custom Skin Creator):** Launch skin creator studio.
* **Slot 51 (Emergency Quarantine):** 1-click server-wide lockdown toggle.
* **Slot 48 (Purge Minions):** Despawn all active minion entities.
* **Slot 53 (Reload Plugin):** Hot-reload all configurations and registries.

---

## 10. Anti-Theft Security & Emergency Quarantine Mode

MinionX implements robust protection against griefing, duplication, inventory desync, and crash exploits:

### Security & Durability Highlights:
* **Co-op Anti-Theft Security:** Island team members can open minion menus and collect items, but cannot pick up minions or break linked storage chests owned by other teammates unless `allow-team-break-storage` or `security.allow-team-pickup` is explicitly enabled in `config.yml`.
* **Thread-Safe Data Operations:** All inventory modifications and database flushes are fully synchronized and thread-safe, preventing transaction race conditions and menu click-spamming exploits.
* **Atomic Disk Serialization:** Minion states are written to temporary files and atomically moved to destination paths, guaranteeing zero corrupted or truncated files if a server crashes mid-write.
* **Safe Pickups & Pending Claim Queue (`/minion claim`):**
  * Minion pickups are executed in a safe, multi-phase sequence: minion items, upgrades, fuel, and experience are secured before the minion entity is removed from the world.
  * If a player's inventory fills up during pickup, the uncollected items and experience are held in a pending claim queue. Players can recover them at any time with `/minion claim`.
* **Crash-Proof Ledger & Recovery (`/minion recover`):**
  * If a server restarts or crashes unexpectedly mid-pickup or during an economy payout, the operation is safely recorded and quarantined across reboots to prevent item loss or duplication.
  * Administrators can inspect, deliver, or audit pending operations at any time using `/minion recover`.
* **Automated Shipping Protection:**
  * Payout calculations protect against mathematical overflow, and coin deposits are safely handled without balance corruption.
* **Emergency Quarantine Mode (`/minion quarantine on`):**
  * Immediately freezes all minion item collections, breaking/pickups, and placements across the entire server.
  * Allows administrators to perform maintenance or investigate issues safely without kicking players or stopping the server.
  * Running `/minion quarantine off` instantly restores normal minion operations.

---

## 11. Plugin Integrations & Protection Hooks

MinionX provides native integration with major Minecraft plugins, handling claims, custom items, economy, and progression systems out-of-the-box.

### 1. MythicMobs Integration Guide
MinionX allows combat minions to spawn and harvest custom MythicMobs instead of vanilla mobs:

* **Configuration in `minions.yml`**:
  Set `target-mob` to any valid MythicMobs internal ID:
  ```yaml
  custom-minions:
    skeletal_knight_minion:
      display-name: "<gold>Skeletal Knight Minion"
      archetype: COMBAT
      target-mob: "SkeletalKnight" # Your MythicMobs internal ID
      head-texture: "eyJ0ZXh0dXJlcyI6..."
      hand-tool: "DIAMOND_SWORD"
      drops:
        - material: "BONE"
          amount: 2
          chance: 1.0
        - item: "mmoitems:SWORD:BONE_DAGGER" # Optional custom item drop
          amount: 1
          chance: 0.15
  ```
* **In-Game Creation (`/minion create`)**:
  1. Open `/minion create` and select the `COMBAT` archetype (Slot 4).
  2. Right-click Slot 9 (`Target Mob`) or follow the chat prompt: type your MythicMob ID (e.g., `SkeletalKnight`).
  3. Pick up any item onto your cursor and place it in Slot 12 (Drop Table) or Slot 24 (Held Tool/Weapon) to set equipment and drops.
* **Mob Management & Spawning**:
  * MinionX automatically checks if MythicMobs is enabled and calls `MythicBukkit.get().getMobManager().spawnMob()`.
  * The spawned entity is tagged with `minion_spawned_mob` and `minion_owner_id` in its PersistentDataContainer, preventing it from despawning (`setRemoveWhenFarAway(false)`).
  * Minions slay mobs within their 5×5 working area and award the configured drops into minion storage.
  * If a player slays a minion-spawned mob manually with weapons, MythicMobs handles player damage and grants standard loot and experience.

### 2. SuperiorSkyblock2 & Island Protection (BentoBox)
MinionX includes dedicated handling for skyblock island networks:

* **Island Claim Protection**:
  * Minions can only be placed within the boundary of an island where the player is a member or has build permission.
  * Verifies island permissions via `SuperiorSkyblockAPI.getPlayer(player).getIsland()` or BentoBox `IslandsManager.getIslandAt(location)`.
* **Automatic Island Collections**:
  * When players collect harvested items from a minion (via "Collect All" or by clicking individual slots), MinionX automatically forwards the progress directly to the island's collection using `island.handleBlockBreak(material, amount)`.
  * No external commands, bridges, or custom scripts are required.
* **Co-op Anti-Theft Protection**:
  * Co-op teammates can open minion dashboards and collect resources by default (`security.access-mode: TEAM_ACCESS`).
  * To prevent co-op griefing, minion pickup is strictly locked to the original owner who placed it (`security.allow-team-pickup: false`).
  * Linked minion storage chests are protected from being broken by team members (`settings.allow-team-break-storage: false`).
* **Island Limits**:
  * Placement caps can be assigned per player or island group using permissions: `minionx.limit.<number>` or `/minion limit set <player> <amount>`.

### 3. Custom Item Plugins (ItemsAdder, MMOItems, Oraxen, EcoItems)
MinionX bridges custom RPG items across drop tables, compression recipes, and hand equipment:

* **Item Identifier Formats**:
  * `itemsadder:<namespace>:<id>` or `itemsadder:<id>` (e.g. `itemsadder:ruby`, `itemsadder:custom_crops:tomato`)
  - `mmoitems:<TYPE>:<ID>` (e.g. `mmoitems:SWORD:FIRE_KATANA`)
  - `ecoitems:<id>` (e.g. `ecoitems:enchanted_iron`)
  - Vanilla materials (e.g. `DIAMOND`)
* **Direct In-Game Cloning**:
  * In the Creator Studios (`/minion create`), pick up any item onto your cursor and click Slot 12 (Drop) or Slot 24 (Held Tool).
  * MinionX copies all CustomModelData, display names, hex lore, and PersistentDataContainer tags directly without requiring manual YAML entry.
* **Super Compactor Support (`recipes.yml`)**:
  * Define custom compression recipes that turn raw custom items into compacted custom items.
  * Strict metadata isolation ensures vanilla compression recipes never accidentally consume items with custom names or CustomModelData.

### 4. Economy Integrations (Vault & RoyaleEconomy)
* Automated Shipping Hoppers sell overflow items when minion storage fills up.
* **Budget Hopper**: Sells excess items at 50% of base price.
* **Enchanted Hopper**: Sells excess items at 90% of base price.
* Base item prices are configured in `prices.yml`. Earned coins accumulate in the hopper slot and deposit directly into the player's balance via Vault upon collection.

### 5. RPG Skill Integrations (AuraSkills & EcoSkills)
* When players claim resources from a minion, skill experience is awarded directly to their RPG profile.
* Configurable in `config.yml` under `skill-rewards`:
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
MinionX checks protection rules before allowing players to place or access minions:
* **WorldGuard**: Checks build and block placement permissions.
* **GriefPrevention**: Checks claim ownership and trust level (`allowBuild` for placement, `allowContainers` for GUI access).
* **Towny**: Verifies resident plot build permissions via `TownyAPI`.
* **Lands**: Checks land area flags via `LandsIntegration` (`canBuild` and `canAccess`).

### 7. Bedrock Edition (GeyserMC & Floodgate)
* Automatically detects Bedrock players and adapts menu navigation with symmetrical, touch-friendly slot layouts for mobile and console players.

---

## 12. Complete Commands & Permissions Reference

| Command | Permission | Description |
|---|---|---|
| `/minion create [minion\|upgrade\|fuel\|skin\|storage]` | `minionx.admin` | Opens the designated in-game creator studio |
| `/minion tier [id]` | `minionx.admin` | Opens the Global or Per-Minion Tier Studio |
| `/minion give <player> <type> [tier]` | `minionx.admin` | Gives a minion head item (Tiers I–XII) |
| `/minion give <player> <fuel_id>` | `minionx.admin` | Gives a configured fuel item |
| `/minion give <player> <upgrade_id>` | `minionx.admin` | Gives a configured upgrade or hopper item |
| `/minion give <player> skin <skin_id>` | `minionx.admin` | Gives a cosmetic minion skin item |
| `/minion give <player> storage <storage_id>` | `minionx.admin` | Gives a minion storage expansion chest |
| `/minion limit <check\|add\|remove\|set>` | `minionx.admin` | Manages player minion placement limits |
| `/minion quarantine [on\|off\|status]` | `minionx.admin` | Controls emergency server-wide quarantine mode |
| `/minion list` | `minionx.use` | Lists player's placed minions and locations |
| `/minion killall` | `minionx.admin` | Purges all placed minions and holograms |
| `/minionx` | `minionx.admin` | Opens the interactive MinionX Operator Hub (Aliases: `/minion op`, `/minionsx`, `/mxop`) |
| `/minion claim` | `minionx.use` | Safely recovers verified escrow items and stored XP; blocks ambiguous operations |
| `/minion recover list` | `minionx.admin` | Lists all pending, quarantined, or ambiguous operations in the escrow ledger |
| `/minion recover resolve <opId> complete [reason]` | `minionx.admin` | Clears operation from escrow ledger with an audit entry |
| `/minion recover resolve <opId> deliver [reason]` | `minionx.admin` | Delivers verified escrow items and safe XP (strictly rejects ambiguous operations to prevent duplication) |
| `/minion recover resolve <opId> deliver-force-all [reason]` | `minionx.admin` | Force-delivers items from an ambiguous operation (withholding unresolved XP in ledger until audited) |
| `/minion recover resolve <opId> deliver-force-all-with-xp [reason]` | `minionx.admin` | Explicit administrative override to deliver both items and stored XP after manual log verification |
| `/minion reload` | `minionx.admin` | Hot-reloads all YAML configurations and registries |

### Permissions Matrix:
* `minionx.admin` — Full administrative control over all MinionX commands and studios.
* `minionx.use` — Standard player access to place, interact with, and view minion lists.
* `minionx.bypass.limit` — Bypasses maximum placed minions per player cap.
* `minionx.limit.<number>` — Sets custom max placed minions limit (e.g., `minionx.limit.10`).

---

## 13. Administrator FAQ & Troubleshooting

**Q: Can I cap a custom minion at Tier V without affecting other 12-tier minions?**  
A: Yes! Simply add `max-tier: 5` under that minion in `minions.yml`, or adjust Slot 33 inside the In-Game Minion Creator Studio (`/minion create`).

**Q: Do custom fuels persist across server restarts?**  
A: Yes. All fuels designed in the Fuel Creator Studio (`/minion create fuel`) are saved directly to `fuels.yml`.

**Q: Will Super Compactor accidentally destroy rare custom items?**  
A: No. MinionX features strict anti-swallowing isolation. Vanilla recipes will never consume items with custom display names or CustomModelData.

**Q: How does offline production work?**  
A: When a chunk unloads or the server restarts, minion calculations pause. Upon chunk reload or player interaction, MinionX computes elapsed downtime cycle-by-cycle across fuel boundaries, calculates exact item production, executes Super Compaction batches, and updates minion storage without ticking entities.

---

## 14. Developer API & Events Guide

MinionX provides a Java API and Bukkit event pipeline for third-party plugin integration.

### Maven Dependency:
Add `MinionX.jar` as a provided dependency in your build tool:

```xml
<dependency>
    <groupId>com.minionx</groupId>
    <artifactId>MinionX</artifactId>
    <version>1.0.1</version>
    <scope>provided</scope>
</dependency>
```

In your `plugin.yml`:
```yaml
depend: [MinionX]
# or softdepend: [MinionX]
```

### Static API Methods (`MinionXAPI`):
The `com.minionx.api.MinionXAPI` class provides static methods for querying and controlling minions:

```java
import com.minionx.api.MinionXAPI;
import com.minionx.core.Minion;

// Query a placed minion by world location
Minion minion = MinionXAPI.getMinionAt(location);

// Query all active minions owned by a player
List<Minion> playerMinions = MinionXAPI.getMinionsByOwner(player.getUniqueId());

// Query placed minion counts and limits
int placedCount = MinionXAPI.getPlacedCount(player.getUniqueId());
int limit = MinionXAPI.getEffectiveLimit(player);
int availableSlots = MinionXAPI.getAvailableSlots(player);

// Give a minion head item directly to a player
boolean success = MinionXAPI.giveMinion(player, "diamond", 11);

// Register custom definitions dynamically
MinionXAPI.registerCustomMinion(customMinionDef);
MinionXAPI.registerCustomFuel(customFuel);
MinionXAPI.registerCustomUpgrade(customUpgrade);
```

### Bukkit Event Pipeline (`com.minionx.api.event`):
MinionX fires standard Bukkit events that can be listened to or cancelled:

| Event | Cancellable | Description |
|---|---|---|
| `MinionPlaceEvent` | Yes | Fired when a player attempts to place a minion in the world. |
| `MinionPickupEvent` | Yes | Fired when a player attempts to pick up a placed minion. |
| `MinionHarvestEvent` | Yes | Fired when a minion harvests a block, crop, or mob entity. |
| `MinionUpgradeEvent` | Yes | Fired when a minion is upgraded to a higher tier. |
| `MinionStorageLinkEvent` | Yes | Fired when an external minion storage chest is placed and linked. |
| `MinionCollectionEvent` | No | Fired when a player collects resources from minion storage or linked chests. |
| `MinionFuelConsumeEvent` | No | Fired when temporary fuel burns or is consumed. |
| `MinionQuarantineEvent` | No | Fired when emergency quarantine mode is toggled on or off. |

### Example Event Listener:
```java
import com.minionx.api.event.MinionPlaceEvent;
import com.minionx.api.event.MinionHarvestEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CustomMinionListener implements Listener {

    @EventHandler
    public void onMinionPlace(MinionPlaceEvent event) {
        // Prevent placement in special worlds or regions
        if (event.getLocation().getWorld().getName().equalsIgnoreCase("spawn")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Minions cannot be placed in the spawn world!");
        }
    }

    @EventHandler
    public void onMinionHarvest(MinionHarvestEvent event) {
        // Custom logging or triggering custom rewards
        Minion minion = event.getMinion();
        // event.getHarvestedItems() contains the items rolled for this cycle
    }
}
```

---
*MinionX — Designed and maintained by AbhinavVanama.*
