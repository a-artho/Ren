from __future__ import annotations

import asyncio
import hashlib
import json
import logging
import os
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Literal
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field, model_validator

from .models import (
    Difficulty,
    EstimateConfidence,
    ExtractionWarning,
    GeneratedPlan,
    Setup,
    SourceRef,
    StudyBlock,
    StudyTaskStatus,
    StudyTaskType,
    TASK_TYPE_MINIMUMS,
    Topic,
)
from .pdf_parser import PdfPageAnchor

logger = logging.getLogger("ren")
BACKEND_ROOT = Path(__file__).parents[1]
SEMANTIC_EXTRACTION_PROMPT_VERSION = "2026-06-29-semantic-material-groups-v4"
SEMANTIC_EXTRACTION_SCHEMA_VERSION = "semantic-schema-v2"
SEMANTIC_EXTRACTION_CACHE_VERSION = "semantic-cache-v2"
DOCUMENT_CONTEXT_VERSION = "document-context-v3"
GLOBAL_EFFORT_CALIBRATION_PROMPT_VERSION = "2026-06-29-global-effort-calibration-v2"
GLOBAL_EFFORT_CALIBRATION_SCHEMA_VERSION = "global-effort-calibration-schema-v1"
CACHE_DISABLED_VALUES = {"0", "false", "no", "off"}


@dataclass(frozen=True)
class PreparedGeminiFile:
    name: str
    uri: str
    mime_type: str = "application/pdf"


@dataclass(frozen=True)
class SourceDocument:
    path: Path
    filename: str
    id: str = ""
    source_id: str = ""
    order: int = 0
    page_count: int | None = None
    page_anchors: list[PdfPageAnchor] = field(default_factory=list)
    anchors_truncated: bool = False
    parser_error: str | None = None
    prepared_gemini_file: PreparedGeminiFile | None = None
    pdf_sha256: str | None = None
    on_prepared_file_failed: Callable[[str], None] | None = field(default=None, repr=False, compare=False)

GEMINI_SEMANTIC_SCHEMA = {
    "type": "object",
    "properties": {
        "title": {"type": "string", "maxLength": 80},
        "topics": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "localTopicIndex": {"type": "integer", "minimum": 1},
                    "title": {"type": "string"},
                },
                "required": ["localTopicIndex", "title"],
            },
        },
        "blocks": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "localBlockIndex": {"type": "integer", "minimum": 1},
                    "title": {"type": "string"},
                    "topicIndexes": {"type": "array", "items": {"type": "integer", "minimum": 1}},
                    "startPage": {"type": "integer", "minimum": 1},
                    "endPage": {"type": "integer", "minimum": 1},
                    "sectionTitle": {"type": "string"},
                    "materialGroupTitle": {"type": "string"},
                    "taskType": {
                        "type": "string",
                        "enum": [
                            "CONCEPT", "PRACTICE", "REVIEW", "MOCK_TEST", "MEMORIZATION",
                            "READING", "SUMMARY", "MISTAKE_REVIEW", "QUIZ", "CUSTOM",
                        ],
                    },
                    "instructions": {"type": "string"},
                    "completionCriteria": {"type": "array", "items": {"type": "string"}},
                    "effortMinMinutes": {"type": "integer", "minimum": 1},
                    "effortLikelyMinutes": {"type": "integer", "minimum": 1},
                    "effortMaxMinutes": {"type": "integer", "minimum": 1},
                    "estimateConfidence": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
                    "difficultyScore": {"type": "integer", "minimum": 1, "maximum": 5},
                    "densityScore": {"type": "integer", "minimum": 1, "maximum": 5},
                    "productionDemandScore": {"type": "integer", "minimum": 1, "maximum": 5},
                    "splitAllowed": {"type": "boolean"},
                    "continuityLabel": {"type": "string"},
                    "prerequisiteLocalBlockIndexes": {
                        "type": "array",
                        "items": {"type": "integer", "minimum": 1},
                    },
                },
                "required": [
                    "localBlockIndex", "title", "topicIndexes", "startPage", "endPage",
                    "sectionTitle", "materialGroupTitle", "taskType", "instructions", "completionCriteria",
                    "effortMinMinutes", "effortLikelyMinutes", "effortMaxMinutes",
                    "estimateConfidence", "difficultyScore", "densityScore",
                    "productionDemandScore", "splitAllowed", "continuityLabel",
                    "prerequisiteLocalBlockIndexes",
                ],
            },
        },
        "warnings": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["title", "topics", "blocks", "warnings"],
}

GLOBAL_EFFORT_CALIBRATION_SCHEMA = {
    "type": "object",
    "properties": {
        "adjustments": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "blockId": {"type": "string"},
                    "relationship": {
                        "type": "string",
                        "enum": [
                            "NEW_FOUNDATION",
                            "EXTENSION",
                            "APPLICATION",
                            "RECAP_REPETITION",
                            "REFERENCE_ADMIN",
                            "UNCHANGED",
                        ],
                    },
                    "effortMinMinutes": {"type": "integer", "minimum": 1},
                    "effortLikelyMinutes": {"type": "integer", "minimum": 1},
                    "effortMaxMinutes": {"type": "integer", "minimum": 1},
                    "estimateConfidence": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
                    "reason": {"type": "string"},
                },
                "required": [
                    "blockId",
                    "relationship",
                    "effortMinMinutes",
                    "effortLikelyMinutes",
                    "effortMaxMinutes",
                    "reason",
                ],
            },
        },
        "warnings": {"type": "array", "items": {"type": "string"}},
    },
    "required": ["adjustments", "warnings"],
}

class SemanticTopic(BaseModel):
    model_config = ConfigDict(extra="ignore")
    localTopicIndex: int = Field(ge=1)
    title: str = Field(min_length=1)


class SemanticBlock(BaseModel):
    model_config = ConfigDict(extra="ignore")
    localBlockIndex: int = Field(ge=1)
    title: str = Field(min_length=1)
    topicIndexes: list[int] = Field(min_length=1)
    startPage: int = Field(ge=1)
    endPage: int = Field(ge=1)
    sectionTitle: str = ""
    materialGroupTitle: str = ""
    taskType: StudyTaskType = StudyTaskType.CONCEPT
    instructions: str = Field(min_length=1)
    completionCriteria: list[str] = Field(default_factory=list)
    effortMinMinutes: int = Field(gt=0, le=1440)
    effortLikelyMinutes: int = Field(gt=0, le=1440)
    effortMaxMinutes: int = Field(gt=0, le=1440)
    estimateConfidence: EstimateConfidence = EstimateConfidence.MEDIUM
    difficultyScore: int = Field(ge=1, le=5)
    densityScore: int = Field(ge=1, le=5)
    productionDemandScore: int = Field(ge=1, le=5)
    splitAllowed: bool = True
    continuityLabel: str = ""
    prerequisiteLocalBlockIndexes: list[int] = Field(default_factory=list)

    @model_validator(mode="after")
    def normalize_ranges(self):
        self.topicIndexes = sorted({index for index in self.topicIndexes if index >= 1})
        if self.endPage < self.startPage:
            self.endPage = self.startPage
        self.effortLikelyMinutes = max(self.effortLikelyMinutes, self.effortMinMinutes)
        self.effortMaxMinutes = max(self.effortMaxMinutes, self.effortLikelyMinutes)
        self.completionCriteria = [item.strip() for item in self.completionCriteria if item.strip()]
        self.prerequisiteLocalBlockIndexes = sorted({
            index
            for index in self.prerequisiteLocalBlockIndexes
            if 1 <= index < self.localBlockIndex
        })
        return self


class SemanticExtraction(BaseModel):
    model_config = ConfigDict(extra="ignore")
    title: str = Field(default="Study plan", min_length=1, max_length=80)
    topics: list[SemanticTopic] = Field(min_length=1)
    blocks: list[SemanticBlock] = Field(min_length=1)
    warnings: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def normalize_indexes(self):
        self.topics = sorted(self.topics, key=lambda topic: topic.localTopicIndex)
        self.blocks = sorted(self.blocks, key=lambda block: block.localBlockIndex)
        topic_indexes = [topic.localTopicIndex for topic in self.topics]
        block_indexes = [block.localBlockIndex for block in self.blocks]
        warnings: list[str] = []
        if len(set(topic_indexes)) != len(topic_indexes):
            raise ValueError("Topic indexes must be unique")
        if len(set(block_indexes)) != len(block_indexes):
            raise ValueError("Block indexes must be unique")
        topic_index_map = {old_index: new_index for new_index, old_index in enumerate(topic_indexes, start=1)}
        block_index_map = {old_index: new_index for new_index, old_index in enumerate(block_indexes, start=1)}
        if topic_indexes != list(range(1, len(topic_indexes) + 1)):
            warnings.append("Repaired non-contiguous topic indexes from Gemini.")
            for topic in self.topics:
                topic.localTopicIndex = topic_index_map[topic.localTopicIndex]
        if block_indexes != list(range(1, len(block_indexes) + 1)):
            warnings.append("Repaired non-contiguous block indexes from Gemini.")
            for block in self.blocks:
                block.localBlockIndex = block_index_map[block.localBlockIndex]
        for block in self.blocks:
            invalid_topic_indexes = sorted({index for index in block.topicIndexes if index not in topic_index_map})
            repaired_topic_indexes = [
                topic_index_map[index]
                for index in block.topicIndexes
                if index in topic_index_map
            ]
            if not repaired_topic_indexes:
                raise ValueError("Block topic indexes must reference local topics")
            if invalid_topic_indexes:
                warnings.append(
                    f"Dropped invalid topic references for block {block.localBlockIndex}: {invalid_topic_indexes}."
                )
            block.topicIndexes = sorted(set(repaired_topic_indexes))
            block.prerequisiteLocalBlockIndexes = sorted({
                block_index_map[index]
                for index in block.prerequisiteLocalBlockIndexes
                if index in block_index_map and block_index_map[index] < block.localBlockIndex
            })
        topic_index_set = {topic.localTopicIndex for topic in self.topics}
        for block in self.blocks:
            if any(index not in topic_index_set for index in block.topicIndexes):
                raise ValueError("Block topic indexes must reference local topics")
        if warnings:
            self.warnings = [*self.warnings, *warnings]
        return self


class GlobalEffortAdjustment(BaseModel):
    model_config = ConfigDict(extra="ignore")
    blockId: str = Field(min_length=1)
    relationship: Literal[
        "NEW_FOUNDATION",
        "EXTENSION",
        "APPLICATION",
        "RECAP_REPETITION",
        "REFERENCE_ADMIN",
        "UNCHANGED",
    ] = "UNCHANGED"
    effortMinMinutes: int = Field(gt=0, le=1440)
    effortLikelyMinutes: int = Field(gt=0, le=1440)
    effortMaxMinutes: int = Field(gt=0, le=1440)
    estimateConfidence: EstimateConfidence | None = None
    reason: str = ""

    @model_validator(mode="after")
    def normalize_ranges(self):
        self.effortLikelyMinutes = max(self.effortLikelyMinutes, self.effortMinMinutes)
        self.effortMaxMinutes = max(self.effortMaxMinutes, self.effortLikelyMinutes)
        self.reason = self.reason.strip()
        return self


class GlobalEffortCalibration(BaseModel):
    model_config = ConfigDict(extra="ignore")
    adjustments: list[GlobalEffortAdjustment] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)

    @model_validator(mode="after")
    def normalize_warnings(self):
        self.warnings = [warning.strip() for warning in self.warnings if warning.strip()]
        return self


def semantic_extractions_to_generated_plan(
    extractions: list[SemanticExtraction],
    documents: list[SourceDocument],
    setup: Setup,
) -> GeneratedPlan:
    title = (setup.planTitle or "").strip() or next(
        (extraction.title for extraction in extractions if extraction.title.strip()),
        "Study plan",
    )
    topics: list[Topic] = []
    blocks: list[StudyBlock] = []
    warnings: list[ExtractionWarning] = []
    global_topic_order = 1
    global_block_order = 1

    for document_index, (extraction, document) in enumerate(zip(extractions, documents), start=1):
        source_id = document.source_id or f"doc{document_index}"
        topic_id_by_local: dict[int, str] = {}
        for topic in extraction.topics:
            topic_id = f"{source_id}-topic-{topic.localTopicIndex}"
            topic_id_by_local[topic.localTopicIndex] = topic_id
            topics.append(Topic(id=topic_id, title=topic.title.strip(), order=global_topic_order))
            global_topic_order += 1

        first_topic_id = next(iter(topic_id_by_local.values()))
        block_id_by_local = {
            block.localBlockIndex: f"{source_id}-block-{block.localBlockIndex}"
            for block in extraction.blocks
        }

        for block in extraction.blocks:
            block_id = block_id_by_local[block.localBlockIndex]
            topic_ids = [
                topic_id_by_local[index]
                for index in block.topicIndexes
                if index in topic_id_by_local
            ] or [first_topic_id]
            dependencies = [
                block_id_by_local[index]
                for index in block.prerequisiteLocalBlockIndexes
                if index in block_id_by_local
            ]
            section_title = block.sectionTitle.strip() or block.title.strip()
            material_group_title = block.materialGroupTitle.strip() or section_title
            source_ref = SourceRef(
                documentId=source_id,
                startPage=block.startPage,
                endPage=block.endPage,
                sectionTitle=section_title,
                materialGroupTitle=material_group_title,
            )
            blocks.append(
                StudyBlock.model_validate(
                    {
                        "id": block_id,
                        "title": block.title.strip(),
                        "order": global_block_order,
                        "durationMinutes": block.effortLikelyMinutes,
                        "estimatedMinutes": block.effortLikelyMinutes,
                        "effortMinMinutes": block.effortMinMinutes,
                        "effortLikelyMinutes": block.effortLikelyMinutes,
                        "effortMaxMinutes": block.effortMaxMinutes,
                        "minimumUsefulMinutes": minimum_minutes_for_task_type(block.taskType),
                        "taskType": block.taskType,
                        "difficulty": difficulty_from_score(block.difficultyScore),
                        "difficultyScore": block.difficultyScore,
                        "densityScore": block.densityScore,
                        "productionDemandScore": block.productionDemandScore,
                        "estimateConfidence": block.estimateConfidence,
                        "instructions": block.instructions.strip(),
                        "completionCriteria": block.completionCriteria,
                        "splitAllowed": block.splitAllowed,
                        "continuityGroup": block.continuityLabel.strip(),
                        "topicIds": topic_ids,
                        "dependencies": dependencies,
                        "sourceRefs": [source_ref.model_dump()],
                        "status": StudyTaskStatus.NOT_STARTED,
                        "scheduledDate": None,
                    }
                )
            )
            global_block_order += 1

        for message in extraction.warnings:
            cleaned = message.strip()
            if cleaned:
                warnings.append(ExtractionWarning(type="SEMANTIC_EXTRACTION_WARNING", message=cleaned))

    return GeneratedPlan(
        title=title,
        topics=topics,
        blocks=blocks,
        extractionWarnings=warnings,
    )


def global_effort_calibration_enabled() -> bool:
    return os.getenv("REN_GLOBAL_EFFORT_CALIBRATION", "1").strip().lower() not in CACHE_DISABLED_VALUES


def apply_global_effort_calibration(
    plan: GeneratedPlan,
    calibration: GlobalEffortCalibration,
) -> GeneratedPlan:
    adjustments = {adjustment.blockId: adjustment for adjustment in calibration.adjustments}
    warnings = list(plan.extractionWarnings)
    calibrated_blocks: list[StudyBlock] = []
    seen_adjustments: set[str] = set()

    for block in plan.blocks:
        adjustment = adjustments.get(block.id)
        if adjustment is None:
            calibrated_blocks.append(block)
            continue

        seen_adjustments.add(adjustment.blockId)
        if adjustment.relationship == "UNCHANGED":
            calibrated_blocks.append(block)
            continue

        likely = max(adjustment.effortLikelyMinutes, block.minimumUsefulMinutes)
        min_minutes = min(
            max(adjustment.effortMinMinutes, block.minimumUsefulMinutes),
            likely,
        )
        max_minutes = max(adjustment.effortMaxMinutes, likely)
        update = {
            "durationMinutes": likely,
            "estimatedMinutes": likely,
            "effortMinMinutes": min_minutes,
            "effortLikelyMinutes": likely,
            "effortMaxMinutes": max_minutes,
        }
        if adjustment.estimateConfidence is not None:
            update["estimateConfidence"] = adjustment.estimateConfidence
        calibrated_blocks.append(
            StudyBlock.model_validate(
                {
                    **block.model_dump(mode="json"),
                    **update,
                }
            )
        )

    for unknown_id in sorted(set(adjustments) - seen_adjustments):
        warnings.append(
            ExtractionWarning(
                type="GLOBAL_EFFORT_CALIBRATION_WARNING",
                message=f"Global calibration referenced unknown block {unknown_id}; ignored.",
                blockId=unknown_id,
            )
        )

    for message in calibration.warnings:
        warnings.append(
            ExtractionWarning(
                type="GLOBAL_EFFORT_CALIBRATION_WARNING",
                message=message,
            )
        )

    return plan.model_copy(update={"blocks": calibrated_blocks, "extractionWarnings": warnings})


def with_global_calibration_failure_warning(plan: GeneratedPlan) -> GeneratedPlan:
    return plan.model_copy(
        update={
            "extractionWarnings": [
                *plan.extractionWarnings,
                ExtractionWarning(
                    type="GLOBAL_EFFORT_CALIBRATION_WARNING",
                    message="Global workload calibration failed; using per-document estimates.",
                ),
            ]
        }
    )


def difficulty_from_score(score: int) -> Difficulty:
    if score <= 2:
        return Difficulty.LIGHT
    if score >= 4:
        return Difficulty.HEAVY
    return Difficulty.STANDARD


def minimum_minutes_for_task_type(task_type: StudyTaskType) -> int:
    return TASK_TYPE_MINIMUMS.get(task_type, 5)


def gemini_concurrency() -> int:
    try:
        value = int(os.getenv("REN_GEMINI_CONCURRENCY", "2"))
    except ValueError:
        value = 2
    return max(1, min(value, 4))


def default_semantic_cache_dir() -> Path:
    configured = os.getenv("REN_EXTRACTION_CACHE_DIR")
    if configured:
        path = Path(configured)
        return path if path.is_absolute() else BACKEND_ROOT / path
    data_dir = Path(os.getenv("REN_DATA_DIR", "data"))
    data_dir = data_dir if data_dir.is_absolute() else BACKEND_ROOT / data_dir
    return data_dir / "extraction-cache"


def semantic_cache_enabled() -> bool:
    return os.getenv("REN_EXTRACTION_CACHE", "1").strip().lower() not in CACHE_DISABLED_VALUES


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def semantic_cache_key(pdf_sha256: str, model: str) -> str:
    material = "|".join(
        [
            SEMANTIC_EXTRACTION_CACHE_VERSION,
            SEMANTIC_EXTRACTION_PROMPT_VERSION,
            SEMANTIC_EXTRACTION_SCHEMA_VERSION,
            DOCUMENT_CONTEXT_VERSION,
            model,
            pdf_sha256,
        ]
    )
    return hashlib.sha256(material.encode("utf-8")).hexdigest()


class SemanticExtractionCache:
    def __init__(self, root: Path | None = None, enabled: bool | None = None):
        self.root = root or default_semantic_cache_dir()
        self.enabled = semantic_cache_enabled() if enabled is None else enabled
        if self.enabled:
            self.root.mkdir(parents=True, exist_ok=True)

    def path_for(self, pdf_sha256: str, model: str) -> Path:
        key = semantic_cache_key(pdf_sha256, model)
        return self.root / key[:2] / f"{key}.json"

    def get(self, document: SourceDocument, model: str, pdf_sha256: str) -> SemanticExtraction | None:
        if not self.enabled:
            return None
        path = self.path_for(pdf_sha256, model)
        if not path.exists():
            return None
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
            metadata = payload.get("metadata", {})
            if metadata.get("cacheVersion") != SEMANTIC_EXTRACTION_CACHE_VERSION:
                return None
            if metadata.get("pdfSha256") != pdf_sha256:
                return None
            if metadata.get("model") != model:
                return None
            if metadata.get("promptVersion") != SEMANTIC_EXTRACTION_PROMPT_VERSION:
                return None
            if metadata.get("schemaVersion") != SEMANTIC_EXTRACTION_SCHEMA_VERSION:
                return None
            if metadata.get("documentContextVersion") != DOCUMENT_CONTEXT_VERSION:
                return None
            extraction = SemanticExtraction.model_validate(payload.get("extraction"))
            logger.info("Semantic extraction cache hit for %s", document.filename)
            return extraction
        except Exception:
            logger.warning("Ignoring unreadable semantic cache entry for %s", document.filename, exc_info=True)
            return None

    def put(self, document: SourceDocument, model: str, pdf_sha256: str, extraction: SemanticExtraction):
        if not self.enabled:
            return
        path = self.path_for(pdf_sha256, model)
        path.parent.mkdir(parents=True, exist_ok=True)
        payload = {
            "metadata": {
                "cacheVersion": SEMANTIC_EXTRACTION_CACHE_VERSION,
                "promptVersion": SEMANTIC_EXTRACTION_PROMPT_VERSION,
                "schemaVersion": SEMANTIC_EXTRACTION_SCHEMA_VERSION,
                "documentContextVersion": DOCUMENT_CONTEXT_VERSION,
                "model": model,
                "pdfSha256": pdf_sha256,
                "filename": document.filename,
            },
            "extraction": extraction.model_dump(mode="json"),
        }
        temporary = path.with_name(f"{path.name}.{os.getpid()}.{uuid4().hex}.tmp")
        try:
            temporary.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
            temporary.replace(path)
            logger.info("Semantic extraction cached for %s", document.filename)
        except Exception:
            temporary.unlink(missing_ok=True)
            logger.warning("Could not write semantic cache entry for %s", document.filename, exc_info=True)


class AIProvider(ABC):
    @abstractmethod
    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan: ...

    async def prepare_document(self, document: SourceDocument) -> PreparedGeminiFile | None:
        del document
        return None

    def should_prepare_document(self, document: SourceDocument) -> bool:
        del document
        return True

    async def delete_prepared_file(self, prepared_file: PreparedGeminiFile):
        del prepared_file


class GeminiProvider(AIProvider):
    def __init__(
        self,
        api_key: str,
        model: str = "gemini-2.5-flash",
        semantic_cache: SemanticExtractionCache | None = None,
    ):
        from google import genai
        self.client = genai.Client(api_key=api_key)
        self.model = model
        self.semantic_cache = semantic_cache or SemanticExtractionCache()

    def should_prepare_document(self, document: SourceDocument) -> bool:
        cache = getattr(self, "semantic_cache", None)
        if cache is None or not document.pdf_sha256:
            return True
        return cache.get(document, self.model, document.pdf_sha256) is None

    async def prepare_document(self, document: SourceDocument) -> PreparedGeminiFile:
        uploaded = None
        try:
            uploaded = await self.client.aio.files.upload(file=document.path)
            return PreparedGeminiFile(
                name=uploaded.name,
                uri=uploaded.uri,
                mime_type=uploaded.mime_type or "application/pdf",
            )
        except BaseException:
            if uploaded is not None:
                try:
                    await self.client.aio.files.delete(name=uploaded.name)
                except Exception:
                    logger.warning("Could not delete abandoned Gemini file %s", uploaded.name, exc_info=True)
            raise

    async def delete_prepared_file(self, prepared_file: PreparedGeminiFile):
        try:
            await self.client.aio.files.delete(name=prepared_file.name)
        except Exception:
            logger.warning("Could not delete Gemini prepared file %s", prepared_file.name, exc_info=True)

    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
        extractions = await self._extract_all_documents_semantic(documents)
        plan = semantic_extractions_to_generated_plan(extractions, documents, setup)
        if len(documents) < 2 or not global_effort_calibration_enabled():
            return plan
        try:
            return await self._calibrate_global_effort(plan, documents, setup)
        except Exception:
            logger.warning("Global workload calibration failed; using per-document estimates", exc_info=True)
            return with_global_calibration_failure_warning(plan)

    async def _extract_all_documents_semantic(self, documents: list[SourceDocument]) -> list[SemanticExtraction]:
        semaphore = asyncio.Semaphore(gemini_concurrency())

        async def extract_with_limit(index: int, document: SourceDocument) -> SemanticExtraction:
            async with semaphore:
                return await self._extract_document_semantic(index, document)

        tasks = [
            asyncio.create_task(extract_with_limit(index, document))
            for index, document in enumerate(documents, start=1)
        ]
        try:
            return await asyncio.gather(*tasks)
        except Exception:
            for task in tasks:
                if not task.done():
                    task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)
            raise

    async def _extract_document_semantic(
        self,
        index: int,
        document: SourceDocument,
    ) -> SemanticExtraction:
        pdf_hash = document.pdf_sha256 or await asyncio.to_thread(file_sha256, document.path)
        cache = getattr(self, "semantic_cache", None)
        if cache is not None:
            cached = cache.get(document, self.model, pdf_hash)
            if cached is not None:
                return cached

        prepared_file = document.prepared_gemini_file
        if prepared_file is not None:
            try:
                extraction = await self._generate_semantic_extraction(index, document, prepared_file)
                if cache is not None:
                    cache.put(document, self.model, pdf_hash, extraction)
                return extraction
            except Exception:
                if document.on_prepared_file_failed is not None:
                    document.on_prepared_file_failed("Prepared Gemini file failed")
                logger.warning(
                    "Prepared Gemini file failed for %s; retrying with a fresh upload",
                    document.filename,
                    exc_info=True,
                )

        uploaded = None
        try:
            uploaded = await self.prepare_document(document)
            extraction = await self._generate_semantic_extraction(index, document, uploaded)
            if cache is not None:
                cache.put(document, self.model, pdf_hash, extraction)
            return extraction
        finally:
            if uploaded is not None:
                await self.delete_prepared_file(uploaded)

    async def _generate_semantic_extraction(
        self,
        index: int,
        document: SourceDocument,
        prepared_file: PreparedGeminiFile,
    ) -> SemanticExtraction:
        from google.genai import types

        file_part = types.Part.from_uri(
            file_uri=prepared_file.uri,
            mime_type=prepared_file.mime_type or "application/pdf",
        )
        response = await self.client.aio.models.generate_content(
            model=self.model,
            contents=[
                format_document_context(index, document),
                file_part,
                semantic_prompt(),
            ],
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=GEMINI_SEMANTIC_SCHEMA,
                temperature=0.2,
            )
        )
        return parse_semantic_response(response.text, document)

    async def _calibrate_global_effort(
        self,
        plan: GeneratedPlan,
        documents: list[SourceDocument],
        setup: Setup,
    ) -> GeneratedPlan:
        from google.genai import types

        response = await self.client.aio.models.generate_content(
            model=self.model,
            contents=[
                format_global_effort_context(plan, documents, setup),
                global_effort_calibration_prompt(),
            ],
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=GLOBAL_EFFORT_CALIBRATION_SCHEMA,
                temperature=0.1,
            ),
        )
        calibration = parse_global_effort_calibration_response(response.text)
        return apply_global_effort_calibration(plan, calibration)


def semantic_prompt() -> str:
    return (
        "You are a source-grounded curriculum segmentation engine, not a calendar scheduler. "
        "Extract a semantic study map for one lecture PDF. "
        "Do not output document ids. "
        "Use only material in this PDF. Preserve the lecture's page/section order. "
        "Do not reorder material to balance difficulty or importance. "
        "Do not decide that any material is priority, skippable, optional, or excluded; the student decides that later. "
        "Do not output dates, day assignments, schedule advice, or study-day recommendations. "
        "Assign a concise document title grounded in this PDF. "
        "Return JSON matching the supplied semantic schema. "
        "Topics are local to this lecture and must have contiguous localTopicIndex values starting at 1. "
        "Blocks are local to this lecture and must have contiguous localBlockIndex values starting at 1. "
        "Every block must reference one or more topicIndexes from the local topics. "
        "Use startPage/endPage for the source span in this PDF. Keep page ranges best-effort but grounded. "
        "For each block, set sectionTitle to the precise local section/subsection label for that block. "
        "Set materialGroupTitle to a broader parent heading used only for grouping nearby blocks in the material view. "
        "Good materialGroupTitle values are chapter titles, lecture units, or major section headings that usually contain multiple adjacent blocks. "
        "Prefer stable broader groups over one group per tiny subsection; for a typical lecture, use a small number of meaningful parent groups rather than copying every block title. "
        "Do not weaken sectionTitle to make grouping easier; keep sectionTitle atomic and use materialGroupTitle for the broader label. "
        "If there is no broader source-grounded parent group, use an empty string. "
        "Do not invent a broader group just to make the material view prettier. "
        "Preserve named examinable concepts, methods, theories, algorithms, metrics, and examples from each source section. "
        "Avoid vague blocks like 'overview' when the source contains specific names. "
        "Keep instructions short and actionable. Completion criteria should describe what the learner can verify after finishing the block. "
        "Choose taskType by what the learner must do: CONCEPT for understanding/explaining ideas, PRACTICE for solving examples/problems, READING for lighter source material, SUMMARY when the source itself calls for synthesis, and CUSTOM for admin/reference material that still needs tracking. "
        "Do not label logistical, syllabus, scope, or reference-only pages as CONCEPT unless they contain real conceptual study material. "
        "Pure admin/logistics that does not need studying can be omitted; if it must be tracked, use CUSTOM with a very small estimate. "
        "Estimate honest active-study effort for a capable but cramming student, not an expert skimming slides. Include reading/decoding the source, making minimal notes, working included examples, and self-checking; do not include breaks. "
        "Use 5-minute increments unless a source span is genuinely tiny. Do not compress work to fit the deadline. "
        "Set effortMinMinutes <= effortLikelyMinutes <= effortMaxMinutes. "
        "Rate difficultyScore, densityScore, and productionDemandScore independently from 1 to 5. "
        "Difficulty anchors: 1 recognition/light repetition, 2 straightforward recall, 3 multi-step understanding/application, 4 integration or nontrivial problem solving, 5 proof/deep synthesis. "
        "Density is how much new information is packed into the source span. "
        "Production demand is how much the learner must actively solve, derive, explain, or produce rather than only read. "
        "Use LOW/MEDIUM/HIGH estimateConfidence to express uncertainty; confidence is not difficulty. "
        "Do not create skim-only tasks for real educational content; use READING for light source material. "
        "Only use REVIEW, QUIZ, MOCK_TEST, or MISTAKE_REVIEW when the source itself contains review, quiz, mock-test, or mistake-review material. "
        "Do not invent future revision tasks. "
        "Do not add a cumulative recap/review block that spans material already covered by earlier blocks. "
        "Each block's page range should move forward with the lecture; a later block should not reach back to earlier pages unless the source has an explicit later cross-reference section. "
        "Prefer semantically coherent blocks of about 15-50 active minutes. If a section is dense, split it into natural source-ordered blocks rather than pretending it is tiny. "
        "Set splitAllowed true when the block has natural sub-boundaries and false when splitting would make it confusing. "
        "Use continuityLabel to keep adjacent blocks near each other when they form one concept; use an empty string otherwise. "
        "For prerequisiteLocalBlockIndexes, include only obvious earlier local block indexes; otherwise use an empty array. "
        "Use warnings only for extraction uncertainty, such as missing readable text, unclear page spans, or ambiguous source structure. "
    )


def global_effort_calibration_prompt() -> str:
    return (
        "You are calibrating active-study effort across an already extracted exam-cram material map. "
        "Do not create, remove, rename, reorder, merge, or split blocks. Do not change page ranges, source refs, topics, or instructions. "
        "Do not decide anything is skippable, optional, excluded, or priority; the student decides that later. "
        "Your job is only to adjust effort estimates after seeing the full document sequence. "
        "A later block may need less effort if it mostly repeats, recaps, or applies concepts that were already introduced earlier. "
        "A later block may still need full effort if it adds new concepts, difficult production work, dense examples, or exam-style practice. "
        "Text length is only one signal. Calibrate by novelty, cognitive load, density, production demand, and relationship to earlier blocks. "
        "Do not compress work to fit the user's deadline or available time. Estimate honest active-study time, excluding breaks. "
        "Use these relationship labels: NEW_FOUNDATION for genuinely new core ideas; EXTENSION for new material building on earlier blocks; "
        "APPLICATION for examples/problems using earlier concepts; RECAP_REPETITION for substantial repeated/recap content; "
        "REFERENCE_ADMIN for admin/reference material that still exists but needs little study; UNCHANGED exists only as a no-op fallback. "
        "Return adjustments only for blocks whose effort or confidence should change; do not return UNCHANGED blocks. For every returned adjustment, use the exact blockId. "
        "Set effortMinMinutes <= effortLikelyMinutes <= effortMaxMinutes, and never set likely effort below the block's minimumUsefulMinutes. "
        "Keep reasons short and grounded in the relationship to earlier/later blocks. "
        "Return JSON matching the supplied schema. "
    )


def format_global_effort_context(plan: GeneratedPlan, documents: list[SourceDocument], setup: Setup) -> str:
    topic_title_by_id = {topic.id: topic.title for topic in plan.topics}
    document_title_by_id = {
        document.source_id or f"doc{index}": document.filename
        for index, document in enumerate(documents, start=1)
    }
    blocks = []
    for block in sorted(plan.blocks, key=lambda item: item.order):
        ref = block.sourceRefs[0] if block.sourceRefs else None
        blocks.append(
            {
                "blockId": block.id,
                "order": block.order,
                "title": block.title,
                "documentId": ref.documentId if ref else None,
                "document": document_title_by_id.get(ref.documentId if ref else "", ref.documentId if ref else None),
                "pages": (
                    f"{ref.startPage}-{ref.endPage}"
                    if ref and ref.startPage and ref.endPage and ref.endPage != ref.startPage
                    else ref.startPage if ref else None
                ),
                "sectionTitle": ref.sectionTitle if ref else None,
                "materialGroupTitle": ref.materialGroupTitle if ref else None,
                "taskType": block.taskType,
                "instructions": block.instructions,
                "minimumUsefulMinutes": block.minimumUsefulMinutes,
                "effortMinMinutes": block.effortMinMinutes,
                "effortLikelyMinutes": block.effortLikelyMinutes,
                "effortMaxMinutes": block.effortMaxMinutes,
                "difficultyScore": block.difficultyScore,
                "densityScore": block.densityScore,
                "productionDemandScore": block.productionDemandScore,
                "estimateConfidence": block.estimateConfidence,
                "topics": [topic_title_by_id[topic_id] for topic_id in block.topicIds if topic_id in topic_title_by_id],
                "dependencies": block.dependencies,
                "completionCriteria": block.completionCriteria[:3],
            }
        )
    payload = {
        "promptVersion": GLOBAL_EFFORT_CALIBRATION_PROMPT_VERSION,
        "schemaVersion": GLOBAL_EFFORT_CALIBRATION_SCHEMA_VERSION,
        "planTitle": setup.planTitle,
        "documents": [
            {
                "documentId": document.source_id or f"doc{index}",
                "order": index,
                "filename": document.filename,
                "pageCount": document.page_count,
            }
            for index, document in enumerate(documents, start=1)
        ],
        "blocks": blocks,
    }
    return "Global effort calibration input:\n" + json.dumps(payload, ensure_ascii=False)


def parse_semantic_response(raw: str | None, document: SourceDocument) -> SemanticExtraction:
    logger.info("Gemini semantic response for %s (first 500 chars): %s", document.filename, raw[:500] if raw else "<empty>")
    try:
        parsed = json.loads(raw)
    except (json.JSONDecodeError, TypeError) as exc:
        logger.error("Gemini returned invalid semantic JSON for %s: %s", document.filename, exc)
        raise ValueError(f"Invalid semantic JSON from Gemini: {exc}") from exc
    try:
        return SemanticExtraction.model_validate(parsed)
    except Exception as exc:
        logger.error("Semantic extraction validation failed for %s: %s", document.filename, exc)
        raise ValueError(f"Semantic extraction validation failed: {exc}") from exc


def parse_global_effort_calibration_response(raw: str | None) -> GlobalEffortCalibration:
    logger.info("Gemini global calibration response (first 500 chars): %s", raw[:500] if raw else "<empty>")
    try:
        parsed = json.loads(raw)
    except (json.JSONDecodeError, TypeError) as exc:
        logger.error("Gemini returned invalid global calibration JSON: %s", exc)
        raise ValueError(f"Invalid global calibration JSON from Gemini: {exc}") from exc
    try:
        return GlobalEffortCalibration.model_validate(parsed)
    except Exception as exc:
        logger.error("Global calibration validation failed: %s", exc)
        raise ValueError(f"Global calibration validation failed: {exc}") from exc


def format_document_context(index: int, document: SourceDocument) -> str:
    document_id = document.source_id or f"doc{index}"
    page_count = f", {document.page_count} pages" if document.page_count else ""
    lines = [f"Document {index} ({document_id}): {document.filename}{page_count}"]

    if document.page_anchors:
        lines.append("Compact page map extracted locally:")
        for anchor in document.page_anchors:
            if anchor.heading or anchor.cues:
                details = []
                if anchor.heading:
                    details.append(f"heading: {anchor.heading}")
                if anchor.cues:
                    details.append(f"cues: {'; '.join(anchor.cues)}")
                lines.append(f"- p. {anchor.page} ({anchor.word_count} words) | {' | '.join(details)}")
            else:
                lines.append(f"- p. {anchor.page} ({anchor.word_count} words): {anchor.text}")
        if document.anchors_truncated:
            lines.append("- Page-map extraction was truncated; continue using the attached PDF for the rest.")
    elif document.parser_error:
        lines.append("No text anchors were extracted locally; use the attached PDF directly.")
    else:
        lines.append("No text anchors were extracted locally; use the attached PDF directly.")

    return "\n".join(lines)
