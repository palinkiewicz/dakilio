package pl.dakil.dakilio.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the startup path plus the app's hottest interactions (scrolling the timeline, opening the
 * entry editor, switching tabs) so they are AOT-compiled on install.
 *
 * Run with: ./gradlew :app:generateReleaseBaselineProfile (needs an API 33+ emulator or device).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "pl.dakil.dakilio",
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.desc("New entry")), 10_000)

        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / 5)
            repeat(2) { list.fling(Direction.DOWN) }
            device.waitForIdle()
            list.fling(Direction.UP)
            device.waitForIdle()
        }

        device.findObject(By.desc("New entry"))?.click()
        device.wait(Until.hasObject(By.text("How are you feeling?")), 5_000)
        device.findObject(By.text("Good"))?.click()
        device.wait(Until.hasObject(By.text("What's been going on?")), 5_000)
        device.pressBack()
        device.wait(Until.hasObject(By.text("Discard")), 3_000)
        device.findObject(By.text("Discard"))?.click()
        device.waitForIdle()

        device.findObject(By.text("Insights"))?.click()
        device.waitForIdle()
        device.findObject(By.text("More"))?.click()
        device.waitForIdle()
    }
}
