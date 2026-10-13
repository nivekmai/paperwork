package com.nivek.paperwork

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

fun currentDateText(date: LocalDate=LocalDate.now(),locale: Locale=Locale.getDefault()): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

@Composable fun TextLibraryDialog(vm: EditorModel,onDismiss: ()->Unit,onInsert: ((String)->Unit)?) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState=rememberLazyListState()
    if(!adding && editingId==null) Dialog(onDismissRequest=onDismiss) {
        Surface(shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max=700.dp).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(onInsert==null) "Saved text" else "Add text",fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss) { Text("Close") }
                }
                LazyColumn(Modifier.weight(1f,fill=false),state=listState,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(onInsert!=null) {
                        item { Button(onClick={onInsert("")},modifier=Modifier.fillMaxWidth()) {
                            Icon(EditorIcons.Text,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Blank text")
                        } }
                        item { TextInsertCard("Today's date",currentDateText()) { onInsert(currentDateText()) } }
                        if(vm.signatures.isNotEmpty()) {
                            item { Text("Names from your signatures",fontWeight=FontWeight.SemiBold) }
                            items(vm.signatures,key={"signature:${it.id}"}) { signature ->
                                TextInsertCard(signature.name,"Insert as text") { onInsert(signature.name) }
                            }
                        }
                        item { HorizontalDivider(); Spacer(Modifier.height(12.dp)); Text("Saved text",fontWeight=FontWeight.SemiBold) }
                    }
                    item { Text("Keep details such as your address, email, or phone number ready for any form. Saved only on this device.",fontSize=14.sp) }
                    item { OutlinedButton(onClick={adding=true},modifier=Modifier.fillMaxWidth()) {
                        Icon(EditorIcons.Add,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Add saved text")
                    } }
                    items(vm.savedTexts,key={"text:${it.id}"}) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(entry.label,fontWeight=FontWeight.SemiBold)
                                Text(entry.text,maxLines=4,overflow=TextOverflow.Ellipsis)
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    if(onInsert!=null) TextButton(onClick={onInsert(entry.text)},modifier=Modifier.weight(1f)) { Text("Insert ${entry.label}",maxLines=1,overflow=TextOverflow.Ellipsis) }
                                    else Spacer(Modifier.weight(1f))
                                    ToolIcon(EditorIcons.Edit,"Edit saved text: ${entry.label}",onClick={editingId=entry.id})
                                    ToolIcon(EditorIcons.Delete,"Delete saved text: ${entry.label}",onClick={deletingId=entry.id})
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val editing=vm.savedTexts.find { it.id==editingId }
    if(adding || editing!=null) SavedTextEditor(editing,onDismiss={adding=false; editingId=null},onSave={entry ->
        if(vm.saveText(entry)) { adding=false; editingId=null }
    })
    vm.savedTexts.find { it.id==deletingId }?.let { entry ->
        AlertDialog(onDismissRequest={deletingId=null},title={Text("Delete ${entry.label}?")},
            text={Text("This removes the saved entry. Text already placed in PDFs is kept.")},
            confirmButton={TextButton(onClick={vm.deleteText(entry); deletingId=null}) { Text("Delete") }},
            dismissButton={TextButton(onClick={deletingId=null}) { Text("Cancel") }})
    }
}

@Composable private fun TextInsertCard(title: String,subtitle: String,onClick: ()->Unit) {
    Card(onClick=onClick,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,fontWeight=FontWeight.SemiBold)
                Text(subtitle,fontSize=14.sp)
            }
            Icon(EditorIcons.Add,null,Modifier.size(20.dp))
        }
    }
}

@Composable fun SavedTextEditor(entry: SavedText?=null,initialText: String="",onDismiss: ()->Unit,onSave: (SavedText)->Unit) {
    val id=rememberSaveable(entry?.id) { entry?.id ?: UUID.randomUUID().toString() }
    var label by rememberSaveable(id) { mutableStateOf(entry?.label.orEmpty()) }
    var value by rememberSaveable(id) { mutableStateOf(entry?.text ?: initialText) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(entry==null) "New saved text" else "Edit saved text",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                OutlinedTextField(value=label,onValueChange={label=it},label={Text("Label")},placeholder={Text("Address, name, email…")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=value,onValueChange={value=it},label={Text("Text to insert")},placeholder={Text("Enter your details")},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
                Text("Use line breaks for an address or other multi-line details.",fontSize=12.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text("Cancel") }
                    Button(onClick={onSave(SavedText(id,label,value))},enabled=label.isNotBlank() && value.isNotBlank()) { Text("Save text") }
                }
            }
        }
    }
}
