package com.nivek.paperwork

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class SavedTextTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()

    @Test fun savedDetailsSurviveReloadAndEditsDoNotChangeInsertedCopies() {
        val vm=EditorModel(app)
        val entry=SavedText(label="Address",text="Jordan García\n123 Example Street\nUnit 4\nExample City")
        assertTrue(vm.saveText(entry))
        val inserted=Mark(text=entry.text)
        val reopened=EditorModel(app)
        assertEquals(listOf(entry),reopened.savedTexts)
        assertTrue(reopened.saveText(entry.copy(text="New address")))
        assertEquals("New address",PdfFiles(app).savedTexts().single().text)
        assertEquals(entry.text,inserted.text)
        reopened.deleteText(entry)
        assertTrue(PdfFiles(app).savedTexts().isEmpty())
        assertEquals(entry.text,inserted.text)
    }

    @Test fun blankDetailsCannotOverwriteTheLibraryAndWhitespaceIsPreserved() {
        val vm=EditorModel(app)
        val entry=SavedText(label="  Reference  ",text="  00123\n  A-B  ")
        assertTrue(vm.saveText(entry))
        assertEquals(entry.copy(label="Reference"),PdfFiles(app).savedTexts().single())
        assertFalse(vm.saveText(entry.copy(text=" \n ")))
        assertFalse(vm.saveText(entry.copy(label=" ")))
        assertEquals(entry.copy(label="Reference"),PdfFiles(app).savedTexts().single())
    }

    @Test fun dateUsesCurrentValueAndDeviceLocale() {
        assertEquals("Jan 2, 2026",currentDateText(LocalDate.of(2026,1,2),Locale.US))
        assertEquals("Jan 3, 2026",currentDateText(LocalDate.of(2026,1,3),Locale.US))
        assertNotEquals(currentDateText(LocalDate.of(2026,1,2),Locale.US),currentDateText(LocalDate.of(2026,1,2),Locale.GERMANY))
    }
}
