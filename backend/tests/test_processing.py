import asyncio
from pathlib import Path
import pytest

from app import main
from app.models import CreatePlanRequest, GeneratedPlan, PlanStatus, Setup
from app.provider import AIProvider, SourceDocument
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
            "blocks": [{"id": "b1", "title": "Block", "order": 1, "durationMinutes": 20,
                        "instructions": "Review the topic.", "topicIds": ["t1"]}],
        })


class SlowProvider(AIProvider):
    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
        await asyncio.sleep(60)
        raise AssertionError("Provider should have been cancelled")


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
async def test_processing_orders_documents_by_numbered_filenames(tmp_path, monkeypatch):
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

    assert [document.filename for document in fake.documents] == ["Lecture 1.pdf", "Lecture 5.pdf"]

