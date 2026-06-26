from pypdf import PdfWriter

from app.pdf_parser import clean_text, fallback_page_count, parse_pdf_metadata


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
