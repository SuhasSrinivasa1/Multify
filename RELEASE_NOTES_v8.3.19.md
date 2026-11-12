# Multify Trader Pro v8.3.19 — API Throttle Reliability

Base: v8.3.18 (source commit d387c3396dfc5fb0a0cb4efeac66d943b0365ba7).

## Evidence from the November 12 v8.3.18 export

- 1,244 Alpha requests and 356 Beta requests were recorded.
- Alpha received 11 HTTP 429 responses: seven on /live-data/quote, one on /live-data/ltp, one on holdings, one on positions, and one on a GTT status endpoint.
- Beta recorded zero HTTP 429 responses.
- Up to eight Alpha quote requests started in one second immediately before quote 429 bursts.
- 61 Fixed scan attempts were deferred while the category cooldown was active.
- Historical requests recorded no HTTP 429, but the one-request-per-second client gate accumulated 271.7 seconds of waiting across 136 waits.
- The logs also showed four completed scans taking about 52 seconds, two around 27–30 seconds, and two around 87–89 seconds.

The request trace does not prove Groww's hidden endpoint quota. The implementation therefore smooths observed bursts and remains below published category ceilings rather than assuming a precise undocumented limit.

## Changes in v8.3.19

1. Alpha LIVE_DATA application ceiling is reduced from 10 requests/second and 300/minute to 4/second and 120/minute. The existing protection reserve remains 2/second and 60/minute, leaving a research allowance of 2/second and 60/minute. Protection traffic can use the full application allowance and still passes the shared recovery gate.
2. Added adaptive Alpha /live-data/quote pacing. Research quote starts are separated by an initial 650 ms; a quote 429 doubles the gap up to 8 seconds, and clean traffic gradually relaxes it. Protection-tagged requests skip this extra research delay but still consume the reserved/global live-data budget.
3. Historical request admission increases cautiously from 1/second and 45/minute to 2/second and 90/minute. This remains below the configured published ceiling of 4/second and 180/minute and addresses the large historical wait time observed with zero historical 429s in this export.
4. Added unit tests for quote pacing and updated live/historical budget tests.

## Explicitly unchanged

- Fixed scoring, 40-factor framework, frozen FIXED-V741 snapshot, recommendation eligibility, market discovery and publication logic.
- 1.1161043030113955% target.
- +0.55% trailing activation, +0.50% initial protected gain, +0.10% trailing step, and -5% hard stop.
- Alpha remains the only execution account. Beta remains read-only; no orders are routed through Beta.
- Category-wide 429 cooldowns and server Retry-After are still respected. No retry/cooldown bypass was added.
- Holdings, positions, GTT status, SELL protection and order reconciliation code is unchanged.
- Diagnostic export and v8.3.18 read-only research instrumentation are retained.

## Expected result and validation

The quote burst governor should reduce simultaneous Alpha quote dispatches. The historical gate should reduce waiting for historical reads. Together, these are intended to reduce repeated cooldowns and help complete scans sooner. Zero 429s or a specific scan duration cannot be guaranteed because the broker's endpoint-specific limits are not disclosed and can change.

The new build must pass the frozen Fixed/trailing source checks, backend tests, Android unit tests, APK build, package version check and APK signature verification. Live Groww behaviour must still be observed after installation.

Version code: 8400
Version name: 8.3.19-api-throttle-reliability
