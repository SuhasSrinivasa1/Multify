# Multify Trader Pro v4.2.0 — Auto Wave Select

## AUTO mode changes
- Initial AUTO campaign now preserves reserve capital instead of allowing the first lot to consume the entire campaign budget.
- The initial Multify trade remains wave 1. Every new 2% absolute move from the original AUTO anchor opens a new decision wave.
- From wave 2 onward, AUTO evaluates LONG and SHORT from the **same live snapshot** and removes the Multify BUY/SELL event prior so trajectory, not the original call direction, decides the incremental side.
- Inputs already available in the app: VWAP, EMA 9/20, ATR, RSI, MACD histogram, ATR-normalized slope, RVOL, opening ranges, Donchian levels, squeeze/expansion, candlestick traps/reclaims, order-book imbalance, spread, day change, market cap and 52-week position.
- Mathematical selector estimates side probability and net expected value for the next ₹5,000 tranche after a conservative cost/slippage allowance.
- Exactly one state is allowed at a wave: LONG, SHORT or HOLD. AUTO never intentionally carries simultaneous long and short MIS positions in the same symbol.
- Same-side winner: add up to ₹5,000, recalculate weighted entry/ATR protection, and rebuild OCO for the new net quantity.
- Opposite-side winner: cancel protection, flatten/reconcile the old broker net position, then open only a ₹5,000 opposite probe with fresh OCO. Opposite flips use stricter probability/score/EV thresholds than same-side adds.
- Weak/ambiguous evidence: HOLD (no forced add and no forced flip).
- Live monitor and pre-event Forecast evaluations are now event-neutral; Multify event priors remain only on the fresh notification path.
- Audit events added: AUTO_WAVE/DIRECTION_DECISION, HOLD, RISK_VETO, SAME_SIDE_ADD, ONE_SIDE_FLIP and NO_RESERVED_BUDGET.

## Build version
- versionCode: 420
- versionName: 4.2.0-auto-wave-select

## Validation completed here
- Pure Kotlin WaveDirectionMath compiles successfully with Kotlin/JVM.
- Smoke tests verified 2% wave detection, bearish SHORT selection and weak-signal HOLD behavior.
- Full Android APK assembly was not run in this environment because Android SDK/Gradle tooling is not installed.
