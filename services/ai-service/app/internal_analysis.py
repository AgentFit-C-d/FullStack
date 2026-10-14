from fastapi import Request

from app.internal_auth import AuthenticatedRequest, InternalAuthError, ReplayGuard, verify_request
from app.settings import HmacKeyRing


async def authenticate_analysis_request(request: Request, key_ring: HmacKeyRing,
                                        replay_guard: ReplayGuard) -> AuthenticatedRequest:
    body = await request.body()
    return verify_request(request.headers, body, method=request.method, path=request.url.path,
                          key_ring=key_ring, replay_guard=replay_guard)
