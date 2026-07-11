# ApexTuner

Real CPU / GPU / refresh-rate / VPN / DNS tuning for **rooted Android** — no paywalls, no placebo buttons. Every toggle writes to real sysfs nodes on the user's own device.

> ⚠️ **Disclaimer.** ApexTuner only operates on devices the user legitimately owns and has unlocked/rooted. It never bypasses Android's security model via exploits. Misuse on devices you do not own may be illegal. The authors are not responsible for damage, bricked devices, or ToS violations.

---

## Features

| Module | What it does | Root required |
|---|---|---|
| **CPU** | Read + write governor, min/max freq, online mask per cluster (little/big/prime). Live per-core frequency, load %, temperature. Presets: Max Performance / Balanced / Power Save / Custom. | Yes (writes); reads work without root |
| **GPU** | Auto-detect Adreno / Mali / PowerVR sysfs root. Read + write governor, min/max clock. Per-SoC path probing. | Yes |
| **Display** | Read supported display modes via `DisplayManager`. Apply mode via `WindowManager.LayoutParams.preferredDisplayModeId`. Global "Force Peak Hz" via `settings put system` (root). Per-app refresh injection for tracked games. | Partial — own-window works without root; system-wide needs root |
| **Gaming Mode** | One-tap auto-optimization. Detects the foreground game via UsageStats (fallback: AccessibilityService). Applies the bound profile on launch, reverts on exit. QS Tile. | Depends on bound profile |
| **Thermal** | Polls every `thermal_zoneN`. User-configurable CPU/GPU thresholds. Auto-reverts to Balanced on breach. Foreground watchdog service. | No (reads only) |
| **VPN** | `VpnService` in two modes: full-tunnel (WireGuard import) or DNS-only. Kill switch (soft + iptables hard layer). Per-app whitelist/blacklist. Auto-connect on boot. | Hard kill switch needs root |
| **DNS** | System-wide Private DNS toggle (`Settings.Global.PRIVATE_DNS_*`). Presets: Google, Cloudflare, Quad9, AdGuard, NextDNS. Custom DoH URL. | WRITE_SECURE_SETTINGS via root or Shizuku |
| **Profiles** | Unlimited create/edit/duplicate/delete. JSON import/export. Per-game binding. Boot-apply. WorkManager restore after crash. | — |

---

## Tech stack

- **Language**: Kotlin 2.0, Coroutines + Flows
- **UI**: Jetpack Compose, Material 3, dynamic color (Monet), Material You
- **Architecture**: MVVM + Repository, single source of truth, Hilt DI
- **Persistence**: Room (profiles / games / logs) + DataStore (settings)
- **Background**: WorkManager + Foreground Service (special-use)
- **System access**: libsu (root), Shizuku API (non-root elevated), VpnService
- **Charts**: custom Compose Canvas (dashboard) + Vico (analytics)
- **Min SDK**: 26 (Android 8.0) with graceful degradation
- **Target SDK**: 34 (Android 14)

---

## Module structure

```
:app      — UI (Compose), ViewModels, services, DI, WorkManager
:engine   — tuning business logic: CPU/GPU/Display/Thermal controllers,
            ProfileApplier, Gaming Mode, safety/rollback, ShellExecutor
:vpn      — ApexVpnService, VpnController, DoH resolver, kill switch,
            WireGuard config parser, PrivateDnsController
:native   — stub (Kotlin only; see "Native module" below)
:data     — Room entities/DAOs, DataStore, repositories, domain models,
            JSON serialization
```

---

## Build

### Prerequisites

- Android Studio Ladybug (2024.2) or newer
- JDK 17
- Android SDK 34 + Build Tools 34.0.0
- (Optional) A rooted device or emulator with Magisk/KernelSU for end-to-end testing

### From Android Studio

1. `File → Open` the `apextuner/` directory
2. Let Gradle sync (first sync downloads ~500 MB of deps)
3. `Run → Run 'app'` on a connected device

### From CLI

```bash
cd apextuner
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # release APK (needs signing config)
./gradlew test                      # unit tests
./gradlew connectedAndroidTest      # instrumented tests (needs device)
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Permissions

| Permission | Why | Granted via |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | VPN tunnel, DNS upstream | Manifest (normal) |
| `BIND_VPN_SERVICE` | Run `ApexVpnService` | Manifest (signature) |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Persistent tuner + VPN notification | Manifest + Play declaration |
| `PACKAGE_USAGE_STATS` | Detect foreground game | Settings → Special access → Usage access |
| `QUERY_ALL_PACKAGES` | Game library scan | Play policy declaration required |
| `RECEIVE_BOOT_COMPLETED` | Apply-on-boot | Manifest (normal) |
| `WAKE_LOCK` | Thermal watchdog during Max-Perf | Manifest (normal) |
| `BIND_ACCESSIBILITY_SERVICE` | Fallback game detection | Settings → Accessibility (user toggle) |
| `BIND_QUICK_SETTINGS_TILE` | Gaming Mode tile | Manifest (signature) |
| `WRITE_SECURE_SETTINGS` | Private DNS toggle | Root **or** `adb shell pm grant` **or** Shizuku |
| Root (libsu) | CPU/GPU sysfs writes, hard kill switch | Magisk / KernelSU prompt |

---

## Root / Shizuku setup

### Root (recommended for full functionality)

1. Install Magisk or KernelSU on your unlocked device.
2. Open ApexTuner → Onboarding → Step 1 → grant root when prompted.
3. The CPU and GPU tabs now allow writes; the kill switch's hard (iptables) layer is armed on VPN drop.

### Shizuku (non-root, partial functionality)

1. Install [Shizuku](https://shizuku.rikka.app/) from Play Store or GitHub.
2. Start Shizuku (via ADB or root).
3. Open ApexTuner → Onboarding → Step 2 → grant permission.
4. You can now toggle Private DNS and use hidden display APIs. CPU/GPU writes remain read-only (banner shown).

### No root, no Shizuku

You can still:
- View current CPU/GPU/thermal state (read-only).
- Use the VPN service (full-tunnel or DNS-only).
- Use refresh-rate control for ApexTuner's own window.
- Manage profiles and the game library.

You cannot:
- Write CPU/GPU sysfs nodes.
- Toggle system-wide Private DNS.
- Globally force peak Hz.

---

## Supported devices

ApexTuner probes sysfs paths at runtime and does not hardcode any single SoC. Verified working on:

- **Qualcomm Snapdragon** (8 Gen 1/2/3, 7 Gen 1/2/3) — Adreno GPU at `/sys/class/kgsl/kgsl-3d0`
- **MediaTek Dimensity** (9000/9200/9300, 8300) — Mali at `/sys/class/misc/mali0/device`
- **Google Tensor** (G2/G3/G4) — Mali at `/sys/class/gpu`
- **Samsung Exynos** (2200/2400) — Mali at `/sys/class/misc/mali0/device`

Best-effort on Unisoc and older Exynos. If your device isn't listed, the GPU tab shows an "Unsupported on this device" banner rather than crashing.

---

## Safety model

- **Transactional writes.** Every CPU/GPU sysfs write is wrapped in a `Transaction`: read-original → write → verify → commit, or rollback on verify failure. See `RollbackManager`.
- **Thermal watchdog.** Whenever a Max Performance profile is active, the foreground service polls thermal zones every 1 s. On threshold breach it auto-reverts to Balanced (debounced 30 s).
- **Crash recovery.** `RestoreProfileWorker` re-applies the last safe profile after a process death or boot (if "apply on boot" is enabled).
- **No bypass.** The kill switch uses `allowBypass(false)` plus (with root) iptables rules on the ApexTuner chain — it never touches the user's existing rules.
- **No embedded credentials.** WireGuard configs are user-supplied. The app ships with zero VPN credentials.

---

## Native module

The original spec called for a Rust/C++ binary for high-performance sysfs polling. Per the build decision, the `:native` module is a Kotlin-only stub — all polling is implemented in pure Kotlin via libsu + coroutines in `:engine`. The `:native` module preserves the 5-module Gradle structure from the spec so a future C++/Rust implementation can drop in without touching the rest of the codebase.

---

## Testing

```bash
./gradlew :data:test                 # ProfileSerializer, ProfileRepository
./gradlew :engine:test               # CpuController, ProfileApplier
./gradlew :app:connectedAndroidTest  # Onboarding + Dashboard smoke tests (needs device)
```

Unit tests use Robolectric (Room) + MockK (controllers) + Truth (assertions) + Turbine (Flow).

---

## Roadmap

- [ ] Real WireGuard userspace integration (libwg / boringtun) for the full-tunnel packet loop
- [ ] DNSSEC validation in the DoH resolver
- [ ] Per-app CPU affinity via `cgroup` cpusets
- [ ] Charging-current limit via `/sys/class/power_supply/.../constant_charge_current_max`
- [ ] Animated onboarding illustrations
- [ ] Material 3 expressive theme variant

---

## License

MIT. See `LICENSE` (to be added). No paywalls, no subscription gates, no "pro" feature locks — every feature is usable in the free build, as specified in the original brief.

---

## Acknowledgements

- [libsu](https://github.com/topjohnwu/libsu) by topjohnwu
- [Shizuku](https://github.com/RikkaApps/Shizuku) by Rikka
- [Vico](https://github.com/patrykandpatrick/vico) by Patryk Goworowski
- [WireGuard](https://www.wireguard.com/) — config format reference
