package com.nivek.paperwork

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** A separate landscape screen keeps rotation out of the portrait signature form. */
class SignatureDrawingActivity: ComponentActivity() {
    private var pad: SignaturePad?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedInk=savedInstanceState?.getString("ink")
        setContent { PaperworkTheme {
            var hasInk by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxSize().safeDrawingPadding()) {
                AndroidView(factory={ctx -> SignaturePad(ctx).also {
                    pad=it; it.onInk={value -> hasInk=value}; savedInk?.let(it::restoreDrawing)
                }},modifier=Modifier.weight(1f).fillMaxHeight())
                Surface(color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.width(80.dp).fillMaxHeight()) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly) {
                        ToolIcon(EditorIcons.Back,"Cancel drawing",onClick={finish()})
                        ToolIcon(EditorIcons.Undo,"Undo stroke",onClick={pad?.undo()},enabled=hasInk)
                        ToolIcon(EditorIcons.Delete,"Clear signature",onClick={pad?.clear()},enabled=hasInk)
                        ToolIcon(EditorIcons.Check,"Accept drawing",onClick={
                            pad?.capture()?.let { (strokes,aspect) ->
                                val ink=Signature("","",strokes,aspect).json().toString()
                                setResult(Activity.RESULT_OK,Intent().putExtra("drawing",ink)); finish()
                            }
                        },enabled=hasInk)
                    }
                }
            }
        } }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        pad?.let { outState.putString("ink",it.saveDrawing()) }
        super.onSaveInstanceState(outState)
    }
}
