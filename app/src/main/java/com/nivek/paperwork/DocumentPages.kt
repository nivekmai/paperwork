package com.nivek.paperwork

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

/** Only composed pages hold previews; the model also keeps a small byte-bounded cache. */
@Composable fun DocumentPages(vm: EditorModel,document: Draft,state: LazyListState,zoom: DocumentZoom,onPage: (Int)->Unit) {
    val strings=localizedResources()
    val scope=rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
        val listWidth=maxWidth
        val listHeight=maxHeight
        val viewportWidth=with(LocalDensity.current) { maxWidth.toPx() }
        val viewportHeight=with(LocalDensity.current) { maxHeight.toPx() }
        SideEffect { zoom.constrain(viewportWidth) }
        fun changeZoom(factor: Float) {
            val center=Offset(viewportWidth/2,viewportHeight/2)
            state.dispatchRawDelta(zoom.transform(factor,center,center,viewportWidth))
        }
        Box(Modifier.fillMaxSize().testTag("document-zoom").semantics {
            stateDescription=strings.getString(R.string.document_zoom_percent,(zoom.scale*100).roundToInt())
            customActions=listOf(
                CustomAccessibilityAction(strings.getString(R.string.zoom_in)) { changeZoom(1.25f); true },
                CustomAccessibilityAction(strings.getString(R.string.zoom_out)) { changeZoom(.8f); true })
        }.pointerInput(document.id,viewportWidth) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                var pinching=false
                do {
                    val event=awaitPointerEvent(PointerEventPass.Initial)
                    if(event.changes.count { it.pressed }>=2) {
                        if(!pinching) { pinching=true; scope.launch { state.stopScroll() } }
                        val previous=event.calculateCentroid(useCurrent=false)
                        val current=event.calculateCentroid(useCurrent=true)
                        if(previous!=Offset.Unspecified && current!=Offset.Unspecified) {
                            state.dispatchRawDelta(zoom.transform(event.calculateZoom(),previous,current,viewportWidth))
                        }
                    }
                    // Consume the entire remainder, including the last finger's lift: never open a page after a pinch.
                    if(pinching) event.changes.forEach { it.consume() }
                } while(event.changes.any { it.pressed })
            }
        }.pointerInput(document.id,viewportWidth) {
            detectHorizontalDragGestures { change,amount ->
                if(zoom.scale>1f) { change.consume(); zoom.pan(amount,viewportWidth) }
            }
        }) {
            // Measure the visible list at unscaled height. This keeps the last page reachable at any zoom.
            LazyColumn(state=state,modifier=Modifier.requiredSize(listWidth,listHeight/zoom.scale).graphicsLayer {
                transformOrigin=TransformOrigin(0f,0f)
                scaleX=zoom.scale; scaleY=zoom.scale; translationX=zoom.panX
            }.testTag("document-pages"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                items(document.pageCount,key={"${document.id}:$it"}) { index ->
                    val size=vm.pageSizes[index]
                    var attempt by remember(document.id,index) { mutableIntStateOf(0) }
                    val detail=if(zoom.scale>1.5f) 1800 else 900
                    val preview by produceState<Result<PageImage>?>(null,document.id,index,attempt,detail) {
                        if(value?.isFailure==true) value=null
                        value=try { Result.success(vm.preview(document,index,detail)) }
                            catch(e: CancellationException) { throw e }
                            catch(e: Exception) { Result.failure(e) }
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text(strings.getString(R.string.page_position,index+1,document.pageCount),style=MaterialTheme.typography.labelMedium)
                        Surface(onClick={onPage(index)},color=Color.White,shadowElevation=2.dp,
                            modifier=Modifier.fillMaxWidth().aspectRatio(size.width/size.height).semantics { contentDescription=strings.getString(R.string.open_page,index+1) }) {
                            Box(contentAlignment=Alignment.Center) {
                                val image=preview?.getOrNull()
                                if(image!=null) {
                                    val paint=remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
                                    val marks=document.marks.filter { it.page==index }
                                    Canvas(Modifier.fillMaxSize().clipToBounds()) {
                                        drawIntoCanvas { canvas ->
                                            val native=canvas.nativeCanvas
                                            native.drawBitmap(image.bitmap,null,RectF(0f,0f,this.size.width,this.size.height),paint)
                                            native.save()
                                            native.scale(this.size.width/image.width,this.size.height/image.height)
                                            marks.forEach { MarkPainter.draw(native,it) }
                                            native.restore()
                                        }
                                    }
                                } else if(preview?.isFailure==true) Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                    Text(strings.getString(R.string.page_preview_failed))
                                    TextButton(onClick={attempt++}) { Text(strings.getString(R.string.retry)) }
                                } else CircularProgressIndicator(Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
