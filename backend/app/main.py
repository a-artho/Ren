from __future__ import annotations

import asyncio, logging, os
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, File, HTTPException, UploadFile
from dotenv import load_dotenv
from .models import CreatePlanRequest, GeneratedPlan, PlanStatus, Setup
from .provider import AIProvider, GeminiProvider
from .store import Store

BACKEND_ROOT = Path(__file__).parents[1]
load_dotenv(BACKEND_ROOT / ".env")
configured_data_dir = Path(os.getenv("REN_DATA_DIR", "data"))
DATA = configured_data_dir if configured_data_dir.is_absolute() else BACKEND_ROOT / configured_data_dir
UPLOADS = DATA / "uploads"
STORE = Store(DATA / "ren.db")
provider: AIProvider | None = None
logger = logging.getLogger("ren")
TASKS: dict[str, asyncio.Task] = {}


def cleanup_document(document_id: str):
    path = STORE.delete_document(document_id)
    if path: path.unlink(missing_ok=True)


def schedule(plan_id: str):
    task = asyncio.create_task(process(plan_id))
    TASKS[plan_id] = task
    task.add_done_callback(lambda completed, key=plan_id: TASKS.pop(key, None))
    return task

async def process(plan_id: str):
    row = STORE.get(plan_id)
    if not row: return
    document_id, setup_json, _, _, _ = row
    path = STORE.document_path(document_id)
    try:
        if not path or not path.exists(): raise ValueError("Document unavailable")
        STORE.set_status(plan_id, PlanStatus.IDENTIFYING_TOPICS)
        if provider is None: raise RuntimeError("AI provider is not configured")
        result = None
        for attempt in range(2):
            try:
                result = await provider.create_plan(path, Setup.model_validate_json(setup_json)); break
            except Exception:
                logger.warning("Attempt %d failed", attempt + 1, exc_info=True)
                if attempt == 1: raise
        STORE.set_status(plan_id, PlanStatus.CREATING_BLOCKS)
        STORE.set_status(plan_id, PlanStatus.FINALIZING)
        STORE.set_status(plan_id, PlanStatus.COMPLETED, result=result)
    except asyncio.CancelledError:
        STORE.set_status(plan_id, PlanStatus.CANCELED)
        raise
    except Exception as exc:
        logger.exception("Plan %s generation failed", plan_id)
        STORE.set_status(plan_id, PlanStatus.FAILED, error=str(exc)[:300])
    finally:
        row = STORE.get(plan_id)
        if row and row[2] in (PlanStatus.COMPLETED, PlanStatus.FAILED, PlanStatus.CANCELED):
            cleanup_document(document_id)

@asynccontextmanager
async def lifespan(app: FastAPI):
    global provider
    UPLOADS.mkdir(parents=True, exist_ok=True)
    key = os.getenv("GEMINI_API_KEY")
    if key: provider = GeminiProvider(key, os.getenv("GEMINI_MODEL", "gemini-2.5-flash"))
    for plan_id in STORE.pending_ids(): schedule(plan_id)
    yield

app = FastAPI(title="Ren API", lifespan=lifespan)

@app.post("/documents", status_code=201)
async def upload_document(file: UploadFile = File(...)):
    if file.content_type != "application/pdf": raise HTTPException(415, "A PDF is required")
    UPLOADS.mkdir(parents=True, exist_ok=True)
    path = UPLOADS / f"upload-{os.urandom(12).hex()}.pdf"
    size = 0
    with path.open("wb") as output:
        while chunk := await file.read(1024 * 1024):
            size += len(chunk)
            if size > 25 * 1024 * 1024:
                output.close(); path.unlink(missing_ok=True); raise HTTPException(413, "PDF is too large")
            output.write(chunk)
    if size < 5 or path.read_bytes()[:5] != b"%PDF-":
        path.unlink(missing_ok=True); raise HTTPException(422, "Invalid PDF")
    return {"documentId": STORE.add_document(path)}

@app.post("/plans", status_code=202)
async def create_plan(request: CreatePlanRequest):
    existing = STORE.plan_id_for_request(request.requestId)
    if existing: return {"planId": existing}
    document_path = STORE.document_path(request.documentId)
    if not document_path or not document_path.exists(): raise HTTPException(404, "Document not found")
    plan_id, created = STORE.create_plan(request)
    if created: schedule(plan_id)
    return {"planId": plan_id}


@app.post("/plans/{plan_id}/cancel")
async def cancel_plan(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    if row[2] == PlanStatus.CANCELED:
        return {"planId": plan_id, "status": PlanStatus.CANCELED}
    if row[2] in (PlanStatus.COMPLETED, PlanStatus.FAILED):
        raise HTTPException(409, "Plan is already finished")
    STORE.set_status(plan_id, PlanStatus.CANCELED)
    task = TASKS.get(plan_id)
    if task and not task.done():
        task.cancel()
        try: await task
        except asyncio.CancelledError: pass
    cleanup_document(row[0])
    return {"planId": plan_id, "status": PlanStatus.CANCELED}

@app.get("/plans/{plan_id}/status")
def plan_status(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    return {"planId": plan_id, "status": row[2], "error": row[4]}

@app.get("/plans/{plan_id}")
def get_plan(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    if row[2] != PlanStatus.COMPLETED: raise HTTPException(409, "Plan is not ready")
    result = GeneratedPlan.model_validate_json(row[3])
    return {"planId": plan_id, "documentId": row[0], "topics": result.model_dump()["topics"],
            "blocks": result.model_dump()["blocks"], "totalEstimatedMinutes": sum(b.durationMinutes for b in result.blocks)}
