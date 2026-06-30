from __future__ import annotations

import logging

from .models import (
    GeneratedPlan,
    Setup,
    SourceDocumentInfo,
    SourceRef,
    StudyBlock,
)
from .provider import SourceDocument

logger = logging.getLogger("ren")


def build_master_plan(
    generated: GeneratedPlan,
    documents: list[SourceDocument],
    setup: Setup | None = None,
) -> GeneratedPlan:
    """Normalize Gemini output into a stable material map.

    The backend deliberately does not schedule work. It only turns Gemini's PDF
    understanding into ordered, source-grounded study blocks. Android owns
    workload scoring, deadline logic, day assignment, and replanning.
    """

    del setup
    return normalize_plan(generated, source_document_infos(documents))


def source_document_infos(documents: list[SourceDocument]) -> list[SourceDocumentInfo]:
    return [
        SourceDocumentInfo(
            id=document.source_id or f"doc{index}",
            filename=document.filename,
            order=index,
            pageCount=document.page_count,
            uploadDocumentId=document.id or None,
        )
        for index, document in enumerate(documents, start=1)
    ]


def normalize_plan(plan: GeneratedPlan, source_documents: list[SourceDocumentInfo]) -> GeneratedPlan:
    topic_id_map = {
        topic.id: f"topic{index}"
        for index, topic in enumerate(sorted(plan.topics, key=lambda item: item.order), start=1)
    }
    topics = [
        topic.model_copy(update={"id": topic_id_map[topic.id], "order": index})
        for index, topic in enumerate(sorted(plan.topics, key=lambda item: item.order), start=1)
    ]

    block_id_map = {
        block.id: f"block{index}"
        for index, block in enumerate(sorted(plan.blocks, key=lambda item: item.order), start=1)
    }
    source_ids = {document.id for document in source_documents}
    default_source = source_documents[0].id if source_documents else "doc1"
    page_counts = {document.id: document.pageCount for document in source_documents}
    source_order = {document.id: document.order for document in source_documents}
    last_source_key: tuple[int, int, int] | None = None
    extraction_warnings = [
        warning.model_copy(update={"blockId": block_id_map.get(warning.blockId, warning.blockId)})
        for warning in plan.extractionWarnings
    ]

    blocks: list[StudyBlock] = []
    for index, block in enumerate(sorted(plan.blocks, key=lambda item: item.order), start=1):
        refs = [sanitize_source_ref(ref, source_ids, default_source, page_counts) for ref in block.sourceRefs]
        if not refs:
            refs = [SourceRef(documentId=default_source, startPage=1, endPage=1, sectionTitle="")]
        first_ref_key = min(source_ref_key(ref, source_order) for ref in refs)
        if last_source_key and first_ref_key < last_source_key:
            message = (
                "Some source references moved backwards; preserving block order "
                "because lecture/document order is the user's expected study path."
            )
            logger.warning("%s Near block %s.", message, block.id)
            extraction_warnings.append(
                {
                    "type": "SOURCE_ORDER_CONFLICT",
                    "message": message,
                    "blockId": block_id_map[block.id],
                    "documentId": refs[0].documentId if refs else None,
                    "startPage": refs[0].startPage if refs else None,
                    "endPage": refs[0].endPage if refs else None,
                }
            )
        last_source_key = max(last_source_key or first_ref_key, max(source_ref_key(ref, source_order) for ref in refs))
        topic_ids = [topic_id_map[topic_id] for topic_id in block.topicIds if topic_id in topic_id_map]
        if not topic_ids and topics:
            topic_ids = [topics[min(index - 1, len(topics) - 1)].id]
        dependencies = [
            block_id_map[dependency]
            for dependency in block.dependencies
            if dependency in block_id_map
            and block_id_map[dependency] != block_id_map[block.id]
            and int(block_id_map[dependency].removeprefix("block")) < index
        ]
        blocks.append(
            StudyBlock.model_validate(
                {
                    **block.model_dump(),
                    "id": block_id_map[block.id],
                    "order": index,
                    "topicIds": topic_ids,
                    "sourceRefs": [ref.model_dump() for ref in refs],
                    "dependencies": dependencies,
                }
            )
        )

    return GeneratedPlan(
        planVersion=3,
        title=plan.title,
        sourceDocuments=source_documents,
        topics=topics,
        blocks=blocks,
        extractionWarnings=extraction_warnings,
    )


def source_ref_key(ref: SourceRef, source_order: dict[str, int]) -> tuple[int, int, int]:
    document_order = source_order.get(ref.documentId, 10_000)
    start_page = ref.startPage or 1
    end_page = ref.endPage or start_page
    return document_order, start_page, end_page


def sanitize_source_ref(
    ref: SourceRef,
    source_ids: set[str],
    default_source: str,
    page_counts: dict[str, int | None],
) -> SourceRef:
    document_id = ref.documentId if ref.documentId in source_ids else default_source
    page_count = page_counts.get(document_id)
    start_page = ref.startPage or 1
    end_page = ref.endPage or start_page
    if page_count:
        start_page = min(start_page, page_count)
        end_page = min(max(end_page, start_page), page_count)
    return SourceRef(
        documentId=document_id,
        startPage=start_page,
        endPage=max(end_page, start_page),
        sectionTitle=(ref.sectionTitle or "").strip(),
        materialGroupTitle=(ref.materialGroupTitle or "").strip(),
    )
