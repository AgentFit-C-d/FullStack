from __future__ import annotations

import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from threading import Lock
from typing import Mapping

from app.settings import HmacKeyRing


class InternalAuthError(ValueError):
    pass


@dataclass(frozen=True)
class AuthenticatedRequest:
    request_id: str
    generation: int
    contract_version: str
    body: dict


def canonical_string(method: str, path: str, timestamp: str, request_id: str,
                     generation: int, contract_version: str, body: bytes) -> str:
    body_hash = hashlib.sha256(body).hexdigest()
    return "\n".join([
        method.upper(), path, timestamp, request_id, str(generation), contract_version, body_hash,
    ])


class ReplayGuard:
    def __init__(self) -> None:
        self._seen: dict[tuple[str, str], float] = {}
        self._lock = Lock()

    def reserve(self, request_id: str, body: bytes, now: float) -> bool:
        fingerprint = (request_id, hashlib.sha256(body).hexdigest())
        with self._lock:
            self._seen = {key: expiry for key, expiry in self._seen.items() if expiry > now}
            if fingerprint in self._seen:
                return False
            self._seen[fingerprint] = now + 600
            return True


def verify_request(headers: Mapping[str, str], body: bytes, *, method: str, path: str,
                   key_ring: HmacKeyRing, replay_guard: ReplayGuard, now: float | None = None) -> AuthenticatedRequest:
    now = time.time() if now is None else now
    key_id = headers.get("X-AgentFit-Key-Id")
    timestamp = headers.get("X-AgentFit-Timestamp")
    provided_signature = headers.get("X-AgentFit-Signature")
    if not key_id or not timestamp or not provided_signature or not timestamp.isdigit():
        raise InternalAuthError("internal authentication failed")
    timestamp_value = int(timestamp)
    if abs(now - timestamp_value) > 300:
        raise InternalAuthError("internal authentication failed")
    secret = key_ring.secret_for(key_id, datetime.fromtimestamp(now, tz=timezone.utc))
    if secret is None:
        raise InternalAuthError("internal authentication failed")
    try:
        payload = json.loads(body)
        request_id = payload["requestId"]
        generation = payload["generation"]
        contract_version = payload["contractVersion"]
        if not isinstance(request_id, str) or not request_id or not isinstance(generation, int) or generation < 1:
            raise ValueError
        if not isinstance(contract_version, str) or not contract_version:
            raise ValueError
    except (ValueError, KeyError, TypeError, json.JSONDecodeError) as exc:
        raise InternalAuthError("internal authentication failed") from exc
    expected = base64.urlsafe_b64encode(
        hmac.new(secret.encode(), canonical_string(method, path, timestamp, request_id,
                                                    generation, contract_version, body).encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    if not hmac.compare_digest(expected, provided_signature):
        raise InternalAuthError("internal authentication failed")
    if not replay_guard.reserve(request_id, body, now):
        raise InternalAuthError("internal authentication failed")
    return AuthenticatedRequest(request_id, generation, contract_version, payload)
