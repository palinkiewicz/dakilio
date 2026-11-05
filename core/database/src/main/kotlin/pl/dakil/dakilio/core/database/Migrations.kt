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

/** Every migration, in order, for the database builder. */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)

private fun SupportSQLiteDatabase.columnsOf(table: String): Set<String> =
    query("PRAGMA table_info(`$table`)").use { cursor ->
        val name = cursor.getColumnIndexOrThrow("name")
        buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
    }
