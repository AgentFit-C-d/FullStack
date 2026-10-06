from app.main import app
from fastapi.testclient import TestClient


def test_health_returns_ai_service_status():
    client = TestClient(app)

    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"service": "ai-service", "status": "ok"}
