# Document Extraction Evaluation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compare multiple PDF extraction and AI analysis pipelines on 5–10 real planning documents and select the best quality/cost trade-off using reproducible measurements.

**Architecture:** Keep the production analysis flow unchanged while adding an offline evaluation runner. Each document is processed by named pipeline variants, all outputs are normalized to the existing analysis-result shape, and a gold JSON file is used to calculate extraction, omission, hallucination, quote-location, latency, and page-cost metrics.

**Tech Stack:** Python, pytest, existing AI-service extraction/provider modules, JSONL/JSON evaluation fixtures.

**Spec:** This plan implements the agreed evaluation goal: compare `pypdf`, `Document Parse`, OCR/fallback, and hybrid strategies on identical documents.

## Global Constraints

- Do not store API keys or raw PDF contents in fixtures, logs, or reports.
- Do not change production result semantics while building the evaluator.
- Every evaluated document must have a human-reviewed gold file before scores are trusted.
- Quote-location failure must be reported separately from value extraction failure.
- Cost must be calculated from recorded provider usage/configuration, never guessed in code.

## Review Focus

- Scanned pages with no text layer: must not be counted as ordinary pypdf success.
- Mixed PDFs: must report per-document and per-page behavior, not only an average.
- Tables: must preserve row/column meaning when comparing quotes.
- Missing fields: must distinguish correct absence from omission.
- AI paraphrased quotes: must be counted as quote-location failures, not silently accepted.

### Task 1: Define evaluation fixtures and gold data

**Files:**
- Create: `services/ai-service/evaluation/README.md`
- Create: `services/ai-service/evaluation/documents/.gitkeep`
- Create: `services/ai-service/evaluation/gold/schema.json`
- Create: `services/ai-service/evaluation/gold/example.json`

- [ ] Select 5–10 documents covering text-only, table-heavy, scanned, mixed, and multi-column PDFs.
- [ ] Record only document IDs, page counts, and anonymized copies or approved local paths.
- [ ] Create one human-reviewed gold JSON per document for the ten required fields and exact quote/page when present.
- [ ] Document whether each field is present, explicitly absent, or not stated.

### Task 2: Add pipeline adapters

**Files:**
- Create: `services/ai-service/evaluation/pipelines.py`
- Modify: `services/ai-service/app/analysis/extraction.py`
- Modify: `services/ai-service/app/analysis/providers/document_parse.py`
- Test: `services/ai-service/tests/test_evaluation_pipelines.py`

**Interfaces:**
- `run_pipeline(document_path: str, pipeline_name: str) -> PipelineRun`
- `PipelineRun` contains `document_id`, `pipeline`, `page_count`, `analysis_output`, `provider_usage`, `latency_ms`, and structured errors.

- [ ] Add variants: `pypdf`, `document-parse`, `parse-ocr`, and `hybrid-fallback`.
- [ ] Ensure each variant exposes the exact text used by both AI analysis and quote grounding.
- [ ] Add tests proving a scanned PDF is classified as a text-layer failure rather than a successful empty extraction.

### Task 3: Implement metric calculation

**Files:**
- Create: `services/ai-service/evaluation/metrics.py`
- Test: `services/ai-service/tests/test_evaluation_metrics.py`

**Interfaces:**
- `score_result(gold: GoldDocument, result: PipelineRun) -> MetricResult`
- `aggregate_metrics(results: list[MetricResult]) -> AggregateReport`

- [ ] Calculate required-field accuracy, omission rate, hallucination rate, quote-location success rate, latency, and page cost.
- [ ] Score explicit absence separately from missing extraction.
- [ ] Count a value match with a missing quote location as a quote failure, not a full success.
- [ ] Add unit tests for true positive, false negative, false positive, and paraphrased quote cases.

### Task 4: Build reproducible evaluation runner and report

**Files:**
- Create: `services/ai-service/evaluation/run_evaluation.py`
- Create: `services/ai-service/evaluation/report.py`
- Modify: `services/ai-service/README.md`

- [ ] Run every selected pipeline against the same fixture set.
- [ ] Write machine-readable results to `evaluation/results/<run-id>.json` without raw document text or secrets.
- [ ] Produce a Markdown summary with per-document and aggregate metrics.
- [ ] Include provider call counts, page counts, latency, and configured unit prices in the report.

### Task 5: Decide production strategy

**Files:**
- Create: `Docs/evaluation/document-extraction-decision.md`

- [ ] Compare quality, quote success, latency, and cost by document type.
- [ ] Choose a default pipeline and explicit fallback rules.
- [ ] Record known limitations and the minimum score threshold for production use.
- [ ] Only after review, update the production service routing and add regression fixtures for every discovered failure.

## Verification

- Run: `services/ai-service/.venv/Scripts/python.exe -m pytest services/ai-service/tests/test_evaluation_metrics.py services/ai-service/tests/test_evaluation_pipelines.py -q`
- Run: `services/ai-service/.venv/Scripts/python.exe -m compileall -q services/ai-service/app services/ai-service/evaluation`
- Verify the report contains no API keys and no raw PDF text.
