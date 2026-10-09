package com.nivek.paperwork

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.*

data class PageViewport(val zoom: Float,val centerX: Float,val centerY: Float,val scale: Float?=null)

class PageCanvas(context: Context): View(context) {
    var pageIndex: Int=0
    var page: PageImage? = null
        set(value) { if(field !== value) { field=value; minimumScale=null; zoom=1f; panX=0f; panY=0f; activeInk=null; doodleId=null; onViewportChanged() }; invalidate() }
    var marks: List<Mark> = emptyList()
        set(value) { if(field!=value) { field=value; invalidate() } }
    var selected: String? = null
        set(value) { if(field!=value) { field=value; invalidate() } }
    var editingId: String? = null
        set(value) { field=value; invalidate() }
    var onSelect: (String?) -> Unit = {}
    var onChange: (Mark) -> Unit = {}
    var onEditText: (Mark) -> Unit = {}
    var onViewportChanged: () -> Unit = {}
    var onDoodle: (Mark)->Unit = {}
    var drawingMode: Boolean=false
        set(value) { if(field!=value) { field=value; activeInk=null; doodleId=null; invalidate() } }
    var defaultColor: Int=0xff172c27.toInt()
    var defaultStroke: Float=2f
    private val snapFeedback=SnapFeedback()
    private val dragRelease=DragRelease()
    private var alignment: PlacementSnap?=null
    private fun snapBump(keys: Set<String>) { if(snapFeedback.update(keys)) performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
    fun nudge(mark: Mark,dxPixels: Float,dyPixels: Float): Mark {
        val p=page ?: return mark
        return resizeMark(mark,ResizeHandle.NONE,dxPixels/pageScale(),dyPixels/pageScale(),p.width,p.height)
    }
    private var activeInk: MutableList<InkPoint>?=null
    private var doodleId: String?=null
    private var inkCancelled=false
    private var focusX=0f; private var focusY=0f
    private var minimumScale: Float?=null
    private var zoom=1f; private var panX=0f; private var panY=0f
    private var dragging: Mark?=null; private var preview: Mark?=null
    private var handle=ResizeHandle.NONE
    private var downX=0f; private var downY=0f; private var lastX=0f; private var lastY=0f
    private var moved=false; private var selectedBeforeDown=false; private var gestureScaled=false
    private val density=resources.displayMetrics.density
    private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private fun fit(): Float { val p=page ?: return 1f; return min((width-24*density)/p.width,(height-24*density)/p.height).coerceAtLeast(.01f) }
    fun pageScale()=fit()*zoom
    private fun left()=(width-(page?.width ?: 0f)*pageScale())/2+panX
    private fun top()=(height-(page?.height ?: 0f)*pageScale())/2+panY
    fun screenBounds(m: Mark)=RectF(left()+m.x*pageScale(),top()+m.y*pageScale(),left()+(m.x+m.width)*pageScale(),top()+(m.y+m.height)*pageScale())
    private val detector=ScaleGestureDetector(context,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
            dragRelease.reset(); gestureScaled=true; dragging=null; preview=null; alignment=null; snapFeedback.reset(); activeInk=null; inkCancelled=true
            focusX=d.focusX; focusY=d.focusY; return true
        }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val old=zoom; val base=minimumScale ?: fit(); zoom=(pageScale()*d.scaleFactor).coerceIn(base,base*5f)/fit()
            val ratio=zoom/old
            panX=d.focusX-width/2-(focusX-width/2-panX)*ratio
            panY=d.focusY-height/2-(focusY-height/2-panY)*ratio
            focusX=d.focusX; focusY=d.focusY
            clampPan(); changedViewport(); return true
        }
    })
    init { contentDescription="PDF page. Tap to select; tap selected text again to type. Drag to move. Corner keeps proportions; edge handles resize one dimension. Pinch to zoom."; isFocusable=true }
    private fun changedViewport() { invalidate(); onViewportChanged() }
    private val lastPosition=IntArray(2)
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        val p=page
        if(p!=null && minimumScale==null) minimumScale=fit()
        val position=IntArray(2); getLocationOnScreen(position)
        val previous=if(p!=null && oldw>0 && oldh>0) {
            val oldScale=min((oldw-24*density)/p.width,(oldh-24*density)/p.height).coerceAtLeast(.01f)*zoom
            PageViewport(zoom,p.width/2-panX/oldScale+(w-oldw)/2f/oldScale+(position[0]-lastPosition[0])/oldScale,p.height/2-panY/oldScale+(h-oldh)/2f/oldScale+(position[1]-lastPosition[1])/oldScale,oldScale)
        } else null
        position.copyInto(lastPosition)
        val target=pendingViewport ?: previous
        pendingViewport=null
        if(target!=null) restoreViewport(target) else { clampPan(); onViewportChanged() }
    }
    fun ensureTextVisible(mark: Mark) {
        if(width==0 || height==0) return
        val bounds=screenBounds(mark)
        val lineHeight=(mark.size*pageScale()*1.5f).coerceAtMost(height*.5f)
        val targetTop=bounds.top.coerceIn(8f,max(8f,height-lineHeight-8f))
        if(targetTop!=bounds.top) { panY+=targetTop-bounds.top; changedViewport() }
    }
    fun resetZoom() { zoom=1f; panX=0f; panY=0f; changedViewport() }
    private fun clampPan() {
        val p=page ?: return
        val maxX=max(0f,(p.width*pageScale()-width)/2+48*density)
        val maxY=max(0f,(p.height*pageScale()-height)/2+48*density)
        panX=panX.coerceIn(-maxX,maxX); panY=panY.coerceIn(-maxY,maxY)
    }
    private var pendingViewport: PageViewport?=null
    fun viewport(): PageViewport? = page?.let {
        if(width==0 || height==0) pendingViewport else PageViewport(zoom,(width/2f-left())/pageScale(),(height/2f-top())/pageScale(),pageScale())
    }
    fun restoreViewport(view: PageViewport) {
        if(width==0 || height==0) { pendingViewport=view; return }
        val p=page ?: return
        zoom=view.scale?.let { it/fit() } ?: view.zoom.coerceIn(1f,5f)
        panX=(p.width/2-view.centerX)*pageScale()
        panY=(p.height/2-view.centerY)*pageScale()
        clampPan(); changedViewport()
    }
    fun centerInViewport(mark: Mark): Mark {
        val p=page ?: return mark
        val view=viewport() ?: PageViewport(1f,p.width/2,p.height/2)
        return mark.copy(x=(view.centerX-mark.width/2).coerceIn(0f,max(0f,p.width-mark.width)),
            y=(view.centerY-mark.height/2).coerceIn(0f,max(0f,p.height-mark.height)))
    }
    /** Apply the screen-space cap only when inserting; later resizing remains unrestricted. */
    fun signatureForInsertion(mark: Mark): Mark {
        val scale=if(width>0 && height>0) min(1f,min(width*.5f/pageScale()/mark.width,height*.5f/pageScale()/mark.height)) else 1f
        return centerInViewport(mark.copy(width=mark.width*scale,height=mark.height*scale))
    }
    private fun handles(m: Mark): List<Triple<ResizeHandle,Float,Float>> {
        if(m.kind=="Line") {
            val a=m.lineStartOnPage(); val b=m.lineEndOnPage()
            return listOf(Triple(ResizeHandle.LINE_START,a.x,a.y),Triple(ResizeHandle.LINE_END,b.x,b.y))
        }
        return listOf(
        Triple(ResizeHandle.CORNER,m.x+m.width,m.y+m.height),
        Triple(ResizeHandle.LEFT,m.x,m.y+m.height/2),
        Triple(ResizeHandle.RIGHT,m.x+m.width,m.y+m.height/2),
        Triple(ResizeHandle.TOP,m.x+m.width/2,m.y),
        Triple(ResizeHandle.BOTTOM,m.x+m.width/2,m.y+m.height)
        )
    }
    private fun hitHandle(m: Mark,x: Float,y: Float): ResizeHandle {
        val radius=if(m.kind=="Line") 20*density/pageScale() else min(18*density,max(6*density,min(m.width,m.height)*pageScale()*.22f))/pageScale()
        return handles(m).minByOrNull { (_,hx,hy) -> hypot(x-hx,y-hy) }?.let { (h,hx,hy) -> if(hypot(x-hx,y-hy)<=radius) h else ResizeHandle.NONE } ?: ResizeHandle.NONE
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c); c.drawColor(Color.rgb(226,233,228)); val p=page ?: return
        c.save(); c.translate(left(),top()); c.scale(pageScale(),pageScale())
        paint.color=Color.WHITE; paint.style=Paint.Style.FILL
        c.drawRect(0f,0f,p.width,p.height,paint)
        c.drawBitmap(p.bitmap,null,RectF(0f,0f,p.width,p.height),paint)
        c.save(); c.clipRect(0f,0f,p.width,p.height)
        marks.filterNot { it.id==editingId }.forEach { MarkPainter.draw(c,if(it.id==preview?.id) preview!! else it) }
        activeInk?.let { ink ->
            val doodleStyle=marks.find { it.id==doodleId } ?: Mark(kind="Doodle",strokeWidth=defaultStroke,color=defaultColor)
            val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=doodleStyle.color; strokeWidth=doodleStyle.strokeWidth; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; this.style=Paint.Style.STROKE }
            if(ink.size==1) { p.style=Paint.Style.FILL; c.drawCircle(ink[0].x,ink[0].y,p.strokeWidth/2,p) }
            else if(ink.isNotEmpty()) c.drawPath(Path().apply { moveTo(ink[0].x,ink[0].y); ink.drop(1).forEach { lineTo(it.x,it.y) } },p)
        }
        c.restore()
        alignment?.let { snap ->
            paint.color=Color.rgb(26,117,92); paint.style=Paint.Style.STROKE; paint.strokeWidth=1f/pageScale()
            snap.xGuide?.let { c.drawLine(it,0f,it,p.height,paint) }
            snap.yGuide?.let { c.drawLine(0f,it,p.width,it,paint) }
        }
        val sel=preview ?: marks.find { it.id==selected }
        if(sel!=null && sel.id!=editingId && !drawingMode) {
            paint.color=Color.rgb(26,117,92); paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f*density/pageScale()
            if(sel.kind!="Line") c.drawRect(sel.x,sel.y,sel.x+sel.width,sel.y+sel.height,paint)
            handles(sel).forEach { (handle,x,y) ->
                val radius=(if(handle in listOf(ResizeHandle.CORNER,ResizeHandle.LINE_START,ResizeHandle.LINE_END)) 7 else 4)*density/pageScale()
                paint.style=Paint.Style.FILL; paint.color=if(handle in listOf(ResizeHandle.CORNER,ResizeHandle.LINE_START,ResizeHandle.LINE_END)) Color.rgb(26,117,92) else Color.WHITE
                c.drawCircle(x,y,radius,paint)
                paint.style=Paint.Style.STROKE; paint.color=Color.rgb(26,117,92); c.drawCircle(x,y,radius,paint)
            }
        }
        c.restore()
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val p=page ?: return false
        if(drawingMode) return doodleTouch(e,p)
        detector.onTouchEvent(e)
        val x=(e.x-left())/pageScale(); val y=(e.y-top())/pageScale()
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragRelease.reset(); alignment=null; snapFeedback.reset(); gestureScaled=false; moved=false; downX=e.rawX; downY=e.rawY; lastX=e.rawX; lastY=e.rawY
                val current=marks.find { it.id==selected }
                handle=current?.let { hitHandle(it,x,y) } ?: ResizeHandle.NONE
                val touch=4*density/pageScale()
                val hit=if(handle!=ResizeHandle.NONE) current else marks.lastOrNull {
                    if(it.kind=="Line") distanceToLine(InkPoint(x,y),it.lineStartOnPage(),it.lineEndOnPage())<=max(10*density/pageScale(),it.strokeWidth)
                    else x>=it.x-touch && x<=it.x+it.width+touch && y>=it.y-touch && y<=it.y+it.height+touch
                }
                selectedBeforeDown=hit!=null && hit.id==selected
                selected=hit?.id; onSelect(selected); dragging=hit; preview=hit
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if(hypot(e.rawX-downX,e.rawY-downY)>touchSlop) moved=true
                if(!detector.isInProgress && !gestureScaled && moved) {
                    val m=dragging
                    if(m!=null) {
                        // Consume batched samples too, so release timing is independent of frame rate.
                        for(i in 0..e.historySize) {
                            val rawX=if(i==e.historySize) e.rawX else e.getHistoricalX(i)+e.rawX-e.x
                            val rawY=if(i==e.historySize) e.rawY else e.getHistoricalY(i)+e.rawY-e.y
                            val time=if(i==e.historySize) e.eventTime else e.getHistoricalEventTime(i)
                            val dx=(rawX-downX)/pageScale(); val dy=(rawY-downY)/pageScale()
                            val changed=resizeMark(m,handle,dx,dy,p.width,p.height)
                            if(handle==ResizeHandle.NONE) {
                                val snap=snapPlacement(changed,marks,p.width,p.height,pageScale())
                                alignment=snap; preview=snap.mark
                                snapBump(buildSet { snap.xGuide?.let { add("x:$it") }; snap.yGuide?.let { add("y:$it") } })
                            } else {
                                preview=changed; alignment=null
                                val end=m.lineEndOnPage()
                                val angle=if(handle==ResizeHandle.LINE_END) lineSnapAngle(m.lineStartOnPage(),InkPoint(end.x+dx,end.y+dy)) else null
                                snapBump(if(angle!=null && changed!=m) setOf("angle:$angle") else emptySet())
                            }
                            dragRelease.record(time,rawX,rawY,preview!!)
                        }
                    }
                    else { panX+=e.rawX-lastX; panY+=e.rawY-lastY; clampPan(); onViewportChanged() }
                }
                lastX=e.rawX; lastY=e.rawY; invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val tapped=dragging
                // Ignore UP coordinates and undo only brief, small wobble after a settled hold.
                val committed=preview?.let { dragRelease.finish(e.eventTime,it) }
                if(committed!=null && committed!=dragging && !gestureScaled) onChange(committed)
                val edit=!moved && !gestureScaled && selectedBeforeDown && handle==ResizeHandle.NONE && tapped?.kind=="Text"
                dragging=null; preview=null; alignment=null; snapFeedback.reset(); performClick(); invalidate(); parent?.requestDisallowInterceptTouchEvent(false)
                if(edit) onEditText(tapped!!)
            }
            MotionEvent.ACTION_CANCEL -> { dragRelease.reset(); dragging=null; preview=null; alignment=null; snapFeedback.reset(); invalidate(); parent?.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }
    private fun doodleTouch(e: MotionEvent,p: PageImage): Boolean {
        if(e.actionMasked==MotionEvent.ACTION_DOWN) { inkCancelled=false; gestureScaled=false }
        if(e.actionMasked==MotionEvent.ACTION_POINTER_DOWN) { activeInk=null; inkCancelled=true; invalidate() }
        detector.onTouchEvent(e)
        fun point(x: Float,y: Float)=InkPoint(((x-left())/pageScale()).coerceIn(0f,p.width),((y-top())/pageScale()).coerceIn(0f,p.height))
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x=(e.x-left())/pageScale(); val y=(e.y-top())/pageScale()
                activeInk=if(x in 0f..p.width && y in 0f..p.height) mutableListOf(InkPoint(x,y)) else null
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> if(!inkCancelled && e.pointerCount==1) activeInk?.let { ink ->
                for(i in 0 until e.historySize) { val next=point(e.getHistoricalX(i),e.getHistoricalY(i)); if(next!=ink.lastOrNull()) ink.add(next) }
                val next=point(e.x,e.y); if(next!=ink.lastOrNull()) ink.add(next)
            }
            MotionEvent.ACTION_UP -> {
                val ink=activeInk
                if(!inkCancelled && ink!=null) {
                    val last=point(e.x,e.y); if(last!=ink.lastOrNull()) ink.add(last)
                    val existing=marks.find { it.id==doodleId }
                    val template=existing ?: Mark(kind="Doodle",page=pageIndex,strokeWidth=defaultStroke,color=defaultColor)
                    val next=doodleFromStrokes(template,(existing?.strokesOnPage() ?: emptyList())+listOf(ink.toList()),p.width,p.height)
                    doodleId=next.id; onDoodle(next); selected=next.id
                }
                activeInk=null; performClick(); parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> { activeInk=null; inkCancelled=true; parent?.requestDisallowInterceptTouchEvent(false) }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}

class SignaturePad(context: Context): View(context) {
    private val strokes=mutableListOf<MutableList<InkPoint>>()
    var onInk: (Boolean)->Unit = {}
    init { setBackgroundColor(Color.WHITE); contentDescription="Signature drawing pad" }
    private var restoredInk: List<List<InkPoint>>?=null
    fun saveDrawing(): String = inkJson(strokes.map { s -> s.map { InkPoint(it.x/width.coerceAtLeast(1),it.y/height.coerceAtLeast(1)) } }).toString()
    fun restoreDrawing(json: String) {
        restoredInk=readInk(org.json.JSONArray(json))
        onInk(restoredInk!!.any { it.size>1 })
        if(width>0 && height>0) applyRestoredInk()
    }
    private fun applyRestoredInk() {
        restoredInk?.let { ink ->
            strokes.clear(); strokes.addAll(ink.map { s -> s.map { InkPoint(it.x*width,it.y*height) }.toMutableList() })
            restoredInk=null; invalidate()
        }
    }
    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(restoredInk!=null) applyRestoredInk()
        else if(oldw>0 && oldh>0) strokes.forEach { s -> s.indices.forEach { i -> s[i]=InkPoint(s[i].x*w/oldw,s[i].y*h/oldh) } }
    }
    fun clear() { restoredInk=null; strokes.clear(); invalidate(); onInk(false) }
    fun undo() { if(strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); invalidate(); onInk(strokes.any { it.size>1 }) }
    fun capture(): Pair<List<List<InkPoint>>,Float> {
        val valid=strokes.filter { it.size>1 }; val points=valid.flatten()
        require(points.isNotEmpty()) { "Draw a signature first." }
        val minX=points.minOf { it.x }; val minY=points.minOf { it.y }
        val w=(points.maxOf { it.x }-minX).coerceAtLeast(8f); val h=(points.maxOf { it.y }-minY).coerceAtLeast(8f)
        val pad=5f
        return valid.map { s -> s.map { InkPoint((it.x-minX+pad)/(w+pad*2),(it.y-minY+pad)/(h+pad*2)) } } to ((w+pad*2)/(h+pad*2))
    }
    override fun onDraw(c: Canvas) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(23,44,39); strokeWidth=3*resources.displayMetrics.density; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND; style=Paint.Style.STROKE }
        strokes.forEach { s -> if(s.isNotEmpty()) { val path=Path(); path.moveTo(s[0].x,s[0].y); s.drop(1).forEach { path.lineTo(it.x,it.y) }; c.drawPath(path,p) } }
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); strokes.add(mutableListOf(InkPoint(e.x,e.y))) }
            MotionEvent.ACTION_MOVE -> { val s=strokes.lastOrNull() ?: return true; for(i in 0 until e.historySize) s.add(InkPoint(e.getHistoricalX(i),e.getHistoricalY(i))); s.add(InkPoint(e.x.coerceIn(0f,width.toFloat()),e.y.coerceIn(0f,height.toFloat()))) }
            MotionEvent.ACTION_UP -> { onInk(strokes.any { it.size>1 }); parent?.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> { if(strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); onInk(strokes.any { it.size>1 }); parent?.requestDisallowInterceptTouchEvent(false) }
        }
        invalidate(); return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
