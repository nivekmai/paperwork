package com.nivek.paperwork

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuickToolsTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun longPressRepeatsEvery150msAndStopsOnRelease() {
        var count=0
        compose.activity.setContent { PaperworkTheme { NudgeBar(true) { _,_ -> count++ } } }
        val right=compose.onNodeWithContentDescription("Nudge right")
        right.performClick(); assertEquals(1,count)
        compose.mainClock.autoAdvance=false
        right.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(550)
        val first=count; assertTrue(first>1)
        compose.mainClock.advanceTimeBy(150); assertEquals(first+1,count)
        compose.mainClock.advanceTimeBy(150); assertEquals(first+2,count)
        right.performTouchInput { up() }
        val released=count
        compose.mainClock.advanceTimeBy(450); assertEquals(released,count)
        compose.mainClock.autoAdvance=true
    }
}
