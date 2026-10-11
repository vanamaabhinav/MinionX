# MinionX

MinionX is a high-performance Minecraft Java plugin inspired by Hypixel SkyBlock. It introduces autonomous, customizable minions featuring authentic visual animations, interactive chest interfaces, dynamic fuels, accessories, and command execution pipelines.

---
Developed by Abhinav Vanama
### 🎭 Authentic Hypixel Minion Heads & Tier XII
- **Authentic Head Textures:** All 37 built-in minions wear exact, authentic Hypixel SkyBlock player heads across every tier (Tiers I–XII), bundled offline with zero external network lag.
- **Tier XII Progression:** Max tier progression extended to Tier XII (6.0s delay, 15 storage slots, 960 item capacity).
- **Tier Browser:** Visualized in `/minionx` Minions Catalog with dedicated 12-slot tier inspection and instant delivery.

### 🎨 Cosmetic Minion Skins
- **51 Authentic Hypixel Skins:** Includes Santa, Snowman, Robot, Shark, Penguin, Pufferfish, Dragon, Sloth, Bee, Mummy, and 40+ more.
- **Dedicated GUI Slot 37:** Insert or retrieve minion skins directly from the Minion GUI to transform appearance and armor styling.
- **Operator Skins Catalog:** Full paginated browser in `/minionx` (Slot 28) with 1-click delivery.
- **Command Support:** `/minion give <player> skin <skin_id>` with complete tab-completion.

## Supported Versions

- **Server Software:** Paper, Purpur, Pufferfish (1.20.4 – 1.21.x)
- **Java Runtime:** Java 21 or higher

---

## Architecture & Features

- **Autonomous Action Engine:** Authentic two-action cycle (Place -> Delay -> Mine -> Delay) with support for external resource generators.
- **Interactive 54-Slot Interface:**
  - Real-time minion statistics and skin rendering
  - Active fuel slot with duration tracking
  - Dual upgrade and accessory slots
  - Tier-scaled storage matrix (1 to 15 slots)
  - Quick-collect, tier upgrade, and pickup controls
- **Tier Progression:** 11 tiers with action delays scaling from 14.0s down to 7.0s and storage capacities scaling up to 960 items.
- **Config-Driven Extensibility:** Define custom fuels and accessories directly in config.yml without modifying Java code.
- **Collection Command Dispatcher:**
  - Configurable console command execution upon resource collection (e.g., Skill EXP or collection tracking).
  - Built-in placeholders (%player%, %amount%, %minion_type%, %minion_tier%, %total_harvested%, etc.) with optional PlaceholderAPI hook.
  - Inline arithmetic evaluation (e.g., {amount * 2.5}, {amount * 10}).
- **Additive Speed Formulation:** Accurately stacks multiple speed multipliers using $T_{\text{effective}} = \frac{T_{\text{base}}}{1 + \sum \text{Buffs}}$.
- **Deterministic Offline Simulation:** Simulates accrued production during chunk unloads or server downtime upon player return.
- **Clean Persistence:** Full YAML state serialization with automatic entity cleanup on server shutdown.

---

## Compilation

Built with Maven 3.9+ and Java 21:

```bash
mvn clean package
```

The shaded production artifact will be generated at target/SkyBlockMinions-1.0.0.jar.

---

## Commands & Permissions

| Command | Permission | Description |
|---|---|---|
| /minion give <player> <type> [tier] | skyblockminions.admin | Gives a minion head item (Tiers 1–11) |
| /minion give <player> <fuel_id> | skyblockminions.admin | Gives a configured fuel item |
| /minion give <player> <upgrade_id> | skyblockminions.admin | Gives a configured upgrade/accessory item |
| /minion list | Default | Lists player-owned minions and coordinates |
| /minionsx / /minionx | skyblockminions.admin | Opens the interactive MinionX Operator Panel GUI |
| /minion reload | skyblockminions.admin | Hot-reloads configuration and registries |

---

## License

MIT License.
