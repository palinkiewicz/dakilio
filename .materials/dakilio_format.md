# The `.dakilio` backup format (version 1)

Dakilio's own backup: everything the app holds (entries, photos, moods, tags, diaries, habits and settings) in one file. The code lives in `core:backup`:
- `DakilioBackup.kt`: the JSON model;
- `BackupCrypto.kt`: encryption;
- `DakilioArchive.kt`: reading and writing;
- `RestorePlan.kt`: mapping a backup onto the database.

There are two variants, which the app tells apart by their first bytes (`detectBackupKind`).

## Plain backup

A ZIP archive:

| Member | Content |
|---|---|
| `dakilio.json` | Always the **first** member (deflated), so the file can be recognised from its first 64 bytes: the ZIP signature `PK\3\4`, then this name at offset 30. |
| `photos/<name>.jpg` | Every photo an entry lists, stored as is (they're JPEGs already, EXIF stripped). |

Unzip it and open `dakilio.json` in any editor.

### `dakilio.json`

Pretty-printed JSON. Fields that are null or at their default (`false`, `0`, `[]`) are left out. Ids are local to the file: they only connect entries to their moods, tags and diaries. Unknown keys are ignored when reading, so later app versions can add fields without a new version number.

```json
{
  "format": "dakilio",
  "version": 1,
  "exportedAt": "2026-11-03T14:20:05.120+01:00",
  "appVersion": "1.0.0",
  "settings": {
    "theme": "Dark", "tagStyle": "Icons", "showHints": false,
    "timelineTextScale": 1.1, "editorTextScale": 1.0,
    "previewLength": "Short", "compactTags": true, "tagLimit": "6", "monthNavigator": true, "monthNavigatorInAppBar": false,
    "closeAfterSave": true, "keywordAutoAdd": false, "lastDiary": 1,
    "timelineDiary": 2, "weekStart": "Region", "photoSize": "Px1280"
  },
  "moods": [
    { "id": 3, "level": 3, "name": "Okay", "primary": true, "color": "periwinkle" },
    { "id": 7, "level": 2, "name": "Sad", "position": 1, "archived": true }
  ],
  "diaries": [
    { "id": 1, "name": "Your Journey", "icon": "explore", "createdAt": "2026-01-01T09:00:00Z", "suggestedGroups": [2],
      "reminders": [ { "enabled": true, "time": "20:30" },
                     { "enabled": false, "time": "07:15", "days": ["mon", "tue", "wed", "thu", "fri"] } ] }
  ],
  "tagGroups": [
    { "id": 2, "name": "People", "color": "orange",
      "tags": [ { "id": 5, "name": "Family", "icon": "family_restroom",
                  "keywords": [ { "word": "mum" }, { "word": "famil", "approximate": true } ] } ] }
  ],
  "habits": [ { "tag": 5, "daysPerWeek": 3, "createdAt": "2026-02-01T10:00:00Z",
                "reminders": [ { "enabled": true, "time": "08:00", "days": ["sat", "sun"] } ] } ],
  "entries": [
    { "time": "2026-11-02T21:05:13.12+01:00", "diary": 1, "mood": 3,
      "title": "Dinner", "note": "With everyone.", "tags": [5],
      "photos": ["photos/3f9c….jpg"],
      "createdAt": "2026-11-02T20:06:00Z", "updatedAt": "2026-11-02T20:06:00Z" }
  ]
}
```

| Field | Meaning |
|---|---|
| `format`, `version` | Always `"dakilio"` and `1`. A file with a higher version is refused ("made by a newer Dakilio"). |
| `settings` | Every key is optional. Unknown enum names leave the device's value alone. `weekStart` is `Region` (follow the device's region), `Monday`, `Saturday` or `Sunday`. `photoSize` is `Px640`, `Px960`, `Px1280`, `Px1600`, `Px2048` or `Original` (the long edge photos are scaled down to); a restore re-encodes the backup's photos at its own size. Search history, the language, intro state and the app lock (its settings and PIN or password, kept in a separate store) aren't included, so a restore never locks anyone out or carries over another device's credential. |
| `moods` | `level`: 1 = worst … 5 = best. One `primary` per level, plus aliases (`position` orders them within a level). `color` is a `TagColor` palette key. |
| `diaries` | `suggestedGroups`: ids of the tag groups the editor expands for that diary. `reminders` are its reminders (the Reminders extra): `time` is the local `HH:mm`, `days` the weekdays (`mon` … `sun`; missing means every day, unknown names are dropped), `enabled` whether it's switched on. A restore keeps them, but a new phone still has to allow notifications. Backups from 1.2.0 have a single `reminder` instead, which is read as one every-day reminder when it's enabled and ignored otherwise. |
| `tagGroups` | Tags are nested in their group. A tag's `color` overrides its group's. `icon` (a diary has one too) is a symbol key: a Material Symbol name (`family_restroom`), or `glyph:` followed by one or two characters for a letter icon (`glyph:A`, `glyph:ł`). An app that doesn't know a key shows its default icon. A tag's optional `keywords` (the Keywords extra) are words that suggest it; `approximate` makes the word's end also match longer words. Blank and duplicate words are dropped on restore. |
| `habits` | One per tag. `daysPerWeek` is 1–7. `reminders` work like a diary's. |
| `entries.time` | An ISO-8601 date-time **with the offset it was written in**. That's both the timestamp and `utc_offset_min`; the entry's day is the date part. |
| `entries.mood` | Optional (an entry may have no mood). |
| `entries.photos` | Archive paths, in display order. |
| `createdAt`, `updatedAt` | ISO instants in UTC. |

**Restoring** replaces everything, like the Daylio import (`planRestore`, then `ImportDao.replaceAll`). It is defensive, since a hand-edited file is allowed:
- dangling ids are dropped and counted;
- an entry whose diary is missing goes into the first diary;
- with no diaries at all, a default diary is created;
- a level without a primary gets the default name;
- extra primaries become aliases;
- photos are re-encoded on the way in.

## Encrypted backup

The plain ZIP above, byte for byte, encrypted with a password:

```
DAKILIO-ENCRYPTED\n
{"version":1,"kdf":"PBKDF2-HMAC-SHA256","iterations":600000,"salt":"<base64, 16 B>","cipher":"AES-256-GCM-STREAM","chunkSize":65536,"noncePrefix":"<base64, 7 B>"}\n
<chunk 0><chunk 1>…<last chunk>
```

- **Key**: PBKDF2-HMAC-SHA256 over the password (UTF-8), with the header's salt and iteration count, giving 256 bits.
- **Chunks**: the plaintext is split into `chunkSize` pieces, and the last one may be shorter or empty. Each piece is sealed with AES-256-GCM, so its ciphertext is the piece plus a 16-byte tag.
  - The 12-byte nonce is `noncePrefix` (7 B) ‖ the chunk counter (4 B, big-endian, from 0) ‖ `0x01` for the last chunk, else `0x00`.
  - The associated data of every chunk is the whole header: the magic line and the JSON line, including both newlines.
- This is the STREAM construction (as in Tink and age). A changed header, reordered, dropped or extra chunks, and a file cut off after any chunk all fail authentication, and decryption needs only one chunk in memory.
- A wrong password fails on the first chunk. If the first chunk opens with the opposite "last" flag, the password was right and the file is damaged.

To decrypt one by hand (in Python, with `cryptography`):
1. Read the two header lines.
2. Derive the key with `PBKDF2HMAC(SHA256, 32, salt, iterations)`.
3. Read blocks of `chunkSize + 16` bytes.
4. Open each block with `AESGCM(key).decrypt(nonce, block, header_bytes)`.
