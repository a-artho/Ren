from pathlib import Path
import pytest

from app import main
from app.models import CreatePlanRequest, GeneratedPlan, PlanStatus, Setup
from app.provider import AIProvider
from app.store import Store


class FakeProvider(AIProvider):
    def __init__(self, failures=0): self.failures, self.calls = failures, 0
    async def create_plan(self, pdf: Path, setup: Setup) -> GeneratedPlan:
        self.calls += 1
        if self.calls <= self.failures: raise ValueError("invalid provider output")
        return GeneratedPlan.model_validate({
            "topics": [{"id": "t1", "title": "Topic", "order": 1}],
            "blocks": [{"id": "b1", "title": "Block", "order": 1, "durationMinutes": 20,
                        "instructions": "Review the topic.", "topicIds": ["t1"]}],
        })


def create_job(tmp_path, monkeypatch, provider):
    store = Store(tmp_path / "ren.db"); monkeypatch.setattr(main, "STORE", store); monkeypatch.setattr(main, "provider", provider)
    pdf = tmp_path / "document.pdf"; pdf.write_bytes(b"%PDF-test")
    document_id = store.add_document(pdf)
    request = CreatePlanRequest(documentId=document_id, requestId="request", setup={
        "goal": "LearnThoroughly", "deadline": "InOneWeek", "dailyStudyMinutes": 20, "studyDays": ["Monday"]})
    plan_id, _ = store.create_plan(request)
    return store, pdf, plan_id


@pytest.mark.asyncio
async def test_processing_retries_once_then_completes_and_deletes_pdf(tmp_path, monkeypatch):
    fake = FakeProvider(failures=1); store, pdf, plan_id = create_job(tmp_path, monkeypatch, fake)
    await main.process(plan_id)
    assert fake.calls == 2
    assert store.get(plan_id)[2] == PlanStatus.COMPLETED
    assert not pdf.exists()


@pytest.mark.asyncio
async def test_processing_fails_safely_after_second_error(tmp_path, monkeypatch):
    fake = FakeProvider(failures=2); store, pdf, plan_id = create_job(tmp_path, monkeypatch, fake)
    await main.process(plan_id)
    row = store.get(plan_id)
    assert fake.calls == 2
    assert row[2] == PlanStatus.FAILED
    assert row[4] == "invalid provider output"
    assert not pdf.exists()
    assert store.document_path(row[0]) is None

