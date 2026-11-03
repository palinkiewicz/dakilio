package pl.dakil.dakilio.core.backup

/** Why a backup can't be read. */
enum class BackupError {
    /** Neither a Dakilio backup nor an encrypted one. */
    NotABackup,

    /** The password doesn't open the file. */
    WrongPassword,

    /** Encrypted, but damaged or cut short: the right password doesn't produce a valid file. */
    Corrupted,
    TooLarge,

    /** Made by a newer Dakilio than this one. */
    NewerVersion,

    /** A Dakilio archive whose JSON can't be read. */
    Malformed,
}

class BackupException(val reason: BackupError, cause: Throwable? = null) : Exception(reason.name, cause)

enum class BackupKind {
    DakilioPlain,
    DakilioEncrypted,

    /** Anything else (a Daylio backup, as far as the app is concerned). */
    Other,
}

/** How many leading bytes [detectBackupKind] needs. */
const val BACKUP_HEAD_BYTES = 64

private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

/**
 * Tells the kinds apart from the file's first [BACKUP_HEAD_BYTES] bytes: an encrypted backup starts
 * with [BackupCrypto.MAGIC], and a plain one is a ZIP whose first member is `dakilio.json` (the
 * name sits at offset 30 of the first local file header).
 */
fun detectBackupKind(head: ByteArray): BackupKind {
    val magic = BackupCrypto.MAGIC.toByteArray(Charsets.US_ASCII)
    if (head.startsWith(magic)) return BackupKind.DakilioEncrypted
    if (!head.startsWith(ZIP_SIGNATURE) || head.size < 30) return BackupKind.Other
    val nameLength = (head[26].toInt() and 0xFF) or ((head[27].toInt() and 0xFF) shl 8)
    val name = DakilioBackup.JSON_MEMBER.toByteArray(Charsets.US_ASCII)
    if (nameLength != name.size || head.size < 30 + name.size) return BackupKind.Other
    return if (head.copyOfRange(30, 30 + name.size).contentEquals(name)) BackupKind.DakilioPlain else BackupKind.Other
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
