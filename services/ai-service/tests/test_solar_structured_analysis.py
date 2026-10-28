import json

from app.ai_v2.analysis import AnalysisCore, SolarProvider


def test_solar_requests_json_mode_and_grounds_scalar_and_array_values(monkeypatch):
    captured = {}

    class Response:
        status_code = 200

        def json(self):
            return {"choices": [{"message": {"content": json.dumps({"items": [
                {"field": "project_name", "value": "AgentFit",
                 "quote": "project_name: AgentFit", "context": "explicit"},
                {"field": "features", "value": ["코드 탐색", "테스트 실행"],
                 "quote": "features: 코드 탐색, 테스트 실행", "context": "explicit"},
            ]})}}]}

    def fake_post(url, **kwargs):
        captured.update(kwargs["json"])
        return Response()

    monkeypatch.setattr("httpx.post", fake_post)
    result = AnalysisCore(SolarProvider("test-key")).analyze(
        "doc-1", "text",
        "project_name: AgentFit\nfeatures: 코드 탐색, 테스트 실행".encode(),
        "2999-01-01T00:00:00Z",
    )

    assert result["profile"]["project_name"] == "AgentFit"
    assert result["profile"]["features"] == ["코드 탐색", "테스트 실행"]
    assert captured["response_format"] == {"type": "json_object"}
