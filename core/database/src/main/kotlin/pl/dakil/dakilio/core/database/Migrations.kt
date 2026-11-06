package pl.dakil.dakilio.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 → 2: diary and habit reminders (`reminder_on`, `reminder_minute`).
 *
 * Hand-written rather than an AutoMigration, because the reminder columns first landed by editing
 * version 1 in place. Development builds from that time are still at version 1 but may already have
 * the columns (and an abandoned `photo_file` table), so this adds only what's missing and drops the
 * leftover table. On a 1.0.0 install it does exactly what the AutoMigration would.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("diary", "habit")) {
            val columns = db.columnsOf(table)
            if ("reminder_on" !in columns) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `reminder_on` INTEGER NOT NULL DEFAULT 0")
            }
            if ("reminder_minute" !in columns) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `reminder_minute` INTEGER NOT NULL DEFAULT 1200")
            }
        }
        db.execSQL("DROP TABLE IF EXISTS `photo_file`")
    }
}

/**
 * 2 → 3: reminders get their own table (the Reminders extra), so a diary or habit can have several,
 * each on chosen weekdays. The reminders that were on move over as every-day reminders; ones that
 * were off were only a remembered time and are dropped. The old columns stay, unused: dropping a
 * column on SQLite before 3.35 means rebuilding `diary` and `habit`, which other tables reference.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `reminder` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `diary_id` INTEGER, " +
                "`habit_id` INTEGER, `minute` INTEGER NOT NULL, `days` INTEGER NOT NULL DEFAULT 127, " +
                "`enabled` INTEGER NOT NULL DEFAULT 1, `created_at` INTEGER NOT NULL, " +
                "FOREIGN KEY(`diary_id`) REFERENCES `diary`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`habit_id`) REFERENCES `habit`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_diary_id` ON `reminder` (`diary_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_habit_id` ON `reminder` (`habit_id`)")
        val now = "CAST(strftime('%s', 'now') AS INTEGER) * 1000"
        db.execSQL(
            "INSERT INTO `reminder` (`diary_id`, `minute`, `days`, `enabled`, `created_at`) " +
                "SELECT `id`, `reminder_minute`, 127, 1, $now FROM `diary` WHERE `reminder_on` = 1 ORDER BY `position`, `id`",
        )
        db.execSQL(
            "INSERT INTO `reminder` (`habit_id`, `minute`, `days`, `enabled`, `created_at`) " +
                "SELECT `id`, `reminder_minute`, 127, 1, $now FROM `habit` WHERE `reminder_on` = 1 ORDER BY `position`, `id`",
        )
        db.execSQL("UPDATE `diary` SET `reminder_on` = 0")
        db.execSQL("UPDATE `habit` SET `reminder_on` = 0")
    }
}

/** Every migration, in order, for the database builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

private fun SupportSQLiteDatabase.columnsOf(table: String): Set<String> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndexOrThrow("name")
        buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
    }
