import argparse
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from evaluation.report import evaluate_directory, render_markdown


def main() -> None:
    parser = argparse.ArgumentParser(description="Compare saved AgentFit extraction pipeline results.")
    parser.add_argument("--gold-dir", type=Path, required=True)
    parser.add_argument("--results-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    report = evaluate_directory(args.gold_dir, args.results_dir)
    args.output.with_suffix(".json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    args.output.with_suffix(".md").write_text(render_markdown(report), encoding="utf-8")


if __name__ == "__main__":
    main()
