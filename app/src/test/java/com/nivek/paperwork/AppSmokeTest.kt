package com.nivek.paperwork

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import android.os.Looper
import org.robolectric.Shadows
import androidx.lifecycle.ViewModelProvider
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import java.io.File
import org.junit.Assert.*
import androidx.activity.compose.setContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], qualifiers="w411dp-h891dp-mdpi", shadows=[PreviewRendererShadow::class, PreviewPageShadow::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppSmokeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun exportScreenRenders() {
        var selected: List<Int>?=null
        compose.activity.setContent { PaperworkTheme { ExportOptions(Draft("test","Test",3),{}, {pages,share -> assertFalse(share); selected=pages}) } }
        compose.onNode(hasSetTextAction()).performTextInput("3, 1")
        compose.onNodeWithText("2 of 3 pages selected").assertIsDisplayed()
        compose.onNodeWithText("Save PDF").performClick()
        assertEquals(listOf(2,0),selected)
        compose.onNode(hasSetTextAction()).performTextReplacement("4")
        compose.onNodeWithText("Save PDF").assertIsNotEnabled()
    }
    @Test fun opensHomeAndSignatureDrawingPad() {
        compose.onNodeWithText("Open a PDF").assertIsDisplayed()
        compose.onNodeWithText("Manage saved signatures (0)").performClick()
        compose.onNodeWithText("Draw a new signature").performClick()
        compose.onNodeWithText("Draw your signature").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Saved signatures").assertIsDisplayed()
    }
    @Test fun acceptsLandscapeDrawingThenNamesAndSavesInPortrait() {
        compose.onNodeWithText("Manage saved signatures (0)").performClick()
        compose.onNodeWithText("Draw a new signature").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Landscape initials")
        compose.onNodeWithText("Save signature").assertIsNotEnabled()
        compose.onNodeWithText("Draw in landscape").performClick()
        compose.waitForIdle()
        val shadow=Shadows.shadowOf(compose.activity)
        val request=shadow.nextStartedActivityForResult
        assertEquals(SignatureDrawingActivity::class.java.name,request.intent.component!!.className)
        val strokes=listOf(listOf(InkPoint(.1f,.2f),InkPoint(.9f,.8f)))
        val result=Signature("","",strokes,3f).json().toString()
        compose.runOnUiThread { shadow.receiveResult(request.intent,android.app.Activity.RESULT_OK,android.content.Intent().putExtra("drawing",result)) }
        compose.onNodeWithText("Redraw in landscape").performClick()
        compose.waitForIdle()
        val retry=shadow.nextStartedActivityForResult
        compose.runOnUiThread { shadow.receiveResult(retry.intent,android.app.Activity.RESULT_CANCELED,null) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Landscape initials").assertIsDisplayed()
        compose.onNodeWithText("Save signature").performClick()
        compose.runOnIdle {
            val vm=ViewModelProvider(compose.activity)[EditorModel::class.java]
            val saved=vm.signatures.single { it.name=="Landscape initials" }
            assertEquals(strokes,saved.strokes); assertEquals(3f,saved.aspect,.001f)
            vm.deleteSignature(saved)
        }
    }
    @Test fun importsPdfAddsTextAndUndoesIt() {
        val file=File(compose.activity.cacheDir,"ui-form.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(file) }
        lateinit var vm: EditorModel
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[EditorModel::class.java]; vm.import(Uri.fromFile(file)) }
        compose.waitUntil(20_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vm.busy }
        assertNull(vm.error)
        compose.onNodeWithText("Page 1 / 1").assertIsDisplayed()
        compose.onNodeWithText("Text").performClick()
        compose.runOnIdle { pageEditor().activeInput!!.setText("Hello, Paperwork!") }
        compose.onNodeWithContentDescription("Done editing").performClick()
        assertEquals("Hello, Paperwork!",vm.draft!!.marks.single().text)
        compose.onNodeWithContentDescription("Undo").performClick()
        assertTrue(vm.draft!!.marks.isEmpty())
        compose.onNodeWithContentDescription("Redo").performClick()
        assertEquals(1,vm.draft!!.marks.size)
        // Select once, then tap again: the native input must open over the PDF.
        compose.runOnIdle { tapText(vm.draft!!.marks.single()) }
        compose.runOnIdle { assertNull(pageEditor().activeInput); tapText(vm.draft!!.marks.single()) }
        compose.runOnIdle {
            val input=pageEditor().activeInput!!
            input.setText("Updated"); input.append(" inline")
            assertEquals("Updated inline",vm.files.drafts().single { it.id==vm.draft!!.id }.marks.single().text)
        }
        compose.onNodeWithContentDescription("Done editing").performClick()
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals("Hello, Paperwork!",vm.draft!!.marks.single().text)
        compose.onNodeWithContentDescription("Redo").performClick()
        assertEquals("Updated inline",vm.draft!!.marks.single().text)
        compose.onNodeWithContentDescription("Export").performClick()
        compose.onNodeWithText("Save PDF").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
    }
    @Test fun additionsUseZoomedViewportAndSignaturePickerPreservesIt() {
        val file=File(compose.activity.cacheDir,"viewport-form.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(file) }
        lateinit var vm: EditorModel
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[EditorModel::class.java]; vm.import(Uri.fromFile(file)) }
        compose.waitUntil(20_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vm.busy }
        val view=PageViewport(3f,360f,480f)
        compose.runOnIdle { pageEditor().canvas.restoreViewport(view) }
        var insertionView=view
        var insertionScale=compose.runOnIdle { pageEditor().canvas.pageScale() }
        fun assertCentered() { compose.runOnIdle {
            val mark=vm.draft!!.marks.last()
            assertEquals(insertionView.centerX,mark.x+mark.width/2,.01f)
            assertEquals(insertionView.centerY,mark.y+mark.height/2,.01f)
            assertEquals(insertionScale,pageEditor().canvas.pageScale(),.001f)
        } }
        compose.onNodeWithText("Shape").performClick()
        compose.onNodeWithContentDescription("Circle").performClick()
        assertCentered()
        compose.runOnIdle { insertionView=pageEditor().canvas.viewport()!!; insertionScale=pageEditor().canvas.pageScale() }
        compose.onNodeWithText("Text").performClick()
        assertCentered()
        compose.onNodeWithContentDescription("Done editing").performClick()
        compose.runOnIdle { vm.saveSignature("Initials",listOf(listOf(InkPoint(0f,0f),InkPoint(1f,1f))),3f) }
        compose.runOnIdle { insertionView=pageEditor().canvas.viewport()!!; insertionScale=pageEditor().canvas.pageScale() }
        compose.onNodeWithText("Sign").performClick()
        compose.onNodeWithText("Insert").performClick()
        assertCentered()
        compose.runOnIdle {
            val canvas=pageEditor().canvas; val signature=vm.draft!!.marks.last(); val b=canvas.screenBounds(signature)
            assertTrue(b.width()<=canvas.width*.5f+.01f)
            assertTrue(b.left>=canvas.width*.25f-.01f); assertTrue(b.right<=canvas.width*.75f+.01f)
            assertEquals(3f,signature.width/signature.height,.001f)
        }
        compose.runOnIdle { assertEquals(insertionView.centerX,pageEditor().canvas.viewport()!!.centerX,.01f); assertEquals(insertionView.centerY,pageEditor().canvas.viewport()!!.centerY,.01f); vm.deleteSignature(vm.signatures.single { it.name=="Initials" }) }
    }
    @Test fun quickStylesAndFineNudgesSaveAndUndo() {
        val file=File(compose.activity.cacheDir,"quick-tools.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(file) }
        lateinit var vm: EditorModel
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[EditorModel::class.java]; vm.import(Uri.fromFile(file)) }
        compose.waitUntil(20_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vm.busy }
        compose.onNodeWithText("Shape").performClick(); compose.onNodeWithContentDescription("Square").performClick()
        compose.onNodeWithContentDescription("Quick color").performClick()
        compose.onNodeWithContentDescription("Quick picker tray").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quick #2454B5").performClick()
        assertEquals(0xff2454b5.toInt(),vm.draft!!.marks.last().color)
        compose.onNodeWithContentDescription("Quick stroke width").performClick()
        compose.onNodeWithText("5 pt").performClick()
        assertEquals(5f,vm.draft!!.marks.last().strokeWidth,.001f)
        val initial=vm.draft!!.marks.last()
        var scale=1f
        compose.runOnIdle { pageEditor().canvas.restoreViewport(PageViewport(3f,300f,400f)); scale=pageEditor().canvas.pageScale() }
        compose.onNodeWithContentDescription("Nudge right").performClick()
        assertEquals(initial.x+1f/scale,vm.draft!!.marks.last().x,.001f)
        compose.onNodeWithText("5 px").performClick(); compose.onNodeWithContentDescription("Nudge down").performClick()
        assertEquals(initial.y+5f/scale,vm.draft!!.marks.last().y,.001f)
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(initial.y,vm.draft!!.marks.last().y,.001f)
        compose.onNodeWithText("Shape").performClick(); compose.onNodeWithContentDescription("Circle").performClick()
        assertEquals(0xff2454b5.toInt(),vm.draft!!.marks.last().color)
        assertEquals(5f,vm.draft!!.marks.last().strokeWidth,.001f)
        assertEquals(vm.draft,vm.files.drafts().single { it.id==vm.draft!!.id })
    }
    @Test fun inlineQuickStylesKeepInputAndHideExtraToolbars() {
        val file=File(compose.activity.cacheDir,"keyboard-tools.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(file) }
        lateinit var vm: EditorModel
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[EditorModel::class.java]; vm.import(Uri.fromFile(file)) }
        compose.waitUntil(20_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vm.busy }
        compose.onNodeWithContentDescription("Nudge right").assertDoesNotExist()
        compose.onNodeWithContentDescription("Fit page").assertDoesNotExist()
        val scale=compose.runOnIdle { pageEditor().canvas.pageScale() }
        compose.onNodeWithText("Text").performClick()
        val input=compose.runOnIdle { pageEditor().activeInput!!.also { it.setText("Hello"); it.setSelection(2) } }
        listOf("Text","Shape","Sign","Doodle").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.onNodeWithContentDescription("Nudge right").assertDoesNotExist()
        compose.onNodeWithContentDescription("Quick color").performClick()
        compose.onNodeWithContentDescription("Quick #2454B5").performClick()
        compose.onNodeWithContentDescription("Quick text size").performClick()
        compose.onNodeWithText("24 pt").performClick()
        compose.runOnIdle {
            assertSame(input,pageEditor().activeInput); assertTrue(input.hasFocus()); assertEquals(2,input.selectionStart)
            assertEquals(scale,pageEditor().canvas.pageScale(),.001f)
            assertEquals(24f,vm.draft!!.marks.last().size,.001f)
            assertEquals(0xff2454b5.toInt(),input.currentTextColor)
        }
        compose.onNodeWithContentDescription("Quick color").performClick()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        compose.onNodeWithText("Hex (RRGGBB or RRGGBBAA)").performScrollTo().performTextReplacement("88000080")
        compose.onNodeWithText("Apply color").performClick()
        compose.runOnIdle { assertSame(input,pageEditor().activeInput); assertEquals(2,input.selectionStart); assertEquals(0x80880000.toInt(),input.currentTextColor) }
        compose.onNodeWithContentDescription("Done editing").performClick()
        compose.onNodeWithText("Doodle").assertIsDisplayed()
        compose.onNodeWithContentDescription("Nudge right").assertIsDisplayed()
        compose.onNodeWithContentDescription("Quick color").performClick(); compose.onNodeWithContentDescription("Quick transparent").performClick()
        compose.onNodeWithText("Doodle").performClick()
        compose.runOnIdle { assertTrue(android.graphics.Color.alpha(pageEditor().canvas.defaultColor)>0) }
        compose.onNodeWithContentDescription("Quick color").performClick()
        compose.onNodeWithContentDescription("Quick transparent").assertDoesNotExist()
    }
    private fun pageEditor(): PageEditor {
        fun find(view: View): PageEditor? {
            if(view is PageEditor) return view
            if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return find(compose.activity.window.decorView)!!
    }
    private fun tapText(mark: Mark) {
        val canvas=pageEditor().canvas; val b=canvas.screenBounds(mark)
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP).forEach { action ->
            MotionEvent.obtain(0,0,action,b.centerX(),b.centerY(),0).let { canvas.onTouchEvent(it); it.recycle() }
        }
    }
    @Test fun visualShapePickerAndIndependentStyleControls() {
        val file=File(compose.activity.cacheDir,"shape-form.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(file) }
        lateinit var vm: EditorModel
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[EditorModel::class.java]; vm.import(Uri.fromFile(file)) }
        compose.waitUntil(20_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vm.busy }
        compose.onNodeWithText("Shape").performClick()
        compose.onNodeWithContentDescription("Circle").performClick()
        val oldHeight=vm.draft!!.marks.single().height
        compose.onNodeWithContentDescription("Edit style").performClick()
        val previewBounds=compose.onNodeWithContentDescription("Style preview").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Dashed").performClick()
        compose.onNodeWithContentDescription("Stroke width").performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        compose.onNodeWithText("Fill color").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Box width").assertDoesNotExist()
        compose.onNodeWithContentDescription("Box height").assertDoesNotExist()
        assertEquals(previewBounds,compose.onNodeWithContentDescription("Style preview").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Style preview").assertIsDisplayed()
        compose.onNodeWithText("Apply").performClick()
        val mark=vm.draft!!.marks.single()
        assertEquals("Dashed",mark.strokeStyle); assertEquals(5f,mark.strokeWidth,.01f)
        assertEquals(48f,mark.width,.01f); assertEquals(oldHeight,mark.height,.01f)
        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test fun lineArrowheadsAndDoodleWorkFromTheEditor() {
        val file=File(compose.activity.cacheDir,"drawing-form.pdf")
        PDDocument().use { doc -> doc.addPage(PDPage()); doc.save(file) }
        lateinit var vm: EditorModel
        compose.runOnUiThread { vm=ViewModelProvider(compose.activity)[EditorModel::class.java]; vm.import(Uri.fromFile(file)) }
        compose.waitUntil(20_000) { Shadows.shadowOf(Looper.getMainLooper()).idle(); !vm.busy }
        compose.onNodeWithText("Shape").performClick()
        compose.onNodeWithContentDescription("Oval").assertDoesNotExist()
        compose.onNodeWithContentDescription("Line").performClick()
        compose.onNodeWithContentDescription("Edit style").performClick()
        compose.onNodeWithContentDescription("Start arrowhead: Circle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("End arrowhead: Triangle").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals("Circle",vm.draft!!.marks.single().startArrow); assertEquals("Triangle",vm.draft!!.marks.single().endArrow)
        compose.onNodeWithText("Doodle").performClick()
        compose.runOnIdle {
            val canvas=pageEditor().canvas
            val b=canvas.screenBounds(Mark(x=100f,y=220f,width=100f,height=60f))
            fun event(action: Int,x: Float,y: Float) { MotionEvent.obtain(0,0,action,x,y,0).let { canvas.onTouchEvent(it); it.recycle() } }
            event(MotionEvent.ACTION_DOWN,b.left,b.top); event(MotionEvent.ACTION_MOVE,b.centerX(),b.bottom); event(MotionEvent.ACTION_UP,b.right,b.top)
            event(MotionEvent.ACTION_DOWN,b.right+10,b.top); event(MotionEvent.ACTION_UP,b.right+10,b.top)
        }
        assertEquals(2,vm.draft!!.marks.size); assertEquals(2,vm.draft!!.marks.last().strokes.size)
        compose.onNodeWithContentDescription("Undo").performClick()
        assertEquals(1,vm.draft!!.marks.last().strokes.size)
        compose.onNodeWithContentDescription("Redo").performClick()
        assertEquals(2,vm.draft!!.marks.last().strokes.size)
        compose.onNodeWithContentDescription("Finish doodle").performClick()
        compose.runOnIdle { assertFalse(pageEditor().canvas.drawingMode) }
        assertEquals(vm.draft,vm.files.drafts().single { it.id==vm.draft!!.id })
    }

}
