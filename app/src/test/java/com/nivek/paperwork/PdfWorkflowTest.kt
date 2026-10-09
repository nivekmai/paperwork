package com.nivek.paperwork

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PdfWorkflowTest {
    private lateinit var context: Context
    private lateinit var files: PdfFiles
    @Before fun setup() { context=ApplicationProvider.getApplicationContext(); PDFBoxResourceLoader.init(context); files=PdfFiles(context) }
    private fun fixture(): File {
        val file=File(context.cacheDir,"fixture.pdf")
        PDDocument().use { doc ->
            listOf(0,90,180,270).forEachIndexed { i,rotation ->
                val page=PDPage(PDRectangle(640f,840f)); page.cropBox=PDRectangle(20f,20f,600f,800f); page.rotation=rotation; doc.addPage(page)
                PDPageContentStream(doc,page).use { s ->
                    s.beginText(); s.setFont(PDType1Font.HELVETICA,16f); s.newLineAtOffset(40f,700f); s.showText("Original page ${i+1}"); s.endText()
                }
            }
            doc.save(file)
        }
        return file
    }
    @Test fun roundedSquaresPersistAndPaintRoundedFillAndStroke() {
        val mark=Mark(kind="Square",x=0f,y=0f,width=60f,height=40f,cornerRadius=18f,fill=Color.RED,color=Color.BLUE,strokeWidth=2f)
        assertEquals(mark,Mark.from(mark.json()))
        val legacy=mark.json().also { it.remove("cornerRadius") }
        assertEquals(0f,Mark.from(legacy).cornerRadius,.001f)
        fun painted(m: Mark)=Bitmap.createBitmap(65,45,Bitmap.Config.ARGB_8888).also { MarkPainter.draw(Canvas(it),m) }
        val rounded=painted(mark)
        assertEquals(0,Color.alpha(rounded.getPixel(3,3)))
        assertEquals(Color.RED,rounded.getPixel(30,20))
        assertEquals(Color.BLUE,rounded.getPixel(30,1))
        assertEquals(Color.RED,painted(mark.copy(cornerRadius=0f)).getPixel(3,3))
        val small=painted(mark.copy(width=12f,height=10f))
        assertEquals(Color.RED,small.getPixel(6,5))
    }
    @Test fun canvasSnappingRequestsHapticsAndNudgesUseScreenPixels() {
        fun canvas()=PageCanvas(context).apply {
            page=PageImage(Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888),600f,800f)
            layout(0,0,600,900)
        }
        fun event(v: PageCanvas,a: Int,x: Float,y: Float) { MotionEvent.obtain(0,0,a,x,y,0).let { v.onTouchEvent(it); it.recycle() } }
        val v=canvas()
        val m=Mark(kind="Square",x=100f,y=100f,width=40f,height=40f)
        v.marks=listOf(m,Mark(x=200f,y=200f,width=40f,height=40f)); v.selected=m.id
        val b=v.screenBounds(m)
        event(v,MotionEvent.ACTION_DOWN,b.centerX(),b.centerY())
        event(v,MotionEvent.ACTION_MOVE,b.centerX(),b.centerY()+99f*v.pageScale())
        assertEquals(android.view.HapticFeedbackConstants.CLOCK_TICK,org.robolectric.Shadows.shadowOf(v).lastHapticFeedbackPerformed())
        event(v,MotionEvent.ACTION_CANCEL,b.centerX(),b.centerY())
        v.restoreViewport(PageViewport(3f,300f,400f))
        val original=v.screenBounds(m); val nudged=v.screenBounds(v.nudge(m,5f,-1f))
        assertEquals(5f,nudged.left-original.left,.001f); assertEquals(-1f,nudged.top-original.top,.001f)
        val lineCanvas=canvas()
        val line=lineWithEndpoints(Mark(kind="Line"),InkPoint(100f,100f),InkPoint(200f,100f),600f,800f)
        lineCanvas.marks=listOf(line); lineCanvas.selected=line.id
        val bounds=lineCanvas.screenBounds(Mark(x=200f,y=100f,width=1f,height=1f))
        event(lineCanvas,MotionEvent.ACTION_DOWN,bounds.left,bounds.top)
        event(lineCanvas,MotionEvent.ACTION_MOVE,bounds.left,bounds.top+100f*lineCanvas.pageScale())
        assertEquals(android.view.HapticFeedbackConstants.CLOCK_TICK,org.robolectric.Shadows.shadowOf(lineCanvas).lastHapticFeedbackPerformed())
    }
    @Test fun viewportResizePreservesScaleAndSmallDragMovesAreNotFiltered() {
        val v=PageCanvas(context).apply { page=PageImage(Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888),600f,800f); layout(0,0,600,900) }
        v.restoreViewport(PageViewport(3f,300f,400f))
        val scale=v.pageScale(); v.layout(0,0,600,500)
        assertEquals(scale,v.pageScale(),.001f)
        v.layout(0,0,600,900); assertEquals(scale,v.pageScale(),.001f)
        v.resetZoom()
        val m=Mark(kind="Square",x=40f,y=50f,width=60f,height=60f)
        v.marks=listOf(m); v.selected=m.id
        var changed: Mark?=null; v.onChange={changed=it}
        val b=v.screenBounds(m)
        fun event(a: Int,x: Float,y: Float) { MotionEvent.obtain(0,0,a,x,y,0).let { v.onTouchEvent(it); it.recycle() } }
        event(MotionEvent.ACTION_DOWN,b.centerX(),b.centerY())
        event(MotionEvent.ACTION_MOVE,b.centerX()+1,b.centerY()+1)
        event(MotionEvent.ACTION_UP,b.centerX()+2,b.centerY()+2)
        assertNull(changed)
        event(MotionEvent.ACTION_DOWN,b.centerX(),b.centerY())
        val x=b.centerX()+100*v.pageScale(); val y=b.centerY()+120*v.pageScale()
        event(MotionEvent.ACTION_MOVE,x,y)
        // These movements are individually and cumulatively below touch slop, but are deliberate drag updates.
        for(offset in 1..3) event(MotionEvent.ACTION_MOVE,x+offset,y+offset)
        // Lifting contributes extra motion which must not alter the last preview.
        event(MotionEvent.ACTION_UP,x+5,y+5)
        assertEquals(140f+3/v.pageScale(),changed!!.x,.001f)
        assertEquals(170f+3/v.pageScale(),changed!!.y,.001f)
    }
    @Test fun releaseRestoresSettledPlacementIncludingBatchedMoves() {
        for(zoom in listOf(1f,3f)) {
            val v=PageCanvas(context).apply { page=PageImage(Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888),600f,800f); layout(0,0,600,900) }
            v.restoreViewport(PageViewport(zoom,300f,400f))
            val m=Mark(kind="Square",x=240f,y=340f,width=60f,height=60f)
            v.marks=listOf(m); v.selected=m.id
            var changed: Mark?=null; v.onChange={changed=it}
            val b=v.screenBounds(m)
            fun event(time: Long,a: Int,x: Float,y: Float) { MotionEvent.obtain(0,time,a,x,y,0).let { v.onTouchEvent(it); it.recycle() } }
            event(0,MotionEvent.ACTION_DOWN,b.centerX(),b.centerY())
            val x=b.centerX()+30*v.pageScale(); val y=b.centerY()+40*v.pageScale()
            event(100,MotionEvent.ACTION_MOVE,x,y)
            // A held position followed by a small batched wobble immediately before UP.
            MotionEvent.obtain(0,300,MotionEvent.ACTION_MOVE,x+3,y+2,0).let {
                it.addBatch(316,x+4,y+3,1f,1f,0); v.onTouchEvent(it); it.recycle()
            }
            event(340,MotionEvent.ACTION_UP,x+6,y+5)
            assertEquals(270f,changed!!.x,.001f); assertEquals(380f,changed!!.y,.001f)
        }
    }
    @Test fun importDraftExportAndExtractPreserveOriginal() {
        val source=fixture(); val original=source.readBytes()
        val d=files.import(Uri.fromFile(source))
        val marks=(0..3).map { page -> Mark(page=page,kind="Square",x=40f,y=50f,width=70f,height=70f,fill=Color.RED,color=Color.RED) }
        val edited=d.copy(marks=marks)
        files.save(edited)
        assertEquals(edited,files.drafts().single { it.id==edited.id })
        val out=files.export(edited,listOf(3,0,1,2))
        PDDocument.load(out).use { doc ->
            assertEquals(4,doc.numberOfPages)
            assertEquals(listOf(270,0,90,180),(0..3).map { doc.getPage(it).rotation })
            val text=PDFTextStripper().getText(doc)
            assertTrue(text.indexOf("Original page 4")<text.indexOf("Original page 1"))
            (0..3).forEach { assertEquals(20f,doc.getPage(it).cropBox.lowerLeftX); assertTrue(doc.getPage(it).resources.xObjectNames.iterator().hasNext()) }
        }
        assertArrayEquals(original,source.readBytes())
        val extracted=files.export(edited,listOf(2))
        PDDocument.load(extracted).use { assertEquals(1,it.numberOfPages); assertTrue(PDFTextStripper().getText(it).contains("Original page 3")) }
        val artifacts=File("build/test-artifacts").apply { mkdirs() }
        out.copyTo(File(artifacts,"rotated-export.pdf"),overwrite=true)
        extracted.copyTo(File(artifacts,"extracted.pdf"),overwrite=true)
        files.delete(edited); assertTrue(files.drafts().none { it.id==d.id })
    }
    @Test fun savedSignaturesSurviveReloadAndDeletionDoesNotChangePlacedInk() {
        val strokes=listOf(listOf(InkPoint(.1f,.2f),InkPoint(.5f,.8f),InkPoint(.9f,.1f)))
        val signature=Signature("sample","Initials",strokes,2f)
        files.saveSignatures(listOf(signature)); assertEquals(listOf(signature),PdfFiles(context).signatures())
        val d=files.import(Uri.fromFile(fixture())).copy(marks=listOf(Mark(kind="Signature",strokes=strokes)))
        files.save(d); files.saveSignatures(emptyList())
        assertEquals(strokes,files.drafts().single { it.id==d.id }.marks.single().strokes)
    }
    @Test fun textShapesAndTransparentBackgroundReallyRender() {
        listOf("Text","Circle","Square","Oval","Check","X","Signature").forEach { kind ->
            val b=Bitmap.createBitmap(300,150,Bitmap.Config.ARGB_8888)
            MarkPainter.draw(Canvas(b),Mark(kind=kind,x=20f,y=20f,width=150f,height=70f,text="Hello\nWorld",color=Color.BLUE,strokes=listOf(listOf(InkPoint(.1f,.1f),InkPoint(.9f,.9f)))))
            assertEquals("Transparent outside $kind",0,b.getPixel(0,0))
            val pixels=IntArray(300*150); b.getPixels(pixels,0,300,0,0,300,150)
            assertTrue("$kind must paint pixels",pixels.count { Color.alpha(it)>0 }>20)
            b.recycle()
        }
    }
    @Test fun signaturePadNormalizesStrokesAndSupportsUndoAndClear() {
        val pad=SignaturePad(context); pad.layout(0,0,800,300)
        fun touch(action: Int,x: Float,y: Float) { MotionEvent.obtain(0,0,action,x,y,0).let { pad.onTouchEvent(it); it.recycle() } }
        touch(MotionEvent.ACTION_DOWN,100f,100f)
        touch(MotionEvent.ACTION_MOVE,200f,150f)
        touch(MotionEvent.ACTION_UP,200f,150f)
        val (ink,aspect)=pad.capture()
        assertEquals(1,ink.size); assertTrue(aspect>1f)
        assertTrue(ink.flatten().all { it.x in 0f..1f && it.y in 0f..1f })
        pad.undo(); assertTrue(runCatching { pad.capture() }.isFailure)
        touch(MotionEvent.ACTION_DOWN,100f,100f); touch(MotionEvent.ACTION_MOVE,120f,130f)
        pad.clear(); assertTrue(runCatching { pad.capture() }.isFailure)
    }
    @Test fun draggingAndResizingStayInsidePage() {
        val view=PageCanvas(context); view.layout(0,0,600,900)
        view.page=PageImage(Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888),600f,800f)
        val mark=Mark(kind="Square",x=100f,y=100f,width=100f,height=100f)
        view.marks=listOf(mark)
        var changed: Mark?=null; view.onChange={changed=it}
        fun touch(action: Int,x: Float,y: Float) { MotionEvent.obtain(0,0,action,x,y,0).let { view.onTouchEvent(it); it.recycle() } }
        touch(MotionEvent.ACTION_DOWN,150f,200f)
        touch(MotionEvent.ACTION_MOVE,2000f,2000f)
        touch(MotionEvent.ACTION_UP,2000f,2000f)
        assertEquals(500f,changed!!.x,.01f); assertEquals(700f,changed!!.y,.01f)
        // Original mark remains selected in this isolated view; drag its corner.
        touch(MotionEvent.ACTION_DOWN,204f,258f)
        touch(MotionEvent.ACTION_MOVE,2000f,2000f)
        touch(MotionEvent.ACTION_UP,2000f,2000f)
        assertEquals(changed!!.width,changed!!.height,.01f)
        assertTrue(changed!!.x+changed!!.width<=600f)
        assertTrue(changed!!.y+changed!!.height<=800f)
    }

    @Test fun legacyShapesKeepTheirStrokeAndMigrateBackgroundToFill() {
        val old=Mark(kind="Circle",size=24f,background=Color.YELLOW).json()
        old.remove("strokeWidth"); old.remove("strokeStyle"); old.remove("fill")
        val restored=Mark.from(old)
        assertEquals(3f,restored.strokeWidth,0f)
        assertEquals("Solid",restored.strokeStyle)
        assertEquals(Color.YELLOW,restored.fill)
        assertEquals(restored,Mark.from(restored.json()))
    }
    @Test fun ellipseFillDoesNotPaintItsBoundingBoxAndStrokeStylesPersist() {
        val m=Mark(kind="Circle",x=10f,y=10f,width=160f,height=80f,strokeWidth=6f,color=Color.BLUE,fill=Color.RED)
        val b=Bitmap.createBitmap(200,120,Bitmap.Config.ARGB_8888)
        MarkPainter.draw(Canvas(b),m)
        assertEquals(Color.RED,b.getPixel(90,50))
        assertEquals(0,b.getPixel(11,11))
        assertEquals(Color.BLUE,b.getPixel(90,13))
        val dashed=m.copy(strokeStyle="Dashed",fill=0)
        assertEquals(dashed,Mark.from(dashed.json()))
        b.eraseColor(0); MarkPainter.draw(Canvas(b),dashed)
        assertEquals(0,b.getPixel(90,50))
        b.recycle()
    }
    @Test fun tapAgainEditsTextButDraggingAndHandlesDoNot() {
        val view=PageCanvas(context); view.layout(0,0,600,900)
        view.page=PageImage(Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888),600f,800f)
        val m=Mark(x=100f,y=100f,width=200f,height=100f,text="Original")
        view.marks=listOf(m)
        var edits=0; view.onEditText={edits++}
        fun touch(action: Int,x: Float,y: Float) { MotionEvent.obtain(0,0,action,x,y,0).let { view.onTouchEvent(it); it.recycle() } }
        val b=view.screenBounds(m)
        fun tap(x: Float,y: Float) { touch(MotionEvent.ACTION_DOWN,x,y); touch(MotionEvent.ACTION_UP,x,y) }
        tap(b.centerX(),b.centerY()); assertEquals(0,edits)
        tap(b.centerX(),b.centerY()); assertEquals(1,edits)
        tap(b.right,b.bottom); assertEquals(1,edits)
        touch(MotionEvent.ACTION_DOWN,b.centerX(),b.centerY())
        touch(MotionEvent.ACTION_MOVE,b.centerX()+40,b.centerY()+20)
        touch(MotionEvent.ACTION_UP,b.centerX()+40,b.centerY()+20)
        assertEquals(1,edits)
    }

    @Test fun allArrowheadsRenderAndOldOvalsStillLoad() {
        val old=Mark(kind="Oval",width=140f,height=50f).json()
        listOf("lineStartX","lineStartY","lineEndX","lineEndY","startArrow","endArrow").forEach { old.remove(it) }
        val restored=Mark.from(old); assertEquals("Oval",restored.kind); assertEquals("None",restored.endArrow)
        val counts=mutableListOf<Int>()
        arrowheadStyles.forEach { head ->
            val m=Mark(kind="Line",x=20f,y=20f,width=180f,height=50f,strokeWidth=4f,color=Color.BLUE,startArrow=head,endArrow=head)
            assertEquals(m,Mark.from(m.json()))
            val b=Bitmap.createBitmap(240,100,Bitmap.Config.ARGB_8888)
            MarkPainter.draw(Canvas(b),m)
            val pixels=IntArray(240*100); b.getPixels(pixels,0,240,0,0,240,100)
            counts.add(pixels.count { Color.alpha(it)>0 }); b.recycle()
        }
        assertTrue(counts.all { it>100 }); assertTrue(counts.drop(1).all { it>counts[0] })
    }
    @Test fun doodlesRenderDotsAndSurviveExportWithArrows() {
        val d=files.import(Uri.fromFile(fixture()))
        val line=Mark(kind="Line",x=40f,y=60f,width=200f,height=40f,color=Color.BLUE,strokeWidth=4f,startArrow="Circle",endArrow="Triangle")
        val ink=listOf(listOf(InkPoint(60f,160f),InkPoint(110f,200f),InkPoint(160f,160f)),listOf(InkPoint(180f,180f)))
        val doodle=doodleFromStrokes(Mark(kind="Doodle",color=Color.RED,strokeWidth=6f),ink,600f,800f)
        val edited=d.copy(marks=listOf(line,doodle)); files.save(edited)
        assertEquals(edited,files.drafts().single { it.id==d.id })
        val b=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888)
        MarkPainter.draw(Canvas(b),doodle); assertEquals(Color.RED,b.getPixel(180,180)); b.recycle()
        val out=files.export(edited,listOf(0))
        val artifacts=File("build/test-artifacts").apply { mkdirs() }; out.copyTo(File(artifacts,"drawing-export.pdf"),overwrite=true)
    }
    @Test fun doodleGesturesGroupStrokesAndCancelInterruptedInk() {
        val v=PageCanvas(context); v.layout(0,0,600,900)
        v.page=PageImage(Bitmap.createBitmap(600,800,Bitmap.Config.ARGB_8888),600f,800f); v.pageIndex=2; v.drawingMode=true
        val saved=mutableListOf<Mark>(); v.onDoodle={ saved.add(it); v.marks=listOf(it) }
        fun touch(action: Int,x: Float,y: Float) { MotionEvent.obtain(0,0,action,x,y,0).let { v.onTouchEvent(it); it.recycle() } }
        touch(MotionEvent.ACTION_DOWN,100f,200f); touch(MotionEvent.ACTION_MOVE,140f,220f); touch(MotionEvent.ACTION_UP,160f,200f)
        touch(MotionEvent.ACTION_DOWN,190f,220f); touch(MotionEvent.ACTION_UP,190f,220f)
        assertEquals(2,saved.size); assertEquals(saved[0].id,saved[1].id); assertEquals(2,saved.last().strokes.size); assertEquals(2,saved.last().page)
        touch(MotionEvent.ACTION_DOWN,200f,300f); touch(MotionEvent.ACTION_CANCEL,210f,310f)
        assertEquals(2,saved.size)
        v.drawingMode=false; v.drawingMode=true
        touch(MotionEvent.ACTION_DOWN,220f,330f); touch(MotionEvent.ACTION_UP,220f,330f)
        assertNotEquals(saved[1].id,saved.last().id)
    }

}
