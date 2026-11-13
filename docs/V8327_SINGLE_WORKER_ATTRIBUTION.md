# v8.3.27 TEST — one Fixed worker, no duplicate scan attribution

## Why
The 2026-11-13 v8.3.26 12:47 IST log export shows two
RUNTIME/FIXED_SCAN_WORKER_STARTED events 1 ms apart and overlapping Fixed
scans. At 12:23 and 12:24 the same recommendation IDs were attributed to
both a 92s and a 184s scan. Groww 429 responses and long cooldowns
occurred later in the session. This is evidence of a duplicate-worker race
and biased latency cohorts, NOT proof that fixing it alone removes 429s.

## Production changes — scheduling only
- FixedSingleWorkerStartGate uses synchronized admission and a LAZY
  coroutine. The running job is registered and started under one lock.
  Service startup and watchdog share the same gate.
  Repeated starts return without launching a second Fixed worker.
  On teardown, the registered worker is cancelled.
- Official Fixed retains its 90s cadence, 53-batch full market discovery,
  prefetch arbitration, 28 discovery slots, 48 detail slots and existing
  Alpha/Beta request governor, 429 cooldowns and broker budgets.
  No extra broker request, stricter throttling or extra market worker.
  Preventing duplicate work reduces avoidable request competition.
  Real broker API speed improvements require future matched logs.
- The per-process scan counter is appended to the scan timestamp so that
  scans started in the same millisecond have distinct IDs.

## Scan attribution fix
- Capture all published recommendation IDs from the local Fixed store
  immediately before the scan and immediately after a successful scan.
- The newly-published ID set difference is the only source of scan
  recommendation attribution; overlapping wall-clock windows cannot
  re-count old recommendations.
- FIXED_LATENCY_SHADOW / SCAN_COHORT_V8327 logs those new IDs, scan
  duration bucket, known client quote-to-ready delay and aggregate
  permit wait. Client capture is not exchange timestamp.
  Summed concurrent permit waiting is not wall-clock waiting.
- Request-count windows can include non-Fixed account traffic and are
  labeled as such. No model/trade decision consumes any audit field.

## Frozen trading invariants
- Original Fixed stock selection, ranking, 40-factor formula, 15m
  completed production candles, PRIMARY repeat confirmation, stock
  eligibility, BUY/SELL/GTT are unchanged.
- Fixed production method blocks and nine immutable strategy policies
  are checked against BOTH v8.3.19 branches.
- V8.3.26 real 5-minute research remains unmodified, including guarded
  postmarket 15:35–18:00 IST collection and forward candle outcomes.
- No slow scan publication veto. No extra filtering.

## Test/acceptance plan
- JVM concurrency test: 24 simultaneous starts create exactly one job;
  a job can be replaced only after cancellation.
- Attribution regression: recommendations from the first cycle are not
  re-counted in subsequent cycles with overlapping wall-clock intervals.
- Source parity and Android CI must pass before APK is shared.
- In live logs verify one Fixed worker launch per service instance,
  unique IDs, unique recommendation attribution, 429s, median/p90
  scan durations, completed coverage and notification delays versus
  matched v8.3.26 conditions. No speed or profit gain is claimed
  without live evidence. Keep Auto Buy OFF during test.
