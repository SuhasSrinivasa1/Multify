# Multify Trader Pro v7.7.3 — Order-First Fixed Notification

## Frozen invariants preserved
- Fixed contract remains `FIXED-V741-20261021-190239-FINAL`.
- Fixed 150-second cadence is unchanged.
- Primary confirmation policy is unchanged.
- Candidate ordering and 48-stock detailed universe selection are unchanged.
- The frozen 40-factor/family model, target, quality, ranking and publication behavior are unchanged.
- All locked Fixed source files remain byte-identical.
- The protected Fixed coordinator region in `TradingRepository.kt` remains byte-identical.
- `SteppedTrailingPolicy.kt` remains byte-identical: +0.55% arm, +0.50% initial protection, +0.10% ratchet.
- Fresh quote and fresh CNC margin checks before Fixed auto-buy remain unchanged.
- Existing API governor, sell reserve, cache TTLs and candle-boundary behavior remain unchanged.
- Fixed evaluation concurrency remains unchanged.

## Order-first Fixed notification
A production Fixed scan already persists eligible recommendations and runs Fixed auto-buy in the same priority cycle.
v7.7.3 adds a dedicated HIGH-importance Android Fixed recommendation notification only after `runFixedPriorityCycle()` returns.

Order is therefore:
1. Frozen Fixed scan and eligibility
2. Recommendation persisted
3. Eligible Fixed auto-buy path processed first
4. Android Fixed notification posted immediately afterward

This removes dependence on the dashboard's normal refresh cadence for user visibility without putting UI/presentation work in front of broker execution.

The repository also emits a nonblocking post-auto-buy delivery event. If the app UI is open, the ViewModel refreshes the dashboard immediately from local state; the existing 15-second polling loop remains only as a fallback.

Notifications are deduplicated per trading day and symbol/generated timestamp across service restarts, and the notification task itself is launched asynchronously after the Fixed priority cycle returns.

## New low-overhead timing telemetry
No new disk/JSON work is placed on the critical path; timing records use the async audit writer. The successful Fixed ENTRY_MARKET_FILLED audit record is also queued asynchronously after the managed position has been persisted, reducing post-fill blocking before the next order pacing interval.

New events:
- `FIXED_PRIORITY_PHASE_PROFILE`
  - recommendation-stage elapsed
  - auto-buy-stage elapsed
  - submitted count
  - total priority-cycle elapsed
- `ENTRY_LATENCY_PROFILE`
  - recommendation age when auto-buy begins
  - fresh quote latency
  - fresh margin latency
  - order-to-fill latency
  - per-symbol total latency
- `FIXED_NOTIFICATION_POSTED`
  - recommendation-to-notification latency
  - confirms notification was posted only after the Fixed priority/auto-buy cycle

## Deliberately NOT changed
The following remain investigation/test-only because changing them can affect snapshot equivalence:
- cache TTL/freshness rules
- historical 15-minute bucket behavior
- LTP/quote cache freshness
- API concurrency
- Fixed 150-second cadence

No recorded-market equivalence corpus currently exists in the repository, so none of those experiments were promoted into production v7.7.3.
