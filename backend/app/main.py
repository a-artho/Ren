from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import os
from contextlib import asynccontextmanager
from pathlib import Path
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from dotenv import load_dotenv
from .models import CreatePlanRequest, GeneratedPlan, PlanStatus, Setup
from .pdf_parser import parse_pdf_metadata
from .planner import build_master_plan
from .provider import AIProvider, GeminiProvider, PreparedGeminiFile, SourceDocument
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
PREP_TASKS: dict[str, asyncio.Task] = {}
PREPARE_UPLOAD_SEMAPHORE: asyncio.Semaphore | None = None
PREPARE_UPLOAD_SEMAPHORE_LIMIT: int | None = None
PREPARE_UPLOAD_SEMAPHORE_LOOP: asyncio.AbstractEventLoop | None = None


def gemini_upload_concurrency() -> int:
    try:
        value = int(os.getenv("REN_GEMINI_UPLOAD_CONCURRENCY", "5"))
    except ValueError:
        value = 5
    return max(1, min(value, 5))


def prepare_upload_semaphore() -> asyncio.Semaphore:
    global PREPARE_UPLOAD_SEMAPHORE, PREPARE_UPLOAD_SEMAPHORE_LIMIT, PREPARE_UPLOAD_SEMAPHORE_LOOP
    limit = gemini_upload_concurrency()
    loop = asyncio.get_running_loop()
    if (
        PREPARE_UPLOAD_SEMAPHORE is None
        or PREPARE_UPLOAD_SEMAPHORE_LIMIT != limit
        or PREPARE_UPLOAD_SEMAPHORE_LOOP is not loop
    ):
        PREPARE_UPLOAD_SEMAPHORE = asyncio.Semaphore(limit)
        PREPARE_UPLOAD_SEMAPHORE_LIMIT = limit
        PREPARE_UPLOAD_SEMAPHORE_LOOP = loop
    return PREPARE_UPLOAD_SEMAPHORE


async def ordered_documents(document_ids: list[str]) -> list[SourceDocument]:
    records = STORE.documents_for_ids(document_ids)
    documents: list[SourceDocument] = []
    for index, record in enumerate(records, start=1):
        if not record.path.exists():
            continue
        metadata = await asyncio.to_thread(parse_pdf_metadata, record.path)
        prepared_file = None
        prepared = STORE.document_prepared_file(record.id)
        if prepared is not None:
            prepared_file = PreparedGeminiFile(
                name=prepared.name,
                uri=prepared.uri,
                mime_type=prepared.mime_type or "application/pdf",
            )

        def clear_failed_prepared(error: str, document_id: str = record.id):
            STORE.clear_document_prepared(document_id, error=error[:300])

        documents.append(
            SourceDocument(
                path=record.path,
                filename=record.filename,
                id=record.id,
                source_id=f"doc{index}",
                order=index,
                page_count=metadata.page_count,
                page_anchors=metadata.page_anchors,
                anchors_truncated=metadata.anchors_truncated,
                parser_error=metadata.parser_error,
                prepared_gemini_file=prepared_file,
                pdf_sha256=record.pdf_sha256,
                on_prepared_file_failed=clear_failed_prepared,
            )
        )
    return documents


async def prepare_document(document_id: str, ai_provider: AIProvider):
    if ai_provider is None:
        return
    if STORE.document_prepared_file(document_id) is not None:
        return
    records = STORE.documents_for_ids([document_id])
    if not records:
        return
    record = records[0]
    if not record.path.exists():
        return
    document = SourceDocument(
        path=record.path,
        filename=record.filename,
        id=record.id,
        pdf_sha256=record.pdf_sha256,
    )
    if not ai_provider.should_prepare_document(document):
        return
    prepared = None
    try:
        async with prepare_upload_semaphore():
            prepared = await ai_provider.prepare_document(document)
        if prepared is None:
            return
        if STORE.document_path(document_id) is None:
            await ai_provider.delete_prepared_file(prepared)
            return
        STORE.set_document_prepared(
            document_id,
            name=prepared.name,
            uri=prepared.uri,
            mime_type=prepared.mime_type or "application/pdf",
        )
    except asyncio.CancelledError:
        raise
    except Exception as exc:
        logger.warning("Gemini file preparation failed for document %s", document_id, exc_info=True)
        if prepared is not None:
            try:
                await ai_provider.delete_prepared_file(prepared)
            except Exception:
                logger.warning(
                    "Failed to delete prepared Gemini file after preparation failure for document %s",
                    document_id,
                    exc_info=True,
                )
        STORE.clear_document_prepared(document_id, error=str(exc)[:300])


def schedule_document_preparation(document_id: str):
    if provider is None:
        return None
    ai_provider = provider
    existing = PREP_TASKS.get(document_id)
    if existing and not existing.done():
        return existing
    task = asyncio.create_task(prepare_document(document_id, ai_provider))
    PREP_TASKS[document_id] = task

    def remove_completed(completed: asyncio.Task, key: str = document_id):
        if PREP_TASKS.get(key) is completed:
            PREP_TASKS.pop(key, None)

    task.add_done_callback(remove_completed)
    return task


async def wait_for_document_preparation(document_ids: list[str]):
    tasks = [PREP_TASKS[document_id] for document_id in document_ids if document_id in PREP_TASKS]
    if tasks:
        await asyncio.gather(*tasks, return_exceptions=True)


async def cleanup_document(document_id: str, active_plan_id: str | None = None):
    if STORE.document_has_active_plan(document_id, exclude_plan_id=active_plan_id):
        return
    task = PREP_TASKS.pop(document_id, None)
    if task and not task.done():
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            logger.debug("Gemini preparation task cancelled for document %s", document_id)
        except Exception:
            logger.warning("Gemini preparation task failed during cleanup for document %s", document_id, exc_info=True)
    prepared = STORE.document_prepared_file(document_id, max_age_hours=None)
    path = STORE.delete_document(document_id)
    if prepared is not None and provider is not None:
        await provider.delete_prepared_file(
            PreparedGeminiFile(name=prepared.name, uri=prepared.uri, mime_type=prepared.mime_type)
        )
    if path:
        path.unlink(missing_ok=True)


def schedule(plan_id: str):
    task = asyncio.create_task(process(plan_id))
    TASKS[plan_id] = task

    def remove_completed(completed: asyncio.Task, key: str = plan_id):
        if TASKS.get(key) is completed:
            TASKS.pop(key, None)

    task.add_done_callback(remove_completed)
    return task

async def process(plan_id: str):
    row = STORE.get(plan_id)
    if not row: return
    document_ids = json.loads(row.document_ids)
    try:
        await wait_for_document_preparation(document_ids)
        documents = await ordered_documents(document_ids)
        if len(documents) != len(document_ids):
            raise ValueError("One or more requested documents are missing")
        STORE.set_status(plan_id, PlanStatus.IDENTIFYING_TOPICS)
        if provider is None: raise RuntimeError("AI provider is not configured")
        result = None
        for attempt in range(2):
            try:
                raw_result = await provider.create_plan(documents, Setup.model_validate_json(row.setup_json))
                STORE.set_status(plan_id, PlanStatus.CREATING_BLOCKS)
                result = build_master_plan(raw_result, documents, Setup.model_validate_json(row.setup_json))
                break
            except Exception:
                logger.warning("Attempt %d failed", attempt + 1, exc_info=True)
                if attempt == 1: raise
        STORE.set_status(plan_id, PlanStatus.FINALIZING)
        STORE.set_status(plan_id, PlanStatus.COMPLETED, result=result)
    except asyncio.CancelledError:
        # The cancel endpoint marks user cancellations before cancelling the task.
        # Infrastructure shutdown must leave other jobs resumable on next startup.
        raise
    except Exception as exc:
        logger.exception("Plan %s generation failed", plan_id)
        STORE.set_status(plan_id, PlanStatus.FAILED, error=str(exc)[:300])
    finally:
        row = STORE.get(plan_id)
        if row and row.status in (PlanStatus.COMPLETED, PlanStatus.FAILED, PlanStatus.CANCELED):
            for did in json.loads(row.document_ids):
                await cleanup_document(did, active_plan_id=plan_id)

@asynccontextmanager
async def lifespan(app: FastAPI):
    global provider
    UPLOADS.mkdir(parents=True, exist_ok=True)
    key = os.getenv("GEMINI_API_KEY")
    if key: provider = GeminiProvider(key, os.getenv("GEMINI_MODEL", "gemini-2.5-flash"))
    for document_id in STORE.abandoned_document_ids(max_age_hours=24):
        await cleanup_document(document_id)
    for plan_id in STORE.pending_ids(): schedule(plan_id)
    try:
        yield
    finally:
        tasks = [
            *[task for task in TASKS.values() if not task.done()],
            *[task for task in PREP_TASKS.values() if not task.done()],
        ]
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

app = FastAPI(title="Study Planner API", lifespan=lifespan)

@app.post("/documents", status_code=201)
async def upload_document(
    file: UploadFile = File(...),
    requestId: str | None = Form(default=None),
):
    if file.content_type != "application/pdf": raise HTTPException(415, "A PDF is required")
    if requestId is not None and not 1 <= len(requestId) <= 128:
        raise HTTPException(422, "Invalid requestId")
    if requestId:
        existing_id = STORE.document_id_for_request(requestId)
        if existing_id:
            existing_path = STORE.document_path(existing_id)
            if existing_path and existing_path.exists():
                schedule_document_preparation(existing_id)
                await file.close()
                return {"documentId": existing_id}
            if STORE.document_has_active_plan(existing_id):
                await file.close()
                raise HTTPException(409, "Document upload is used by an active plan")
            await cleanup_document(existing_id)
    UPLOADS.mkdir(parents=True, exist_ok=True)
    path = UPLOADS / f"upload-{os.urandom(12).hex()}.pdf"
    filename = Path((file.filename or "document.pdf").replace("\\", "/")).name or "document.pdf"
    size = 0
    digest = hashlib.sha256()
    try:
        with path.open("wb") as output:
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > 25 * 1024 * 1024:
                    raise HTTPException(413, "PDF is too large")
                digest.update(chunk)
                output.write(chunk)
        with path.open("rb") as uploaded:
            header = uploaded.read(5)
        if size < 5 or header != b"%PDF-":
            raise HTTPException(422, "Invalid PDF")
        document_id = STORE.add_document(
            path,
            request_id=requestId,
            filename=filename,
            pdf_sha256=digest.hexdigest(),
        )
        schedule_document_preparation(document_id)
        return {"documentId": document_id}
    except Exception:
        path.unlink(missing_ok=True)
        raise
    finally:
        await file.close()

@app.post("/plans", status_code=202)
async def create_plan(request: CreatePlanRequest):
    existing = STORE.plan_id_for_request(request.requestId)
    if existing: return {"planId": existing}
    for doc_id in request.documentIds:
        path = STORE.document_path(doc_id)
        if not path or not path.exists():
            raise HTTPException(404, f"Document {doc_id} not found")
    plan_id, created = STORE.create_plan(request)
    if created: schedule(plan_id)
    return {"planId": plan_id}


@app.delete("/documents/{document_id}")
async def delete_document(document_id: str):
    path = STORE.document_path(document_id)
    if path is None:
        raise HTTPException(404, "Document not found")
    if STORE.document_has_active_plan(document_id):
        raise HTTPException(409, "Document is used by an active plan")
    await cleanup_document(document_id)
    return {"documentId": document_id, "deleted": True}


@app.post("/plans/{plan_id}/cancel")
async def cancel_plan(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    document_ids = json.loads(row.document_ids)
    if row.status == PlanStatus.CANCELED:
        return {"planId": plan_id, "status": PlanStatus.CANCELED}
    if row.status in (PlanStatus.COMPLETED, PlanStatus.FAILED):
        raise HTTPException(409, "Plan is already finished")
    STORE.set_status(plan_id, PlanStatus.CANCELED)
    task = TASKS.get(plan_id)
    if task and not task.done():
        task.cancel()
        try:
            await task
        except asyncio.CancelledError:
            logger.debug("Plan %s generation task cancelled", plan_id)
    else:
        for did in document_ids:
            await cleanup_document(did, active_plan_id=plan_id)
    return {"planId": plan_id, "status": PlanStatus.CANCELED}

@app.get("/plans/{plan_id}/status")
def plan_status(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    return {"planId": plan_id, "status": row.status, "error": row.error}

@app.get("/plans/{plan_id}")
def get_plan(plan_id: str):
    row = STORE.get(plan_id)
    if not row: raise HTTPException(404, "Plan not found")
    if row.status != PlanStatus.COMPLETED: raise HTTPException(409, "Plan is not ready")
    result = GeneratedPlan.model_validate_json(row.result_json)
    document_ids = json.loads(row.document_ids)
    dumped = result.model_dump(mode="json")
    return {"planId": plan_id, "documentIds": document_ids,
            "planVersion": result.planVersion,
            "title": result.title,
            "sourceDocuments": dumped.get("sourceDocuments", []),
            "extractionWarnings": dumped.get("extractionWarnings", []),
            "topics": dumped["topics"],
            "blocks": dumped["blocks"]}
