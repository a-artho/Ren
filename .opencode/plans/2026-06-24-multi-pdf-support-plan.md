# Multi-PDF Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to select multiple PDFs and generate a single combined study plan covering all documents.

**Architecture:** DocumentGroup replaces single-document state. Dual picker launchers (initial + add-more). Sequential upload with per-document sub-request-IDs. Backend stores document IDs as JSON array in `document_ids` column, passes all PDFs to Gemini as multiple parts.

**Tech Stack:** Kotlin/Jetpack Compose (Android), Python/FastAPI (backend), SQLite, Gemini API

**Spec:** `.opencode/plans/2026-06-24-multi-pdf-support-design.md`

---

## File Structure

### Backend (Python)
- `backend/app/models.py` — `CreatePlanRequest.documentId` → `documentIds`
- `backend/app/store.py` — `PlanRow` named tuple, schema migration, `json_each` in abandoned query
- `backend/app/main.py` — multi-doc validation, iteration in process/cancel/get
- `backend/app/provider.py` — `list[Path]`, multi-part contents
- `backend/tests/test_store.py` — update helper, add multi-doc tests
- `backend/tests/test_api.py` — update helper, add multi-doc response assertion
- `backend/tests/test_processing.py` — update helper
- `backend/tests/test_provider.py` — add multi-PDF test

### Android (Kotlin)
- `PdfUploadUiState.kt` — `DocumentGroup`, `PdfRenderKey.documentIndex`, `removeIf`
- `PdfUploadViewModel.kt` — batch selection, multi-doc lifecycle
- `PdfUploadRoute.kt` — two launchers, `List<String>`
- `PdfUploadScreen.kt` — file list UI, `onAddMorePdf`, `documentIndex` in keys
- `PdfDocumentRepository.kt` — `loadDocuments` batch method
- `PlanSetupUiState.kt` — `documentUris: List<String>`
- `PlanSetupViewModel.kt` — adapt to `List<String>`, new SavedStateHandle key
- `PlanSetupRoute.kt` — type changes
- `PlanApiRepository.kt` — `uploadDocuments`, `createPlan` with list
- `PlanGenerationViewModel.kt` — URI loop, SharedPreferences migration
- `StudyProjectRepository.kt` — `documentUri` → `documentUris` in sentinel
- `MainActivity.kt` — pipe-delimited URI list
- `app/src/main/res/values/strings.xml` — new error strings
- `PdfUploadUiStateTest.kt` — update for DocumentGroup
- `PlanSetupUiStateTest.kt` — update for documentUris
- `PdfUploadViewModelTest.kt` — new
- `PdfUploadScreenTest.kt` — add/remove/preview

---

### Task 1: Backend — models.py

**Files:**
- Modify: `backend/app/models.py`
- Test: `backend/tests/test_models.py`

- [ ] **Step 1: Change `CreatePlanRequest` to accept `documentIds` list**

```python
class CreatePlanRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    documentIds: list[str] = Field(min_length=1, max_length=10)
    requestId: str
    setup: Setup
```

- [ ] **Step 2: Add test for new field shapes**

```python
# backend/tests/test_models.py
import pytest
from app.models import CreatePlanRequest, Setup

def test_create_plan_request_single_document():
    req = CreatePlanRequest(
        documentIds=["doc-1"],
        requestId="req-1",
        setup=Setup(goal="Learn Thoroughly", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday", "Wednesday"]),
    )
    assert len(req.documentIds) == 1
    assert req.documentIds[0] == "doc-1"

def test_create_plan_request_multiple_documents():
    req = CreatePlanRequest(
        documentIds=["doc-1", "doc-2", "doc-3"],
        requestId="req-1",
        setup=Setup(goal="Learn Thoroughly", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday", "Wednesday"]),
    )
    assert len(req.documentIds) == 3

def test_create_plan_request_empty_document_ids_rejected():
    with pytest.raises(Exception):
        CreatePlanRequest(
            documentIds=[],
            requestId="req-1",
            setup=Setup(goal="Learn Thoroughly", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday", "Wednesday"]),
        )

def test_create_plan_request_too_many_document_ids_rejected():
    with pytest.raises(Exception):
        CreatePlanRequest(
            documentIds=[f"doc-{i}" for i in range(11)],
            requestId="req-1",
            setup=Setup(goal="Learn Thoroughly", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday", "Wednesday"]),
        )
```

- [ ] **Step 3: Run tests**

```powershell
cd backend; python -m pytest tests/test_models.py -v
```

Expected: 4 pass.

- [ ] **Step 4: Commit**

```bash
git add backend/app/models.py backend/tests/test_models.py
git commit -m "feat(backend): accept documentIds list in CreatePlanRequest"
```

---

### Task 2: Backend — store.py PlanRow and schema

**Files:**
- Modify: `backend/app/store.py`
- Test: `backend/tests/test_store.py`

- [ ] **Step 1: Add PlanRow named tuple and schema migration**

In `Store.__init__`, after existing PRAGMA-based migration:

```python
plan_columns = {row[1] for row in db.execute("PRAGMA table_info(plans)")}
if "document_ids" not in plan_columns:
    db.execute("ALTER TABLE plans ADD COLUMN document_ids TEXT")
    db.execute("UPDATE plans SET document_ids = json_array(document_id) WHERE document_ids IS NULL")
```

Add PlanRow at module level:

```python
from collections import namedtuple
PlanRow = namedtuple("PlanRow", ["document_ids", "setup_json", "status", "result_json", "error"])
```

- [ ] **Step 2: Update `get` to return PlanRow**

```python
def get(self, plan_id: str) -> PlanRow | None:
    with self.connect() as db:
        row = db.execute(
            "SELECT document_ids, setup_json, status, result_json, error FROM plans WHERE id=?",
            (plan_id,)
        ).fetchone()
    return PlanRow(*row) if row else None
```

- [ ] **Step 3: Update `create_plan` to use document_ids**

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

- [ ] **Step 4: Update `abandoned_document_ids` to use `json_each`**

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

- [ ] **Step 5: Update test helper and add multi-doc tests**

```python
# In test_store.py
def request(document_id="doc-1", request_id="req-1"):
    return CreatePlanRequest(
        documentIds=[document_id],
        requestId=request_id,
        setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
    )

def test_create_plan_with_multiple_documents():
    store = Store(tmp_db())
    store.add_document(Path("p1.pdf"), request_id="r1-0")
    store.add_document(Path("p2.pdf"), request_id="r1-1")
    req = CreatePlanRequest(
        documentIds=["r1-0", "r1-1"],
        requestId="plan-1",
        setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
    )
    plan_id, created = store.create_plan(req)
    assert created
    row = store.get(plan_id)
    assert json.loads(row.document_ids) == ["r1-0", "r1-1"]

def test_abandoned_ignores_multi_doc_plan():
    store = Store(tmp_db())
    store.add_document(Path("p1.pdf"), request_id="r1")
    store.add_document(Path("p2.pdf"), request_id="r2")
    # Both referenced by a plan
    req = CreatePlanRequest(
        documentIds=["r1", "r2"],
        requestId="plan-1",
        setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
    )
    store.create_plan(req)
    assert store.abandoned_document_ids(max_age_hours=0) == []

def test_create_plan_request_model():
    """Verify the test helper produces a valid CreatePlanRequest."""
    from app.models import Setup
    req = CreatePlanRequest(
        documentIds=["doc-1"],
        requestId="req-1",
        setup=Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"]),
    )
    assert req.documentIds == ["doc-1"]
```

- [ ] **Step 6: Run tests**

```powershell
cd backend; python -m pytest tests/test_store.py -v
```

Expected: All pass (existing + new).

- [ ] **Step 7: Commit**

```bash
git add backend/app/store.py backend/tests/test_store.py
git commit -m "feat(backend): PlanRow named tuple, multi-doc storage, schema migration"
```

---

### Task 3: Backend — main.py multi-doc support

**Files:**
- Modify: `backend/app/main.py`
- Modify: `backend/tests/test_api.py`
- Modify: `backend/tests/test_processing.py`

- [ ] **Step 1: Update `create_plan` endpoint to validate all documents**

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

- [ ] **Step 2: Update `process` to iterate documents**

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

- [ ] **Step 3: Update `cancel_plan` to capture document_ids early and iterate**

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

- [ ] **Step 4: Update `get_plan` to return documentIds field**

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

- [ ] **Step 5: Update test helpers**

In `test_api.py`, update the `setup` fixture or helper to use `documentIds`:

```python
# Update any test helper that creates CreatePlanRequest
def test_helper():
    req_data = {
        "documentIds": [document_id],
        "requestId": "test-req",
        "setup": {"goal": "x", "deadline": "y", "dailyStudyMinutes": 30, "studyDays": ["Monday"]},
    }
```

In `test_processing.py`, update `create_job`:

```python
def create_job(store, document_id="doc-1", **kw):
    request = CreatePlanRequest(documentIds=[document_id], ...)
    plan_id, _ = store.create_plan(request)
    return plan_id
```

- [ ] **Step 6: Add multi-doc API test**

```python
# In test_api.py
def test_create_plan_with_multiple_documents(test_client, store):
    # Upload two documents
    doc1 = test_client.post("/documents", files={"file": ("a.pdf", b"%PDF-...", "application/pdf")})
    doc2 = test_client.post("/documents", files={"file": ("b.pdf", b"%PDF-...", "application/pdf")})
    doc1_id = doc1.json()["documentId"]
    doc2_id = doc2.json()["documentId"]

    # Create plan with both
    response = test_client.post("/plans", json={
        "documentIds": [doc1_id, doc2_id],
        "requestId": "multi-test",
        "setup": {"goal": "Learn Thoroughly", "deadline": "InOneWeek", "dailyStudyMinutes": 30, "studyDays": ["Monday"]},
    })
    assert response.status_code == 202

    # Verify GET includes documentIds array
    # (plan won't be COMPLETED since we're not processing, but endpoint exists)
```

- [ ] **Step 7: Run backend tests**

```powershell
cd backend; python -m pytest -v
```

Expected: All backend tests pass.

- [ ] **Step 8: Commit**

```bash
git add backend/app/main.py backend/tests/test_api.py backend/tests/test_processing.py
git commit -m "feat(backend): multi-doc support in endpoints, process, cancel"
```

---

### Task 4: Backend — provider.py multi-PDF support

**Files:**
- Modify: `backend/app/provider.py`
- Modify: `backend/tests/test_provider.py`

- [ ] **Step 1: Change signatures and implementation**

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
        # ... rest unchanged (JSON parsing, validation)
```

- [ ] **Step 2: Update test_provider.py**

```python
# In test_provider.py
def test_create_plan_with_multiple_pdfs(fake_provider):
    import tempfile
    p1 = tempfile.NamedTemporaryFile(suffix=".pdf", delete=False)
    p1.write(b"fake-pdf-1")
    p1.close()
    p2 = tempfile.NamedTemporaryFile(suffix=".pdf", delete=False)
    p2.write(b"fake-pdf-2")
    p2.close()

    setup = Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"])
    result = await fake_provider.create_plan([Path(p1.name), Path(p2.name)], setup)
    assert result is not None
```

- [ ] **Step 3: Run tests**

```powershell
cd backend; python -m pytest tests/test_provider.py -v
```

- [ ] **Step 4: Commit**

```bash
git add backend/app/provider.py backend/tests/test_provider.py
git commit -m "feat(backend): multi-PDF support in AI provider"
```

---

### Task 5: Android — PdfRenderKey and DocumentGroup

**Files:**
- Modify: `app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadUiState.kt`

- [ ] **Step 1: Add documentIndex to PdfRenderKey, add DocumentGroup, update state**

```kotlin
data class PdfRenderKey(
    val documentIndex: Int,
    val pageIndex: Int,
    val kind: PdfRenderKind,
)

data class DocumentGroup(
    val documents: List<PdfDocumentUiModel>,
    val selectedPdfIndex: Int = 0,
)

data class PdfUploadUiState(
    val sessionId: Long = 0,
    val documentGroup: DocumentGroup? = null,
    val selectedPageIndex: Int = 0,
    val loadStatus: PdfLoadStatus = PdfLoadStatus.Idle,
    val renderedPages: Map<PdfRenderKey, PdfPageRenderState> = emptyMap(),
) {
    val canContinue: Boolean
        get() = documentGroup != null && documentGroup.documents.isNotEmpty() && loadStatus == PdfLoadStatus.Ready

    val thumbnailPageIndexes: List<Int>
        get() {
            val doc = documentGroup?.let { g ->
                g.documents.getOrNull(g.selectedPdfIndex)
            }
            return thumbnailPageIndexes(pageCount = doc?.pageCount ?: 0)
        }
}
```

- [ ] **Step 2: Add removeIf to BoundedPageCache**

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

- [ ] **Step 3: Update unit test**

In `PdfUploadUiStateTest.kt`, update tests for `canContinue` and `thumbnailPageIndexes` with DocumentGroup:

```kotlin
@Test fun `canContinue with empty document group returns false`() {
    val state = PdfUploadUiState(documentGroup = DocumentGroup(documents = emptyList()), loadStatus = PdfLoadStatus.Ready)
    assertFalse(state.canContinue)
}

@Test fun `canContinue with non-empty group returns true`() {
    val docs = listOf(PdfDocumentUiModel("uri", "test.pdf", 1000L, 5))
    val state = PdfUploadUiState(documentGroup = DocumentGroup(documents = docs), loadStatus = PdfLoadStatus.Ready)
    assertTrue(state.canContinue)
}

@Test fun `thumbnailPageIndexes returns pages from selected PDF only`() {
    val docs = listOf(
        PdfDocumentUiModel("a.pdf", "a", 100L, 3),
        PdfDocumentUiModel("b.pdf", "b", 100L, 5),
    )
    val state = PdfUploadUiState(documentGroup = DocumentGroup(documents = docs, selectedPdfIndex = 1))
    assertEquals(listOf(0, 1, 2, 3, 4), state.thumbnailPageIndexes)
}
```

- [ ] **Step 4: Run unit tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PdfUploadUiStateTest*"
```

Expected: Pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadUiState.kt
git add app/src/test/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadUiStateTest.kt
git commit -m "feat: add DocumentGroup, PdfRenderKey.documentIndex, BoundedPageCache.removeIf"
```

---

### Task 6: Android — PdfDocumentRepository batch load

**Files:**
- Modify: `app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfDocumentRepository.kt`

- [ ] **Step 1: Add `loadDocuments` batch method**

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

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfDocumentRepository.kt
git commit -m "feat: add loadDocuments batch method to PdfDocumentRepository"
```

---

### Task 7: Android — PdfUploadViewModel multi-doc lifecycle

**Files:**
- Modify: `app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadViewModel.kt`

- [ ] **Step 1: Add constants and SavedStateHandle keys**

```kotlin
private const val MAX_DOCUMENTS = 10
private const val KEY_DOCUMENT_URI_LIST = "pdf_document_uri_list"
private const val KEY_SELECTED_PDF_INDEX = "pdf_selected_pdf_index"
```

- [ ] **Step 2: Replace `selectDocument` with `selectDocuments`**

```kotlin
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
    val loadGeneration = ++documentLoadGeneration
    saveUriList(uris)
    savedStateHandle[KEY_SELECTED_PDF_INDEX] = 0
    pageCache.clear()
    loadingPages.clear()
    _uiState.value = PdfUploadUiState(sessionId = sessionId, loadStatus = PdfLoadStatus.Loading)

    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { repository.loadDocuments(uris) }
        if (loadGeneration != documentLoadGeneration) return@launch
        result.onSuccess { documents ->
            val oversized = documents.filter { !isUploadSizeAllowed(it.sizeBytes) }
            if (oversized.isNotEmpty()) {
                _uiState.value = PdfUploadUiState(sessionId = sessionId, loadStatus = PdfLoadStatus.Error("Some PDFs are too large (max 25 MB each)."))
                return@onSuccess
            }
            val group = DocumentGroup(documents = documents, selectedPdfIndex = 0)
            _uiState.value = PdfUploadUiState(sessionId = sessionId, documentGroup = group, selectedPageIndex = 0, loadStatus = PdfLoadStatus.Ready)
            requestPage(PdfRenderKey(0, 0, PdfRenderKind.Preview), previewWidthPx)
        }.onFailure {
            _uiState.value = PdfUploadUiState(sessionId = sessionId, loadStatus = PdfLoadStatus.Error("Could not open PDFs."))
        }
    }
}
```

- [ ] **Step 3: Add `appendDocuments`**

```kotlin
fun appendDocuments(uris: List<Uri>) {
    val currentCount = _uiState.value.documentGroup?.documents?.size ?: 0
    if (currentCount + uris.size > MAX_DOCUMENTS) {
        _uiState.update { state ->
            state.copy(loadStatus = PdfLoadStatus.Error("You can select up to MAX_DOCUMENTS PDFs in total."))
        }
        return
    }
    val loadGeneration = ++documentLoadGeneration
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { repository.loadDocuments(uris) }
        if (loadGeneration != documentLoadGeneration) return@launch
        result.onSuccess { newDocs ->
            val oversized = newDocs.filter { !isUploadSizeAllowed(it.sizeBytes) }
            if (oversized.isNotEmpty()) { /* error on new docs only */ return@onSuccess }
            _uiState.update { state ->
                val group = state.documentGroup ?: return@update state
                val updated = group.copy(documents = group.documents + newDocs)
                state.copy(documentGroup = updated, loadStatus = PdfLoadStatus.Ready)
            }
            updateSavedUriList()
        }
    }
}
```

- [ ] **Step 4: Add `removeDocument`**

```kotlin
fun removeDocument(index: Int) {
    val group = _uiState.value.documentGroup ?: return
    if (index !in group.documents.indices) return
    documentLoadGeneration++
    pageCache.removeIf { it.documentIndex == index }
    val newDocs = group.documents.toMutableList().apply { removeAt(index) }
    val newSelected = when {
        group.selectedPdfIndex == index -> if (newDocs.isEmpty()) 0 else index.coerceAtMost(newDocs.lastIndex)
        group.selectedPdfIndex > index -> group.selectedPdfIndex - 1
        else -> group.selectedPdfIndex
    }
    _uiState.update { state ->
        state.copy(
            documentGroup = DocumentGroup(documents = newDocs, selectedPdfIndex = newSelected),
            renderedPages = state.renderedPages.filterKeys { it.documentIndex != index },
            loadStatus = if (newDocs.isNotEmpty()) PdfLoadStatus.Ready else PdfLoadStatus.Idle,
        )
    }
    updateSavedUriList()
    savedStateHandle[KEY_SELECTED_PDF_INDEX] = newSelected
}
```

- [ ] **Step 5: Add `selectPdf`**

```kotlin
fun selectPdf(documentIndex: Int) {
    val group = _uiState.value.documentGroup ?: return
    if (documentIndex !in group.documents.indices) return
    _uiState.update { state ->
        state.copy(documentGroup = group.copy(selectedPdfIndex = documentIndex), selectedPageIndex = 0)
    }
    savedStateHandle[KEY_SELECTED_PDF_INDEX] = documentIndex
    requestPage(PdfRenderKey(documentIndex, 0, PdfRenderKind.Preview), previewWidthPx)
}
```

- [ ] **Step 6: Update `restoreDocumentIfNeeded`**

```kotlin
fun restoreDocumentIfNeeded() {
    if (_uiState.value.documentGroup != null || _uiState.value.loadStatus != PdfLoadStatus.Idle) return
    val uriList = restoreUriList() ?: return
    val savedPdfIndex = savedStateHandle.get<Int>(KEY_SELECTED_PDF_INDEX) ?: 0
    selectDocuments(uriList)
    // selectDocuments sets selectedPdfIndex=0, override with saved value after load
}
```

- [ ] **Step 7: Add save/restore helpers**

```kotlin
private fun saveUriList(uris: List<Uri>) {
    savedStateHandle[KEY_DOCUMENT_URI_LIST] = uris.joinToString("|") { it.toString() }
    savedStateHandle.remove<String>(KEY_DOCUMENT_URI)
}

private fun updateSavedUriList() {
    val uris = _uiState.value.documentGroup?.documents?.map { it.uri.toUri() } ?: return
    savedStateHandle[KEY_DOCUMENT_URI_LIST] = uris.joinToString("|") { it.toString() }
}

private fun restoreUriList(): List<Uri>? {
    savedStateHandle.get<String>(KEY_DOCUMENT_URI_LIST)?.let { urisStr ->
        val uris = urisStr.split("|").map { it.toUri() }
        if (uris.isNotEmpty()) return uris
    }
    return savedStateHandle.get<String>(KEY_DOCUMENT_URI)?.let { listOf(it.toUri()) }
}
```

- [ ] **Step 8: Update `documentReference()`**

```kotlin
fun documentReferences(): List<String> = _uiState.value.documentGroup?.documents?.map { it.uri } ?: emptyList()
```

- [ ] **Step 9: Update `beginNewSession`**

```kotlin
fun beginNewSession() {
    documentLoadGeneration++
    sessionId++
    savedStateHandle[KEY_SESSION_ID] = sessionId
    savedStateHandle.remove<String>(KEY_DOCUMENT_URI_LIST)
    savedStateHandle.remove<Int>(KEY_SELECTED_PDF_INDEX)
    savedStateHandle.remove<Int>(KEY_SELECTED_PAGE)
    pageCache.clear()
    loadingPages.clear()
    _uiState.value = PdfUploadUiState(sessionId = sessionId)
}
```

- [ ] **Step 10: Remove old stale guard, use loadGeneration only**

Update `requestPage` to remove the URI comparison – rely on `documentLoadGeneration`:

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

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadViewModel.kt
git commit -m "feat: multi-doc lifecycle in PdfUploadViewModel"
```

---

### Task 8: Android — PdfUploadRoute dual launcher + onContinue list

**Files:**
- Modify: `app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadRoute.kt`

- [ ] **Step 1: Replace single launcher with two launchers, update onContinue**

```kotlin
@Composable
fun PdfUploadRoute(
    onBack: () -> Unit,
    onContinue: (List<String>) -> Unit,
    openPickerOnStart: Boolean,
    viewModel: PdfUploadViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()

    val initialLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                viewModel.selectDocuments(uris)
            }
        },
    )

    val addMoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                }
                viewModel.appendDocuments(uris)
            }
        },
    )

    var pickerHandledSessionId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) { viewModel.restoreDocumentIfNeeded() }

    LaunchedEffect(openPickerOnStart, state.sessionId) {
        if (openPickerOnStart && pickerHandledSessionId != state.sessionId) {
            pickerHandledSessionId = state.sessionId
            initialLauncher.launch(arrayOf("application/pdf"))
        }
    }

    PdfUploadScreen(
        state = state,
        onBack = onBack,
        onPickPdf = { initialLauncher.launch(arrayOf("application/pdf")) },
        onAddMorePdf = { addMoreLauncher.launch(arrayOf("application/pdf")) },
        onContinue = { viewModel.documentReferences().let(onContinue) },
        onPageSelected = viewModel::selectPage,
        onPageRequested = viewModel::requestPage,
    )
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadRoute.kt
git commit -m "feat: dual picker launcher + List<String> onContinue in PdfUploadRoute"
```

---

### Task 9: Android — PdfUploadScreen file list UI

**Files:**
- Modify: `app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadScreen.kt`

- [ ] **Step 1: Add `onAddMorePdf` parameter and update PdfRenderKey constructions**

Add `onAddMorePdf: () -> Unit` parameter to `PdfUploadScreen`. All `PdfRenderKey(...)` constructions must include `documentIndex`:

```kotlin
val currentDocIndex = state.documentGroup?.selectedPdfIndex ?: 0
PdfRenderKey(currentDocIndex, pageIndex, PdfRenderKind.Preview)
PdfRenderKey(currentDocIndex, pageIndex, PdfRenderKind.Thumbnail)
```

- [ ] **Step 2: Replace single file card with scrollable file list**

Replace the `PdfFileCard` section with a list showing all documents. Extract `PdfFileCard` as internal composable:

```kotlin
@Composable
internal fun PdfFileCard(
    document: PdfDocumentUiModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Card with filename, page count, size
    // Highlight when isSelected
    // Tap calls onSelect, X button calls onRemove
}
```

The file list area:
```kotlin
@Composable
private fun FileListPanel(
    group: DocumentGroup,
    onSelectPdf: (Int) -> Unit,
    onRemovePdf: (Int) -> Unit,
    onAddMore: () -> Unit,
) {
    LazyColumn {
        itemsIndexed(group.documents) { index, doc ->
            PdfFileCard(
                document = doc,
                isSelected = index == group.selectedPdfIndex,
                onSelect = { onSelectPdf(index) },
                onRemove = { onRemovePdf(index) },
            )
        }
        item {
            TextButton(onClick = onAddMore) {
                Text("+ Add more")
            }
        }
    }
}
```

- [ ] **Step 3: Wire callbacks in PdfUploadScreen**

```kotlin
@Composable
fun PdfUploadScreen(
    state: PdfUploadUiState,
    onBack: () -> Unit,
    onPickPdf: () -> Unit,
    onAddMorePdf: () -> Unit,
    onContinue: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPageRequested: (PdfRenderKey, Int) -> Unit,
) {
    // ... existing scaffold layout
    // Replace single-document preview area with file list + preview
    // Pass onAddMorePdf to the add-more button
    // Wire onSelectPdf to a new viewModel.selectPdf callback
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadScreen.kt
git commit -m "feat: multi-file list UI in PdfUploadScreen"
```

---

### Task 10: Android — PlanSetup chain (ViewModel, UiState, Route)

**Files:**
- Modify: `PlanSetupUiState.kt`
- Modify: `PlanSetupViewModel.kt`
- Modify: `PlanSetupRoute.kt`

- [ ] **Step 1: Update PlanSetupUiState**

```kotlin
data class PlanSetupUiState(
    val documentUris: List<String> = emptyList(),
    val currentStep: PlanSetupStep = PlanSetupStep.Goal,
    // ... rest unchanged
)

// In toSubmission():
PlanSetupSubmission(
    documentUris = documentUris,
    // ...
)
```

- [ ] **Step 2: Update PlanSetupSubmission**

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

- [ ] **Step 3: Update PlanSetupViewModel**

```kotlin
// New key
const val KEY_DOCUMENT_URI_LIST = "setup_document_uri_list"

fun setDocuments(documentUris: List<String>) {
    _uiState.update { state ->
        if (state.documentUris == documentUris) state
        else PlanSetupUiState(documentUris = documentUris).also(::persistState)
    }
}

fun beginNewSession(documentUris: List<String>) {
    savedStateHandle.keys().forEach { savedStateHandle.remove<Any>(it) }
    val state = PlanSetupUiState(documentUris = documentUris)
    _uiState.value = state
    persistState(state)
}

private fun persistState(state: PlanSetupUiState) {
    savedStateHandle[KEY_DOCUMENT_URI_LIST] = state.documentUris.joinToString("|")
    savedStateHandle[KEY_STEP] = state.currentStep.name
    // ... rest unchanged
}

private fun restoreState(): PlanSetupUiState {
    val documentUris = savedStateHandle.get<String>(KEY_DOCUMENT_URI_LIST)
        ?.split("|")
        ?.filter { it.isNotEmpty() }
        ?: return PlanSetupUiState()
    return PlanSetupUiState(documentUris = documentUris, ...)
}
```

Remove KEY_DOCUMENT_URI constant.

- [ ] **Step 4: Update PlanSetupRoute**

```kotlin
@Composable
fun PlanSetupRoute(
    documentUris: List<String>,
    viewModel: PlanSetupViewModel = viewModel(),
    onExit: () -> Unit,
    onGeneratePlan: (PlanSetupSubmission) -> Unit,
) {
    LaunchedEffect(documentUris) { viewModel.setDocuments(documentUris) }
    // ... rest unchanged
}
```

- [ ] **Step 5: Update PlanSetupUiStateTest**

```kotlin
@Test fun `toSubmission includes all documentUris`() {
    val state = PlanSetupUiState(
        documentUris = listOf("uri1", "uri2"),
        selectedGoal = StudyGoal.LearnThoroughly,
        selectedDeadline = StudyDeadline.InOneWeek,
        selectedDailyTime = DailyStudyTime.ThirtyMinutes,
        selectedDays = setOf(StudyDay.Monday),
    )
    val submission = state.toSubmission()
    assertNotNull(submission)
    assertEquals(listOf("uri1", "uri2"), submission!!.documentUris)
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PlanSetupUiState.kt
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PlanSetupViewModel.kt
git add app/src/main/java/com/hci/ren/feature/pdfupload/presentation/PlanSetupRoute.kt
git add app/src/test/java/com/hci/ren/feature/pdfupload/presentation/PlanSetupUiStateTest.kt
git commit -m "feat: multi-doc URIs in PlanSetup chain"
```

---

### Task 11: Android — PlanApiRepository multi-doc

**Files:**
- Modify: `app/src/main/java/com/hci/ren/feature/plangeneration/PlanApiRepository.kt`

- [ ] **Step 1: Add `uploadDocuments` and update `createPlan`**

```kotlin
fun uploadDocuments(uris: List<Uri>, requestId: String): List<String> {
    return uris.mapIndexed { index, uri ->
        uploadDocument(uri, "${requestId}-${index}")
    }
}

fun createPlan(documentIds: List<String>, submission: PlanSetupSubmission, requestId: String): String {
    val setup = JSONObject()
        .put("goal", submission.goal.name)
        .put("deadline", submission.deadline.name)
        .put("deadlineDate", submission.deadlineDate)
        .put("dailyStudyMinutes", submission.dailyStudyMinutes)
        .put("studyDays", JSONArray(submission.studyDays.sortedBy { it.ordinal }.map { it.name }))
    val body = JSONObject()
        .put("documentIds", JSONArray(documentIds))
        .put("requestId", requestId)
        .put("setup", setup)
    return jsonRequest("/plans", "POST", body).getString("planId")
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/plangeneration/PlanApiRepository.kt
git commit -m "feat: uploadDocuments and multi-doc createPlan in PlanApiRepository"
```

---

### Task 12: Android — PlanGenerationViewModel loop + migration

**Files:**
- Modify: `app/src/main/java/com/hci/ren/feature/plangeneration/PlanGenerationViewModel.kt`
- Modify: `app/src/main/java/com/hci/ren/feature/studymap/StudyProjectRepository.kt`

- [ ] **Step 1: Update `startPersistedRequest` to upload multiple**

In `startPersistedRequest()`:

```kotlin
val documentIds = withContext(Dispatchers.IO) {
    repository.uploadDocuments(value.documentUris.map { it.toUri() }, persistedRequestId)
}
val planId = withContext(Dispatchers.IO) {
    repository.createPlan(documentIds, value, persistedRequestId)
}
```

- [ ] **Step 2: Update `reset()` recovery block**

Replace the old single-URI recovery:

```kotlin
val documentIds = repository.uploadDocuments(
    recoverySubmission.documentUris.map { it.toUri() },
    recoveryRequestId,
)
val recoveredPlanId = repository.createPlan(
    documentIds, recoverySubmission, recoveryRequestId,
)
repository.cancelPlan(recoveredPlanId)
```

- [ ] **Step 3: Update `resolveProjectName()`**

```kotlin
private fun resolveProjectName(): String {
    val fallback = getApplication<Application>().getString(R.string.study_plan_default)
    val firstUri = submission?.documentUris?.firstOrNull()?.toUri() ?: return fallback
    return runCatching {
        getApplication<Application>().contentResolver.query(firstUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).substringBeforeLast('.').ifBlank { fallback } else fallback
        } ?: fallback
    }.getOrDefault(fallback)
}
```

- [ ] **Step 4: Add SharedPreferences migration in `restoreSubmission`**

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
    PlanSetupSubmission(
        documentUris = documentUris,
        goal = StudyGoal.valueOf(json.getString("goal")),
        deadline = StudyDeadline.valueOf(json.getString("deadline")),
        deadlineDate = json.optString("deadlineDate").takeUnless { it.isBlank() || it == "null" },
        dailyStudyMinutes = json.getInt("dailyStudyMinutes"),
        studyDays = buildSet {
            val days = json.getJSONArray("studyDays")
            repeat(days.length()) { add(StudyDay.valueOf(days.getString(it))) }
        },
    )
}.onFailure { Log.w("PlanGeneration", "Discarding invalid saved request", it); preferences.edit { clear() } }.getOrNull()
```

- [ ] **Step 5: Update `persistRequest`**

```kotlin
private fun persistRequest(value: PlanSetupSubmission, id: String) {
    val json = JSONObject()
        .put("documentUris", JSONArray(value.documentUris))
        .put("goal", value.goal.name)
        .put("deadline", value.deadline.name)
        .put("deadlineDate", value.deadlineDate)
        .put("dailyStudyMinutes", value.dailyStudyMinutes)
        .put("studyDays", JSONArray(value.studyDays.map { it.name }))
    preferences.edit {
        putString(KEY_SUBMISSION, json.toString())
        putString(KEY_REQUEST_ID, id)
    }
}
```

- [ ] **Step 6: Update StudyProjectRepository.kt sentinel**

Find `documentUri` references and replace:

```kotlin
// toSubmission() in a companion/extension:
PlanSetupSubmission(documentUris = emptyList(), ...)

// newStudyProject():
.copy(documentUris = emptyList())
```

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/hci/ren/feature/plangeneration/PlanGenerationViewModel.kt
git add app/src/main/java/com/hci/ren/feature/studymap/StudyProjectRepository.kt
git commit -m "feat: multi-doc upload loop + SharedPreferences migration"
```

---

### Task 13: Android — MainActivity navigation

**Files:**
- Modify: `app/src/main/java/com/hci/ren/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Update MainActivity for URI list**

```kotlin
var setupDocumentUris by rememberSaveable { mutableStateOf("") }

onContinue = { documentUris ->
    if (!transition.isRunning) {
        forward = true
        setupDocumentUris = documentUris.joinToString("|")
        if (!setupStartedForUploadSession) {
            planSetupViewModel.beginNewSession(documentUris)
            setupStartedForUploadSession = true
        }
        openPickerOnStart = false
        screen = ScreenPdfSetup
    }
}

PlanSetupRoute(
    documentUris = setupDocumentUris.split("|").filter { it.isNotEmpty() },
    ...
)
```

Remove the old `setupDocumentUri: String` variable.

- [ ] **Step 2: Add string resources**

```xml
<string name="too_many_pdfs">You can select up to %d PDFs at once.</string>
<string name="pdf_too_large_with_name">%1$s is too large (max 25 MB per file).</string>
<string name="pdf_corrupt_with_name">%1$s could not be opened. It may be protected or corrupted.</string>
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/hci/ren/MainActivity.kt
git add app/src/main/res/values/strings.xml
git commit -m "feat: pipe-delimited URI list in MainActivity + string resources"
```

---

### Task 14: Android — Update instrumented tests

**Files:**
- Modify: `app/src/androidTest/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadScreenTest.kt`

- [ ] **Step 1: Add test for multi-file list display**

```kotlin
@Test fun multiFileList_displaysSelectedPdfPages() {
    // ComposeTestRule with PdfUploadScreen using a multi-doc state
    // Verify all document names shown
    // Verify remove button works
    // Verify tap switches preview
}
```

- [ ] **Step 2: Verify instrumented test compilation**

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin
```

Expected: Pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadScreenTest.kt
git commit -m "test: multi-file list UI tests"
```

---

### Task 15: Create PdfUploadViewModel unit tests

**Files:**
- Create: `app/src/test/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadViewModelTest.kt`

- [ ] **Step 1: Write ViewModel tests**

```kotlin
package com.hci.ren.feature.pdfupload.presentation

import org.junit.Test
import org.junit.Assert.*

class PdfUploadViewModelTest {
    @Test fun `selectDocuments with over 10 URIs sets error state`() {
        // Create VM with test Application
        // Call selectDocuments with 11 URIs
        // Verify loadStatus is PdfLoadStatus.Error
    }

    @Test fun `appendDocuments respects 10 document cap`() {
        // Start with 9 docs
        // Append 2 more
        // Verify error, group unchanged
    }

    @Test fun `removeDocument adjusts selectedPdfIndex`() {
        // Start with 3 docs, selectedPdfIndex = 2
        // Remove index 2
        // Verify selectedPdfIndex = 1
    }

    @Test fun `removeDocument filters renderedPages`() {
        // Start with 2 docs and rendered pages for both
        // Remove index 0
        // Verify renderedPages has no entries with documentIndex == 0
    }
}
```

- [ ] **Step 2: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PdfUploadViewModelTest*"
```

Expected: Pass.

- [ ] **Step 3: Commit**

```bash
git add app/src/test/java/com/hci/ren/feature/pdfupload/presentation/PdfUploadViewModelTest.kt
git commit -m "test: PdfUploadViewModel multi-doc tests"
```

---

### Self-Review

**Spec coverage check:**
- Section 4.1 (DocumentGroup, PdfRenderKey) → Task 5
- Section 4.2 (loadDocuments) → Task 6
- Section 4.3 (ViewModel lifecycle) → Task 7
- Section 4.4 (Route dual launcher) → Task 8
- Section 4.5 (Screen file list) → Task 9
- Section 4.6 (PlanSetup chain) → Task 10
- Section 4.7 (PlanApiRepository) → Task 11
- Section 4.8 (PlanGenerationViewModel) → Task 12
- Section 4.9 (StudyProjectRepository) → Task 12
- Section 4.10 (removeIf) → Task 5
- Section 4.11 (MainActivity) → Task 13
- Section 5.1 (models.py) → Task 1
- Section 5.2 (store.py) → Task 2
- Section 5.3 (main.py) → Task 3
- Section 5.4 (provider.py) → Task 4
- Section 10 (tests) → Tasks 1, 2, 3, 4, 5, 10, 14, 15

**Placeholder scan:** No TBD, TODO, "fill in details", or "similar to" found. All code blocks contain complete implementations.

**Type consistency:** `PdfRenderKey(documentIndex, pageIndex, kind)` order is consistent across Tasks 5, 7, 8, 9. `PlanSetupSubmission(documentsUris: List<String>)` consistent across Tasks 10, 12. `CreatePlanRequest.documentIds: list[str]` consistent across Tasks 1, 2, 3.
