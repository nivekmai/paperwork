package com.nivek.paperwork

import org.junit.Assert.*
import org.junit.Test

class AlignmentGeometryTest {
    @Test fun edgesAndCentersSnapWithinTwoScreenPixelsAtAnyZoom() {
        val peer=Mark(x=100f,y=100f,width=80f,height=80f)
        for(scale in listOf(.5f,1f,3f)) {
            val mark=Mark(x=250f,y=100f+1.9f/scale,width=30f,height=30f)
            val snap=snapPlacement(mark,listOf(peer),600f,800f,scale)
            assertEquals(100f,snap.mark.y,.001f); assertEquals(100f,snap.yGuide!!,.001f)
            val outside=mark.copy(y=100f+2.1f/scale)
            assertEquals(outside.y,snapPlacement(outside,listOf(peer),600f,800f,scale).mark.y,.001f)
            val center=mark.copy(y=140f-mark.height/2+1.5f/scale)
            assertEquals(140f,snapPlacement(center,listOf(peer),600f,800f,scale).mark.let { it.y+it.height/2 },.001f)
        }
    }
    @Test fun snappingIgnoresSelfAndOtherPagesAndKeepsItemsInsidePage() {
        val m=Mark(x=123f,y=234f,width=40f,height=40f)
        val result=snapPlacement(m,listOf(m,m.copy(id="other",page=1,y=235f)),600f,800f,1f)
        assertNull(result.xGuide); assertNull(result.yGuide)
        val edge=snapPlacement(m.copy(x=559f,y=779f,height=20f),emptyList(),600f,800f,1f)
        assertEquals(560f,edge.mark.x,.001f); assertEquals(780f,edge.mark.y,.001f)
    }
    @Test fun hapticDetentsOnlyFireOnEntryChangeOrReentry() {
        val feedback=SnapFeedback()
        assertFalse(feedback.update(emptySet()))
        assertTrue(feedback.update(setOf("x:100")))
        assertFalse(feedback.update(setOf("x:100")))
        assertTrue(feedback.update(setOf("x:100","y:200")))
        assertFalse(feedback.update(setOf("x:100")))
        assertFalse(feedback.update(emptySet()))
        assertTrue(feedback.update(setOf("x:100")))
        feedback.reset(); assertTrue(feedback.update(setOf("angle:1")))
        assertFalse(feedback.update(setOf("angle:1"))); assertTrue(feedback.update(setOf("angle:2")))
    }
}
