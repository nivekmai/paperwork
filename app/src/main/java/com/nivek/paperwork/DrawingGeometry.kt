package com.nivek.paperwork

import kotlin.math.*

val arrowheadStyles=listOf("None","Open","Triangle","Circle","Diamond","Bar")
fun Mark.lineStartOnPage()=InkPoint(x+lineStart.x*width,y+lineStart.y*height)
fun Mark.lineEndOnPage()=InkPoint(x+lineEnd.x*width,y+lineEnd.y*height)

private fun drawingBounds(low: Float,high: Float,pad: Float,limit: Float): Pair<Float,Float> {
    val size=max(min(8f,limit),high-low+pad*2).coerceAtMost(limit)
    val origin=(low-pad).coerceIn(0f,max(0f,limit-size))
    return origin to size
}
fun lineWithEndpoints(m: Mark,start: InkPoint,end: InkPoint,pageWidth: Float,pageHeight: Float): Mark {
    val a=InkPoint(start.x.coerceIn(0f,pageWidth),start.y.coerceIn(0f,pageHeight))
    val b=InkPoint(end.x.coerceIn(0f,pageWidth),end.y.coerceIn(0f,pageHeight))
    if(hypot(b.x-a.x,b.y-a.y)<2f) return m
    val (x,w)=drawingBounds(min(a.x,b.x),max(a.x,b.x),4f,pageWidth)
    val (y,h)=drawingBounds(min(a.y,b.y),max(a.y,b.y),4f,pageHeight)
    return m.copy(x=x,y=y,width=w,height=h,lineStart=InkPoint((a.x-x)/w,(a.y-y)/h),lineEnd=InkPoint((b.x-x)/w,(b.y-y)/h))
}
/** Snap the head around the fixed tail; shorten along the snapped ray at page edges. */
fun snappedLineHead(tail: InkPoint,head: InkPoint,pageWidth: Float,pageHeight: Float): InkPoint {
    val dx=head.x-tail.x; val dy=head.y-tail.y
    val rawAngle=atan2(dy,dx).toDouble()
    val step=round(rawAngle/(PI/4)).toInt()
    val angle=step*PI/4
    if(abs(rawAngle-angle)>Math.toRadians(2.0)+1e-6) return head
    // Exact zeroes keep horizontal and vertical lines stable at page boundaries.
    val ux=if(step%2==0) round(cos(angle)).toFloat() else cos(angle).toFloat()
    val uy=if(step%2==0) round(sin(angle)).toFloat() else sin(angle).toFloat()
    fun room(origin: Float,direction: Float,limit: Float)=when {
        direction>0f -> (limit-origin)/direction
        direction<0f -> -origin/direction
        else -> Float.POSITIVE_INFINITY
    }
    val length=min(hypot(dx,dy),min(room(tail.x,ux,pageWidth),room(tail.y,uy,pageHeight))).coerceAtLeast(0f)
    return InkPoint(tail.x+ux*length,tail.y+uy*length)
}
fun distanceToLine(point: InkPoint,start: InkPoint,end: InkPoint): Float {
    val dx=end.x-start.x; val dy=end.y-start.y
    val length2=dx*dx+dy*dy
    if(length2==0f) return hypot(point.x-start.x,point.y-start.y)
    val t=(((point.x-start.x)*dx+(point.y-start.y)*dy)/length2).coerceIn(0f,1f)
    return hypot(point.x-start.x-t*dx,point.y-start.y-t*dy)
}
fun Mark.strokesOnPage()=strokes.map { s -> s.map { InkPoint(x+it.x*width,y+it.y*height) } }
/** Crop to the ink with room for round caps, keeping every stroke in page coordinates. */
fun doodleFromStrokes(m: Mark,ink: List<List<InkPoint>>,pageWidth: Float,pageHeight: Float): Mark {
    val strokes=ink.filter { it.isNotEmpty() }.map { s -> s.map { InkPoint(it.x.coerceIn(0f,pageWidth),it.y.coerceIn(0f,pageHeight)) } }
    val points=strokes.flatten(); require(points.isNotEmpty())
    val pad=m.strokeWidth/2+1f
    val (x,w)=drawingBounds(points.minOf { it.x },points.maxOf { it.x },pad,pageWidth)
    val (y,h)=drawingBounds(points.minOf { it.y },points.maxOf { it.y },pad,pageHeight)
    return m.copy(kind="Doodle",x=x,y=y,width=w,height=h,strokes=strokes.map { s -> s.map { InkPoint((it.x-x)/w,(it.y-y)/h) } })
}
