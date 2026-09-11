Purchase order receiving module for sellernest. 



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
