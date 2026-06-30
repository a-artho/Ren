import hashlib

from fastapi.testclient import TestClient

from app import main
from app.store import Store


async def no_processing(_plan_id: str):
    return None


def client(tmp_path, monkeypatch):
    monkeypatch.delenv("GEMINI_API_KEY", raising=False)
    monkeypatch.setattr(main, "STORE", Store(tmp_path / "ren.db"))
    monkeypatch.setattr(main, "UPLOADS", tmp_path / "uploads")
    monkeypatch.setattr(main, "process", no_processing)
    monkeypatch.setattr(main, "provider", None)
    return TestClient(main.app)


def setup(document_id, request_id="request-1"):
    return {"documentIds": [document_id], "requestId": request_id, "setup": {
        "goal": "PrepareForExam", "planTitle": "HCI final", "deadline": "InOneWeek", "deadlineDate": None,
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


def test_document_upload_rejects_missing_active_idempotent_document(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        first = api.post(
            "/documents",
            data={"requestId": "upload-request-1"},
            files={"file": ("first.pdf", b"%PDF-first", "application/pdf")},
        )
        document_id = first.json()["documentId"]
        api.post("/plans", json=setup(document_id))
        path = main.STORE.document_path(document_id)
        assert path is not None
        path.unlink()

        retry = api.post(
            "/documents",
            data={"requestId": "upload-request-1"},
            files={"file": ("retry.pdf", b"%PDF-retry", "application/pdf")},
        )

        assert retry.status_code == 409
        assert main.STORE.document_path(document_id) == path
        assert list(main.UPLOADS.glob("*.pdf")) == []


def test_document_upload_stores_original_filename(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        body = b"%PDF-test"
        uploaded = api.post(
            "/documents",
            files={"file": ("Lecture 05 - Factoring.pdf", body, "application/pdf")},
        )

        assert uploaded.status_code == 201
        document_id = uploaded.json()["documentId"]
        document = main.STORE.documents_for_ids([document_id])[0]
        assert document.filename == "Lecture 05 - Factoring.pdf"
        assert document.pdf_sha256 == hashlib.sha256(body).hexdigest()


def test_upload_rejects_wrong_type_and_invalid_pdf(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        assert api.post("/documents", files={"file": ("x.txt", b"hello", "text/plain")}).status_code == 415
        assert api.post("/documents", files={"file": ("x.pdf", b"hello", "application/pdf")}).status_code == 422


def test_upload_rejects_invalid_request_id_length(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        empty = api.post(
            "/documents",
            data={"requestId": ""},
            files={"file": ("x.pdf", b"%PDF-test", "application/pdf")},
        )
        too_long = api.post(
            "/documents",
            data={"requestId": "x" * 129},
            files={"file": ("x.pdf", b"%PDF-test", "application/pdf")},
        )

        assert empty.status_code == 422
        assert too_long.status_code == 422


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


def test_delete_document_rejects_active_plan_document(tmp_path, monkeypatch):
    with client(tmp_path, monkeypatch) as api:
        uploaded = api.post("/documents", files={"file": ("lesson.pdf", b"%PDF-test", "application/pdf")})
        document_id = uploaded.json()["documentId"]
        api.post("/plans", json=setup(document_id))

        deleted = api.delete(f"/documents/{document_id}")

        assert deleted.status_code == 409
        assert main.STORE.document_path(document_id) is not None


def test_delete_document_removes_prepared_gemini_file(tmp_path, monkeypatch):
    class FakeProvider:
        def __init__(self):
            self.deleted = []

        async def delete_prepared_file(self, prepared_file):
            self.deleted.append(prepared_file.name)

    with client(tmp_path, monkeypatch) as api:
        uploaded = api.post("/documents", files={"file": ("lesson.pdf", b"%PDF-test", "application/pdf")})
        document_id = uploaded.json()["documentId"]
        fake = FakeProvider()
        monkeypatch.setattr(main, "provider", fake)
        main.STORE.set_document_prepared(
            document_id,
            name="files/prepared",
            uri="https://example.test/prepared",
            mime_type="application/pdf",
        )

        deleted = api.delete(f"/documents/{document_id}")

        assert deleted.status_code == 200
        assert deleted.json() == {"documentId": document_id, "deleted": True}
        assert main.STORE.document_path(document_id) is None
        assert fake.deleted == ["files/prepared"]


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
                "id": "b1", "title": "Block 1", "order": 1,
                "effortMinMinutes": 20, "effortLikelyMinutes": 30, "effortMaxMinutes": 40,
                "instructions": "Read", "topicIds": ["t1"],
                "taskType": "REVIEW",
            }],
            extractionWarnings=[{
                "type": "SOURCE_ORDER_CONFLICT",
                "message": "Source order was adjusted.",
                "blockId": "b1",
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
        assert data["extractionWarnings"][0]["type"] == "SOURCE_ORDER_CONFLICT"
        assert "totalEstimatedMinutes" not in data

