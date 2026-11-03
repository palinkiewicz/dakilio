package pl.dakil.dakilio

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import pl.dakil.dakilio.core.data.repository.IncomingBackups

/**
 * Invisible entry point for backups shared to Dakilio or opened with it.
 *
 * The sharing app decides which task a share target starts in, and some (Daylio's backup screen)
 * make Android ignore MainActivity's singleTask and start a second copy inside their own task. This
 * activity may land anywhere: it only forwards the file to MainActivity in Dakilio's own task, passing
 * on the read permission, and finishes before it draws anything.
 */
class BackupReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IncomingBackups.uriFrom(intent)?.let { uri ->
            val forward = Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(forward)
        }
        finish()
    }
}
