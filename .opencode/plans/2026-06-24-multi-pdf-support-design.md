# Multi-PDF Support — Design Spec

## 1. Overview

Allow a user to select **multiple PDF files** at once in the upload screen and generate a **single combined study plan** covering all of them. The backend receives all PDFs, merges their content in the AI context, and produces one unified plan with topics and blocks across all documents.

## 2. Constraints & Assumptions

- Maximum **10 PDFs** per session. Enforced client-side in the ViewModel with a user-facing error message.
- Each individual PDF still subject to existing 25 MB limit.
- All PDFs processed through a **single AI call** — Gemini receives multiple `Part.from_bytes()`.
- Plan setup wizard (goal, deadline, study days) **unchanged** — applies to combined material.
- **Android and backend are upgraded atomically** — no backward-compat shim on `POST /plans`. The old `documentId` field is replaced by `documentIds` in a single deploy.

## 3. Architecture

### 3.1 Data Model Changes

#### PlanSetupSubmission (Android)

```kotlin
data class PlanSetupSubmission(
    val documentUris: List<String>,
    val goal: StudyGoal,
    val deadline: StudyDeadline,
    val deadlineDate: String?,
    val dailyStudyMinutes: Int,
    val studyDays: Set<StudyDay>,
)
```

#### CreatePlanRequest (Backend)

```python
class CreatePlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    documentIds: list[str] = Field(min_length=1, max_length=10)
    requestId: str
    setup: Setup
```

#### Store — plans table

```sql
-- Column: document_ids TEXT  (JSON array of UUIDs)
-- Legacy column kept: document_id TEXT (set to documentIds[0] for backward compat)
```

### 3.2 DocumentGroup (Android)

```kotlin
data class DocumentGroup(
    val documents: List<PdfDocumentUiModel>,
    val selectedPdfIndex: Int = 0,
)
```

`PdfUploadUiState.document: PdfDocumentUiModel?` → `documentGroup: DocumentGroup?`.

`PdfRenderKey` gains `documentIndex: Int` as first field:

```kotlin
data class PdfRenderKey(
    val documentIndex: Int,
    val pageIndex: Int,
    val kind: PdfRenderKind,
)
```

### 3.3 Backend PlanRow Named Tuple

Replace positional index access with a named tuple:

```python
from collections import namedtuple
PlanRow = namedtuple("PlanRow", ["document_ids", "setup_json", "status", "result_json", "error"])

def get(self, plan_id: str) -> PlanRow | None:
    with self.connect() as db:
        row = db.execute(
            "SELECT document_ids, setup_json, status, result_json, error FROM plans WHERE id=?",
            (plan_id,)
        ).fetchone()
    return PlanRow(*row) if row else None
```

Every caller in `main.py` uses `row.document_ids` and parses with `json.loads()`.

## 4. Android Changes — Detailed

### 4.1 PdfUploadUiState.kt

```kotlin
data class PdfUploadUiState(
    val sessionId: Long = 0,
    val documentGroup: DocumentGroup? = null,
    val selectedPageIndex: Int = 0,
    val loadStatus: PdfLoadStatus = PdfLoadStatus.Idle,
    val renderedPages: Map<PdfRenderKey, PdfPageRenderState> = emptyMap(),
) {
    val canContinue: Boolean
        get() = documentGroup != null &&
                documentGroup.documents.isNotEmpty() &&
                loadStatus == PdfLoadStatus.Ready

    val thumbnailPageIndexes: List<Int>
        get() {
            val doc = documentGroup?.let { g ->
                g.documents.getOrNull(g.selectedPdfIndex)
            }
            return thumbnailPageIndexes(pageCount = doc?.pageCount ?: 0)
        }
}

data class DocumentGroup(
    val documents: List<PdfDocumentUiModel>,
    val selectedPdfIndex: Int = 0,
)
```

`PdfRenderKey.documentIndex` added as first field.

### 4.2 PdfDocumentRepository.kt

New batch-load method (includes `takePersistableUriPermission` per document):

```kotlin
fun loadDocuments(uris: List<Uri>): Result<List<PdfDocumentUiModel>> = runCatching {
    uris.map { uri ->
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
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
    }
}
```

Each document load is wrapped in try/catch. On failure, `runCatching` returns a `Result.failure` with the exception. The ViewModel handles this by rejecting the entire batch and displaying which file(s) failed.

### 4.3 PdfUploadViewModel.kt

**Key methods:**

| Method | Purpose |
|---|---|
| `selectDocuments(uris: List<Uri>)` | Loads all PDFs, validates each ≤25 MB, creates DocumentGroup, renders first page of first PDF. If `uris.size > MAX_DOCUMENTS`, sets error state immediately. Increments `documentLoadGeneration`. |
| `appendDocuments(uris: List<Uri>)` | Called by "Add More". Appends to existing group. Caps total to `MAX_DOCUMENTS`. On failure, only new docs rejected — existing ones preserved. Increments `documentLoadGeneration`. |
| `removeDocument(index: Int)` | Removes doc from group. Adjusts `selectedPdfIndex`: if removed index < selectedPdfIndex, decrement; if == selectedPdfIndex, clamp to last valid (0 if empty). Filters `renderedPages` entries with matching `documentIndex`. Calls `pageCache.removeIf { it.documentIndex == index }`. Increments `documentLoadGeneration`. |
| `selectPdf(documentIndex: Int)` | Switches preview PDF. Persists `selectedPdfIndex` to SavedStateHandle. Loads first page of that PDF. |
| `documentReferences(): List<String>` | Returns all URIs. |
| `restoreDocumentIfNeeded()` | Reconstructs URI list from `SavedStateHandle` (pipe-delimited `KEY_DOCUMENT_URI_LIST`). Restores `selectedPdfIndex` from `KEY_SELECTED_PDF_INDEX`. Reloads all documents. |
| `beginNewSession()` | Removes `KEY_DOCUMENT_URI_LIST`, `KEY_SELECTED_PDF_INDEX`, `KEY_SELECTED_PAGE` from SavedStateHandle. Increments session. Resets state. |

**Stale guard in requestPage**: Use documentLoadGeneration only. No URI comparison.

```kotlin
fun requestPage(key: PdfRenderKey, targetWidthPx: Int) {
    val group = _uiState.value.documentGroup ?: return
    val doc = group.documents.getOrNull(key.documentIndex) ?: return
    val loadGeneration = documentLoadGeneration
    if (key.pageIndex !in 0 until doc.pageCount) return

    pageCache.get(key)?.let { bitmap ->
        _uiState.update { state ->
            state.copy(renderedPages = state.renderedPages + (key to PdfPageRenderState.Ready(bitmap)))
        }
        return
    }

    if (!loadingPages.add(key)) return
    _uiState.update { state ->
        state.copy(renderedPages = state.renderedPages + (key to PdfPageRenderState.Loading))
    }

    viewModelScope.launch {
        val result = renderSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                repository.renderPage(uri = doc.uri.toUri(), pageIndex = key.pageIndex, targetWidthPx = targetWidthPx)
            }
        }
        if (loadGeneration != documentLoadGeneration) return@launch
        // ... rest unchanged
    }
}
```

**SavedStateHandle keys:**

```kotlin
const val KEY_DOCUMENT_URI_LIST = "pdf_document_uri_list"
const val KEY_SELECTED_PDF_INDEX = "pdf_selected_pdf_index"
```

**Client-side 10-document limit:**

```kotlin
private const val MAX_DOCUMENTS = 10

fun selectDocuments(uris: List<Uri>) {
    if (uris.size > MAX_DOCUMENTS) {
        _uiState.value = PdfUploadUiState(
            sessionId = sessionId,
            loadStatus = PdfLoadStatus.Error(
                getApplication<Application>().getString(R.string.too_many_pdfs, MAX_DOCUMENTS)
            )
        )
        return
    }
    // ...
}

fun appendDocuments(uris: List<Uri>) {
    val currentCount = _uiState.value.documentGroup?.documents?.size ?: 0
    if (currentCount + uris.size > MAX_DOCUMENTS) {
        // Show error
        return
    }
    // ...
}
```

### 4.4 PdfUploadRoute.kt

Two separate launchers to disambiguate initial vs Add More:

```kotlin
val initialLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
    onResult = { uris ->
        if (uris.isNotEmpty()) {
            viewModel.selectDocuments(uris)
        }
    },
)

val addMoreLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
    onResult = { uris ->
        if (uris.isNotEmpty()) {
            viewModel.appendDocuments(uris)
        }
    },
)
```

LaunchedEffect for auto-open:
```kotlin
LaunchedEffect(openPickerOnStart, state.sessionId) {
    if (openPickerOnStart && pickerHandledSessionId != state.sessionId) {
        pickerHandledSessionId = state.sessionId
        initialLauncher.launch(arrayOf("application/pdf"))
    }
}
```

`onContinue: (List<String>) -> Unit`. Route passes `onAddMorePdf = { addMoreLauncher.launch(arrayOf("application/pdf")) }` to the Screen.

### 4.5 PdfUploadScreen.kt

**New parameter:** `onAddMorePdf: () -> Unit`.

**File list** replaces single file card. `PdfFileCard` extracted as internal composable in `components/`:

```kotlin
@Composable
fun PdfFileCard(
    document: PdfDocumentUiModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
)
```

List shows all documents. Selected highlighted. Tap switches preview. X removes. "Add More" button at bottom.

**PdfRenderKey constructions** must include `documentIndex`:
```kotlin
val currentDocIndex = state.documentGroup?.selectedPdfIndex ?: 0
PdfRenderKey(currentDocIndex, pageIndex, PdfRenderKind.Preview)
PdfRenderKey(currentDocIndex, pageIndex, PdfRenderKind.Thumbnail)
```

Page preview and thumbnail sidebar show selected PDF's pages only.

### 4.6 PlanSetupRoute.kt, PlanSetupUiState.kt, PlanSetupViewModel.kt

- `documentUri` → `documentUris: List<String>` throughout.
- `PlanSetupViewModel` SavedStateHandle: new `KEY_DOCUMENT_URI_LIST` constant. `persistState` writes pipe-delimited: `savedStateHandle[KEY_DOCUMENT_URI_LIST] = state.documentUris.joinToString("|")`. `restoreState` splits by `"|"`. `beginNewSession` accepts `List<String>`.
- `PlanSetupRoute.onContinue: (List<String>) -> Unit`.
- Wizard steps **unchanged**.

### 4.7 PlanApiRepository.kt

**uploadDocuments** — sequential per-document upload with per-doc sub-request-ID:

```kotlin
fun uploadDocuments(uris: List<Uri>, requestId: String): List<String> {
    return uris.mapIndexed { index, uri ->
        uploadDocument(uri, "${requestId}-${index}")
    }
}
```

If upload A succeeds but B fails, exception propagates. **Idempotency note**: On retry with the same requestId (UploadOrCreate/Polling failures), already-uploaded docs return existing IDs. On retry with a new requestId (BackendTerminal), all docs are re-uploaded.

**createPlan** accepts `List<String>`:
```kotlin
val body = JSONObject()
    .put("documentIds", JSONArray(documentIds))
    .put("requestId", requestId)
    .put("setup", setup)
```

### 4.8 PlanGenerationViewModel.kt

**`reset()`** — update recovery block to use multi-document API:
```kotlin
} else if (recoveryRequest != null) {
    val (recoverySubmission, recoveryRequestId) = recoveryRequest
    viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val documentIds = repository.uploadDocuments(
                recoverySubmission.documentUris.map { it.toUri() },
                recoveryRequestId,
            )
            val recoveredPlanId = repository.createPlan(
                documentIds, recoverySubmission, recoveryRequestId,
            )
            repository.cancelPlan(recoveredPlanId)
        }
    }
}
```

**`resolveProjectName()`** — use first URI:
```kotlin
private fun resolveProjectName(): String {
    val fallback = getApplication<Application>().getString(R.string.study_plan_default)
    val firstUri = submission?.documentUris?.firstOrNull()?.toUri() ?: return fallback
    return runCatching {
        getApplication<Application>().contentResolver.query(firstUri, ...)
    }.getOrDefault(fallback)
}
```

**`startPersistedRequest()`** — upload loop:
```kotlin
val documentIds = repository.uploadDocuments(
    value.documentUris.map { it.toUri() },
    persistedRequestId,
)
repository.createPlan(documentIds, value, persistedRequestId)
```

**SharedPreferences migration** in `restoreSubmission()`:
```kotlin
private fun restoreSubmission(): PlanSetupSubmission? = runCatching {
    val json = JSONObject(preferences.getString(KEY_SUBMISSION, null) ?: return null)
    val documentUris = if (json.has("documentUris")) {
        val arr = json.getJSONArray("documentUris")
        buildList { repeat(arr.length()) { add(arr.getString(it)) } }
    } else {
        val single = json.optString("documentUri", null) ?: return null
        json.put("documentUris", JSONArray(listOf(single)))
        json.remove("documentUri")
        preferences.edit { putString(KEY_SUBMISSION, json.toString()) }
        listOf(single)
    }
    PlanSetupSubmission(documentUris = documentUris, ...)
}
```

**`persistRequest`** — serialize `documentUris`:
```kotlin
.put("documentUris", JSONArray(value.documentUris))
```

### 4.9 StudyProjectRepository.kt

This file is NOT in the feature package but uses `PlanSetupSubmission` directly. Two places break:

1. `toSubmission()` (JSONObject extension) constructs `PlanSetupSubmission(documentUri = "")`. Change to `PlanSetupSubmission(documentUris = emptyList())`.

2. `newStudyProject()` in tests/persistence calls `.copy(documentUri = "")`. Change to `.copy(documentUris = emptyList())`.

No other behavioral change here — this repository only creates dummy/sentinel submission objects for storage purposes.

### 4.10 BoundedPageCache — add removeIf

```kotlin
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
```

### 4.11 MainActivity.kt

```kotlin
var setupDocumentUris by rememberSaveable { mutableStateOf("") }

onContinue = { documentUris ->
    setupDocumentUris = documentUris.joinToString("|")
    planSetupViewModel.beginNewSession(documentUris)
}

PlanSetupRoute(
    documentUris = setupDocumentUris.split("|").filter { it.isNotEmpty() },
    ...
)
```

## 5. Backend Changes — Detailed

### 5.1 models.py

```python
class CreatePlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    documentIds: list[str] = Field(min_length=1, max_length=10)
    requestId: str
    setup: Setup
```

### 5.2 store.py

**Schema migration guarded by PRAGMA:**
```python
plan_columns = {row[1] for row in db.execute("PRAGMA table_info(plans)")}
if "document_ids" not in plan_columns:
    db.execute("ALTER TABLE plans ADD COLUMN document_ids TEXT")
    db.execute("UPDATE plans SET document_ids = json_array(document_id) WHERE document_ids IS NULL")
```

**PlanRow:**
```python
from collections import namedtuple
PlanRow = namedtuple("PlanRow", ["document_ids", "setup_json", "status", "result_json", "error"])
```

**`get` returns PlanRow:**
```python
def get(self, plan_id: str) -> PlanRow | None:
    with self.connect() as db:
        row = db.execute(
            "SELECT document_ids, setup_json, status, result_json, error FROM plans WHERE id=?",
            (plan_id,)
        ).fetchone()
    return PlanRow(*row) if row else None
```

**`create_plan`:**
```python
def create_plan(self, request: CreatePlanRequest) -> tuple[str, bool]:
    with self.connect() as db:
        existing = db.execute("SELECT id FROM plans WHERE request_id=?", (request.requestId,)).fetchone()
        if existing: return existing[0], False
        plan_id = str(uuid4())
        db.execute(
            "INSERT INTO plans(id,request_id,document_ids,document_id,setup_json,status) VALUES(?,?,?,?,?,?)",
            (plan_id, request.requestId, json.dumps(request.documentIds),
             request.documentIds[0], request.setup.model_dump_json(), PlanStatus.ANALYZING)
        )
    return plan_id, True
```

**`abandoned_document_ids` — use `json_each` instead of LIKE:**
```python
def abandoned_document_ids(self, max_age_hours: int) -> list[str]:
    modifier = f"-{max_age_hours} hours"
    with self.connect() as db:
        return [
            row[0]
            for row in db.execute(
                """
                SELECT DISTINCT documents.id
                FROM documents
                LEFT JOIN plans ON EXISTS (
                    SELECT 1 FROM json_each(plans.document_ids) WHERE value = documents.id
                )
                WHERE plans.id IS NULL
                  AND documents.created_at < datetime('now', ?)
                ORDER BY documents.created_at
                """,
                (modifier,),
            ).fetchall()
        ]
```

### 5.3 main.py

**`create_plan` endpoint** — validates ALL document IDs exist (cache path to avoid redundant DB calls):
```python
@app.post("/plans", status_code=202)
async def create_plan(request: CreatePlanRequest):
    existing = STORE.plan_id_for_request(request.requestId)
    if existing: return {"planId": existing}
    for doc_id in request.documentIds:
        path = STORE.document_path(doc_id)
        if not path or not path.exists():
            raise HTTPException(404, f"Document {doc_id} not found")
    plan_id, created = STORE.create_plan(request)
    if created: schedule(plan_id)
    return {"planId": plan_id}
```

**`process` function** — iterate all documents:
```python
async def process(plan_id: str):
    row = STORE.get(plan_id)
    if not row: return
    document_ids = json.loads(row.document_ids)
    paths = [p for p in (STORE.document_path(did) for did in document_ids) if p and p.exists()]
    try:
        if not paths:
            raise ValueError("No documents available")
        STORE.set_status(plan_id, PlanStatus.IDENTIFYING_TOPICS)
        if provider is None:
            raise RuntimeError("AI provider is not configured")
        result = None
        for attempt in range(2):
            try:
                result = await provider.create_plan(paths, Setup.model_validate_json(row.setup_json))
                break
            except Exception:
                logger.warning("Attempt %d failed", attempt + 1, exc_info=True)
                if attempt == 1: raise
        STORE.set_status(plan_id, PlanStatus.CREATING_BLOCKS)
        STORE.set_status(plan_id, PlanStatus.FINALIZING)
        STORE.set_status(plan_id, PlanStatus.COMPLETED, result=result)
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        logger.exception("Plan %s generation failed", plan_id)
        STORE.set_status(plan_id, PlanStatus.FAILED, error=str(exc)[:300])
    finally:
        row = STORE.get(plan_id)
        if row and row.status in (PlanStatus.COMPLETED, PlanStatus.FAILED, PlanStatus.CANCELED):
            for did in json.loads(row.document_ids):
                cleanup_document(did)
```

**Status ordering note**: The existing status flow (ANALYZING → IDENTIFYING_TOPICS → CREATING_BLOCKS → FINALIZING → COMPLETED) is preserved. ANALYZING is set at plan creation. Once `process()` begins, it immediately promotes to IDENTIFYING_TOPICS. The Android visual progress shows ANALYZING briefly before the first server poll advances it. This is consistent with current behavior.

**`cancel_plan` endpoint** — capture `document_ids` early:
```python
@app.post("/plans/{plan_id}/cancel")
async def cancel_plan(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    document_ids = json.loads(row.document_ids)
    if row.status == PlanStatus.CANCELED:
        return {"planId": plan_id, "status": PlanStatus.CANCELED}
    if row.status in (PlanStatus.COMPLETED, PlanStatus.FAILED):
        raise HTTPException(409, "Plan is already finished")
    STORE.set_status(plan_id, PlanStatus.CANCELED)
    task = TASKS.get(plan_id)
    if task and not task.done():
        task.cancel()
        try: await task
        except asyncio.CancelledError: pass
    for did in document_ids:
        cleanup_document(did)
    return {"planId": plan_id, "status": PlanStatus.CANCELED}
```

**Double cleanup note**: When `cancel_plan` cancels the process task, the task's `finally` block also attempts cleanup. The second pass is safe — `delete_document` is idempotent (DELETE on missing row is a no-op) and `path.unlink(missing_ok=True)` ignores missing files. No data loss or error.

**`get_plan` endpoint:**
```python
@app.get("/plans/{plan_id}")
def get_plan(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    if row.status != PlanStatus.COMPLETED: raise HTTPException(409, "Plan is not ready")
    result = GeneratedPlan.model_validate_json(row.result_json)
    document_ids = json.loads(row.document_ids)
    return {
        "planId": plan_id, "documentIds": document_ids, "documentId": document_ids[0],
        "title": result.title,
        "topics": result.model_dump()["topics"],
        "blocks": result.model_dump()["blocks"],
        "totalEstimatedMinutes": sum(b.durationMinutes for b in result.blocks),
    }
```

### 5.4 provider.py

```python
class AIProvider(ABC):
    @abstractmethod
    async def create_plan(self, pdfs: list[Path], setup: Setup) -> GeneratedPlan: ...

class GeminiProvider(AIProvider):
    async def create_plan(self, pdfs: list[Path], setup: Setup) -> GeneratedPlan:
        prompt = (
            "Create a faithful study plan from these PDF documents. Use only material in the documents. "
            "Identify topics that span multiple documents and group related content together. "
            "Assign a concise, meaningful title to this study plan based on the documents' subject matter. "
            "Return JSON matching the supplied schema. Topics and blocks must use contiguous 1-based order. "
            "Each block must reference valid topic IDs. Keep instructions short and actionable. "
            "Estimate honest task durations without compressing work to fit the deadline. "
            "For every block include its task type, HIGH/MEDIUM/LOW priority, a short priority reason, "
            "whether it can be skipped in an emergency, and a minimum useful duration. Minimums are "
            "LEARN 20, PRACTICE 15, REVIEW 10, QUIZ 10, MOCK_EXAM 30 (or its actual duration), and SKIM 5 minutes. "
            f"Learner setup: {setup.model_dump_json()}"
        )
        contents = [
            *[types.Part.from_bytes(data=p.read_bytes(), mime_type="application/pdf") for p in pdfs],
            prompt,
        ]
        response = await self.client.aio.models.generate_content(
            model=self.model, contents=contents,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=GEMINI_PLAN_SCHEMA,
                temperature=0.2,
            ),
        )
        # ... rest unchanged
```

## 6. Duplicate-Request Handling

Each document upload uses per-document sub-request-ID: `"$requestId-$index"`. This ensures backend idempotency on `POST /documents` works independently per file.

## 7. Process-Death Recovery

### 7.1 During PDF Selection

URI list saved as pipe-delimited in `SavedStateHandle` (`KEY_DOCUMENT_URI_LIST`). `selectedPdfIndex` saved separately (`KEY_SELECTED_PDF_INDEX`). `restoreDocumentIfNeeded` reconstructs both and reloads all documents. Stale results discarded via `documentLoadGeneration` guard.

### 7.2 During Plan Generation

Submission (with URI list) persisted to `SharedPreferences`. On recovery, `restoreSubmission()` migrates old single-document format to new multi-document format. `startPersistedRequest()` re-uploads all PDFs with per-document sub-IDs. Backend returns existing document IDs via idempotency.

## 8. Error Handling

| Scenario | Behavior |
|---|---|
| One PDF fails to load (corrupt/encrypted) | Initial selection: all rejected with error identifying the file. Append: only new docs rejected, existing preserved. |
| One PDF exceeds 25 MB | Same — batch-level rejection scoped to the operation. |
| User selects >10 PDFs | Client-side error: "You can select up to 10 PDFs." No backend call. |
| One PDF upload fails (network) | `uploadDocuments` loop throws → plan generation Failed with retry. On same-requestId retry, completed uploads return existing IDs. On new requestId, all re-uploaded. |
| Backend finds missing document ID | 404 with specific missing doc ID → plan generation fails. |
| User removes all PDFs | Continue disabled (`canContinue` checks non-empty). |
| User adds more PDFs | Appended to group; old cache entries preserved. |

**String resources** in `strings.xml`:
```xml
<string name="too_many_pdfs">You can select up to %d PDFs at once.</string>
<string name="pdf_too_large_with_name">%1$s is too large (max 25 MB per file).</string>
<string name="pdf_corrupt_with_name">%1$s could not be opened. It may be protected or corrupted.</string>
```

## 9. Backward Compatibility

### 9.1 API Level

Android and backend deployed together. `POST /plans` accepts only `documentIds`. Old clients sending `documentId` get 422.

### 9.2 Database

Backend startup migration fills `document_ids` from `document_id` for existing rows. `document_id` column kept (set to `documentIds[0]`).

### 9.3 SharedPreferences

`restoreSubmission()` detects old `documentUri` key and migrates to `documentUris` in-place.

## 10. Testing Strategy

### 10.1 Unit Tests (Android)

- **PdfUploadUiStateTest**: `canContinue` with empty/single/multi groups. `thumbnailPageIndexes` for selected PDF.
- **PlanSetupUiStateTest**: `toSubmission()` includes all URIs. Existing step validation unchanged.
- **PdfUploadViewModelTest** (new): `selectDocuments` with 0, 1, 5, 11 URIs. `appendDocuments` exceeds 10 cap. `removeDocument` adjusts indices. `renderPage` resolves correct URI by `documentIndex`. `restoreDocumentIfNeeded` reconstructs group. `restoreSubmission` migrates old format.

### 10.2 Instrumented Tests (Android)

- **PdfUploadScreenTest**: Multi-file list display. Add More appends. Remove removes. Preview switches on PDF selection. Back behavior.
- **PlanSetupScreenTest**: Minimal — wizard steps unchanged.

### 10.3 Backend Tests

**Update existing test helpers:**

`test_store.py` helper:
```python
def request(document_id="doc-1", request_id="req-1"):
    return CreatePlanRequest(documentIds=[document_id], requestId=request_id, setup=dummy_setup())
```

`test_api.py` helper:
```python
@pytest.fixture
def setup():
    # Upload document via POST /documents
    # Returns documentId
    # create_plan uses documentIds=[documentId]
```

`test_processing.py` helper:
```python
def create_job(store, document_id="doc-1", **kw):
    request = CreatePlanRequest(documentIds=[document_id], ...)
    plan_id, _ = store.create_plan(request)
    return plan_id
```

**New tests:**
- `test_models.py`: `documentIds` with 1, 5 items passes; empty list rejects; 11 items rejects.
- `test_store.py`: `create_plan` with 3 IDs. `get` returns `PlanRow` with `document_ids` JSON array. `abandoned_document_ids` excludes docs linked via multi-doc plan. Migration idempotency (rerun on already-migrated data). Old-format row (`document_id` only, no `document_ids`) is handled.
- `test_api.py`: Upload 2 PDFs, create plan with both. `GET /plans/{id}` returns `documentIds` as list, `documentId` equals `documentIds[0]`. Cancel cleans up all documents.
- `test_provider.py`: Mock `create_plan(paths=[p1, p2])` — verifies both PDF bytes sent in `contents`. Prompt includes multi-document language.

## 11. Files Changed Summary

| File | Change |
|---|---|
| `PdfUploadUiState.kt` | `DocumentGroup`, `PdfRenderKey.documentIndex` |
| `PdfUploadViewModel.kt` | Batch selection, multi-doc lifecycle, `removeIf` cache |
| `PdfUploadRoute.kt` | Two launchers (initial + add-more), `List<String>` |
| `PdfUploadScreen.kt` | `onAddMorePdf`, file list UI, `PdfFileCard` extraction, `documentIndex` in keys |
| `PdfDocumentRepository.kt` | `loadDocuments` batch method |
| `PlanSetupUiState.kt` | `documentUris: List<String>` |
| `PlanSetupViewModel.kt` | `List<String>`, `KEY_DOCUMENT_URI_LIST`, migration |
| `PlanSetupRoute.kt` | Type changes |
| `PlanApiRepository.kt` | `uploadDocuments`, `createPlan` with list |
| `PlanGenerationViewModel.kt` | URI loop in `startPersistedRequest`/`reset`, `resolveProjectName`, migration |
| `StudyProjectRepository.kt` | `documentUri` → `documentUris` in `toSubmission`/`copy` |
| `BoundedPageCache` (in PdfUploadUiState.kt) | `removeIf` method |
| `MainActivity.kt` | Pipe-delimited URI list |
| `strings.xml` | `too_many_pdfs`, `pdf_too_large_with_name`, `pdf_corrupt_with_name` |
| `PdfUploadUiStateTest.kt` | Update for DocumentGroup |
| `PlanSetupUiStateTest.kt` | Update for `documentUris` |
| `PdfUploadViewModelTest.kt` | New — multi-doc ViewModel tests |
| `PdfUploadScreenTest.kt` | Multi-file list, add/remove/preview switch |
| `backend/models.py` | `documentIds: list[str]` |
| `backend/store.py` | `PlanRow`, schema migration with PRAGMA guard, `json_each` in abandoned |
| `backend/main.py` | Multi-doc validation, iteration, cleanup |
| `backend/provider.py` | `list[Path]`, multi-part contents |
| `backend/tests/test_store.py` | Update helper, add multi-doc/migration tests |
| `backend/tests/test_api.py` | Update helper, add multi-doc response assertion |
| `backend/tests/test_processing.py` | Update helper |
| `backend/tests/test_provider.py` | Multi-PDF test |

## 12. Non-Goals

- Parallel PDF upload (sequential for simplicity and idempotency).
- Per-document plan customization.
- Page reordering across PDFs.
- Backend-side PDF text extraction (Gemini handles PDF natively).
- Drag-to-reorder PDFs.
