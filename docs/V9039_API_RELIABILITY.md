# Multify Trader Pro v9.0.39 — API reliability TEST

Transport/reliability update based on the v9.0.38 TEST branch. No production trading strategy changes.

## Changes
- **Beta-only adaptive recovery:** 1,300 ms start, 950 ms floor, 8,000 ms ceiling; each clean 60-second window reduces the gap by 20%; a 429 doubles it. Alpha pacing is unchanged.
- **Restart-persistent Beta governor:** persists cooldown deadlines, recent 429 incident history, adaptive gap, half-open probe state and recent admissions. Restores rolling 1-second, rolling 60-second and calendar-minute usage. Admission is committed before dispatch.
- **Priority-aware Beta live queue:** full-market Fixed discovery LTP batches outrank detail quote/research; equal-priority requests are FIFO; active reads are never preempted; cancelled waiters are removed.
- **Idle inventory heartbeat:** verified-empty portfolio polling changes from 45 to 180 seconds. If external/manual holdings or GTTs are present, the previous 45-second cadence remains for quarantine/reconciliation. The slower interval requires recent empty inventory and GTT snapshots, no app-owned open positions, no pending entries and no critical trade. BUY preflight still forces fresh Alpha inventory; app-owned SELL/GTT safety is unchanged.

## Retained protections
- Beta stays read-only; the existing interceptor continues to deny order, holdings, positions and GTT endpoints.
- Alpha remains the exclusive execution/protection account.
- Alpha GrowwRateLimitInterceptor and its budgets/recovery are unchanged.
- FixedHistoricalWindowStore still reuses only a verified historical prefix; every evaluation requests fresh broker candles and validates timestamp overlap.
- GrowwHotDataCacheInterceptor still coalesces identical research GETs; execution/protection quotes bypass cache.
- Fixed 40-factor scoring, 15-minute inputs, confirmation, targets, BUY/SELL/GTT and trailing thresholds remain unchanged. Forecast remains frozen.

## Capacity limitation
Beta's 6/s and 40/minute local caps are unchanged. This update improves queue fairness and post-429 recovery; it does not promise a 72-batch Beta-only fallback sweep inside the 90-second scan target. We retain the conservative cap rather than infer a higher hidden Groww allowance from a single session.

## Validation
CI runs frozen-strategy and broker-safety checks, backend tests, Android unit tests and a side-by-side TEST APK build with application ID suffix `.v9039test`. Passing CI validates code/tests, not live Groww quota behavior. The next session export must compare queue waits, governor waits, pacing, cooldown restoration, sweep coverage, 429s and protection health.

**Do not install a differently signed package over the live app or uninstall it while holdings, pending orders, GTTs or unexported state exist.**
