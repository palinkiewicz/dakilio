# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Dakilio is an open-source, privacy-first Android mood journal: a smaller alternative to Daylio. It uses Kotlin, Jetpack Compose and Material 3 Expressive. Package: `pl.dakil.dakilio`.

`.materials/poc.md` is the product spec and the source of truth. `.materials/dakilio_early_stage_ideas.md` is background on *why* things are the way they are, not a backlog.

## Commands

The system JDK on this machine is a JRE without `javac`, so always use Android Studio's JDK:
`export JAVA_HOME=/opt/android-studio/jbr`.

- Compile while iterating: `./gradlew :feature:<name>:impl:compileDebugKotlin` (or `:app:assembleDebug`).
- All unit tests: `./gradlew testDebugUnitTest :core:model:test`. `core:model` is a JVM module, so its task is `test`, not `testDebugUnitTest`.
- A single test class: `./gradlew :feature:timeline:impl:testDebugUnitTest --tests "pl.dakil.dakilio.feature.timeline.impl.TimelineBuilderTest"`.
- Lint: `./gradlew :app:lintDebug`.
- Build types, which all install side by side:

  | Build type | App id | Launcher label | Notes |
  |---|---|---|---|
  | `assembleDebug` | `pl.dakil.dakilio.debug` | "Dakilio (DEBUG)" | — |
  | `assemblePreview` | `pl.dakil.dakilio.preview` | "Dakilio (PREVIEW)" | R8 like release, signed with the debug key. Use it to smoke-test minification. |
  | `assembleRelease` | `pl.dakil.dakilio` | "Dakilio" | Signed only when a `keystore.properties` exists; otherwise unsigned. |

  Labels come from the `appName` manifest placeholder in `app/build.gradle.kts`.
- Emulator verification:
  - Use AVD `transport-test` (API 35, 1080×2400) and address it with `adb -s emulator-5554`.
  - Launch with `pl.dakil.dakilio.debug/pl.dakil.dakilio.MainActivity`.
  - Debug builds allow `run-as pl.dakil.dakilio.debug` (for example, `ls files/photos`).
- Asset pipelines (both are reproducible; don't hand-edit their outputs):
  - `python3 -I tools/fetch-icons.py` regenerates `core/designsystem/res/drawable/sym_*.xml` and the generated `DakilioSymbols.kt` from `tools/icons.txt`. To add an icon, add a line to `icons.txt` and re-run the script.
  - `tools/subset-font.sh` rebuilds the Google Sans Flex subset (axes `wght` and `ROND`).
- Baseline profile: `:app:generateBaselineProfile` times out under Gradle's runner on the emulator. Instead:
  1. Install `app-nonMinifiedRelease.apk` and the `baselineprofile` test APK.
  2. Run `adb shell am instrument -w -e targetAppId pl.dakil.dakilio pl.dakil.dakilio.baselineprofile/androidx.test.runner.AndroidJUnitRunner`.
  3. Copy the pulled profile into `app/src/main/generated/baselineProfiles/` as both `baseline-prof.txt` and `startup-prof.txt`.
- If AAPT2 fails with "Daemon startup failed / spawn helper", a Gradle daemon is running on a JDK that was updated on disk. Run `./gradlew --stop`.

## Build setup

- Uses AGP 9.5 with built-in Kotlin, so modules never apply `org.jetbrains.kotlin.android`. Also uses Kotlin 2.4, KSP 2.3, Hilt and Gradle 9.8.
- Convention plugins live in `build-logic/convention`: `dakilio.android.{application,library,feature,room}`, `dakilio.hilt` and `dakilio.jvm.library`. Shared Android/Kotlin config, Compose setup and opt-ins (Material 3 Expressive, shared transitions, …) are applied there. Change build config there, not in individual modules.
- `material3` is pinned to `1.5.0-beta02` in `gradle/libs.versions.toml`, ahead of the BOM, because the stable line hides the Expressive APIs (`MaterialExpressiveTheme`, `MaterialShapes`, `ButtonGroup`, flexible app bars, `ShortNavigationBar`, `SegmentedListItem`).
- `compose-stability.conf` marks `core.model` types and read-only collections as stable. Keep models immutable.
- Unit tests include Android resources (AGP's Compose Preview tasks require it), and `failOnNoDiscoveredTests` is off for modules without tests.
- Keep dependencies minimal. There is deliberately no network stack and no `INTERNET` permission. Coil is used without its network module. Icons come from bundled Material Symbols, not `material-icons-extended`. There are no chart or reorder libraries; charts are drawn with Canvas.

## Architecture

The layout follows NowInAndroid: `app` → `feature:*:impl` → `feature:*:api` / `core:*`.

- `core:model` (pure JVM): domain models, plus pure logic that has to be host-testable (`EntryValidation`, `Streaks`, the mood helpers `primaries()`/`byLevel()`).
- `core:database`: the Room schema, exported to `core/database/schemas/`. Bump the version and add a migration for any schema change.
  - `DefaultData` seeds everything inside `RoomDatabase.Callback.onCreate`: the 5 primary moods (ids 1..5 = their level), the "Your Journey" diary, tag groups and 12 **sample entries with photos** copied from `assets/sample/`.
  - Sample posts use the reserved ids `DefaultData.SAMPLE_POST_IDS` (1..12). Removal relies on those ids. SQLite `AUTOINCREMENT` never reuses ids, so user posts can't collide with them.
- `core:data`: repository interfaces and `Local*` implementations, bound in `DataModule`. Feature modules see only models and these interfaces, never entities or DAOs. `PostRepository.posts` is a single `Flow<List<Post>>`. Timeline, search and insights all derive from it in memory on the Default dispatcher; there is no Paging and no FTS.
- `core:datastore`: user preferences only (theme, the last-used diaries of the editor, the sample-hint flag, tag style, beginner hints). User content lives in Room.
- `core:designsystem`: the theme, the Google Sans Flex typography (rounded `ROND` axis for display and headline styles), shapes, the symbol catalog, haptics, `MorphShape`/`PolygonShape`, and generic expressive primitives.
- `core:ui`: domain-aware components (`MoodBadge`, `PostCard`, diary selectors, `IconPicker`, `PhotoViewer`), formatters, `NavTransitions`, and `Modifier.sharedContainer`.
- `core:navigation`: Navigation 3 with **one back stack per bottom-bar tab** (`NavigationState` and `Navigator`). Back from another tab's root returns to the Timeline tab. The entries of each stack are decorated separately so state and ViewModels survive tab switches.
- Feature `api` modules contain only `@Serializable` NavKeys. `impl` modules expose `EntryProviderScope<NavKey>.xxxEntry(navigator)`, which `app/ui/DakilioApp.kt` wires up. A feature may depend only on other features' `api` modules.
- ViewModels use `hiltViewModel()`. Keyed screens (the entry editor and the diary editor) use Hilt assisted injection with their NavKey.

### Non-obvious decisions

- **Moods: a fixed 5-level scale with aliases** (like Daylio). The level count is a constant (`MOOD_LEVELS`), deliberately not configurable: a variable scale would break per-level charts, the validated palette and `.daylio` import.
  - Each level has exactly one primary mood (`is_primary`), which can be renamed but never removed, plus any number of aliases: other names for the same level ("Sad" next to "Bad").
  - Entries point at the exact mood, so they keep the chosen name. Analytics and the search filter group by `level`; `Mood.value` is computed as `(level - 1) / 4` for averages.
  - Deleting an alias with no entries just deletes it. With entries, the user either **archives** it (`is_archived`: hidden from pickers, old entries keep it) or **replaces** it (its entries move to another mood, then it's deleted). `post.mood_id` is `ON DELETE RESTRICT`, so a mood is never deleted under entries.
  - Picking: a level with aliases shows a dot (`MoodBadge(hasAliases)`). In the editors, the first tap on a face selects the level's primary, and tapping the selected face again opens `MoodAliasOverlay` (core:ui), a dimmed full-screen stack of that level's moods. The rules are the pure `moodTap()` in `MoodPicking.kt`. The wizard doesn't auto-advance on a first tap of a level with aliases. The timeline's check-in card has nothing selected yet, so it opens the overlay on the first tap.
  - Deselecting: a level without aliases clears when its selected face is tapped again. Inside the overlay, tapping the current mood keeps it, and an explicit clear button after the faces deselects.
  - Customization lives in `feature:moods` (More → Moods).
- **Mood visuals belong to the level.** `MoodVisuals.kt` holds one rest/active MaterialShapes polygon pair and face curvature per level, plus the per-level *light-mode* mark color. Aliases share their level's face and color. Dark-mode tones are derived in OKLCH at runtime (`deriveMoodColors`).
  - The default palette (crimson, orange, periwinkle, green, teal) is a diverging scale that was validated for colour-vision-deficiency separation in both themes.
  - Don't swap it for a red→yellow→green ramp: yellow and green collapse for protanopes.
- **Deleting tags or groups archives them** (`is_archived`), with an Undo snackbar. Old entries keep showing archived tags; pickers and the customization screen hide them.
- **Deleting a diary** is blocked for the last remaining diary. Entries that belong only to the deleted diary are either moved to another diary or deleted together with their photo files.
- **Two editor layouts.** New entries go through the mood → tags → note step wizard. Existing entries open `EntryOverview`, a single page (one row of mood faces, then the tags, then the note) for quick edits. Both share `EntryEditorViewModel`, `tagGroupItems` and `NoteFields`.
- **Diaries suggest tag groups.** In the editor, groups suggested by any selected diary are expanded; the rest are collapsed under "More groups". When none are suggested, all groups are expanded.
  - Every group in the editor is a collapsible panel (`TagPicker.kt`). What the user folds open or shut lasts only for that editor session (`toggledGroups` in `EntryEditorScreen`).
  - The tag style is a user setting (`UserSettings.tagStyle`): a Daylio-style icon grid (the default) or chips. Read-only tag displays (post cards, search) always use chips.
- **Photos** come through the system Photo Picker, so no permission is needed. `LocalPhotoRepository.import` re-encodes each image to at most 2560 px JPEG, which strips EXIF (including GPS), into `filesDir/photos`.
  - Files imported during an editor session are deleted if the draft is abandoned.
  - `sweepOrphans()` runs at startup.
- **Post time** is stored as an epoch timestamp plus `utc_offset_min` and a `local_date` epoch day, so an entry stays on the day it was written even across time zones.
- **The timeline layout** is a pure function, `buildTimeline()`, with unit tests. It covers:
  - day headers;
  - today's check-in card;
  - "No entries" rows;
  - runs of 3 or more empty days collapsed into an expandable quiet stretch;
  - "Also this day" chips for entries in other diaries, only under days that have entries in the selected diary (and today);
  - days with entries only in other diaries are compact empty rows (and can join quiet stretches), with a small read-only `DiaryHint` of those diaries' icons.
- **Insights** follow the `dataviz` skill's rules: stat tiles, a stacked bar with 2dp gaps, legends that carry the mood faces, and single-hue bars. `InsightsCalculator` is pure and tested. Streaks ignore the selected range.
- **Dates use the app's language** (`ui_format_language`, currently `en`) combined with the device region, so they never mix with untranslated UI.
- **Container transforms:** the FAB and the post cards grow into the editor, and the search icon grows into Search. This works through `sharedContainer(key)` together with `NavTransitions.sharedContainer` entry metadata. Tabs use a fade-through transition (`NavTransitions.topLevel`).
- **Privacy:** `data_extraction_rules.xml` excludes everything from cloud backup but allows device-to-device transfer.

## Conventions

- All user-visible text goes in each module's `res/values/strings.xml`, including seed data. The UI is English-only for now.
- Use Material 3 Expressive throughout:
  - spring motion through `MaterialTheme.motionScheme`;
  - shape morphing on selection or press (`pressScale`, `MorphShape`);
  - segmented groups (`SegmentedShapes`, `segmentedItemColors()`);
  - `raisedToggleColors()` for toggles on container-colored backgrounds;
  - haptics through `rememberHaptics()`.
- **No endless decorative animation** (`rememberInfiniteTransition`, `infiniteRepeatable`, perpetual spins or pulses). It keeps the display at full refresh rate and stops LTPO screens from dropping to 1 Hz. Motion should be a short, settling response to a state change or input. Loading indicators are the only exception, because they disappear once the content is ready.
- **Beginner hints:** any line that teaches how to use the UI (as opposed to describing state) goes through `Hint { }` or `hintOrNull()` from `core/ui/Hints.kt`, so the "Show beginner hints" setting (`UserSettings.showHints`, provided as `LocalShowHints` in `MainActivity`) covers it.
  - Never hints: empty states, counts, validation, dialog warnings, setting descriptions, placeholders and screen taglines.
  - Layouts must look right without the hint: put spacing on the neighbours, not on the hint.
- Use `DakilioIcon(DakilioSymbols.X, filled = selected)` for icons. Tags and diaries store symbol keys, which `symbolForKey()` resolves.
- Keep new logic Android-free where possible so it can be host-tested. The existing tests cover the timeline builder, the insights calculator, search, streaks and entry validation.
