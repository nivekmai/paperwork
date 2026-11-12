package com.nivek.paperwork

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
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
@Composable fun DocumentPages(vm: EditorModel,document: Draft,state: LazyListState,onPage: (Int)->Unit) {
    val strings=localizedResources()
    LazyColumn(state=state,modifier=Modifier.fillMaxSize().testTag("document-pages"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        items(document.pageCount,key={"${document.id}:$it"}) { index ->
            val size=vm.pageSizes[index]
            var attempt by remember(document.id,index) { mutableIntStateOf(0) }
            val preview by produceState<Result<PageImage>?>(null,document.id,index,attempt) {
                value=null
                value=try { Result.success(vm.preview(document,index)) }
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
