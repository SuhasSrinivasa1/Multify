"""Read-only Fixed peak-entry comparison from a user-exported diagnostics ZIP.

A single day is descriptive, not a causal test. No broker/API calls and no
simulation of unobserved intrabar execution or invented exchange quote age.
"""
from __future__ import annotations
import json
import zipfile
from datetime import datetime
from zoneinfo import ZoneInfo

INDIA = ZoneInfo("Asia/Kolkata")

def _time_bucket(timestamp_ms: int | None) -> str:
    if not timestamp_ms or timestamp_ms <= 0:
        return "UNKNOWN"
    local = datetime.fromtimestamp(timestamp_ms / 1000, tz=INDIA)
    minutes = local.hour * 60 + local.minute
    if minutes >= 930:
        return "AFTER_CASH_CLOSE"
    if minutes >= 900:
        return "LAST_30_MIN"
    if minutes >= 870:
        return "LAST_60_MIN"
    return "EARLIER"

def analyze_fixed_peak_entries(recommendations: list[dict],
                               early_watch: list[dict]) -> list[dict]:
    by_key = {(str(x.get("date", "")), str(x.get("symbol", "")).upper()): x
              for x in early_watch}
    results = []
    for rec in recommendations:
        day = str(rec.get("recommendationDate", ""))
        symbol = str(rec.get("symbol", "")).upper()
        if not day or not symbol:
            continue
        early = by_key.get((day, symbol), {})
        started = rec.get("generatedAtMs") or None # SCAN START, not notification time!
        first = early.get("firstPrimaryScanAtMs")
        first_price = early.get("firstPrimaryPrice")
        entry = rec.get("entryPrice")
        try:
            rise = (float(entry) / float(first_price) - 1) * 100 if (
                first_price is not None and float(first_price) > 0 and entry is not None) else None
        except (TypeError, ValueError, ZeroDivisionError):
            rise = None
        try:
            spread = float(early["observedSpreadBps"]) if early.get("observedSpreadBps") is not None else None
        except (ValueError, TypeError):
            spread = None
        results.append({
            "date": day,
            "symbol": symbol,
            "target_hit_observed": str(rec.get("status", "")).upper() == "TARGET_HIT",
            "target_pct": rec.get("targetPct"),
            "max_favourable_pct_observed": rec.get("maxFavourablePct"),
            "max_adverse_pct_observed": rec.get("maxAdversePct"),
            "official_scan_started_at_ms_NOT_posted_at_ms": started,
            "time_bucket_based_on_scan_start_ONLY": _time_bucket(started),
            "first_primary_scan_started_at_ms": first,
            "first_primary_reference_price": first_price,
            "published_reference_entry_price": entry,
            "price_advance_before_official_entry_pct": rise,
            "confirmation_wait_lower_bound_ms": started - first if (
                isinstance(started, int) and isinstance(first, int) and started >= first) else None,
            "spread_observed_bps": spread,
            "spread_exceeds_40bps_shadow": spread is not None and spread > 40,
            "spread_unknown_or_crossed": spread is None,
            "day_traded_shares_at_last_primary": early.get("dayTradedShares"),
            "quality_shadow_warnings": early.get("warnings", []),
            "not_execution_or_causal_evidence": True
        })
    return sorted(results, key=lambda x:(x["date"],x["symbol"]))

def analyze_export(path: str) -> list[dict]:
    with zipfile.ZipFile(path) as z:
        def load(name, fallback):
            try:
                return json.loads(z.read(name))
            except KeyError:
                return fallback
        return analyze_fixed_peak_entries(load("fixed_recommendations.json", []),
                                           load("fixed_early_watch.json", []))

if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Fixed source-backed peak-entry diagnostics")
    parser.add_argument("logs_zip", help="Multify diagnostic ZIP")
    args = parser.parse_args()
    print(json.dumps(analyze_export(args.logs_zip), indent=2, sort_keys=True))
