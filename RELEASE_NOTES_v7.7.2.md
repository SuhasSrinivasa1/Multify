# Multify Trader Pro v7.7.2 — Universal Positive Replay

## Non-negotiable invariants
- Fixed remains the byte-for-byte protected v7.4.1 reference contract.
- Fixed scan timing, ranking, publication, target logic and auto-buy path are unchanged from v7.7.1.
- The shared stepped trailing policy is unchanged: arm +0.55%, protect +0.50%, ratchet +0.10%.
- Multify immediate execution and market-hours API priority architecture remain unchanged.

## Universal positive evidence for Forecast
Forecast can now learn positive evidence from three independent sources:
- FIXED
- MULTIFY
- FORECAST

A target hit is not required. A case qualifies as positive evidence when any of the following is true:
- an actual realized close is positive;
- the research/session close is positive;
- target was hit; or
- maximum favourable excursion was enough for the unchanged stepped trailing policy to arm and protect a positive stop.

Negative and flat outcomes remain diagnostic only and never create negative ranking weights.

## Context and anti-overfitting
Each positive evidence row records, when available:
- 40-factor snapshot;
- Momentum/Trend, Participation/Liquidity and Structure/Context family scores;
- dominant family;
- market breadth regime;
- market-cap proxy cohort (LARGE_PROXY / MID_PROXY / SMALL_PROXY);
- time-of-day cohort;
- strategy and setup regime;
- MFE, MAE, close/realized return, target hit and protected trailing return.

Context bonuses do not activate from one day. Evidence must mature across multiple samples, at least 3 distinct trading days and 3 distinct symbols. Exact-context evidence requires 5 rows; broader fallback context requires 8.

The cap cohort is explicitly a model proxy based on the existing market-cap factor, not an exchange/legal large/mid/small-cap classification. Sector context remains scaffolded as UNAVAILABLE until a reliable sector source is available.

## After-hours replay
No new replay traffic runs in the market-hours critical path.
After market:
- all captured Forecast research snapshots can be outcome-backfilled, not only the first five sessions;
- completed Forecast rows are replayed into the universal evidence ledger;
- retained Fixed recommendations are replayed against historical 5-minute candles in bounded batches so a target miss that crossed the +0.55% trail-arm level can still become positive Forecast evidence;
- positive Multify calls use a nearby live Forecast snapshot when available; otherwise the app reconstructs a historical 40-factor approximation after hours;
- historical Multify reconstruction leaves live-only order-book/spread fields unavailable rather than inventing them.

Fixed is only read as an evidence source. No replay result is written back into Fixed.

## Diagnostics and export
The diagnostics ZIP now includes forecast_positive_evidence.json in addition to the existing Forecast research snapshots and deep telemetry.
FORECAST_REPLAY / UNIVERSAL_POSITIVE_EVIDENCE_REFRESH logs source counts, positive replay counts and diagnostic-only counts.
