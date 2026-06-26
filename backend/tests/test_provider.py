from pathlib import Path
from google.genai import types

from app.pdf_parser import PdfPageAnchor
from app.provider import AIProvider, GEMINI_PLAN_SCHEMA, SourceDocument, format_document_context
from app.models import GeneratedPlan, Setup


def test_plan_schema_is_accepted_by_installed_gemini_sdk():
    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=GEMINI_PLAN_SCHEMA,
    )

    assert config.response_schema == GEMINI_PLAN_SCHEMA


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
