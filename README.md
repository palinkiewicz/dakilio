# Dakilio

A small, private, open-source mood journal for Android. It's an alternative to Daylio, built with
Jetpack Compose and Material 3 Expressive. Requires Android 10 or newer.

## Features

- **Private by design:** no account, and no `INTERNET` permission. Entries stay on the device.
  Imported photos are re-encoded, which strips EXIF data such as GPS location. Nothing goes to
  cloud backup; device-to-device transfer still works.
- **Quick check-in:** a three-step wizard (mood → tags → note and photos). Every step can be
  skipped, but an entry needs at least something. Existing entries open read-only until you tap
  Edit.
- **Moods:** a fixed five-level scale. Each level has one main mood plus any number of extra names
  (say, "Sad" next to "Bad"), and moods can have their own colors.
- **Tags:** tag groups with icons and colors, shown as a Daylio-style icon grid or as chips. Tags
  and groups you no longer use can be archived instead of deleted.
- **Diaries:** each entry lives in one diary. A diary suggests the tag groups that open first; the
  rest stay collapsed.
- **Extras:** habits (a tag and a target of days per week) and keywords (words in a note that
  suggest a tag).
- **Search:** filter by text, mood, tags, diary, date and more, with quick presets such as
  *On this day*.
- **Insights:** streaks, average mood, mood distribution, a 12-week journey map and top tags.
- **App lock:** a PIN or password, optional fingerprint or face unlock, auto-lock and a choice of
  recovery. It guards the app's screens; it doesn't encrypt the data.
- **Backups:** save everything to a `.dakilio` file, optionally password-protected, or import a
  Daylio backup.
- **Languages:** English and Polish.

## Building

Requires JDK 17+ with `javac`. The Android Studio JBR works:
`export JAVA_HOME=/opt/android-studio/jbr`.

| Command | Result |
|---|---|
| `./gradlew assembleDebug` | `pl.dakil.dakilio.debug`: **Dakilio (DEBUG)**, deep blue icon |
| `./gradlew assemblePreview` | `pl.dakil.dakilio.preview`: **Dakilio (PREVIEW)**, R8-minified like release, signed with the debug key, violet icon |
| `./gradlew assembleRelease` | `pl.dakil.dakilio`: **Dakilio**. Signed only if a `keystore.properties` exists (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| `./gradlew testDebugUnitTest :core:model:test :core:daylio:test :core:backup:test :core:security:test` | Host-side unit tests |

All three variants install side by side. Debug and preview builds start with sample entries (with
photos) to show the app off; remove them in *Settings → Starter content*. Release builds start
empty.

Pushing a tag builds the APK on GitHub Actions and attaches it to the release: `v1.2.3` builds a
release APK, `v1.2.3-preview` a preview one.

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
app                   shell: theme, app lock gate, NavDisplay with one back stack per tab, bottom bar
core:model            pure-Kotlin domain models, validation, streaks, habits
core:common           dispatchers, application scope, photo directory
core:database         Room schema and the default seed
core:samples          sample entries and photos (debug and preview builds only)
core:datastore        user settings, search history, app lock settings
core:data             repositories, photo import, app lock state
core:daylio           .daylio backup reader and import planner (pure Kotlin)
core:backup           .dakilio backup format and encryption (pure Kotlin)
core:security         app lock hashing and lockout rules (pure Kotlin)
core:designsystem     theme, typography, shapes, Material Symbols, expressive primitives
core:ui               mood badges, post card, selectors, icon picker, transitions
core:navigation       per-tab back stacks and the Navigator
feature:*:api / impl  timeline, entry, search, insights, more, moods, tags, diaries, settings,
                      importer, backup, archive, intro, habits, keywords, lock
baselineprofile       profile generator
```

The mood scale is fixed at five levels so per-level charts, the color palette and Daylio import
stay consistent; extra mood names are aliases of a level. The default mood colors form a diverging
scale that has been validated for color-vision deficiencies. Dark-mode tones are derived in OKLCH
at runtime.

## Licenses

- Code: [GPL-3.0](LICENSE).
- Material Symbols: Apache-2.0.
- Google Sans Flex: [OFL-1.1](third_party/google-sans-flex/OFL.txt).
- Sample photos: CC0, credited in `core/samples/src/main/assets/sample/ATTRIBUTION.md`.
