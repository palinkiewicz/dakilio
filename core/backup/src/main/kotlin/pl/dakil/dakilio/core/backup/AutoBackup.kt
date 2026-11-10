package pl.dakil.dakilio.core.backup

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import pl.dakil.dakilio.core.model.AutoBackupFrequency

/**
 * The pure rules of automatic backups: their file names, which old ones go, whether anything changed
 * and when a backup is overdue.
 */
object AutoBackup {
    const val PREFIX = "dakilio-auto-"
    const val EXTENSION = ".dakilio"

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    private val pattern = Regex("""^dakilio-auto-(\d{4}-\d{2}-\d{2}-\d{4})\.dakilio$""")

    /** `dakilio-auto-2026-11-10-0312.dakilio`: sorts by name in time order. */
    fun fileName(time: LocalDateTime): String = PREFIX + stamp.format(time) + EXTENSION

    /**
     * The time in an automatic backup's [name], or null for any other file. Only names this returns a
     * time for are ever deleted, so the user's own files in the folder are safe.
     */
    fun parseFileName(name: String): LocalDateTime? {
        val match = pattern.matchEntire(name) ?: return null
        return try {
            LocalDateTime.parse(match.groupValues[1], stamp)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** The automatic backups among [names] beyond the newest [keep]; other files are never listed. */
    fun filesToDelete(names: Collection<String>, keep: Int): List<String> =
        names.mapNotNull { name -> parseFileName(name)?.let { name to it } }
            .sortedWith(compareByDescending<Pair<String, LocalDateTime>> { it.second }.thenByDescending { it.first })
            .drop(keep.coerceAtLeast(1))
            .map { it.first }

    /**
     * Identifies a backup's content: the JSON without when or by which version it was made, plus
     * every photo's name and size ([photos]: file name → bytes). Photo names are unique, but scaling
     * photos down rewrites them under the same name, so the size has to count too.
     */
    fun fingerprint(backup: DakilioBackup, photos: Map<String, Long>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(backup.copy(exportedAt = null, appVersion = null).encode().toByteArray(Charsets.UTF_8))
        photos.toSortedMap().forEach { (name, size) ->
            digest.update(0)
            digest.update(name.toByteArray(Charsets.UTF_8))
            digest.update(0)
            digest.update(size.toString().toByteArray(Charsets.US_ASCII))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Whether backups are overdue: none succeeded for twice the interval plus a day. That leaves room
     * for a phone that's charged every other night, and still warns well before much is lost.
     * [lastSuccess] is null before the first backup; [since] is then when they were turned on.
     */
    fun isOverdue(lastSuccess: Instant?, since: Instant?, now: Instant, frequency: AutoBackupFrequency): Boolean {
        val from = lastSuccess ?: since ?: return false
        return Duration.between(from, now) > Duration.ofDays(frequency.days * 2L + 1)
    }
}
