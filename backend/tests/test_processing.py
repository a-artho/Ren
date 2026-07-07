import asyncio
import pytest

from app import main
from app.models import CreatePlanRequest, GeneratedPlan, PlanStatus, Setup
from app.provider import AIProvider, PreparedGeminiFile, SourceDocument
from app.store import Store


class FakeProvider(AIProvider):
    def __init__(self, failures=0):
        self.failures, self.calls = failures, 0
        self.documents: list[SourceDocument] = []
    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
        self.calls += 1
        self.documents = documents
        if self.calls <= self.failures: raise ValueError("invalid provider output")
        return GeneratedPlan.model_validate({
            "topics": [{"id": "t1", "title": "Topic", "order": 1}],
            "blocks": [{"id": "b1", "title": "Block", "order": 1,
                        "effortMinMinutes": 10, "effortLikelyMinutes": 20, "effortMaxMinutes": 30,
                        "instructions": "Review the topic.", "topicIds": ["t1"]}],
        })


class SlowProvider(AIProvider):
    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
        await asyncio.sleep(60)
        raise AssertionError("Provider should have been cancelled")


class SlowPrepareProvider(AIProvider):
    def __init__(self):
        self.current = 0
        self.max_seen = 0

    async def prepare_document(self, document: SourceDocument) -> PreparedGeminiFile:
        self.current += 1
        self.max_seen = max(self.max_seen, self.current)
        await asyncio.sleep(0.03)
        self.current -= 1
        return PreparedGeminiFile(
            name=f"files/{document.filename}",
            uri=f"https://example.test/{document.filename}",
            mime_type="application/pdf",
        )

    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
        raise AssertionError("Only document preparation should run")


class CachedPrepareProvider(AIProvider):
    def __init__(self):
        self.prepared = False

    def should_prepare_document(self, document: SourceDocument) -> bool:
        return False

    async def prepare_document(self, document: SourceDocument) -> PreparedGeminiFile:
        self.prepared = True
        raise AssertionError("Cached documents should not be pre-uploaded")

    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
        raise AssertionError("Only document preparation should run")


def create_job(tmp_path, monkeypatch, provider):
    store = Store(tmp_path / "ren.db"); monkeypatch.setattr(main, "STORE", store); monkeypatch.setattr(main, "provider", provider)
    pdf = tmp_path / "document.pdf"; pdf.write_bytes(b"%PDF-test")
    document_id = store.add_document(pdf)
    request = CreatePlanRequest(documentIds=[document_id], requestId="request", setup={
        "goal": "PrepareForExam", "planTitle": "HCI final", "deadline": "InOneWeek", "dailyStudyMinutes": 20, "studyDays": ["Monday"]})
    plan_id, _ = store.create_plan(request)
    return store, pdf, plan_id


@pytest.mark.asyncio
async def test_processing_retries_once_then_completes_and_deletes_pdf(tmp_path, monkeypatch):
    fake = FakeProvider(failures=1); store, pdf, plan_id = create_job(tmp_path, monkeypatch, fake)
    await main.process(plan_id)
    assert fake.calls == 2
    assert store.get(plan_id).status == PlanStatus.COMPLETED
    assert not pdf.exists()


@pytest.mark.asyncio
async def test_processing_fails_safely_after_second_error(tmp_path, monkeypatch):
    import json
    fake = FakeProvider(failures=2); store, pdf, plan_id = create_job(tmp_path, monkeypatch, fake)
    await main.process(plan_id)
    row = store.get(plan_id)
    assert fake.calls == 2
    assert row.status == PlanStatus.FAILED
    assert row.error == "invalid provider output"
    assert not pdf.exists()
    assert store.document_path(json.loads(row.document_ids)[0]) is None


@pytest.mark.asyncio
async def test_infrastructure_cancellation_preserves_pending_job(tmp_path, monkeypatch):
    import json
    store, pdf, plan_id = create_job(tmp_path, monkeypatch, SlowProvider())
    task = asyncio.create_task(main.process(plan_id))
    await asyncio.sleep(0)

    task.cancel()
    with pytest.raises(asyncio.CancelledError):
        await task

    row = store.get(plan_id)
    assert row.status != PlanStatus.CANCELED
    assert store.document_path(json.loads(row.document_ids)[0]) == pdf
    assert pdf.exists()


@pytest.mark.asyncio
async def test_processing_preserves_requested_document_order(tmp_path, monkeypatch):
    store = Store(tmp_path / "ren.db")
    monkeypatch.setattr(main, "STORE", store)
    fake = FakeProvider()
    monkeypatch.setattr(main, "provider", fake)
    lecture_5 = tmp_path / "lecture-five.pdf"; lecture_5.write_bytes(b"%PDF-five")
    lecture_1 = tmp_path / "lecture-one.pdf"; lecture_1.write_bytes(b"%PDF-one")
    doc_5 = store.add_document(lecture_5, filename="Lecture 5.pdf")
    doc_1 = store.add_document(lecture_1, filename="Lecture 1.pdf")
    request = CreatePlanRequest(documentIds=[doc_5, doc_1], requestId="ordered-request", setup={
        "goal": "PrepareForExam", "planTitle": "HCI final", "deadline": "InOneWeek", "dailyStudyMinutes": 20, "studyDays": ["Monday"]})
    plan_id, _ = store.create_plan(request)

    await main.process(plan_id)

    assert [document.filename for document in fake.documents] == ["Lecture 5.pdf", "Lecture 1.pdf"]


@pytest.mark.asyncio
async def test_document_preparation_limits_concurrent_gemini_uploads(tmp_path, monkeypatch):
    store = Store(tmp_path / "ren.db")
    monkeypatch.setattr(main, "STORE", store)
    monkeypatch.setenv("REN_GEMINI_UPLOAD_CONCURRENCY", "2")
    monkeypatch.setattr(main, "PREPARE_UPLOAD_SEMAPHORE", None)
    monkeypatch.setattr(main, "PREPARE_UPLOAD_SEMAPHORE_LIMIT", None)
    monkeypatch.setattr(main, "PREPARE_UPLOAD_SEMAPHORE_LOOP", None)
    provider = SlowPrepareProvider()
    document_ids = []
    for index in range(6):
        pdf = tmp_path / f"lecture-{index}.pdf"
        pdf.write_bytes(b"%PDF-test")
        document_ids.append(store.add_document(pdf, filename=f"Lecture {index}.pdf"))

    await asyncio.gather(*(main.prepare_document(document_id, provider) for document_id in document_ids))

    assert provider.max_seen == 2
    assert all(store.document_prepared_file(document_id) is not None for document_id in document_ids)


@pytest.mark.asyncio
async def test_preparation_skips_documents_when_semantic_cache_is_warm(tmp_path, monkeypatch):
    store = Store(tmp_path / "ren.db")
    monkeypatch.setattr(main, "STORE", store)
    provider = CachedPrepareProvider()
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    document_id = store.add_document(pdf, filename="Lecture.pdf", pdf_sha256="hash-123")

    await main.prepare_document(document_id, provider)

    assert provider.prepared is False
    assert store.document_prepared_file(document_id) is None


@pytest.mark.asyncio
async def test_preparation_deletes_gemini_file_if_store_update_fails(tmp_path, monkeypatch):
    class TrackingPrepareProvider(AIProvider):
        def __init__(self):
            self.deleted = []

        async def prepare_document(self, document: SourceDocument) -> PreparedGeminiFile:
            return PreparedGeminiFile(
                name=f"files/{document.filename}",
                uri=f"https://example.test/{document.filename}",
                mime_type="application/pdf",
            )

        async def delete_prepared_file(self, prepared_file: PreparedGeminiFile):
            self.deleted.append(prepared_file.name)

        async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
            raise AssertionError("Only document preparation should run")

    store = Store(tmp_path / "ren.db")
    monkeypatch.setattr(main, "STORE", store)
    provider = TrackingPrepareProvider()
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    document_id = store.add_document(pdf, filename="Lecture.pdf")

    def fail_set_document_prepared(*args, **kwargs):
        raise RuntimeError("database write failed")

    monkeypatch.setattr(store, "set_document_prepared", fail_set_document_prepared)

    await main.prepare_document(document_id, provider)

    assert provider.deleted == ["files/Lecture.pdf"]
    assert store.document_prepared_file(document_id) is None


@pytest.mark.asyncio
async def test_cleanup_cancels_in_flight_preparation_before_deleting_document(tmp_path, monkeypatch):
    store = Store(tmp_path / "ren.db")
    monkeypatch.setattr(main, "STORE", store)
    monkeypatch.setattr(main, "PREP_TASKS", {})
    monkeypatch.setattr(main, "provider", None)
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    document_id = store.add_document(pdf, filename="Lecture.pdf")
    cancelled = asyncio.Event()

    async def slow_prepare():
        try:
            await asyncio.sleep(60)
        except asyncio.CancelledError:
            cancelled.set()
            raise

    task = asyncio.create_task(slow_prepare())
    main.PREP_TASKS[document_id] = task
    await asyncio.sleep(0)

    await main.cleanup_document(document_id)

    assert cancelled.is_set()
    assert task.cancelled()
    assert store.document_path(document_id) is None
    assert not pdf.exists()


@pytest.mark.asyncio
async def test_lifespan_shutdown_cancels_preparation_tasks(tmp_path, monkeypatch):
    store = Store(tmp_path / "ren.db")
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.setattr(main, "STORE", store)
    monkeypatch.setattr(main, "UPLOADS", tmp_path / "uploads")
    monkeypatch.setattr(main, "TASKS", {})
    monkeypatch.setattr(main, "PREP_TASKS", {})
    cancelled = asyncio.Event()

    async def slow_prepare():
        try:
            await asyncio.sleep(60)
        except asyncio.CancelledError:
            cancelled.set()
            raise

    task = asyncio.create_task(slow_prepare())
    main.PREP_TASKS["doc-1"] = task
    await asyncio.sleep(0)

    async with main.lifespan(main.app):
        pass

    assert cancelled.is_set()
    assert task.cancelled()


@pytest.mark.asyncio
async def test_processing_fails_when_a_requested_document_file_is_missing(tmp_path, monkeypatch):
    fake = FakeProvider()
    store, pdf, plan_id = create_job(tmp_path, monkeypatch, fake)
    pdf.unlink()

    await main.process(plan_id)

    row = store.get(plan_id)
    assert fake.calls == 0
    assert row.status == PlanStatus.FAILED
    assert row.error == "One or more requested documents are missing"

