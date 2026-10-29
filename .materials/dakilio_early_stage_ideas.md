# 🌿 Dakilio — Journey Tracker

> *"Life is a journey!"* — A native Android, resource-respecting, open-source Daylio-like alternative built with Jetpack Compose & Material 3 Expressive.

## 🚀 Core Philosophy & Principles

* **Privacy First & Local Storage:** Zero mandatory accounts, no cloud dependencies, end-to-end encryption for backups.
* **Resource Respecting:** Optimized Jetpack Compose runtime without heavy WebViews; lightweight vector assets to keep APK size minimal.
* **Expressive & Joyful:** Bold typography scales, dynamic color themes, tactile haptic feedback, and fluid spring animations.
* **Deep Customization:** Total user ownership over feelings, habits/activities, color schemes, haptics, and modes.

## ⚡ The Core Loop: Frictionless Check-in

Designed as a **full-screen, animated, expressive, and haptic dialog** with spring motions.

### 1. Step 1: Mood Selection (Hero Moment)

* **Flexible Grade Scale:** Supports 3, 5, 7, or custom $N$-tier mood scales.
* **Visuals:** Radical Expressive shapes (oversized geometric shapes: floating pills, rounded cards, organic morphing blobs).
* **Interactivity:** Shapes morph gracefully on tap with tactile haptic feedback (customizable/toggleable in settings).

### 2. Step 2: Activities & Habits

* **Layout:** Categorized grids featuring fluid pill chips with suggested tag groups.
* **Customization:** Fully categorizable habits/activities with custom colors and lightweight vector icons (SVG/Material Icons to prevent APK bloat).
* **Interactivity:** Multi-select with snappy micro-animations.

### 3. Step 3: Note & Photos

* **Notes:** Just basic header, bold, italic support.
* **Media:** Direct local media embedding without external cloud locks.

---

## 🗂️ Modular Spaces (Multi-Diary Engine)

Allow users to create distinct contexts (*Life*, *Work*, *Dreams*, *School*, *Projects*) without destroying the zero-friction daily flow.

* **Default Mental Model:** Unified single stream out-of-the-box. Multi-diary support is an optional power feature in settings.
* **Per-Diary Customization:**
  * Toggle Mood tracking ON/OFF per diary (e.g., disable moods entirely for a *Dreams* or *Project Log* diary).
  * Unique default tag/activity subsets suggested per space.
* **Changing Entry Diary After Creation:**
  * Users can reassign an entry to another diary at any time.
  * **Non-Blocking Tag Architecture:** Activities are never restricted or lost when moving entries. The destination diary provides **suggested tag groups** expanded at the top, while tag groups non-native to that diary remain gracefully **collapsed** at the bottom for full accessibility.

---

## 📐 Dynamic Mood Scales & `GradeSetVersion` Schema

To allow users to change grade scales or modify icons/labels over time without corrupting historical logs or breaking long-term analytics:

### 1. The `GradeSetVersion` Schema

```
┌───────────────────────┐         1:N         ┌───────────────────────┐
│   GradeSetVersion     │ ──────────────────> │        Grade          │
├───────────────────────┤                     ├───────────────────────┤
│ id (PK)               │                     │ id (PK)               │
│ scale_size (Int: 3,5) │                     │ set_version_id (FK)   │
│ created_at (Timestamp)│                     │ tier_index (Int: 1..N)│
│ is_active (Boolean)   │                     │ label (String)        │
└───────────────────────┘                     │ icon_id (String)      │
            │                                 │ color_hex (String)    │
            │ 1:N                             └───────────────────────┘
            ▼                                             ▲
┌───────────────────────┐                                 │
│        Entry          │                                 │ 1:N
├───────────────────────┤                                 │
│ id (PK)               │                                 │
│ timestamp             │                                 │
│ grade_id (FK) ────────┼─────────────────────────────────┘
│ note_text             │
└───────────────────────┘
```

### 2. Versioning & Update Rules

* **Scale Size Changed (e.g., 5-tier $\rightarrow$ 3-tier):** Automatically generates a **new `GradeSetVersion`**. Historical entries remain locked to their original scale version and visual definitions.
* **Scale Size Unchanged (Modifying icons/labels on existing 5-tier):** Prompts the user:
  * 🔘 *Update All Past Entries:* Overwrites labels/icons for past entries linked to this scale.
  * ⚪ *Start Fresh From Today:* Creates a new `GradeSetVersion` so historical entries preserve their old visual state as a snapshot.

### 3. Normalized Math for Unified Analytics

To seamlessly plot mood trends across different scales over years of data, all scores map to a normalized $0.0 - 1.0$ float:

$$\text{Normalized Score} = \frac{\text{Grade.tier\_index} - 1}{\text{GradeSetVersion.scale\_size} - 1}$$

* Example: $3/3$ on a 3-point scale and $5/5$ on a 5-point scale both map to $1.0$ ($100\%$), maintaining seamless continuity on trend charts.

---

## 🌉 The Daylio Migration Bridge (Import & Export Engine)

### 📥 Import Wizard

* Parses `.daylio` compressed zip archives and CSV exports.
* **Normalizes Data:** Maps historical moods, custom activity icons, and notes seamlessly.
* **Media Extraction:** Safely moves attached media into local, app-scoped storage while preserving timestamps.
* **Gamified Experience:** Designed as an entertaining, interactive mini-experience (e.g., retro travel/passport stamp animation during migration progress).

### 📤 Export & Backup Engine

* **Encrypted Local Backups:** Password-protected exported `.dakilio` files.
* **Daylio Export Mode:** Option to re-export data into Daylio-compatible CSV/format for ultimate interoperability.

---

## 💡 Delightful Additions (Without Overwhelm)

### 1. Ambient Mood Echoes (Gentle Insights)

* **Default View (Editorial Summary):** Replaces clinical dashboards with dynamic, human-readable insights once sufficient data is available (e.g., *"You tend to feel most energized on days with 'Hiking' or 'Reading'"*).
* **Poweruser Analytics:** Accessible deep dives with expressive, clear charts, mood matrices, and per-diary filter toggles (`All` | `Life` | `Work` | `Dreams`).
* **Graceful Omission:** Entries in diaries with mood tracking disabled are automatically excluded from mood charts without disrupting streak or entry count heatmaps.

### 2. Contextual Micro-Reflections (Optional Prompts)

* **Targeted Prompts:** When a lower mood tier is selected, gentle and empathetic prompts appear (e.g., *"Would you like to write down what felt heaviest today, or just leave it here?"*).
* **Quick Reflection Mode (Setting):** A configurable setting that provides randomized, feeling-dependent prompts to eliminate writer’s block without forcing long journal entries.

---

## 📱 User Interface Architecture

### 1. The Timeline / Home Screen

* **Top App Bar:** Large, bold editorial header (*"Your Journey"*) featuring dynamic time-of-day greetings and a subtle streak counter pill.
* **Layout:** Hybrid interface combining a sticky horizontal calendar strip at the top with a staggered vertical card stream below.
* **Expressive Styling:** 24dp corner radii, soft elevated shadows, color-coded mood accents, and edge-to-edge embedded media thumbnails.

### 2. Settings & Deep Personalization Hub

* **Haptic Controls:** Granular sliders/toggles for haptic intensity or complete disabling.
* **Custom Feelings & Grade Sets:** Add, reorder, recolor, and assign lightweight vector icons to feelings with version control support.
* **Spaces Manager:** Create and configure multi-diary contexts, toggle mood tracking per space, and organize tag group order.
* **Modes & Behavior:** Quick Reflection Mode, dynamic theme engines (wallpaper color matching vs. expressive seasonal palettes), notification schedules, and local security (Biometric/PIN lock).

---

## 📝 Ideas & Brainstorming Notes (For Modification)

* \[ \] *Idea: Add custom widgets for home screen quick check-ins.*
* \[ \] *Idea: Explore localized soundscapes/audio cues for the full-screen check-in flow.*
* \[ \] *Idea: Gamified milestones (e.g., visual travel stamps for check-in streaks).*