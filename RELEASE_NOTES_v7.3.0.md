# Multify Trader Pro v7.3.0 — Resilient Forecast

## Why this update exists

The 2026-10-19 v7.2 export proved that ARM OFF did **not** stop forecasting: the engine kept scanning and learning. It also exposed three reliability/quality problems:

1. Android/Vivo restarted the foreground process repeatedly. The export contained 102 foreground-engine starts and 63 notification-listener disconnects. The v7.2 discovery baseline was memory-only, so many restarts turned the next market scan back into a cold baseline pass.
2. Groww/DNS connectivity was intermittent. The export contained 129 market-wide LTP batch errors and 130 per-symbol forecast outcome-monitor errors, mostly inability to resolve `api.groww.in`.
3. Four of the five published forecasts were created on the first cold scan before market-wide discovery was ready. The first five qualifying names could consume the full daily ceiling even if better candidates appeared later.

The five v7.2 published forecasts all missed the approximately 1.44% target in the exported state. Their stored average end-of-day return was about -1.38%, with average MFE about +0.56%. v7.3 improves reliability and publication discipline rather than loosening thresholds.

## Discovery state survives Android process death

- Market-wide previous-LTP and session-baseline maps are persisted locally by trading date.
- A restarted foreground process restores the same-day discovery state instead of starting from zero.
- A new trading date clears the prior session automatically.
- A degraded/partial network scan never overwrites a healthy persisted baseline unless at least 85% of the NSE master is covered.
- Audit logs show coverage %, persisted baseline count, whether state was restored, batch failures and whether a scan was aborted for network health.

## Cold scans are research-only

- A truly cold first market-wide pass establishes the baseline and continues 40-factor research.
- It cannot publish a production recommendation until a healthy broad-market baseline exists.
- ARM state remains irrelevant to forecasting: ARM OFF still disables orders only.

## PRIMARY recommendations must persist across scans

The maximum remains five per day; five is still a ceiling, not a quota.

- A market-wide discovered PRIMARY must be PRIMARY on the current scan and at least one prior scan within the last 20 minutes before it may publish.
- A fallback/history/DNA candidate must be PRIMARY on the current scan and at least two prior scans within the last 20 minutes.
- This prevents one-scan spikes from consuming all five slots.
- Scan logs expose `prior_primary_confirmations`, market-wide discovery membership and `publication_ready`.

## Groww/network outage hardening

- Full-market batch LTP pacing is reduced to leave headroom under Groww's shared Live Data limit.
- Transient LTP failures receive a bounded retry.
- Three consecutive discovery network failures abort the current broad scan instead of hammering all remaining batches.
- Low-coverage/aborted scans preserve the last healthy baseline and cannot publish from incomplete market data.
- Published forecast outcome monitoring uses batch LTP instead of one quote request per recommendation.
- After-hours research backfill stops the current batch after three consecutive transient network failures and retries still-pending rows on a later research pass.

## Existing trading protections remain unchanged

- Long-only NSE CASH/CNC.
- No downward averaging.
- No downside-loss sell.
- One authoritative GTT per app-owned holding.
- Fixed +0.55% arm / +0.50% protect / +0.10% ratchet ladder.
- End-of-day ₹100 estimated-net recovery GTT.
- Positive-close-only forecast learning.

Version: `7.3.0-resilient-forecast` (`versionCode 730`).
