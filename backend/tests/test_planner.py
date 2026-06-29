from app.models import ExtractionWarning, GeneratedPlan, SourceDocumentInfo, SourceRef, StudyTaskStatus
from app.planner import build_master_plan, normalize_plan
from app.provider import SourceDocument


def source(path, name, source_id, order):
    path.write_bytes(b"%PDF-test")
    return SourceDocument(path=path, filename=name, id=f"upload-{order}", source_id=source_id, order=order, page_count=10)


def raw_plan(block_minutes):
    return GeneratedPlan.model_validate({
        "title": "HCI final",
        "topics": [{"id": "t-old", "title": "Topic", "order": 1}],
        "blocks": [
            {
                "id": f"old-{index}",
                "title": f"Block {index}",
                "order": index,
                "durationMinutes": minutes,
                "estimatedMinutes": minutes,
                "minimumUsefulMinutes": 5,
                "taskType": "CONCEPT",
                "difficulty": "STANDARD",
                "estimateConfidence": "MEDIUM",
                "instructions": "Study this block.",
                "topicIds": ["t-old"],
                "dependencies": [],
                "sourceRefs": [{
                    "documentId": "doc1",
                    "startPage": index,
                    "endPage": index,
                    "sectionTitle": f"Section {index}",
                    "materialGroupTitle": "Parent section",
                }],
            }
            for index, minutes in enumerate(block_minutes, start=1)
        ],
    })


def test_master_plan_normalizes_sources_and_stable_ids_without_scheduling(tmp_path):
    plan = build_master_plan(
        raw_plan([30, 45]),
        [source(tmp_path / "lecture-1.pdf", "Lecture 1.pdf", "doc1", 1)],
    )

    assert plan.planVersion == 3
    assert plan.sourceDocuments[0].id == "doc1"
    assert [block.id for block in plan.blocks] == ["block1", "block2"]
    assert [block.order for block in plan.blocks] == [1, 2]
    assert all(block.sourceRefs[0].documentId == "doc1" for block in plan.blocks)
    assert plan.blocks[0].sourceRefs[0].sectionTitle == "Section 1"
    assert plan.blocks[0].sourceRefs[0].materialGroupTitle == "Parent section"
    assert all(block.status == StudyTaskStatus.NOT_STARTED for block in plan.blocks)
    assert all(block.scheduledDate is None for block in plan.blocks)
    assert not hasattr(plan, "schedule")
    assert not hasattr(plan.blocks[0], "robustMinutes")
    assert not hasattr(plan.blocks[0], "cognitivePoints")


def test_normalize_plan_drops_forward_dependencies_without_reordering_blocks():
    raw = raw_plan([30, 30])
    raw.blocks[0].dependencies = ["old-2"]
    raw.blocks[1].dependencies = ["old-1"]

    plan = normalize_plan(
        raw,
        [SourceDocumentInfo(id="doc1", filename="Lecture 1.pdf", order=1, pageCount=10)],
    )

    assert [block.id for block in plan.blocks] == ["block1", "block2"]
    assert plan.blocks[0].dependencies == []
    assert plan.blocks[1].dependencies == ["block1"]


def test_normalize_plan_preserves_provider_warnings_and_remaps_block_ids():
    raw = raw_plan([30])
    raw.extractionWarnings = [
        ExtractionWarning(
            type="GLOBAL_EFFORT_CALIBRATION_WARNING",
            message="Calibration had to ignore one adjustment.",
            blockId="old-1",
        )
    ]

    plan = normalize_plan(
        raw,
        [SourceDocumentInfo(id="doc1", filename="Lecture 1.pdf", order=1, pageCount=10)],
    )

    assert plan.extractionWarnings[0].type == "GLOBAL_EFFORT_CALIBRATION_WARNING"
    assert plan.extractionWarnings[0].blockId == "block1"


def test_normalize_plan_surfaces_source_order_conflicts():
    raw = raw_plan([30, 30])
    raw.blocks[0].sourceRefs = [SourceRef(documentId="doc1", startPage=5, endPage=5, sectionTitle="")]
    raw.blocks[1].sourceRefs = [SourceRef(documentId="doc1", startPage=2, endPage=2, sectionTitle="")]

    plan = normalize_plan(
        raw,
        [SourceDocumentInfo(id="doc1", filename="Lecture 1.pdf", order=1, pageCount=10)],
    )

    assert plan.extractionWarnings
    assert plan.extractionWarnings[0].type == "SOURCE_ORDER_CONFLICT"
    assert plan.extractionWarnings[0].blockId == "block2"


def test_normalize_plan_clamps_source_refs_to_document_page_count():
    raw = raw_plan([30])
    raw.blocks[0].sourceRefs = [SourceRef(documentId="doc1", startPage=99, endPage=120, sectionTitle="")]

    plan = normalize_plan(
        raw,
        [SourceDocumentInfo(id="doc1", filename="Lecture 1.pdf", order=1, pageCount=10)],
    )

    assert plan.blocks[0].sourceRefs[0].startPage == 10
    assert plan.blocks[0].sourceRefs[0].endPage == 10
