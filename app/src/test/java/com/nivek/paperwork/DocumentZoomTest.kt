package com.nivek.paperwork

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class DocumentZoomTest {
    @Test fun pinchKeepsTheSameDocumentPointUnderMovingFingers() {
        val zoom=DocumentZoom()
        val delta=zoom.transform(2f,Offset(150f,400f),Offset(160f,420f),400f)
        assertEquals(2f,zoom.scale,0f)
        assertEquals(160f,150f*zoom.scale+zoom.panX,.001f)
        assertEquals(420f,(600f-(200f+delta))*zoom.scale,.001f)
    }
    @Test fun zoomAndPanningStayInsideTheDocument() {
        val zoom=DocumentZoom()
        zoom.transform(100f,Offset(200f,300f),Offset(200f,300f),400f)
        assertEquals(4f,zoom.scale,0f)
        zoom.pan(-5000f,400f); assertEquals(-1200f,zoom.panX,0f)
        zoom.pan(5000f,400f); assertEquals(0f,zoom.panX,0f)
        zoom.transform(.001f,Offset(200f,300f),Offset(200f,300f),400f)
        assertEquals(1f,zoom.scale,0f); assertEquals(0f,zoom.panX,0f)
        assertEquals(0f,zoom.transform(Float.NaN,Offset.Zero,Offset.Zero,400f),0f)
        assertEquals(1f,zoom.scale,0f)
    }
    @Test fun rotationClampsPanningWithoutResettingZoom() {
        val zoom=DocumentZoom(3f,-1000f)
        zoom.constrain(300f)
        assertEquals(3f,zoom.scale,0f); assertEquals(-600f,zoom.panX,0f)
    }
}
