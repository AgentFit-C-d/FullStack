from app.health import health_payload


def test_health_payload_contains_service_status():
    assert health_payload() == {"service": "ai-service", "status": "ok"}
