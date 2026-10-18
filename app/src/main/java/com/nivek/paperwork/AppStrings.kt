package com.nivek.paperwork

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources

/** Compose resources refresh when the app locale changes. */
@Composable fun localizedResources(): Resources = LocalResources.current

/** Keep model/JSON identifiers stable across translations. */
fun Resources.optionLabel(value: String): String = getString(when(value) {
    "Text" -> R.string.option_text
    "Circle" -> R.string.option_circle
    "Square" -> R.string.option_square
    "Oval" -> R.string.option_oval
    "Check" -> R.string.option_check
    "X" -> R.string.option_x
    "Line" -> R.string.option_line
    "Doodle" -> R.string.option_doodle
    "Signature" -> R.string.option_signature
    "Solid" -> R.string.option_solid
    "Dashed" -> R.string.option_dashed
    "Dotted" -> R.string.option_dotted
    "None" -> R.string.option_none
    "Open" -> R.string.option_open
    "Triangle" -> R.string.option_triangle
    "Diamond" -> R.string.option_diamond
    "Bar" -> R.string.option_bar
    else -> R.string.option_shape
})

/** Validation can stay independent of Android Context and resolve at the UI boundary. */
class UserInputException(@param:StringRes val resource: Int, vararg val arguments: Any): IllegalArgumentException()
fun Throwable.userMessage(resources: Resources): String = when(this) {
    is UserInputException -> resources.getString(resource,*arguments)
    else -> resources.getString(R.string.operation_failed)
}
