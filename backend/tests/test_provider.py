from google.genai import types

from app.provider import GEMINI_PLAN_SCHEMA


def test_plan_schema_is_accepted_by_installed_gemini_sdk():
    config = types.GenerateContentConfig(
        response_mime_type="application/json",
        response_schema=GEMINI_PLAN_SCHEMA,
    )

    assert config.response_schema == GEMINI_PLAN_SCHEMA
