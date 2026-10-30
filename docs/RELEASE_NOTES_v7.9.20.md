# Multify Trader Pro v7.9.20

## Protected Fixed and BUY paths
- The Fixed 40-factor scoring engine, recommendation target contract, entry-quality overlay, 150-second cadence, and five-minute candle input remain unchanged.
- The Multify, Fixed and Forecast BUY submission and Auto Buy scheduling paths remain unchanged. Only Multify holding trailing metadata is updated to describe the net-profit policy.
- Fixed market discovery reserves a portion of the same candidate budget for low-pulse pre-spike/consolidation and second-peak pullback setups (session move up to 12%). The existing full scoring and entry safety checks still decide publication.

## SELL execution and reconciliation
- The common -2% hard stop remains active until net-profit trailing is armed.
- The profitable trail arms only after the estimated delivery-cost model supports at least +0.50% net return plus a 25 bps execution-slippage reserve.
- The existing +0.10 percentage-point ratchet cadence is retained.
- Overnight recovery GTTs target the greater of ₹100 net or estimated net +0.50% of invested capital; their child SELL remains LIMIT-based.
- A triggered Groww GTT is treated as an exit-pending state. The app locates its child SELL order by reference, then by a unique symbol/side/product/time match.
- Actual order average fill price and trade records are used for realized gross/net P&L; the GTT trigger is never recorded as the fill.
- Partial fills are recorded atomically with the remaining quantity reopened for protection.
- A terminal zero-fill child order is reconciled before the app re-protects or invokes the local emergency fallback. No second SELL is sent while the broker child order remains unresolved.
- Pending GTT exits are processed before broker-quantity ownership conflict checks.

## Forecast
- Historical Forecast MFE only influences the adaptive target after a minimum of five valid outcomes across three sessions and three distinct symbols.
- Candidate target selection enforces estimated +0.50% net economics after estimated charges and spread.
- Existing strict spread, participation, breakout, extension and entry-quality gates are not relaxed.
- PRIMARY and SECONDARY eligibility remain separate; SECONDARY is watch-only.

## Data migration
- Room database version 17 adds persistent GTT reference, trigger-time, child-order, and net-profit floor metadata using migration 16->17.
