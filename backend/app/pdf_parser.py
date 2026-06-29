from __future__ import annotations

import re
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path


MAX_PAGE_ANCHOR_TEXT_CHARS = 220
MAX_PAGE_CUE_LINES = 3
MAX_PAGE_CUE_CHARS = 90
MAX_PAGE_HEADING_CHARS = 90
MAX_ANCHOR_CHARS_PER_DOCUMENT = 12_000
MAX_ANCHOR_PAGES_PER_DOCUMENT = 80
REPEATED_LINE_MIN_PAGES = 3
REPEATED_LINE_FRACTION = 0.25


@dataclass(frozen=True)
class PdfPageAnchor:
    page: int
    word_count: int
    text: str
    heading: str = ""
    cues: tuple[str, ...] = field(default_factory=tuple)


@dataclass(frozen=True)
class PdfMetadata:
    page_count: int | None
    page_anchors: list[PdfPageAnchor]
    anchors_truncated: bool = False
    parser_error: str | None = None


def parse_pdf_metadata(path: Path) -> PdfMetadata:
    """Extract lightweight page-map anchors for Gemini grounding.

    This is intentionally not OCR, a layout engine, or a local summarizer. It
    keeps only source-grounded navigation cues so Gemini can orient page ranges
    and section boundaries from the attached PDF with less prompt noise.
    """

    try:
        from pypdf import PdfReader

        reader = PdfReader(str(path))
        page_count = len(reader.pages) or fallback_page_count(path)
        pages: list[tuple[int, str, str, list[str]]] = []
        truncated = False

        for index, page in enumerate(reader.pages, start=1):
            if index > MAX_ANCHOR_PAGES_PER_DOCUMENT:
                truncated = True
                break
            try:
                raw_text = page.extract_text() or ""
            except Exception:
                raw_text = ""
            text = clean_text(raw_text)
            if not text:
                continue
            pages.append((index, text, raw_text, source_lines(raw_text)))

        repeated_lines = repeated_page_lines([page_lines for _, _, _, page_lines in pages])
        anchors: list[PdfPageAnchor] = []
        total_chars = 0
        for index, text, _raw_text, page_lines in pages:
            anchor = build_page_anchor(index, text, page_lines, repeated_lines)
            if not anchor:
                continue
            if total_chars + len(anchor.text) > MAX_ANCHOR_CHARS_PER_DOCUMENT:
                truncated = True
                break
            total_chars += len(anchor.text)
            anchors.append(anchor)

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


def source_lines(value: str) -> list[str]:
    lines = [clean_text(line) for line in value.replace("\r", "\n").split("\n")]
    lines = [line for line in lines if not is_noise_line(line)]
    if len(lines) > 1:
        return dedupe_preserving_order(lines)

    text = clean_text(value)
    if not text:
        return []
    fragments = re.split(r"(?<=[.!?])\s+|\u2022\s*", text)
    fallback_lines = [clean_text(fragment) for fragment in fragments if not is_noise_line(fragment)]
    return dedupe_preserving_order(fallback_lines)


def repeated_page_lines(page_lines: list[list[str]]) -> set[str]:
    if not page_lines:
        return set()
    counts: Counter[str] = Counter()
    for lines in page_lines:
        counts.update({canonical_line(line) for line in lines if repeated_line_candidate(line)})
    return {
        line
        for line, count in counts.items()
        if count >= REPEATED_LINE_MIN_PAGES and count / len(page_lines) >= REPEATED_LINE_FRACTION
    }


def build_page_anchor(
    page: int,
    text: str,
    lines: list[str],
    repeated_lines: set[str] | None = None,
) -> PdfPageAnchor | None:
    cleaned = clean_text(text)
    if not cleaned:
        return None

    repeated_lines = repeated_lines or set()
    usable_lines = [
        line
        for line in lines
        if canonical_line(line) not in repeated_lines and not is_noise_line(line)
    ]
    heading = choose_heading(usable_lines)
    cues = choose_cues(usable_lines, heading)

    if heading or cues:
        anchor_text = compact_join([heading, *cues], MAX_PAGE_ANCHOR_TEXT_CHARS)
    else:
        anchor_text = truncate_text(cleaned, MAX_PAGE_ANCHOR_TEXT_CHARS)

    return PdfPageAnchor(
        page=page,
        word_count=len(cleaned.split()),
        text=anchor_text,
        heading=heading,
        cues=tuple(cues),
    )


def choose_heading(lines: list[str]) -> str:
    for line in lines[:8]:
        candidate = truncate_text(line, MAX_PAGE_HEADING_CHARS)
        if is_meaningful_source_line(candidate):
            return candidate
    return ""


def choose_cues(lines: list[str], heading: str) -> list[str]:
    cues: list[str] = []
    seen = {canonical_line(heading)} if heading else set()
    for line in lines:
        canonical = canonical_line(line)
        if canonical in seen or not is_meaningful_source_line(line):
            continue
        cues.append(truncate_text(line, MAX_PAGE_CUE_CHARS))
        seen.add(canonical)
        if len(cues) >= MAX_PAGE_CUE_LINES:
            break
    return cues


def is_meaningful_source_line(value: str) -> bool:
    line = clean_text(value)
    if not line or is_noise_line(line):
        return False
    return bool(re.search(r"[A-Za-z]", line))


def is_noise_line(value: str) -> bool:
    line = clean_text(value)
    if not line:
        return True
    if not re.search(r"[A-Za-z]", line):
        return True
    lowered = line.lower()
    if re.fullmatch(r"(page|slide)\s*\d+(\s*(of|/)\s*\d+)?", lowered):
        return True
    if re.fullmatch(r"\d+\s*/\s*\d+", lowered):
        return True
    return False


def repeated_line_candidate(value: str) -> bool:
    line = clean_text(value)
    return 3 <= len(line) <= 120 and bool(re.search(r"[A-Za-z]", line))


def canonical_line(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def dedupe_preserving_order(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        canonical = canonical_line(value)
        if not canonical or canonical in seen:
            continue
        seen.add(canonical)
        result.append(value)
    return result


def compact_join(values: list[str], limit: int) -> str:
    result = ""
    for value in [item for item in values if item]:
        next_result = value if not result else f"{result} | {value}"
        if len(next_result) > limit:
            if not result:
                return truncate_text(value, limit)
            break
        result = next_result
    return result


def truncate_text(value: str, limit: int) -> str:
    text = clean_text(value)
    if len(text) <= limit:
        return text
    truncated = text[:limit].rstrip()
    if " " in truncated:
        truncated = truncated.rsplit(" ", 1)[0]
    return f"{truncated}..."
