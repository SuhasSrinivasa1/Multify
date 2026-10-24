# Multify Trader Pro v7.8.0 — Forecast Validation

This release is a Forecast/adaptive-only refinement of v7.7.3. The frozen Fixed engine, candidate discovery/ranking, historical snapshot, 150-second scan interval, publication policy, source locks, one-way learning boundary, shared stepped trailing policy, runtime priority, API guard, ARM/auto-buy safeguards and Room migration integrity remain protected.

## Forecast changes

- Adds post-cost candidate economics using the existing Groww delivery-charge model plus conservative spread/base slippage assumptions. A Forecast PRIMARY must remain profitable after estimated charges and slippage before publication.
- Adds a current risk-adjusted measure using net expected return versus an ATR/slippage downside proxy. It is used only as a ranking tie-breaker; existing learning bonus caps are unchanged.
- Analyses positive, flat and adverse historical research outcomes together, including sample count, distinct trading days, distinct symbols, consistency, median MFE/MAE/close and chronological holdout target-hit rate.
- Keeps historical negative penalties, stricter liquidity/data-quality filters, contextual target recalibration, historical confidence bonus proposals and regime-specific factor-family weights in shadow mode only.
- Stores richer research evidence and fixes research replay to persist the candidate's actual context-expanded target rather than the unexpanded base target.
- Adds Forecast-versus-Fixed after-hours comparison, realised Forecast exit/net/MFE/profit-capture diagnostics, and time-of-day outcome analysis.
- Adds per-scan performance/API telemetry (scan/discovery/evaluation duration, cache hits/misses, request and 429 deltas, governor waits) and Forecast recommendation-to-notification latency telemetry.
- Sector learning remains `UNAVAILABLE`; no unreliable or non-timestamped sector source was introduced.

## Validation policy

All experimental historical penalties, target changes, stricter filters and family reweighting are recorded with `applied_live=false`. Their purpose is out-of-sample validation before any future live-policy promotion.

