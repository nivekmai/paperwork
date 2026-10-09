package com.nivek.paperwork

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

private object AboveAnchor: PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect,windowSize: IntSize,layoutDirection: LayoutDirection,popupContentSize: IntSize)=IntOffset(
        (anchorBounds.center.x-popupContentSize.width/2).coerceIn(0,(windowSize.width-popupContentSize.width).coerceAtLeast(0)),
        (anchorBounds.top-popupContentSize.height).coerceAtLeast(0))
}
@Composable fun QuickStyleBar(color: Int,width: Float,isText: Boolean,allowTransparent: Boolean=true,keepKeyboard: Boolean=false,onColor: (Int)->Unit,onWidth: (Float)->Unit) {
    var tray by remember { mutableStateOf<String?>(null) }
    var custom by remember { mutableStateOf(false) }
    Box(contentAlignment=Alignment.Center) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Box(Modifier.padding(vertical=6.dp)) { Swatch(color,false,"Quick color") { tray=if(tray=="color") null else "color" } }
            ToolIcon(EditorIcons.Stroke,if(isText) "Quick text size" else "Quick stroke width",onClick={tray=if(tray=="width") null else "width"})
        }
        if(tray!=null) Popup(popupPositionProvider=AboveAnchor,onDismissRequest={tray=null},properties=PopupProperties(focusable=!keepKeyboard)) {
            Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=6.dp,modifier=Modifier.widthIn(max=360.dp).semantics { contentDescription="Quick picker tray" }) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(tray=="color") {
                        listOf(0,0xff172c27.toInt(),0xff000000.toInt(),0xff2454b5.toInt(),0xffb42c35.toInt(),0xff246b5b.toInt(),0xffffe49a.toInt(),0xffffffff.toInt()).filter { allowTransparent || it!=0 }.forEach { choice ->
                            Swatch(choice,color==choice,if(choice==0) "Quick transparent" else "Quick #${colorHex(choice)}") { onColor(choice); tray=null }
                        }
                        TextButton(onClick={tray=null;custom=true}) { Text("Custom") }
                    } else {
                        (if(isText) listOf(10f,12f,16f,20f,24f,32f,48f) else listOf(.5f,1f,2f,3f,5f,8f,12f)).forEach { choice ->
                            FilterChip(selected=width==choice,onClick={onWidth(choice);tray=null},label={Text("${if(choice%1==0f) choice.toInt().toString() else choice.toString()} pt")})
                        }
                    }
                }
            }
        }
    }
    if(custom) CustomColorDialog("Color",color,onDismiss={custom=false},onApply={onColor(it);custom=false},keepKeyboard=keepKeyboard)
}
@Composable fun NudgeBar(selected: Boolean,onNudge: (Float,Float)->Unit) {
    if(!selected) return
    var step by rememberSaveable { mutableIntStateOf(1) }
    Surface(color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().height(48.dp)) {
        BoxWithConstraints(contentAlignment=Alignment.Center) {
            if(selected) Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min=maxWidth),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                listOf(1,5).forEach { amount -> FilterChip(selected=step==amount,onClick={step=amount},label={Text("$amount px")}) }
                RepeatNudgeButton(EditorIcons.Left,"Nudge left",onClick={onNudge(-step.toFloat(),0f)})
                RepeatNudgeButton(EditorIcons.Up,"Nudge up",onClick={onNudge(0f,-step.toFloat())})
                RepeatNudgeButton(EditorIcons.Down,"Nudge down",onClick={onNudge(0f,step.toFloat())})
                RepeatNudgeButton(EditorIcons.Right,"Nudge right",onClick={onNudge(step.toFloat(),0f)})
            } else Text("Select an item to nudge",style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable fun RepeatNudgeButton(icon: ImageVector,label: String,onClick: ()->Unit) {
    val action by rememberUpdatedState(onClick)
    val timeout=LocalViewConfiguration.current.longPressTimeoutMillis
    Box(Modifier.size(48.dp).semantics { contentDescription=label; role=Role.Button; onClick { action(); true } }.pointerInput(timeout) {
        detectTapGestures(onPress={
            coroutineScope {
                var repeated=false
                val job=launch { delay(timeout); while(true) { repeated=true; action(); delay(150) } }
                try { val released=tryAwaitRelease(); if(released && !repeated) action() } finally { job.cancel() }
            }
        })
    },contentAlignment=Alignment.Center) { Icon(icon,null,tint=MaterialTheme.colorScheme.primary) }
}
