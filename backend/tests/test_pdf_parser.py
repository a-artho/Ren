from pypdf import PdfWriter

from app.pdf_parser import (
    build_page_anchor,
    clean_text,
    fallback_page_count,
    parse_pdf_metadata,
    repeated_page_lines,
    source_lines,
)


def test_parse_pdf_metadata_reads_real_page_count(tmp_path):
    path = tmp_path / "blank.pdf"
    writer = PdfWriter()
    writer.add_blank_page(width=72, height=72)
    writer.add_blank_page(width=72, height=72)
    with path.open("wb") as output:
        writer.write(output)

    metadata = parse_pdf_metadata(path)

    assert metadata.page_count == 2
    assert metadata.page_anchors == []
    assert metadata.parser_error is None


def test_parse_pdf_metadata_falls_back_for_invalid_pdf(tmp_path):
    path = tmp_path / "broken.pdf"
    path.write_bytes(b"%PDF-test\n/Type /Page\n")

    metadata = parse_pdf_metadata(path)

    assert metadata.page_count == 1
    assert metadata.page_anchors == []
    assert metadata.parser_error


def test_fallback_page_count_ignores_pages_plural(tmp_path):
    path = tmp_path / "pages.pdf"
    path.write_bytes(b"/Type /Pages\n/Type /Page\n")

    assert fallback_page_count(path) == 1


def test_clean_text_collapses_whitespace():
    assert clean_text("  one\n\n two\t three  ") == "one two three"


def test_build_page_anchor_keeps_source_heading_and_compact_cues():
    text = """
    Lecture 9 - Project logistics
    Development Lifecycle
    Requirements analysis and prototyping
    Evaluation standards and submission criteria
    """

    anchor = build_page_anchor(4, text, source_lines(text))

    assert anchor is not None
    assert anchor.heading == "Lecture 9 - Project logistics"
    assert "Development Lifecycle" in anchor.cues
    assert "Requirements analysis and prototyping" in anchor.cues
    assert len(anchor.text) <= 220


def test_repeated_page_lines_filters_document_furniture_from_anchor():
    pages = [
        source_lines("HCI Final Slides\nClassical Ciphers\nCaesar shift examples"),
        source_lines("HCI Final Slides\nTransposition Ciphers\nColumnar examples"),
        source_lines("HCI Final Slides\nModern Block Ciphers\nDES rounds"),
    ]

    repeated = repeated_page_lines(pages)
    anchor = build_page_anchor(2, " ".join(pages[1]), pages[1], repeated)

    assert "hci final slides" in repeated
    assert anchor is not None
    assert anchor.heading == "Transposition Ciphers"
    assert "HCI Final Slides" not in anchor.text
