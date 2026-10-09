package com.nivek.paperwork

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

/** Robolectric has no PDFium renderer. Only the blank-page UI fixture uses this stand-in.
 * PDF parsing, painting, extraction, and export are tested without these shadows. */
@Implements(PdfRenderer::class)
class PreviewRendererShadow {
    @Implementation fun __constructor__(fd: ParcelFileDescriptor) {}
    @Implementation fun openPage(index: Int): PdfRenderer.Page = Shadow.newInstanceOf(PdfRenderer.Page::class.java)
    @Implementation fun getPageCount(): Int = 1
    @Implementation fun close() {}
}
@Implements(PdfRenderer.Page::class)
class PreviewPageShadow {
    @Implementation fun getWidth(): Int = 612
    @Implementation fun getHeight(): Int = 792
    @Implementation fun render(bitmap: Bitmap, clip: Rect?, transform: Matrix?, mode: Int) { bitmap.eraseColor(Color.WHITE) }
    @Implementation fun close() {}
}
