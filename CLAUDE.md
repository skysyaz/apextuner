# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

ApexTuner is a root/shizuku Android tuning app (CPU/GPU/display/thermal/VPN/DNS) for rooted devices. Every privileged toggle writes to real sysfs nodes — there are no placebo buttons. Kotlin 2.0, Jetpack Compose, MVVM + Repository, Hilt DI, Room + DataStore. Min SDK 26, target/compile SDK 34, JDK 17.

This is **not** a git repo (no `.git/`); don't assume git history is available.

## Build & test

```bash
./gradlew :app:assembleDebug          # debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # release (no signing config is wired up)
./gradlew test                        # all unit tests
./gradlew :data:test                  # ProfileSerializer, ProfileRepository (Robolectric + Room)
./gradlew :engine:test                # CpuController, ProfileApplier (MockK + Truth)
./gradlew connectedAndroidTest        # instrumented Onboarding/Dashboard smoke (needs device/emulator)
./gradlew :engine:testDebugUnitTest --tests "com.apextuner.engine.cpu.CpuControllerTest"  # single test class
```

`configuration-cache` + `parallel` are on (see `gradle.properties`). First sync downloads ~500 MB. The `:app` debug build has `applicationIdSuffix = ".debug"`.

## Module graph

Five Gradle modules (declared in `settings.gradle.kts`). Dependency direction matters:

```
:app  →  :engine, :vpn, :data, :native
:vpn  →  :engine, :data
:engine → :data
:native → :engine, :data   (Kotlin-only stub — no C++/Rust yet)
:data  →  (nothing internal)
```

`:data` is the leaf: Room entities/DAOs, DataStore, repositories, domain models, JSON serialization. `:engine` is tuning business logic. `:vpn` is the VPN/DNS subsystem. `:app` is UI + services + DI + WorkManager. **`:engine` does not depend on `:vpn`** (avoids a cycle) — so `ProfileApplier` does not apply network config. The `:app` orchestration layer calls both `ProfileApplier` and `VpnController` when a profile has a network config. Preserve this boundary when adding profile-driven behavior.

All module build files use the version catalog at `gradle/libs.versions.toml` — add deps there, not inline.

## Architecture: the parts that span multiple files

### Shell abstraction (privileged access)
`engine/root/ShellExecutor.kt` defines the interface every privileged op flows through (`exec`, `execScript`, `readFile`, `writeFile`). Three implementations: `SuShell` (libsu root), `ShizukuShell` (IShell binder, non-root elevated), `UnprivilegedShell` (Runtime exec, reads only). `ShellSelector` picks the highest-capability backend **per call** (`best()`, `bestForSysfsWrite()` → root only, `bestForSecureSettings()` → root or Shizuku) and re-evaluates from `RootAvailability.cached()` so a mid-session grant/revocation takes effect immediately. **Never call a concrete shell directly from a controller** — go through `ShellSelector`. `ShellResult` is a plain data class so controllers are unit-testable without Android.

### Transactional sysfs writes
`engine/safety/Transaction.kt` + `RollbackManager.kt`. Every privileged controller (CPU, GPU) wraps writes in: `rollback.begin(id)` → `tx.capture(path, original)` per node → write + verify-by-reread → `tx.commit()` on success, or `RollbackManager.rollback(tx, shell)` on any verify failure. Rollback reverses captured writes; paths whose original was unreadable (`null`) are skipped (writing garbage is worse than writing nothing). Write order within a cluster matters — governor → min freq → max freq → online — because the kernel rejects max<min. See `CpuController.apply` for the canonical pattern; mirror it for any new sysfs controller.

### ProfileApplier is the single funnel
`engine/profile/ProfileApplier.kt` is the one entry point for applying any profile — built-in presets, user profiles, Gaming Mode, boot-apply, and WorkManager restore all go through `apply` / `applyById` / `applyBuiltIn`. Each subsystem (CPU/GPU/Display) apply is wrapped in `runCatching` and logged; a failure in one subsystem does **not** abort the others. For built-in `ThermalPolicy` presets, it synthesizes a config from live topology (`CpuController.buildPreset`, `GpuController.buildPreset`) instead of requiring a stored row. After a successful apply it persists `activeProfileId` and `lastSafeProfileId` to DataStore.

### Capability tiers and graceful degradation
Three tiers (root / Shizuku / neither). Writes silently no-op and return false when the backend can't write; the UI surfaces a "Root Required" banner. Reads work without root on most kernels. When adding a privileged feature, pick the right `bestFor*` selector and make the no-capability path return a sentinel, not throw.

### Data flow
Room (`ApexDatabase` with Profile/Game/Log DAOs) for persisted entities + DataStore (`SettingsDataStore`) for key-value settings and the root/shizuku/active-profile flags. Repositories wrap DAOs/DataStore. `TunerLog` is written via `LogRepository` from everywhere — the safety rollback path and every subsystem-apply failure log here. Sample profiles live in `samples/*.json` and exercise the `ProfileJson` serialization shape.

### Hilt DI
`app/di/AppModule.kt` provides the database, DAOs, repositories, and `SettingsDataStore` as `@Singleton` in `SingletonComponent`. Controllers and `ShellSelector`/`RollbackManager` use `@Inject constructor` and are discovered by Hilt without explicit provides. WorkManager uses `HiltWorkerFactory` (the default `WorkManagerInitializer` is removed in the manifest and replaced with manual init via `ApexTunerApp`). `RestoreProfileWorker` re-applies `lastSafeProfileId` after process death / boot.

### Entry points / services (declared in `app/src/main/AndroidManifest.xml`)
`MainActivity` (Compose host, `ApexNavGraph`), `TunerForegroundService` (special-use FGS: persistent tuner state + thermal watchdog, holds a wakelock while Max-Perf is active), `ApexVpnService` (`VpnService`, full-tunnel or DNS-only, `allowBypass(false)` + iptables hard kill switch on the ApexTuner chain — never touches the user's existing rules), `GamingModeTileService` (QS tile), `GameLaunchAccessibilityService` (fallback game detection; primary path is `UsageStatsManager` via `GameDetector`), `BootReceiver` (apply-on-boot).

## Conventions

- Package root is `com.apextuner.{module}` (`:native` uses `com.apextuner.native_` — `native` is a Kotlin keyword). Keep new code under the matching module package.
- Coroutines + Flows throughout. `SettingsDataStore.snapshot` is a `Flow`; controllers/repositories expose suspend reads and `Flow` for observable state. ViewModels collect via `lifecycle-runtime-compose`'s `collectAsStateWithLifecycle`.
- sysfs paths are probed at runtime, never hardcoded to one SoC — `CpuPaths`/`GpuPaths`/`ThermalPaths` centralize the per-SoC path logic. New SoC support goes there.
- Unit tests use Robolectric (Room) + MockK (controllers, mock `ShellExecutor`) + Truth + Turbine (Flow). When testing a controller, pass a fake/mock `ShellExecutor` — don't bring up libsu/Shizuku.
- The `:native` module is intentionally a Kotlin stub; do not add real native code without coordinating the module-graph intent (see README "Native module"). All polling is pure-Kotlin via libsu + coroutines in `:engine`.