package com.nivek.paperwork

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable fun BackupDialog(onDismiss: ()->Unit,onExport: ()->Unit,onImport: ()->Unit) {
    val strings=localizedResources()
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(strings.getString(R.string.backup_transfer),style=MaterialTheme.typography.titleLarge)
                Text(strings.getString(R.string.backup_description))
                Button(onClick=onExport,modifier=Modifier.fillMaxWidth()) { Text(strings.getString(R.string.export_library)) }
                OutlinedButton(onClick=onImport,modifier=Modifier.fillMaxWidth()) { Text(strings.getString(R.string.import_library)) }
                Text(strings.getString(R.string.backup_android_description),style=MaterialTheme.typography.bodySmall)
                TextButton(onClick=onDismiss) { Text(strings.getString(R.string.close)) }
            }
        }
    }
}
