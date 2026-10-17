from __future__ import annotations

from typing import Any


def rank_candidates(needs: list[dict[str, Any]], candidates: list[dict[str, Any]]) -> dict[str, Any]:
    if not isinstance(needs, list) or not isinstance(candidates, list) or len(needs) > 6 or len(candidates) > 15:
        raise ValueError("INVALID_SKILL_REQUEST")
    if any(not isinstance(candidate, dict) or len(str(candidate.get("content", ""))) > 6000 for candidate in candidates):
        raise ValueError("INVALID_SKILL_REQUEST")
    matches = []
    for candidate in candidates:
        content = f"{candidate.get('name', '')} {candidate.get('content', '')}".casefold()
        relevant = [need for need in needs if any(token and token.casefold() in content for token in str(need.get("query", "")).split())]
        if not relevant:
            continue
        score = min(100, round(50 + 50 * len(relevant) / max(1, len(needs))))
        matches.append({
            "candidateId": candidate.get("id"),
            "needIds": [need.get("id") for need in relevant],
            "score": score,
            "reason": "입력된 need와 후보 내용의 키워드가 일치합니다.",
        })
    return {"matches": matches}
