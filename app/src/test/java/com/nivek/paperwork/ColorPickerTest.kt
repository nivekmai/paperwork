package com.nivek.paperwork

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import android.graphics.Bitmap
import android.graphics.Canvas
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
class ColorPickerTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun hexOpacityAndPaintingPreserveAlpha() {
        assertEquals(0x80236aba.toInt(),parseColorHex("236ABA80",255))
        assertEquals(0x40236aba,parseColorHex("#236ABA",64))
        assertNull(parseColorHex("FFFFF",255)); assertNull(parseColorHex("GGFF00",255))
        var applied: Int?=null
        compose.activity.setContent { PaperworkTheme { CustomColorDialog("Fill color",0xff123456.toInt(),{}, {applied=it}) } }
        compose.onNodeWithText("Hex (RRGGBB or RRGGBBAA)").performScrollTo().performTextReplacement("236ABA80")
        compose.onNodeWithText("Alpha (%)").performScrollTo().performTextReplacement("25")
        compose.onNodeWithText("Apply color").performClick()
        assertEquals(0x40236aba,applied)
        val mark=Mark(kind="Square",x=0f,y=0f,width=40f,height=40f,fill=applied!!,color=0)
        assertEquals(mark,Mark.from(mark.json()))
        val bitmap=Bitmap.createBitmap(50,50,Bitmap.Config.ARGB_8888)
        MarkPainter.draw(Canvas(bitmap),mark)
        assertEquals(64,android.graphics.Color.alpha(bitmap.getPixel(20,20)))
    }
    @Test fun squareHueOpacityAndTransparentSwatchAreUsable() {
        var applied=0
        compose.activity.setContent { PaperworkTheme { CustomColorDialog("Fill color",0xffff0000.toInt(),{}, {applied=it}) } }
        compose.onNodeWithContentDescription("Hue").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(120f) }
        compose.onNodeWithContentDescription("Saturation and brightness").performScrollTo().performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Opacity").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(128f) }
        compose.onNodeWithText("Apply color").performClick()
        assertEquals(128,android.graphics.Color.alpha(applied))
        assertTrue(android.graphics.Color.green(applied)>android.graphics.Color.red(applied))
        compose.activity.setContent { PaperworkTheme { androidx.compose.foundation.layout.Column { ColorPicker("Fill",applied,true) {applied=it} } } }
        compose.onNodeWithText("Transparent").assertDoesNotExist()
        val transparent=compose.onNodeWithContentDescription("Fill: Transparent").fetchSemanticsNode().boundsInRoot
        val black=compose.onNodeWithContentDescription("Fill: #000000").fetchSemanticsNode().boundsInRoot
        assertEquals(black.top,transparent.top,.1f); assertEquals(black.height,transparent.height,.1f)
        compose.onNodeWithContentDescription("Fill: Transparent").performClick(); assertEquals(0,applied)
    }
    @Test fun colorDialogFitsNormallyAndScrollsForLargeFonts() {
        compose.activity.setContent { PaperworkTheme { CustomColorDialog("Fill color",0xff123456.toInt(),{}, {}) } }
        fun scrollRange()=compose.onNodeWithContentDescription("Color controls").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].maxValue()
        compose.onNodeWithText("Hex (RRGGBB or RRGGBBAA)").assertIsDisplayed()
        assertEquals(0f,scrollRange(),.1f)
        try {
            org.robolectric.RuntimeEnvironment.setFontScale(2.5f)
            compose.activityRule.scenario.recreate()
            compose.activity.setContent { PaperworkTheme { CustomColorDialog("Fill color",0xff123456.toInt(),{}, {}) } }
            assertTrue("range=${scrollRange()}, fontScale=${compose.activity.resources.configuration.fontScale}, controls=${compose.onNodeWithContentDescription("Color controls").fetchSemanticsNode().boundsInRoot}",scrollRange()>0)
            compose.onNodeWithText("Hex (RRGGBB or RRGGBBAA)").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Apply color").assertIsDisplayed()
        } finally { org.robolectric.RuntimeEnvironment.setFontScale(1f) }

    }
    @Test fun squareStyleRadiusSavesWithoutChangingDimensions() {
        val original=Mark(kind="Square",width=120f,height=80f,fill=android.graphics.Color.RED)
        var saved: Mark?=null
        val page=PageImage(Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888),600f,800f)
        compose.activity.setContent { PaperworkTheme { MarkDialog(original,page,{}, {saved=it}) } }
        compose.onNodeWithContentDescription("Corner radius").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(18f) }
        compose.onNodeWithText("Apply").performClick()
        assertEquals(original.copy(cornerRadius=18f),saved)
    }

}
