import json
from pathlib import Path

from app.models import CreatePlanRequest, GeneratedPlan, PlanStatus
from app.store import Store


def request(request_id="request-1", document_id="doc-1"):
    return CreatePlanRequest.model_validate({
        "documentIds": [document_id], "requestId": request_id,
        "setup": {"goal": "LearnThoroughly", "deadline": "InOneWeek", "dailyStudyMinutes": 30, "studyDays": ["Monday"]},
    })


def generated_plan():
    return GeneratedPlan.model_validate({
        "topics": [{"id": "t1", "title": "Foundations", "order": 1}],
        "blocks": [{"id": "b1", "title": "Read", "order": 1, "durationMinutes": 30,
                    "instructions": "Read and summarize.", "topicIds": ["t1"]}],
    })


def test_store_persists_document_idempotent_job_and_result(tmp_path: Path):
    store = Store(tmp_path / "ren.db")
    pdf = tmp_path / "doc.pdf"; pdf.write_bytes(b"%PDF-test")
    document_id = store.add_document(pdf)
    value = request(document_id=document_id)

    plan_id, created = store.create_plan(value)
    duplicate_id, duplicate_created = store.create_plan(value)
    store.set_status(plan_id, PlanStatus.COMPLETED, generated_plan())

    assert created is True
    assert duplicate_created is False
    assert duplicate_id == plan_id
    assert store.document_path(document_id) == pdf
    row = store.get(plan_id)
    assert row.status == PlanStatus.COMPLETED
    assert GeneratedPlan.model_validate_json(row.result_json) == generated_plan()


def test_pending_ids_excludes_terminal_jobs(tmp_path: Path):
    store = Store(tmp_path / "ren.db")
    first, _ = store.create_plan(request("one"))
    second, _ = store.create_plan(request("two"))
    store.set_status(second, PlanStatus.FAILED, error="PLAN_GENERATION_FAILED")
    assert store.pending_ids() == [first]


def test_store_reuses_document_for_upload_request(tmp_path: Path):
    store = Store(tmp_path / "ren.db")
    pdf = tmp_path / "doc.pdf"
    pdf.write_bytes(b"%PDF-test")

    document_id = store.add_document(pdf, request_id="upload-request")

    assert store.document_id_for_request("upload-request") == document_id


def test_store_finds_only_old_unclaimed_documents(tmp_path: Path):
    store = Store(tmp_path / "ren.db")
    old_pdf = tmp_path / "old.pdf"
    claimed_pdf = tmp_path / "claimed.pdf"
    old_pdf.write_bytes(b"%PDF-old")
    claimed_pdf.write_bytes(b"%PDF-claimed")
    old_id = store.add_document(old_pdf)
    claimed_id = store.add_document(claimed_pdf)
    claimed_request = request("claimed-request", document_id=claimed_id)
    store.create_plan(claimed_request)
    with store.connect() as db:
        db.execute("UPDATE documents SET created_at='2000-01-01 00:00:00'")

    assert store.abandoned_document_ids(max_age_hours=24) == [old_id]


def test_create_plan_with_multiple_documents(tmp_path: Path):
    store = Store(tmp_path / "ren.db")
    p1 = tmp_path / "a.pdf"; p1.write_bytes(b"%PDF-a")
    p2 = tmp_path / "b.pdf"; p2.write_bytes(b"%PDF-b")
    d1 = store.add_document(p1, request_id="r1-0")
    d2 = store.add_document(p2, request_id="r1-1")
    req = CreatePlanRequest.model_validate({
        "documentIds": [d1, d2], "requestId": "plan-1",
        "setup": {"goal": "LearnThoroughly", "deadline": "InOneWeek", "dailyStudyMinutes": 30, "studyDays": ["Monday"]},
    })
    plan_id, created = store.create_plan(req)
    assert created
    row = store.get(plan_id)
    assert json.loads(row.document_ids) == [d1, d2]


def test_abandoned_ignores_multi_doc_plan(tmp_path: Path):
    store = Store(tmp_path / "ren.db")
    p1 = tmp_path / "a.pdf"; p1.write_bytes(b"%PDF-a")
    p2 = tmp_path / "b.pdf"; p2.write_bytes(b"%PDF-b")
    d1 = store.add_document(p1)
    d2 = store.add_document(p2)
    req = CreatePlanRequest.model_validate({
        "documentIds": [d1, d2], "requestId": "multi-plan",
        "setup": {"goal": "LearnThoroughly", "deadline": "InOneWeek", "dailyStudyMinutes": 30, "studyDays": ["Monday"]},
    })
    store.create_plan(req)
    with store.connect() as db:
        db.execute("UPDATE documents SET created_at='2000-01-01 00:00:00'")
    assert store.abandoned_document_ids(max_age_hours=24) == []

