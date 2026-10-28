from dataclasses import dataclass
import json
from typing import Any


@dataclass(frozen=True)
class MetricResult:
    required_total: int
    correct_fields: int
    omissions: int
    hallucinations: int
    quote_locations: int
    quote_expected: int
    page_count: int
    latency_ms: int
    total_cost: float

    @property
    def accuracy(self) -> float:
        return self.correct_fields / self.required_total if self.required_total else 1.0

    @property
    def omission_rate(self) -> float:
        return self.omissions / self.required_total if self.required_total else 0.0

    @property
    def hallucination_rate(self) -> float:
        extracted = self.correct_fields + self.omissions + self.hallucinations
        return self.hallucinations / extracted if extracted else 0.0

    @property
    def quote_success_rate(self) -> float:
        return self.quote_locations / self.quote_expected if self.quote_expected else 1.0

    @property
    def cost_per_page(self) -> float:
        return self.total_cost / self.page_count if self.page_count else 0.0


@dataclass(frozen=True)
class AggregateReport:
    documents: int
    correct_fields: int
    omissions: int
    hallucinations: int
    average_accuracy: float
    average_omission_rate: float
    average_hallucination_rate: float
    average_quote_success_rate: float
    average_latency_ms: float
    total_cost: float


def _canonical(value: Any) -> str:
    if isinstance(value, list):
        value = sorted(value, key=lambda item: str(item).casefold())
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _has_value(value: Any) -> bool:
    return value is not None and value != "" and value != []


def _field_evidence(result: dict[str, Any], field: str) -> list[dict[str, Any]]:
    return [
        evidence
        for candidate in result.get("candidates", [])
        if candidate.get("field") == field
        for evidence in candidate.get("evidence", [])
    ]


def score_result(
    gold: dict[str, Any],
    result: dict[str, Any],
    *,
    page_count: int,
    latency_ms: int,
    total_cost: float,
) -> MetricResult:
    gold_fields = {field: value for field, value in gold.get("fields", {}).items() if _has_value(value)}
    profile = result.get("profile", {})
    correct_fields = sum(
        1 for field, expected in gold_fields.items() if _has_value(profile.get(field)) and _canonical(profile[field]) == _canonical(expected)
    )
    omissions = sum(1 for field in gold_fields if not _has_value(profile.get(field)))
    predicted = {field: value for field, value in profile.items() if _has_value(value)}
    for candidate in result.get("candidates", []):
        field, value = candidate.get("field"), candidate.get("proposedValue")
        if field and _has_value(value) and field not in predicted:
            predicted[field] = value
    hallucinations = sum(
        1 for field, value in predicted.items()
        if field not in gold_fields or _canonical(value) != _canonical(gold_fields[field])
    )
    quote_expected = sum(1 for field in gold.get("quotes", {}) if field in gold_fields)
    quote_locations = sum(
        1
        for field, expected in gold_fields.items()
        if field in gold.get("quotes", {})
        and _has_value(profile.get(field))
        and _canonical(profile[field]) == _canonical(expected)
        and _field_evidence(result, field)
    )
    return MetricResult(required_total=len(gold_fields), correct_fields=correct_fields, omissions=omissions, hallucinations=hallucinations, quote_locations=quote_locations, quote_expected=quote_expected, page_count=page_count, latency_ms=latency_ms, total_cost=total_cost)


def aggregate_metrics(results: list[MetricResult]) -> AggregateReport:
    if not results:
        return AggregateReport(0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
    return AggregateReport(
        documents=len(results),
        correct_fields=sum(item.correct_fields for item in results),
        omissions=sum(item.omissions for item in results),
        hallucinations=sum(item.hallucinations for item in results),
        average_accuracy=sum(item.accuracy for item in results) / len(results),
        average_omission_rate=sum(item.omission_rate for item in results) / len(results),
        average_hallucination_rate=sum(item.hallucination_rate for item in results) / len(results),
        average_quote_success_rate=sum(item.quote_success_rate for item in results) / len(results),
        average_latency_ms=sum(item.latency_ms for item in results) / len(results),
        total_cost=sum(item.total_cost for item in results),
    )
