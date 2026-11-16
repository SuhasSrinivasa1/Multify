# Multify Trader Pro v9.0.6 — API Recovery and Research Integrity TEST

## Baseline and scope
Forked from the exact v9.0.5 independent quote/history routing branch on 16 November 2026.
Frozen LIVE Fixed decision/ranking/publication functions, 15-minute scoring, 28 discovery and
48 detailed slots, 107 reference calls, 1.1161043% calibration, original -2% hard stop,
+0.55% / +0.50% / +0.10% trailing, and Alpha-only BUY/SELL/GTT are retained.
Forecast research and automatic buying remain frozen; no new five-minute live strategy.

## Changes made
1. **Research-only Alpha quote pressure relief:** the already-enforced Alpha quote
   interceptor updates a read-only advisory through `AlphaReadPacingPolicy` after quote
   429/success. If the Alpha quote lane is in a post-429 >=1.8-second gap and Beta
   LIVE is healthy, Fixed detailed research can obtain its quote through Beta while
   healthy Alpha HISTORICAL continues supplying the original 15-minute candles.
   Independent provider routing and broker admission remain authoritative;
   no extra history requests are authorized by this hint.
2. **Adaptive next-sweep timing:** track up to 12 completed, valid full-NSE sweep durations
   per actual DUAL, ALPHA_ONLY, BETA_ONLY lane. Once 3 samples exist, use nearest-rank
   p90 + 8 seconds, bounded to 35–150 seconds; otherwise retain the existing
   50s / 82s / 76s leads. The original 45-second snapshot freshness and 85% coverage
   checks, same-slot deduplication, and 90-second nominal cadence remain unchanged.
   Incomplete/aborted sweeps never affect the timing estimate.
3. **Full-NSE missing data diagnostics:** classify broker-returned invalid LTP as
   NON_FINITE, NON_POSITIVE, BELOW_EXISTING_LTP_FLOOR; preserve missing-symbol
   counts and emit up to 20 sanitized examples. There are no extra LTP calls,
   relaxed stock gates, or fabricated market prices.
4. **Historical cache diagnostics:** distinguish never-before-seen cache keys
   from symbols whose valid broker window was previously stored but evicted
   from the bounded 128-entry LRU. Keep the existing fresh-delta, 2-overlap,
   12-minute and timestamp validation unchanged.
5. Version code 9006, distinct TEST version name, updated CI source lock, new
   focused tests for routing, independent lead calculation, invalid-data 
   audit behavior, and cache classification.

## Explicitly not changed
- Existing per-account 60% LIVE_DATA research ceilings (6/s and 180/rolling minute),
  Beta history 1/s and 8/min, Alpha history 1/s and 45/min, and current broker cooldown rules.
- GrowwRateLimitInterceptor, its prior unmodified Alpha protection admission logic,
  and the order/GTT lifecycle.
- Fixed candidate selection, score, quality gates, publication behavior,
  or the frozen v8.3.16 research-only shadow clone.

## Testing required before live use
- Verify byte parity of the frozen v8.3.19 Fixed decision and v8.3.16 shadow,
  and broker execution source-lock checks.
- Run all Android unit and backend replay tests, compile APK and check signature.
- Compare a complete NSE session against v9.0.5: p50/p90 scan time, p50/p90
  LTP sweep time, Alpha quote local wait counts and duration, Alpha/Beta 429s,
  effective coverage, Fixed detailed evaluation completeness, and GTT readiness.
- Confirm Beta remains read-only and no fake prices or partial publication.
- Verify signing fingerprint before any attempt at an in-place upgrade. A CI
  debug signature is not automatically compatible with an installed app.

This TEST change is not evidence of live profitability, no-429 reliability,
or a consistently achieved 90-second scan. Keep Auto BUY OFF during validation.
