package com.hci.ren.feature.pdfupload.presentation

import android.graphics.Bitmap
import java.util.Locale

data class PdfUploadUiState(
    val sessionId: Long = 0,
    val document: PdfDocumentUiModel? = null,
    val selectedPageIndex: Int = 0,
    val loadStatus: PdfLoadStatus = PdfLoadStatus.Idle,
    val renderedPages: Map<PdfRenderKey, PdfPageRenderState> = emptyMap(),
) {
    val canContinue: Boolean
        get() = document != null && loadStatus == PdfLoadStatus.Ready

    val thumbnailPageIndexes: List<Int>
        get() = thumbnailPageIndexes(
            pageCount = document?.pageCount ?: 0,
            selectedPageIndex = selectedPageIndex,
        )
}

data class PdfDocumentUiModel(
    val uri: String,
    val fileName: String,
    val sizeBytes: Long,
    val pageCount: Int,
) {
    val details: String
        get() = "$pageCount ${if (pageCount == 1) "page" else "pages"} • ${formatFileSize(sizeBytes)}"
}

sealed interface PdfLoadStatus {
    data object Idle : PdfLoadStatus
    data object Loading : PdfLoadStatus
    data object Ready : PdfLoadStatus
    data class Error(val message: String) : PdfLoadStatus
}

data class PdfRenderKey(
    val pageIndex: Int,
    val kind: PdfRenderKind,
)

enum class PdfRenderKind {
    Preview,
    Thumbnail,
}

sealed interface PdfPageRenderState {
    data object Loading : PdfPageRenderState
    data class Ready(val bitmap: Bitmap) : PdfPageRenderState
    data class Error(val message: String) : PdfPageRenderState
}

@Suppress("UNUSED_PARAMETER")
fun thumbnailPageIndexes(
    pageCount: Int,
    selectedPageIndex: Int,
): List<Int> = (0 until pageCount).toList()

fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "Unknown size"

    val units = listOf("B", "KB", "MB", "GB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex += 1
    }

    return if (unitIndex == 0) {
        "${sizeBytes} B"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unitIndex])
    }
}

class BoundedPageCache<K, V>(
    private val maxEntries: Int,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive." }
    }

    private val entries = LinkedHashMap<K, V>(maxEntries, 0.75f, true)

    @Synchronized
    fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V): K? {
        entries[key] = value
        if (entries.size <= maxEntries) return null
        val eldestKey = entries.entries.first().key
        entries.remove(eldestKey)
        return eldestKey
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
