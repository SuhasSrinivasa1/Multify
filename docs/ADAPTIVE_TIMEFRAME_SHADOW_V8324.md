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
