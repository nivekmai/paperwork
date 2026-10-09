package com.nivek.paperwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.AtomicFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt

data class PageImage(val bitmap: Bitmap, val width: Float, val height: Float)
class PdfFiles(private val context: Context) {
    private val root = File(context.filesDir,"documents").apply { mkdirs() }
    private val signaturesFile = File(context.filesDir,"signatures.json")
    fun source(d: Draft) = File(root,"${d.id}/source.pdf")
    fun drafts(): List<Draft> = root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
        runCatching { Draft.from(JSONObject(AtomicFile(File(dir,"draft.json")).openRead().bufferedReader().use { it.readText() })) }.getOrNull()
    }.sortedByDescending { File(root,"${it.id}/draft.json").lastModified() }
    private fun atomic(file: File, text: String) {
        val a = AtomicFile(file); val stream = a.startWrite()
        try { stream.write(text.toByteArray(Charsets.UTF_8)); a.finishWrite(stream) }
        catch (e: Exception) { a.failWrite(stream); throw e }
    }
    fun save(d: Draft) { atomic(File(root,"${d.id}/draft.json"),d.json().toString()) }
    fun delete(d: Draft) { check(File(root,d.id).deleteRecursively()) { "Could not delete draft." } }
    fun import(uri: Uri): Draft {
        val id = UUID.randomUUID().toString(); val dir = File(root,id).apply { mkdirs() }
        try {
            val name = context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "Document.pdf"
            val file = File(dir,"source.pdf")
            context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Cannot open this PDF." }; file.outputStream().use { input.copyTo(it) } }
            val count = PDDocument.load(file, MemoryUsageSetting.setupMixed(32L*1024*1024).setTempDir(context.cacheDir)).use { doc ->
                require(doc.numberOfPages > 0) { "This PDF has no pages." }
                require(!doc.isEncrypted) { "Password-protected PDFs are not supported yet. Import an unlocked copy." }
                require(doc.currentAccessPermission.canModify()) { "This PDF does not permit editing." }
                doc.numberOfPages
            }
            val draft = Draft(id,name,count); save(draft); return draft
        } catch(e: Exception) { dir.deleteRecursively(); throw e }
    }
    fun render(d: Draft, index: Int): PageImage {
        ParcelFileDescriptor.open(source(d),ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer -> renderer.openPage(index).use { page ->
                val scale = minOf(2f,1800f / maxOf(page.width,page.height))
                val b = Bitmap.createBitmap((page.width*scale).roundToInt().coerceAtLeast(1),(page.height*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                b.eraseColor(android.graphics.Color.WHITE)
                page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return PageImage(b,page.width.toFloat(),page.height.toFloat())
            } }
        }
    }
    fun signatures(): List<Signature> {
        if (!signaturesFile.exists()) return emptyList()
        val a = JSONArray(AtomicFile(signaturesFile).openRead().bufferedReader().use { it.readText() })
        return (0 until a.length()).map { val j = a.getJSONObject(it); Signature(j.getString("id"),j.getString("name"),readInk(j.getJSONArray("strokes")),j.getDouble("aspect").toFloat()) }
    }
    fun saveSignatures(s: List<Signature>) { atomic(signaturesFile,JSONArray().also { a -> s.forEach { a.put(it.json()) } }.toString()) }
    fun export(d: Draft, pages: List<Int>): File {
        val dir = File(context.cacheDir,"exports").apply { mkdirs() }
        val out = File(dir,"Paperwork-${UUID.randomUUID()}.pdf")
        try {
            PDDocument.load(source(d), MemoryUsageSetting.setupMixed(32L*1024*1024).setTempDir(context.cacheDir)).use { doc ->
                // Freeze existing field appearances before appending new visible marks.
                doc.documentCatalog.acroForm?.flatten()
                pages.forEach { index ->
                    val page = doc.getPage(index); val marks = d.marks.filter { it.page == index }
                    if (marks.isNotEmpty()) {
                        val box = page.cropBox; val quarterTurn = ((page.rotation % 180)+180)%180 == 90
                        val w = if(quarterTurn) box.height else box.width; val h = if(quarterTurn) box.width else box.height
                        val scale = minOf(3f,3000f/maxOf(w,h))
                        val bitmap = Bitmap.createBitmap((w*scale).roundToInt().coerceAtLeast(1),(h*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap); canvas.scale(bitmap.width/w,bitmap.height/h)
                            marks.forEach { MarkPainter.draw(canvas,it) }
                            val image = LosslessFactory.createFromImage(doc,bitmap)
                            PDPageContentStream(doc,page,PDPageContentStream.AppendMode.APPEND,true,true).use { stream ->
                                val m = pageTransform(page.rotation,box.lowerLeftX,box.lowerLeftY,box.width,box.height)
                                stream.transform(Matrix(m[0],m[1],m[2],m[3],m[4],m[5])); stream.drawImage(image,0f,0f,w,h)
                            }
                        } finally { bitmap.recycle() }
                    }
                }
                // Reuse the same document so resources, crop boxes, and rotations remain intact.
                val selected = pages.map { index -> doc.getPage(index).also { page ->
                    // Materialize inheritable entries before detaching from the source page tree.
                    page.resources = page.resources
                    page.mediaBox = page.mediaBox
                    page.cropBox = page.cropBox
                    page.rotation = page.rotation
                } }
                while(doc.numberOfPages > 0) doc.removePage(0)
                selected.forEach { doc.addPage(it) }
                doc.documentCatalog.documentOutline = null
                doc.save(out)
            }
            return out
        } catch(e: Exception) { out.delete(); throw e }
    }
}
