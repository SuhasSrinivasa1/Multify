# v9.0.40 TEST — Beta full-detail capacity guard

## Evidence source and scope

Reviewed the user-provided `Phayra-v9036-eod-largecap-diagnostics (3).zip` in full. Its internal manifest identifies the running build as `9.0.39-api-reliability-TEST-debug`; the filename is not treated as the running version.

The export contains five completed Fixed scan profiles:

| Scan | Duration | Alpha live requests | Alpha historical requests |
|---|---:|---:|---:|
| 1 | 117.5 sec | 93 | 24 |
| 2 | 136.7 sec | 54 | 21 |
| 3 | 155.1 sec | 95 | 24 |
| 4 | 151.2 sec | 50 | 18 |
| 5 | 160.0 sec | 0 | 32 |

All five exceeded the 90-second target. Completed scan start gaps were approximately 209s, 138s, 429s and 847s. The export also contains 47 full-NSE sweep profiles: 12 completed and 35 aborted. This is one session, not a multi-day performance or profitability validation.

### Root cause confirmed in scan 5

During the fifth completed Fixed scan, Beta supplied:

- 70 full-market LTP requests;
- 47 individual detailed quote requests;
- 117 total Beta LIVE_DATA requests during a 160-second scan.

The Beta governor is deliberately configured at 40 LIVE_DATA requests per rolling/fixed minute, with adaptive request spacing. The route therefore sent discovery and the complete 48-candidate detail workload to the same constrained Beta lane while Alpha LIVE_DATA was cooling down.

The caller already checked whether Beta had a small amount of detail headroom, but the fallback branch in `FixedCategoryAccountRoutingPolicy.choose` still selected Beta whenever Alpha LIVE_DATA was unavailable and Beta had any capacity. That allowed the detail loop to queue across later quota windows. This is a transport-routing defect, not a Fixed scoring or confirmation defect.

### Actual broker rejections — do not infer hidden limits

The wire log contains seven actual HTTP 429 responses: five Alpha and two Beta. All were LIVE_DATA LTP/quote requests. None carried a `Retry-After` value.

| IST | Account | Endpoint | Same-account LIVE_DATA requests in preceding 60s | Multify cooldown |
|---|---|---|---:|---:|
| 11:30:08 | Alpha | LTP | 36 | 90 sec |
| 11:35:03 | Beta | LTP | 15 | 90 sec |
| 11:36:23 | Alpha | LTP | 56 | 5 min |
| 11:45:02 | Alpha | LTP | 11 | 15 min |
| 11:46:58 | Beta | LTP | 8 | 5 min |
| 12:02:04 | Alpha | LTP | 6 | 60 min |
| 13:08:05 | Alpha | Quote | 14 | 90 sec |

Counts are reconstructed from actual wire-start timestamps, not logical/suppressed attempts. They are below Groww's published Live Data ceiling, but do not prove a hidden per-endpoint, per-UCC, IP or concurrency limit. External traffic and undocumented provider behaviour remain possible. Do not raise local request budgets based on these logs.

Groww's published documentation currently states that Live Data is a shared category across Quote, LTP and OHLC at 10 requests/second and 300/minute; LTP accepts up to 50 instruments per request. These are published ceilings, not a guarantee that every account/session will be accepted at those rates.

### Beta wait interpretation

There were 2,006 positive Beta governor-wait events, with approximately 67.8 minutes of accumulated wait:

- priority-queue wait: 53.1 minutes;
- pacing/governor wait: 14.7 minutes.

These are sums across concurrent requests, not elapsed wall-clock scan time. The measured LTP/quote wire response durations were generally below the existing 950ms minimum inter-admission gap. Raising concurrency would not raise the 40/min admission ceiling and could increase simultaneous in-flight requests while the cause of the low-volume 429s remains unknown. Therefore this increment deliberately does **not** change Beta concurrency, request rate, adaptive pacing or cooldown policy.

## v9.0.40 change

Add a read-only transport precondition for the frozen Fixed detailed-evaluation cycle:

1. Fixed's detailed universe remains 48 candidates.
2. Beta may be selected as the sole LIVE quote provider during an Alpha LIVE_DATA cooldown only if all 48 detail quotes plus the existing 3-request headroom reserve fit in BOTH current rolling-60s and fixed-calendar-minute headroom.
3. With Beta's unchanged 40/min local ceiling, that full-cycle precondition cannot be met. Beta can still perform read-only full-market LTP discovery, but it will not also start a 48-quote detail cycle that must spill into later quota windows.
4. If Alpha LIVE_DATA is unavailable and Beta cannot satisfy the full-cycle precondition, the existing incomplete-transport path is used. The Fixed scan is marked incomplete, and partial publication is blocked.
5. When Alpha LIVE_DATA is healthy, existing deterministic Beta detail routing and capacity checks remain unchanged. Alpha remains the only account permitted to execute BUY/SELL and create/modify/verify protective GTTs. Beta remains read-only.

## Explicitly unchanged

- Frozen Fixed 40-factor scoring, 15-minute production inputs, candidate universe/order, PRIMARY/SECONDARY confirmation, ranking, publication criteria and target.
- Fixed BUY/SELL/GTT thresholds and protection logic.
- Beta 6/s and 40/min LIVE_DATA local governor, 950ms minimum gap, historical 1/s and 8/min caps, adaptive pacing and cooldowns.
- Alpha/Beta authentication separation and independent account governors.
- Full-NSE discovery universe, 50-symbol LTP batch size and coverage threshold.
- Forecast remains frozen; no new strategy or timeframe is promoted.

## Validation required before any live use

CI must pass the new policy tests, frozen Fixed/source-parity checks, full Android unit suite, backend tests and TEST APK identity/signature checks.

On the next supervised NSE session, compare against the v9.0.39 export:

- completed Fixed scan duration p50/p90 and actual start spacing;
- full-NSE sweep completion, duration, 50-symbol batch coverage and abort reasons;
- Alpha/Beta LIVE_DATA requests, 1s/60s counts, wire latency and all 429s;
- Beta detail quotes during Alpha LIVE_DATA cooldown (expected: zero unless the full-cycle headroom guard passes);
- `BETA_LIVE_CAPACITY_BELOW_FULL_DETAIL_CYCLE` and `DETAIL_CATEGORY_NOT_AVAILABLE` counts;
- incomplete scan status and proof that no partial Fixed publication occurs;
- Alpha-only order/GTT activity and Beta read-only enforcement.

This is a transport-safety and capacity correction, not proof of a 90-second scan or improved trading profitability. Keep Auto BUY disabled during validation and do not install a TEST package over a live package without verifying signer compatibility and broker-side holdings/GTT state.
