# Changelog

All notable changes to **MinionX** are documented in this file.

---

## [1.0.3] - 2026-11-01

### Grounded Overview for Buyers & Server Owners
> **MinionX v1.0.3** introduces native island claim protection and team access integration for **SuperiorSkyblock2**, along with critical security, compatibility, and performance overhauls across all supported soft dependencies (**Vault**, **PlaceholderAPI**, **Geyser-Spigot**, **floodgate**, **MythicMobs**, **AuraSkills**, **EcoSkills**, **EcoCollections**, **ItemsAdder**, **MMOItems**, **EcoItems**, **BentoBox**, **Towny**, **Lands**, and **GriefPrevention**).
>
> On servers running SuperiorSkyblock2 or BentoBox, minion placement is now strictly checked against island boundaries, preventing non-members from placing minions on other players' islands and closing void/wilderness placement exploits. Towny integration has been re-engineered using canonical `PlayerCacheUtil` permissions, fixing a startup failure where Towny silently disabled itself.
>
> Across all non-protection soft dependencies, Vault integration now supports canonical `OfflinePlayer` parameter signatures, eliminating silent transaction failures on strict economy providers. A native `PlaceholderExpansion` registers `%minionx_...%` placeholders with PlaceholderAPI for scoreboards, tab lists, and chat. Cross-platform Bedrock detection supports modern Floodgate 2.0+ (`isFloodgatePlayer`) and standalone `GeyserApi`. Combat minions support modern MythicMobs v5 (`MythicBukkit.inst().getAPIHelper()`) and legacy v4, and all hook lookups utilize zero-allocation cached reflection handles refreshed on startup and reload.

---

### Key Highlights & Technical Changes

#### 1. Native SuperiorSkyblock2 Claim Protection (`ProtectionHookManager`)
- **Reflective Island Lookup**: Added reflective island boundary and membership checking via `SuperiorSkyblockAPI.getIslandAt(location)`.
- **Placement Enforcement**: Prevents non-members from placing minions on SuperiorSkyblock2 islands (`ProtectionHookManager.canPlaceMinion`).
- **SkyBlock Wilderness Guarding**: Integrates with SuperiorSkyblock's `GridManager.isIslandsWorld(World)` to prevent players from placing minions outside islands in designated SkyBlock worlds.
- **Team Access & Co-op Support**: In `security.access-mode: TEAM_ACCESS`, queries `Island.isMember(SuperiorPlayer)` and `Island.isCoop(SuperiorPlayer)` to allow island team members to open minion GUIs and deposit fuel (`ProtectionHookManager.canAccessMinion`).
- **Co-op Dismantle Security**: Prevents teammates and visitors from picking up minions unless `security.allow-team-pickup: true` or `security.access-mode: FULL_COOP` is explicitly enabled.
- **Fail-Safe Reflection & Clean Reloads**: Automatically detects SuperiorSkyblock2 and gracefully falls back to default Bukkit protections if absent. Nulls cached reflection handles on disable to prevent ClassLoader leaks across server reloads.

#### 2. Protection Soft-Dependency Overhaul & Bug Fixes
- **Towny Runtime Activation (`PlayerCacheUtil`)**: Resolved a critical issue where `TownBlock.canPlayerBuild` failed with `NoSuchMethodException` on startup. Implemented Towny's canonical `PlayerCacheUtil.getCachePermission` targeting `ActionType.BUILD` for placement and `ActionType.SWITCH` for GUI container access.
- **BentoBox Wilderness Guarding (`IWM`)**: Hooked BentoBox's `IslandWorldManager.inWorld(World)`. In BentoBox island worlds where no island exists (`isPresent() == false`), placement is now vetoed to prevent void/wilderness minion exploits.
- **BentoBox Co-op & Trusted Access**: Extended island membership checks to inspect `Island.getMembers()`, allowing players with Coop (rank 100) or Trusted (rank 200) status to place minions and access GUI menus under `TEAM_ACCESS`.
- **Lands 6.x/7.x Trust Resolution**: Added modern Lands compatibility for `Area.isTrusted(UUID)` alongside legacy `canBuild`/`canAccess`, ensuring continuous protection across all Lands release generations.
- **Pure Negative-Guard Veto Chain**: Converted `canPlaceMinion` across all 5 providers into a non-short-circuiting veto chain. A plugin only vetoes when an unauthorized action is detected in its domain; authorized actions continue through remaining providers to verify overlapping regions before permitting placement.

#### 3. Soft-Dependencies Audit & Cross-Version Hardening
- **Vault Economy Method Resolution (`EconomyHook`)**: Resolved parameter signature mismatch where `EconomyHook` was querying `Player.class`, whereas Vault's `net.milkbowl.vault.economy.Economy` declares `OfflinePlayer.class`. Implemented tiered parameter lookup (`OfflinePlayer` -> `Player` -> `String`) and cached reflection handles, eliminating `NoSuchMethodException` and silent transaction failures.
- **Native PlaceholderAPI Expansion (`MinionXPlaceholderExpansion`)**: Registered native `minionx` expansion with PlaceholderAPI exposing `%minionx_placed%`, `%minionx_limit%`, `%minionx_available%`, `%minionx_bonus%`, and `%minionx_quarantine_status%` for external scoreboards, TAB, chat, and menus.
- **Cached PlaceholderAPI Reflection (`PlaceholderUtil`)**: Cached `PlaceholderAPI.setPlaceholders` reflection calls to eliminate per-string classloader contention and heap allocation during high-frequency parsing.
- **Cross-Platform Bedrock Detection (`GeyserHook`)**: Updated Floodgate API reflection to target canonical Floodgate 2.0+ `isFloodgatePlayer(UUID)` with fallback to legacy `isFloodgateId(UUID)`. Integrated `org.geysermc.geyser.api.GeyserApi.api().isBedrockPlayer(UUID)` for standalone Geyser-Spigot instances.
- **Combat Mob Spawning Resilience (`MythicMobsHook`)**: Added modern MythicMobs v5 resolution via `MythicBukkit.inst().getAPIHelper().spawnMythicMob(String, Location)` returning Bukkit `Entity` directly, with fallbacks to `MythicBukkit.get().getMobManager()` and legacy MythicMobs v4 (`MythicMobs.inst().getAPIHelper()`).
- **Skills Hook Caching & AureliumSkills Fallback (`SkillHook`)**: Cached reflection handles for AuraSkills and EcoSkills. Added fallback support for legacy AureliumSkills (`com.archyx.aureliumskills.api.AureliumSkillsApi`) and server null guards.
- **Custom Item Reverse Identification (`CustomItemHook`)**: Added cached reflection handles and reverse item detection (`isCustomItem` / `identifyCustomItem`) for ItemsAdder, MMOItems, and EcoItems.
- **Lifecycle Hook Refreshing (`MinionX` & `MinionCommand`)**: Wired all hook detection logic into `MinionX.onEnable()` and `/minion reload` (`checkHooks()`), ensuring dynamic plugin reloads and late-loading dependencies are detected without restarting the server.

#### 4. Zero-Allocation Reflection Optimization (`CollectionsHook` & `ProtectionHookManager`)
- **Collections Hook Caching**: Cached reflective method handles for EcoCollections (`EcoCollectionsAPI.addProgress`) and SuperiorSkyblock2 (`SuperiorSkyblockAPI.getPlayer`, `SuperiorPlayer.getIsland`, `Island.handleBlockBreak`) during plugin initialization.
- **Eliminated Per-Harvest Lookups**: Replaced per-tick `Class.forName` and `Class.getMethod` lookups on minion harvests with static cached invocations, removing JVM classloader synchronization overhead and heap allocations during mass minion operation.
- **Cached Parameter Types**: Cached `getPlayer` UUID/Player signature flags to eliminate repeated `Method.getParameterTypes()` array cloning on minion access and placement checks.

#### 5. Backwards Compatibility Delegate (`IslandHookManager`)
- Added `canPickupMinion(Player player, Minion minion)` delegating to `ProtectionHookManager` to maintain binary and source compatibility for external addons referencing `IslandHookManager`.

---

### Verification & Technical Metrics
- **Test Suite**: 257 unit tests passed across 36 test classes (`100% pass rate`).
- **Compiler Checks**: Compiled with `javac 21 -parameters -proc:none -Xlint:deprecation -Xlint:unchecked` (0 errors).
- **Artifact Versioning**: Bumped to `1.0.3` across `plugin.yml`, `pom.xml`, `README.md`, and test suites.
- **Production Artifacts**: Packaged `target/MinionX-1.0.3.jar` and `target/MinionX.jar` (499,555 bytes).

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
20. `softdepend`: Declared explicit load-order list in `plugin.yml` for all 16 supported external plugins (Vault, PlaceholderAPI, GriefPrevention, BentoBox, Towny, Lands, AuraSkills, EcoSkills, SuperiorSkyblock2, EcoCollections, ItemsAdder, MMOItems, EcoItems, Geyser-Spigot, floodgate, MythicMobs).

---

### Verification & Technical Metrics (v1.0.2)
- **Test Suite**: 236 unit tests passed across 34 test classes (`100% pass rate`).
- **Compiler Checks**: Compiled with `javac 21 -parameters -proc:none -Xlint:all` (0 warnings, 0 errors).
- **Artifact Versioning**: Bumped to `1.0.2` across `plugin.yml`, `pom.xml`, and test suites.
- **Git State**: Clean commit on branch `v1.0.2`. Merged into `main`.
