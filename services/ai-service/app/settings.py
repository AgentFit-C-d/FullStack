from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import datetime, timezone


class SettingsError(ValueError):
    pass


@dataclass(frozen=True)
class HmacKeyRing:
    current_key_id: str
    current_secret: str
    previous_key_id: str | None = None
    previous_secret: str | None = None
    previous_valid_until: datetime | None = None

    @classmethod
    def from_env(cls, environ: dict[str, str] | None = None) -> "HmacKeyRing":
        env = os.environ if environ is None else environ
        current_id = env.get("AGENTFIT_HMAC_CURRENT_KEY_ID", "").strip()
        current_secret = env.get("AGENTFIT_HMAC_CURRENT_SECRET", "").strip()
        if not current_id or not current_secret:
            raise SettingsError("current HMAC key id and secret are required together")

        previous_id = env.get("AGENTFIT_HMAC_PREVIOUS_KEY_ID", "").strip() or None
        previous_secret = env.get("AGENTFIT_HMAC_PREVIOUS_SECRET", "").strip() or None
        previous_until_raw = env.get("AGENTFIT_HMAC_PREVIOUS_VALID_UNTIL", "").strip() or None
        if any(value is not None for value in (previous_id, previous_secret, previous_until_raw)):
            if not all(value is not None for value in (previous_id, previous_secret, previous_until_raw)):
                raise SettingsError("previous HMAC key id, secret, and validUntil are required together")
            try:
                previous_until = datetime.fromisoformat(previous_until_raw.replace("Z", "+00:00"))
            except ValueError as exc:
                raise SettingsError("previous HMAC validUntil must be ISO-8601") from exc
            if previous_until <= datetime.now(timezone.utc):
                raise SettingsError("previous HMAC key is expired")
        else:
            previous_until = None
        return cls(current_id, current_secret, previous_id, previous_secret, previous_until)

    def secret_for(self, key_id: str, now: datetime) -> str | None:
        if key_id == self.current_key_id:
            return self.current_secret
        if key_id == self.previous_key_id and self.previous_valid_until and now < self.previous_valid_until:
            return self.previous_secret
        return None
