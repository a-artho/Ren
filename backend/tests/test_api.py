from fastapi.testclient import TestClient

from app import main
from app.store import Store


async def no_processing(_plan_id: str):
    return None


def client(tmp_path, monkeypatch):
    monkeypatch.setattr(main, "STORE", Store(tmp_path / "ren.db"))
    monkeypatch.setattr(main, "UPLOADS", tmp_path / "uploads")
    monkeypatch.setattr(main, "process", no_processing)
    return TestClient(main.app)


def setup(document_id, request_id="request-1"):
    return {"documentIds": [document_id], "requestId": request_id, "setup": {
        "goal": "LearnThoroughly", "deadline": "InOneWeek", "deadlineDate": None,
        "dailyStudyMinutes": 30, "studyDays": ["Monday", "Wednesday"]}}


def test_document_plan_status_contract_and_idempotency(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        uploaded = api.post("/documents", files={"file": ("lesson.pdf", b"%PDF-test", "application/pdf")})
        assert uploaded.status_code == 201
        document_id = uploaded.json()["documentId"]

        created = api.post("/plans", json=setup(document_id))
        duplicate = api.post("/plans", json=setup(document_id))
        assert created.status_code == 202
        assert duplicate.json()["planId"] == created.json()["planId"]

        status = api.get(f"/plans/{created.json()['planId']}/status")
        assert status.json() == {"planId": created.json()["planId"], "status": "ANALYZING", "error": None}
        assert api.get(f"/plans/{created.json()['planId']}").status_code == 409


def test_document_upload_is_idempotent_by_request_id(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        first = api.post(
            "/documents",
            data={"requestId": "upload-request-1"},
            files={"file": ("first.pdf", b"%PDF-first", "application/pdf")},
        )
        second = api.post(
            "/documents",
            data={"requestId": "upload-request-1"},
            files={"file": ("second.pdf", b"%PDF-second", "application/pdf")},
        )

        assert first.status_code == 201
        assert second.status_code == 201
        assert second.json()["documentId"] == first.json()["documentId"]
        assert len(list(main.UPLOADS.glob("*.pdf"))) == 1


def test_completed_plan_retry_does_not_reupload_deleted_document(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        first = api.post(
            "/documents",
            data={"requestId": "completed-request"},
            files={"file": ("first.pdf", b"%PDF-first", "application/pdf")},
        )
        document_id = first.json()["documentId"]
        api.post("/plans", json=setup(document_id, request_id="completed-request"))
        main.cleanup_document(document_id)

        repeated = api.post(
            "/documents",
            data={"requestId": "completed-request"},
            files={"file": ("second.pdf", b"%PDF-second", "application/pdf")},
        )

        assert repeated.json()["documentId"] == document_id
        assert list(main.UPLOADS.glob("*.pdf")) == []


def test_upload_rejects_wrong_type_and_invalid_pdf(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        assert api.post("/documents", files={"file": ("x.txt", b"hello", "text/plain")}).status_code == 415
        assert api.post("/documents", files={"file": ("x.pdf", b"hello", "application/pdf")}).status_code == 422


def test_unknown_resources_and_invalid_setup_are_rejected(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        assert api.get("/plans/missing/status").status_code == 404
        assert api.get("/plans/missing").status_code == 404
        invalid = setup("missing"); invalid["setup"]["dailyStudyMinutes"] = 0
        assert api.post("/plans", json=invalid).status_code == 422


def test_consumed_document_rejects_new_request_but_preserves_idempotency(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        uploaded = api.post("/documents", files={"file": ("lesson.pdf", b"%PDF-test", "application/pdf")})
        document_id = uploaded.json()["documentId"]
        created = api.post("/plans", json=setup(document_id))
        plan_id = created.json()["planId"]

        path = main.STORE.document_path(document_id)
        assert path is not None
        path.unlink()
        main.STORE.delete_document(document_id)

        duplicate = api.post("/plans", json=setup(document_id))
        fresh = api.post("/plans", json=setup(document_id, request_id="request-2"))

        assert duplicate.status_code == 202
        assert duplicate.json()["planId"] == plan_id
        assert fresh.status_code == 404


def test_cancel_marks_plan_terminal_and_removes_document(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        uploaded = api.post("/documents", files={"file": ("lesson.pdf", b"%PDF-test", "application/pdf")})
        document_id = uploaded.json()["documentId"]
        created = api.post("/plans", json=setup(document_id))
        plan_id = created.json()["planId"]

        cancelled = api.post(f"/plans/{plan_id}/cancel")

        assert cancelled.status_code == 200
        assert cancelled.json() == {"planId": plan_id, "status": "CANCELED"}
        assert api.get(f"/plans/{plan_id}/status").json()["status"] == "CANCELED"
        assert main.STORE.document_path(document_id) is None
        assert api.post(f"/plans/{plan_id}/cancel").status_code == 200


def test_completed_plan_returns_title(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        from app.main import STORE
        from app.models import GeneratedPlan, PlanStatus
        uploaded = api.post("/documents", files={"file": ("lesson.pdf", b"%PDF-test", "application/pdf")})
        document_id = uploaded.json()["documentId"]
        created = api.post("/plans", json=setup(document_id))
        plan_id = created.json()["planId"]

        plan = GeneratedPlan(
            title="Test Subject Matter",
            topics=[{"id": "t1", "title": "Topic 1", "order": 1}],
            blocks=[{
                "id": "b1", "title": "Block 1", "order": 1, "durationMinutes": 30,
                "instructions": "Read", "topicIds": ["t1"],
                "minimumUsefulMinutes": 10, "priority": "MEDIUM", "taskType": "REVIEW",
                "priorityReason": "Foundation", "isSkippable": True,
            }],
        )
        STORE.set_status(plan_id, PlanStatus.COMPLETED, result=plan)

        response = api.get(f"/plans/{plan_id}")
        assert response.status_code == 200
        data = response.json()
        assert data["title"] == "Test Subject Matter"
        assert data["planId"] == plan_id
        assert "topics" in data
        assert "blocks" in data
        assert "totalEstimatedMinutes" in data

