# Changelog

All notable changes to **MinionX** are documented in this file.

---

## [1.0.2] - 2026-10-30

### Grounded Overview for Buyers & Server Owners
> **MinionX v1.0.2** adds modular subsystem toggles, proximity-culled hologram nametags (`nametags.mode: TARGETED`), and full integration for previously unread configuration options, messages, and permission checks.
>
> Subsystems (fuels, upgrades, automated hoppers, skins, external storage chests, and offline simulation) can now be individually enabled or disabled in `config.yml`. When a feature is disabled, its corresponding GUI slots are replaced with standard background border panes, its subcommands and tab-completions are hidden, and default configuration files for that feature are omitted from disk on initial startup.

---

### Key Highlights & Technical Changes

#### 1. Modular Subsystem Toggles (`features:` in `config.yml`)
Server administrators can selectively enable or disable any minion subsystem:
- `features.fuels: true/false` - Custom fuels, burn timers, and speed multipliers (`fuels.yml`).
- `features.upgrades: true/false` - Modifier accessories (Compactor, Auto-Smelter, Diamond Spreading, Flycatcher, etc. - `upgrades.yml`).
- `features.automated-shipping: true/false` - Budget & Enchanted Hoppers for automated offsite selling (`prices.yml`).
- `features.skins: true/false` - Minion skin cosmetic transformations (`skins.yml`).
- `features.storage-chests: true/false` - Physical adjacent external chests (Small, Medium, Large - `storages.yml`).
- `features.offline-catchup: true/false` - Offline resource catchup calculation on chunk/island loading.

**"Feature Never Existed" Behavior:**
- **GUI Consistency**: When a subsystem is disabled (e.g., `features.fuels: false` or `features.skins: false`), its GUI slot (Slots 10, 19, 28, 37, 46) renders as a standard `BLACK_STAINED_GLASS_PANE` with title `" "`, matching the surrounding menu border without showing locked or error items.
- **Config Suppression**: Disabling a feature prevents the plugin from saving the unused default configuration file to disk on first startup.
- **Dynamic Tab-Completion & Studio Guarding**: `/minion create <type>` and `/minion give <player> <item>` hide disabled features from tab-completion and prevent opening disabled creator studios.
- **Inventory Shift-Click Protection**: Shift-clicking disabled feature items from player inventory into the minion GUI is prevented.

#### 2. Client-Side Hologram Culling (`nametags.mode: TARGETED`)
- Implemented **Proximity Culling** for `nametags.mode: TARGETED` using Paper's native `Display.setViewRange(0.1f)` API.
- Minion floating nametags cull on the client beyond approximately 6 blocks distance, reducing visual clutter in dense minion setups.
- **Architecture**: Delegates culling to the Minecraft client rendering engine, avoiding per-tick player raycasting or server-side distance calculation loops.
- Supported nametag modes: `ALWAYS`, `TARGETED`, and `DISABLED`.

#### 3. Configuration & Message Remediation (19 Keys Audited)
Previously unread or mismatched configuration options, messages, and permissions are now connected:
1. `settings.enable-animations`: Controls arm swinging and head turning in `MinionAnimation.java`.
2. `settings.enable-sounds`: Controls block breaking, tree chopping, and GUI audio feedback.
3. `settings.enable-particles`: Controls block break particles, crop growth particles, and tree harvest particles.
4. `settings.geyser-touch-friendly`: Respected in `OperatorGUIListener.java` alongside Bedrock player detection.
5. `storage-layouts.use-custom-layouts`: Checked in `SymmetricalSlotCalculator.java` before reading custom chest slot layouts.
6. `settings.max-offline-hours: 0`: Disables offline simulation calculations in `OfflineCalculator.java` and `FeatureToggle.java`.
7. `gui.head-lore`: Documented in `config.yml` with placeholders `%delay%`, `%harvested%`, `%stored%`, `%capacity%`, `%status%`, and `%tier_roman%`.
8. `messages.inventory-full`: Dispatched when player inventory has no room during item collection.
9. `messages.already-max-tier`: Dispatched when attempting to upgrade a minion already at maximum tier.
10. `messages.not-enough-resources` & `messages.not-enough-items`: Unified with automatic fallback aliasing.
11. `messages.picked-up`: Dispatched when reclaiming placed minions with `%minion%` and `%type%` placeholders.
12. `messages.area-blocked`: Dispatched in `MinionPlaceListener.java` when vertical space above the block is obstructed.
13. `messages.fuel-burned-out`: Dispatched when temporary fuels finish burning on removal.
14. `messages.cannot-stack-permanent-fuel`: Dispatched when attempting to insert multiple permanent fuels.
15. `messages.fuel-extended`: Dispatched when adding matching temporary fuel to extend duration.
16. `messages.no-permission`: Routed across `/minion` subcommands.
17. `messages.no-minion-access-permission`: Dispatched when unauthorized players interact with protected minion entities or linked storage.
18. `minionx.use`: Checked in `MinionPlaceListener.java` and `MinionInteractListener.java`.
19. `minionx.storage`: Checked in `MinionStorageListener.java` before placing or opening minion storage chests.

---

### Verification & Technical Metrics
- **Test Suite**: 236 unit tests passed across 34 test classes (`100% pass rate`).
- **Compiler Checks**: Compiled with `javac 21 -parameters -proc:none -Xlint:all` (0 warnings, 0 errors).
- **Artifact Versioning**: Bumped to `1.0.2` across `plugin.yml`, `pom.xml`, and test suites.
- **Git State**: Clean commit on branch `v1.0.2`. Not merged into `main`.
