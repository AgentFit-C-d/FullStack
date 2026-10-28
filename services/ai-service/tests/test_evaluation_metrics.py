from app.evaluation.metrics import aggregate_metrics, score_result


def test_score_result_separates_accuracy_omission_hallucination_and_quote_failure():
    gold = {
        "fields": {
            "project_name": "AgentFit",
            "backend": ["Spring Boot"],
            "database": "PostgreSQL",
            "deployment": None,
        },
        "quotes": {
            "project_name": [{"page": 1, "quote": "프로젝트명은 AgentFit이다."}],
            "backend": [{"page": 2, "quote": "백엔드는 Spring Boot로 구현한다."}],
            "database": [{"page": 3, "quote": "PostgreSQL을 사용한다."}],
        },
    }
    result = {
        "profile": {"project_name": "AgentFit", "backend": ["Spring Boot"], "database": "MySQL"},
        "candidates": [
            {"field": "project_name", "proposedValue": "AgentFit", "status": "suggested", "evidence": [{"page": 1}]},
            {"field": "backend", "proposedValue": ["Spring Boot"], "status": "suggested", "evidence": []},
            {"field": "database", "proposedValue": "MySQL", "status": "suggested", "evidence": [{"page": 3}]},
            {"field": "ai", "proposedValue": ["Solar"], "status": "suggested", "evidence": [{"page": 4}]},
        ],
    }

    scored = score_result(gold, result, page_count=4, latency_ms=1200, total_cost=0.08)

    assert scored.required_total == 3
    assert scored.correct_fields == 2
    assert scored.omissions == 0
    assert scored.hallucinations == 2
    assert scored.quote_locations == 1
    assert scored.quote_expected == 3
    assert scored.accuracy == 2 / 3
    assert scored.omission_rate == 0
    assert scored.hallucination_rate == 2 / 4
    assert scored.quote_success_rate == 1 / 3
    assert scored.cost_per_page == 0.02


def test_aggregate_metrics_averages_rates_and_sums_counts():
    first = score_result({"fields": {"backend": "A"}, "quotes": {}}, {"profile": {"backend": "A"}, "candidates": []}, page_count=2, latency_ms=100, total_cost=0.02)
    second = score_result({"fields": {"backend": "A"}, "quotes": {}}, {"profile": {}, "candidates": []}, page_count=4, latency_ms=300, total_cost=0.04)

    report = aggregate_metrics([first, second])

    assert report.documents == 2
    assert report.correct_fields == 1
    assert report.omissions == 1
    assert report.average_accuracy == 0.5
    assert report.total_cost == 0.06
    assert report.average_latency_ms == 200
