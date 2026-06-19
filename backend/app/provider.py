from __future__ import annotations

import json
import logging
from abc import ABC, abstractmethod
from pathlib import Path

from .models import GeneratedPlan, Setup

logger = logging.getLogger("ren")

GEMINI_PLAN_SCHEMA = {
    "type": "object",
    "properties": {
        "topics": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "title": {"type": "string"},
                    "order": {"type": "integer"},
                },
                "required": ["id", "title", "order"],
            },
        },
        "blocks": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "id": {"type": "string"},
                    "title": {"type": "string"},
                    "order": {"type": "integer"},
                    "durationMinutes": {"type": "integer"},
                    "instructions": {"type": "string"},
                    "topicIds": {"type": "array", "items": {"type": "string"}},
                },
                "required": [
                    "id", "title", "order", "durationMinutes", "instructions", "topicIds",
                ],
            },
        },
    },
    "required": ["topics", "blocks"],
}


class AIProvider(ABC):
    @abstractmethod
    async def create_plan(self, pdf: Path, setup: Setup) -> GeneratedPlan: ...


class GeminiProvider(AIProvider):
    def __init__(self, api_key: str, model: str = "gemini-2.5-flash"):
        from google import genai
        self.client = genai.Client(api_key=api_key)
        self.model = model

    async def create_plan(self, pdf: Path, setup: Setup) -> GeneratedPlan:
        from google.genai import types
        prompt = (
            "Create a faithful study plan from this PDF. Use only material in the document. "
            "Return JSON matching the supplied schema. Topics and blocks must use contiguous "
            "1-based order. Each block must reference valid topic IDs. Keep instructions short "
            f"and actionable. Learner setup: {setup.model_dump_json()}"
        )
        response = await self.client.aio.models.generate_content(
            model=self.model,
            contents=[types.Part.from_bytes(data=pdf.read_bytes(), mime_type="application/pdf"), prompt],
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=GEMINI_PLAN_SCHEMA,
                temperature=0.2,
            ),
        )
        raw = response.text
        logger.info("Gemini raw response (first 500 chars): %s", raw[:500] if raw else "<empty>")
        try:
            parsed = json.loads(raw)
        except (json.JSONDecodeError, TypeError) as exc:
            logger.error("Gemini returned invalid JSON: %s", exc)
            raise ValueError(f"Invalid JSON from Gemini: {exc}") from exc
        try:
            return GeneratedPlan.model_validate(parsed)
        except Exception as exc:
            logger.error("Pydantic validation failed: %s", exc)
            raise ValueError(f"Plan validation failed: {exc}") from exc
