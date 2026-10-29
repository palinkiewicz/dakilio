# Dakilio

A small, private, open-source mood journal for Android. It's an alternative to Daylio, built with
Jetpack Compose and Material 3 Expressive.

- **Private by design:** no account, and no `INTERNET` permission. Entries stay on the device.
  Imported photos are re-encoded, which strips EXIF data such as GPS location. Nothing goes to
  cloud backup; device-to-device transfer still works.
- **Quick check-in:** a three-step wizard (mood → tags → note and photos). Every step can be
  skipped, but an entry needs at least something.
- **Diaries:** an entry can live in several diaries. Each diary suggests the tag groups that open
  first; the rest stay collapsed.
- **Insights:** streaks, average mood, mood distribution, a 12-week journey map and top tags.
- **Starter content:** the first launch includes sample entries (with photos) to show the app off.
  Remove them in *Settings → Starter content*.

## Building

Requires JDK 17+ with `javac`. The Android Studio JBR works:
`export JAVA_HOME=/opt/android-studio/jbr`.

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | `pl.dakil.dakilio.debug` — **Dakilio (DEBUG)**, amber icon |
| `./gradlew assemblePreview` | `pl.dakil.dakilio.preview` — **Dakilio (PREVIEW)**: R8-minified like release, signed with the debug key, violet icon |
| `./gradlew assembleRelease` | `pl.dakil.dakilio` — **Dakilio**. Signed only if a `keystore.properties` exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| `./gradlew testDebugUnitTest :core:model:test` | Host-side unit tests |

All three variants install side by side.

Two scripts rebuild the bundled assets:
- `python3 -I tools/fetch-icons.py` regenerates the Material Symbols drawables and `DakilioSymbols`
  from `tools/icons.txt`.
- `tools/subset-font.sh` rebuilds the Google Sans Flex subset.

The baseline profile comes from `baselineprofile/BaselineProfileGenerator`. To regenerate it on an
API 33+ device, run `:app:generateBaselineProfile`. If the Gradle runner times out, run the
instrumentation directly and copy the output to `app/src/main/generated/baselineProfiles/`.

## Architecture

The project follows the NowInAndroid multi-module layout. It uses Hilt for dependency injection,
Room for storage, DataStore for settings, Navigation 3 for navigation, and Coil 3 for local images
only.

```
app                   shell: theme, NavDisplay with one back stack per tab, bottom bar
core:model            pure-Kotlin domain models, validation, streaks
core:common           dispatchers, application scope, photo directory
core:database         Room schema (moods are versioned via mood_set), default and sample seed
core:datastore        user settings
core:data             repositories, photo import
core:designsystem     theme, typography, shapes, Material Symbols, expressive primitives
core:ui               mood badges, post card, selectors, icon picker, transitions
core:navigation       per-tab back stacks and the Navigator
feature:*:api / impl  timeline, entry editor, search, insights, more, tags, diaries, settings
baselineprofile       profile generator
```

Mood values are stored normalized to the range 0.0–1.0, so analytics keep working if the mood scale
changes later. The mood colors form a diverging scale that has been validated for color-vision
deficiencies. Dark-mode tones are derived in OKLCH at runtime.

## Licenses

- Code: [GPL-3.0](LICENSE).
- Material Symbols: Apache-2.0.
- Google Sans Flex: [OFL-1.1](third_party/google-sans-flex/OFL.txt).
- Sample photos: CC0, credited in `core/database/src/main/assets/sample/ATTRIBUTION.md`.
