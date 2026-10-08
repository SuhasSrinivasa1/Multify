from __future__ import annotations

import hashlib
import hmac
import sqlite3
import time
from fastapi import Header, HTTPException, Request

from .config import get_settings
from .storage import db_connection


def _body_sha256(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def canonical_message(method: str, path: str, timestamp: str, nonce: str, body: bytes) -> bytes:
    return f"{method.upper()}\n{path}\n{timestamp}\n{nonce}\n{_body_sha256(body)}".encode()


def verify_signature(secret: str, message: bytes, supplied: str) -> bool:
    expected = hmac.new(secret.encode(), message, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, supplied.lower())


async def require_phone_auth(
    request: Request,
    x_client_id: str = Header(default="", alias="X-Client-Id"),
    x_timestamp: str = Header(default="", alias="X-Timestamp"),
    x_nonce: str = Header(default="", alias="X-Nonce"),
    x_signature: str = Header(default="", alias="X-Signature"),
) -> None:
    settings = get_settings()
    if x_client_id != settings.phone_client_id:
        raise HTTPException(status_code=401, detail="unknown client")
    try:
        ts = int(x_timestamp)
    except ValueError as exc:
        raise HTTPException(status_code=401, detail="bad timestamp") from exc
    now = int(time.time())
    if abs(now - ts) > settings.max_clock_skew_seconds:
        raise HTTPException(status_code=401, detail="stale request")
    if len(x_nonce) < 12 or len(x_nonce) > 128:
        raise HTTPException(status_code=401, detail="bad nonce")
    body = await request.body()
    message = canonical_message(request.method, request.url.path, x_timestamp, x_nonce, body)
    if not verify_signature(settings.phone_hmac_secret, message, x_signature):
        raise HTTPException(status_code=401, detail="bad signature")

    cutoff = now - settings.nonce_ttl_seconds
    with db_connection() as db:
        db.execute("DELETE FROM used_nonces WHERE seen_at < ?", (cutoff,))
        try:
            db.execute("INSERT INTO used_nonces(nonce, seen_at) VALUES(?, ?)", (x_nonce, now))
            db.commit()
        except sqlite3.IntegrityError as exc:
            raise HTTPException(status_code=409, detail="replayed request") from exc
