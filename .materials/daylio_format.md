# The `.daylio` backup format: a guide for the Dakilio importer

## Context
Dakilio should be able to import a Daylio backup, which is the "Daylio Migration Bridge" in `.materials/dakilio_early_stage_ideas.md`.

This guide was reverse-engineered on 2026-11-01 from two real backups that exercise most of Daylio's features (Daylio Android, backup `version: 15`, Android 15). The second backup was taken after archiving a custom mood, archiving one activity and deleting another, to pin down how those states are stored. The sample file lives only on a contributor's machine; it isn't in the repo.

The format is undocumented, so anything not seen in the sample is marked **(unverified)**. Update this file whenever another backup confirms or contradicts something here. It's the reference for the importer.

---

## 1. The container
A `.daylio` file is a plain **ZIP** archive (deflate). The sample has three members:

| ZIP member | Size | What it is |
|---|---|---|
| `backup.daylio` | 19 KB | **Base64 text** that decodes to **UTF-8 JSON**: the entire database |
| `/assets/photos/2026/11/3b14e2dacf698a7ac4bbc336d9c27410` | 74 KB | a photo: a JPEG (JFIF) with no file extension |
| `/assets/audio/2026/11/7ff6cf360f5ab334dfd7a0690d5063f3` | 47 KB | a voice note: an MP4/M4A (`ftypmp42`) |

Notes on the container:
- **The asset names start with a slash** (`/assets/...`). `java.util.zip.ZipFile.getEntry("/assets/…")` works with the exact name. **But on Android 14+, for apps targeting SDK 34+, the system zip-path validator makes `ZipFile`'s constructor throw `ZipException` for names starting with `/`.** The importer installs its own `ZipPathValidator` callback that still rejects `..` but allows the leading slash (`allowRootedZipEntries()` in `LocalDaylioImportRepository`). Host (JVM) tests can't catch this. Never use a member name as an output path (zip-slip); we only ever *look entries up*, we never extract by name.
- **Asset folders are `YYYY/MM` of the asset's own `createdAt`**, with a 1-based month, *not* the entry's date. The photo of the 29 Oct entry lives under `2026/11`. Find assets by **file name = `checksum`**, not by rebuilding the path.
- **The `checksum` is not a reliable content hash.** It equals the MD5 of the audio file, but *not* of the photo, which Daylio probably hashed before recompressing it. Use it only as the lookup key, and never verify against it.
- **The Base64 is MIME-style:** 76-character lines separated by `\n`. In Kotlin, decode with `java.util.Base64.getMimeDecoder()`, which is fine on minSdk 29.

## 2. Top-level JSON
```
version 15                     backup schema version: gate parsing on it
customMoods[]                  the mood scale (5 groups + custom moods)       → IMPORT
tag_groups[]                   activity groups                                → IMPORT
tags[]                         activities                                     → IMPORT
dayEntries[]                   the entries                                    → IMPORT
assets[]                       photo/audio index referenced by entries         → IMPORT photos, skip audio
metadata{}                     counts, created_at, platform…                  → use for the preview / sanity checks
moodIconsPackId, preferredMoodIconsIdsForMoodIdsForIconsPack,
moodIconsDefaultFreePackId     Daylio icon-pack ids                           → ignore
achievements[], daysInRowLongestChain, prefs[], reminders[],
goals[], goalEntries[], goalSuccessWeeks[], milestones[], isReminderOn
                               Daylio-internal state                           → ignore
writingTemplates[]             9 note templates (HTML bodies)                 → ignore for now (possible future feature)
```
- `metadata.number_of_photos` is **2**, so it counts the voice note too. It counts *assets*, not photos.
- `prefs[].PRESELECTED_LANG = "en"` is the language Daylio uses for the predefined mood names. It's useful in §3.

## 3. `customMoods[]`: the mood scale
```json
{"id":1,"custom_name":"","mood_group_id":1,"mood_group_order":0,"icon_id":1054,"predefined_name_id":1,"state":0,"createdAt":…}
{"id":6,"custom_name":"rad2","mood_group_id":1,"mood_group_order":1,"icon_id":1015,"predefined_name_id":-1,"state":0,…}
{"id":2,"custom_name":"gooooood","mood_group_id":2,"mood_group_order":0,"predefined_name_id":2,…}
```

| Field | Meaning |
|---|---|
| `id` | mood id; `dayEntries[].mood` points here. The ids are **not** levels: id 6 is a custom "rad". |
| `mood_group_id` | **the level, inverted: 1 = rad (best) … 5 = awful (worst)**. Dakilio's level is `6 - mood_group_id`. |
| `mood_group_order` | the order within the group. `0` is the group's base mood. |
| `predefined_name_id` | 1..5 for the five built-in moods (rad, good, meh, bad, awful); **-1 for custom moods** |
| `custom_name` | **empty means "use the predefined name"**, translated by Daylio. Non-empty means the user renamed the base mood (`"gooooood"`), or it's a custom mood's name (`"rad2"`). |
| `icon_id`, plus the icon-pack ids at the top level | Daylio's own artwork. **Ignore.** Dakilio draws faces per level. |
| `state` | **0 = active, 2 = archived (verified).** `1` has not been seen for moods. Treat any non-zero value as archived. Note that tags use `1` for archived (§4). |

**Mapping onto Dakilio's fixed-5 model** (primaries plus aliases) is a direct fit:
- **Base mood of a group:** the one with `predefined_name_id != -1`, or `mood_group_order == 0`. It maps onto Dakilio's **primary** at `level = 6 - mood_group_id`.
  - With an empty `custom_name`, keep Dakilio's own primary name.
  - With a set `custom_name`, offer to rename Dakilio's primary to it. Default: yes, because it's the user's own wording ("gooooood").
- **Custom moods** (`predefined_name_id == -1`) become **aliases** on their level. Order them by `mood_group_order`, and reuse an existing alias that has the same name (ignoring case).
- **Archived moods** (non-zero `state`) stay in the backup, and entries keep referencing them; in the sample, entry 3 still uses the archived "rad2". Import such a mood as an **archived** alias if any entry uses it, and skip it otherwise.

## 4. `tag_groups[]` and `tags[]`: activities
```json
{"id":1,"name":"Emotions","is_expanded":true,"order":1,"id_predefined":14}
{"id":4,"name":"Custom Group","is_expanded":true,"order":4,"id_predefined":-1}
{"id":23,"name":"Custom activity #1","createdAt":…,"icon":173,"order":23,"state":0,"id_tag_group":4}
```
- **Groups:**
  - `name` and `order` map onto Dakilio's `tag_group.name` and `position`.
  - `id_predefined` (−1 for custom groups) and `is_expanded` can be ignored.
  - Groups have **no `state` field** in the backups seen so far, so there's no archived state to read. Dakilio archives an imported group when it has tags and **all of them are archived**; otherwise it would show up as an empty group in the pickers.
  - **Merge** with an existing group of the same name, ignoring case. The sample's Emotions and Health groups exist in Dakilio's seed data too.
- **Tags:**
  - `name` → `label`, `order` → `position`, `id_tag_group` → the group.
  - `state`: **0 = active, 1 = archived (verified).** An archived tag stays in `tags[]`, entries keep referencing it, and it maps to `is_archived = 1`. Treat any other non-zero value as archived too.
  - **Deleting** a tag in Daylio removes it from `tags[]` *and* strips its id from every entry's `tags` list (verified), so the importer never sees it. Still skip unknown tag ids defensively (see §8).
  - **Merge** with an existing tag of the same name in the same group. Matched tags keep Dakilio's icon.
  - `icon` is a Daylio icon id (for example 26143, a bunch of party balloons; 67, a runner). Ids run from 1 to about 10 million with large gaps. Mood `icon_id`s (1054, 1015, …) are a separate set of face artwork. The id-to-name list was taken from the Daylio app itself.
  - Dakilio never bundles that artwork. `daylioTagIcon` (core:daylio `DaylioTagIcons.kt`) maps about 400 of those ids onto Material Symbols from our own picker (running → `directions_run`, rain → `rainy`, user_group_man_man → `groups`, …). Ids without a clear counterpart (letters, numbers, arrows, brands, most of the niche artwork) fall back to a keyword table over the English label (sleep → `bedtime`, walk → `directions_walk`, …), then to `label`. Both tables are pure and tested.
- **Older backups** may lack `tag_groups` or `id_tag_group` **(unverified)**. In that case, put everything in one "Daylio" group.

## 5. `dayEntries[]`: the entries
```json
{"id":4,"minute":0,"hour":20,"day":29,"month":9,"year":2026,
 "datetime":1793300400000,"timeZoneOffset":3600000,
 "mood":1,"note":"This one has an image","note_title":"Sample title 4",
 "tags":[2,17],"assets":[1],"isFavorite":false}
```

| Field | Meaning | Dakilio |
|---|---|---|
| `datetime` | epoch **milliseconds** (UTC) | `post.timestamp` |
| `timeZoneOffset` | offset in **milliseconds** (+1 h = 3600000) | `utc_offset_min = timeZoneOffset / 60000` |
| `year`, `month`, `day`, `hour`, `minute` | the local wall clock. **`month` is 0-based** (`9` = October). | `local_date`, read from these fields |
| `mood` | `customMoods.id`. Daylio always requires a mood. | the mapped mood id |
| `note_title` | plain text; may be `""` | `title`; empty becomes null |
| `note` | **HTML** (see §6) | `note`, converted to plain text |
| `tags` | `tags.id` list | the mapped tag ids |
| `assets` | `assets.id` list (photos *and* audio) | photos only |
| `isFavorite` | a favourite flag | nothing in Dakilio; skip for now (it could become a "Favorite" tag later) |

- **Times:** in all 6 sample entries, `datetime + timeZoneOffset` matches the wall-clock fields exactly once the month is read as 0-based. `day 1, month 10` is 1 Nov 13:27 +01:00. Take `timestamp` and `offset` straight from the backup and **do not recompute the offset from the phone's time zone**. Today's `LocalPostRepository.savePost()` does recompute it, so the importer needs its own insert path.
- **Multiple entries per day** are normal in Daylio and fit Dakilio's model.
- **Diaries:** Daylio has none, so every imported entry goes into one diary the user picks. The default is a new diary called "Daylio"; the user can choose an existing one instead.
- **Empty entries can't happen**, because every Daylio entry has a mood. So `EntryValidation` always holds.

## 6. Notes are HTML
The tags seen across notes and templates are `b`, `i`, `u`, `strike`, `br`, `div`, `ol`, `ul` and `li`, with inline `style=` attributes. No entities appeared, but `&amp;`, `&lt;`, `&gt;`, `&quot;`, `&#39;` and `&nbsp;` should be expected **(unverified)**.

Emoji arrive as raw UTF-8 (🥀🔥🥺) and pass through unchanged.

Dakilio notes are plain text, so the importer converts the HTML with a small, pure, host-testable function rather than Android's `Html`:
- `<br>` and block ends (`</div>`, `</p>`, `</li>`) become newlines, and runs of 3 or more newlines collapse to 2;
- `<ul><li>` becomes `• item`, and `<ol><li>` becomes `1. item`, `2. item`, counting per list;
- every other tag is dropped (bold, italic, underline and strike are lost), and entities are decoded;
- the result is trimmed.

The formatted sample note becomes:
```
This one will be very formatted...

Bold

Some italic and bold

1. list item
2. List item another
• Bullet
• Bullet another and emoji 🥀🥀🔥🔥🥺🥺
Underlined

Strike through
```
If Dakilio later gets Markdown notes, the same converter can emit `**bold**`, `*italic*` and `~~strike~~` instead. That's a one-function change.

## 7. `assets[]`: photos and audio
```json
{"id":1,"checksum":"3b14e2dacf698a7ac4bbc336d9c27410","type":1,"createdAt":…,"createdAtOffset":3600000,
 "android_metadata":"{\"Name\":\"81532.jpg\",\"LastModified\":…,\"Orientation\":0}"}
{"id":2,"checksum":"7ff6cf360f5ab334dfd7a0690d5063f3","type":2,…,
 "android_metadata":"{\"Name\":\"20261101_133223.mp4\",…,\"Duration\":2391}"}
```
- **Type:** `type 1` is a photo (JPEG) and `type 2` is a voice note (MP4/M4A, `Duration` in ms). Audio is **skipped**, and the preview reports how many voice notes are skipped.
- **`android_metadata` is a JSON string inside JSON** and needs parsing twice. `Orientation` is a rotation in degrees (0 in the sample) **(unverified for other values)**. Apply it if the decoder doesn't already, because ImageDecoder only honours EXIF orientation, and Daylio may have stripped EXIF.
- **Photos go through Dakilio's existing pipeline** (decode, downscale to 2560 px, re-encode as JPEG, EXIF dropped). `PhotoRepository.import(uri)` only accepts a `Uri`, so it needs an overload that takes an `InputStream` or `ImageDecoder.Source` (for example `ByteBuffer`).
- **Missing assets:** an asset listed in JSON but missing from the ZIP is skipped and counted, and the entry is imported without it.

## 8. Pitfalls checklist

| # | Pitfall | Handling |
|---|---|---|
| 1 | Mood groups are inverted (1 = best) | `level = 6 - mood_group_id`; clamp, and reject values outside 1..5 |
| 2 | `month` is 0-based | `LocalDate.of(year, month + 1, day)`, cross-checked against `datetime + offset` |
| 3 | The original offsets must be kept | a dedicated bulk insert, not `savePost()` |
| 4 | Asset paths use the asset's date, not the entry's | look assets up by file name = `checksum` |
| 5 | The `checksum` isn't always the content's MD5 | never verify it |
| 6 | MIME Base64 with line breaks | `Base64.getMimeDecoder()` |
| 7 | Notes are HTML | a pure converter to plain text (§6) |
| 8 | An empty `custom_name` | means the predefined name; keep Dakilio's own primary name |
| 9 | Names overlap with seed data (Emotions, Health…) | merge groups and tags by name, ignoring case |
| 10 | Re-importing the same file | duplicates. Add a nullable `post.external_id`, such as `"daylio:<datetime>:<entryId>"`, unique when not null, and skip what's already there. Schema v1 can still change pre-release. |
| 11 | Untrusted file (ZIP bomb, huge JSON, absolute names) | read only the expected members; cap `backup.daylio` and the decoded JSON (for example 50 MB) and each photo; never write by member name; parse leniently, ignoring unknown keys; validate ranges |
| 12 | Large backups (hundreds of photos) | copy the picked document to `cacheDir` once and open it with `ZipFile` for random access; import photos one at a time on IO with progress; run everything in one DB transaction at the end, deleting copied photos if it fails |
| 13 | Other backup versions | gate on `version`; tolerate missing optional sections (`tag_groups`, `assets`) **(unverified)** |
| 14 | `state` means different things per type: moods use 2 for archived, tags use 1 | treat non-zero as archived for both; import archived moods only if entries reference them |
| 16 | An entry references a tag or mood id that isn't defined (a corrupt file or another version; real deletions clean up after themselves) | skip that reference, count it in the preview, and keep the entry. An entry whose mood is missing still has to get *some* mood, so fall back to the level's primary if the level is known, or skip the entry and count it |
| 15 | Voice notes and favourites have no Dakilio counterpart | skip them, and show both counts in the preview |

## 9. Suggested shape of the importer (for the later implementation plan)
> **Implemented as a replace, not a merge.** The importer (`core:daylio`, `ImportDao.replaceAll`, `feature:importer`) wipes all user content and imports the backup into a fresh "Your Journey" diary. So the merge-by-name rules (§3 renaming offer, §4 merging, pitfall 9), the target-diary choice (§5) and the `external_id` dedupe (pitfall 10) don't apply. See CLAUDE.md, "Daylio import replaces everything".

- **`core:daylio`** (pure JVM, host-tested), containing:
  - `DaylioBackup` data classes, parsed with **kotlinx-serialization-json**. It's a new, small dependency: only `-core` is in the project today. Configure it with `ignoreUnknownKeys`.
  - `readBackup(zipBytes or File)`: ZIP → Base64 → JSON → model, plus asset lookup by checksum.
  - `planImport(backup, existing moods/tags/groups)`: produces a pure **ImportPlan** (moods to rename or create, groups and tags to create or reuse, entries ready to insert, skipped counts).
  - `daylioHtmlToText()`.
  - Tests use a small handcrafted fixture JSON. The real sample could be added under `src/test/resources` if you're happy to commit it.
- **`core:data`:** `ImportRepository.import(plan, diaryId, photos)` does a bulk insert with the original timestamps and offsets, in one transaction, and adds the `external_id` dedupe.
- **`feature:importer`** (More → Import from Daylio), in three steps:
  1. pick the file (the system picker, `ACTION_OPEN_DOCUMENT` with `*/*`, checking the extension);
  2. **preview**: the date range, entry, mood, alias, tag and group counts, photos, skipped voice notes, the target diary and the "rename primaries" toggle;
  3. **progress** (finite, so no endless animation), then a result summary.

## 10. Sample-specific facts, useful as test expectations
These describe **backup #2**, the file currently on disk:
- 6 entries from 27 Oct to 1 Nov 2026, all at +01:00, with one photo and one voice note.
- 6 moods: the 5 base moods (good renamed to "gooooood") plus custom "rad2" on rad, which is **archived (`state: 2`)**. Entry 3 uses "rad2", so it should land as an archived level-5 alias.
- 4 groups and **23** tags: 22 predefined Daylio tags, plus "Custom activity #1" in "Custom Group", which is **archived (`state: 1`)** and used by entries 2 and 3. "Custom activity #2" was deleted, so it's absent.
- Entry 5 has mood 5 (awful), tags `[4, 20]` and only the voice note, which is skipped. So it imports with mood, tags and text but no media.
- Entry 6 has the heavy HTML note, no tags, and title "Sample title 5", which duplicates entry 5's title.

Backup #1 differed only in this: rad2 and Custom activity #1 were active, Custom activity #2 (id 24) existed and was used by entries 2 and 5, and entry 5's mood was 3 (meh).

## 11. Open questions (still unverified)
Worth covering with the next test backup:
- What deleting a mood does. Does Daylio force reassigning its entries first, or keep it with another `state`?
- Archiving and deleting a whole tag group: is there a group `state`, and what happens to its tags?
- Whether `state: 1` (or other values) ever appears for moods.
- `isFavorite: true`: is it just a flag, or more?
- Older backup versions (< 15): missing sections such as `tag_groups`, renamed fields.
- `android_metadata.Orientation` values other than 0.
- How notes escape `&`, `<` and `>` (HTML entities): write a note containing them.
