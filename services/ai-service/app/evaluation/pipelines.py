from dataclasses import dataclass
from pathlib import Path
import shutil
import tempfile
import time
from typing import Any

from ..analysis.extraction import ExtractedDocument, SourcePage, extract_document, pdf_page_count

SUPPORTED_PIPELINES = ("pypdf", "pymupdf", "ocrmypdf", "document-parse", "parse-ocr", "hybrid-fallback")


@dataclass(frozen=True)
class PipelineRun:
    document_id: str
    pipeline: str
    document: ExtractedDocument | None
    page_count: int
    latency_ms: int
    error_code: str | None = None


def classify_pipeline_error(error: Exception) -> str:
    code = str(error)
    if code.startswith(("DEPENDENCY_NOT_INSTALLED:", "EXTERNAL_TOOL_MISSING:")):
        return code
    return {
        "NO_TEXT_LAYER": "TEXT_LAYER_MISSING",
        "INVALID_PDF": "INVALID_INPUT",
        "LOCKED_PDF": "LOCKED_INPUT",
        "TOO_MANY_PAGES": "PAGE_LIMIT_EXCEEDED",
    }.get(code, "PIPELINE_FAILED")


def _source_for(data: bytes, document_id: str) -> ExtractedDocument:
    pages = pdf_page_count(data)
    return ExtractedDocument(document_id, "pdf", "", tuple(SourcePage(page, 0, 0) for page in range(1, pages + 1)))


def _extract_pymupdf(data: bytes, document_id: str) -> ExtractedDocument:
    try:
        import pymupdf as fitz
    except ImportError as error:
        raise ValueError("DEPENDENCY_NOT_INSTALLED:PyMuPDF") from error
    pdf = fitz.open(stream=data, filetype="pdf")
    if pdf.is_encrypted or not pdf.page_count:
        raise ValueError("INVALID_PDF")
    parts, pages, offset = [], [], 0
    for number, page in enumerate(pdf, 1):
        text = page.get_text("text")
        if not text.strip():
            raise ValueError("NO_TEXT_LAYER")
        if number > 1:
            parts.append("\n\f\n"); offset += 3
        pages.append(SourcePage(number, offset, offset + len(text)))
        parts.append(text); offset += len(text)
    return ExtractedDocument(document_id, "pdf", "".join(parts), tuple(pages))


def _extract_ocrmypdf(data: bytes, document_id: str) -> ExtractedDocument:
    if not shutil.which("tesseract") or not (shutil.which("gswin64c") or shutil.which("gs")):
        raise ValueError("EXTERNAL_TOOL_MISSING:OCRmyPDF requires Tesseract and Ghostscript")
    try:
        import ocrmypdf
    except ImportError as error:
        raise ValueError("DEPENDENCY_NOT_INSTALLED:OCRmyPDF") from error
    with tempfile.TemporaryDirectory(prefix="agentfit-ocr-") as directory:
        source = Path(directory) / "source.pdf"
        output = Path(directory) / "ocr.pdf"
        source.write_bytes(data)
        ocrmypdf.ocr(str(source), str(output), skip_text=True, deskew=True, progress_bar=False)
        return extract_document(output.read_bytes(), "pdf", document_id, layout=True)


async def extract_for_pipeline(
    data: bytes,
    document_id: str,
    pipeline: str,
    *,
    document_parse: Any = None,
    deadline: float | None = None,
) -> PipelineRun:
    if pipeline not in SUPPORTED_PIPELINES:
        raise ValueError("UNSUPPORTED_PIPELINE")
    started = time.perf_counter()
    try:
        if pipeline == "pypdf":
            document = extract_document(data, "pdf", document_id, layout=True)
        elif pipeline == "pymupdf":
            document = _extract_pymupdf(data, document_id)
        elif pipeline == "ocrmypdf":
            document = _extract_ocrmypdf(data, document_id)
        else:
            if document_parse is None or deadline is None:
                raise ValueError("DOCUMENT_PARSE_ADAPTER_REQUIRED")
            source = _source_for(data, document_id)
            document = await document_parse.parse(data, source, deadline)
        return PipelineRun(document_id, pipeline, document, len(document.pages), round((time.perf_counter() - started) * 1000))
    except Exception as error:
        if pipeline == "hybrid-fallback" and classify_pipeline_error(error) == "TEXT_LAYER_MISSING" and document_parse is not None and deadline is not None:
            source = _source_for(data, document_id)
            document = await document_parse.parse(data, source, deadline)
            return PipelineRun(document_id, pipeline, document, len(document.pages), round((time.perf_counter() - started) * 1000))
        return PipelineRun(document_id, pipeline, None, 0, round((time.perf_counter() - started) * 1000), classify_pipeline_error(error))
