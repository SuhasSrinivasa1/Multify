# Multify Trader Pro v9.0.5: Independent LIVE/HISTORICAL Recovery — TEST

## User-provided evidence
Source file: `MultifyTraderPro-logs-v9.0.4-fixed-independent-recovery-test-debug-20261116-134612.zip`.
The 6,840 event rows were read in full. Captured 16 Nov 2026 ~12:57–13:46 IST.

- One official Fixed recommendation (HINDOILEXP), with four PRIMARY observations of that one stock. Seven completed evaluation cycles, no evidence of other distinct confirmed PRIMARY stocks in this window.
- 280 `FIXED_DUAL_ACCOUNT/BETA_DETAIL_ALPHA_COOLDOWN_TAKEOVER` observations.
- 66 `FIXED_DUAL_ACCOUNT/BETA_ONLY_DETAIL_BUDGET_DEFERRED` — locally denied Beta HISTORICAL admissions, not HTTP 429s.
- 45 sampled `FIXED_PERFORMANCE/HISTORICAL_REUSE_SKIPPED`, including 5–9-row fresh histories previously rejected as too short for incremental *reuse* despite >=5 rows being sufficient for Fixed feature extraction.
- Five actual ALPHA HTTP 429 in the export (LIVE_DATA LTP and quote, HISTORICAL candles); **zero Beta 429**. See raw `API_HTTP_TRACE/REQUEST_FINISHED` for exact times and account/category 1s/60s counts.
- Repeated full 53-batch market sweeps with >92% coverage; during Alpha LIVE cooldown, Beta successfully fetched up to 50 of 53 LTP batches. Discovery recovery worked, detailed evaluation became the bottleneck.

## Specific regression
v9.0.4 used `alphaResearchAvailableForFixed()`, a conjunction of Alpha LIVE_DATA and HISTORICAL availability, to decide BOTH category routes. One Alpha LIVE quote 429 therefore shifted healthy Alpha HISTORICAL work to Beta, whose fixed HISTORICAL governor is only 1/s and 8/min. This caused local Beta budget exhaustion and scan delays.

## v9.0.5 fixes, constrained to transport/data integrity
1. Independent per-category account routing (`FixedCategoryAccountRoutingPolicy`): quote provider chosen from healthy Alpha/Beta LIVE; 15m history provider chosen separately from healthy Alpha/Beta HISTORICAL. During Alpha LIVE cooldown, quote=Beta and history=Alpha when Alpha HISTORICAL is healthy. During Alpha HISTORICAL cooldown, quote=Alpha and history=Beta when those categories are available. The original stable one-in-six Beta deterministic split is preserved only if Beta HISTORICAL has *advisory* headroom; the atomic 8/min Beta gate remains authoritative.
2. Read-only Fixed service preflight now accepts independent per-category coverage: any healthy LIVE source **and** any healthy HISTORICAL source. This does not relax account-specific BUY, SELL or protective GTT readiness, which remain Alpha-only.
3. If a genuine Alpha LIVE quote 429 arrives mid-request, retry only the quote once with healthy Beta. If Alpha HISTORICAL gets 429 mid-request, retry only full 15-minute candles once with Beta. Do not replay a successful quote for a history error or replay successful historical data for a quote error. Switch the in-memory history cache key to the real candle account when fallback switches accounts.
4. Short valid 15m history reuse: the original Fixed scorer requires at least 5 broker candles. The incremental cache now accepts 5–9 valid rows, but a new broker request and at least **two overlapping chronological timestamps** are required; non-overlap forces full broker fetch; stale (>12min) or malformed cache rejected. No synthetic candles, 5m substitution, or stale only evaluation.
5. The v9.0.4 dynamic 53-batch discovery takeover, prepared-sweep path, corrected coverage UI, independent Alpha/Beta 60% LIVE_DATA research caps (6/s and 180/60s each), Beta historical 1/s and 8/min, Alpha protective priority and unchanged -2% stop remain.

## Release checks
- Exact byte parity against BOTH v8.3.19 LIVE Fixed strategy branches: `generateFixedForecasts`, `discoverFixedMarketWideCandidates`, 40-factor scorer, quality, ranking, publication and candidate policies. Read-only v8.3.16 shadow clone also exact.
- Original worker/BUY/SELL/GTT service text must remain byte-matched after stripping precisely marked transport preflight and prefetch additions.
- Unit tests enumerate all 16 Alpha/Beta category availability combinations, including Alpha LIVE off/HIST on and converse; historical cache short-row overlap, nonoverlap and stale cases.
- Android unit suite, offline replay suite and APK assembly are required. Automated build success ≠ proven live 90s scans or improved recommendation hit rates.

## Hard constraints and live acceptance
- Provider 429s occurred well below published category ceilings. This change does not claim to eliminate them; app-counters cannot know traffic generated outside this app.
- Under a sustained outage of BOTH Alpha LIVE and HISTORICAL, Beta only has 8 historical requests/min: a complete 48-stock detail evaluation in 90 seconds remains infeasible. Incomplete scans fail closed.
- Baseline discovery still requires two sufficiently complete market snapshots, and PRIMARY still requires historical repeat confirmation. Recommendation *count* is deliberately not forced.
- To verify on NSE hours, gather one new complete diagnostic ZIP: count `SPLIT_LIVE_HISTORY_ROUTING` for live=BETA/history=ALPHA, check Beta history local denial count trending down, two 85%+ official baseline sweeps, successful detailed evaluations and published recommendations, per-account LIVE 1s/60s bounds, and completed scan p50/p90 latency. Trading protection must never be bypassed.
- TEST with Auto BUY off until new live logs. Debug APK signing certificates may vary across CI builds: do not uninstall an app managing holdings or GTT without a verified data/protection reconciliation.
