# Multify Trader Pro v7.3.2 — Resilient Forecast Health

v7.3.2 contains all v7.3.0 resilience changes and the v7.3.1 learning-restore tool, plus visible discovery-health and target-calibration diagnostics.

## Discovery health is visible in the app

The Forecast screen now shows:

- discovery status: HEALTHY, WARMING, WAITING, DEGRADED or STALE
- current NSE market-wide coverage %
- whether the persisted broad-market baseline is ready
- baseline/health age
- failed LTP batch count
- whether production publication is being suppressed while broad discovery is unhealthy

Discovery health is persisted with the same trading date, so Android process recreation does not erase the operator-visible health state.

## Forecast selection and target calibration are measured separately

The learning dashboard now evaluates all completed recommendations for performance diagnostics while retaining positive-close-only rows for production forecast learning.

It reports:

- percentage that reached +0.55% MFE — the live trailing-arm threshold
- percentage that reached +1.00% MFE
- full forecast-target hit rate
- MFE P25 / median / P75 distribution
- total completed evaluation count

Negative-close examples still do not train production ranking/target learning, but they are included in these performance diagnostics so the displayed hit rates cannot be artificially inflated by the positive-close training filter.

## Resilience inherited from v7.3

- same-day market discovery state survives Android process restarts
- partial/degraded scans cannot overwrite a healthy baseline
- transient Groww/DNS/429 failures receive bounded retry/backoff
- three consecutive discovery failures abort the broad scan instead of hammering the API
- cold broad-market scans are research-only
- market-wide PRIMARY candidates require current + one prior PRIMARY confirmation
- fallback/history candidates require current + two prior PRIMARY confirmations
- outcome monitoring uses batch LTP
- ARM OFF continues forecasting and learning; it disables real orders only
- maximum five production recommendations remains a ceiling, not a quota

## Existing trading protections remain unchanged

- long-only NSE CASH/CNC
- no downward averaging
- no downside-loss sell
- one authoritative GTT per app-owned holding
- +0.55% arm / +0.50% protect / +0.10% ratchet ladder
- end-of-day ₹100 estimated-net recovery GTT
- positive-close-only production forecast learning

Version: `7.3.2-resilient-forecast-health` (`versionCode 732`).
