package com.nivek.paperwork

import kotlin.math.*

enum class ResizeHandle { NONE, CORNER, LEFT, RIGHT, TOP, BOTTOM, LINE_START, LINE_END }

/** Deltas are measured from gesture start, so the corner ratio never drifts. */
fun resizeMark(m: Mark, handle: ResizeHandle, dx: Float, dy: Float, pageWidth: Float, pageHeight: Float): Mark {
    val minimum = 8f
    return when(handle) {
        ResizeHandle.LINE_START -> {
            val start=m.lineStartOnPage(); lineWithEndpoints(m,InkPoint(start.x+dx,start.y+dy),m.lineEndOnPage(),pageWidth,pageHeight)
        }
        ResizeHandle.LINE_END -> {
            val start=m.lineStartOnPage(); val end=m.lineEndOnPage()
            val snapped=snappedLineHead(start,InkPoint(end.x+dx,end.y+dy),pageWidth,pageHeight)
            lineWithEndpoints(m,start,snapped,pageWidth,pageHeight)
        }
        ResizeHandle.CORNER -> {
            val maxScale = min((pageWidth-m.x)/m.width,(pageHeight-m.y)/m.height).coerceAtLeast(.001f)
            val minScale = min(maxScale,max(minimum/m.width,minimum/m.height))
            val ratio = ((m.width+dx)*m.width+(m.height+dy)*m.height)/(m.width*m.width+m.height*m.height)
            val scale = ratio.coerceIn(minScale,maxScale)
            m.copy(width=m.width*scale,height=m.height*scale)
        }
        ResizeHandle.RIGHT -> m.copy(width=(m.width+dx).coerceIn(minimum,max(minimum,pageWidth-m.x)))
        ResizeHandle.BOTTOM -> m.copy(height=(m.height+dy).coerceIn(minimum,max(minimum,pageHeight-m.y)))
        ResizeHandle.LEFT -> {
            val x=(m.x+dx).coerceIn(0f,max(0f,m.x+m.width-minimum)); m.copy(x=x,width=m.x+m.width-x)
        }
        ResizeHandle.TOP -> {
            val y=(m.y+dy).coerceIn(0f,max(0f,m.y+m.height-minimum)); m.copy(y=y,height=m.y+m.height-y)
        }
        ResizeHandle.NONE -> m.copy(x=(m.x+dx).coerceIn(0f,max(0f,pageWidth-m.width)),y=(m.y+dy).coerceIn(0f,max(0f,pageHeight-m.height)))
    }
}
