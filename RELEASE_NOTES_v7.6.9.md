# Multify Trader Pro v7.6.9 — Hot Data Forecast Uplift

## Fixed remains frozen
The accepted v7.4.1 Fixed model, feature formulas, weights, thresholds, ranking, publication, target calibration and locked coordinator region remain byte-for-byte protected by CI. Fixed still scans every 2.5 minutes.

## Speed layer
- Adds a bounded RAM-only Groww hot-data cache ahead of the REST governor.
- 15-minute historical responses may be reused for up to 22 seconds within the same 15-minute candle bucket; crossing a bucket forces a fresh fetch.
- LTP cache is 1 second and final Quote cache is only 350 ms.
- Prefetches the previous Fixed scan's detailed symbols 13–18 seconds before the next Fixed scan.
- S24-class devices with 8+ processors use eight independent Fixed evaluation coroutines. Network misses remain governed globally.
- Removes redundant Fixed discovery/detail pacing; the process-wide REST governor remains authoritative.
- Adds per-scan timing/network/cache telemetry.

## Groww API headroom
- Adds a process-wide local ledger for Auth, Orders, Live Data, Non-Trading and Historical requests.
- Audits rolling 1-second/60-second use and remaining app-budget headroom every 30 seconds.
- Foreground notification shows Live/Historical/Order minute usage.
- Honors Retry-After seconds on HTTP 429 when Groww supplies it, while retaining the minimum cooldown.
- No extra Groww request is made just to query quota because Groww does not document a remaining-quota endpoint.

## Faster order confirmation
Market orders are still submitted once with deterministic reference recovery. If the initial response is not already EXECUTED, fill polling starts at 125 ms and backs off adaptively instead of waiting 450 ms before every status read.

## Forecast improvement
- Positive Fixed history remains one-way into Forecast only.
- Forecast may expand an individual target by at most 0.75 percentage points when matching positive Fixed outcomes show additional upside.
- The expanded target is capped by the current candidate's volatility capacity and is accepted only when it preserves the same existing quality/publication/execution gate result.
- Position-size/capital risk is not increased.
