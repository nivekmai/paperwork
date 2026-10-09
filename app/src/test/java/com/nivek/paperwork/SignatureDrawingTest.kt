package com.nivek.paperwork

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w891dp-h411dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SignatureDrawingTest {
    @get:Rule val compose=createAndroidComposeRule<SignatureDrawingActivity>()
    private fun pad(): SignaturePad {
        fun find(v: View): SignaturePad? {
            if(v is SignaturePad) return v
            if(v is ViewGroup) for(i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
            return null
        }
        return find(compose.activity.window.decorView)!!
    }
    private fun stroke() { compose.runOnIdle {
        val p=pad()
        listOf(Triple(MotionEvent.ACTION_DOWN,50f,50f),Triple(MotionEvent.ACTION_MOVE,200f,120f),Triple(MotionEvent.ACTION_UP,220f,140f)).forEach { (a,x,y) ->
            MotionEvent.obtain(0,0,a,x,y,0).let { p.onTouchEvent(it); it.recycle() }
        }
    } }
    @Test fun landscapePadUsesAvailableSpaceAndReturnsDrawingOnly() {
        compose.onNodeWithContentDescription("Accept drawing").assertIsNotEnabled()
        compose.runOnIdle {
            val a=compose.activity
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,a.packageManager.getActivityInfo(a.componentName,0).screenOrientation)
            assertTrue(pad().width>700); assertTrue(pad().height>300)
        }
        val undo=compose.onNodeWithContentDescription("Undo stroke").fetchSemanticsNode().boundsInRoot
        val clear=compose.onNodeWithContentDescription("Clear signature").fetchSemanticsNode().boundsInRoot
        val accept=compose.onNodeWithContentDescription("Accept drawing").fetchSemanticsNode().boundsInRoot
        assertEquals(undo.center.x,clear.center.x,.1f); assertEquals(clear.center.x,accept.center.x,.1f)
        assertTrue(undo.bottom<clear.top && clear.bottom<accept.top)
        stroke()
        compose.onNodeWithContentDescription("Undo stroke").performClick()
        compose.onNodeWithContentDescription("Accept drawing").assertIsNotEnabled()
        stroke()
        compose.onNodeWithContentDescription("Clear signature").performClick()
        compose.onNodeWithContentDescription("Accept drawing").assertIsNotEnabled()
        stroke()
        compose.onNodeWithContentDescription("Accept drawing").performClick()
        val shadow=Shadows.shadowOf(compose.activity)
        assertEquals(Activity.RESULT_OK,shadow.resultCode)
        val result=JSONObject(shadow.resultIntent.getStringExtra("drawing")!!)
        assertEquals(1,readInk(result.getJSONArray("strokes")).size)
        assertTrue(result.getDouble("aspect")>1)
    }
    @Test fun drawingSurvivesRecreationAndCanBeCancelled() {
        stroke()
        val before=compose.runOnIdle { pad().capture() }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Accept drawing").assertIsEnabled()
        compose.runOnIdle {
            val after=pad().capture()
            assertEquals(before.second,after.second,.0001f)
            assertEquals(before.first.map { it.size },after.first.map { it.size })
            before.first.flatten().zip(after.first.flatten()).forEach { (a,b) -> assertEquals(a.x,b.x,.0001f); assertEquals(a.y,b.y,.0001f) }
        }
        compose.onNodeWithContentDescription("Cancel drawing").performClick()
        assertEquals(Activity.RESULT_CANCELED,Shadows.shadowOf(compose.activity).resultCode)
    }
}
