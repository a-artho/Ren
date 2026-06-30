from pathlib import Path
import asyncio
import json
import pytest
from google.genai import types

from app.pdf_parser import PdfPageAnchor
from app.provider import (
    AIProvider,
    GLOBAL_EFFORT_CALIBRATION_SCHEMA,
    GEMINI_SEMANTIC_SCHEMA,
    GeminiProvider,
    GlobalEffortCalibration,
    PreparedGeminiFile,
    SemanticExtraction,
    SemanticExtractionCache,
    SourceDocument,
    apply_global_effort_calibration,
    format_document_context,
    format_global_effort_context,
    semantic_extractions_to_generated_plan,
)
from app.models import GeneratedPlan, Setup


def test_semantic_schema_is_accepted_by_installed_gemini_sdk():
    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=GEMINI_SEMANTIC_SCHEMA,
    )

    assert config.response_schema == GEMINI_SEMANTIC_SCHEMA


def test_global_calibration_schema_is_accepted_by_installed_gemini_sdk():
    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=GLOBAL_EFFORT_CALIBRATION_SCHEMA,
    )

    assert config.response_schema == GLOBAL_EFFORT_CALIBRATION_SCHEMA


def test_semantic_extraction_canonicalizes_into_generated_plan(tmp_path):
    extraction = SemanticExtraction.model_validate({
        "title": "Lecture title",
        "topics": [
            {"localTopicIndex": 1, "title": "Modes"},
            {"localTopicIndex": 2, "title": "AES"},
        ],
        "blocks": [
            {
                "localBlockIndex": 1,
                "title": "ECB and CBC",
                "topicIndexes": [1],
                "startPage": 4,
                "endPage": 8,
                "sectionTitle": "Modes",
                "materialGroupTitle": "Block cipher modes",
                "taskType": "CONCEPT",
                "instructions": "Compare ECB and CBC.",
                "completionCriteria": ["Explain ECB leakage."],
                "effortMinMinutes": 20,
                "effortLikelyMinutes": 35,
                "effortMaxMinutes": 50,
                "estimateConfidence": "MEDIUM",
                "difficultyScore": 4,
                "densityScore": 3,
                "productionDemandScore": 3,
                "keepTogetherLabel": "modes",
                "prerequisiteLocalBlockIndexes": [],
            },
            {
                "localBlockIndex": 2,
                "title": "AES structure",
                "topicIndexes": [2],
                "startPage": 9,
                "endPage": 12,
                "sectionTitle": "AES",
                "materialGroupTitle": "Block ciphers",
                "taskType": "CONCEPT",
                "instructions": "Understand AES rounds.",
                "completionCriteria": ["Name the core AES round operations."],
                "effortMinMinutes": 25,
                "effortLikelyMinutes": 40,
                "effortMaxMinutes": 65,
                "estimateConfidence": "LOW",
                "difficultyScore": 5,
                "densityScore": 4,
                "productionDemandScore": 4,
                "keepTogetherLabel": "",
                "prerequisiteLocalBlockIndexes": [1],
            },
        ],
        "warnings": ["Page labels were ambiguous."],
    })
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    setup = Setup(
        goal="PrepareForExam",
        planTitle="HCI final",
        deadline="InOneWeek",
        dailyStudyMinutes=30,
        studyDays=["Monday"],
    )

    plan = semantic_extractions_to_generated_plan(
        [extraction],
        [SourceDocument(pdf, "Lecture 1.pdf", source_id="doc1", page_count=12)],
        setup,
    )

    assert plan.title == "HCI final"
    assert [topic.id for topic in plan.topics] == ["doc1-topic-1", "doc1-topic-2"]
    assert [block.id for block in plan.blocks] == ["doc1-block-1", "doc1-block-2"]
    assert plan.blocks[0].effortLikelyMinutes == 35
    assert plan.blocks[0].effortMaxMinutes == 50
    assert plan.blocks[0].difficultyScore == 4
    assert plan.blocks[0].sourceRefs[0].documentId == "doc1"
    assert plan.blocks[0].sourceRefs[0].sectionTitle == "Modes"
    assert plan.blocks[0].sourceRefs[0].materialGroupTitle == "Block cipher modes"
    assert plan.blocks[1].dependencies == ["doc1-block-1"]
    assert plan.extractionWarnings[0].message == "Page labels were ambiguous."


def test_semantic_extraction_repairs_non_contiguous_indexes():
    extraction = SemanticExtraction.model_validate({
        "title": "Lecture",
        "topics": [
            {"localTopicIndex": 2, "title": "First"},
            {"localTopicIndex": 4, "title": "Second"},
        ],
        "blocks": [
            {
                "localBlockIndex": 3,
                "title": "First block",
                "topicIndexes": [2],
                "startPage": 1,
                "endPage": 2,
                "taskType": "CONCEPT",
                "instructions": "Study first.",
                "effortMinMinutes": 10,
                "effortLikelyMinutes": 20,
                "effortMaxMinutes": 30,
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
                "prerequisiteLocalBlockIndexes": [],
            },
            {
                "localBlockIndex": 7,
                "title": "Second block",
                "topicIndexes": [4],
                "startPage": 3,
                "endPage": 4,
                "taskType": "CONCEPT",
                "instructions": "Study second.",
                "effortMinMinutes": 10,
                "effortLikelyMinutes": 20,
                "effortMaxMinutes": 30,
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
                "prerequisiteLocalBlockIndexes": [3],
            },
        ],
        "warnings": [],
    })

    assert [topic.localTopicIndex for topic in extraction.topics] == [1, 2]
    assert [block.localBlockIndex for block in extraction.blocks] == [1, 2]
    assert extraction.blocks[0].topicIndexes == [1]
    assert extraction.blocks[1].topicIndexes == [2]
    assert extraction.blocks[1].prerequisiteLocalBlockIndexes == [1]
    assert "Repaired non-contiguous topic indexes from Gemini." in extraction.warnings
    assert "Repaired non-contiguous block indexes from Gemini." in extraction.warnings


def test_semantic_extraction_drops_extra_invalid_topic_refs_but_rejects_orphan_blocks():
    extraction = SemanticExtraction.model_validate({
        "title": "Lecture",
        "topics": [{"localTopicIndex": 1, "title": "Topic"}],
        "blocks": [
            {
                "localBlockIndex": 1,
                "title": "Block",
                "topicIndexes": [1, 99],
                "startPage": 1,
                "endPage": 2,
                "taskType": "CONCEPT",
                "instructions": "Study it.",
                "effortMinMinutes": 10,
                "effortLikelyMinutes": 20,
                "effortMaxMinutes": 30,
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
                "prerequisiteLocalBlockIndexes": [],
            }
        ],
        "warnings": [],
    })

    assert extraction.blocks[0].topicIndexes == [1]
    assert "Dropped invalid topic references for block 1: [99]." in extraction.warnings

    with pytest.raises(ValueError, match="Block topic indexes must reference local topics"):
        SemanticExtraction.model_validate({
            "title": "Lecture",
            "topics": [{"localTopicIndex": 1, "title": "Topic"}],
            "blocks": [
                {
                    "localBlockIndex": 1,
                    "title": "Block",
                    "topicIndexes": [99],
                    "startPage": 1,
                    "endPage": 2,
                    "taskType": "CONCEPT",
                    "instructions": "Study it.",
                    "effortMinMinutes": 10,
                    "effortLikelyMinutes": 20,
                    "effortMaxMinutes": 30,
                    "difficultyScore": 3,
                    "densityScore": 3,
                    "productionDemandScore": 3,
                    "prerequisiteLocalBlockIndexes": [],
                }
            ],
            "warnings": [],
        })

    with pytest.raises(ValueError, match="Block topic indexes must reference local topics"):
        SemanticExtraction.model_validate({
            "title": "Lecture",
            "topics": [{"localTopicIndex": 1, "title": "Topic"}],
            "blocks": [
                {
                    "localBlockIndex": 1,
                    "title": "Block",
                    "topicIndexes": [0],
                    "startPage": 1,
                    "endPage": 2,
                    "taskType": "CONCEPT",
                    "instructions": "Study it.",
                    "effortMinMinutes": 10,
                    "effortLikelyMinutes": 20,
                    "effortMaxMinutes": 30,
                    "difficultyScore": 3,
                    "densityScore": 3,
                    "productionDemandScore": 3,
                    "prerequisiteLocalBlockIndexes": [],
                }
            ],
            "warnings": [],
        })


def test_global_effort_calibration_updates_effort_without_changing_source_map(tmp_path):
    plan = GeneratedPlan.model_validate({
        "title": "Plan",
        "topics": [{"id": "t1", "title": "Topic", "order": 1}],
        "blocks": [
            {
                "id": "doc1-block-1",
                "title": "Foundation",
                "order": 1,
                "effortMinMinutes": 40,
                "effortLikelyMinutes": 60,
                "effortMaxMinutes": 90,
                "taskType": "CONCEPT",
                "instructions": "Learn it.",
                "topicIds": ["t1"],
                "sourceRefs": [{"documentId": "doc1", "startPage": 1, "endPage": 4, "sectionTitle": "A"}],
            }
        ],
    })
    calibration = GlobalEffortCalibration.model_validate({
        "adjustments": [
            {
                "blockId": "doc1-block-1",
                "relationship": "RECAP_REPETITION",
                "effortMinMinutes": 5,
                "effortLikelyMinutes": 15,
                "effortMaxMinutes": 25,
                "estimateConfidence": "HIGH",
                "reason": "Mostly repeats earlier material.",
            }
        ],
        "warnings": [],
    })

    calibrated = apply_global_effort_calibration(plan, calibration)

    assert calibrated.blocks[0].effortMinMinutes == 5
    assert calibrated.blocks[0].effortLikelyMinutes == 15
    assert calibrated.blocks[0].effortMaxMinutes == 25
    assert calibrated.blocks[0].estimateConfidence.value == "HIGH"
    assert calibrated.blocks[0].sourceRefs == plan.blocks[0].sourceRefs
    assert calibrated.blocks[0].title == "Foundation"
    assert calibrated.blocks[0].order == 1


def test_global_effort_calibration_ignores_unchanged_adjustments():
    plan = GeneratedPlan.model_validate({
        "title": "Plan",
        "topics": [{"id": "t1", "title": "Topic", "order": 1}],
        "blocks": [
            {
                "id": "doc1-block-1",
                "title": "Keep effort",
                "order": 1,
                "effortMinMinutes": 30,
                "effortLikelyMinutes": 45,
                "effortMaxMinutes": 60,
                "taskType": "CONCEPT",
                "instructions": "Learn it.",
                "topicIds": ["t1"],
            }
        ],
    })
    calibration = GlobalEffortCalibration.model_validate({
        "adjustments": [
            {
                "blockId": "doc1-block-1",
                "relationship": "UNCHANGED",
                "effortMinMinutes": 20,
                "effortLikelyMinutes": 20,
                "effortMaxMinutes": 20,
                "reason": "No adjustment needed.",
            }
        ],
        "warnings": [],
    })

    calibrated = apply_global_effort_calibration(plan, calibration)

    assert calibrated.blocks[0].effortMinMinutes == 30
    assert calibrated.blocks[0].effortLikelyMinutes == 45
    assert calibrated.blocks[0].effortMaxMinutes == 60


def test_global_effort_context_includes_instructions(tmp_path):
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    plan = GeneratedPlan.model_validate({
        "title": "Plan",
        "topics": [{"id": "t1", "title": "Topic", "order": 1}],
        "blocks": [
            {
                "id": "doc1-block-1",
                "title": "Worked examples",
                "order": 1,
                "effortMinMinutes": 20,
                "effortLikelyMinutes": 35,
                "effortMaxMinutes": 50,
                "taskType": "PRACTICE",
                "instructions": "Redo the worked factoring examples without looking.",
                "topicIds": ["t1"],
                "sourceRefs": [{"documentId": "doc1", "startPage": 4, "endPage": 8, "sectionTitle": "Examples"}],
            }
        ],
    })

    context = format_global_effort_context(
        plan,
        [SourceDocument(pdf, "Lecture.pdf", source_id="doc1", page_count=8)],
        Setup(goal="PrepareForExam", planTitle="Plan", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    )

    assert "Redo the worked factoring examples without looking." in context


@pytest.mark.asyncio
async def test_semantic_extraction_cancels_remaining_work_after_failure(tmp_path, monkeypatch):
    monkeypatch.setenv("REN_GEMINI_CONCURRENCY", "2")
    pdf1 = tmp_path / "bad.pdf"
    pdf2 = tmp_path / "slow.pdf"
    pdf1.write_bytes(b"%PDF-bad")
    pdf2.write_bytes(b"%PDF-slow")

    class FailingProvider(GeminiProvider):
        def __init__(self):
            self.cancelled = False

        async def _extract_document_semantic(self, index, document):
            if index == 1:
                await asyncio.sleep(0.01)
                raise ValueError("bad extraction")
            try:
                await asyncio.sleep(60)
            except asyncio.CancelledError:
                self.cancelled = True
                raise

    provider = FailingProvider()

    with pytest.raises(ValueError):
        await provider._extract_all_documents_semantic([
            SourceDocument(pdf1, "Bad.pdf", source_id="doc1"),
            SourceDocument(pdf2, "Slow.pdf", source_id="doc2"),
        ])

    assert provider.cancelled is True


def test_gemini_provider_uses_file_api_for_semantic_extraction(tmp_path, monkeypatch):
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    semantic_response = {
        "title": "Lecture",
        "topics": [{"localTopicIndex": 1, "title": "Topic"}],
        "blocks": [
            {
                "localBlockIndex": 1,
                "title": "Block",
                "topicIndexes": [1],
                "startPage": 1,
                "endPage": 2,
                "sectionTitle": "Section",
                "taskType": "CONCEPT",
                "instructions": "Study the block.",
                "completionCriteria": ["Explain the block."],
                "effortMinMinutes": 15,
                "effortLikelyMinutes": 25,
                "effortMaxMinutes": 40,
                "estimateConfidence": "HIGH",
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
                "keepTogetherLabel": "",
                "prerequisiteLocalBlockIndexes": [],
            }
        ],
        "warnings": [],
    }

    class Uploaded:
        name = "files/test"
        uri = "https://example.test/file"
        mime_type = "application/pdf"

    class FakeFiles:
        def __init__(self):
            self.uploaded = None
            self.deleted = None

        async def upload(self, file):
            self.uploaded = file
            return Uploaded()

        async def delete(self, name):
            self.deleted = name

    class FakeModels:
        def __init__(self):
            self.config = None
            self.contents = None

        async def generate_content(self, model, contents, config):
            self.config = config
            self.contents = contents
            return type("Response", (), {"text": json.dumps(semantic_response)})()

    class FakeAio:
        def __init__(self):
            self.files = FakeFiles()
            self.models = FakeModels()

    class FakeClient:
        def __init__(self):
            self.aio = FakeAio()

    def fail_from_bytes(**kwargs):
        raise AssertionError("semantic path should use Files API, not inline PDF bytes")

    monkeypatch.setattr(types.Part, "from_bytes", fail_from_bytes)
    provider = GeminiProvider.__new__(GeminiProvider)
    provider.client = FakeClient()
    provider.model = "test-model"

    import asyncio

    plan = asyncio.run(provider.create_plan(
        [SourceDocument(pdf, "Lecture 1.pdf", source_id="doc1", page_count=2)],
        Setup(goal="PrepareForExam", planTitle="Plan", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    ))

    assert provider.client.aio.files.uploaded == pdf
    assert provider.client.aio.files.deleted == "files/test"
    assert provider.client.aio.models.config.response_schema == GEMINI_SEMANTIC_SCHEMA
    assert plan.blocks[0].id == "doc1-block-1"
    assert plan.blocks[0].effortLikelyMinutes == 25
    assert plan.blocks[0].sourceRefs[0].materialGroupTitle == ""


def test_gemini_provider_reuses_prepared_gemini_file(tmp_path, monkeypatch):
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    semantic_response = {
        "title": "Lecture",
        "topics": [{"localTopicIndex": 1, "title": "Topic"}],
        "blocks": [
            {
                "localBlockIndex": 1,
                "title": "Block",
                "topicIndexes": [1],
                "startPage": 1,
                "endPage": 1,
                "sectionTitle": "Section",
                "taskType": "CONCEPT",
                "instructions": "Study the block.",
                "completionCriteria": ["Explain the block."],
                "effortMinMinutes": 15,
                "effortLikelyMinutes": 25,
                "effortMaxMinutes": 40,
                "estimateConfidence": "HIGH",
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
                "keepTogetherLabel": "",
                "prerequisiteLocalBlockIndexes": [],
            }
        ],
        "warnings": [],
    }

    class FakeFiles:
        def __init__(self):
            self.upload_count = 0
            self.delete_count = 0

        async def upload(self, file):
            self.upload_count += 1
            raise AssertionError("prepared semantic path should not upload again")

        async def delete(self, name):
            self.delete_count += 1

    class FakeModels:
        async def generate_content(self, model, contents, config):
            return type("Response", (), {"text": json.dumps(semantic_response)})()

    class FakeAio:
        def __init__(self):
            self.files = FakeFiles()
            self.models = FakeModels()

    class FakeClient:
        def __init__(self):
            self.aio = FakeAio()

    provider = GeminiProvider.__new__(GeminiProvider)
    provider.client = FakeClient()
    provider.model = "test-model"
    provider.semantic_cache = SemanticExtractionCache(tmp_path / "cache")

    import asyncio

    plan = asyncio.run(provider.create_plan(
        [SourceDocument(
            pdf,
            "Lecture 1.pdf",
            source_id="doc1",
            page_count=1,
            prepared_gemini_file=PreparedGeminiFile(
                name="files/prepared",
                uri="https://example.test/prepared",
                mime_type="application/pdf",
            ),
        )],
        Setup(goal="PrepareForExam", planTitle="Plan", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    ))

    assert provider.client.aio.files.upload_count == 0
    assert provider.client.aio.files.delete_count == 0
    assert plan.blocks[0].id == "doc1-block-1"


def test_gemini_provider_clears_stale_prepared_file_and_retries_upload(tmp_path, monkeypatch):
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    semantic_response = {
        "title": "Lecture",
        "topics": [{"localTopicIndex": 1, "title": "Topic"}],
        "blocks": [
            {
                "localBlockIndex": 1,
                "title": "Block",
                "topicIndexes": [1],
                "startPage": 1,
                "endPage": 1,
                "sectionTitle": "Section",
                "taskType": "CONCEPT",
                "instructions": "Study the block.",
                "completionCriteria": ["Explain the block."],
                "effortMinMinutes": 15,
                "effortLikelyMinutes": 25,
                "effortMaxMinutes": 40,
                "estimateConfidence": "HIGH",
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
                "keepTogetherLabel": "",
                "prerequisiteLocalBlockIndexes": [],
            }
        ],
        "warnings": [],
    }

    class Uploaded:
        name = "files/fresh"
        uri = "https://example.test/fresh"
        mime_type = "application/pdf"

    class FakeFiles:
        def __init__(self):
            self.upload_count = 0
            self.deleted = []

        async def upload(self, file):
            self.upload_count += 1
            return Uploaded()

        async def delete(self, name):
            self.deleted.append(name)

    class FakeModels:
        def __init__(self):
            self.calls = 0

        async def generate_content(self, model, contents, config):
            self.calls += 1
            if self.calls == 1:
                raise RuntimeError("stale file")
            return type("Response", (), {"text": json.dumps(semantic_response)})()

    class FakeAio:
        def __init__(self):
            self.files = FakeFiles()
            self.models = FakeModels()

    class FakeClient:
        def __init__(self):
            self.aio = FakeAio()

    provider = GeminiProvider.__new__(GeminiProvider)
    provider.client = FakeClient()
    provider.model = "test-model"
    provider.semantic_cache = None
    cleared = []

    import asyncio

    plan = asyncio.run(provider.create_plan(
        [SourceDocument(
            pdf,
            "Lecture 1.pdf",
            source_id="doc1",
            page_count=1,
            pdf_sha256="hash-123",
            prepared_gemini_file=PreparedGeminiFile(
                name="files/stale",
                uri="https://example.test/stale",
                mime_type="application/pdf",
            ),
            on_prepared_file_failed=cleared.append,
        )],
        Setup(goal="PrepareForExam", planTitle="Plan", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    ))

    assert cleared == ["Prepared Gemini file failed"]
    assert provider.client.aio.files.upload_count == 1
    assert provider.client.aio.files.deleted == ["files/fresh"]
    assert provider.client.aio.models.calls == 2
    assert plan.blocks[0].id == "doc1-block-1"


def test_gemini_provider_calibrates_multi_pdf_effort(tmp_path, monkeypatch):
    pdf1 = tmp_path / "lecture-1.pdf"
    pdf2 = tmp_path / "lecture-2.pdf"
    pdf1.write_bytes(b"%PDF-lecture-1")
    pdf2.write_bytes(b"%PDF-lecture-2")

    def semantic_response(title, block_title, likely):
        return {
            "title": title,
            "topics": [{"localTopicIndex": 1, "title": title}],
            "blocks": [
                {
                    "localBlockIndex": 1,
                    "title": block_title,
                    "topicIndexes": [1],
                    "startPage": 1,
                    "endPage": 3,
                    "sectionTitle": block_title,
                    "materialGroupTitle": title,
                    "taskType": "CONCEPT",
                    "instructions": "Study the block.",
                    "completionCriteria": ["Explain the block."],
                    "effortMinMinutes": max(20, likely - 10),
                    "effortLikelyMinutes": likely,
                    "effortMaxMinutes": likely + 20,
                    "estimateConfidence": "MEDIUM",
                    "difficultyScore": 3,
                    "densityScore": 3,
                    "productionDemandScore": 3,
                    "keepTogetherLabel": "",
                    "prerequisiteLocalBlockIndexes": [],
                }
            ],
            "warnings": [],
        }

    class FakeFiles:
        async def upload(self, file):
            raise AssertionError("prepared files should skip upload")

        async def delete(self, name):
            raise AssertionError("prepared files should not be deleted by extraction")

    class FakeModels:
        def __init__(self):
            self.schemas = []

        async def generate_content(self, model, contents, config):
            self.schemas.append(config.response_schema)
            if config.response_schema == GEMINI_SEMANTIC_SCHEMA:
                context = contents[0]
                if "Document 1" in context:
                    payload = semantic_response("Lecture 1", "Core concept", 60)
                else:
                    payload = semantic_response("Lecture 2", "Repeated application", 60)
                return type("Response", (), {"text": json.dumps(payload)})()
            if config.response_schema == GLOBAL_EFFORT_CALIBRATION_SCHEMA:
                payload = {
                    "adjustments": [
                        {
                            "blockId": "doc2-block-1",
                            "relationship": "APPLICATION",
                            "effortMinMinutes": 20,
                            "effortLikelyMinutes": 30,
                            "effortMaxMinutes": 45,
                            "estimateConfidence": "HIGH",
                            "reason": "Applies the first lecture's core concept.",
                        }
                    ],
                    "warnings": [],
                }
                return type("Response", (), {"text": json.dumps(payload)})()
            raise AssertionError("unexpected schema")

    class FakeAio:
        def __init__(self):
            self.files = FakeFiles()
            self.models = FakeModels()

    class FakeClient:
        def __init__(self):
            self.aio = FakeAio()

    monkeypatch.delenv("REN_GLOBAL_EFFORT_CALIBRATION", raising=False)
    provider = GeminiProvider.__new__(GeminiProvider)
    provider.client = FakeClient()
    provider.model = "test-model"
    provider.semantic_cache = None

    import asyncio

    plan = asyncio.run(provider.create_plan(
        [
            SourceDocument(
                pdf1,
                "Lecture 1.pdf",
                source_id="doc1",
                page_count=3,
                prepared_gemini_file=PreparedGeminiFile("files/one", "https://example.test/one", "application/pdf"),
            ),
            SourceDocument(
                pdf2,
                "Lecture 2.pdf",
                source_id="doc2",
                page_count=3,
                prepared_gemini_file=PreparedGeminiFile("files/two", "https://example.test/two", "application/pdf"),
            ),
        ],
        Setup(goal="PrepareForExam", planTitle="Plan", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    ))

    assert provider.client.aio.models.schemas.count(GEMINI_SEMANTIC_SCHEMA) == 2
    assert provider.client.aio.models.schemas.count(GLOBAL_EFFORT_CALIBRATION_SCHEMA) == 1
    assert [block.effortLikelyMinutes for block in plan.blocks] == [60, 30]
    assert plan.blocks[1].sourceRefs[0].documentId == "doc2"
    assert plan.blocks[1].sourceRefs[0].startPage == 1
    assert plan.blocks[1].estimateConfidence.value == "HIGH"


def test_gemini_provider_reuses_cached_semantic_extraction(tmp_path, monkeypatch):
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-same-lecture")
    semantic_response = {
        "title": "Lecture",
        "topics": [{"localTopicIndex": 1, "title": "Topic"}],
        "blocks": [
            {
                "localBlockIndex": 1,
                "title": "Block",
                "topicIndexes": [1],
                "startPage": 1,
                "endPage": 2,
                "sectionTitle": "Section",
                "taskType": "CONCEPT",
                "instructions": "Study the block.",
                "completionCriteria": ["Explain the block."],
                "effortMinMinutes": 15,
                "effortLikelyMinutes": 25,
                "effortMaxMinutes": 40,
                "estimateConfidence": "HIGH",
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
                "keepTogetherLabel": "",
                "prerequisiteLocalBlockIndexes": [],
            }
        ],
        "warnings": [],
    }

    class Uploaded:
        name = "files/test"
        uri = "https://example.test/file"
        mime_type = "application/pdf"

    class FakeFiles:
        def __init__(self):
            self.upload_count = 0
            self.delete_count = 0

        async def upload(self, file):
            self.upload_count += 1
            return Uploaded()

        async def delete(self, name):
            self.delete_count += 1

    class FakeModels:
        def __init__(self):
            self.calls = 0
            self.contents = None

        async def generate_content(self, model, contents, config):
            self.calls += 1
            self.contents = contents
            return type("Response", (), {"text": json.dumps(semantic_response)})()

    class FakeAio:
        def __init__(self):
            self.files = FakeFiles()
            self.models = FakeModels()

    class FakeClient:
        def __init__(self):
            self.aio = FakeAio()

    provider = GeminiProvider.__new__(GeminiProvider)
    provider.client = FakeClient()
    provider.model = "test-model"
    provider.semantic_cache = SemanticExtractionCache(tmp_path / "cache")

    import asyncio

    first = asyncio.run(provider.create_plan(
        [SourceDocument(pdf, "Lecture 1.pdf", source_id="doc1", page_count=2)],
        Setup(goal="PrepareForExam", planTitle="First plan", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"]),
    ))
    second = asyncio.run(provider.create_plan(
        [SourceDocument(pdf, "Lecture 1.pdf", source_id="doc1", page_count=2)],
        Setup(goal="PrepareForExam", planTitle="Second plan", deadline="InOneWeek", dailyStudyMinutes=45, studyDays=["Tuesday"]),
    ))

    assert provider.client.aio.files.upload_count == 1
    assert provider.client.aio.files.delete_count == 1
    assert provider.client.aio.models.calls == 1
    assert "First plan" not in provider.client.aio.models.contents[-1]
    assert first.title == "First plan"
    assert second.title == "Second plan"
    assert second.blocks[0].effortLikelyMinutes == 25
    assert list((tmp_path / "cache").rglob("*.json"))


def test_gemini_provider_skips_preupload_when_hash_cache_entry_exists(tmp_path):
    pdf = tmp_path / "lecture.pdf"
    pdf.write_bytes(b"%PDF-test")
    extraction = SemanticExtraction.model_validate({
        "title": "Lecture",
        "topics": [{"localTopicIndex": 1, "title": "Topic"}],
        "blocks": [
            {
                "localBlockIndex": 1,
                "title": "Block",
                "topicIndexes": [1],
                "startPage": 1,
                "endPage": 1,
                "taskType": "CONCEPT",
                "instructions": "Study the block.",
                "effortMinMinutes": 10,
                "effortLikelyMinutes": 20,
                "effortMaxMinutes": 30,
                "difficultyScore": 3,
                "densityScore": 3,
                "productionDemandScore": 3,
            }
        ],
        "warnings": [],
    })
    cache = SemanticExtractionCache(tmp_path / "cache")
    document = SourceDocument(pdf, "Lecture.pdf", pdf_sha256="hash-123")
    cache.put(document, "test-model", "hash-123", extraction)
    provider = GeminiProvider.__new__(GeminiProvider)
    provider.model = "test-model"
    provider.semantic_cache = cache

    assert provider.should_prepare_document(document) is False


class RecordingProvider(AIProvider):
    """Captures pdfs and setup for inspection without calling Gemini."""

    def __init__(self):
        self.documents = None
        self.setup = None

    async def create_plan(self, documents: list[SourceDocument], setup: Setup) -> GeneratedPlan:
        self.documents = documents
        self.setup = setup
        return GeneratedPlan(
            title="Test",
            topics=[{"id": "t1", "title": "Topic", "order": 1}],
            blocks=[{"id": "b1", "title": "Block", "order": 1,
                      "effortMinMinutes": 10, "effortLikelyMinutes": 20, "effortMaxMinutes": 30,
                      "taskType": "REVIEW",
                      "instructions": "Read", "topicIds": ["t1"]}],
        )


def test_create_plan_with_multiple_pdfs():
    import tempfile
    p1 = tempfile.NamedTemporaryFile(suffix=".pdf", delete=False)
    p1.write(b"fake-pdf-1")
    p1.close()
    p2 = tempfile.NamedTemporaryFile(suffix=".pdf", delete=False)
    p2.write(b"fake-pdf-2")
    p2.close()

    import asyncio
    provider = RecordingProvider()
    setup = Setup(goal="PrepareForExam", planTitle="HCI final", deadline="InOneWeek", dailyStudyMinutes=30, studyDays=["Monday"])
    result = asyncio.run(provider.create_plan([
        SourceDocument(Path(p1.name), "Lecture 1.pdf"),
        SourceDocument(Path(p2.name), "Lecture 2.pdf"),
    ], setup))

    assert len(provider.documents) == 2
    assert provider.documents[0].path.read_bytes() == b"fake-pdf-1"
    assert provider.documents[1].path.read_bytes() == b"fake-pdf-2"
    assert provider.documents[0].filename == "Lecture 1.pdf"
    assert provider.setup == setup
    assert provider.setup.planTitle == "HCI final"
    assert result.title == "Test"

    Path(p1.name).unlink(missing_ok=True)
    Path(p2.name).unlink(missing_ok=True)


def test_format_document_context_includes_page_anchors(tmp_path):
    path = tmp_path / "lecture.pdf"
    path.write_bytes(b"%PDF-test")
    document = SourceDocument(
        path=path,
        filename="Lecture 1.pdf",
        source_id="doc1",
        page_count=12,
        page_anchors=[
            PdfPageAnchor(
                page=3,
                word_count=42,
                text="Gulfs of Execution | Gulf of evaluation",
                heading="Gulfs of Execution",
                cues=("Gulf of evaluation",),
            )
        ],
    )

    context = format_document_context(1, document)

    assert "Document 1 (doc1): Lecture 1.pdf, 12 pages" in context
    assert "Compact page map extracted locally:" in context
    assert "p. 3 (42 words) | heading: Gulfs of Execution | cues: Gulf of evaluation" in context
