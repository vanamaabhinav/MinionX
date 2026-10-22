# MinionX Administrator Manual & Server Owner Documentation

> **MinionX** - Autonomous Minions Plugin for **Paper / Purpur / Pufferfish 1.20.4 – 1.21+**  
> **Author:** AbhinavVanama  
> **Plugin Version:** `v1.0.0` (Release)  
> **Source Repository:** [https://github.com/vanamaabhinav/MinionX](https://github.com/vanamaabhinav/MinionX)

---

## Table of Contents
1. [System Requirements & Installation](#1-system-requirements--installation)
2. [Quick Start Deployment](#2-quick-start-deployment)
3. [Modular Configuration Architecture](#3-modular-configuration-architecture)
4. [In-Game Creator Studios](#4-in-game-creator-studios)
   * [Minion Creator Studio (`/minion create`)](#minion-creator-studio)
   * [Upgrade & Hopper Creator Studio (`/minion create upgrade`)](#upgrade--hopper-creator-studio)
   * [Fuel Creator Studio (`/minion create fuel`)](#fuel-creator-studio)
   * [Tier Creator & Editor Studio (`/minion tier`)](#tier-creator--editor-studio)
   * [Skin Creator Studio (`/minion create skin`)](#skin-creator-studio)
   * [Storage Creator Studio (`/minion create storage`)](#storage-creator-studio)
5. [Per-Minion Max Tier Caps](#5-per-minion-max-tier-caps)
6. [Super Compactor 3000 & Custom Items Support](#6-super-compactor-3000--custom-items-support)
7. [Multi-Drop Engine with Chance Percentages](#7-multi-drop-engine-with-chance-percentages)
8. [Operator Control Center (`/minionx`)](#8-operator-control-center-minionx)
9. [Anti-Dupe Security & Emergency Quarantine Mode](#9-anti-dupe-security--emergency-quarantine-mode)
10. [Plugin Integrations & Protection Hooks](#10-plugin-integrations--protection-hooks)
11. [Complete Commands & Permissions Reference](#11-complete-commands--permissions-reference)
12. [Administrator FAQ & Troubleshooting](#12-administrator-faq--troubleshooting)

---

## 1. System Requirements & Installation

MinionX is engineered for production Minecraft servers with bounded-budget tick scheduling, non-ticking display entities, and deterministic offline downtime simulation.

### Requirements:
* **Server Software:** Paper, Purpur, or Pufferfish (1.20.4 – 1.21.x). *(Spigot and CraftBukkit are not supported due to modern display entities and asynchronous tick requirements)*.
* **Java Version:** Java 21 LTS or newer.
* **Network:** Optional connection for asynchronous GitHub update checks and anonymous bStats metrics.

### Step-by-Step Installation:
1. Download the latest `MinionX-1.0.0.jar` build.
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
2. The minion spawns with authentic animations, billboard text display holograms, and commences its two-phase cycle:
   * **Phase 1 (Place):** Places the target ore, crop, sapling, or spawns the target mob.
   * **Phase 2 (Harvest):** Breaks the block, fells the tree, reaps the mature crop, or slays the mob.
3. Right-click the minion at any time to open its 54-slot interactive control GUI.

---

## 3. Modular Configuration Architecture

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

## 4. In-Game Creator Studios

MinionX features full-fledged in-game visual GUIs for designing, customizing, and deploying plugin content without touching YAML files.

### Minion Creator Studio
* **Command:** `/minion create`
* **Features:**
  * Define Minion ID and formatted display name.
  * Select archetype: `MINER`, `FORAGING`, `FARMING`, or `COMBAT`.
  * Set primary block/ore to place and harvest.
  * **Slot 33 (Max Tier Cap):** Adjust individual maximum tier limit (1 to max tier).
  * Clone exact drop items with custom metadata, lore, or CustomModelData directly from player cursor.
  * Integrate custom MythicMobs internal IDs for combat minions.
  * Choose hand tools, armor styles, and head textures.

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

## 5. Per-Minion Tier Customization (Max Tier, Speed, Capacity & Cost)

Different minions can have different maximum tier caps, individual speeds, storage capacities, and upgrade costs per tier.
* Standard Ore Minions: Progress up to **Tier XII** using global defaults.
* Specialized Resource Minions: Can be capped at **Tier V** or **Tier X**.
* Each tier for a specific minion can override `action-delay`, `storage-slots`, and `upgrade-cost`. Any tier not explicitly overridden automatically falls back to global settings in `tiers.yml`.

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

## 6. Super Compactor 3000 & Unified Custom Items Support

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

## 7. Multi-Drop Engine with Chance Percentages

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

## 8. Operator Control Center (`/minionx`)

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

## 9. Anti-Dupe Security, Escrow Durability & Crash Safety

MinionX implements robust protection against duplication, inventory desync, and crash exploits:

### Security & Durability Highlights:
* **Thread-Safe Data Operations:** All inventory accesses and modifications are synchronized and backed by thread-safe data structures.
* **Atomic Disk Serialization:** States are written to temporary files and atomically moved to destination paths, guaranteeing zero corrupted or truncated files if a server crashes.
* **Non-Duplicable Pickups & Durable Escrow:**
  * Minions are unregistered before dropping.
  * Pickups are staged in durable operation records (`minion_pending_operations`) embedding stored XP (`XP:<xp>;;;PAYLOAD:::...`) before deletion.
* **Crash-Safety Boundary & Zero-Leftover XP Invariant:**
  * Both `/minion claim` and `/minion recover` write and positively confirm `DELIVERY_ATTEMPTED` on disk before modifying player inventory or granting XP. If a server stops mid-delivery, reboot reconciliation marks it `UNKNOWN` / `DELIVERY_ATTEMPTED` and quarantines the minion.
  * Stored XP is **only** awarded when inventory delivery has **zero leftovers**. If inventory fills up, unclaimed XP is preserved with remaining leftovers.
  * If persisting leftovers fails in `/minion claim` or `/minion recover`, the operation is fenced to `AMBIGUOUS_PARTIAL_DELIVERY` with a canonical single-XP emergency audit ledger to prevent duplicate replay.
  * Ordinary recovery (`/minion recover resolve <opId> deliver`) strictly rejects ambiguous operations (`DELIVERY_ATTEMPTED`, `UNKNOWN`, `AMBIGUOUS_PARTIAL_DELIVERY`, `COMPLETION_UNCONFIRMED`).
  * If items are delivered under `deliver-force-all` while XP remains unresolved, the XP-only ledger update is verified durably, retaining pending XP until explicitly audited (`deliver-force-all-with-xp` or `complete`), and fencing on persistence failure to prevent stale item replay.
  * **Ambiguous Leftover State Preservation & Dual-Layer `XP_AMBIGUOUS` Defense:**
    * When an administrator resolves an ambiguous operation using `deliver-force-all` and player inventory fills up before all items fit, leftover state selection is strictly decoupled from XP presence: any operation with ambiguous delivery history preserves `DELIVERY_ATTEMPTED` (never downgraded to `QUARANTINED_LEFTOVERS`), even if the operation has no XP or XP was already delivered.
    * Unresolved XP is encoded with canonical prefix `XP_AMBIGUOUS:<xp>;;;PAYLOAD:::`. Both `/minion claim` and ordinary recovery (`deliver` / `deliver-force-all`) strictly withhold this XP—even if the stored state is nominally safe (`QUARANTINED_LEFTOVERS`)—ensuring automatic claims can never award ambiguous XP. Only the explicit administrative override `/minion recover resolve <opId> deliver-force-all-with-xp` can grant it.
    * When items are claimed under `/minion claim` while XP remains ambiguous, updating the durable ledger to the XP-only payload is strictly verified. If persistence fails or is unconfirmed, the operation is immediately fenced to `AMBIGUOUS_PARTIAL_DELIVERY` with empty leftovers (`LEFTOVER:::`) to prevent stale item replay.
    * **Malformed XP Payload Retention & Quarantine Invariant:** If an operation's XP amount in `XP_AMBIGUOUS:`, `XP:`, `XP_GRANTED:`, or `XP_DELIVERED:` is malformed (e.g. non-numeric token or integer overflow), parsing failure is explicitly detected (`xpParseFailed = true`). Parsing failure independently requires operation retention (`xpRemainsUnresolved = xpParseFailed || ...`), ensuring malformed records are NEVER deleted even if marked as already delivered (`XP_GRANTED:` / `XP_DELIVERED:`). The raw token is preserved (`XP_AMBIGUOUS:<rawToken>;;;PAYLOAD:::`) under `DELIVERY_ATTEMPTED` in the escrow ledger for administrative audit.
    * **Missing Delimiter & Negative XP Detection:** Recognized XP prefixes (`XP_DELIVERED:`, `XP_GRANTED:`, `XP_AMBIGUOUS:`, `XP_PENDING:`, `XP:`) are detected before delimiter validation. If the `;;;PAYLOAD:::` delimiter is absent or invalid, or if the parsed XP amount is negative (`parsed < 0`), parsing is rejected (`xpParseFailed = true`, `xpToGrant = 0`), automatic XP grants are prohibited, and the record is held for manual audit.
    * **Emergency Fallback Write Verification:** Both primary ledger persistence and emergency fallback writes (`AMBIGUOUS_PARTIAL_DELIVERY`) strictly inspect boolean return values. If an emergency fallback write fails, the operation remains locked in the last confirmed ambiguous fence state, automatic item/XP claims remain blocked, and an alert is logged to warn administrators of an unconfirmed ledger update.
    * **Item Delimiter Collision Escaping:** All serialized YAML item payloads are escaped via `MinionPayloadUtil.escapeItemPayload`, percent-encoding `%` and reserved delimiters (`;;;ITEM;;;`, `;;;PAYLOAD:::`, `;;;LEFTOVER:::`). Item stacks with arbitrary display names, lores, or metadata can never corrupt ledger delimiter splitting upon deserialization.
    * **Automated Shipping Currency Overflow Protection:** In `Minion.java`, incoming coin values are checked with `Double.isFinite(coins) && coins >= 0.0001`. In-memory micro-coin addition uses `Math.addExact(this.shippingCoinsMicros, addMicros)` and catches `ArithmeticException`, safely rejecting overflowing credits and logging an audit warning without mutating the balance or manufacturing `Long.MAX_VALUE`.
* **Unresolved Payout Quarantine Fencing:**
  * Payouts interrupted during Vault calls (`ATTEMPTED` or `PAYOUT_AMBIGUOUS`) are quarantined on startup. Automatic Vault retries and rollbacks are prohibited.
* **Emergency Quarantine Mode (`/minion quarantine on`):**
  * Immediately freezes all minion item collections, breaking/pickups, and placements across the entire server.
  * Ticking continues or freezes depending on `settings.quarantine-freeze-ticks`.
  * Administrators with `minionx.admin` bypass quarantine restrictions for live troubleshooting.

---

## 10. Plugin Integrations & Protection Hooks

MinionX natively integrates with the Minecraft server ecosystem:

* **Land Claims & Island Protection:**
  * WorldGuard, GriefPrevention, Towny, Lands.
  * SuperiorSkyblock2, BentoBox (BSkyBlock, AcidIsland).
  * Automatically respects build/break permissions and island member boundaries.
* **Economy & Hoppers:**
  * Vault & RoyaleEconomy integration for Budget Hoppers (50% sell price) and Enchanted Hoppers (90% sell price).
* **Skills Progression:**
  * AuraSkills & EcoSkills integration for direct skill experience awards.
* **Collections Systems:**
  * Native `CollectionsHook` interfacing with EcoCollections, SuperiorSkyblock2 Collections, and custom developer providers.
* **Bedrock Edition (Geyser & Floodgate):**
  * Auto-detects Bedrock players and optimizes menu navigation for mobile touch controls.

---

## 11. Complete Commands & Permissions Reference

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

## 12. Administrator FAQ & Troubleshooting

**Q: Can I cap a custom minion at Tier V without affecting other 12-tier minions?**  
A: Yes! Simply add `max-tier: 5` under that minion in `minions.yml`, or adjust Slot 33 inside the In-Game Minion Creator Studio (`/minion create`).

**Q: Do custom fuels persist across server restarts?**  
A: Yes. All fuels designed in the Fuel Creator Studio (`/minion create fuel`) are saved directly to `fuels.yml`.

**Q: Will Super Compactor accidentally destroy rare custom items?**  
A: No. MinionX features strict anti-swallowing isolation. Vanilla recipes will never consume items with custom display names or CustomModelData.

**Q: How does offline production work?**  
A: When a chunk unloads or the server restarts, minion calculations pause. Upon chunk reload or player interaction, MinionX computes elapsed downtime cycle-by-cycle across fuel boundaries, calculates exact item production, executes Super Compaction batches, and updates minion storage without ticking entities.

---
*MinionX — Designed and maintained by AbhinavVanama.*
