from backend.app.fixed_peak_report import analyze_fixed_peak_entries

def test_first_primary_price_is_not_confused_with_notification_time():
    official = [{
        "recommendationDate": "2026-11-03", "symbol": "WEALTH",
        "generatedAtMs": 1793700000000, "entryPrice": 870.0,
        "targetPct": 1.1161043, "status": "ACTIVE",
        "maxFavourablePct": 0.58, "maxAdversePct": -2.3,
    }]
    early = [{
        "date": "2026-11-03", "symbol": "WEALTH",
        "firstPrimaryScanAtMs": 1793699700000,
        "firstPrimaryPrice": 845.0,
        "observedSpreadBps": 233.0,
        "dayTradedShares": 1479, "warnings": ["WIDE_SPREAD_SHADOW"]
    }]
    rows = analyze_fixed_peak_entries(official, early)
    assert len(rows) == 1
    x = rows[0]
    assert round(x["price_advance_before_official_entry_pct"], 2) == 2.96
    assert x["confirmation_wait_lower_bound_ms"] == 300000
    assert x["spread_exceeds_40bps_shadow"] is True
    assert x["official_scan_started_at_ms_NOT_posted_at_ms"] == official[0]["generatedAtMs"]
    assert x["not_execution_or_causal_evidence"] is True

def test_missing_early_watch_is_unknown_not_zero_spread_or_runup():
    records = analyze_fixed_peak_entries([{
        "recommendationDate": "2026-11-03", "symbol": "PFIZER",
        "entryPrice": 3754.6, "status": "TARGET_HIT"
    }], [])
    assert records[0]["target_hit_observed"] is True
    assert records[0]["price_advance_before_official_entry_pct"] is None
    assert records[0]["spread_observed_bps"] is None
    assert records[0]["spread_unknown_or_crossed"] is True
