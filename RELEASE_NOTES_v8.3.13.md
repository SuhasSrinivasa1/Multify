# Multify Trader Pro v8.3.13 — Unified API Budget and Fixed Performance

Baseline: v8.3.12 source commit 2725df3aca18cccb81b8a303222526823675e997.

## Production objectives

- Preserve the frozen Fixed recommendation and execution contract.
- Reduce avoidable Alpha/Beta HTTP 429s and prevent Beta failures from flooding Alpha.
- Keep full NSE discovery and detailed Fixed evaluation ahead of background market-data work.
- Make coverage reflect valid broker prices across the full configured NSE master, not only prices above the discovery ranker's floor.
- Keep Fixed Auto Buy independent of unrelated LIVE_DATA cooldowns after a fresh quote has been obtained.

## Transport and throughput changes

1. **One rolling Alpha/Beta market-data budget**
   - Alpha LIVE_DATA: 55 requests/minute; Alpha LTP endpoint: 18/minute.
   - Beta LIVE_DATA: 35 requests/minute.
   - Combined LIVE_DATA envelope: 90 requests/minute.
   - Historical budgets are tracked separately (Alpha 45/minute, Beta 8/minute, combined 53/minute).
   - The coordinator tracks both accounts and records lane-specific 429 pressure. For five minutes after a 429, the affected lane and shared envelope temporarily back off; the normal limits restore automatically after the pressure window.
   - Alpha order traffic remains on the Alpha order category. Beta remains read-only.

2. **Fixed-first scheduling**
   - The 53-batch full-NSE LTP sweep is routed as 35 Beta batches and 18 Alpha batches when both accounts are healthy.
   - Beta is reserved for wide LTP discovery; Alpha handles detailed Fixed quote/candle evaluation and all broker execution.
   - Fixed and execution requests receive internal priority over background market-data requests.
   - Internal priority metadata is stripped before requests reach Groww.

3. **Cooldown-aware recovery**
   - Beta Fixed prefetch can wait up to 90 seconds for its lane to recover; ordinary opportunistic Beta reads remain bounded.
   - The affected lane is paced when it resumes. A Beta cooldown does not automatically send an unbounded queue to Alpha.
   - Alpha fallback is subject to the same rolling per-account and combined budget. Alpha cooldowns suppress the fallback locally rather than sending doomed requests.
   - Alpha and Beta 429s remain lane-specific; an unrelated LIVE_DATA cooldown does not become an ORDERS cooldown.

4. **Targeted coverage repair**
   - Any finite positive broker LTP is counted for universe coverage, including values below ₹10.
   - The unchanged discovery ranker still excludes prices below ₹10, and the frozen Fixed entry band remains unchanged.
   - Missing or invalid symbols are retried in targeted batches once; successful 50-symbol batches are not fetched again.
   - Logs separately report raw returned symbols, valid positive LTPs, below-₹10 observations, missing broker symbols, invalid prices and targeted-retry recovery.

5. **Fixed Auto Buy cooldown stages**
   - Before a quote: LIVE_DATA and NON_TRADING must be available.
   - After quote failure: only LIVE_DATA is checked.
   - Before MARKET BUY: NON_TRADING ownership verification and ORDERS availability are required.
   - A LIVE_DATA cooldown that starts after a fresh quote no longer vetoes the order. Fresh quote, duplicate ownership, order-endpoint, price, liquidity, safety-halt and session checks remain mandatory.

6. **Diagnostics**
   - Export a unified Alpha/Beta rolling-budget snapshot alongside the existing Alpha API snapshot.
   - Preserve separate account counts, cooldown pressure, missing-symbol counts and retry outcomes.

## Mac / streaming note

The repository contains a research-only GrowwFeed streaming adapter that supports up to 1,000 instruments per subscription. Three subscriptions could cover a 2,645-symbol universe. The Android production app does not yet consume that adapter; this APK remains REST-based and does not depend on an unverified streaming service. A Mac source/log comparison is still needed to confirm whether the Mac application used this exact streaming path.

## Explicitly unchanged

- Frozen v7.4.1 Fixed 40-factor model and factor weights.
- Fixed selection, ranking, candidate ordering, confidence and PRIMARY classification.
- Fixed recommendation publication and target calculations.
- Fixed price band, liquidity and entry safeguards.
- Existing Fixed scan cadence and historical candle interval.
- Fixed outcome learning/averages and frozen reference inputs.
- SELL protection, hard stops, trailing logic, GTT lifecycle and position ownership.
- Per-order sizing and Alpha-only order execution.

The changes in this release are request routing, admission, recovery, coverage accounting and telemetry. No strategy threshold or stock-selection rule is changed.

## Verification

The dedicated CI workflow checks the frozen Fixed/SELL/GTT source, verifies that Groww endpoint paths and parameters are unchanged apart from internal priority metadata, runs backend tests and Android unit tests, builds the APK, verifies its package/signature and packages the exact source.

The CI workflow now supports stable production signing when the original keystore is configured as GitHub Actions secrets: MULTIFY_KEYSTORE_BASE64, MULTIFY_KEYSTORE_PASSWORD, and optionally MULTIFY_KEY_ALIAS / MULTIFY_KEY_PASSWORD. Without those secrets it produces a CI-debug-signed fallback APK, which may not update an installation signed with a different key. CI success does not replace live broker validation.
