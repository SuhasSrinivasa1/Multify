# Multify Trader Pro v6.8.0 — Final Adaptive Forecast

## Final forecasting and learning architecture

- Multify's rolling 30-trading-day headline average is now **maximum favourable excursion (MFE)**, not Multify's reported close return.
- MFE is measured from the recommendation entry reference to the highest Groww candle price observed before Multify's Book Profit/close timestamp.
- New live Book Profit events capture MFE immediately when Groww history is available.
- Existing timestamped historical Multify calls are progressively backfilled from Groww historical candles. Missing timestamps are excluded rather than estimated.
- The forecast engine has a hard **1.00% minimum target** per stock.
- Forecast target percentage is frozen for the session from prior evidence only: max(1.00%, rolling Multify MFE average, robust prior forecast full-session MFE).
- Same-day target hits no longer inflate later targets during that same session.
- Forecast MFE continues updating after a target is hit until the session ends, so winners are not artificially truncated at their target.
- Weak calls are no longer forced to fill five slots. Five is a maximum, not a quota.
- Live forecast candidates must be high-quality **TREND** setups with minimum confidence/score, positive VWAP/EMA structure, RVOL, positive slopes, acceptable spread, and enough ATR-based volatility capacity to support the adaptive target.
- The poor MIXED-regime path observed in v6.5-v6.7 logs is no longer admitted unless the policy is changed in a future researched release.
- Multify DNA symbols and recurrence still influence ranking, but cannot override the hard quality gate.

## Dynamic trailing

- Autonomous trailing now follows the current rolling Multify MFE average.
- The trail-arm threshold is **max(0.50%, Multify 30-day MFE average)**.
- The threshold is cost-aware and applied to estimated net profit, so the protected stop must cover the dynamic threshold after estimated cash-market costs.
- When Multify MFE rises above 0.50%, all app-owned LONG holdings dynamically use the higher value.
- Existing armed stops that no longer protect the current dynamic MFE threshold are reset to the emergency failsafe and must re-qualify.
- Once armed, the ₹0.25 high-water ratchet, adaptive ATR/price trail, and non-loosening stop behavior remain active.
- Emergency/failsafe loss protection remains independent.
- A genuine Multify Book Profit notification remains independent and can still close the app-owned Multify holding.

## Observability

- Averages UI now labels Multify MFE explicitly and shows historical MFE backfill progress.
- Forecast UI shows today's average target and the hard 1.00% floor separately from observed full-session forecast MFE.
- Holdings show the dynamic MFE trail-arm percentage separately from the forecast/reference target.
- Logs export both Multify max price and MFE percentage.
- Version: `6.8.0-final-adaptive-forecast` (`versionCode 680`).
