package com.nivek.paperwork

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class LibraryBackupTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()
    private val signature=Signature("sig","Jordan García",listOf(listOf(InkPoint(0f,.25f),InkPoint(1f,.75f))),2.5f)
    private val text=SavedText("text","Address","Jordan García\n123 Example Street\n東京")
    private fun bytes(s: List<Signature> = listOf(signature),t: List<SavedText> = listOf(text)) =
        ByteArrayOutputStream().also { LibraryBackup.write(it,s,t) }.toByteArray()

    @Test fun transfersLibrariesBetweenInstallsAndKeepsDrawingGeometry() {
        val backup=bytes()
        val files=PdfFiles(app)
        files.restoreLibrary(LibraryBackup.read(backup.inputStream()))
        assertEquals(listOf(signature),PdfFiles(app).signatures())
        assertEquals(listOf(text),PdfFiles(app).savedTexts())
        assertFalse(File(app.noBackupFilesDir,"library-restore.json").exists())
    }
    @Test fun mergePreservesExistingEntriesAndRepeatedImportsAreIdempotent() {
        val current=LibraryBackup.Library(listOf(signature),listOf(text))
        val changed=LibraryBackup.Library(listOf(signature.copy(name="Initials")),listOf(text.copy(text="New address")))
        val merged=LibraryBackup.merge(current,changed)
        assertEquals(signature,merged.signatures.first())
        assertEquals(text,merged.texts.first())
        assertEquals(2,merged.signatures.map { it.id }.distinct().size)
        assertEquals(2,merged.texts.map { it.id }.distinct().size)
        assertEquals(merged,LibraryBackup.merge(merged,changed))
        assertEquals(current,LibraryBackup.merge(current,LibraryBackup.Library(listOf(signature.copy(id="other")),listOf(text.copy(id="other")))) )
    }
    @Test fun interruptedRestoreRollsBackBothLibrariesOnReopen() {
        val files=PdfFiles(app)
        files.saveSignatures(listOf(signature)); files.saveTexts(listOf(text))
        File(app.noBackupFilesDir,"library-restore.json").writeBytes(bytes())
        files.saveSignatures(listOf(signature.copy(name="Interrupted change")))
        val reopened=PdfFiles(app)
        assertEquals(listOf(signature),reopened.signatures())
        assertEquals(listOf(text),reopened.savedTexts())
        assertFalse(File(app.noBackupFilesDir,"library-restore.json").exists())
    }
    @Test fun rejectsMalformedFutureAndOversizedBackupsWithoutChangingLibraries() {
        val files=PdfFiles(app)
        files.saveSignatures(listOf(signature)); files.saveTexts(listOf(text))
        val valid=String(bytes(),Charsets.UTF_8)
        val cases=listOf("not json", "{}", JSONObject(valid).put("version",2).toString(),
            JSONObject(valid).also { it.getJSONArray("signatures").getJSONObject(0).put("aspect",0) }.toString(),
            JSONObject(valid).also { it.getJSONArray("texts").getJSONObject(0).put("text", " ") }.toString(),
            JSONObject(valid).also { it.getJSONArray("signatures").getJSONObject(0).put("aspect",1e-40) }.toString(),
            " ".repeat(LibraryBackup.MAX_BYTES+1))
        cases.forEach { body ->
            assertTrue(runCatching { LibraryBackup.read(body.byteInputStream()) }.exceptionOrNull() is UserInputException)
            assertEquals(listOf(signature),files.signatures()); assertEquals(listOf(text),files.savedTexts())
        }
    }
    @Test fun rejectsDuplicateIdsAndInvalidInk() {
        val valid=String(bytes(),Charsets.UTF_8)
        val duplicate=JSONObject(valid).also { it.getJSONArray("texts").put(text.json()) }
        val invalid=JSONObject(valid).also { it.getJSONArray("signatures").getJSONObject(0).getJSONArray("strokes").getJSONArray(0).getJSONArray(0).put(0,2.0) }
        listOf(duplicate,invalid).forEach { assertTrue(runCatching { LibraryBackup.read(it.toString().byteInputStream()) }.exceptionOrNull() is UserInputException) }
    }
    @Test fun emptyLibrariesRoundTrip() {
        assertEquals(LibraryBackup.Library(emptyList(),emptyList()),LibraryBackup.read(bytes(emptyList(),emptyList()).inputStream()))
    }
}
