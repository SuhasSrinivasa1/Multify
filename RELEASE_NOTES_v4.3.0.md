# Multify Trader Pro v4.3.0 — Ten-Wave Trajectory Engine

## First wave is preserved
- Wave 1 remains driven by rolling learned averages.
- AUTO: long leg + eligible post-sell short leg.
- LONG: long leg only.
- SHORT: monitor the long call, execute only the post-sell short leg.
- First-wave protective stop and favourable-only trailing stop are always active for AUTO/LONG/SHORT.

## Waves 2–10
- Maximum is now 10 proper waves, 2% spacing from the original anchor.
- Each active wave uses a fresh event-neutral market snapshot and chooses one side only: LONG, SHORT or HOLD.
- ₹5,000 add/probe per active wave, subject to campaign budget and risk vetoes.
- If the user selects fewer active waves, later waves remain prediction-only and are logged as PREVIEW_ONLY with the preferred green side.
- Opposite-side decisions flatten/reconcile first; the broker never carries hidden simultaneous long and short MIS positions.

## Trajectory formula
The wave classifier combines: trend slope, VWAP position, EMA9/20 relation, relative-volume confirmation, order-book imbalance, day move, ensemble long-vs-short score, market/sector context when available, and stock-specific learned history when available. Missing context is neutral, never fabricated.

Trajectory classes: UP_TREND, DOWN_TREND, OSCILLATING, NEUTRAL. In OSCILLATING mode both directions can be used on different waves, but only one direction is selected at a time.

## Risk
- Continuous ATR ratcheting trailing stop begins after ~0.60 ATR favourable movement and tightens after ~1.50 ATR.
- No wave order is placed when the selected wave is outside the configured active-wave count.
- Existing spread, risk, ownership/reconciliation and daily-loss vetoes remain.

## Build note
This source is ready for Android SDK 35 / Java 17. The current ChatGPT runtime does not contain the Android SDK/Gradle toolchain, so it cannot truthfully emit a compiled APK binary here. Since the prior app was uninstalled, the next local build can be installed fresh even if a new signing key is used.
