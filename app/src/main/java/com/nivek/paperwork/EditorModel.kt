package com.nivek.paperwork

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class EditorModel(app: Application) : AndroidViewModel(app) {
    private val strings get() = getApplication<Application>().resources
    val files = PdfFiles(app)
    var drafts by mutableStateOf(files.drafts()); private set
    var signatures by mutableStateOf(runCatching { files.signatures() }.getOrDefault(emptyList())); private set
    var savedTexts by mutableStateOf(runCatching { files.savedTexts() }.getOrDefault(emptyList())); private set
    var draft by mutableStateOf<Draft?>(null); private set
    var page by mutableIntStateOf(0); private set
    var pageImage by mutableStateOf<PageImage?>(null); private set
    var selected by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    private var inlineId: String?=null
    private var inlineHasCheckpoint=false
    fun beginInlineEdit(id: String, alreadyRecorded: Boolean=false) {
        if(inlineId==id) return
        inlineId=id; inlineHasCheckpoint=alreadyRecorded
    }
    fun endInlineEdit() { inlineId=null; inlineHasCheckpoint=false }
    fun updateInlineText(mark: Mark) {
        val d=draft ?: return
        if(d.marks.none { it.id==mark.id }) return
        if(inlineId!=mark.id) beginInlineEdit(mark.id)
        val next=d.copy(marks=d.marks.map { if(it.id==mark.id) mark else it })
        if(next==d) return
        try {
            files.save(next)
            if(!inlineHasCheckpoint) { undo.addLast(d.marks); if(undo.size>50) undo.removeFirst(); inlineHasCheckpoint=true }
            redo.clear(); draft=next; historyState()
        } catch(e: Exception) { error=strings.getString(R.string.draft_not_saved) }
    }
    private val undo = ArrayDeque<List<Mark>>()
    private val redo = ArrayDeque<List<Mark>>()
    private fun historyState() { canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty() }
    private fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try { block() } catch(e: Exception) { error = e.userMessage(strings) }
            finally { busy = false }
        }
    }
    fun import(uri: Uri) = task {
        val d = withContext(Dispatchers.IO) { files.import(uri) }
        openInternal(d)
        drafts = withContext(Dispatchers.IO) { files.drafts() }
    }
    fun open(d: Draft) = task { openInternal(d) }
    private suspend fun openInternal(d: Draft) {
        val image = withContext(Dispatchers.IO) { files.render(d,0) }
        endInlineEdit(); draft = d; page = 0; pageImage = image; selected = null
        undo.clear(); redo.clear(); historyState()
    }
    fun close() { endInlineEdit(); draft = null; pageImage = null; selected = null; drafts = files.drafts() }
    fun go(index: Int) {
        val d = draft ?: return
        if(index !in 0 until d.pageCount) return
        task { val image = withContext(Dispatchers.IO) { files.render(d,index) }; page = index; pageImage = image; selected = null }
    }
    fun commit(marks: List<Mark>) {
        endInlineEdit()
        val d = draft ?: return
        if (marks == d.marks) return
        val next = d.copy(marks=marks)
        try {
            files.save(next)
            undo.addLast(d.marks); if(undo.size>50) undo.removeFirst()
            redo.clear(); draft = next; historyState()
        } catch(e: Exception) { error = strings.getString(R.string.draft_not_saved) }
    }
    fun update(m: Mark) { commit(draft!!.marks.map { if(it.id==m.id) m else it }) }
    fun add(m: Mark) { commit(draft!!.marks + m); selected=m.id }
    fun removeSelected() { commit(draft!!.marks.filterNot { it.id==selected }); selected=null }
    fun duplicate() {
        val m = draft?.marks?.find { it.id==selected } ?: return
        val p = pageImage ?: return
        add(m.copy(id=UUID.randomUUID().toString(),x=(m.x+12).coerceAtMost((p.width-m.width).coerceAtLeast(0f)),y=(m.y+12).coerceAtMost((p.height-m.height).coerceAtLeast(0f))))
    }
    fun undo() { travel(undo,redo) }
    fun redo() { travel(redo,undo) }
    private fun travel(from: ArrayDeque<List<Mark>>, to: ArrayDeque<List<Mark>>) {
        endInlineEdit()
        val d = draft ?: return; if(from.isEmpty()) return
        try { val next = d.copy(marks=from.last()); files.save(next); from.removeLast(); to.addLast(d.marks); draft=next; selected=null; historyState() }
        catch(e: Exception) { error=strings.getString(R.string.draft_not_saved) }
    }
    fun delete(d: Draft) { try { files.delete(d); drafts=files.drafts() } catch(e: Exception) { error=e.userMessage(strings) } }
    fun saveSignature(name: String, strokes: List<List<InkPoint>>, aspect: Float) {
        val next=signatures+Signature(UUID.randomUUID().toString(),name.trim().ifEmpty { strings.getString(R.string.signature_number,signatures.size+1) },strokes,aspect)
        try { files.saveSignatures(next); signatures=next } catch(e: Exception) { error=e.userMessage(strings) }
    }
    fun deleteSignature(s: Signature) { try { val next=signatures-s; files.saveSignatures(next); signatures=next } catch(e: Exception) { error=e.userMessage(strings) } }
    fun saveText(entry: SavedText): Boolean {
        val clean=entry.copy(label=entry.label.trim())
        val next=if(savedTexts.any { it.id==entry.id }) savedTexts.map { if(it.id==entry.id) clean else it } else savedTexts+clean
        return try { files.saveTexts(next); savedTexts=next; true } catch(e: Exception) { error=e.userMessage(strings); false }
    }
    fun deleteText(entry: SavedText) {
        try { val next=savedTexts.filterNot { it.id==entry.id }; files.saveTexts(next); savedTexts=next } catch(e: Exception) { error=e.userMessage(strings) }
    }
    fun exportLibrary(uri: Uri) = task {
        val library=LibraryBackup.Library(signatures.toList(),savedTexts.toList())
        withContext(Dispatchers.IO) {
            // Validate before touching the destination document.
            val bytes=java.io.ByteArrayOutputStream().also { LibraryBackup.write(it,library.signatures,library.texts) }.toByteArray()
            (getApplication<Application>().contentResolver.openOutputStream(uri,"wt") ?: throw UserInputException(R.string.cannot_write)).use { it.write(bytes) }
        }
        message=strings.getString(R.string.backup_exported)
    }
    fun importLibrary(uri: Uri) = task {
        val before=LibraryBackup.Library(signatures.toList(),savedTexts.toList())
        val merged=withContext(Dispatchers.IO) {
            val incoming=(getApplication<Application>().contentResolver.openInputStream(uri) ?: throw UserInputException(R.string.invalid_backup)).use(LibraryBackup::read)
            val next=LibraryBackup.merge(before,incoming)
            files.restoreLibrary(next)
            next
        }
        signatures=merged.signatures; savedTexts=merged.texts
        message=strings.getString(R.string.backup_imported,signatures.size-before.signatures.size,savedTexts.size-before.texts.size)
    }
    fun export(pages: List<Int>, done: (File) -> Unit) {
        val d=draft ?: return
        task { val file=withContext(Dispatchers.IO) { files.export(d,pages) }; done(file) }
    }
    fun copyExport(file: File, uri: Uri) = task {
        withContext(Dispatchers.IO) {
            (getApplication<Application>().contentResolver.openOutputStream(uri,"wt") ?: throw UserInputException(R.string.cannot_write)).use { out -> file.inputStream().use { it.copyTo(out) } }
        }
        message=strings.getString(R.string.pdf_saved)
    }
}
