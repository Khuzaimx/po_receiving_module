Purchase order receiving module for sellernest warehouses an internal app for receiving purchase orders at a warehouse dock integrated with sellernest. 

## Stack

| Concern | Choice | Reason |
|---|---|---|
| Language | Kotlin | First-class coroutines for the scan/IO pipeline. |
| UI | Jetpack Compose, Material 3 | Declarative state rendering suits a screen whose content changes on every scan. Theming is overridden heavily — see the design system in `ui/theme`. |
| Architecture | MVVM + unidirectional data flow | One ViewModel per screen exposing an immutable `UiState`. Scans are events into a state machine, never direct view mutations. See `core/mvvm`. |
| DI | Hilt | Standard, low ceremony. |
| Networking | Retrofit + OkHttp + kotlinx.serialization | OkHttp interceptors attach `Authorization` and `X-Active-Org` exactly once, centrally — never per call site. See `network/AuthInterceptor`, `network/ActiveOrgInterceptor`. |
| Local storage | Room | The in-progress count draft. Must survive process death, not merely backgrounding. See `data/local`. |
| Background submit | WorkManager | Guaranteed execution with backoff across process death and reboot — what makes the queued submit real rather than best-effort. See `work/WorkRequestFactory`. |
| Camera scanning | CameraX + ML Kit Barcode Scanning | On-device, no network, no per-scan cost. |
| Hardware scanning | Zebra DataWedge intent API + keyboard-wedge fallback | Both feed one shared `onScan` handler. |
| Auth | AppAuth for Android + AndroidX Browser (Custom Tabs) | RFC 8252 compliant. |
| Token storage | EncryptedSharedPreferences (Android Keystore) | Never plain `SharedPreferences`, never external storage. |
| Min SDK | API 26 (Android 8.0) | Covers the rugged fleet (Zebra TC-series, Honeywell CT-series) in practice while allowing modern APIs. |

### Why native, not React Native or Flutter

A deliberate decision, not a default preference:

- **Hardware scanner integration.** Zebra DataWedge communicates by Android
  broadcast intent. Consuming that in a cross-platform framework means writing a
  native module anyway.
- **Scan-to-render latency.** A JS bridge in the scan path is precisely where
  dropped frames and input lag appear, and lag here causes double-scans and
  miscounts.
- **Single platform.** iOS is out of scope; the chief cross-platform argument
  doesn't apply.
- **Camera control.** CameraX exposes torch, zoom, and focus directly — the
  weakest area of cross-platform camera wrappers.

## Design language

An industrial tool, not a consumer app: high-contrast single light theme (no
dark mode), 48–56 dp touch targets, colour used only for state and always
paired with an icon and a text label. See `ui/theme` and
`ui/components/ComponentGalleryScreen` for the full rule set and a live
reference of every state.

## Project structure

```
app/src/main/java/com/sellernest/poreceiving/
  core/mvvm/        the UiState/UiEvent ViewModel convention, plus a worked example
  navigation/        the full §7 screen graph (stubs until each milestone lands)
  network/           Retrofit + OkHttp + kotlinx.serialization, the §9 API contract
  data/local/        Room: draft, draft line, serials, photos, queued submission
  work/              WorkManager scaffolding and backoff configuration
  ui/theme/          the design system tokens
  ui/components/     shared components built on those tokens
```

## Building

Requires a JDK, the Android SDK, and Gradle (via the wrapper — run `gradle
wrapper` once from a local Gradle install, or open the project in Android
Studio, to generate `gradlew`/`gradlew.bat` and the wrapper jar).

```
./gradlew assembleDebug
./gradlew test                        # unit tests
./gradlew connectedDebugAndroidTest    # instrumented tests; requires a device or emulator
```
