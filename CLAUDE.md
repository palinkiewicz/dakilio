# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Dakilio is an open-source, privacy-first Android mood journal: a smaller alternative to Daylio. It uses Kotlin, Jetpack Compose and Material 3 Expressive. Package: `pl.dakil.dakilio`.

The app is past its proof-of-concept stage. `.materials/poc.md` was the original POC brief; it's kept only as history and is no longer the spec. Where it disagrees with the code or this file (its POC-only scope limits, user-defined mood counts), the code and this file win. `.materials/dakilio_early_stage_ideas.md` is background on *why* things are the way they are, not a backlog. `.materials/daylio_format.md` documents the `.daylio` backup format (reverse-engineered) and how it maps onto Dakilio; read it before working on the importer.

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
- `core:database`: the Room schema, exported to `core/database/schemas/`. There are no migrations before the first release: a schema change edits version 1 in place (the exported `1.json` regenerates on build), and existing installs must be reinstalled.
  - `DefaultData` seeds everything inside `RoomDatabase.Callback.onCreate`: the 5 primary moods (ids 1..5 = their level), the "Your Journey" diary, tag groups and, in debug and preview builds only, 12 **sample entries with photos**.
  - The samples (entries, their strings and the photos under `assets/sample/`) live in `core:samples`, which binds the optional `SampleData`. Only `:app`'s debug, preview and `nonMinifiedRelease` (baseline profile) variants depend on it, so release APKs carry none of it and start empty.
  - Sample posts use the reserved ids `DefaultData.SAMPLE_POST_IDS` (1..12). Removal relies on those ids. The seed moves the post id sequence past the range even without samples, and SQLite `AUTOINCREMENT` never reuses ids, so user posts can't collide with them. The sample UI (the More hint, Settings → Starter content) shows only while samples exist.
- `core:data`: repository interfaces and `Local*` implementations, bound in `DataModule`. Feature modules see only models and these interfaces, never entities or DAOs. `PostRepository.posts` is a single `Flow<List<Post>>`. Timeline, search and insights all derive from it in memory on the Default dispatcher; there is no Paging and no FTS.
- `core:daylio` (pure JVM, host-tested): reads `.daylio` backups (`DaylioArchive`), converts note HTML to text (`daylioHtmlToText`), guesses tag icons (`daylioTagIcon`) and maps a backup onto Dakilio's model (`planImport` → `ImportPlan`). Uses kotlinx-serialization-json.
- `core:datastore`: user preferences only (theme, the last-used diary of the editor, the sample-hint flag, tag style, beginner hints, entry text sizes, timeline view options). User content lives in Room.
- `core:designsystem`: the theme, the Google Sans Flex typography (rounded `ROND` axis for display and headline styles), shapes, the symbol catalog, haptics, `MorphShape`/`PolygonShape`, and generic expressive primitives.
- `core:ui`: domain-aware components (`MoodBadge`, `PostCard`, diary selectors, `IconPicker`, `PhotoViewer`, the delete dialogs, `SuggestedGroupCard`, `DaylioBackupCard`), formatters, `NavTransitions`, and `Modifier.sharedContainer`.
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
- **Mood visuals belong to the level.** `MoodVisuals.kt` holds one rest/active MaterialShapes polygon pair and face curvature per level, plus the per-level *light-mode* mark color. Aliases share their level's face. Dark-mode tones are derived in OKLCH at runtime (`deriveAccentColors`).
  - The default palette (crimson, orange, periwinkle, green, teal) is a diverging scale that was validated for colour-vision-deficiency separation in both themes.
  - **Mood colors** work like tag colors: `mood.color_key` stores a `TagColor` palette key. `Mood.colorKey` is the mood's own color; `Mood.color` is the one it shows (own, else its level primary's), resolved in the data layer (`MoodDao.observePrimaryColors()`, also for entries' moods). With no color at all, `moodColors()` falls back to the level's built-in color (`levelColors()`). The seed sets no colors, so a fresh install shows the validated palette.
  - Colors are picked in More → Moods: the edit dialog (name, then a `ColorRow` with "Level color" for a primary or "Use “…” color" for an alias) shows a live face preview.
  - Don't swap it for a red→yellow→green ramp: yellow and green collapse for protanopes.
- **Deleting a tag** first counts its entries (`TagRepository.tagUsage`). An unused tag is just deleted. A used one offers:
  - **archive** (`is_archived`, with an Undo snackbar): old entries keep showing it, while pickers and the customization screen hide it;
  - **delete** for good: it's removed from every entry, and entries left with nothing at all (no mood, text, photos or other tags) are deleted too, since the editor never saves an empty entry. The dialog states both counts.
- **Deleting a tag group** follows the same rules (`TagRepository.groupUsage`). A group none of whose tags are used is just deleted. A used one offers **archive** (with an Undo snackbar) or **delete** for good, which deletes its tags, removes them from every entry and deletes entries left with nothing but tags from that group.
- **Extras** are optional tools that build on the core features. They're the first section of the More screen (there's no Extras sub-screen), so their settings don't crowd the Tags, Moods or Diaries screens. Habits is the only one for now.
  - An extra has **no on/off toggle**: its state is the switch. One that holds nothing is simply unused, and its row says "Not used yet" instead of showing a count.
  - Each extra is its own feature module with its own screen; More depends only on its `api` module. Adding one: a new module, one `MoreItem` in More's Extras section (plus its status in `MoreViewModel`) and one `xxxEntry` in `DakilioApp`.
- **Tag colors** are part of More → Tags (`feature:tags`, not an extra): a default color per tag group, which a tag can override.
  - On the Tags screen, each group card's color button (a dot of the group color, or a palette icon) opens `GroupColorSheet`, which applies a pick at once with a live preview of the group's tags. A tag's own color is a "Color" row in the tag editor sheet ("Use group color", then the palette), saved with the tag. The sheet lives in `TagColorPicker.kt`; the swatches, `ColorDot` and `ColorRow` are shared with the Moods screen from core:ui `ColorSwatches.kt`. The tag chips on the Tags screen show their resolved color.
  - The seed groups (and so the suggested groups) come with a default color each, chosen to fit their meaning and to be the palette's most distinct six: Emotions pink, People orange, Activities violet, Health green, Journey brown, Weather sky (`DefaultData.Group.colorKey`). The suggestion cards show their tags in it.
  - `tag.color_key` and `tag_group.color_key` store a palette key from `TagColor` (core:ui), never a color value, so the palette can be retuned without a migration. Never rename a key; unknown keys mean no color.
  - `Tag.colorKey` is the tag's own color; `Tag.color` is the one it shows (own, else its group's), resolved in the data layer, also for entries' tags (`PostRepository.posts` combines with `TagDao.observeGroupColors()`).
  - The tones come from `deriveAccentColors` (core:ui `color/AccentColors.kt`), which the mood colors use too. Read-only `TagPill`s use the `soft`/`accent` tones. The editor colors only a **selected** tag's fill; unselected tags stay neutral so selection stays the strongest signal in the grid.
- **Habits** (`feature:habits`), an extra: a habit is one tag (unique per tag) plus a target of days per week. Its name is the tag's label.
  - Entries of **every diary** count, and a day counts once however many entries carry the tag. Weeks start on Monday (`Habits.weekStart`, the same as the Insights heatmap). The pure logic lives in `core:model` `Habit.kt` and is tested.
  - `habit.tag_id` is `ON DELETE CASCADE`, so deleting a tag or its group, the intro's group pruning and a Daylio import remove its habit too.
  - **The progress popup:** `EntryEditorViewModel.save()` reads `HabitRepository.weekCounts` for the entry's week before and after saving. `Habits.bumps` keeps the habits whose count went up, so a day already counted never bumps again, and edits count as well as new entries. The bumps go into the `HabitProgressEvents` singleton (a `StateFlow`, so they survive the editor closing). `HabitProgressHost`, placed once in `DakilioApp` above the nav bar, shows them as a self-dismissing card: the old count, then a tick up with a "+1", a filling segment and haptics.
- **The Archived screen** (More → Archived, `feature:archive`) lists archived mood aliases, archived tag groups (with all their tags) and archived tags of active groups. Each can be restored or deleted for good; an archived alias with entries can only be deleted by replacing it. Restoring a tag whose group is archived restores the group too. The delete dialogs (`DeleteTagDialog`, `DeleteGroupDialog`, `DeleteAliasDialog`, `ReplaceAliasDialog`) live in `core:ui` so the Tags, Moods and Archived screens share them.
- **Every entry is in exactly one diary** (`post.diary_id`, `ON DELETE RESTRICT`). The editor picks it with the single-select `DiarySelector` and remembers the last one (`UserSettings.lastEntryDiaryId`).
- **Deleting a diary** is blocked for the last remaining diary. Its entries are either moved to another diary or deleted together with their photo files.
- **Two editor layouts.** New entries go through the mood → tags → note step wizard. Existing entries open `EntryOverview`, a single page (one row of mood faces, then the tags, then the note) for quick edits. Its tags start folded into read-only chips of every selected tag (archived ones included); Edit or a tap on the chips unfolds the groups for that session. Both share `EntryEditorViewModel`, `tagGroupItems` and `NoteFields`.
- **Diaries suggest tag groups.** In the editor, groups suggested by the entry's diary are expanded; the rest are collapsed under "More groups". When none are suggested, all groups are expanded.
  - Every group in the editor is a collapsible panel (`TagPicker.kt`). What the user folds open or shut lasts only for that editor session (`toggledGroups` in `EntryEditorScreen`).
  - The tag style is a user setting (`UserSettings.tagStyle`): a Daylio-style icon grid (the default) or chips. Read-only tag displays (post cards, search) always use chips.
- **Daylio import replaces everything** (More → Import from Daylio, `feature:importer`). It doesn't merge: after a warning and a confirmation, every entry, photo, diary, tag group, tag and mood alias is deleted, and the backup goes into a fresh "Your Journey" diary.
  - The swap is one transaction (`ImportDao.replaceAll`). Photos are copied (re-encoded through `PhotoRepository.importBytes`) before it, and they're deleted again if it fails.
  - The 5 primaries are kept and renamed: a renamed Daylio base mood keeps its name, the others get the seed names. Daylio's custom moods become aliases; archived ones only if entries use them.
  - Daylio stores no archived state for groups, so a group whose tags are all archived is imported as an archived group.
  - `sqlite_sequence` is never reset, so imported entries can't take the reserved sample ids.
  - Daylio names its assets `/assets/...`, which Android 14+ `ZipFile` rejects by default. `allowRootedZipEntries()` installs a `ZipPathValidator` that allows the leading slash but still rejects `..`.
  - Voice notes and favourites are skipped and counted in the preview.
  - **Getting the file in:** the invisible `BackupReceiverActivity` accepts `SEND` and `VIEW` of `.daylio` files and forwards the URI (with the read grant) to `MainActivity` (`singleTask`) in Dakilio's own task. The filters are deliberately not on `MainActivity`: Daylio's backup screen shares in a way that makes Android ignore `singleTask` and start a second copy inside Daylio's task. The filters mirror Daylio's own manifest: the types `application/daylio` and `application/octet-stream` (Android doesn't know the extension), plus `.daylio` path patterns for "Open with". Daylio itself registers no `SEND` target. `MainActivity` drops the URI into the `IncomingBackups` singleton. While a file is waiting, `DakilioApp` opens More → Import, and `DaylioImportViewModel` takes the file, unless an import is already running. When Daylio is installed (a `<queries>` entry in core:ui's manifest; `rememberDaylioInstalled()`/`openDaylioBackup()` live in core:ui), the Pick step offers a shortcut (`DaylioBackupCard`) to Daylio's `BackupAdvancedActivity`, falling back to its launcher screen.
- **First-run intro** (`feature:intro`, hosted by `app/ui/IntroFlow.kt` in its own Nav3 back stack instead of `DakilioApp`): language → Daylio (only when installed) → tag groups.
  - **Nothing may open the database before the language step is done**, because `DefaultData.seed` writes localized names on first open. That's why `sweepOrphans()` skips a database that doesn't exist yet. The language list has only English for now; applying a pick (`LocaleManager`) is a TODO in `LanguageStep`.
  - The groups step shows `DefaultData.suggestedGroups()` (the seed groups, read from resources without the DB), all ticked. "Get started" calls `OnboardingRepository.keepStarterGroups`, which deletes the unpicked seed groups by their fixed ids with the usual delete-for-good rule (their tags leave the sample entries).
  - The Daylio step offers `DaylioBackupCard`, or a file picked by hand; both lead to the normal import screen (`DaylioImportKey`) pushed onto the intro's stack. A finished import ends the intro. A `.daylio` file shared or opened at any point during the intro pushes the import screen too (`DakilioApp`'s More → Import routing isn't composed yet).
  - `OnboardingRepository.shouldShowIntro()`: not if finished (`intro_done`); yes if it was left halfway (`intro_started`); otherwise only if the database doesn't exist yet, so installs from before the intro never see it (its group step would delete their groups).
  - The same suggestions are offered later in More → Tags (an app bar action and the empty state, `SuggestedGroupsSheet`). One counts as added while an active group has its name, and adding inserts a new group with fresh ids.
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
- **Timeline view options** live in a menu (`TimelineViewMenu`) that grows out of the tune button in the timeline's app bar: preview size (`PreviewLength`: 4/8/16 note lines or full), compact icon-only tags and the timeline text size, which is the same preference as Settings → Entry text size. They reach every `PostCard` (timeline and search) through `LocalEntryDisplay`, provided in `MainActivity`. In search, a note whose first match is past its start is shown as an excerpt from just before it (the pure `noteExcerpt()`).
- **Search** (`feature:search`) has one filter model, `SearchFilters` (`PostSearch.kt`, pure and tested): text, mood levels, tags (any/all), diaries, a `Period`, weekdays and `Has` (photos, text, long note). Every way into a filter sets those same fields, so all of them show and can be edited in the facet bar (Mood · Tags · Diary · When · Has, each with a live sheet in `FilterSheets.kt`):
  - Before anything is typed, `SearchIdle` offers recent searches, **lenses** (`Lenses.kt`: presets such as On this day, Bright days or Tough days, hidden when they find nothing; Shuffle opens a random entry over a month old) and the frequent or forgotten tags.
  - Typing `#…` swaps the facet bar for matching tags; picking one removes the word and adds a tag filter. With results, "Narrow down" offers the most common tags that would actually narrow them.
  - Period presets stay relative (`Last7Days`, `OnThisDay`, …) so a saved search keeps its meaning.
  - **Recent searches** stay on the device (`SearchHistoryDataSource`, max 8). Core stores them as opaque strings; search encodes `SearchFilters` as JSON, and unreadable entries or missing tags and diaries are dropped when they're read. A search is saved on the IME Search action and when a result is opened, but not when it's only a lens.
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
- Keep new logic Android-free where possible so it can be host-tested. The existing tests cover the timeline builder, the insights calculator, search, search excerpts, streaks, habits, entry validation and the Daylio reader and planner.
