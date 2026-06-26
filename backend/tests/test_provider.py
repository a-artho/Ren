from pathlib import Path
from google.genai import types

from app.provider import AIProvider, GEMINI_PLAN_SCHEMA
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
        self.pdfs = None
        self.setup = None

    async def create_plan(self, pdfs: list[Path], setup: Setup) -> GeneratedPlan:
        self.pdfs = pdfs
        self.setup = setup
        return GeneratedPlan(
            title="Test",
            topics=[{"id": "t1", "title": "Topic", "order": 1}],
            blocks=[{"id": "b1", "title": "Block", "order": 1, "durationMinutes": 20,
                      "minimumUsefulMinutes": 10, "priority": "MEDIUM", "taskType": "REVIEW",
                      "priorityReason": "Foundation", "isSkippable": True,
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
    setup = Setup(goal="x", deadline="y", dailyStudyMinutes=30, studyDays=["Monday"])
    result = asyncio.run(provider.create_plan([Path(p1.name), Path(p2.name)], setup))

    assert len(provider.pdfs) == 2
    assert provider.pdfs[0].read_bytes() == b"fake-pdf-1"
    assert provider.pdfs[1].read_bytes() == b"fake-pdf-2"
    assert provider.setup == setup
    assert result.title == "Test"

    Path(p1.name).unlink(missing_ok=True)
    Path(p2.name).unlink(missing_ok=True)
