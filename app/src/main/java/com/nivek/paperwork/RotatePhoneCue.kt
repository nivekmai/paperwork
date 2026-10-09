package com.nivek.paperwork

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable fun RotatePhoneCue(onDismiss: ()->Unit,onReady: ()->Unit) {
    val rotation=remember { Animatable(0f) }
    val ready by rememberUpdatedState(onReady)
    LaunchedEffect(Unit) {
        rotation.animateTo(-90f,tween(durationMillis=1200,delayMillis=150))
        ready()
    }
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text("Rotate your phone",style=MaterialTheme.typography.titleLarge)
                val color=MaterialTheme.colorScheme.primary
                Canvas(Modifier.size(144.dp).graphicsLayer { rotationZ=rotation.value }) {
                    val w=size.width*.45f; val h=size.height*.8f
                    drawRoundRect(color,Offset((size.width-w)/2,(size.height-h)/2),Size(w,h),CornerRadius(10.dp.toPx()),style=Stroke(3.dp.toPx()))
                    drawLine(color,Offset(size.width*.44f,size.height*.83f),Offset(size.width*.56f,size.height*.83f),3.dp.toPx())
                }
                Text("Opening the landscape drawing pad…",style=MaterialTheme.typography.bodyMedium)
                TextButton(onClick=onDismiss) { Text("Cancel") }
            }
        }
    }
}
