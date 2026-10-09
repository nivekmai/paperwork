package com.nivek.paperwork

import kotlin.math.hypot

/** Observes raw screen coordinates; never filters the live drag preview. */
internal class DragRelease {
    private data class Sample(val time: Long,val x: Float,val y: Float,val mark: Mark)
    private var anchor: Sample?=null
    private var releaseAnchor: Sample?=null
    private var departureTime=0L

    fun reset() { anchor=null; releaseAnchor=null }

    fun record(time: Long,x: Float,y: Float,mark: Mark) {
        val sample=Sample(time,x,y,mark)
        releaseAnchor?.let {
            if(time-departureTime>100 || hypot(x-it.x,y-it.y)>8f) releaseAnchor=null
        }
        val old=anchor
        if(old==null) { anchor=sample; return }
        if(hypot(x-old.x,y-old.y)>1f) {
            // A pause can contain no MOVE events at all. Event time still captures it.
            if(releaseAnchor==null && time-old.time>=100) {
                releaseAnchor=old
                departureTime=time
                if(hypot(x-old.x,y-old.y)>8f) releaseAnchor=null
            }
            anchor=sample
        }
    }

    fun finish(time: Long,latest: Mark): Mark {
        val settled=releaseAnchor
        val result=if(settled!=null && time-departureTime in 0L..100L) settled.mark else latest
        reset()
        return result
    }
}
