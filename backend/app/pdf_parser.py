from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path


MAX_PAGE_SNIPPET_CHARS = 900
MAX_ANCHOR_CHARS_PER_DOCUMENT = 30_000
MAX_ANCHOR_PAGES_PER_DOCUMENT = 120


@dataclass(frozen=True)
class PdfPageAnchor:
    page: int
    word_count: int
    text: str


@dataclass(frozen=True)
class PdfMetadata:
    page_count: int | None
    page_anchors: list[PdfPageAnchor]
    anchors_truncated: bool = False
    parser_error: str | None = None


def parse_pdf_metadata(path: Path) -> PdfMetadata:
    """Extract lightweight page anchors for Gemini grounding.

    This is intentionally not OCR and not a layout engine. It should improve
    page grounding when text extraction is available, while falling back safely
    for scanned/weird PDFs.
    """

    try:
        from pypdf import PdfReader

        reader = PdfReader(str(path))
        page_count = len(reader.pages) or fallback_page_count(path)
        anchors: list[PdfPageAnchor] = []
        total_chars = 0
        truncated = False
        for index, page in enumerate(reader.pages, start=1):
            if index > MAX_ANCHOR_PAGES_PER_DOCUMENT or total_chars >= MAX_ANCHOR_CHARS_PER_DOCUMENT:
                truncated = True
                break
            try:
                text = clean_text(page.extract_text() or "")
            except Exception:
                text = ""
            if not text:
                continue
            snippet = text[:MAX_PAGE_SNIPPET_CHARS]
            total_chars += len(snippet)
            anchors.append(
                PdfPageAnchor(
                    page=index,
                    word_count=len(text.split()),
                    text=snippet,
                )
            )
        return PdfMetadata(page_count=page_count, page_anchors=anchors, anchors_truncated=truncated)
    except Exception as exc:
        return PdfMetadata(
            page_count=fallback_page_count(path),
            page_anchors=[],
            parser_error=str(exc)[:160],
        )


def fallback_page_count(path: Path) -> int | None:
    try:
        data = path.read_bytes()
    except OSError:
        return None
    count = len(re.findall(rb"/Type\s*/Page\b", data))
    return count or None


def clean_text(value: str) -> str:
    return re.sub(r"\s+", " ", value).strip()
