import pytest

from app.ai_v2.rank import rank_candidates


def test_rank_caps_candidate_content_and_count():
    with pytest.raises(ValueError, match="INVALID_SKILL_REQUEST"):
        rank_candidates(
            [{"id": "n1", "query": "test"}],
            [{"id": "c1", "name": "candidate", "content": "x" * 6001}],
        )
