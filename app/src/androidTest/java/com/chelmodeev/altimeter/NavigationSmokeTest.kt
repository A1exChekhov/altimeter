package com.chelmodeev.altimeter

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule(order = 0)
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun allFourPagesStayForegroundForFiveCycles() {
        repeat(5) { cycle ->
            open("nav_map", "page_map", cycle)
            open("nav_track", "page_track", cycle)
            open("nav_analytics", "page_analytics", cycle)
            open("nav_home", "page_home", cycle)
        }
    }

    @Test
    fun backOnHomeCannotSilentlySendAppToBackground() {
        compose.onNodeWithTag("page_home", useUnmergedTree = true).assertIsDisplayed()

        Espresso.pressBack()
        compose.waitForIdle()

        compose.onNodeWithTag("minimize_confirmation", useUnmergedTree = true)
            .assertIsDisplayed()
        assertEquals(Lifecycle.State.RESUMED, compose.activity.lifecycle.currentState)

        // A second Back dismisses the dialog and leaves the Activity visible.
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("minimize_confirmation", useUnmergedTree = true)
            .assertDoesNotExist()
        assertEquals(Lifecycle.State.RESUMED, compose.activity.lifecycle.currentState)
    }

    private fun open(navTag: String, pageTag: String, cycle: Int) {
        compose.onNodeWithTag(navTag, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(pageTag, useUnmergedTree = true).assertIsDisplayed()
        assertEquals(
            "Activity left foreground in cycle ${cycle + 1} after $navTag",
            Lifecycle.State.RESUMED,
            compose.activity.lifecycle.currentState,
        )
    }
}
