# MinionX

MinionX is a high-performance Minecraft Java plugin providing an autonomous, universal minion engine for Paper, Purpur, and Pufferfish. It introduces autonomous, customizable minions featuring authentic visual animations, interactive chest interfaces, dynamic fuels, accessories, and command execution pipelines.

---
**Author:** AbhinavVanama  
**Current Version:** `v0.2.0` (Pre-Release)  
📖 **Complete Server Owner & Developer Manual:** See [WIKI.md](WIKI.md) for full gameplay guides, in-game creator studio tutorials, commands, modular configuration breakdown, and developer API documentation.

### 🌟 Key Highlights & Capabilities
- **Modular Architecture:** Cleanly organized into `config.yml`, `tiers.yml`, `minions.yml`, `fuels.yml`, `upgrades.yml`, `recipes.yml`, and `messages.yml`.
- **Dynamic Tier Studio & Per-Minion Max Tier Caps:** Define arbitrary tiers in-game (`/minion tier`) or configure individual `max-tier` limits per minion (e.g. capped at Tier V or Tier XII).
- **Custom Item Super Compactor 3000:** Compress custom RPG items (with CustomModelData, PDC keys, and display names) alongside vanilla items with strict isolation.
- **Multi-Drop Engine:** Minions can produce multiple distinct drops with configurable individual drop chances (e.g. String + Spider Eye).
- **In-Game Studios:** Full In-Game Minion Creator Studio, Skin Studio, and Tier Studio without needing manual YAML editing.
- **Developer API & Event Suite:** `MinionXAPI` with 8 custom Bukkit events (`MinionPlaceEvent`, `MinionHarvestEvent`, etc.) for seamless third-party plugin integration.

### 👑 Built-in Minion Heads & Tier XII
- **Custom Head Textures:** All 37 built-in minions wear authentic player heads across every tier (Tiers I–XII), bundled offline with zero external network lag.
- **Tier XII Progression:** Max tier progression extended to Tier XII (5.0s delay, 15 storage slots, 960 item capacity).
- **Tier Browser:** Visualized in `/minionsx` Minions Catalog with dedicated 12-slot tier inspection and instant delivery.

### 🎨 Cosmetic Minion Skins
- **51 Custom Skins:** Includes Santa, Snowman, Robot, Shark, Penguin, Pufferfish, Dragon, Sloth, Bee, Mummy, and 40+ more.
- **Dedicated GUI Slot 37:** Insert or retrieve minion skins directly from the Minion GUI to transform appearance and armor styling.
- **Operator Skins Catalog:** Full paginated browser in `/minionsx` (Slot 28) with 1-click delivery.
- **Command Support:** `/minion give <player> skin <skin_id>` with complete tab-completion.

## Supported Versions

- **Server Software:** Paper, Purpur, Pufferfish (1.20.4 – 1.21.x)
- **Java Runtime:** Java 21 or higher

---

## Architecture & Features

- **Autonomous Action Engine:** Two-action cycle (Place -> Delay -> Mine -> Delay) with support for external resource generators.
- **Interactive 54-Slot Interface:**
  - Real-time minion statistics and skin rendering
  - Active fuel slot with duration tracking
  - Dual upgrade and accessory slots
  - Tier-scaled storage matrix (1 to 15 slots)
  - Quick-collect, tier upgrade, and pickup controls
- **Tier Progression:** 12 default tiers with action delays scaling down to 5.0s and storage capacities scaling up to 960 items, with custom per-minion caps.
- **Config-Driven Extensibility:** Define custom minions, fuels, and recipes directly in modular YAML configs or via in-game studios.
- **Collection Command Dispatcher:**
  - Configurable console command execution upon resource collection (e.g., Skill EXP or collection tracking).
  - Built-in placeholders ```(%player%, %amount%, %minion_type%, %minion_tier%, %total_harvested%, etc.)``` with optional PlaceholderAPI hook.
  - Inline arithmetic evaluation (e.g., {amount * 2.5}, {amount * 10}).
- **Additive Speed Formulation:** Accurately stacks multiple speed multipliers using $T_{\text{effective}} = \frac{T_{\text{base}}}{1 + \sum \text{Buffs}}$.
- **Deterministic Offline Simulation:** Simulates accrued production during chunk unloads or server downtime upon player return.
- **Clean Persistence:** Full YAML / SQLite / MySQL state serialization with automatic entity cleanup on server shutdown.

---

## Compilation

Built with Maven 3.9+ and Java 21:

```bash
mvn clean package
```

The production artifact will be generated at `target/MinionX-0.2.0-SNAPSHOT.jar`.

---

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| `/minion create` | `minionx.admin` | Opens the In-Game Minion Creator Studio |
| `/minion tier` | `minionx.admin` | Opens the In-Game Dynamic Tier Studio |
| `/minion give <player> <type> [tier]` | `minionx.admin` | Gives a minion head item |
| `/minion give <player> <fuel_id>` | `minionx.admin` | Gives a configured fuel item |
| `/minion give <player> <upgrade_id>` | `minionx.admin` | Gives a configured upgrade/accessory item |
| `/minion give <player> skin <skin_id>` | `minionx.admin` | Gives a cosmetic minion skin |
| `/minion give <player> storage <storage_id>` | `minionx.admin` | Gives a minion storage chest |
| `/minion limit <check|add|remove|set>` | `minionx.admin` | Manages player placement limits |
| `/minion quarantine [on|off|status]` | `minionx.admin` | Emergency server-wide anti-dupe freeze |
| `/minion list` | `minionx.use` | Lists player-owned minions and coordinates |
| `/minion killall` | `minionx.admin` | Purges all placed minions and displays |
| `/minionsx` | `minionx.admin` | Opens the interactive MinionX Operator Panel GUI |
| `/minion reload` | `minionx.admin` | Hot-reloads configuration and registries |

*(Aliases for `/minion`: `/minions`, `/mx`, `/minionx`)*

---

## License

Commercial Proprietary License — Copyright (c) 2026 AbhinavVanama. All rights reserved. See [LICENSE](LICENSE) for terms.
