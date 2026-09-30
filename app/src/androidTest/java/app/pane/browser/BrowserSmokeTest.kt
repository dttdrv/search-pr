package app.pane.browser

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.waitForStable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.regex.Pattern

/** Real Compose chrome and Gecko pages, using fresh accessibility nodes for every action. */
@RunWith(AndroidJUnit4::class)
class BrowserSmokeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val shots = File(context.getExternalFilesDir(null), "smoke")

    @Test
    fun browserSurfacesRemainUsable() {
        // Gecko can emit accessibility events continuously. Wait for each requested control,
        // rather than requiring every window to be idle (the shell dump command's failure mode).
        val config = Configurator.getInstance()
        val oldIdleTimeout = config.waitForIdleTimeout
        config.waitForIdleTimeout = 0
        try {
            launch()
            node(By.text("Start Browsing"))
            shot("01-onboarding")
            scrollTo(By.text("Block ads with uBlock Origin")).click()
            tap(By.text("Start Browsing"))
            node(By.desc("Menu"))
            shot("02-start-page")

            openPage()
            node(By.text("Example Domain"), 60_000)
            shot("03-page")
            // Compare the live TextureView with/without glass and both screenshot paths.
            // Accessibility text alone cannot prove that Gecko has actually painted the page.
            for (quality in listOf("off", "full")) {
                context.startActivity(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("glass", quality))
                SystemClock.sleep(1_000)
                shot("03-page-$quality")
            }
            device.executeShellCommand("screencap -p ${File(shots, "03-page-shell.png").absolutePath}")
            tap(By.desc("Menu"))
            node(By.text("Find"))
            shot("04-menu")
            tap(By.text("Find"))
            node(By.clazz("android.widget.EditText").pkg(context.packageName)).text = "domain"
            node(By.text(Pattern.compile("[1-9][0-9]* of [1-9][0-9]*")))
            shot("05-find")
            device.pressBack()
            if (device.wait(Until.hasObject(By.desc("Next match")), 500)) device.pressBack()
            assertTrue("Back must close Find and retain the page", device.wait(Until.gone(By.desc("Next match")), 5_000))
            node(By.desc("Menu"))

            // A new intent must reveal its page even while the previous address is being edited.
            tap(By.descContains("Address:"))
            node(By.text("Cancel"))
            openPage()
            node(By.desc("Menu"))
            assertTrue("External link retained the old editor", device.wait(Until.gone(By.text("Cancel")), 5_000))
            tap(By.descContains("Address:"))
            node(By.clazz("android.widget.EditText").pkg(context.packageName)).text = "privacy"
            shot("06-search")
            tap(By.text("Cancel"))

            tap(By.descContains(" tabs"))
            node(By.desc("Done"))
            shot("07-tabs")
            openPage()
            node(By.desc("Menu"))
            assertTrue("External link retained the tab overview", device.wait(Until.gone(By.desc("Done")), 5_000))

            menu("Settings")
            shot("08-settings")
            scrollTo(By.text("Appearance")).click()
            for (theme in listOf("Dark", "Automatic", "Light", "Automatic")) {
                tap(By.text(theme))
                node(By.text(theme).selected(true))
            }
            shot("09-appearance")
            device.pressBack()
            node(By.text("Appearance"))
            device.pressBack()
            node(By.desc("Menu"))

            menu("Bookmark")
            node(By.text("Saved"))
            device.pressBack()
            menu("Bookmarks")
            node(By.text("Example Domain"))
            shot("10-bookmarks")
            device.pressBack()
            menu("History")
            node(By.text("Example Domain"))
            shot("11-history")
            device.pressBack()
            menu("Extensions")
            node(By.text("Browse Add-ons"))
            shot("12-extensions")
            device.pressBack()

            device.setOrientationLeft()
            node(By.desc("Menu"))
            SystemClock.sleep(1_000)
            shot("13-landscape")
            device.setOrientationNatural()
            node(By.desc("Menu"))
            menu("New Private Tab")
            if (device.wait(Until.hasObject(By.text("Cancel")), 2_000)) tap(By.text("Cancel"))
            tap(By.descContains(" tabs"))
            node(By.desc("Done"))
            assertSecureWindow(true)
            tap(By.desc("Done"))
            openPage()
            node(By.desc("Menu"))
            node(By.text("Example Domain"))
            assertSecureWindow(false)
            shot("14-return-from-private")

            // The debug password-manager probe must receive the real site's web fields and domain.
            device.executeShellCommand("settings put secure autofill_service app.pane.browser/app.pane.browser.debug.ProbeAutofillService")
            openPage("https://github.com/login")
            node(By.textContains("Username or email"), 60_000).click()
            val deadline = SystemClock.uptimeMillis() + 20_000
            var log = ""
            while (SystemClock.uptimeMillis() < deadline) {
                log = device.executeShellCommand("logcat -d -s PaneAutofillProbe:I")
                if (Regex("FILL_REQUEST .*github\\.com.*password").containsMatchIn(log)) break
                SystemClock.sleep(500)
            }
            File(shots, "autofill.txt").writeText(log)
            assertTrue("Autofill did not receive GitHub's password field and domain", Regex("FILL_REQUEST .*github\\.com.*password").containsMatchIn(log))
            shot("15-autofill")
        } finally {
            shot("last-screen")
            device.dumpWindowHierarchy(File(shots, "last-window.xml"))
            device.unfreezeRotation()
            config.waitForIdleTimeout = oldIdleTimeout
        }
    }

    private fun launch(url: String? = null) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (url != null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(url)
        }
        context.startActivity(intent)
    }

    private fun openPage(url: String = "https://example.com") = launch(url)

    private fun node(selector: BySelector, timeout: Long = 15_000): UiObject2 {
        val control = device.wait(Until.findObject(selector), timeout) ?: throw AssertionError("Missing control: $selector")
        // Let this control's entrance or layout settle without waiting for the live web page.
        control.waitForStable(requireStableScreenshot = false)
        return control
    }

    private fun tap(selector: BySelector) = node(selector).click()

    private fun scrollTo(selector: BySelector): UiObject2 {
        repeat(5) {
            if (device.wait(Until.hasObject(selector), 1_000)) return node(selector)
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 30)
        }
        return node(selector)
    }

    private fun menu(entry: String) {
        tap(By.desc("Menu"))
        scrollTo(By.text(entry)).click()
    }

    private fun shot(name: String) {
        shots.mkdirs()
        device.takeScreenshot(File(shots, "$name.png"))
    }

    private fun assertSecureWindow(expected: Boolean) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .first { it is MainActivity }
            val secure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            assertTrue("Screenshot protection should be $expected", secure == expected)
        }
    }
}
