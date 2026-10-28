"""Run a metadata-only Document Parse connectivity probe for evaluation PDFs."""
import asyncio
import argparse
import json
import os
from pathlib import Path
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.analysis.extraction import ExtractedDocument, SourcePage, pdf_page_count
from app.analysis.providers.document_parse import DocumentParseProvider


async def probe(path: Path) -> dict:
    started = time.perf_counter()
    try:
        data = path.read_bytes()
        pages = pdf_page_count(data)
        source = ExtractedDocument(path.name, "pdf", "", tuple(SourcePage(page, 0, 0) for page in range(1, pages + 1)))
        document = await DocumentParseProvider(os.environ["UPSTAGE_API_KEY"]).parse(data, source, time.monotonic() + 90)
        return {"file": path.name, "status": "SUCCEEDED", "pages": len(document.pages), "characters": len(document.text), "latencyMs": round((time.perf_counter() - started) * 1000)}
    except Exception as error:
        return {"file": path.name, "status": "FAILED", "error": str(error), "latencyMs": round((time.perf_counter() - started) * 1000)}


async def main() -> None:
    if not os.environ.get("UPSTAGE_API_KEY"):
        raise SystemExit("UPSTAGE_API_KEY is missing")
    parser = argparse.ArgumentParser()
    parser.add_argument("--file", action="append")
    args = parser.parse_args()
    paths = [Path(value) for value in args.file] if args.file else sorted(Path("evaluation/documents").glob("*.pdf"))
    for path in paths:
        print(json.dumps(await probe(path), ensure_ascii=True), flush=True)


if __name__ == "__main__":
    asyncio.run(main())
