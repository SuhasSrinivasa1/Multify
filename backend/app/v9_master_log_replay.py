"""v9 master: reproducible, zero-network audit of native Multify export ZIPs.

Does NOT claim to reconstruct exact intrabar prices, executable returns or
historical v8.3.16/.19 results when their diagnostic ZIPs are unavailable.
Usage: python backend/app/v9_master_log_replay.py /path/to/*logs*.zip
"""
from __future__ import annotations
import argparse
import json
import re
import statistics
import zipfile
from collections import defaultdict
from pathlib import Path

VERSION = re.compile(r"logs-v(8[.]3[.][0-9]+)-")
STAMP = re.compile(r"([0-9]{8}-[0-9]{6})[.]zip$")

def _json(z: zipfile.ZipFile, name: str, default):
    try:
        return json.loads(z.read(name).decode("utf-8"))
    except KeyError:
        return default

def _jsonl(z: zipfile.ZipFile, name: str):
    try:
        return [json.loads(line) for line in z.read(name).decode("utf-8").splitlines()
                if line.strip()]
    except KeyError:
        return []

def version_of(path):
    m = VERSION.search(Path(path).name)
    return "v" + m.group(1) if m else None

def summarize_exports(paths):
    groups = defaultdict(list)
    for raw in paths:
        path = Path(raw)
        version = version_of(path)
        if version is not None:
            groups[version].append(path)
    rows = []
    for version, group in sorted(groups.items()):
        group = sorted(group, key=lambda p: (STAMP.search(p.name).group(1)
                                             if STAMP.search(p.name) else "", p.name))
        latest = group[-1]
        with zipfile.ZipFile(latest) as z:
            recommendations = _json(z, "fixed_recommendations.json", [])
            ids = {str(r.get("id")) for r in recommendations}
            outcome = _json(z, "fixed_sampled_maturity_and_repeat_research.json", {})
            maturity = outcome.get("sampled_target_hit_maturity", {})
            h15 = next((x for x in maturity.get("horizons", [])
                        if x.get("horizon_minutes") == 15), None)
            audit = _jsonl(z, "system_audit.jsonl")
            profiles = [a for a in audit if a.get("event") == "FIXED_SCAN_PROFILE"
                        and a.get("category") == "RUNTIME"]
            durations = [p.get("details", {}).get("elapsed_ms")
                         for p in profiles]
            durations = [float(x) for x in durations
                         if isinstance(x, (int, float)) and x >= 0]
            rates = [a for a in audit if a.get("event") == "HTTP_429"
                     and a.get("category") == "API_RATE_LIMIT"]
            paired = _json(z, "fixed_5m_shadow_pairs.json", [])
            # The preexisting GrowwHttpTraceLedger already tracks exact local
            # ALPHA/BETA endpoint 1s/60s dispatch counts for every HTTP call.
            traces = [x.get("details", {}) for x in audit
                      if x.get("category") == "API_HTTP_TRACE"
                      and x.get("event") == "REQUEST_FINISHED"]
            at429 = [d for d in traces if d.get("http_status") == 429]
            by_ep = defaultdict(lambda: {"success": 0, "rate_limit": 0,
                                           "highest_successful_60s": 0,
                                           "limit_at_429_60s": []})
            for d in traces:
                endpoint = d.get("endpoint")
                account = d.get("account")
                if not endpoint or not account:
                    continue
                group = by_ep[account + " " + endpoint]
                n = d.get("endpoint_60s_at_start")
                if d.get("http_status") in (200, 201):
                    group["success"] += 1
                    if isinstance(n, (int, float)):
                        group["highest_successful_60s"] = max(
                            group["highest_successful_60s"], n)
                elif d.get("http_status") == 429:
                    group["rate_limit"] += 1
                    group["limit_at_429_60s"].append(n)
            quota_evidence = {k: v for k, v in sorted(by_ep.items())
                              if v["rate_limit"] > 0}
            incident_details = [{
                "account": d.get("account"),
                "endpoint": d.get("endpoint"),
                "endpoint_1s_at_start": d.get("endpoint_1s_at_start"),
                "endpoint_60s_at_start": d.get("endpoint_60s_at_start"),
                "category_60s_at_start": d.get("category_60s_at_start"),
                "account_60s_at_start": d.get("account_60s_at_start")
            } for d in at429]
            source_version = _json(z, "export_manifest.json", {}).get("version")
        cumulative = True
        for older in group[:-1]:
            with zipfile.ZipFile(older) as z:
                older_ids = {str(x.get("id"))
                             for x in _json(z, "fixed_recommendations.json", [])}
            if not older_ids.issubset(ids):
                cumulative = False
        mature = h15.get("matured_events") if h15 else None
        hit = h15.get("all", {}).get("full_target_hits") if h15 else None
        rows.append({
            "version": version, "installed_version": source_version,
            "latest_cumulative_export": latest.name, "files_seen": len(group),
            "older_recommendation_ids_all_present": cumulative,
            "recommendations": len(ids),
            "mature_15m": mature, "target_hits_15m": hit,
            "sampled_target_hit_rate_15m": (
                round(100 * hit / mature, 2) if mature and hit is not None else None),
            "scan_profiles": len(durations),
            "scan_median_sec": (round(statistics.median(durations) / 1000, 2)
                                if durations else None),
            "http_429_events": len(rates),
            "http_429_trace_incidents": incident_details,
            "endpoint_429_vs_successful_dispatch": quota_evidence,
            "does_not_establish_broker_hidden_quota": True,
            "actual_5m_shadow_pairs": len(paired),
            "performance_is_simulated": False,
            "sampled_LTP_not_realized_PnL": True,
            "same_trading_day_confounded_by_time_of_day": True
        })
    return {
        "schema": "multify_v9_master_audit_v1",
        "compared_versions": rows,
        "missing_original_version_logs": [
            label for label in ("v8.3.16", "v8.3.19")
            if not any(x["version"] == label for x in rows)],
        "best_strategy_selected": None,
        "broker_429_quota_inference": "Observed endpoint counts vary widely at rejection; successful requests may exceed rejected counts. Do not invent a fixed broker quota.",
        "reason": "No same-day matched full-candle replay or original v16/v19 exports",
        "source_constraints": [
            "Latest cumulative ZIP per installed version only; do not double-count exports",
            "Maturity from app-exported sampled-full-market-LTP cohorts",
            "Historical 15m candle data not retained in complete form for all candidates",
            "Active/unobserved recommendations are not recorded as failures",
            "Target touches are not broker fills or realized profits",
            "A version's time-of-day and market state differ between deployments",
            "5m challenger requires actual timestamped 5m data, not extrapolated quotes"]
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("zips", nargs="+")
    args = parser.parse_args()
    print(json.dumps(summarize_exports(args.zips), indent=2, ensure_ascii=False))

if __name__ == "__main__":
    main()
