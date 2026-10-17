<div align="center">

# ⚡ MinionX ⚡
### Enterprise Autonomous Minions Engine for Minecraft Paper / Purpur 1.20.4 – 1.21+
**The Most Powerful, Feature-Packed & Zero-Lag Hypixel-Inspired Minions Plugin Ever Engineered.**

[![Paper](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Pufferfish-00a8ff?style=for-the-badge&logo=curseforge)](https://papermc.io)
[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.4%20%E2%80%93%201.21+-brightgreen?style=for-the-badge&logo=minecraft)](https://www.minecraft.net)
[![Java Version](https://img.shields.io/badge/Java-21%20LTS-orange?style=for-the-badge&logo=openjdk)](https://adoptium.net)
[![Performance](https://img.shields.io/badge/TPS-20.0%20Guaranteed-success?style=for-the-badge&logo=speedtest)](https://github.com/vanamaabhinav/MinionX)
[![Status](https://img.shields.io/badge/Release-v0.2.0--Pre--Release-blueviolet?style=for-the-badge)](https://github.com/vanamaabhinav/MinionX)

[Explore Full Wiki](https://av-studios-1.gitbook.io/av-studios-minionx-wiki/) • [Administrator Manual](DOCUMENTATION.md) • [Report Issue](https://github.com/vanamaabhinav/MinionX/issues)

</div>

---

## 💎 Why MinionX?

Whether you are hosting a **SkyBlock network**, **Prison empire**, **Survival SMP**, or **RPG realm**, automated minions are the backbone of player retention and long-term economy. 

Most minion plugins suffer from **crippling server lag**, **duplication exploits**, **clunky YAML-only configs**, and **inflexible hardcoded tiers**.

**MinionX solves all of that.** Engineered from the ground up on modern **Java 21** and optimized specifically for **Paper, Purpur, and Pufferfish**, MinionX delivers an authentic Hypixel-grade minion experience with **unmatched customization, modern billboard display entities, 5 in-game visual creator studios, and an uncompromising 20 TPS performance guarantee.**

---

## 🌟 Flagship Features at a Glance

| Feature | Description |
|---|---|
| 👑 **37+ Built-in Minions** | Mining, Farming, Foraging, and Combat archetypes with authentic animations. |
| 🗿 **Bundled Offline Head Textures** | Authentic player heads for all 37 minions across Tiers I–XII bundled locally (Zero Mojang API rate limits). |
| 🎛️ **6 In-Game Visual Studios** | Create and edit Minions, Upgrades/Hoppers, Fuels, Tiers, Skins, and Storages visually in-game — **Zero YAML required!** |
| 📈 **Per-Minion Tier Studio** | Configure independent Max Tiers, upgrade costs, speeds, and capacities for each individual minion. |
| ⚡ **Deterministic O(1) Offline Math** | Calculates 30 days of offline production instantly without loading or ticking entities. |
| 🧰 **Unified Custom RPG Items** | Seamlessly works with **ItemsAdder**, **MMOItems**, **Oraxen**, and custom PDC/CustomModelData items. |
| 🔄 **Super Compactor 3000** | Auto-condenses raw items into enchanted tiers with strict anti-swallowing isolation. |
| 🛡️ **Anti-Dupe & Emergency Quarantine** | Thread-safe transactions and a one-click `/minion quarantine` freeze switch. |
| 🎭 **51 Collectible Minion Skins** | Transform minion appearance with cosmetic skins, RGB armor dyes, and held accessories. |
| 📦 **Symmetrical Storage Expansions** | Link Small (+3), Medium (+9), Large (+15), or custom chests up to 27 slots. |
| 💰 **Automated Hoppers & Economy** | Budget and Enchanted Hoppers sell overflow directly to **Vault** economy balances. |
| 🌐 **100% Geyser / Bedrock Friendly** | Symmetrical, touch-friendly UI layouts optimized for mobile and console players. |

---

## 🚀 Deep Dive: Standout Features

### 1. 🎛️ Six In-Game Visual Creator Studios (No YAML Required)
Stop wasting hours debugging indentation in YAML files! MinionX allows server owners and administrators to design, customize, and deploy their entire minion ecosystem in real-time through intuitive graphical interfaces:

- **Minion Creator Studio (`/minion create`)**: Configure archetype, display names, target blocks, mob types, weapons, tools, and drops.
- **Upgrade & Hopper Creator Studio (`/minion create upgrade`)**: Live configuration for automated shipping hoppers, sell ratios, speed catalysts, auto-smelters, and compactors.
- **Fuel Creator Studio (`/minion create fuel`)**: Visual speed slider (5%–10% steps), temporary vs permanent burn toggle, duration presets (`30m` to `7d`), and live test item dispenser.
- **Tier Studio (`/minion tier` & `/minion tier <minion_id>`)**: Live action delay slider, storage capacity matrix, upgrade cost editor, and arbitrary Roman numerals (Tier I to XII+).
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

### 2. 📈 Flexible Per-Minion Tiers & Max Tier Limits
Every server has unique balance requirements. Why should a combat minion have the exact same tier scaling as a cobblestone minion?

- **Custom Max Tier Caps**: Cap specific minions at Tier V, Tier X, or Tier XII. When a minion hits its cap, the GUI upgrade button turns into Bedrock with an authentic locked banner.
- **Per-Minion Tier Overrides**: Configure custom speeds, storage slots, and upgrade costs per minion.
- **Automatic Fallback**: Any tier not explicitly customized automatically inherits global defaults from `tiers.yml`.
- **In-Game or Config Driven**: Configure in `minions.yml` or use the interactive `+` / `-` buttons in the Per-Minion Tier Studio (`/minion tier <minion_id>`).

---

### 3. 🧰 Super Compactor 3000 & Unified Custom Items Support
MinionX features an enterprise-grade compression engine that seamlessly bridges vanilla items and custom RPG items.

- **Unified Custom Item Schema**: Both minion drops and Super Compactor recipes share the identical schema:
  - `material` / `item` (Vanilla or hooks: `itemsadder:ruby`, `mmoitems:SWORD_KATANA`)
  - `amount` / `input-amount`
  - `chance` (0.0 to 1.0 drop probability)
  - `display-name` with HEX / MiniMessage color codes
  - `custom-model-data` for resource pack integration
  - `pdc-key` & `pdc-value` for persistent data tags
  - `glow` / `enchanted` for subtle enchant glints
- **Anti-Swallowing Strict Isolation**: Vanilla compression recipes strictly reject items with CustomModelData, custom names, or PDC tags. Players will never lose their expensive custom RPG items to accidental auto-compression!

---

### 4. ⚡ 20 TPS Guaranteed: Engineered for Massive Networks
MinionX was built from day one to handle thousands of active minions on high-population servers:

- **Zero Entity Tick Overhead**: Armor stands are placed with Paper-native `setCanTick(false)` and `setMarker(true)`. The Minecraft entity tick loop completely skips minion models!
- **Modern Billboard Text Displays**: Nametags and floating holograms use modern Paper `TextDisplay` entities with `Billboard.CENTER`. They rotate client-side without sending continuous server packets or causing flicker.
- **Deterministic O(1) Offline Progress Math**: When chunks unload or players disconnect, minions cease all ticking. When chunks reload, MinionX calculates elapsed downtime using deterministic math and fills inventories instantly.
- **Thread-Safe Atomic Saves**: All data is written asynchronously to atomic temporary files before replacing save files, preventing corrupted states during sudden power outages or crashes.

---

### 5. 🛡️ Enterprise Anti-Dupe Security & Emergency Quarantine
Economy exploits can ruin a server in minutes. MinionX provides unmatched security guarantees:

- **Synchronized Transaction Locks**: Inventory collections, upgrades, and hopper transfers use internal lock guards to prevent click-spamming and packet injection dupes.
- **Emergency Quarantine Switch (`/minion quarantine on`)**: Suspect a glitch or exploit? Toggle Quarantine Mode to immediately freeze all placed minion actions and GUI interactions network-wide without needing to kick players or take the server offline!
- **Sanity Audits**: Automatic inventory validation prevents negative item counts or invalid slot placements.

---

### 6. 🎭 51 Cosmetic Minion Skins & Ambient NPC Life
Minions aren't just resource machines — they bring life and personality to player islands:

- **51 Bundled Skins**: Santa, Snowman, Robot, Shark, Penguin, Pufferfish, Dragon, Sloth, Bee, Mummy, Cyber Knight, and 40+ more!
- **Dedicated GUI Slot 37**: Players can hot-swap skins directly from the minion interface.
- **Floating Dialogue Bubbles**: Minions periodically chat with their owners using floating speech bubbles when clicked or upon reaching harvest milestones!
- **Sound & Particle FX**: Authentic block breaking, crop reaping, mob slaying sounds, and leveling fanfare.

---

## 🎮 Operator Hub (`/minionsx`)

The **MinionX Operator Panel** is the ultimate administrative command center:

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
- **1-Click Minion Delivery**: Left-click to inspect, right-click to receive Tier I, or Shift-click to receive max Tier XII!
- **Complete Upgrades Vault**: Grab Auto-Smelters, Super Compactors, Diamond Spreadings, Flycatchers, and Hoppers.
- **Emergency Controls**: Instantly toggle Quarantine Mode with visual status feedback.

---

## 🔌 Supported Integrations & Hooks

MinionX integrates out-of-the-box with all leading Minecraft plugins:

| Category | Supported Plugins |
|---|---|
| 🛡️ **Land & Region Protection** | WorldGuard, GriefPrevention, Towny, Lands, BentoBox, SuperiorSkyblock |
| 💰 **Economy** | Vault (Supported by EssentialsX, CMI, etc.), RoyaleEconomy |
| ⚔️ **Custom Mobs & RPG** | MythicMobs (Custom combat spawns & drops) |
| 🎨 **Custom Items & Blocks** | ItemsAdder, MMOItems, Oraxen |
| 📊 **Skills & Collections** | AuraSkills, EcoSkills, Custom Console Dispatcher with Math Expressions |
| 📱 **Bedrock Compatibility** | GeyserMC, Floodgate |
| 📈 **Metrics & Analytics** | Anonymous bStats integration |

---

## ⚡ Quick Start Deployment (Under 60 Seconds)

1. **Install**: Drop `MinionX-0.2.0-SNAPSHOT.jar` into your server's `plugins/` folder.
2. **Start**: Start your server running Paper / Purpur 1.20.4 or newer (Java 21 required).
3. **Deploy**:
   ```bash
   /minion give <player> diamond 11
   ```
4. **Place**: Place the minion head on any block. Right-click to open its 54-slot interactive dashboard.
5. **Customize**: Type `/minionsx` or `/minion create` to explore in-game studios!

---

## 📜 Complete Commands & Permissions Reference

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
| `/minionsx` | `minionx.admin` | Opens the all-in-one MinionX Operator Panel |
| `/minion reload` | `minionx.admin` | Instant zero-downtime configuration hot-reload |

---

## 📄 License & Commercial Support

**Commercial Proprietary Software**  
Copyright © 2026 **AbhinavVanama**. All rights reserved.  
Unauthorized redistribution, decompilation, or reselling of this software is strictly prohibited. For licensing inquiries, commercial deployment, or enterprise support, please refer to the project repository.
