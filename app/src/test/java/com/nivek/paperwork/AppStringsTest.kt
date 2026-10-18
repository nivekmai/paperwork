package com.nivek.paperwork

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class AppStringsTest {
    private val app=ApplicationProvider.getApplicationContext<Application>()
    @Test fun localizedFormattingDoesNotChangeStoredShapeIdentifiers() {
        val config=Configuration(app.resources.configuration).apply { setLocale(Locale.FRANCE) }
        val resources=app.createConfigurationContext(config).resources
        assertEquals("Stroke width: 1,5 pt",resources.getString(R.string.stroke_width_value,1.5f))
        val mark=Mark(kind="Square",strokeStyle="Dashed",endArrow="Triangle")
        assertEquals("Square",resources.optionLabel(mark.kind))
        assertEquals(mark,Mark.from(mark.json()))
        arrowheadStyles.forEach { assertTrue(resources.optionLabel(it).isNotBlank()) }
    }
    @Test fun validationErrorsResolveThroughResources() {
        val error=runCatching { parsePages("1,1",2) }.exceptionOrNull()!!
        assertEquals(app.getString(R.string.duplicate_page,1),error.userMessage(app.resources))
        assertEquals(app.getString(R.string.operation_failed),RuntimeException("Untranslated library detail").userMessage(app.resources))
    }
}
