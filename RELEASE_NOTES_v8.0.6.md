# Multify Trader Pro v8.0.6 — Fixed Repeat Recommendation Correction TEST

## User-requested behavior
The same NSE stock may receive another **Fixed** recommendation on the same trading day if the frozen full-market scanner independently observes a genuinely new eligible setup. Reprocessing the same recommendation event must not create another alert or BUY order.

This release is a deliberately narrow exception to Fixed's previous *one-symbol-per-day* publication identity. The frozen 40-factor scoring, 15-minute candle calculations, universe, ranking, profitability target (1.1161043%), price/capacity gates, and original broker SELL/GTT protections are unchanged.

## Precise conditions for a repeat
- If no prior local or restored Fixed event exists for a symbol, the original PRIMARY confirmation and discovery requirements apply.
- After a prior event, an explicit non-PRIMARY/non-publishable/non-executable observation **after** that event must demonstrate that the original setup ended. A missing observation does **not** count as a reset.
- At least **30 minutes** must separate the prior recommendation and the proposed new one.
- At least one fresh qualifying PRIMARY research observation must occur after the reset and **before** the new scan, and the current scan must still pass the original Fixed publication/confirmation policy, including market-wide discovery and fresh 20-minute confirmation evidence.
- If restored legacy research contains a previous event, its timestamp is respected. An old event is not treated as new simply because the APK was reinstalled. Later genuine setups are not blocked by the stock symbol alone.

## What was changed in source
- The permitted changes inside `generateFixedForecasts` are exactly: per-scan observed research price metadata, the user-authorized new-event requalification criterion, and an event-specific recommendation ID. Protected-source CI reverses these three fragments and requires the entire Fixed 15-minute selection block to match its historical baseline byte for byte.
- FixedRecommendationStore now retains separate entries by event ID instead of overwriting all same-day records with the same symbol. 63-bit stable SHA-256-derived IDs prevent an accumulating archive from being limited to 32-bit collisions.
- FixedHistoricalEvidenceArchive and FixedOutcomePerformanceStore key observations by **date + symbol + event timestamp**, preserving distinct performance records.
- The imported same-day event journal stores **symbol + exact generatedAtMs** rather than blocking the entire symbol. It is strictly research state: never restores actual holdings, orders, GTTs, or automatically buyable recommendations.
- Notification deduplication uses **symbol + generatedAtMs**. Legacy v8.0.5 single-symbol notification keys are mapped to their earliest known local event. A later confirmed event may notify separately.
- Manual Fixed BUY resolves the most recent active same-symbol event; auto Fixed BUY evaluates only the newest active event per symbol and continues respecting live holdings and legacy GTT/order checks.
- Fixed screen shows separate same-symbol recommendation events and disables BUY on older rows. User-visible time is labeled **scan start**, not publication time. Exact customer notification timings retain their own logs.
- New policy/unit regressions verify the first event, uninterrupted old PRIMARY, witnessed reset, fresh requalification, 30-minute separation, imported prior records, legacy notification keys, and distinct ID behavior.

## Unchanged safety
- Existing BUY-only composite peak/extension/liquidity guards, +0.40% price extension, bid/ask spread checks, NSE market-time cutoff of 15:15 IST and broker budget checks.
- The −2% hard stop, +0.55% trailing arm, +0.50% protected gain and +0.10% ratchet, original broker SELL/GTT, cash-only CNC.
- The v8.0.4 broker-governed three-way REST lookahead and optional **read-only** Fixed Fast Watch.
- Multify and Forecast signal-selection logic, separate Univest learning-only status.

## Test limitations
This is an engineering TEST APK. Successful CI does not prove all trade paths, actual Groww fill protection, improved P&L, or reduced market-data latency. The stricter witnessed-reset rule may miss some legitimate fresh setups if a non-qualifying state was not sampled. Keep Auto BUY OFF until broker execution tests are completed.

## Installation caution
v8.0.x debug APK signing keys may change between GitHub Actions runs. Do not uninstall an app with open holdings, GTT orders or unexported learning data. Export the old ZIP and verify broker positions/GTTs before replacing it. An in-place update requires the same Android signer.
