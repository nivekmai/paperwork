package com.nivek.paperwork

import org.junit.Assert.*
import org.junit.Test

class DrawingGeometryTest {
    private fun same(a: InkPoint,b: InkPoint) { assertEquals(a.x,b.x,.001f); assertEquals(a.y,b.y,.001f) }
    @Test fun endpointsMoveIndependentlyAndCanCross() {
        val m=lineWithEndpoints(Mark(kind="Line"),InkPoint(100f,100f),InkPoint(200f,100f),600f,800f)
        val moved=resizeMark(m,ResizeHandle.LINE_START,150f,70f,600f,800f)
        same(InkPoint(250f,170f),moved.lineStartOnPage()); same(InkPoint(200f,100f),moved.lineEndOnPage())
        val vertical=resizeMark(m,ResizeHandle.LINE_END,-100f,150f,600f,800f)
        same(InkPoint(100f,100f),vertical.lineStartOnPage()); same(InkPoint(100f,250f),vertical.lineEndOnPage())
    }
    @Test fun endpointsStayOnPageAndCannotCollapse() {
        val m=lineWithEndpoints(Mark(kind="Line"),InkPoint(100f,100f),InkPoint(200f,100f),600f,800f)
        val clamped=resizeMark(m,ResizeHandle.LINE_END,1000f,-1000f,600f,800f)
        same(InkPoint(600f,0f),clamped.lineEndOnPage())
        assertEquals(m,resizeMark(m,ResizeHandle.LINE_END,-100f,0f,600f,800f))
        assertTrue(clamped.x>=0 && clamped.y>=0 && clamped.x+clamped.width<=600 && clamped.y+clamped.height<=800)
    }
    @Test fun headSnapsToEvery45DegreeDirectionWhileTailStaysFixed() {
        val tail=InkPoint(300f,400f)
        val m=lineWithEndpoints(Mark(kind="Line"),tail,InkPoint(400f,400f),600f,800f)
        for(degrees in 0 until 360 step 45) {
            val rawAngle=Math.toRadians(degrees+1.5)
            val target=InkPoint(tail.x+100*kotlin.math.cos(rawAngle).toFloat(),tail.y+100*kotlin.math.sin(rawAngle).toFloat())
            val moved=resizeMark(m,ResizeHandle.LINE_END,target.x-400,target.y-400,600f,800f)
            val angle=Math.toRadians(degrees.toDouble())
            same(tail,moved.lineStartOnPage())
            same(InkPoint(tail.x+100*kotlin.math.cos(angle).toFloat(),tail.y+100*kotlin.math.sin(angle).toFloat()),moved.lineEndOnPage())
        }
        val free=resizeMark(m,ResizeHandle.LINE_START,13f,27f,600f,800f)
        same(InkPoint(313f,427f),free.lineStartOnPage()); same(m.lineEndOnPage(),free.lineEndOnPage())
    }
    @Test fun snappingHasTwoDegreeToleranceOnBothSidesAndAcrossAngleWrap() {
        val tail=InkPoint(300f,400f)
        for(degrees in 0 until 360 step 45) for(offset in listOf(-2.1,-2.0,-1.9,1.9,2.0,2.1,12.0)) {
            val angle=Math.toRadians(degrees+offset)
            val head=InkPoint(300f+100*kotlin.math.cos(angle).toFloat(),400f+100*kotlin.math.sin(angle).toFloat())
            val result=snappedLineHead(tail,head,600f,800f)
            if(kotlin.math.abs(offset)>2) same(head,result)
            else {
                val snapped=Math.toRadians(degrees.toDouble())
                same(InkPoint(300f+100*kotlin.math.cos(snapped).toFloat(),400f+100*kotlin.math.sin(snapped).toFloat()),result)
            }
        }
        same(InkPoint(200f,0f),snappedLineHead(InkPoint(100f,100f),InkPoint(1100f,-900f),600f,800f))
    }
    @Test fun doodleNormalizationPreservesDotsAndAllStrokes() {
        val ink=listOf(listOf(InkPoint(100f,100f),InkPoint(150f,120f)),listOf(InkPoint(200f,200f)))
        val m=doodleFromStrokes(Mark(kind="Doodle",page=2),ink,600f,800f)
        assertEquals(2,m.page); assertEquals(2,m.strokes.size)
        m.strokesOnPage().flatten().zip(ink.flatten()).forEach { (a,b)->same(a,b) }
        val dot=doodleFromStrokes(Mark(kind="Doodle"),listOf(listOf(InkPoint(0f,0f))),600f,800f)
        assertTrue(dot.width>=8f && dot.height>=8f); same(InkPoint(0f,0f),dot.strokesOnPage().single().single())
    }
    @Test fun diagonalHitTestingDoesNotSelectTheEmptyBoundingBox() {
        assertTrue(distanceToLine(InkPoint(150f,150f),InkPoint(100f,100f),InkPoint(200f,200f))<.01f)
        assertTrue(distanceToLine(InkPoint(100f,200f),InkPoint(100f,100f),InkPoint(200f,200f))>60f)
    }
}
