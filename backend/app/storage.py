from __future__ import annotations

import json
import sqlite3
import time
from contextlib import contextmanager
from typing import Iterator

from .config import get_settings


SCHEMA = """
PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS used_nonces (
    nonce TEXT PRIMARY KEY,
    seen_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS idempotency (
    idem_key TEXT PRIMARY KEY,
    request_hash TEXT NOT NULL,
    response_json TEXT,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS audit_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    category TEXT NOT NULL,
    event TEXT NOT NULL,
    data_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS risk_state (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
"""


@contextmanager
def db_connection() -> Iterator[sqlite3.Connection]:
    settings = get_settings()
    db = sqlite3.connect(settings.db_path, timeout=10, check_same_thread=False)
    db.row_factory = sqlite3.Row
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    with db_connection() as db:
        db.executescript(SCHEMA)
        db.commit()


def audit(category: str, event: str, data: dict) -> None:
    with db_connection() as db:
        db.execute(
            "INSERT INTO audit_events(ts, category, event, data_json) VALUES(?,?,?,?)",
            (int(time.time()), category, event, json.dumps(data, sort_keys=True, separators=(",", ":"))),
        )
        db.commit()


def read_risk_value(key: str, default: str = "") -> str:
    with db_connection() as db:
        row = db.execute("SELECT value FROM risk_state WHERE key=?", (key,)).fetchone()
        return row["value"] if row else default


def write_risk_value(key: str, value: str) -> None:
    now = int(time.time())
    with db_connection() as db:
        db.execute(
            "INSERT INTO risk_state(key,value,updated_at) VALUES(?,?,?) "
            "ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at",
            (key, value, now),
        )
        db.commit()
