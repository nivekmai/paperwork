package com.nivek.paperwork

import kotlin.math.*

data class PlacementSnap(val mark: Mark,val xGuide: Float?=null,val yGuide: Float?=null)
/** All three moving anchors can align with any edge or center on the same page. */
fun snapPlacement(mark: Mark,others: List<Mark>,pageWidth: Float,pageHeight: Float,scale: Float): PlacementSnap {
    val tolerance=2f/scale.coerceAtLeast(.01f)
    fun axis(origin: Float,length: Float,limit: Float,targets: List<Float>): Pair<Float,Float?> {
        var best=tolerance+1e-5f; var result=origin; var guide: Float?=null
        for(offset in listOf(0f,length/2,length)) for(target in targets) {
            val candidate=target-offset; val distance=abs(candidate-origin)
            if(candidate>=0f && candidate+length<=limit && distance<=tolerance && distance<best) {
                best=distance; result=candidate; guide=target
            }
        }
        return result to guide
    }
    val peers=others.filter { it.id!=mark.id && it.page==mark.page }
    val xs=listOf(0f,pageWidth/2,pageWidth)+peers.flatMap { listOf(it.x,it.x+it.width/2,it.x+it.width) }
    val ys=listOf(0f,pageHeight/2,pageHeight)+peers.flatMap { listOf(it.y,it.y+it.height/2,it.y+it.height) }
    val (x,gx)=axis(mark.x,mark.width,pageWidth,xs); val (y,gy)=axis(mark.y,mark.height,pageHeight,ys)
    return PlacementSnap(mark.copy(x=x,y=y),gx,gy)
}
fun lineSnapAngle(tail: InkPoint,head: InkPoint): Int? {
    val dx=head.x-tail.x; val dy=head.y-tail.y
    if(hypot(dx,dy)<2f) return null
    val angle=atan2(dy,dx).toDouble(); val step=round(angle/(PI/4)).toInt()
    return if(abs(angle-step*PI/4)<=Math.toRadians(2.0)+1e-6) (step+8)%8 else null
}
/** A detent fires once on entry or change, not on every move while held. */
class SnapFeedback {
    private var previous=emptySet<String>()
    fun update(current: Set<String>): Boolean { val bump=(current-previous).isNotEmpty(); previous=current; return bump }
    fun reset() { previous=emptySet() }
}
