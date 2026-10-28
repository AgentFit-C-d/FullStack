from pathlib import Path

from evaluation.live_analysis import build_record, load_dotenv_value


FIXTURES = Path(__file__).parents[1] / "evaluation" / "test-fixtures"


def test_load_dotenv_value_reads_the_requested_key_without_loading_other_values():
    env_file = FIXTURES / "live-analysis.env"

    assert load_dotenv_value(env_file, "UPSTAGE_API_KEY") == "expected-key"


def test_load_dotenv_value_returns_none_when_key_is_missing():
    env_file = FIXTURES / "live-analysis.env"

    assert load_dotenv_value(env_file, "NOT_CONFIGURED") is None


def test_build_record_preserves_unknown_cost_instead_of_guessing_zero():
    record = build_record("doc-1", "document-parse", 3, {"modelMetadata": {"latencyMs": 120}}, None)

    assert record["totalCost"] is None
