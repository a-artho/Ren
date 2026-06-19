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
            CREATE TABLE IF NOT EXISTS documents(id TEXT PRIMARY KEY, path TEXT NOT NULL, created_at TEXT DEFAULT CURRENT_TIMESTAMP);
            CREATE TABLE IF NOT EXISTS plans(id TEXT PRIMARY KEY, request_id TEXT UNIQUE NOT NULL, document_id TEXT NOT NULL,
              setup_json TEXT NOT NULL, status TEXT NOT NULL, result_json TEXT, error TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP);
            """)

    def connect(self): return sqlite3.connect(self.db_path)
    def add_document(self, path: Path) -> str:
        document_id = str(uuid4())
        with self.connect() as db: db.execute("INSERT INTO documents(id,path) VALUES(?,?)", (document_id, str(path)))
        return document_id
    def document_path(self, document_id: str) -> Path | None:
        with self.connect() as db: row = db.execute("SELECT path FROM documents WHERE id=?", (document_id,)).fetchone()
        return Path(row[0]) if row else None
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
        terminal = (PlanStatus.COMPLETED, PlanStatus.FAILED)
        with self.connect() as db: return [r[0] for r in db.execute("SELECT id FROM plans WHERE status NOT IN (?,?)", terminal).fetchall()]

