package com.nivek.paperwork

import org.junit.Assert.assertEquals
import org.junit.Test

class DragReleaseTest {
    private val settled=Mark(kind="Square",x=100f,y=100f)
    private val moved=settled.copy(x=103f,y=102f)
    @Test fun briefLiftWobbleReturnsSettledPosition() {
        val drag=DragRelease()
        drag.record(100,100f,100f,settled)
        drag.record(300,103f,102f,moved)
        assertEquals(settled,drag.finish(340,moved))
    }
    @Test fun continuousSmallMovesRemainExact() {
        val drag=DragRelease()
        for(i in 0..20) drag.record(i*16L,100f+i,100f,settled.copy(x=100f+i))
        val last=settled.copy(x=120f)
        assertEquals(last,drag.finish(330,last))
    }
    @Test fun heldAdjustmentIsKept() {
        val drag=DragRelease()
        drag.record(100,100f,100f,settled)
        drag.record(300,103f,102f,moved)
        assertEquals(moved,drag.finish(450,moved))
    }
    @Test fun largeMovementAfterPauseIsKept() {
        val drag=DragRelease()
        drag.record(100,100f,100f,settled)
        drag.record(300,103f,102f,moved)
        val last=settled.copy(x=120f)
        drag.record(320,120f,100f,last)
        assertEquals(last,drag.finish(340,last))
    }
    @Test fun screenPixelThresholdDoesNotGrowWithZoom() {
        for(scale in listOf(.5f,1f,4f)) {
            val drag=DragRelease(); val last=settled.copy(x=100f+4f/scale)
            drag.record(100,100f,100f,settled)
            drag.record(300,104f,100f,last)
            assertEquals(settled,drag.finish(330,last))
        }
    }
    @Test fun resetDiscardsPreviousGesture() {
        val drag=DragRelease()
        drag.record(100,100f,100f,settled)
        drag.record(300,103f,102f,moved)
        drag.reset()
        drag.record(310,103f,102f,moved)
        assertEquals(moved,drag.finish(340,moved))
    }
}
