import asyncio
import argparse
import json
import os
from pathlib import Path
import sys

import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import app.analysis.providers.document_parse as document_parse_module
from app.analysis.providers.document_parse import _PlainHtml, _StructuredHtml, parse_document_response
from app.analysis.secret_guard import assert_no_secrets


async def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("path")
    path = Path(parser.parse_args().path)
    timeout = httpx.Timeout(60.0, connect=10.0)
    async with httpx.AsyncClient(timeout=timeout, trust_env=False) as client:
        with path.open("rb") as source:
            response = await client.post(
                "https://api.upstage.ai/v1/document-digitization",
                headers={"Authorization": f"Bearer {os.environ['UPSTAGE_API_KEY']}"},
                files={"document": (path.name, source, "application/pdf")},
                data={"model": "document-parse", "mode": "standard", "ocr": "auto", "output_formats": "[\"html\"]", "coordinates": "false"},
            )
    print(json.dumps({"status": response.status_code, "contentType": response.headers.get("content-type"), "bytes": len(response.content)}))
    if response.status_code != 200:
        print(json.dumps({"errorKeys": sorted(response.json().keys())}))
        return
    body = response.json()
    elements = body.get("elements")
    first = elements[0] if isinstance(elements, list) and elements else {}
    print(json.dumps({"keys": sorted(body.keys()), "usage": body.get("usage"), "elementsType": type(elements).__name__, "elementsCount": len(elements) if isinstance(elements, list) else None, "firstKeys": sorted(first.keys()), "firstPageType": type(first.get("page")).__name__, "firstIdType": type(first.get("id")).__name__, "firstCategoryType": type(first.get("category")).__name__, "firstContentType": type(first.get("content")).__name__, "firstContentKeys": sorted(first.get("content", {}).keys()) if isinstance(first.get("content"), dict) else None}))
    element_errors = {}
    for index, element in enumerate(elements):
        try:
            parsed_text = _StructuredHtml().text(element["content"]["html"])
            if not parsed_text:
                print(json.dumps({"emptyParsedElement": index, "category": element.get("category"), "page": element.get("page")}))
        except Exception as error:
            key = f"{element.get('category')}:{type(error).__name__}:{str(error)}"
            element_errors[key] = element_errors.get(key, 0) + 1
    if element_errors:
        print(json.dumps({"elementErrors": element_errors}))
    print(json.dumps({"badIds": sum(type(element.get("id")) is not int or element.get("id") < 0 for element in elements), "badPages": sum(type(element.get("page")) is not int or element.get("page") < 1 for element in elements), "missingCategory": sum("category" not in element for element in elements)}))
    print(json.dumps({"pagesPresent": sorted({element.get("page") for element in elements}), "duplicateIds": len({element.get("id") for element in elements}) != len(elements), "emptyHtml": sum(1 for element in elements if not element.get("content", {}).get("html", "").strip()), "categories": sorted({element.get("category") for element in elements})}))
    try:
        def trace(frame, event, arg):
            if frame.f_code.co_name == "parse_document_response" and event == "exception":
                print(json.dumps({"parserExceptionLine": frame.f_lineno, "exceptionType": type(arg[1]).__name__, "exception": str(arg[1]), "category": frame.f_locals.get("element", {}).get("category")}))
            return trace
        sys.settrace(trace)
        parsed = parse_document_response(body, "probe", body["usage"]["pages"])
        sys.settrace(None)
        print(json.dumps({"parsedPages": len(parsed.pages), "parsedCharacters": len(parsed.text)}))
    except Exception as error:
        sys.settrace(None)
        print(json.dumps({"parserError": type(error).__name__, "parserMessage": str(error)}))
    document_parse_module.assert_no_secrets = lambda value: None
    try:
        parsed = parse_document_response(body, "probe", body["usage"]["pages"])
        print(json.dumps({"parsedWithoutSecretGuard": True, "parsedPages": len(parsed.pages)}))
    except Exception as error:
        print(json.dumps({"parserWithoutSecretGuardError": type(error).__name__, "parserWithoutSecretGuardMessage": str(error)}))
    plain_errors = {}
    def safe_text(element):
        try:
            return _StructuredHtml().text(element["content"]["html"])
        except Exception:
            try:
                return _PlainHtml().text(element["content"]["html"])
            except Exception as error:
                key = f"{element.get('category')}:{type(error).__name__}:{str(error)}"
                plain_errors[key] = plain_errors.get(key, 0) + 1
                return ""
    page_texts = {page: "\n".join(safe_text(element) for element in elements if element["page"] == page) for page in sorted({element["page"] for element in elements})}
    print(json.dumps({"pagesWithText": sum(bool(text) for text in page_texts.values()), "totalCharacters": sum(len(text) for text in page_texts.values()), "emptyPages": [page for page, text in page_texts.items() if not text]}))
    if plain_errors:
        print(json.dumps({"plainErrors": plain_errors}))
    try:
        assert_no_secrets("\n\f\n".join(page_texts.values()))
    except Exception as error:
        print(json.dumps({"aggregateSecretGuard": str(error)}))
    for page, text in page_texts.items():
        try:
            assert_no_secrets(text)
        except Exception as error:
            print(json.dumps({"secretGuardPage": page, "error": str(error), "characters": len(text)}))


if __name__ == "__main__":
    asyncio.run(main())
