package com.nivek.paperwork

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

fun colorHex(color: Int)="%06X".format(color and 0xffffff)
/** Six digits preserve opacity; eight digits use the user-facing RRGGBBAA order. */
fun parseColorHex(text: String,alpha: Int): Int? {
    val hex=text.removePrefix("#")
    if(!hex.matches(Regex("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) return null
    return if(hex.length==6) (alpha.coerceIn(0,255) shl 24) or hex.toInt(16)
    else (hex.takeLast(2).toInt(16) shl 24) or hex.take(6).toInt(16)
}
private fun DrawScope.checkerboard() {
    val cell=6.dp.toPx()
    drawRect(Color.White)
    for(y in 0..(size.height/cell).toInt()) for(x in 0..(size.width/cell).toInt())
        if((x+y)%2==0) drawRect(Color(0xffcbd0cd),Offset(x*cell,y*cell),Size(cell,cell))
}
@Composable fun Swatch(color: Int,selected: Boolean,label: String,onClick: ()->Unit) {
    val outline=if(selected) MaterialTheme.colorScheme.primary else Color.Gray
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).border(if(selected) 3.dp else 1.dp,outline,RoundedCornerShape(9.dp)).clickable(onClick=onClick).semantics { contentDescription=label; this.selected=selected },contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { checkerboard(); drawRect(Color(color)) }
        if(selected) Text("✓",color=if(android.graphics.Color.alpha(color)<128 || (color and 0xffffff) in listOf(0xffffff,0xffe49a)) Color.Black else Color.White)
    }
}
@Composable fun ColorPicker(label: String,value: Int,transparent: Boolean,onChange: (Int)->Unit) {
    var custom by remember { mutableStateOf(false) }
    Text(label,fontWeight=FontWeight.Medium)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(transparent) Swatch(0,android.graphics.Color.alpha(value)==0,"$label: Transparent") { onChange(0) }
        listOf(0xff172c27.toInt(),0xff000000.toInt(),0xff2454b5.toInt(),0xffb42c35.toInt(),0xff246b5b.toInt(),0xffffe49a.toInt(),0xffffffff.toInt()).forEach { color ->
            Swatch(color,value==color,"$label: #${colorHex(color)}") { onChange(color) }
        }
    }
    OutlinedButton(onClick={custom=true},modifier=Modifier.fillMaxWidth()) {
        Canvas(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp))) { checkerboard(); drawRect(Color(value)) }
        Spacer(Modifier.width(8.dp)); Text("Custom color · #${colorHex(value)} · ${(android.graphics.Color.alpha(value)/255f*100).roundToInt()}%")
    }
    if(custom) CustomColorDialog(label,value,onDismiss={custom=false},onApply={onChange(it);custom=false})
}
@Composable fun CustomColorDialog(label: String,initial: Int,onDismiss: ()->Unit,onApply: (Int)->Unit,keepKeyboard: Boolean=false) {
    val hsv=remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial,it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableIntStateOf(android.graphics.Color.alpha(initial)) }
    var hex by remember { mutableStateOf(colorHex(initial)) }
    var opacity by remember { mutableStateOf((alpha/255f*100).roundToInt().toString()) }
    fun color()=android.graphics.Color.HSVToColor(alpha,floatArrayOf(hue,saturation,brightness))
    fun syncHex() { hex=colorHex(color()) }
    val current=color()
    val hexFocus=remember { FocusRequester() }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        if(keepKeyboard) {
            val view=LocalView.current
            SideEffect { (view.parent as? DialogWindowProvider)?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE) }
            LaunchedEffect(Unit) { hexFocus.requestFocus() }
        }
        Surface(Modifier.fillMaxWidth().padding(16.dp),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    ToolIcon(EditorIcons.Back,"Back to style",onClick=onDismiss)
                    Text(label,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                    Canvas(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).semantics { contentDescription="Custom color preview" }) { checkerboard(); drawRect(Color(current)) }
                }
                Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()).semantics { contentDescription="Color controls" },verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    val pick by rememberUpdatedState<(Float,Float)->Unit>({ x,y -> saturation=x; brightness=1-y; syncHex() })
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(10.dp)).semantics {
                        contentDescription="Saturation and brightness"
                        stateDescription="Saturation ${(saturation*100).roundToInt()}%, brightness ${(brightness*100).roundToInt()}%"
                        customActions=listOf(
                            CustomAccessibilityAction("Increase saturation") { saturation=(saturation+.05f).coerceAtMost(1f); syncHex(); true },
                            CustomAccessibilityAction("Decrease saturation") { saturation=(saturation-.05f).coerceAtLeast(0f); syncHex(); true },
                            CustomAccessibilityAction("Increase brightness") { brightness=(brightness+.05f).coerceAtMost(1f); syncHex(); true },
                            CustomAccessibilityAction("Decrease brightness") { brightness=(brightness-.05f).coerceAtLeast(0f); syncHex(); true })
                    }.pointerInput(Unit) {
                        awaitEachGesture {
                            val down=awaitFirstDown(); down.consume()
                            fun select(p: Offset) { pick((p.x/size.width).coerceIn(0f,1f),(p.y/size.height).coerceIn(0f,1f)) }
                            select(down.position)
                            drag(down.id) { select(it.position); it.consume() }
                        }
                    }) {
                        drawRect(Brush.horizontalGradient(listOf(Color.White,Color.hsv(hue,1f,1f))))
                        drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
                        val point=Offset(saturation*size.width,(1-brightness)*size.height)
                        drawCircle(Color.Black.copy(alpha=.4f),9.dp.toPx(),point,style=Stroke(4.dp.toPx()))
                        drawCircle(Color.White,8.dp.toPx(),point,style=Stroke(3.dp.toPx()))
                    }
                    Text("Hue")
                    GradientSlider(hue,0f..360f,"Hue",listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)) { hue=it; syncHex() }
                    Text("Opacity: ${(alpha/255f*100).roundToInt()}%")
                    val opaque=Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,brightness)))
                    GradientSlider(alpha.toFloat(),0f..255f,"Opacity",listOf(opaque.copy(alpha=0f),opaque),checker=true) { alpha=it.roundToInt(); opacity=(alpha/255f*100).roundToInt().toString(); syncHex() }
                    OutlinedTextField(value=opacity,onValueChange={text -> opacity=text; text.toIntOrNull()?.takeIf { it in 0..100 }?.let { alpha=(it*255f/100).roundToInt(); syncHex() } },label={Text("Alpha (%)")},singleLine=true,isError=opacity.toIntOrNull() !in 0..100,modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(value=hex,onValueChange={text ->
                        hex=text.removePrefix("#").take(8)
                        parseColorHex(hex,alpha)?.let { parsed ->
                            val values=FloatArray(3); android.graphics.Color.colorToHSV(parsed,values)
                            hue=values[0]; saturation=values[1]; brightness=values[2]; alpha=android.graphics.Color.alpha(parsed)
                            opacity=(alpha/255f*100).roundToInt().toString()
                        }
                    },label={Text("Hex (RRGGBB or RRGGBBAA)")},prefix={Text("#")},singleLine=true,isError=parseColorHex(hex,alpha)==null,modifier=Modifier.fillMaxWidth().focusRequester(hexFocus))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text("Cancel") }
                    Button(onClick={onApply(current)},enabled=parseColorHex(hex,alpha)!=null && opacity.toIntOrNull() in 0..100) { Text("Apply color") }
                }
            }
        }
    }
}
@Composable private fun GradientSlider(value: Float,range: ClosedFloatingPointRange<Float>,label: String,colors: List<Color>,checker: Boolean=false,onChange: (Float)->Unit) {
    Box(contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().padding(horizontal=10.dp).height(12.dp).clip(RoundedCornerShape(6.dp))) { if(checker) checkerboard(); drawRect(Brush.horizontalGradient(colors)) }
        Slider(value=value,onValueChange=onChange,valueRange=range,modifier=Modifier.fillMaxWidth().semantics { contentDescription=label },colors=SliderDefaults.colors(activeTrackColor=Color.Transparent,inactiveTrackColor=Color.Transparent,thumbColor=MaterialTheme.colorScheme.primary))
    }
}
