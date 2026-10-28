import json
from pathlib import Path

from app.evaluation.metrics import aggregate_metrics, score_result


def evaluate_directory(gold_dir: Path, results_dir: Path) -> dict:
    by_pipeline: dict[str, list] = {}
    for result_path in sorted(results_dir.glob("*.json")):
        result = json.loads(result_path.read_text(encoding="utf-8"))
        gold_path = gold_dir / f"{result['documentId']}.json"
        gold = json.loads(gold_path.read_text(encoding="utf-8"))
        metric = score_result(
            gold,
            result["analysis"],
            page_count=int(result["pageCount"]),
            latency_ms=int(result.get("latencyMs", 0)),
            total_cost=float(result.get("totalCost", 0)),
        )
        by_pipeline.setdefault(result["pipeline"], []).append(metric)
    reports = {}
    for pipeline, metrics in by_pipeline.items():
        aggregate = aggregate_metrics(metrics)
        reports[pipeline] = {
            "documents": aggregate.documents,
            "accuracy": aggregate.average_accuracy,
            "omissionRate": aggregate.average_omission_rate,
            "hallucinationRate": aggregate.average_hallucination_rate,
            "quoteSuccessRate": aggregate.average_quote_success_rate,
            "averageLatencyMs": aggregate.average_latency_ms,
            "totalCost": aggregate.total_cost,
        }
    return {"pipelines": reports}


def render_markdown(report: dict) -> str:
    lines = ["# Document extraction evaluation", "", "| Pipeline | Accuracy | Omission | Hallucination | Quote success | Avg latency (ms) | Total cost |", "|---|---:|---:|---:|---:|---:|---:|"]
    for pipeline, values in report["pipelines"].items():
        lines.append(f"| {pipeline} | {values['accuracy']:.1%} | {values['omissionRate']:.1%} | {values['hallucinationRate']:.1%} | {values['quoteSuccessRate']:.1%} | {values['averageLatencyMs']:.0f} | {values['totalCost']:.4f} |")
    return "\n".join(lines) + "\n"
