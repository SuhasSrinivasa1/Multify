# Multify Trader Pro 9.0.0 MASTER TEST — evidence before strategy promotion

Base: v8.3.28 commit 309b50c297964bf313b26c651a1287e1795e5807.
Branch: v9-master-validation-shadow-20261113.
This is ONE consolidated source lineage, NOT a merge of divergent old PRs.

## What is still LIVE (unchanged)
- Fixed official production uses the v8.3.19 strategy implementation,
  15-minute completed candles, original 40-factor engine, unchanged
  28 discovery candidate slots, 48 detailed candidate slots, original
  PRIMARY repeat confirmation, target calibration 1.1161043%.
- Service retains v8.3.27 single-flight Fixed worker, unique scan IDs,
  unambiguous published-recommendation ID attribution and existing
  90-second nominal schedule (not a proven 90-second completion time).
- All active-position protection, order ownership, SELL/GTT verification,
  Auto Buy safeguards, 0.55/0.50/0.10 positive gain trail retained.
- Common app-owned CNC long hard stop remains -2.0% of actual average
  filled entry price, with verified existing GTT in-place tightening.
  A -2% trigger cannot guarantee that slippage/gaps stay within -2%.
- v8.3.26 guarded REAL five-minute candle research remains read-only:
  one discretionary historical Alpha request when permitted, with
  after-close 15:35–18:00 IST window, original quote cutoff, censored
  15m/60m forward observations. Never feed 5m data into live BUY.
- No recommendation veto based on slow scan. Existing broker API 429
  safety cooldown policy is NOT relaxed without verified quota evidence.

## New master challenger — SAME input, no additional broker API call
- Original v8.3.16 indicator engine cloned unchanged under
  V9Fixed8316StrategyShadow. Source blob:
  6dab9793449456db8f42df13011d305f47092b63.
- Original v8.3.16 quality policy cloned unchanged under
  V9Fixed8316QualityShadow. Source blob:
  55cf70f60a366f0271cd1cdb7f5faf7d4aaab9d3.
- v8.3.19, v8.3.26 and v8.3.28 have the same Fixed scoring/quality
  engine; use the existing v8.3.19 implementation for their common
  baseline rather than redundantly copying three identical engines.
- Each already-fetched production 15m quote+history is offered to
  bounded asynchronous CPU-only shadow worker (queue capacity 128).
  It evaluates the original v16 vs live v19 indicators and PRIMARY
  quality using a shared NEUTRAL breadth proxy, then records
  V9_MASTER_SHADOW/MATCHED_V16_V19 in existing export audit JSONL.
- This is NOT full independent v16 market-wide discovery/ranking
  replay: official current market breadth is unknown at this callback.
  All research labels are non-executable and cannot select an Auto BUY.
- Old v16 accepted unverified timestamps, still-forming bars and
  previous-session VWAP, and allowed missing spreads; a resulting
  higher retrospective hit rate might be lookahead bias or unsafe
  liquidity selection. Research records explicitly flag that risk.

## API quota diagnosis — reusing existing trace, not reinventing it
- Important audit correction: the ORIGINAL GrowwHttpTraceLedger already
  records actual ALPHA/BETA requests at dispatch, response status,
  endpoint, and 1s/60s endpoint/category/account counters. We removed
  a redundant v9 counter before the final build.
- The NEW offline v9_master_log_replay.py reads those existing 200/429
  endpoint records to compare successful dispatch windows with windows
  rejected by Groww. There are no additional broker requests, no new
  admission locks, and no additional telemetry emitted by HTTP code.
- In the Nov 13 v8.3.27 session, quote 429s occurred at 10, 37 and 18
  local same-endpoint requests/minute, while a successful quote request
  was observed at 45/min. Holdings 429s occurred at 12, 9 and 3/min,
  while other holdings requests succeeded at 12/min. Thus the
  local request rate is not a deterministic broker-side quota.
- Alpha & Beta broker transport and existing cooldown/pacing policies
  are now byte-identical to v8.3.28. Do not increase concurrency or
  invent a fixed rate cap from a few 429s; causes may include hidden
  shared quotas, windows, other sessions, server state, and
  account/IP-wide traffic unavailable to this client.
- Identify preventable duplicate or low-priority calls separately.
  Never use stale cached quotes to authorize a live BUY/SELL.

## Historical evidence, not invented winner
Only five original raw diagnostic ZIPs are currently available:
v8.3.25 (09:58/10:11), v8.3.26 (12:47), v8.3.27 (13:56/15:11).
Use the LAST cumulative export per installed version; old export IDs
are subsets in the later export for v25 and v27.
The 15m sampled-full-market-LTP maturity cohorts are:
  v8.3.25: 8 mature / 3 target hits = 37.50%
  v8.3.26: 2 mature / 1 target hit = 50.00%
  v8.3.27: 13 mature / 3 target hits = 23.08%
Total: 23 mature / 7 hits = 30.43%.
These are not realized profits, use different times in ONE trading
session, have large sampling uncertainty and cannot rank strategies.
No original raw v8.3.16/v8.3.19 ZIP or v8.3.28 live-session ZIP was
available in the current workspace. No valid numerical claim that
v16, v19, v26 or v28 is best can yet be made. Historic v7.4.1 is
preserved only as a source and golden recommendation contract, not a
complete matched candle/outcome replay dataset.

## How to use on Nov 16, 2026
1. Keep Fixed Auto BUY OFF in the TEST build until safety and broker
   GTT reconciliation are confirmed. Debug signing certificate may
   differ; NEVER uninstall current position-tracking app casually.
2. Export the diagnostic ZIP before and after market and after the
   optional five-minute research window. Do NOT rely only on a final
   oversized export: archive periodic ZIPs without deleting local
   evidence. The app still streams ZIP entries to avoid memory spikes.
3. Validate the number of shadow matched samples, dropped queues,
   5-minute paired samples, existing GTT status and broker 429 pressure.
4. Run backend/app/v9_master_log_replay.py on the exported ZIP(s).
   Keep latest cumulative export per installed version; do not count
   immature or unobserved records as failures.
5. Compare on SAME market-day candidates; exclude lookahead features
   and timestamp-invalid bars; evaluate target-before -2% stop,
   5/15/30/60-minute horizons and realistic cost/slippage assumptions.
6. Keep the production v19 Fixed logic until held-out evidence shows
   a challenger improves risk-adjusted performance without hurting
   broker/API availability or trading safeguards. Do not auto-switch
   merely based on a few winning recommendations.

## Invariants and release acceptance
- Production Fixed generation/discovery blocks and nine immutable
  quality/scoring/selection policies must match BOTH v8.3.19 sources.
- v16 shadow implementation must match v8.3.16 source at the object
  body (modulo research-only object rename).
- The v8.3.28 API governor, service worker, actual LONG hard stop,
  trailing and GTT controls must not be silently changed.
- Offline log replay test, versioned candle tests, full Android JVM
  regression tests and APK build must pass from the same SHA.
- Passing CI alone is not evidence of better market outcomes or
  eliminated broker 429 cooldowns.
