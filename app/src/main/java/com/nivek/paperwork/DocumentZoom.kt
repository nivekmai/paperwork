package com.nivek.paperwork

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset

/** Viewport coordinates are screen pixels; LazyColumn scroll offsets are unscaled pixels. */
@Stable class DocumentZoom(scale: Float=1f,panX: Float=0f) {
    var scale by mutableFloatStateOf(scale); private set
    var panX by mutableFloatStateOf(panX); private set
    fun pan(delta: Float,width: Float) { panX=(panX+delta).coerceIn(width*(1-scale),0f) }
    fun constrain(width: Float) { panX=panX.coerceIn(width*(1-scale),0f) }
    fun transform(factor: Float,previous: Offset,current: Offset,width: Float): Float {
        if(!factor.isFinite() || factor<=0f) return 0f
        val before=scale
        scale=(before*factor).coerceIn(1f,4f)
        panX=(current.x-(previous.x-panX)*scale/before).coerceIn(width*(1-scale),0f)
        return previous.y/before-current.y/scale
    }
    companion object {
        val Saver=listSaver<DocumentZoom,Float>(save={listOf(it.scale,it.panX)},restore={DocumentZoom(it[0],it[1])})
    }
}
