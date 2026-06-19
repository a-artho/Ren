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
    return {"documentId": document_id, "requestId": request_id, "setup": {
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

