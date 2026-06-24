package com.hci.ren.feature.pdfupload.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfUploadUiStateTest {
    @Test
    fun thumbnailPageIndexesIncludeSelectedPage() {
        assertEquals(
            listOf(0, 1, 2, 3),
            thumbnailPageIndexes(pageCount = 4),
        )
    }

    @Test
    fun thumbnailPageIndexesIncludeSinglePageDocument() {
        assertEquals(
            listOf(0),
            thumbnailPageIndexes(pageCount = 1),
        )
    }

    @Test
    fun continueIsEnabledOnlyWhenDocumentIsReady() {
        assertFalse(PdfUploadUiState().canContinue)

        assertTrue(
            PdfUploadUiState(
                document = PdfDocumentUiModel(
                    uri = "content://ren/document",
                    fileName = "Lecture.pdf",
                    sizeBytes = 1_536,
                    pageCount = 12,
                ),
                loadStatus = PdfLoadStatus.Ready,
            ).canContinue,
        )

        assertFalse(
            PdfUploadUiState(
                document = PdfDocumentUiModel(
                    uri = "content://ren/document",
                    fileName = "Lecture.pdf",
                    sizeBytes = 1_536,
                    pageCount = 12,
                ),
                loadStatus = PdfLoadStatus.Error("Could not open this PDF."),
            ).canContinue,
        )
    }

    @Test
    fun formattedDocumentDetailsIncludePagesAndSize() {
        val document = PdfDocumentUiModel(
            uri = "content://ren/document",
            fileName = "Lecture.pdf",
            sizeBytes = 1_572_864,
            pageCount = 8,
        )

        assertEquals("8 pages • 1.5 MB", document.details)
    }

    @Test
    fun boundedCacheEvictsLeastRecentlyUsedEntry() {
        val cache = BoundedPageCache<Int, String>(maxEntries = 2)

        cache.put(1, "one")
        cache.put(2, "two")
        assertEquals("one", cache.get(1))
        assertEquals(listOf(2), cache.put(3, "three"))

        assertEquals("one", cache.get(1))
        assertNull(cache.get(2))
        assertEquals("three", cache.get(3))
    }

    @Test
    fun boundedCacheClearRemovesAllEntries() {
        val cache = BoundedPageCache<Int, String>(maxEntries = 2)
        cache.put(1, "one")
        cache.put(2, "two")

        cache.clear()

        assertNull(cache.get(1))
        assertNull(cache.get(2))
    }

    @Test
    fun boundedCacheReportsEvictedEntry() {
        val cache = BoundedPageCache<Int, String>(maxEntries = 2)
        cache.put(1, "one")
        cache.put(2, "two")

        assertEquals(listOf(1), cache.put(3, "three"))
    }

    @Test
    fun renderDimensionsBoundExtremeAspectRatiosAndPixelCount() {
        val tall = boundedRenderDimensions(
            sourceWidth = 1,
            sourceHeight = 100_000,
            targetWidth = 1_400,
        )

        assertTrue(tall.width >= 1)
        assertTrue(tall.height >= 1)
        assertTrue(tall.width.toLong() * tall.height <= MaxPdfRenderPixels)
        assertTrue(tall.height <= MaxPdfRenderDimension)
    }

    @Test
    fun weightedCacheNeverExceedsByteBudget() {
        val cache = BoundedPageCache<Int, Int>(maxWeight = 100, weightOf = Int::toLong)

        cache.put(1, 60)
        val evicted = cache.put(2, 60)

        assertEquals(listOf(1), evicted)
        assertTrue(cache.currentWeight <= 100)
    }

    @Test
    fun androidUploadLimitMatchesBackendLimit() {
        assertTrue(isUploadSizeAllowed(MaxPdfUploadBytes))
        assertFalse(isUploadSizeAllowed(MaxPdfUploadBytes + 1))
        assertTrue(isUploadSizeAllowed(0))
    }

    @Test
    fun restoredPageIsClampedToCurrentDocument() {
        assertEquals(0, restoredPageIndex(-1, pageCount = 5))
        assertEquals(3, restoredPageIndex(3, pageCount = 5))
        assertEquals(4, restoredPageIndex(99, pageCount = 5))
        assertEquals(0, restoredPageIndex(2, pageCount = 0))
    }
}
