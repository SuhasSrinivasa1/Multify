"""Opt-in Groww SDK research feed. NEVER influences Android Fixed decisions or orders.

Groww documents a Python GrowwFeed.subscribe_ltp callback and up to 1000
instruments per subscription. The raw Android WebSocket protocol is not
documented; this adapter intentionally does not guess one.
"""
from __future__ import annotations
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Iterable
import json
import time

MAX_SUBSCRIPTION = 1000
MAX_AGE_MS = 10_000
MAX_FUTURE_SKEW_MS = 2_000

@dataclass(frozen=True)
class FeedPoint:
    symbol: str
    exchange_token: str
    ltp: float
    exchange_timestamp_ms: int
    received_at_ms: int
    source: str = "GROWW_SDK_RESEARCH_FEED"

    @property
    def source_age_ms(self) -> int:
        return self.received_at_ms - self.exchange_timestamp_ms

def select_subscription(instruments: Iterable[dict], limit: int = MAX_SUBSCRIPTION):
    """Observe up to one documented subscription's capacity, no hidden over-subscription."""
    if limit < 1 or limit > MAX_SUBSCRIPTION:
        raise ValueError("subscription cap must be between 1 and 1000")
    selected, seen = [], set()
    for row in instruments:
        exchange, segment, token = (
            str(row.get("exchange", "")).upper(),
            str(row.get("segment", "")).upper(),
            str(row.get("exchange_token", "")),
        )
        if exchange != "NSE" or segment != "CASH" or not token or token in seen:
            continue
        seen.add(token)
        selected.append({"exchange": exchange, "segment": segment, "exchange_token": token})
        if len(selected) >= limit:
            break
    return selected

def parse_ltp_frame(raw: dict, token_to_symbol: dict[str, str],
                    received_at_ms: int) -> list[FeedPoint]:
    """Only source-timestamped NSE/CASH prices within the research freshness window."""
    try:
        cells = raw.get("ltp", {}).get("NSE", {}).get("CASH", {})
    except (AttributeError, TypeError):
        return []
    if not isinstance(cells, dict):
        return []
    points = []
    for token, v in cells.items():
        symbol = token_to_symbol.get(str(token))
        if not symbol or not isinstance(v, dict):
            continue
        try:
            price = float(v["ltp"])
            source_at_ms = int(float(v["tsInMillis"]))
        except (ValueError, TypeError, KeyError, OverflowError):
            continue
        from math import isfinite
        age = received_at_ms - source_at_ms
        if not isfinite(price) or price <= 0 or age < -MAX_FUTURE_SKEW_MS or age > MAX_AGE_MS:
            continue
        points.append(FeedPoint(symbol, str(token), price, source_at_ms, received_at_ms))
    return points

def run_opt_in_probe(access_token: str, instruments: list[dict],
                     token_to_symbol: dict[str, str], sink):
    """Blocking RESEARCH-ONLY SDK listener; caller must provide explicit credentials.

    No network is initiated until invoked explicitly. Results are never passed
    to the Android trading app or used to place orders.
    """
    try:
        from growwapi import GrowwFeed, GrowwAPI
    except ImportError as exc:
        raise RuntimeError("Optional growwapi package required for research feed") from exc
    chosen = select_subscription(instruments)
    if not chosen:
        raise ValueError("No valid NSE CASH exchange tokens")
    feed = GrowwFeed(GrowwAPI(access_token))
    def on_data_received(_meta):
        received = int(time.time() * 1000)
        for point in parse_ltp_frame(feed.get_ltp(), token_to_symbol, received):
            sink(json.dumps({
                "source": point.source, "symbol": point.symbol, "ltp": point.ltp,
                "source_at_ms": point.exchange_timestamp_ms,
                "received_at_ms": point.received_at_ms,
                "source_age_ms": point.source_age_ms,
                "research_only": True, "trade_eligible": False
            }, separators=(",", ":")))
    feed.subscribe_ltp(chosen, on_data_received=on_data_received)
    feed.consume()
