package com.nivek.paperwork

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import kotlin.math.min

class MainActivity: ComponentActivity() {
    private val model: EditorModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent { PaperworkTheme { Paperwork(model) } }
        if(savedInstanceState==null) receive(intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); receive(intent) }
    @Suppress("DEPRECATION") private fun receive(intent: Intent) {
        val uri = when(intent.action) { Intent.ACTION_VIEW -> intent.data; Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM); else -> null }
        if(uri!=null) model.import(uri)
    }
}
private val Green=Color(0xff246b5b)
@Composable fun PaperworkTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Green,secondary=Color(0xff547267),background=Color(0xfff6f8f4),surface=Color(0xfff6f8f4),surfaceVariant=Color(0xffe5ece6)),content=content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Paperwork(vm: EditorModel) {
    val strings=localizedResources()
    val context=LocalContext.current
    var backupScreen by rememberSaveable { mutableStateOf(false) }
    var signatureScreen by rememberSaveable { mutableStateOf(false) }
    var textEditor by remember { mutableStateOf<Mark?>(null) }
    var shapeMenu by remember { mutableStateOf(false) }
    var textLibrary by rememberSaveable { mutableStateOf(false) }
    var saveTextValue by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTextInput by remember { mutableStateOf<Mark?>(null) }
    var exportScreen by remember { mutableStateOf(false) }
    var deleteDraft by remember { mutableStateOf<Draft?>(null) }
    var pendingExportPath by rememberSaveable { mutableStateOf<String?>(null) }
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> if(uri!=null) pendingExportPath?.let { vm.copyExport(File(it),uri) } }
    val saveLibrary=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportLibrary) }
    val restoreLibrary=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importLibrary) }
    val snack=remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snack.showSnackbar(it); vm.message=null } }
    val d=vm.draft
    val selected=d?.marks?.find { it.id==vm.selected }
    var editor by remember { mutableStateOf<PageEditor?>(null) }
    var editingText by remember { mutableStateOf(false) }
    var doodling by remember { mutableStateOf(false) }
    val keyboardActive=editingText || WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)>0
    var quickColor by rememberSaveable { mutableIntStateOf(0xff172c27.toInt()) }
    var quickStroke by rememberSaveable { mutableFloatStateOf(2f) }
    var quickTextSize by rememberSaveable { mutableFloatStateOf(16f) }
    BackHandler(enabled=(signatureScreen || d!=null) && !vm.busy) { if(editingText) editor?.finishEditing() else if(doodling) doodling=false else if(signatureScreen) signatureScreen=false else if(exportScreen) exportScreen=false else vm.close() }
    fun fresh(kind: String): Mark {
        val p=vm.pageImage
        val w=min(if(kind=="Text") 240f else if(kind=="Line") 160f else 48f,(p?.width ?: 612f)*.75f)
        val h=min(if(kind=="Text") 70f else if(kind=="Line") 24f else 48f,(p?.height ?: 792f)*.75f)
        val mark=Mark(page=vm.page,kind=kind,color=quickColor,strokeWidth=quickStroke,size=quickTextSize,x=((p?.width ?: 612f)-w)/2,y=((p?.height ?: 792f)-h)/2,width=w,height=h)
        return editor?.canvas?.centerInViewport(mark) ?: mark
    }
    fun insertText(value: String) {
        textLibrary=false
        var mark=fresh("Text").copy(text=value)
        if(value.isNotEmpty()) {
            mark=mark.copy(height=maxOf(mark.height,MarkPainter.textLayout(mark).height+2f).coerceAtMost(vm.pageImage!!.height))
            mark=editor?.canvas?.centerInViewport(mark) ?: mark
        }
        vm.add(mark)
        if(value.isEmpty()) pendingTextInput=mark
    }
    LaunchedEffect(textLibrary,pendingTextInput) {
        if(!textLibrary) pendingTextInput?.let { mark ->
            vm.beginInlineEdit(mark.id,alreadyRecorded=true); editor?.startEditing(mark); pendingTextInput=null
        }
    }
    fun exportAction(pages: List<Int>,share: Boolean) {
        editor?.finishEditing(); doodling=false
        exportScreen=false
        vm.export(pages) { file ->
            if(share) {
                val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                val intent=Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent,strings.getString(R.string.share_pdf)))
            } else { pendingExportPath=file.absolutePath; save.launch(strings.getString(R.string.export_filename,vm.draft!!.name.substringBeforeLast('.'))) }
        }
    }
    Scaffold(
        modifier=Modifier.imePadding(),
        topBar={ TopAppBar(title={ Column {
            Text(if(signatureScreen) strings.getString(R.string.saved_signatures) else if(exportScreen) strings.getString(R.string.export_pdf) else if(d==null) strings.getString(R.string.app_name) else d.name, maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
            Text(if(signatureScreen) strings.getString(R.string.library_subtitle) else if(d==null) strings.getString(R.string.tagline) else strings.getString(R.string.local_draft),fontSize=12.sp,color=Green)
        } },navigationIcon={ if(d!=null || signatureScreen) ToolIcon(EditorIcons.Back,strings.getString(R.string.back),onClick={ if(editingText) editor?.finishEditing() else if(doodling) doodling=false else if(signatureScreen) signatureScreen=false else if(exportScreen) exportScreen=false else vm.close() },enabled=!vm.busy) },actions={
            if(d!=null && !signatureScreen && !exportScreen) ToolIcon(EditorIcons.Export,strings.getString(R.string.export),
                onClick={ editor?.finishEditing(); doodling=false; exportScreen=true },enabled=!vm.busy)
        }) },
        snackbarHost={ SnackbarHost(snack) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                exportScreen && d!=null -> ExportOptions(d,onDismiss={exportScreen=false},onExport=::exportAction)
                signatureScreen -> SignatureLibrary(vm,onInsert=if(d==null) null else { s ->
                    val p=vm.pageImage!!; val w=min(min(180f,p.width*.6f),p.height*.5f*s.aspect); val h=w/s.aspect
                    val mark=Mark(page=vm.page,kind="Signature",x=(p.width-w)/2,y=(p.height-h)/2,width=w,height=h,strokes=s.strokes,size=12f,color=quickColor,strokeWidth=quickStroke)
                    vm.add(editor?.canvas?.signatureForInsertion(mark) ?: mark); signatureScreen=false
                })
                d==null -> LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item { Spacer(Modifier.height(10.dp)); Card(colors=CardDefaults.cardColors(containerColor=Color(0xffe4eee7))) {
                        Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Text(strings.getString(R.string.home_title),fontSize=28.sp,fontWeight=FontWeight.Bold)
                            Text(strings.getString(R.string.home_intro))
                            Button(onClick={open.launch(arrayOf("application/pdf"))},modifier=Modifier.fillMaxWidth()) { Icon(EditorIcons.Open,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.open_pdf)) }
                        }
                    } }
                    item { OutlinedButton(onClick={signatureScreen=true},modifier=Modifier.fillMaxWidth()) { Text(strings.getString(R.string.manage_signatures,vm.signatures.size)) } }
                    item { OutlinedButton(onClick={textLibrary=true},modifier=Modifier.fillMaxWidth()) { Icon(EditorIcons.SavedText,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.manage_saved_text,vm.savedTexts.size)) } }
                    item { OutlinedButton(onClick={backupScreen=true},modifier=Modifier.fillMaxWidth()) { Text(strings.getString(R.string.backup_transfer)) } }
                    item { Text(strings.getString(R.string.your_drafts),fontSize=20.sp,fontWeight=FontWeight.SemiBold) }
                    if(vm.drafts.isEmpty()) item { Text(strings.getString(R.string.empty_drafts),color=Color(0xff63716a)) }
                    items(vm.drafts,key={it.id}) { draft ->
                        Card(onClick={vm.open(draft)},modifier=Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(draft.name,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis); Text(strings.getString(R.string.draft_summary,draft.pageCount,draft.marks.size),fontSize=13.sp,color=Color(0xff63716a)) }
                                ToolIcon(EditorIcons.Delete,strings.getString(R.string.delete_draft),onClick={deleteDraft=draft})
                            }
                        }
                    }
                    item { Text(strings.getString(R.string.home_storage),fontSize=12.sp,color=Color(0xff63716a)); Spacer(Modifier.height(16.dp)) }
                }
                else -> Column(Modifier.fillMaxSize()) {
                    Surface(color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            ToolIcon(EditorIcons.Back,strings.getString(R.string.previous_page),onClick={editor?.finishEditing(); doodling=false; vm.go(vm.page-1)},enabled=vm.page>0 && !vm.busy)
                            Text(strings.getString(R.string.page_position,vm.page+1,d.pageCount),fontSize=14.sp)
                            ToolIcon(EditorIcons.Next,strings.getString(R.string.next_page),onClick={editor?.finishEditing(); doodling=false; vm.go(vm.page+1)},enabled=vm.page<d.pageCount-1 && !vm.busy)
                        }
                    }
                    NudgeBar(selected=selected!=null && !doodling && !keyboardActive) { dx,dy ->
                        editor?.finishEditing()
                        selected?.let { mark -> editor?.canvas?.nudge(mark,dx,dy)?.let(vm::update) }
                    }
                    AndroidView(factory={ctx -> PageEditor(ctx).also { next ->
                        val previous=editor?.canvas
                        next.canvas.page=vm.pageImage
                        if(previous?.page === vm.pageImage) previous?.viewport()?.let(next.canvas::restoreViewport)
                        editor=next
                    }},update={ v ->
                        v.canvas.page=vm.pageImage; v.canvas.pageIndex=vm.page; v.canvas.drawingMode=doodling; v.canvas.marks=d.marks.filter { it.page==vm.page }; v.canvas.selected=vm.selected
                        v.canvas.defaultColor=quickColor; v.canvas.defaultStroke=quickStroke
                        v.canvas.onSelect={vm.selected=it}; v.canvas.onChange=vm::update
                        v.canvas.onDoodle={ mark ->
                            if(vm.draft!!.marks.any { it.id==mark.id }) vm.update(mark) else vm.add(mark)
                            vm.selected=mark.id
                            v.canvas.marks=vm.draft!!.marks.filter { it.page==vm.page }
                        }
                        v.onBeginText={vm.beginInlineEdit(it)}; v.onTextChange=vm::updateInlineText
                        v.onFinishText=vm::endInlineEdit; v.onEditingChanged={editingText=it}
                    },modifier=Modifier.fillMaxWidth().weight(1f).clipToBounds())
                    Surface(color=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=4.dp,modifier=Modifier.fillMaxWidth()) {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min=maxWidth).padding(horizontal=8.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                            if(editingText) ToolIcon(EditorIcons.Check,strings.getString(R.string.done_editing),onClick={editor?.finishEditing()})
                            if(doodling) ToolIcon(EditorIcons.Check,strings.getString(R.string.finish_doodle),onClick={doodling=false})
                            ToolIcon(EditorIcons.Undo,strings.getString(R.string.undo),onClick={editor?.finishEditing(); vm.undo()},enabled=vm.canUndo)
                            ToolIcon(EditorIcons.Redo,strings.getString(R.string.redo),onClick={editor?.finishEditing(); vm.redo()},enabled=vm.canRedo)
                            QuickStyleBar(color=selected?.color ?: quickColor,width=selected?.let { if(it.kind=="Text") it.size else it.strokeWidth } ?: quickStroke,isText=selected?.kind=="Text",allowTransparent=!doodling && selected?.kind!="Doodle",keepKeyboard=keyboardActive,onColor={ color ->
                                quickColor=color
                                selected?.let { val changed=it.copy(color=color); if(editor?.applyInlineStyle(changed)!=true) vm.update(changed) }
                            },onWidth={ width ->
                                if(selected?.kind=="Text") {
                                    quickTextSize=width; val changed=selected.copy(size=width)
                                    if(editor?.applyInlineStyle(changed)!=true) vm.update(changed)
                                } else { quickStroke=width; selected?.let { vm.update(it.copy(strokeWidth=width)) } }
                            })
                            if(selected!=null) {
                                ToolIcon(EditorIcons.Edit,strings.getString(R.string.edit_style),onClick={editor?.finishEditing(); doodling=false; textEditor=selected})
                                if(selected.kind=="Text" && selected.text.isNotBlank()) ToolIcon(EditorIcons.SavedText,strings.getString(R.string.save_to_library),onClick={editor?.finishEditing(); saveTextValue=selected.text})
                                ToolIcon(EditorIcons.Copy,strings.getString(R.string.duplicate),onClick={editor?.finishEditing(); doodling=false; vm.duplicate()})
                                ToolIcon(EditorIcons.Delete,strings.getString(R.string.delete),onClick={editor?.finishEditing(); doodling=false; vm.removeSelected()},tint=MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    }
                    if(!keyboardActive) Surface(color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                        Column {
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth().padding(10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Button(onClick={
                                    editor?.finishEditing(); doodling=false; textLibrary=true
                                },modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) { Icon(EditorIcons.Text,null,Modifier.size(20.dp)); Text(strings.getString(R.string.tool_text),fontSize=12.sp) }
                                }
                                OutlinedButton(onClick={editor?.finishEditing(); doodling=false; shapeMenu=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) { Icon(EditorIcons.Shape,null,Modifier.size(20.dp)); Text(strings.getString(R.string.tool_shape),fontSize=12.sp) }
                                }
                                OutlinedButton(onClick={editor?.finishEditing(); doodling=false; signatureScreen=true},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp)) {
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) { Icon(EditorIcons.Sign,null,Modifier.size(20.dp)); Text(strings.getString(R.string.tool_sign),fontSize=12.sp) }
                                }
                                FilledTonalButton(onClick={editor?.finishEditing(); doodling=!doodling; if(doodling) { vm.selected=null; if(android.graphics.Color.alpha(quickColor)==0) quickColor=0xff172c27.toInt() }},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=10.dp),colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(doodling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,contentColor=if(doodling) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)) {
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) { Icon(EditorIcons.Doodle,null,Modifier.size(20.dp)); Text(strings.getString(R.string.tool_doodle),fontSize=12.sp) }
                                }
                            }

                        }
                    }
                }
            }
            if(vm.busy) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.15f)).clickable(enabled=true,onClick={}),contentAlignment=Alignment.Center) {
                Surface(shape=RoundedCornerShape(18.dp)) { Row(Modifier.padding(24.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(strings.getString(R.string.working)) } }
            }
        }
    }
    if(backupScreen) BackupDialog(onDismiss={backupScreen=false},onExport={backupScreen=false; saveLibrary.launch(strings.getString(R.string.library_filename))},onImport={backupScreen=false; restoreLibrary.launch(arrayOf("application/json","text/plain","application/octet-stream"))})
    if(textLibrary) TextLibraryDialog(vm,onDismiss={textLibrary=false},onInsert=if(d==null) null else ::insertText)
    saveTextValue?.let { value -> SavedTextEditor(initialText=value,onDismiss={saveTextValue=null},onSave={entry -> if(vm.saveText(entry)) { saveTextValue=null; vm.message=strings.getString(R.string.text_saved) }}) }
    if(shapeMenu) ShapePicker(onDismiss={shapeMenu=false},onPick={kind -> vm.add(fresh(kind)); shapeMenu=false})
    textEditor?.let { mark -> MarkDialog(mark,vm.pageImage!!,onDismiss={textEditor=null},onSave={vm.update(it); textEditor=null}) }
    deleteDraft?.let { draft -> AlertDialog(onDismissRequest={deleteDraft=null},title={Text(strings.getString(R.string.delete_local_draft))},text={Text(strings.getString(R.string.delete_draft_message,draft.name))},confirmButton={TextButton(onClick={vm.delete(draft); deleteDraft=null}) { Text(strings.getString(R.string.delete)) }},dismissButton={TextButton(onClick={deleteDraft=null}) { Text(strings.getString(R.string.cancel)) }}) }
    vm.error?.let { error -> AlertDialog(onDismissRequest={vm.error=null},title={Text(strings.getString(R.string.operation_failed_title))},text={Text(error)},confirmButton={TextButton(onClick={vm.error=null}) { Text(strings.getString(R.string.ok)) }}) }
}

@Composable fun ExportOptions(d: Draft,onDismiss: ()->Unit,onExport: (List<Int>,Boolean)->Unit) {
    val strings=localizedResources()
    var range by remember { mutableStateOf("") }
    val result=remember(range,d.pageCount) { runCatching { parsePages(range,d.pageCount) } }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Text(strings.getString(R.string.choose_pages),fontSize=26.sp,fontWeight=FontWeight.SemiBold)
        Text(strings.getString(R.string.pages_hint))
        OutlinedTextField(value=range,onValueChange={range=it},label={Text(strings.getString(R.string.pages_label))},isError=result.isFailure,singleLine=true,modifier=Modifier.fillMaxWidth())
        Text(result.fold({strings.getString(R.string.selected_pages,it.size,d.pageCount)},{it.userMessage(strings)}),fontSize=14.sp,color=if(result.isFailure) MaterialTheme.colorScheme.error else Green)
        Text(strings.getString(R.string.export_explainer),fontSize=14.sp)
        Button(onClick={onExport(result.getOrThrow(),false)},enabled=result.isSuccess,modifier=Modifier.fillMaxWidth()) { Icon(EditorIcons.Download,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.save_pdf)) }
        OutlinedButton(onClick={onExport(result.getOrThrow(),true)},enabled=result.isSuccess,modifier=Modifier.fillMaxWidth()) { Icon(EditorIcons.Share,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.share)) }
        TextButton(onClick=onDismiss,modifier=Modifier.align(Alignment.End)) { Text(strings.getString(R.string.cancel)) }
    }
}

@Composable fun ShapePicker(onDismiss: ()->Unit,onPick: (String)->Unit) {
    val strings=localizedResources()
    Dialog(onDismissRequest=onDismiss) {
        Surface(shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    ToolIcon(EditorIcons.Back,strings.getString(R.string.back),onClick=onDismiss)
                    Text(strings.getString(R.string.insert_shape),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                }
                listOf("Circle","Square","Line","Check","X").chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        row.forEach { kind ->
                            Surface(onClick={onPick(kind)},modifier=Modifier.weight(1f).aspectRatio(1f).semantics { contentDescription=strings.optionLabel(kind) },shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                                Canvas(Modifier.fillMaxSize().padding(18.dp)) {
                                    drawIntoCanvas { c ->
                                        val h=size.height
                                        MarkPainter.draw(c.nativeCanvas,Mark(kind=kind,x=2f,y=(size.height-h)/2+2,width=size.width-4,height=h-4,strokeWidth=6f))
                                    }
                                }
                            }
                        }
                        repeat(3-row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable fun ArrowheadPicker(label: String,value: String,start: Boolean,onChange: (String)->Unit) {
    val strings=localizedResources()
    Text(label,fontWeight=FontWeight.Medium)
    arrowheadStyles.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            row.forEach { style ->
                Surface(onClick={onChange(style)},modifier=Modifier.weight(1f).semantics { contentDescription=strings.getString(R.string.labeled_value,label,strings.optionLabel(style)) },shape=RoundedCornerShape(10.dp),color=if(value==style) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,border=if(value==style) BorderStroke(2.dp,Green) else null) {
                    Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
                            drawIntoCanvas { c -> MarkPainter.draw(c.nativeCanvas,Mark(kind="Line",x=4f,y=0f,width=size.width-8,height=size.height,strokeWidth=2f,startArrow=if(start) style else "None",endArrow=if(start) "None" else style)) }
                        }
                        Text(strings.optionLabel(style),fontSize=11.sp)
                    }
                }
            }
        }
    }
}

@Composable fun MarkDialog(initial: Mark,page: PageImage,onDismiss: ()->Unit,onSave: (Mark)->Unit) {
    val strings=localizedResources()
    var m by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max=740.dp),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    ToolIcon(EditorIcons.Back,strings.getString(R.string.back),onClick=onDismiss)
                    Text(strings.getString(R.string.mark_style,strings.optionLabel(m.kind)),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                }
                Text(strings.getString(R.string.preview),fontSize=12.sp,color=Green)
                MarkPreview(m,Modifier.fillMaxWidth().height(100.dp).semantics { contentDescription=strings.getString(R.string.style_preview) })
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(m.kind=="Text") {
                        Row(Modifier.horizontalScroll(rememberScrollState())) { listOf("sans-serif" to strings.getString(R.string.font_sans),"serif" to strings.getString(R.string.font_serif),"monospace" to strings.getString(R.string.font_mono),"cursive" to strings.getString(R.string.font_script)).forEach { (value,label) -> FilterChip(selected=m.font==value,onClick={m=m.copy(font=value)},label={Text(label)},modifier=Modifier.padding(end=6.dp)) } }
                        Text(strings.getString(R.string.font_size_value,m.size.toInt()))
                        Slider(value=m.size,onValueChange={m=m.copy(size=it)},valueRange=8f..64f)
                        Text(strings.getString(R.string.line_spacing_value,m.spacing))
                        Slider(value=m.spacing,onValueChange={m=m.copy(spacing=it)},valueRange=1f..2.5f)
                        ColorPicker(strings.getString(R.string.text_color),m.color,false) { m=m.copy(color=it) }
                        ColorPicker(strings.getString(R.string.background),m.background,true) { m=m.copy(background=it) }
                    } else {
                        Text(strings.getString(R.string.stroke_width_value,m.strokeWidth))
                        Slider(value=m.strokeWidth.coerceIn(.5f,12f),onValueChange={m=m.copy(strokeWidth=it)},valueRange=.5f..12f,modifier=Modifier.semantics { contentDescription=strings.getString(R.string.stroke_width) })
                        if(m.isShape) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                            listOf("Solid","Dashed","Dotted").forEach { style -> FilterChip(selected=m.strokeStyle==style,onClick={m=m.copy(strokeStyle=style)},label={Text(strings.optionLabel(style))}) }
                        }
                        if(m.kind=="Square") {
                            val maxRadius=min(m.width,m.height)/2
                            val radius=m.cornerRadius.coerceIn(0f,maxRadius)
                            Text(strings.getString(R.string.corner_radius_value,radius))
                            Slider(value=radius,onValueChange={m=m.copy(cornerRadius=it)},valueRange=0f..maxRadius,modifier=Modifier.semantics { contentDescription=strings.getString(R.string.corner_radius) })
                        }
                        ColorPicker(strings.getString(R.string.stroke_color),m.color,true) { m=m.copy(color=it) }
                        if(m.isClosedShape) ColorPicker(strings.getString(R.string.fill_color),m.fill,true) { m=m.copy(fill=it) }
                        if(m.kind=="Line") {
                            ArrowheadPicker(strings.getString(R.string.start_arrowhead),m.startArrow,true) { m=m.copy(startArrow=it) }
                            ArrowheadPicker(strings.getString(R.string.end_arrowhead),m.endArrow,false) { m=m.copy(endArrow=it) }
                            Text(strings.getString(R.string.line_hint),fontSize=12.sp)
                        }
                    }
                    if(m.kind=="Text") Text(strings.getString(R.string.edit_text_hint),fontSize=12.sp)
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text(strings.getString(R.string.cancel)) }
                    Button(onClick={onSave(m)}) { Icon(EditorIcons.Check,null,Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.apply)) }
                }
            }
        }
    }
}
@Composable fun MarkPreview(mark: Mark,modifier: Modifier=Modifier) {
    Canvas(modifier.background(Color.White,RoundedCornerShape(8.dp)).border(1.dp,Color(0xffd3ddd5),RoundedCornerShape(8.dp))) {
        drawIntoCanvas { c ->
            val scale=min((size.width-16)/mark.width,(size.height-16)/mark.height).coerceAtLeast(.01f)
            c.nativeCanvas.save(); c.nativeCanvas.translate((size.width-mark.width*scale)/2,(size.height-mark.height*scale)/2); c.nativeCanvas.scale(scale,scale)
            MarkPainter.draw(c.nativeCanvas,mark.copy(x=0f,y=0f)); c.nativeCanvas.restore()
        }
    }
}

@Composable fun SignatureLibrary(vm: EditorModel,onInsert: ((Signature)->Unit)?) {
    val strings=localizedResources()
    var drawing by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Signature?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Button(onClick={drawing=true},modifier=Modifier.fillMaxWidth()) { Icon(EditorIcons.Add,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(strings.getString(R.string.draw_new_signature)) } }
        if(vm.signatures.isEmpty()) item { Text(strings.getString(R.string.signature_intro)) }
        items(vm.signatures,key={it.id}) { s ->
            Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(s.name,fontWeight=FontWeight.SemiBold)
                MarkPreview(Mark(kind="Signature",width=200f,height=200f/s.aspect,strokes=s.strokes,size=12f),Modifier.fillMaxWidth().height(90.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    ToolIcon(EditorIcons.Delete,strings.getString(R.string.delete_signature),onClick={deleting=s})
                    if(onInsert!=null) Button(onClick={onInsert(s)}) { Text(strings.getString(R.string.insert)) }
                }
            } }
        }
        item { Text(strings.getString(R.string.signature_footer),fontSize=12.sp,color=Color(0xff63716a)) }
    }
    if(drawing) SignatureDialog(onDismiss={drawing=false},onSave={name,strokes,aspect -> vm.saveSignature(name,strokes,aspect); drawing=false})
    deleting?.let { s -> AlertDialog(onDismissRequest={deleting=null},title={Text(strings.getString(R.string.delete_named,s.name))},text={Text(strings.getString(R.string.delete_signature_message))},confirmButton={TextButton(onClick={vm.deleteSignature(s); deleting=null}) { Text(strings.getString(R.string.delete)) }},dismissButton={TextButton(onClick={deleting=null}) { Text(strings.getString(R.string.cancel)) }}) }
}
@Composable fun SignatureDialog(onDismiss: ()->Unit,onSave: (String,List<List<InkPoint>>,Float)->Unit) {
    val strings=localizedResources()
    var name by rememberSaveable { mutableStateOf("") }
    var acceptedDrawing by rememberSaveable { mutableStateOf<String?>(null) }
    val context=LocalContext.current
    val activity=context as android.app.Activity
    val previousOrientation=rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) {
        activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { if(!activity.isChangingConfigurations) activity.requestedOrientation=previousOrientation }
    }
    val draw=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==android.app.Activity.RESULT_OK) result.data?.getStringExtra("drawing")?.let { acceptedDrawing=it }
    }
    var rotateCue by remember { mutableStateOf(false) }
    if(rotateCue) {
        RotatePhoneCue(onDismiss={rotateCue=false},onReady={rotateCue=false; draw.launch(Intent(context,SignatureDrawingActivity::class.java))})
        return
    }
    val drawing=remember(acceptedDrawing) { acceptedDrawing?.let { org.json.JSONObject(it) } }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp),shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(strings.getString(R.string.draw_signature),fontSize=22.sp,fontWeight=FontWeight.SemiBold)
                OutlinedTextField(value=name,onValueChange={name=it},label={Text(strings.getString(R.string.signature_name))},modifier=Modifier.fillMaxWidth(),singleLine=true)
                if(drawing!=null) MarkPreview(Mark(kind="Signature",width=200f,height=200f/drawing.getDouble("aspect").toFloat(),strokes=readInk(drawing.getJSONArray("strokes"))),Modifier.fillMaxWidth().height(140.dp))
                else Text(strings.getString(R.string.landscape_hint))
                OutlinedButton(onClick={ rotateCue=true },modifier=Modifier.fillMaxWidth()) {
                    Icon(EditorIcons.Sign,null,Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if(drawing==null) strings.getString(R.string.draw_landscape) else strings.getString(R.string.redraw_landscape))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=onDismiss) { Text(strings.getString(R.string.cancel)) }
                    Button(onClick={drawing?.let { onSave(name,readInk(it.getJSONArray("strokes")),it.getDouble("aspect").toFloat()) }},enabled=drawing!=null) { Text(strings.getString(R.string.save_signature)) }
                }
            }
        }
    }
}
