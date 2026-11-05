# Multify Trader Pro v8.1.0 — Early Conviction Dual Lane (TEST)

## Main change
A **second, independent Early Conviction lane** ranks EXISTING first-PRIMARY, two-sweep Market Pulse and spare-capacity Fast Watch evidence. Its source is Android-recorded broker REST data, not an exchange-stamped streaming feed.

- **Early BUY Idea** is displayed when a frozen PRIMARY has been observed, independent corroboration is present, and quality, spread, liquidity and freshness checks pass. It is an early **research signal**, **not** an official Fixed BUY recommendation, permission to execute a broker order, or a calibrated win probability.
- **WATCH** means evidence is incomplete or cautionary.
- Display at most three Early BUY Ideas; if none qualify, show at most one top WATCH.
- At most three EARLY BUY IDEA push notifications per NSE trading day, one per symbol/day, 9:20–15:00 IST.
- Historical raw observations remain in backend diagnostics, not the main Fixed screen.
- New persistent 30-session read-only early opportunity journal tracks first observed price/time, quality score and whether a later official Fixed event occurred. Export/import through `early_opportunity_timeline.json`, with no trading-state restore.

## No production strategy changes
- Frozen Fixed v7.4.1 40-factor/15-minute candle scoring, universe/ranking, 1.1161043% target, full PRIMARY confirmation and v8.0.6 same-stock independent requalification are unchanged.
- Existing Fixed BUY execution eligibility and auto-buy gates are unchanged.
- Existing hard-stop, non-loosening trailing and Groww GTT reconcile code from v8.0.7 is retained.
- Multify signal-to-BUY execution, 0.40% pre-trade quote extension guard and full position reconciliation retained.
- Forecast algorithm and trade permissions unchanged.

## Broker/data budget
The early-conviction classifier itself makes ZERO broker requests. Fast Watch remains discretionary, yields to Fixed and holdings, and has a **12-candidate** maximum (down from 48); Market Pulse captures up to **12** leading research names per sweep (down from 20). This reduces research noise, **not** official Fixed recommendation count.

## Important limitations
- The older experimental Python Groww streaming adapter is still not deployed as a live Android market-data source. Live stream authorization, full NSE universe coverage and hosting have NOT been verified. This APK instead works without external infrastructure, using broker REST observations already collected by Fixed and opportunistic Fast Watch.
- No controlled evidence yet proves that EARLY BUY IDEA labels increase net realized profit. Quality scores are deterministic evidence rankings, NOT win probabilities.
- A true official Fixed recommendation still requires all original confirmations. This APK DOES NOT place an automatic BUY from the early lane.
- No guarantee of faster full NSE discovery; 75–77s REST discovery remains constrained by the existing broker rate governor.
- Forecast incomplete 1-minute candles and delayed historical reconstruction remain conservative and are NOT claimed resolved.
- Tests cannot reproduce actual broker-side GTT fills, cancellation races or live profitability.

## Safety and installation
- This remains TEST. Use with Auto BUY OFF until broker-side protection is separately validated.
- Export logs before changing versions. Debug APKs may be signed with different ephemeral certificates and cannot be installed over one another.
- Never uninstall an app that owns open holdings/protective GTTs or has unexported local data. Confirm the position and GTT at Groww before migration.
