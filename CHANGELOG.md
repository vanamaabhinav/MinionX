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

#### 6. Native WorldGuard 7.x Region Protection & Perimeter Guarding
- **WorldGuard 7.x Integration (`Flags.BUILD` & `Flags.INTERACT`)**: Added reflective WorldGuard 7.x support checking `RegionQuery.testState(...)` for `Flags.BUILD` during minion placement and `Flags.INTERACT` during GUI container access. Protects server spawns, warzones, and admin regions.
- **Minion Operational Area Guarding**: Upgraded `ProtectionHookManager.canPlaceMinion(player, location, radius)` to evaluate both the center placement block and the 8 boundary points of the minion's working area (default 5x5, radius 2). Prevents players from placing minions near claim edges to exploit neighboring claims or wilderness.
- **Admin & Bypass Permissions**: Respects `minionx.admin`, `minionx.bypass.protection`, and `worldguard.region.bypass.*` to allow server operators to manage minions globally.
- **Native Custom Item Pricing (`PriceRegistry`)**: Integrated `CustomItemHook.identifyCustomItem(item)` directly into `PriceRegistry.getPrice(ItemStack item)`, allowing ItemsAdder, MMOItems, and EcoItems custom items to match configured prices by their native plugin namespaced ID.

#### 7. Compiler Warnings Remediation & Tooling Configuration
- **Clean `-Xlint:all` Compilation (0 Warnings)**:
  - Fixed Paper 1.20+ `getDescription()` deprecation in `MinionXPlaceholderExpansion.java` using `getPluginMeta().getVersion()` with backwards-compatible fallback.
  - Eliminated unchecked raw cast warnings in `ProtectionHookManager.java` for Towny `ActionType` by invoking `valueOf` dynamically via reflection.
- **Git Line-Ending Normalization (`.gitattributes`)**: Added root `.gitattributes` file to standardize LF normalization on text sources and binary handling on `.jar` files, eliminating Windows Git conversion warnings.

#### 8. Final Dependencies Audit & Redundant Code Cleanup
- **Expander Upgrade Boundary Guarding (`MinionGUIListener`)**: Equipping a Minion Expander dynamically calculates the prospective working radius (`2 + expansion`) and verifies that the enlarged perimeter does not encroach into an unauthorized claim or region via `ProtectionHookManager.canPlaceMinion`.
- **Minion Storage Linking Authorization (`MinionStorageListener`)**: Placing a custom Minion Storage chest adjacent to a minion now verifies `ProtectionHookManager.canAccessMinion(player, targetMinion)` before linking, preventing unauthorized players from linking chests to other players' minions.
- **Redundant Code Removal (`EconomyHook` & `ProtectionHookManager`)**: Removed dead `royaleEconomyDirect` field in `EconomyHook` and removed unreferenced static field `ssApiClass` in `ProtectionHookManager`.
- **Direct Hook Invocation (`MinionInteractListener`)**: Replaced internal legacy `IslandHookManager` delegation with direct `ProtectionHookManager.canAccessMinion` call while retaining `IslandHookManager` for external compatibility.
- **Multi-Variant Custom Item Price Matching (`PriceRegistry`)**: Enhanced `getPrice(ItemStack)` to check full namespaced IDs (`itemsadder:custom_ores:ruby`), plugin-trimmed IDs (`custom_ores:ruby`), and short IDs (`ruby`).

#### 9. Public Developer API Expansion & Javadoc Remediation
- **Public API Surface Hardening (`MinionXAPI`)**: Added `getMinion(UUID)`, `createMinionItem(typeOrId, tier)`, `createMinionItem(MinionType, tier)`, `isMinionItem(ItemStack)`, `registerCustomStorage(CustomMinionStorage)`, `registerCustomSkin(MinionSkin)`, and `isQuarantined()`.
- **Bulletproof Minion Item Check (`MinionItemFactory`)**: Wrapped PDC item metadata retrieval in `isMinionItem(ItemStack)` with defensive null/exception handling to eliminate potential NPEs when evaluated against mock or unloaded item instances.
- **Doclint & HTML Entity Remediation (`MinionSkin`)**: Corrected bare ampersand entity in `MinionSkin.java` class Javadoc, achieving clean `javadoc -Xdoclint:all` compilation without fatal errors.
- **Comprehensive API Tests (`DeveloperApiTest`)**: Added 3 unit tests verifying item generation, identification, UUID lookups, and dynamic registrations.

---

#### 10. 3-Phase Verification & Remediation (Core, Protection, API/GUI)
- **Phase 1: Core Engine & Data Integrity**:
  - **Cross-Java Compatibility**: Replaced Java 21-only `Math.clamp` calls across 15 classes with `Math.max(min, Math.min(max, val))`, guaranteeing compile and runtime compatibility across Java 17, 21, and future LTS releases.
  - **Configurable Offline Catchup Cap**: Added `settings.max-offline-hours: 168` (7 days default) in `config.yml` with `-1` unlimited support, `0` disabled toggle, and `minionx.offline.unlimited` permission bypass.
  - **Quarantine Auto-Recovery**: Added optional `settings.quarantine-auto-recovery` (disabled by default) with configurable ping interval (default 60s) and automatic lifting of storage quarantine once database connectivity is restored.
- **Phase 2: External Ecosystem & Multi-Region Protection**:
  - **WorldGuard 7.x Instance Handle**: Corrected static instance passing in `ProtectionHookManager.java` for `wrapPlayer(Player)`.
  - **9-Point Perimeter Area Verification**: Wired minion placement to validate all 8 perimeter boundary blocks of the minion's working radius (in addition to the center block), completely preventing border griefing into neighboring claims.
  - **Expanded Custom Item Ecosystem**: Added reflection hooks for Oraxen (`oraxen:item_id`) and Nexo (`nexo:item_id`) with safe fallbacks alongside ItemsAdder, MMOItems, and EcoItems.
  - **Vault Economy Test Coverage**: Created comprehensive `EconomyHookTest` verifying deposit results, balance lookups, and transaction failures.
- **Phase 3: Developer API, In-Game Tooling & Release**:
  - **GUI Anti-Dupe Click Debounce**: Added a 75ms click debounce in `MinionGUIListener.java` and `OperatorGUIListener.java` to prevent autoclicker/packet spam duplication exploits.
  - **Custom Events Contract Verification**: Added `CustomEventsTest` verifying all 8 custom events (`MinionPlaceEvent`, `MinionPickupEvent`, `MinionHarvestEvent`, `MinionUpgradeEvent`, `MinionStorageLinkEvent`, `MinionQuarantineEvent`, `MinionFuelConsumeEvent`, and `MinionCollectionEvent`).
  - **Javadoc Doclint Remediation**: Fixed bare ampersands and unescaped HTML entities in Javadoc across `MinionTickTask.java`, `PlayerLimitManager.java`, `UpgradeCreatorGUI.java`, `GeyserHook.java`, `SkillHook.java`, and `ColorUtil.java`, achieving 100% clean `javadoc -Xdoclint:all,-missing` compilation.

#### 11. In-Game Creator Studio Audit & Configurable Multi-Item Tier Upgrades
- **Configurable Multi-Item Tier Upgrades (`UpgradeRequirement` & `UpgradeCostHelper`)**: Introduced `UpgradeRequirement` allowing server administrators to configure multiple distinct item requirements per minion tier upgrade in `minions.yml` and `tiers.yml` with custom display names, quantities, custom model data, and PersistentDataContainer keys.
- **Interactive Upgrade Checklist GUI (`MinionGUI` & `MinionGUIListener`)**: Dynamic multi-item checklist in next-tier and quick-upgrade icons showing item name, required count, and real-time inventory count with colorized status (`§a✔ Have: X/Y` / `§c✘ Have: X/Y`), missing items breakdown upon failed upgrade, and atomic multi-item inventory consumption.
- **In-Game Creator Studio Security & Polish (`MinionCreatorListener`)**: Added 75ms click debouncing across all creator studios, replaced unbounded chat input maps with 60-second expiring `PromptSession` handlers, supported chat cancellation without reopen, and unblocked player inventory navigation (`slot >= 54`).
- **Defensive ID Sanitization & Numeric Bounding**: Standardized regex `[^a-z0-9_-]+` ID sanitization across all creator sessions (`MinionCreatorSession`, `MinionSkinCreatorSession`, `MinionStorageCreatorSession`, `FuelCreatorSession`, `UpgradeCreatorSession`), enforced 32-character caps, bounded fuel speed buff multipliers to `[0.01, 10.0]`, and capped tier additions to max tier 54 in `TierEditorListener`.
- **Live Armor Stand Refresh (`MinionManager.refreshMinionVisuals`)**: Automatically refreshes armor stand equipment and text holograms for all loaded active minions across all worlds when creator studio modifications are saved.
- **Dual-Mode Head Texture Resolution (`MinionCreatorGUI` & `MinionSkinCreatorGUI`)**: Distinguishes Base64 skin payloads from Minecraft usernames (`^[a-zA-Z0-9_]{3,16}$`) using `SkullUtil.applySkin(meta, value)` for textures and `setOwningPlayer` for player names.
- **Defensive Directory Creation (`MinionTier`, `MinionRegistry`, `FuelRegistry`)**: Added `file.getParentFile().mkdirs()` checks ensuring clean first-time directory creation when saving custom definitions.
- **Zero Unnecessary Files**: Removed legacy `.bak` files and Python bytecode caches.

#### 12. Fuels & Upgrades Subsystem Audit, Dynamic Item Spreading & Multi-Drop Upgrades
- **Offline Fuel Segmentation Fix (AUD-F01 & AUD-F02)**: Resolved critical logic condition in `OfflineCalculator.java` where temporary fuel burning out while offline was skipped because `isFuelActive()` evaluated to false at calculation time. The calculation now segments offline downtime into two phases: active fuel boost period (Segment 1) up to `fuelEnd`, followed by baseline minion speed (Segment 2), deactivating the expired fuel cleanly.
- **Expander Boundary Claim Guarding (AUD-U01)**: Added claim perimeter check to shift-clicking Minion Expanders in `MinionGUIListener.java` (`ProtectionHookManager.canPlaceMinion(player, loc, 2 + expansion)`), preventing unauthorized enlargement into protected land.
- **Dynamic Configurable Item Spreading (AUD-U02 & AUD-U03)**: Decoupled hardcoded diamond spreading logic in `MinionTickTask.java` and `OfflineCalculator.java`. Any item can now be configured as a spreading drop with custom material, amount, chance, display name, and custom model data. Registered default `potato_spreading` upgrade (10% chance for Potato drop).
- **Multi-Drop Bonus Generator Upgrades (`BONUS_DROPS`)**: Introduced `BONUS_DROPS` behavior supporting multiple distinct items per upgrade, each with individual probability chances, amounts, custom display names, custom model data, and external plugin hook IDs. Added default showcase `bountiful_potato` (`Bountiful Potato`: 25% Potato, 10% Poisonous Potato, 5% Baked Potato x2).
- **Duplicate Unique Upgrade Prevention (AUD-U04)**: Blocked equipping duplicate unique non-stackable upgrades (e.g. 2x Super Compactor or 2x Auto-Smelter) across both slot-click and shift-click interactions in `MinionGUIListener.java`.
- **Defensive Directory Creation (AUD-U05)**: Added `file.getParentFile().mkdirs()` in `UpgradeRegistry.saveCustomUpgrade()` to prevent `FileNotFoundException` when saving new upgrades on fresh servers.
- **Synchronized Offline Simulation**: Remainder channels (`bonus_<material>_<chance>`) accurately track fractional yields across offline catchup cycles, maintaining identical item generation ratios between online ticking and offline catchup.

#### 13. Multi-Behavior Upgrade Architecture & Modular In-Game Studio
- **Composite Multi-Behavior Capabilities (`CustomUpgrade`)**: Upgraded `CustomUpgrade` from single-behavior to composite `Set<UpgradeBehavior>` capabilities. An upgrade can now simultaneously combine multiple characteristics (e.g. `AUTO_SMELTER`, `SPEED_BUFF`, `EXPANDER`, `ITEM_SPREADING`, and `AUTO_SELLER`) executing in parallel across both online ticking and offline catchup simulation.
- **Unified Multi-Item Spreading Engine**: Eliminated redundant `DIAMOND_SPREADING` and `BONUS_DROPS` enums in favor of a single unified `ITEM_SPREADING` schema. Spreading upgrades can define multiple distinct bonus items with individual materials, quantities, probabilities, display names, custom model data, and external hook IDs.
- **In-Game Upgrade Creator Studio Overhaul (`UpgradeCreatorGUI`)**: Redesigned the 54-slot upgrade studio into a modular capability dashboard:
  - Row 1 features dedicated controls for Auto-Smelter toggle, 3-state Compactor mode cycle (`NONE` / `STANDARD` / `SUPER`), Speed Buff (+/- 5%), Area Expansion (+/- 1 block), and Auto-Seller (+/- 5%).
  - Rows 2–3 feature an interactive Multi-Item Spreading Shelf (Slots 20–24) supporting direct cursor item setting, chance tuning, quantity tuning, drop deletion via [Q], and adding new items via Slot 25.
- **Concurrency Barrier Precedence (`MinionStorage`)**: Guaranteed that `barrierMinions.remove(minionId)` runs before completing the barrier `CompletableFuture`, eliminating race conditions when awaiting barrier task completion.

#### 14. Configuration Files Audit, Discrepancy Remediation, & Bundled Template
- **Compactor Recipes Dynamic Loader Fix (`MinionX.java`)**: Resolved an issue where `compactor-recipes:` in `recipes.yml` was bypassed during startup; `MinionX.loadRegistries()` now invokes `CompressionManager.loadCompactorRecipes(compSec)`.
- **Target Aliases in Minion Definitions (`MinionRegistry.java`)**: Added resolution support for `target-crop`, `target-log`, and `target-sapling` configuration keys, aligning runtime parsing with the documented YAML schema.
- **Default Storage Template Resource (`src/main/resources/storages.yml`)**: Bundled a fully documented `storages.yml` default template containing standard storage chests (`small_storage`, `medium_storage`, `large_storage`, `max_storage`), loaded automatically when storage chests are enabled.
- **Message Fallback Parity (`config.yml`)**: Added missing fuel messages (`fuel-burned-out`, `fuel-extended`, `cannot-stack-permanent-fuel`) to the fallback messages section in `config.yml`.
- **Windows File System Lock Recovery (`MinionStorage.java`)**: Added fallback file copy/delete when atomic moves on Windows encounter transient destination file locks.
- **Automated Configuration Audit Test Suite (`ConfigAuditAndRemediationTest.java`)**: Added 8 automated tests validating YAML syntax, compactor recipes, target aliases, storage registries, message parity, upgrade schema, and fuels across all resources.

#### 15. In-Game Creator Metadata Preservation, Unified Head Engine, & %tier_roman% Fixes
- **Placeholder Formatting (`MinionGUI.java` & `PlaceholderUtil.java`)**:
  - Resolved an issue where the central minion head item in `MinionGUI` rendered `%tier_roman%` literally on the GUI stat lore.
  - Added full replacement support for `%tier%`, `%tier_roman%`, `%minion_tier%`, and `%minion_tier_roman%` in `MinionGUI.java` and `PlaceholderUtil.java`.
- **Unified Head Skin Engine (`SkullUtil.java`)**:
  - Implemented centralized `SkullUtil` handling skull texture extraction and skin application across minion armor stands, GUI menus, items, and in-game creator studios.
  - Supports Base64 texture payloads, direct Mojang skin URLs (`textures.minecraft.net/texture/...`), 64-character hex skin hashes, player usernames (`^[a-zA-Z0-9_]{3,16}$`), and player UUIDs.
  - Eliminates silent Steve head fallbacks when setting custom minion skins or player heads.
- **Full-Fidelity Custom Item Specification (`CustomItemSpec.java`)**:
  - Added native support for `skull-texture` and `enchantments` map in `CustomItemSpec`.
  - Updated `fromItemStack(ItemStack)` to preserve custom display names, multi-line lore, all active enchantments, glowing effect, and skull skins.
  - Updated `isCustom()` to detect items with custom lore, enchantments, or skull textures, ensuring they are preserved intact during YAML serialization (`toConfigMap`) and deserialization (`fromMap`).
- **In-Game Creator Studio Cursor Preservation**:
  - `MinionCreatorListener`: Slot 10 (Miner target) clones the cursor item to preserve custom metadata instead of creating an empty `new ItemStack(Material)`.
  - `MinionCreatorListener`: Slot 15 (Skull Texture) and Slot 10 (Skin Creator) utilize `SkullUtil.extractTexture(cursor)` to copy skin textures from held player heads.
  - `MinionCreatorGUI`: Slots 12 and 13 display custom lore lines, active enchantments, and custom head texture status alongside slot instructions instead of overwriting the item's lore in the menu.
  - `UpgradeCreatorListener` & `UpgradeCreatorSession`: Slot 4 uses `applyIconFrom(cursor)` to preserve custom display name, lore, custom model data, and glow on upgrade items. Slots 20–24 use `BonusDropEntry.fromItemStack(cursor)` to preserve custom attributes on spreading bonus items.

### Verification & Technical Metrics
- **Test Suite**: 310 unit tests passed across 44 test classes (`100% pass rate`).
- **Compiler Checks**: Compiled with `javac --release 17 -proc:none -Xlint:all` (**0 warnings, 0 errors**).
- **Documentation Checks**: Validated with `javadoc -Xdoclint:all,-missing` (**0 errors, 0 warnings**).
- **Static Analysis**: Checked with zero IDE warnings (**0 unused imports, 0 unused private members**).
- **Bytecode Verification**: Verified `Major Version 61.0` (Java 17 target).
- **Artifact Versioning**: Bumped to `1.0.3` across `plugin.yml`, `pom.xml`, `README.md`, and test suites.
- **Production Artifacts**: Packaged `target/MinionX-1.0.3.jar` and `target/MinionX.jar` (525,654 bytes).

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
