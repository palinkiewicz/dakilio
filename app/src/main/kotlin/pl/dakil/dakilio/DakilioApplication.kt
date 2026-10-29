package pl.dakil.dakilio

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.dakil.dakilio.core.common.ApplicationScope
import pl.dakil.dakilio.core.data.repository.PhotoRepository

@HiltAndroidApp
class DakilioApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var photoRepository: PhotoRepository

    @Inject @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Tidy up photos left behind by drafts that were never saved.
        applicationScope.launch { photoRepository.sweepOrphans() }
    }

    // Local files only: Coil's network stack is deliberately not on the classpath.
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context).crossfade(true).build()
}
