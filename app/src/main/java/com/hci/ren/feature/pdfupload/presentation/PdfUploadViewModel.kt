package com.hci.ren.feature.pdfupload.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfUploadViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val repository = PdfDocumentRepository(application.contentResolver)
    private val pageCache = BoundedPageCache<PdfRenderKey, android.graphics.Bitmap>(
        maxEntries = 12,
    )
    private val loadingPages = mutableSetOf<PdfRenderKey>()
    private var documentLoadGeneration = 0L
    private var sessionId = 0L

    private val _uiState = MutableStateFlow(PdfUploadUiState())
    val uiState: StateFlow<PdfUploadUiState> = _uiState.asStateFlow()

    fun selectDocument(uri: Uri) {
        val loadGeneration = ++documentLoadGeneration
        pageCache.clear()
        loadingPages.clear()
        _uiState.value = PdfUploadUiState(sessionId = sessionId, loadStatus = PdfLoadStatus.Loading)

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.loadDocument(uri)
            }

            if (loadGeneration != documentLoadGeneration) return@launch

            result
                .onSuccess { document ->
                    _uiState.value = PdfUploadUiState(
                        sessionId = sessionId,
                        document = document,
                        selectedPageIndex = 0,
                        loadStatus = PdfLoadStatus.Ready,
                    )
                    requestPage(PdfRenderKey(0, PdfRenderKind.Preview), previewWidthPx)
                }
                .onFailure {
                    _uiState.value = PdfUploadUiState(
                        sessionId = sessionId,
                        loadStatus = PdfLoadStatus.Error(
                            "This PDF could not be opened. It may be protected, corrupted, or no longer accessible.",
                        ),
                    )
                }
        }
    }

    fun selectPage(pageIndex: Int) {
        val document = _uiState.value.document ?: return
        if (pageIndex !in 0 until document.pageCount) return

        _uiState.update { state ->
            state.copy(selectedPageIndex = pageIndex)
        }
        requestPage(PdfRenderKey(pageIndex, PdfRenderKind.Preview), previewWidthPx)
    }

    fun requestPage(
        key: PdfRenderKey,
        targetWidthPx: Int,
    ) {
        val document = _uiState.value.document ?: return
        val loadGeneration = documentLoadGeneration
        if (key.pageIndex !in 0 until document.pageCount) return

        pageCache.get(key)?.let { bitmap ->
            _uiState.update { state ->
                state.copy(
                    renderedPages = state.renderedPages + (key to PdfPageRenderState.Ready(bitmap)),
                )
            }
            return
        }

        if (!loadingPages.add(key)) return
        _uiState.update { state ->
            state.copy(renderedPages = state.renderedPages + (key to PdfPageRenderState.Loading))
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.renderPage(
                    uri = Uri.parse(document.uri),
                    pageIndex = key.pageIndex,
                    targetWidthPx = targetWidthPx,
                )
            }

            if (loadGeneration != documentLoadGeneration || _uiState.value.document?.uri != document.uri) {
                return@launch
            }

            loadingPages.remove(key)
            result
                .onSuccess { bitmap ->
                    val evictedKey = pageCache.put(key, bitmap)
                    _uiState.update { state ->
                        state.copy(
                            renderedPages = (state.renderedPages - listOfNotNull(evictedKey)) +
                                (key to PdfPageRenderState.Ready(bitmap)),
                        )
                    }
                }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            renderedPages = state.renderedPages + (
                                key to PdfPageRenderState.Error("Page could not be previewed.")
                                ),
                        )
                    }
                }
        }
    }

    fun documentReference(): String? = _uiState.value.document?.uri

    fun beginNewSession() {
        documentLoadGeneration += 1
        sessionId += 1
        pageCache.clear()
        loadingPages.clear()
        _uiState.value = PdfUploadUiState(sessionId = sessionId)
    }

    private companion object {
        const val previewWidthPx = 1400
    }
}
