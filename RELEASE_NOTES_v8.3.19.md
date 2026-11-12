# Multify Trader Pro v8.3.19 — Alpha Read-Request Pacing (TEST)

## Evidence: November 12, 2026 v8.3.18 cumulative 12:02 IST export
- Eight ALPHA LIVE_DATA HTTP 429 responses (seven /v1/live-data/quote, one /v1/live-data/ltp) in three clusters around 10:30, 10:33, 10:40, and 10:58. Some quote responses were simultaneous.
- Three ALPHA NON_TRADING HTTP 429s: holdings 11:10, positions 11:15 and protective GTT status 12:02. BETA recorded no HTTP 429s.
- 61 deferred Fixed scan audit events, cumulative across the export.
- Actual APEX BUY filled 83 shares; broker-side HARD_STOP_GTT ACTIVE at INR 333.8 as of 12:01:59. GABRIEL BUY was rejected due to broker RMS insufficient margin. Account recovery/GTT status must be checked directly with Groww.
- Zero Historical HTTP 429s; historical reuse needs additional proof before changing cache policy. This release intentionally avoids speculative caching or switching Beta data after Alpha cooldown.

## Changes
- Add independent coroutine-based pre-dispatch Alpha GET start spacing for /v1/live-data/quote (initial 850ms, slowly relaxes to a 650ms floor only after clean periods).
- Pace routine Alpha account GET reads (holdings, positions, advanced order list and GTT status) on a separate lane (initial 1000ms, 900ms clean floor). Protect order creation, order details, modifications/cancellations and 401/auth requests from this extra lane.
- Adapt each lane conservatively on HTTP 429. Existing category cooldown, 401 recovery and LTP/historical limits remain in force. No automatic retry bursts.
- Trace endpoint pacing via the existing API_RATE_LIMIT CLIENT_GOVERNOR_WAIT event reason, e.g. ALPHA_QUOTE_ENDPOINT_PACING and ALPHA_ACCOUNT_READ_PACING.
- Add pure policy unit tests and frozen-trading-source CI assertions.

## Not changed
- Fixed selection, 40-factor scoring, quality thresholds, publication, target 1.1161043%, -5% stop, +0.55/+0.50/+0.10 trailing, API order routing, GTT creation/modification or verification frequency, frozen Forecast, or historical candle logic.
- No new Alpha/Beta substitution or arbitrary suppressions of recommendations.
- Source locks do not guarantee identical market outcomes: quote retrieval latency can affect the timing of candidates and price snapshots.

## Deployment warnings
- TEST build. GitHub CI unit tests/compilation are not a live Groww quota or execution test.
- A different debug signing certificate cannot upgrade the current installed APK in place.
- APEX has a filled position and an app-tracked broker-side protective GTT in the last uploaded export. Before any replacement, verify APEX and its GTT with Groww, export local data, and do not uninstall or clear data if protection or ledger state remains active.
- Do not enable unattended Auto Buy until a data-preserving install, reconnection, live cooldown and broker GTT verification have passed.
