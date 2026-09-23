package com.kl.travel

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.kl.travel.ui.SPLASH_TAP_TAG
import com.kl.travel.ui.SplashGate
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SplashSkipTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var scenario: ActivityScenario<ComponentActivity>? = null
    @org.junit.After fun closeActivity() { scenario?.close(); scenario = null }

    @Before fun register() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(ctx.packageManager).addActivityIfNotPresent(ComponentName(ctx, ComponentActivity::class.java))
    }

    @Test fun tapAnywhereSkipsTheSplash() {
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        scenario!!.onActivity { a ->
            a.setContent { SplashGate { Text("APP CONTENT") } }
        }
        // splash is up: the skip layer is there and the hint text is visible
        rule.onNodeWithTag(SPLASH_TAP_TAG).assertIsDisplayed()
        rule.onNodeWithText("tap anywhere to skip", substring = true).assertIsDisplayed()
        rule.onNodeWithTag(SPLASH_TAP_TAG).performTouchInput { click() }
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        // splash gone
        assertTrue(rule.onAllNodesWithTagCount(SPLASH_TAP_TAG) == 0)
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size
}
