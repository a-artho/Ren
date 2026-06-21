from __future__ import annotations

import json, sqlite3
from pathlib import Path
from uuid import uuid4
from .models import CreatePlanRequest, GeneratedPlan, PlanStatus


class Store:
    def __init__(self, db_path: Path):
        self.db_path = db_path
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript("""
            CREATE TABLE IF NOT EXISTS documents(id TEXT PRIMARY KEY, path TEXT NOT NULL, request_id TEXT,
              created_at TEXT DEFAULT CURRENT_TIMESTAMP);
            CREATE TABLE IF NOT EXISTS plans(id TEXT PRIMARY KEY, request_id TEXT UNIQUE NOT NULL, document_id TEXT NOT NULL,
              setup_json TEXT NOT NULL, status TEXT NOT NULL, result_json TEXT, error TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP);
            """)
            columns = {row[1] for row in db.execute("PRAGMA table_info(documents)")}
            if "request_id" not in columns:
                db.execute("ALTER TABLE documents ADD COLUMN request_id TEXT")
            db.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS documents_request_id ON documents(request_id)"
            )

    def connect(self): return sqlite3.connect(self.db_path)
    def add_document(self, path: Path, request_id: str | None = None) -> str:
        document_id = str(uuid4())
        with self.connect() as db:
            db.execute(
                "INSERT OR IGNORE INTO documents(id,path,request_id) VALUES(?,?,?)",
                (document_id, str(path), request_id),
            )
            if request_id is None:
                return document_id
            row = db.execute(
                "SELECT id,path FROM documents WHERE request_id=?",
                (request_id,),
            ).fetchone()
        if row[1] != str(path):
            path.unlink(missing_ok=True)
        return row[0]
    def document_id_for_request(self, request_id: str) -> str | None:
        with self.connect() as db:
            row = db.execute("SELECT id FROM documents WHERE request_id=?", (request_id,)).fetchone()
        return row[0] if row else None
    def document_path(self, document_id: str) -> Path | None:
        with self.connect() as db: row = db.execute("SELECT path FROM documents WHERE id=?", (document_id,)).fetchone()
        return Path(row[0]) if row else None
    def delete_document(self, document_id: str) -> Path | None:
        path = self.document_path(document_id)
        with self.connect() as db: db.execute("DELETE FROM documents WHERE id=?", (document_id,))
        return path
    def plan_id_for_request(self, request_id: str) -> str | None:
        with self.connect() as db:
            row = db.execute("SELECT id FROM plans WHERE request_id=?", (request_id,)).fetchone()
        return row[0] if row else None
    def plan_document_id_for_request(self, request_id: str) -> str | None:
        with self.connect() as db:
            row = db.execute(
                "SELECT document_id FROM plans WHERE request_id=?",
                (request_id,),
            ).fetchone()
        return row[0] if row else None
    def create_plan(self, request: CreatePlanRequest) -> tuple[str, bool]:
        with self.connect() as db:
            existing = db.execute("SELECT id FROM plans WHERE request_id=?", (request.requestId,)).fetchone()
            if existing: return existing[0], False
            plan_id = str(uuid4())
            db.execute("INSERT INTO plans(id,request_id,document_id,setup_json,status) VALUES(?,?,?,?,?)",
                       (plan_id, request.requestId, request.documentId, request.setup.model_dump_json(), PlanStatus.ANALYZING))
        return plan_id, True
    def get(self, plan_id: str):
        with self.connect() as db:
            row = db.execute("SELECT document_id,setup_json,status,result_json,error FROM plans WHERE id=?", (plan_id,)).fetchone()
        return row
    def set_status(self, plan_id: str, status: PlanStatus, result: GeneratedPlan | None = None, error: str | None = None):
        with self.connect() as db: db.execute("UPDATE plans SET status=?,result_json=?,error=? WHERE id=?",
            (status, result.model_dump_json() if result else None, error, plan_id))
    def pending_ids(self):
        terminal = (PlanStatus.COMPLETED, PlanStatus.FAILED, PlanStatus.CANCELED)
        with self.connect() as db:
            return [r[0] for r in db.execute("SELECT id FROM plans WHERE status NOT IN (?,?,?)", terminal).fetchall()]
    def abandoned_document_ids(self, max_age_hours: int) -> list[str]:
        modifier = f"-{max_age_hours} hours"
        with self.connect() as db:
            return [
                row[0]
                for row in db.execute(
                    """
                    SELECT documents.id
                    FROM documents
                    LEFT JOIN plans ON plans.document_id = documents.id
                    WHERE plans.id IS NULL
                      AND documents.created_at < datetime('now', ?)
                    ORDER BY documents.created_at
                    """,
                    (modifier,),
                ).fetchall()
            ]

