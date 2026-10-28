import pytest

from app.analysis.config import read_config


def test_allows_the_explicit_compose_hostname_for_internal_http():
    config = read_config({
        "AI_SERVICE_TOKEN": "t" * 32,
        "API_INTERNAL_URL": "http://core:8080",
        "AI_INTERNAL_ALLOWED_HTTP_HOSTS": "core",
    })

    assert config.api_origin == "http://core:8080"


def test_rejects_an_unlisted_non_local_http_hostname():
    with pytest.raises(ValueError, match="INVALID_AI_CONFIG"):
        read_config({
            "AI_SERVICE_TOKEN": "t" * 32,
            "API_INTERNAL_URL": "http://core:8080",
        })
