# Multify Trader Pro v8.3.22 — Intraday Pattern & Timing Research

Base: CI-verified v8.3.21 primary carry-forward/entry-timing build. Version code: 8403.

## Implemented, research-only, no new Groww API calls
- 40-factor feature journal now records standardized hypotheses: green-candle volume >=80%, RVOL >=3, resting order-book imbalance with spread <=40bps, their three-way combination, price within 3 ATR of VWAP, RSI >=75 with nonpositive slope, VWAP extension plus near session high, and opening/midday session cohorts. Nullable unobserved data is NEVER treated as an actual negative signal.
- The full-NSE market-path export now aligns previous approximately-90s change against the actual approximately-five-minute LTP sample for EACH selected candidate. Records deceleration/reversal research.
- Exact unclamped Fixed pre-0.99-cap scoring components are recorded in a bounded asynchronous audit event for each selected candidate; production score and ordering are unchanged. The frozen Forecast champion/research feedback bonuses are exactly zero.
- For every published Fixed event, log first available prior PRIMARY price and scan time, actual recommendation persistence time, matched evaluation quote-client time if known, and percentage of the fixed target already consumed before publication.
- Add a distinctly labelled non-tradable PRIMARY early-watch section to the Fixed screen: "AWAITING CONFIRMATION", up to six fresh candidates, NO BUY button. Official publishing/confirmation and Auto Buy remain unchanged.
- When the user opens the Fixed screen, log the first observed Compose screen state per recommended event in separate read-only shared preferences. Export as `fixed_screen_compose_observation.json`. This is NOT verified physical pixel rendering; if the screen was not opened, no sample exists.
- Export first-PRIMARY to published-entry chronological evidence as `fixed_first_primary_to_publication_study.json`; all source prices are sampled quotes, not executable fills.
- Extend maturity-matched sampled MFE research to 15, **20**, 30 and 60 minutes with observed +0.50%, +0.55%, +1%, full target, first sampled hit time and later below-entry observations. No hindsight lookahead into production decisions.
- The `fixed_shadow_hypotheses_manifest.json` declares 16 test ideas for longitudinal investigation: timing, exhaustion, volume, opening window, repeats, score saturation, early watch, API budget and held-out validation.
- Keep v8.3.21's 5-slot PRIMARY follow-up, unchanged original confirmation, price-band-aware 28-stock dynamic discovery, and research LTP throttle intact.

## Deliberately NOT enabled
- No new trading confidence/RSI/VWAP/volume filters.
- No new automatic exits, target changes, retraining, self-learning, or intraday app resets.
- No predictive claim of 90% accuracy. A single day's 14/15 early +0.50% observations are not independent, reproducible evidence.
- No automatic day-one learning from research hypotheses. No source-quality replacement for true exchange tick and volume data.

## Frozen production contract
107 canonical calls; 40-factor Fixed; existing PRIMARY, spread, RVOL, breadth and 15m VWAP criteria; target +1.1161043030113955%; +0.55% trailing arm, +0.50% initial protection, +0.10% increments; hard -5%; broker BUY/SELL/GTT and Alpha/Beta ownership unchanged.

## Important real-trading deployment note
CI debug build identity may not match the installed APK and may refuse in-place upgrade. Do not uninstall or clear a device with live broker-managed app state or GTTs. Verify broker inventory and active GTTs separately. Keep Fixed Auto Buy OFF while validating v8.3.22's diagnostic output. This build is test-only, not proven to outperform v8.3.21.
