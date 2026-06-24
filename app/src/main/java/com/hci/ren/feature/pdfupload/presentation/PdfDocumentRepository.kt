package com.hci.ren.feature.pdfupload.presentation

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import androidx.core.graphics.createBitmap

class PdfDocumentRepository(
    private val contentResolver: ContentResolver,
) {
    fun loadDocument(uri: Uri): Result<PdfDocumentUiModel> = runCatching {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers grant read access only for the current process.
        }

        val metadata = queryMetadata(uri)
        val pageCount = readPageCount(uri)
        PdfDocumentUiModel(
            uri = uri.toString(),
            fileName = metadata.fileName ?: "Selected PDF",
            sizeBytes = metadata.sizeBytes ?: 0L,
            pageCount = pageCount,
        )
    }

    fun loadDocuments(uris: List<Uri>): Result<List<PdfDocumentUiModel>> = runCatching {
        uris.mapNotNull { uri ->
            runCatching {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) { }
                val metadata = queryMetadata(uri)
                val pageCount = readPageCount(uri)
                PdfDocumentUiModel(
                    uri = uri.toString(),
                    fileName = metadata.fileName ?: "Selected PDF",
                    sizeBytes = metadata.sizeBytes ?: 0L,
                    pageCount = pageCount,
                )
            }.getOrNull()
        }.also { loaded ->
            if (loaded.isEmpty()) throw IOException("Could not open any of the selected PDFs.")
        }
    }

    fun renderPage(
        uri: Uri,
        pageIndex: Int,
        targetWidthPx: Int,
    ): Result<Bitmap> = runCatching {
        contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
            requireNotNull(descriptor) { "PDF is no longer accessible." }
            PdfRenderer(descriptor).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount) {
                    "Page is outside the PDF."
                }
                renderer.openPage(pageIndex).use { page ->
                    val dimensions = boundedRenderDimensions(
                        sourceWidth = page.width,
                        sourceHeight = page.height,
                        targetWidth = targetWidthPx,
                    )
                    createBitmap(dimensions.width, dimensions.height).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }

    private fun queryMetadata(uri: Uri): PdfFileMetadata {
        return contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                PdfFileMetadata()
            } else {
                PdfFileMetadata(
                    fileName = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME),
                    sizeBytes = cursor.getLongOrNull(OpenableColumns.SIZE),
                )
            }
        }
    }

    private fun readPageCount(uri: Uri): Int {
        return contentResolver.openFileDescriptor(uri, "r").use { descriptor ->
            requireNotNull(descriptor) { "PDF is no longer accessible." }
            PdfRenderer(descriptor).use { renderer ->
                renderer.pageCount.takeIf { it > 0 }
                    ?: throw IOException("PDF does not contain any pages.")
            }
        }
    }
}

internal const val MaxPdfRenderDimension = 8_192
internal const val MaxPdfRenderPixels = 4_000_000L

internal data class PdfRenderDimensions(
    val width: Int,
    val height: Int,
)

internal fun boundedRenderDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
): PdfRenderDimensions {
    require(sourceWidth > 0 && sourceHeight > 0) { "PDF page dimensions must be positive." }
    var width = targetWidth.coerceIn(1, MaxPdfRenderDimension)
    var height = (width.toDouble() * sourceHeight / sourceWidth)
        .coerceAtLeast(1.0)

    val dimensionScale = minOf(1.0, MaxPdfRenderDimension / height)
    val pixelScale = minOf(1.0, kotlin.math.sqrt(MaxPdfRenderPixels / (width * height)))
    val scale = minOf(dimensionScale, pixelScale)
    width = (width * scale).toInt().coerceAtLeast(1)
    height = (height * scale).toInt().coerceIn(1, MaxPdfRenderDimension).toDouble()
    return PdfRenderDimensions(width, height.toInt())
}

private data class PdfFileMetadata(
    val fileName: String? = null,
    val sizeBytes: Long? = null,
)

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}

private fun Cursor.getLongOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getLong(index)
}
