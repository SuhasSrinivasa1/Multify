# v9.0.9 TEST — Account-wide API admission and Fixed diagnostics

This release addresses the 2026-11-17 v9.0.8 13:04 IST export. It is an
experimental transport reliability release, not a claim that Groww has a new
official per-UCC cap or that a particular trading strategy is profitable.

## Verified incident
The imported ZIP includes 718 APP-level HTTP attempts (Alpha 393 / Beta 325).
Beta LIVE_DATA had **304** traced requests and one HTTP 429 on
`/v1/live-data/quote` at approximately **12:58:31 IST**. The response's
start-of-request rolling category count was 49 (account-wide 50).
The single account 429 is insufficient to identify a universal endpoint,
account, subscription, IP, or multi-UCC limit; provider rates may be lower than
published aggregate ceilings. Alpha had no observed 429 in this export.
A successful Alpha-only full NSE LTP sweep took 82,536ms because the former
research LTP floor was 1,500ms per start. Two-account discovery had observed
52.7s, 74.9s and 96.6s sweeps, the latter during concurrent prefetch.
The exported recommendation SUVEN is a PRIMARY at ₹272.35, target ₹275.39.
No BUY order was made: the exported settings explicitly show Fixed Auto Buy OFF,
arm_effective=false. The 13:04 screenshot ₹271.30 implies approximately -0.386%
after the reference, not evidence of a realized position loss.

## API changes
1. **Alpha and Beta each use fixed-wall-clock minute AND rolling 60s AND
   rolling 1s capacity checks.** No near-minute-boundary burst is permitted.
   The calendar limit is an extra constraint, never an assumption that Groww
   resets its quota at HH:MM:00.
2. **Beta LIVE_DATA safe budget is 40/min** across its separately authenticated
   read-only research, rather than 180; this is a temporary conservative
   hypothesis to avoid re-triggering the observed 429 at ~49, NOT a claim about
   official Groww limits. Beta historical remains 8/min, 1/s.
3. **Alpha research /ltp floor is 1,000ms** (from 1,500); initial adaptive
   Alpha gap 1,050ms. Existing per-category 6/s, 180/min non-protective
   Alpha research cap and priority headroom remain, as do independent order and
   GTT protections. Alpha is still the exclusive broker trading account.
4. **Actual remaining capacity routes Fixed**: Beta roughly half of
   healthy NSE LTP batches and at most 2/5 detail quote symbols when safe,
   rather than a blanket 80% and subsequent cooldown. When Beta reaches its
   per-minute cap, healthy Alpha can carry more research within its own
   available budget. The same 2,633-symbol scan, 53 original batches and
   85% data-coverage threshold are unchanged. No duplicate sweep added.
5. **Prefetch yields to foreground Fixed** and active broker trade/protection;
   it only runs with spare account capacity. This is a deliberate trade-off:
   some cycles may forego prefetch rather than delay a current recommendation.
6. **No ungoverned client retries/redirects.** Explicit
   `retryOnConnectionFailure(false)`, `followRedirects(false)`, and a
   separate network interceptor that audits each actual HTTP wire exchange
   per account, category, endpoint, 60s rolling and fixed calendar minute.
   No auth/token, URL query, payload or credential is logged.
7. **Trace category repaired**: order/status/list/holdings calls are
   NON_TRADING; actual create/cancel/modify mutations are ORDERS.
   New exports: `wire_api_usage_snapshot.json`,
   `beta_api_gate_snapshot.json`, `beta_live_minute_status.json`.
   Persisted `API_WIRE ACTUAL_WIRE_ATTEMPT_V909` audit events allow
   historic minute reconstruction even after process restarts, though
   live in-memory governor windows themselves do not persist.

## What remains unchanged in live trading
- Exact v8.3.19 frozen Fixed decision, 40 factors, tiering, confirmation,
  target (1.1161043030113955%), filters, full-NSE discovery and publication.
- Original BUY preflight, alpha-only orders, duplicate/budget/anti-chase checks,
  GTT and stops. Hard stop -2%; stepped trailing +0.55/+0.50/+0.10.
- Forecast remains frozen; Beta research remains read-only.

## Indicator accuracy — safe research phase
SUVEN's logged 14 completed 15m candles cannot warm EMA50, ATR14, RSI14,
MACD(12/26/9) or Bollinger20 to their conventional intended history lengths.
The v8.3.19 strategy intentionally computes some values from shorter histories.
v9.0.9 now emits explicit read-only
`FIXED_SELECTION_QUALITY INDICATOR_WARMUP_SHADOW_V909` per evaluation.
It does **not** silently modify those values, impose a 50-bar hard gate
(which would suppress nearly all today's live signals), or change the
near-session-high caution into a hard rejection. Such a strategy change
requires *point-in-time multi-day data, positive AND negative outcomes,
walk-forward testing, and separate opt-in approval*. This fulfills the
diagnostic prerequisite without contradicting the user's original instruction
to freeze selection criteria.

## Acceptance, limitations and installation
Source-parity, backend replays and ALL Android unit tests must pass;
package must be signed/hashed in CI. Tests cannot prove live broker thresholds,
future scan times, recommendation target hits or profitable trailing ratchets.
Existing Groww GTT ownership conflicts remain a separate operational issue.
Do not uninstall an app managing live holdings/GTT metadata merely to install
a differently signed test APK. Verify signing fingerprint compatibility first.
