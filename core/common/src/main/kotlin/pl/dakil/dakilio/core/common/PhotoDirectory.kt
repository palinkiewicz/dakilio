package pl.dakil.dakilio.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App-private home of every entry photo. Never exposed to other apps or backed up to the cloud. */
@Singleton
class PhotoDirectory @Inject constructor(@ApplicationContext context: Context) {
    val root: File = File(context.filesDir, "photos")

    fun file(fileName: String): File = File(root, fileName)

    fun ensureExists(): File = root.apply { mkdirs() }
}
