# Changelog

All notable changes to **MinionX** are documented in this file.

---

## [1.0.2] - 2026-10-30

### Short Description for Buyers & Server Owners
> **MinionX v1.0.2** introduces complete **modular subsystem toggles**, a revolutionary **zero-overhead proximity hologram culling engine** (`nametags.mode: TARGETED`), and a full audit ensuring **every single configuration option, message, and permission is active and respected**. 
>
> If you don't want fuels, upgrades, hoppers, skins, or external chests on your server, simply set their toggle to `false` in `config.yml`. Under our **"Feature Never Existed"** philosophy, disabled features leave zero trace: their GUI slots seamlessly blend into the background as standard border panes, their subcommands and tab-completions vanish, their mechanics incur 0% CPU overhead, and their configuration files (`fuels.yml`, `upgrades.yml`, `skins.yml`, `storages.yml`) are never generated in your server folder.

---

### Key Highlights & Features

#### 1. Modular Subsystem Toggles (`features:` in `config.yml`)
Server administrators can now selectively enable or disable any minion subsystem:
- `features.fuels: true/false` - Custom fuels, burn timers, and speed multipliers (`fuels.yml`).
- `features.upgrades: true/false` - Modifier accessories (Compactor, Auto-Smelter, Diamond Spreading, Flycatcher, etc. - `upgrades.yml`).
- `features.automated-shipping: true/false` - Budget & Enchanted Hoppers for automated offsite selling (`prices.yml`).
- `features.skins: true/false` - Minion skin cosmetic transformations (`skins.yml`).
- `features.storage-chests: true/false` - Physical adjacent external chests (Small, Medium, Large - `storages.yml`).
- `features.offline-catchup: true/false` - Offline resource catchup calculation on chunk/island loading.

**The "Feature Never Existed" Philosophy:**
- **Zero GUI Clutter**: When a subsystem is disabled (e.g. `features.fuels: false` or `features.skins: false`), its GUI slot (Slots 10, 19, 28, 37, 46) does not display barrier blocks, locks, or error messages; it renders as a standard `BLACK_STAINED_GLASS_PANE` (`" "`), blending seamlessly into the border.
- **Config Suppression**: Disabling a feature prevents the plugin from saving the unused configuration file to disk on first startup.
- **Dynamic Tab-Completion & Studio Guarding**: `/minion create <type>` and `/minion give <player> <item>` dynamically hide disabled features from tab-completion and prevent opening their studio editors.
- **Shift-Click & Interaction Safety**: Shift-clicking disabled feature items from inventory into the minion GUI is cleanly prevented.

#### 2. Native Client-Side Hologram Culling (`nametags.mode: TARGETED`)
- Implemented **Option B (Native Proximity Culling)** for `nametags.mode: TARGETED` using Paper's native `Display.setViewRange(0.1f)` API.
- Minion floating nametags automatically cull on the client beyond $\approx 6$ blocks distance, eliminating visual clutter in dense minion farms.
- **Zero Server Overhead**: Uses native client-side rendering distance culling without any server-side player raycasts, distance loops, or packet listeners.
- Supported nametag modes: `ALWAYS`, `TARGETED`, and `DISABLED`.

#### 3. Ghost Configuration Options Remediation (19 Audited & Fixed)
Every previously dormant or mismatched configuration key is now fully wired up and active:
1. `settings.enable-animations`: Controls arm swinging and head turning in `MinionAnimation.java`.
2. `settings.enable-sounds`: Controls block breaking, tree chopping, and GUI audio feedback.
3. `settings.enable-particles`: Controls block break particles, crop growth bonemeal particles, and tree harvest particles.
4. `settings.geyser-touch-friendly`: Respected in `OperatorGUIListener.java` for Bedrock/Geyser tap interactions.
5. `storage-layouts.use-custom-layouts`: Checked in `SymmetricalSlotCalculator.java` before applying custom chest slot definitions.
6. `settings.max-offline-hours: 0`: Now cleanly disables offline catchup simulation entirely in `OfflineCalculator.java` and `FeatureToggle.java`.
7. `gui.head-lore`: Fully documented in `config.yml` with support for `%delay%`, `%harvested%`, `%stored%`, `%capacity%`, `%status%`, and `%tier_roman%`.
8. `messages.inventory-full`: Now routed when player inventory cannot accommodate collected items.
9. `messages.already-max-tier`: Now routed when attempting to upgrade a minion at max tier.
10. `messages.not-enough-resources` & `messages.not-enough-items`: Unified with automatic fallback aliasing.
11. `messages.picked-up`: Now routed when reclaiming placed minions with `%minion%` and `%type%` placeholders.
12. `messages.area-blocked`: Now routed in `MinionPlaceListener.java` when vertical headroom is obstructed.
13. `messages.fuel-burned-out`: Now routed when temporary fuels finish burning.
14. `messages.cannot-stack-permanent-fuel`: Now routed when attempting to insert multiple permanent fuels.
15. `messages.fuel-extended`: Now routed when adding matching temporary fuel to extend duration.
16. `messages.no-permission`: Now routed uniformly across `/minion` subcommands.
17. `messages.no-minion-access-permission`: Now routed when unauthorized players click minion entities or linked storage.
18. `minionx.use`: Checked in `MinionPlaceListener.java` and `MinionInteractListener.java`.
19. `minionx.storage`: Checked in `MinionStorageListener.java` before placing or opening minion storage chests.

---

### Technical & Quality Verification
- **Test Suite**: 236 unit tests passed across 34 test suites (`100% pass rate`).
- **Compiler Warnings**: Clean build with `javac 21 -parameters -proc:none -Xlint:all` (0 warnings, 0 errors).
- **Artifact Versioning**: Bumped to `1.0.2` across `plugin.yml`, `pom.xml`, and test suites.
- **Git State**: Clean commit on branch `v1.0.2`. Not merged into `main` pending operator instruction.
