package com.nivek.paperwork

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Small native vector icons. No bitmap assets or network font dependency. */
object EditorIcons {
    private fun glyph(name: String, data: String) = ImageVector.Builder(name,24.dp,24.dp,24f,24f).apply {
        addPath(PathParser().parsePathString(data).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round)
    }.build()
    val Stroke=ImageVector.Builder("Stroke width",24.dp,24.dp,24f,24f).apply {
        listOf("M3 5 H21" to 4f,"M3 12 H18" to 2.5f,"M3 19 H14" to 1f).forEach { (path,width) ->
            addPath(PathParser().parsePathString(path).toNodes(),fill=null,stroke=SolidColor(Color.Black),strokeLineWidth=width,strokeLineCap=StrokeCap.Round)
        }
    }.build()
    val Up=glyph("Move up","M5 11 L12 4 L19 11 M12 4 V21")
    val Down=glyph("Move down","M5 13 L12 20 L19 13 M12 20 V3")
    val Left=glyph("Move left","M11 5 L4 12 L11 19 M4 12 H21")
    val Right=glyph("Move right","M13 5 L20 12 L13 19 M20 12 H3")
    val Back=glyph("Chevron left","M15 5 L8 12 L15 19")
    val Next=glyph("Chevron right","M9 5 L16 12 L9 19")
    val Undo=glyph("Undo","M9 4 L4 9 L9 14 M4 9 H14 A6 6 0 0 1 14 21")
    val Redo=glyph("Redo","M15 4 L20 9 L15 14 M20 9 H10 A6 6 0 0 0 10 21")
    val Edit=glyph("Pencil","M14 5 L19 10 M4 15 L15 4 Q17 2 19 4 L20 5 Q22 7 20 9 L9 20 L3 21 Z")
    val Copy=glyph("Copy","M9 8 H20 V21 H9 Z M5 16 H3 V3 H15 V5")
    val Delete=glyph("Trash","M3 6 H21 M9 6 V3 H15 V6 M5 6 L6 21 H18 L19 6 M10 10 V17 M14 10 V17")
    val Fit=glyph("Fit page","M3 8 V3 H8 M16 3 H21 V8 M21 16 V21 H16 M8 21 H3 V16 M8 7 H16 V17 H8 Z")
    val Text=glyph("Text","M4 6 V3 H20 V6 M12 3 V21 M8 21 H16")
    val Shape=glyph("Shapes","M3 3 H13 V13 H3 Z M17 8 A7 7 0 1 1 8 17")
    val Sign=glyph("Signature","M3 16 C7 16 14 1 11 3 C8 5 5 20 9 18 C11 17 12 11 13 13 C14 15 12 19 15 17 L20 13 M3 21 H21")
    val Doodle=glyph("Doodle","M3 17 C3 7 10 2 12 5 C15 9 2 15 6 18 C10 22 17 3 19 7 C22 11 15 18 21 18")
    val Check=glyph("Done","M4 12 L9 17 L20 6")
    val Add=glyph("Add","M12 4 V20 M4 12 H20")
    val Download=glyph("Download","M12 3 V16 M7 11 L12 16 L17 11 M4 16 V21 H20 V16")
    val Share=glyph("Share","M12 16 V3 M7 8 L12 3 L17 8 M6 11 H3 V21 H21 V11 H18")
    val Open=glyph("Open PDF","M3 7 V4 H10 L13 7 H21 V10 M3 7 V21 H19 L22 10 H7 L3 21")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ToolIcon(icon: ImageVector,label: String,onClick: ()->Unit,enabled: Boolean=true,modifier: Modifier=Modifier,tint: Color=MaterialTheme.colorScheme.primary) {
    TooltipBox(positionProvider=TooltipDefaults.rememberPlainTooltipPositionProvider(),tooltip={PlainTooltip { Text(label) }},state=rememberTooltipState()) {
        IconButton(onClick=onClick,enabled=enabled,modifier=modifier) { Icon(icon,contentDescription=label,tint=if(enabled) tint else tint.copy(alpha=.35f)) }
    }
}
