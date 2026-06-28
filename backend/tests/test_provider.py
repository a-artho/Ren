from pathlib import Path
import json
from google.genai import types

from app.pdf_parser import PdfPageAnchor
from app.provider import (
    AIProvider,
    GEMINI_PLAN_SCHEMA,
    GEMINI_SEMANTIC_SCHEMA,
    GeminiProvider,
    SemanticExtraction,
    SourceDocument,
    format_document_context,
    semantic_extractions_to_generated_plan,
)
from app.models import Difficulty, GeneratedPlan, Setup


def test_semantic_schema_is_accepted_by_installed_gemini_sdk():
    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=GEMINI_PLAN_SCHEMA,
    )

    assert config.response_schema == GEMINI_SEMANTIC_SCHEMA


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
                "splitAllowed": True,
                "continuityLabel": "modes",
                "prerequisiteLocalBlockIndexes": [],
            },
            {
                "localBlockIndex": 2,
                "title": "AES structure",
                "topicIndexes": [2],
                "startPage": 9,
                "endPage": 12,
                "sectionTitle": "AES",
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
                "splitAllowed": False,
                "continuityLabel": "",
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
    assert plan.blocks[0].durationMinutes == 35
    assert plan.blocks[0].estimatedMinutes == 35
    assert plan.blocks[0].effortMaxMinutes == 50
    assert plan.blocks[0].difficulty == Difficulty.HEAVY
    assert plan.blocks[0].sourceRefs[0].documentId == "doc1"
    assert plan.blocks[1].dependencies == ["doc1-block-1"]
    assert plan.blocks[1].status.value == "NOT_STARTED"
    assert plan.extractionWarnings[0].message == "Page labels were ambiguous."


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
                "splitAllowed": True,
                "continuityLabel": "",
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

    monkeypatch.delenv("REN_EXTRACTION_MODE", raising=False)
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
    assert plan.blocks[0].durationMinutes == 25


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
            blocks=[{"id": "b1", "title": "Block", "order": 1, "durationMinutes": 20,
                      "minimumUsefulMinutes": 10, "taskType": "REVIEW",
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
        page_anchors=[PdfPageAnchor(page=3, word_count=42, text="HCI has gulfs of execution and evaluation.")],
    )

    context = format_document_context(1, document)

    assert "Document 1 (doc1): Lecture 1.pdf, 12 pages" in context
    assert "Page 3 (42 words): HCI has gulfs" in context
