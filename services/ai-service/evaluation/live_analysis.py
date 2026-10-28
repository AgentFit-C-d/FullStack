"""Run one local PDF through an extraction pipeline and save a comparable result.

This is an evaluation tool. It does not call the Core API or change production
analysis attempts.
"""

import argparse
import asyncio
import json
import os
import time
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.analysis.canonical_document import CanonicalDocument, CanonicalPage, render_canonical_document
from app.analysis.extraction import ExtractedDocument, SourcePage, extract_document, pdf_page_count
from app.analysis.grounding import validate_grounding
from app.analysis.providers.document_parse import DocumentParseProvider
from app.analysis.providers.upstage import UpstageProvider


def load_dotenv_value(path: Path, key: str) -> str | None:
    if not path.is_file():
        return None
    prefix = f"{key}="
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith(prefix):
            return line[len(prefix):].strip()
    return None


def _empty_pdf_source(document_id: str, data: bytes) -> ExtractedDocument:
    return ExtractedDocument(
        document_id,
        "pdf",
        "",
        tuple(SourcePage(number, 0, 0) for number in range(1, pdf_page_count(data) + 1)),
    )


async def analyze_pdf(data: bytes, document_id: str, pipeline: str, api_key: str) -> dict:
    started = time.perf_counter()
    if pipeline == "pypdf":
        document = extract_document(data, "pdf", document_id, layout=True)
        canonical = CanonicalDocument(data, tuple(CanonicalPage(page.number, page.start, page.end) for page in document.pages))
    elif pipeline == "document-parse":
        source = _empty_pdf_source(document_id, data)
        document = await DocumentParseProvider(api_key).parse(data, source, time.monotonic() + 175)
        canonical = render_canonical_document(document)
    else:
        raise ValueError("SUPPORTED_PIPELINES: pypdf, document-parse")

    provider = UpstageProvider(api_key)
    output = await provider.extract(document, time.monotonic() + 175, canonical)
    return validate_grounding(
        output,
        document,
        "upstage",
        provider.model,
        round((time.perf_counter() - started) * 1000),
        input_format="original-pdf" if pipeline == "pypdf" else None,
    )


def build_record(document_id: str, pipeline: str, page_count: int, analysis: dict, total_cost: float | None) -> dict:
    return {
        "documentId": document_id,
        "pipeline": pipeline,
        "pageCount": page_count,
        "latencyMs": analysis["modelMetadata"]["latencyMs"],
        "totalCost": total_cost,
        "analysis": analysis,
    }


async def _main_async(args: argparse.Namespace) -> None:
    api_key = os.environ.get("UPSTAGE_API_KEY") or load_dotenv_value(Path(__file__).resolve().parents[3] / ".env", "UPSTAGE_API_KEY")
    if not api_key:
        raise SystemExit("UPSTAGE_API_KEY is not configured")

    data = args.document.read_bytes()
    result = await analyze_pdf(data, args.document_id, args.pipeline, api_key)
    record = build_record(args.document_id, args.pipeline, pdf_page_count(data), result, args.total_cost)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"status": "SUCCEEDED", "output": str(args.output), "latencyMs": record["latencyMs"]}, ensure_ascii=False))


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate one PDF with a real Upstage analysis call.")
    parser.add_argument("--document", type=Path, required=True)
    parser.add_argument("--document-id", required=True)
    parser.add_argument("--pipeline", choices=("pypdf", "document-parse"), required=True)
    parser.add_argument("--total-cost", type=float, help="Cost confirmed from Upstage Console; omit it when the cost is still unknown.")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    asyncio.run(_main_async(args))


if __name__ == "__main__":
    main()
