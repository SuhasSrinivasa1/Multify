# Multify 8.3.24 — Timeframe evidence shadow (TEST ONLY)

## Source lineage and protection

Based exclusively on commit a6752e82a8573db4a050334bf8956fde0c7348e0
(v8.3.23). Changes are confined to read-only research diagnostics,
tests, a build workflow, and the Android version label. Never merge
this branch into v8.3.16, either v8.3.19 branch, or the v8.3.23 branch.
The production 15-minute Fixed 40-factor evaluation, confirmation,
market scanning, BUY/SELL/GTT, stop and trailing rules are not amended.

## Added

- Explicit 09:45 IST *research* schedule alongside a separate
  stock-specific 5-minute/15-minute shadow label.
- Compare the already-sampled 5m and 15m Fixed tiers against the **same**
  quote and time. Record early-watch, continuation, divergence and
  exhaustion hypotheses.
- Missing completed five-minute bars and missing/wide bid–ask spreads
  yield missing-data/risk labels; they do NOT grant permission to trade.
- Research runs only inside the existing bounded 5m shadow sample,
  preserving the v8.3.23 prefetch / API protection.
- CI tests assert the 09:45 boundary, conservative missing-data behavior,
  exhaustion warnings and absence of production trading authority.

## Important caveats

A 5m Fixed-model run changes RSI/EMA horizon relative to 15m. Its
PRIMARY tier is therefore NOT equivalent to the production Fixed tier.
The two labels are counterfactual, not independently validated returns.

The diagnostic logs do not prove that 9:45 is optimal. No early-fill
price is assumed; sampled last traded prices cannot establish precise
target-before-stop ordering. Future paired chronological 5m OHLCV
replays must establish profitability *after* slippage and costs.

Leave Fixed Auto Buy OFF while testing. A CI-signed APK may not have a
signing certificate compatible with the existing installation. Do not
uninstall an active trading installation while it owns holdings or
broker-side protective orders, or before exporting local diagnostics.

## Offline timing replay

This research CLI needs timestamped completed five-minute OHLCV and actual
notification-available times. Summary MFE/MAE cannot reconstruct chronology.

Candles CSV headers: symbol,at_ms,open,high,low,close,volume
Signals CSV headers: key,symbol,at_ms,target_pct
Each at_ms is UTC epoch milliseconds. Candle timestamps denote bar START;
signal timestamps must denote when the signal was actually available.

Usage:
python -m backend.app.adaptive_timeframe_replay --candles history.csv --signals fixed_signals.csv > replay.json

Compare identical official Fixed events under (A) next eligible 5m open,
(B) later 5m breakout/volume confirmation, and (C) 9:45 scheduled study.
No entry means zero return, not a winning trade. Missing bars are censored.
If stop and target overlap a candle, pessimistically assume stop.

Default stop 5%, target 1.1161043%, 5 bps each side slippage, 20 bps
roundtrip cost and 60-minute horizon are configurable assumptions, NOT
current exact trailing GTT or broker-fill reconstruction. Earlier-than-Fixed
signals cannot be studied without earlier timestamped candidate evidence.
