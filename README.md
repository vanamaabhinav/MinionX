<div align="center">

# MinionX
### Autonomous Minions Plugin for Minecraft Paper / Purpur 1.20.4 – 1.21+
**An Authentic Hypixel-Inspired Minions Engine**

[![Paper](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Pufferfish-00a8ff?style=for-the-badge&logo=curseforge)](https://papermc.io)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.4%20%E2%80%93%201.21+-brightgreen?style=for-the-badge&logo=minecraft)](https://www.minecraft.net)
[![Java Version](https://img.shields.io/badge/Java-21%20LTS-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![Performance](https://img.shields.io/badge/Scheduling-Priority%20Queue-success?style=for-the-badge&logo=speedtest)](https://github.com/vanamaabhinav/MinionX)
[![Status](https://img.shields.io/badge/Release-v1.0.1-blueviolet?style=for-the-badge)](https://github.com/vanamaabhinav/MinionX)

[Explore Full Wiki](docs/WIKI.md) • [Administrator Manual](docs/DOCUMENTATION.md) • [Report Issue](https://github.com/vanamaabhinav/MinionX/issues)

</div>

---

## About MinionX

Whether you are running a **SkyBlock network**, **Prison server**, **Survival SMP**, or **RPG realm**, automated minions provide passive resource generation for your players. 

Built on **Java 21** and designed specifically for **Paper, Purpur, and Pufferfish (1.20.4 – 1.21+)**, MinionX combines interactive graphical dashboards, in-game creator menus, offline catch-up calculations, and support for custom items and economy integrations.

---

## Key Features

| Feature | Description |
|---|---|
| **37+ Built-in Minions** | Mining, Farming, Foraging, and Combat archetypes with working animations. |
| **Automated Work Cycles** | Minions place, grow, or spawn targets within their working area, then harvest resources into storage. |
| **Per-Tier Visual Styles** | Assign unique custom player heads (Base64 texture or player name), armor, and tools to each tier. |
| **6 In-Game Visual Studios** | Create and edit Minions, Upgrades/Hoppers, Fuels, Tiers, Skins, and Storages visually in-game. |
| **Per-Minion Tier Customization** | Configure independent Max Tiers, upgrade costs, speeds, and capacities for each individual minion. |
| **Offline Production Simulation** | Calculates offline downtime production cycle-by-cycle without loading or ticking entities. |
| **Custom Item Framework Support** | Works with **ItemsAdder**, **MMOItems**, **Oraxen**, and custom PDC/CustomModelData items. |
| **Item Compaction (Super Compactor)** | Auto-condenses raw items into enchanted tiers with strict metadata protection. |
| **Anti-Theft Protection & Quarantine** | Co-op theft protection on minions/chests, transaction locks, and a one-click `/minion quarantine` freeze switch. |
| **51 Cosmetic Minion Skins** | Customize minion appearance with cosmetic skins, RGB armor dyes, and held accessories. |
| **External Storage Expansions** | Link Small (+3), Medium (+9), Large (+15), or custom chests up to 27 slots. |
| **Automated Hoppers & Economy** | Budget and Enchanted Hoppers sell overflow directly to **Vault** economy balances. |
| **Geyser & Bedrock Compatibility** | Symmetrical, touch-friendly UI layouts optimized for mobile and console players. |

---

## Feature Details

### 1. In-Game Visual Creator Studios (No YAML Required)
MinionX allows server owners and administrators to design, customize, and deploy their minion ecosystem through interactive graphical interfaces:

- **Minion Creator Studio (`/minion create`)**: Configure archetypes, display names, target blocks, mob types, weapons, tools, and drops. Pick up any item onto your cursor to equip it in Slot 24, or click with an empty cursor to cycle through common tools.
- **Upgrade & Hopper Creator Studio (`/minion create upgrade`)**: Configure automated shipping hoppers, sell ratios, speed catalysts, auto-smelters, and compactors.
- **Fuel Creator Studio (`/minion create fuel`)**: Visual speed slider, temporary vs permanent burn toggle, duration presets (`30m` to `7d`), and live test item dispenser.
- **Tier Studio (`/minion tier` & `/minion tier <minion_id>`)**: Live action delay editor, storage capacity matrix, upgrade cost editor, and arbitrary Roman numerals (Tier I to XII+).
- **Skin Creator Studio (`/minion create skin`)**: Design cosmetic visual skins using Base64 player head textures, RGB armor dyes, and held items.
- **Storage Creator Studio (`/minion create storage`)**: Visually design chest models from 3 to 27 expansion slots.

```
       +-------------------------------------------------------------+
       |                  MinionX Creator Studio                     |
       +-------------------------------------------------------------+
       |  [Archetype]     [Target Block]   [Drop Table]   [Max Tier] |
       |     FARMING          WHEAT           WHEAT+SEED     Tier V  |
       +-------------------------------------------------------------+
       |  [Hand Tool]     [Armor Tint]     [Skull Texture]  [SAVE]   |
       |  DIAMOND_HOE      #FF9900 RGB         Base64        CONFIRM |
       +-------------------------------------------------------------+
```

---

### 2. Flexible Per-Minion Tiers & Max Tier Limits
Every server has unique balance requirements. MinionX allows tuning tier progression per minion:

- **Custom Max Tier Caps**: Cap specific minions at Tier V, Tier X, or Tier XII. When a minion hits its cap, the GUI upgrade button turns into Bedrock with an authentic locked banner.
- **Per-Minion Tier Overrides**: Configure custom speeds, storage slots, and upgrade costs per minion.
- **Automatic Fallback**: Any tier not explicitly customized automatically inherits global defaults from `tiers.yml`.
- **In-Game or Config Driven**: Configure in `minions.yml` or use the interactive `+` / `-` buttons in the Per-Minion Tier Studio (`/minion tier <minion_id>`).

---

### 3. Super Compactor 3000 & Custom Item Support
MinionX includes a compression engine that bridges vanilla items and custom RPG items.

- **Unified Custom Item Schema**: Both minion drops and Super Compactor recipes share the identical schema:
  - `material` / `item` (Vanilla or hooks: `itemsadder:ruby`, `mmoitems:SWORD_KATANA`)
  - `amount` / `input-amount`
  - `chance` (0.0 to 1.0 drop probability)
  - `display-name` with HEX / MiniMessage color codes
  - `custom-model-data` for resource pack integration
  - `pdc-key` & `pdc-value` for persistent data tags
  - `glow` / `enchanted` for subtle enchant glints
- **Metadata Protection**: Vanilla compression recipes strictly reject items with CustomModelData, custom names, or PDC tags. Players will not lose expensive custom items to accidental auto-compression.

---

### 4. Performance & Scalability Architecture
MinionX was built from day one to handle high minion counts on active servers:

- **Non-Ticking Display Entities**: Minion models use Paper-native `setCanTick(false)` and `setMarker(true)` armor stands. The vanilla entity tick loop completely skips minion models.
- **Modern Billboard Text Displays**: Nametags and floating holograms use Paper `TextDisplay` entities with `Billboard.CENTER`. They rotate client-side without sending continuous server packets or causing flicker.
- **Bounded Priority Queue Scheduling**: Minion AI actions are scheduled via a bounded generational priority queue processing up to a capped action and time budget per tick with latency shedding under heavy server load.
- **Offline Downtime Simulation**: When chunks unload or players disconnect, minions cease all ticking. When chunks reload, MinionX computes elapsed downtime cycle-by-cycle across fuel boundaries without ticking entities.
- **Durable Persistence & Single-Server Isolation**: Flat-file storage writes atomically to temporary files before moving, while SQLite and MySQL database backends provide persistent relational storage. Each server instance requires an isolated database schema (cross-server proxy synchronization is not supported).

---

### 5. Anti-Theft Security, Durable Escrow & Emergency Quarantine
Economy exploits and griefing are prevented through layered protections:

- **Co-op Anti-Theft Protection**: Co-op island members can open minion menus and collect resources, but cannot pick up other players' minions or break linked storage chests unless permitted by server settings (`allow-team-break-storage`).
- **Synchronized Transaction Locks**: Inventory collections, upgrades, and hopper transfers use internal lock guards to prevent click-spamming and transaction race conditions.
- **Durable Escrow Journaling**: Minion pickups stage items and XP in a durable pending operations journal (`minion_pending_operations`) before deleting world entities, with `/minion claim` and `/minion recover` reconciliation across crashes.
- **Emergency Quarantine Switch (`/minion quarantine on`)**: Suspect a glitch or exploit? Toggle Quarantine Mode to immediately freeze all placed minion actions and GUI interactions network-wide without needing to kick players or take the server offline.
- **Sanity Audits**: Automatic inventory validation prevents negative item counts or invalid slot placements.

---

### 6. Minion Work Lifecycle & Mechanics
All minions operate on a consistent, predictable work cycle tailored to their archetype:

- **Two-Phase Action Cycle**: Minions alternate between a generation phase and a harvest phase based on their configured action delay.
  - *Mining & Foraging*: Places target ores, stones, or tree saplings, then breaks or fells them when ready.
  - *Farming*: Tills land, plants crops, and harvests fully grown crops while replanting automatically.
  - *Combat & Animals*: Spawns target entities within the working area, then slays them to harvest drops.
- **Working Radius & Area Boundaries**: Minions operate within a default 5×5 area centered on their placement block. The working area can be expanded to 7×7 or 9×9 using Minion Expander upgrades.
- **Target Tracking & Status Displays**: Minions scan their defined area before harvesting. If targets wander out of range or are unavailable, the minion pauses and displays a status indicating it is waiting for targets.
- **Entity Handling & Daylight Protection**: Mobs spawned by combat minions are protected from daylight fire through event interception, allowing them to remain active during the day. When players slay minion-spawned mobs or harvest minion blocks manually, standard vanilla drops and experience are granted. Picking up a minion safely leaves existing placed blocks or mobs in the world.
- **Storage & Overflow**: Harvested items accumulate in internal slots or adjacent linked chests. If capacity is reached, minions enter a Full state unless equipped with an automated shipping hopper to sell overflow.

---

### 7. 51 Cosmetic Minion Skins & Ambient NPC Life
Minions bring life and personality to player islands:

- **51 Bundled Skins**: Santa, Snowman, Robot, Shark, Penguin, Pufferfish, Dragon, Sloth, Bee, Mummy, Cyber Knight, and 40+ more.
- **Dedicated GUI Slot 37**: Players can hot-swap skins directly from the minion interface.
- **Floating Dialogue Bubbles**: Minions periodically chat with their owners using floating speech bubbles when clicked or upon reaching harvest milestones.
- **Sound & Particle FX**: Authentic block breaking, crop reaping, mob slaying sounds, and leveling fanfare.

---

## Operator Hub (`/minionx`)

The **MinionX Operator Panel** is the administrative command center:

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
- **1-Click Minion Delivery**: Left-click to inspect, right-click to receive Tier I, or Shift-click to receive max Tier XII.
- **Complete Upgrades Vault**: Grab Auto-Smelters, Super Compactors, Diamond Spreadings, Flycatchers, and Hoppers.
- **Emergency Controls**: Instantly toggle Quarantine Mode with visual status feedback.

---

## Supported Integrations

MinionX integrates out-of-the-box with leading Minecraft plugins:

| Category | Supported Plugins |
|---|---|
| **Land & Region Protection** | WorldGuard, GriefPrevention, Towny, Lands, BentoBox, SuperiorSkyblock |
| **Economy** | Vault (Supported by EssentialsX, CMI, etc.), RoyaleEconomy |
| **Custom Mobs & RPG** | MythicMobs (Custom combat spawns & drops) |
| **Custom Items & Blocks** | ItemsAdder, MMOItems, Oraxen |
| **Skills & Collections** | AuraSkills, EcoSkills, Custom Console Dispatcher with Math Expressions |
| **Bedrock Compatibility** | GeyserMC, Floodgate |
| **Metrics & Analytics** | Anonymous bStats integration |

---

## Quick Start Guide

1. **Install**: Drop `MinionX-1.0.1.jar` into your server's `plugins/` folder.
2. **Start**: Start your server running Paper / Purpur 1.20.4 or newer (Java 21 required).
3. **Deploy**:
   ```bash
   /minion give <player> diamond 11
   ```
4. **Place**: Place the minion head on any block. Right-click to open its 54-slot interactive dashboard.
5. **Customize**: Type `/minionx` or `/minion create` to explore in-game studios.

---

## Commands & Permissions Reference

| Command | Permission | Description |
|---|---|---|
| `/minion create` | `minionx.admin` | Opens the In-Game Minion Creator Studio |
| `/minion create upgrade` | `minionx.admin` | Opens the In-Game Upgrade & Hopper Creator Studio |
| `/minion create fuel` | `minionx.admin` | Opens the In-Game Fuel Creator Studio |
| `/minion create skin` | `minionx.admin` | Opens the In-Game Skin Creator Studio |
| `/minion create storage` | `minionx.admin` | Opens the In-Game Storage Creator Studio |
| `/minion tier` | `minionx.admin` | Opens the Global Tier Studio |
| `/minion tier <minion_id>` | `minionx.admin` | Opens the Per-Minion Tier Studio |
| `/minion give <player> <type> [tier]` | `minionx.admin` | Gives a minion head item |
| `/minion give <player> <fuel_id>` | `minionx.admin` | Gives a configured fuel item |
| `/minion give <player> <upgrade_id>` | `minionx.admin` | Gives an upgrade or accessory |
| `/minion give <player> skin <skin_id>` | `minionx.admin` | Gives a cosmetic minion skin token |
| `/minion give <player> storage <storage_id>` | `minionx.admin` | Gives a minion storage chest |
| `/minion limit <check\|add\|remove\|set>` | `minionx.admin` | Manages player minion placement limits |
| `/minion quarantine [on\|off\|status]` | `minionx.admin` | Emergency network-wide anti-dupe freeze |
| `/minion list` | `minionx.use` | View your placed minions and coordinates |
| `/minion killall` | `minionx.admin` | Purge placed minions and remove display entities |
| `/minionx` | `minionx.admin` | Opens the all-in-one MinionX Operator Panel (Aliases: `/minion op`, `/minionsx`, `/mxop`) |
| `/minion reload` | `minionx.admin` | Instant zero-downtime configuration hot-reload |

---

## License & Commercial Support

**Commercial Proprietary Software**  
Copyright © 2026 **AbhinavVanama**. All rights reserved.  
Unauthorized redistribution, decompilation, or reselling of this software is strictly prohibited. For licensing inquiries, commercial deployment, or enterprise support, please refer to the project repository.
