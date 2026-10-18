package com.nivek.paperwork

import androidx.compose.foundation.clickable
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
    val strings=localizedResources()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState=rememberLazyListState()
    if(!adding && editingId==null) Dialog(onDismissRequest=onDismiss) {
        Surface(shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.fillMaxWidth().heightIn(max=700.dp).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(if(onInsert==null) strings.getString(R.string.saved_text) else strings.getString(R.string.add_text),fontSize=22.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                    TextButton(onClick=onDismiss) { Text(strings.getString(R.string.close)) }
                }
                LazyColumn(Modifier.weight(1f,fill=false),state=listState,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(onInsert!=null) {
                        item { Button(onClick={onInsert("")},modifier=Modifier.fillMaxWidth()) {
                            Icon(EditorIcons.Text,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.blank_text))
                        } }
                        item { OutlinedButton(onClick={onInsert(currentDateText(locale=strings.configuration.locales[0]))},modifier=Modifier.fillMaxWidth()) { Text(currentDateText(locale=strings.configuration.locales[0])) } }
                        items(vm.signatures,key={"signature:${it.id}"}) { signature ->
                            OutlinedButton(onClick={onInsert(signature.name)},modifier=Modifier.fillMaxWidth()) { Text(signature.name) }
                        }
                    }
                    item { OutlinedButton(onClick={adding=true},modifier=Modifier.fillMaxWidth()) {
                        Icon(EditorIcons.Add,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.add_saved_text))
                    } }
                    items(vm.savedTexts,key={"text:${it.id}"}) { entry ->
                        Card(Modifier.fillMaxWidth().then(if(onInsert!=null) Modifier.clickable { onInsert(entry.text) } else Modifier)) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                    Text(entry.label,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                                    ToolIcon(EditorIcons.Edit,strings.getString(R.string.edit_named_text,entry.label),onClick={editingId=entry.id})
                                    ToolIcon(EditorIcons.Delete,strings.getString(R.string.delete_named_text,entry.label),onClick={deletingId=entry.id})
                                }
                                Text(entry.text,maxLines=4,overflow=TextOverflow.Ellipsis)
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
        AlertDialog(onDismissRequest={deletingId=null},title={Text(strings.getString(R.string.delete_named,entry.label))},
            text={Text(strings.getString(R.string.delete_text_message))},
            confirmButton={TextButton(onClick={vm.deleteText(entry); deletingId=null}) { Text(strings.getString(R.string.delete)) }},
            dismissButton={TextButton(onClick={deletingId=null}) { Text(strings.getString(R.string.cancel)) }})
    }
}

@Composable fun SavedTextEditor(entry: SavedText?=null,initialText: String="",onDismiss: ()->Unit,onSave: (SavedText)->Unit) {
    val strings=localizedResources()
    val id=rememberSaveable(entry?.id) { entry?.id ?: UUID.randomUUID().toString() }
    var label by rememberSaveable(id) { mutableStateOf(entry?.label.orEmpty()) }
    var value by rememberSaveable(id) { mutableStateOf(entry?.text ?: initialText) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp),shape=MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(if(entry==null) strings.getString(R.string.new_saved_text) else strings.getString(R.string.edit_saved_text),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                OutlinedTextField(value=label,onValueChange={label=it},label={Text(strings.getString(R.string.label))},placeholder={Text(strings.getString(R.string.saved_text_placeholder))},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(value=value,onValueChange={value=it},label={Text(strings.getString(R.string.text_to_insert))},placeholder={Text(strings.getString(R.string.enter_details))},minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
                Text(strings.getString(R.string.multiline_hint),fontSize=12.sp)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text(strings.getString(R.string.cancel)) }
                    Button(onClick={onSave(SavedText(id,label,value))},enabled=label.isNotBlank() && value.isNotBlank()) { Text(strings.getString(R.string.save_text)) }
                }
            }
        }
    }
}
