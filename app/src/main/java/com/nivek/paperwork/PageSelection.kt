package com.nivek.paperwork

/** Returns zero-based pages in the requested order. Refuses duplicates and invalid ranges. */
fun parsePages(input: String, count: Int): List<Int> {
    require(count > 0) { throw UserInputException(R.string.document_no_pages) }
    if (input.isBlank()) return (0 until count).toList()
    val pages = mutableListOf<Int>()
    input.split(',').forEach { token ->
        val s = token.trim()
        require(s.matches(Regex("[0-9]+(?:\\s*-\\s*[0-9]+)?"))) { throw UserInputException(R.string.page_syntax) }
        val ends = s.split('-').map { it.trim().toIntOrNull() ?: throw UserInputException(R.string.page_too_large) }
        val start = ends.first(); val end = ends.last()
        require(start in 1..count && end in 1..count) { throw UserInputException(R.string.page_bounds,count) }
        require(end >= start) { throw UserInputException(R.string.ascending_pages) }
        (start..end).forEach { require(it - 1 !in pages) { throw UserInputException(R.string.duplicate_page,it) }; pages.add(it - 1) }
    }
    return pages
}
/** Maps the upright page's bottom-left coordinate system into the PDF crop box. */
fun pageTransform(rotation: Int, x: Float, y: Float, w: Float, h: Float): FloatArray = when ((rotation % 360 + 360) % 360) {
    90 -> floatArrayOf(0f, 1f, -1f, 0f, x + w, y)
    180 -> floatArrayOf(-1f, 0f, 0f, -1f, x + w, y + h)
    270 -> floatArrayOf(0f, -1f, 1f, 0f, x, y + h)
    else -> floatArrayOf(1f, 0f, 0f, 1f, x, y)
}
