package com.nivek.paperwork

import org.junit.Assert.*
import org.junit.Test

class ResizeGeometryTest {
    private val m=Mark(kind="Signature",x=60f,y=80f,width=200f,height=60f)
    @Test fun cornerKeepsSignatureAspectEvenWhenPointerMovesUnevenly() {
        val resized=resizeMark(m,ResizeHandle.CORNER,100f,5f,600f,800f)
        assertEquals(m.width/m.height,resized.width/resized.height,.0001f)
        assertTrue(resized.width>m.width)
        assertEquals(m.x,resized.x,0f); assertEquals(m.y,resized.y,0f)
    }
    @Test fun cornerClampsBothDimensionsWithoutDistorting() {
        val bigger=resizeMark(m,ResizeHandle.CORNER,10000f,10000f,600f,800f)
        assertEquals(600f,bigger.x+bigger.width,.001f)
        assertTrue(bigger.y+bigger.height<=800f)
        assertEquals(m.width/m.height,bigger.width/bigger.height,.0001f)
        val smaller=resizeMark(m,ResizeHandle.CORNER,-10000f,-10000f,600f,800f)
        assertTrue(smaller.width>=8f && smaller.height>=8f)
        assertEquals(m.width/m.height,smaller.width/smaller.height,.0001f)
    }
    @Test fun sidesAdjustOneDimensionAndKeepTheOppositeEdgeFixed() {
        val left=resizeMark(m,ResizeHandle.LEFT,-20f,500f,600f,800f)
        assertEquals(40f,left.x,0f); assertEquals(220f,left.width,0f); assertEquals(m.height,left.height,0f)
        assertEquals(m.x+m.width,left.x+left.width,0f)
        val right=resizeMark(m,ResizeHandle.RIGHT,30f,500f,600f,800f)
        assertEquals(230f,right.width,0f); assertEquals(m.height,right.height,0f)
        val top=resizeMark(m,ResizeHandle.TOP,500f,-30f,600f,800f)
        assertEquals(50f,top.y,0f); assertEquals(90f,top.height,0f); assertEquals(m.width,top.width,0f)
        assertEquals(m.y+m.height,top.y+top.height,0f)
        val bottom=resizeMark(m,ResizeHandle.BOTTOM,500f,30f,600f,800f)
        assertEquals(90f,bottom.height,0f); assertEquals(m.width,bottom.width,0f)
    }
    @Test fun circleAndSquareCanStretchThenScaleAtTheirNewRatio() {
        listOf("Circle","Square").forEach { kind ->
            val shape=m.copy(kind=kind,width=48f,height=48f)
            val wide=resizeMark(shape,ResizeHandle.RIGHT,32f,10f,600f,800f)
            assertEquals(80f,wide.width,0f); assertEquals(48f,wide.height,0f)
            val scaled=resizeMark(wide,ResizeHandle.CORNER,40f,30f,600f,800f)
            assertEquals(80f/48f,scaled.width/scaled.height,.0001f)
        }
    }
    @Test fun allEdgesStopAtPageBoundsAndDoNotFlip() {
        val left=resizeMark(m,ResizeHandle.LEFT,-10000f,0f,600f,800f)
        val top=resizeMark(m,ResizeHandle.TOP,0f,-10000f,600f,800f)
        assertEquals(0f,left.x,0f); assertEquals(0f,top.y,0f)
        assertEquals(8f,resizeMark(m,ResizeHandle.LEFT,10000f,0f,600f,800f).width,0f)
        assertEquals(8f,resizeMark(m,ResizeHandle.TOP,0f,10000f,600f,800f).height,0f)
    }
}
