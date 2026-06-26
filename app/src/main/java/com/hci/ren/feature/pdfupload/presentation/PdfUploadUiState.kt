package com.hci.ren.feature.pdfupload.presentation

import android.graphics.Bitmap
import java.util.Locale

data class DocumentGroup(
    val documents: List<PdfDocumentUiModel>,
    val selectedPdfIndex: Int = 0,
)

data class PdfUploadUiState(
    val sessionId: Long = 0,
    val documentGroup: DocumentGroup? = null,
    val selectedPageIndex: Int = 0,
    val loadStatus: PdfLoadStatus = PdfLoadStatus.Idle,
    val noticeMessage: String? = null,
    val renderedPages: Map<PdfRenderKey, PdfPageRenderState> = emptyMap(),
) {
    val canContinue: Boolean
        get() = documentGroup != null && documentGroup.documents.isNotEmpty() && loadStatus == PdfLoadStatus.Ready

    val selectedDocumentCount: Int
        get() = documentGroup?.documents?.size ?: 0

    val remainingDocumentSlots: Int
        get() = (MaxPdfDocumentCount - selectedDocumentCount).coerceAtLeast(0)

    val thumbnailPageIndexes: List<Int>
        get() {
            val doc = documentGroup?.let { g ->
                g.documents.getOrNull(g.selectedPdfIndex)
            }
            return thumbnailPageIndexes(pageCount = doc?.pageCount ?: 0)
        }
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

data class DuplicateFilterResult<T>(
    val uniqueItems: List<T>,
    val duplicateCount: Int,
)

fun <T> filterDuplicateDocuments(
    candidates: List<T>,
    existingKeys: Set<String> = emptySet(),
    keyOf: (T) -> String,
): DuplicateFilterResult<T> {
    val seen = existingKeys.toMutableSet()
    val uniqueItems = mutableListOf<T>()
    var duplicateCount = 0

    candidates.forEach { item ->
        val key = keyOf(item)
        if (seen.add(key)) {
            uniqueItems += item
        } else {
            duplicateCount += 1
        }
    }

    return DuplicateFilterResult(uniqueItems = uniqueItems, duplicateCount = duplicateCount)
}

fun PdfDocumentUiModel.identityKey(): String =
    "${fileName.trim().lowercase(Locale.US)}|$sizeBytes|$pageCount"

sealed interface PdfLoadStatus {
    data object Idle : PdfLoadStatus
    data object Loading : PdfLoadStatus
    data object Ready : PdfLoadStatus
    data class Error(val message: String) : PdfLoadStatus
}

data class PdfRenderKey(
    val documentIndex: Int,
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

fun thumbnailPageIndexes(
    pageCount: Int,
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
    private val maxWeight: Long,
    private val weightOf: (V) -> Long,
) {
    constructor(maxEntries: Int) : this(maxEntries.toLong(), { 1L })

    init {
        require(maxWeight > 0) { "maxWeight must be positive." }
    }

    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private var storedWeight = 0L

    @get:Synchronized
    val currentWeight: Long
        get() = storedWeight

    @Synchronized
    fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V): List<K> {
        val valueWeight = weightOf(value)
        require(valueWeight in 0..maxWeight) { "Entry exceeds cache weight limit." }
        entries.remove(key)?.let { storedWeight -= weightOf(it) }
        entries[key] = value
        storedWeight += valueWeight
        val evictedKeys = mutableListOf<K>()
        while (storedWeight > maxWeight) {
            val eldest = entries.entries.first()
            entries.remove(eldest.key)
            storedWeight -= weightOf(eldest.value)
            evictedKeys += eldest.key
        }
        return evictedKeys
    }

    @Synchronized
    fun removeIf(predicate: (K) -> Boolean) {
        val it = entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (predicate(entry.key)) {
                storedWeight -= weightOf(entry.value)
                it.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        storedWeight = 0
    }
}

const val MaxPdfUploadBytes = 25L * 1024 * 1024
const val MaxPdfDocumentCount = 10

fun isUploadSizeAllowed(sizeBytes: Long): Boolean =
    sizeBytes <= 0L || sizeBytes <= MaxPdfUploadBytes

fun restoredPageIndex(savedIndex: Int, pageCount: Int): Int =
    if (pageCount <= 0) 0 else savedIndex.coerceIn(0, pageCount - 1)
