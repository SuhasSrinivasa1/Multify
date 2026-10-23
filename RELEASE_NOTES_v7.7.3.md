# Multify Trader Pro v7.7.3 — Fixed Delivery Latency

## Purpose
Reduce presentation latency after a Fixed recommendation while preserving the frozen Fixed decision engine, market-data freshness semantics, confirmation timing, 48-stock selection, auto-buy safety and trailing architecture.

## Fixed remains unchanged
- All files in FIXED_FINAL_LOCK.gitsha remain byte-identical.
- The protected TradingRepository Fixed coordinator region remains byte-identical.
- Fixed interval remains 150 seconds.
- Candidate construction/order and 48-stock cap remain unchanged.
- Primary confirmation rules remain unchanged.
- All 40 Fixed factors/families, ranking, quality, target and publication rules remain unchanged.
- Shared stepped trailing remains 0.55% arm -> 0.50% protected gain -> +0.10% ratchet.

## Market-data/input semantics deliberately unchanged
v7.7.3 does NOT modify:
- hot-cache TTLs or 15-minute candle-boundary behavior;
- Groww API request definitions;
- OkHttp interceptor ordering;
- app API budgets;
- Fixed evaluation concurrency.

These v7.7.2 input-semantic files are pinned by FIXED_INPUT_SEMANTICS_V772.gitsha and verified in CI.

No production concurrency increase or cache-freshness change is included because a recorded-market-input equivalence fixture is not present in the source repository.

## BUY first, presentation second
The Fixed priority cycle now exposes a detailed delivery result while preserving this sequence:

1. run the unchanged Fixed scan;
2. persist eligible Fixed recommendations inside the unchanged frozen coordinator;
3. run Fixed auto-buy exactly as before;
4. only after auto-buy processing returns, emit the presentation delivery event;
5. refresh an open UI immediately and send the Android Fixed notification asynchronously.

Notification work is therefore never inserted between recommendation persistence and Fixed auto-buy.

## Immediate UI delivery
The ViewModel listens for the post-auto-buy Fixed delivery event and refreshes the dashboard from local state immediately. The existing 15-second UI refresh loop remains as a fallback.

## Fixed Android recommendation alert
A dedicated high-importance "Fixed recommendations" channel is added.
Alerts are sent only after the Fixed auto-buy attempt completes.

## Critical-path telemetry
New asynchronous telemetry:
- FIXED_CRITICAL_PATH_PROFILE
  - decision elapsed ms
  - decision-complete to auto-buy-start microseconds
  - auto-buy elapsed ms
  - cycle elapsed ms
  - new recommendation count
  - auto-buy submitted count
- FIXED_AUTO_BUY_PROFILE
  - recommendation-to-row-start ms
  - fresh quote ms
  - fresh margin ms
  - pre-order guard ms
  - order-to-fill ms
  - row total ms
- FIXED_DELIVERY_NOTIFICATION
  - recommendation generated-to-notification ms
  - post-auto-buy delivery confirmation

The successful ENTRY_MARKET_FILLED audit record is now queued asynchronously after the position is persisted, avoiding synchronous audit-file work between one completed Fixed order and the next order's pacing interval.

## Safety retained
- Fresh quote before Fixed sizing remains.
- Fresh Groww margin check remains.
- Existing deterministic order reference remains.
- Existing direct CNC MARKET BUY remains.
- Existing position persistence remains before presentation.
- Existing 175ms Fixed order pacing remains.
- SELL protection, Multify priority and API governor remain unchanged.
