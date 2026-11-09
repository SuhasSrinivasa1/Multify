# Multify Trader Pro v8.3.9 — Fixed API Efficiency & Wallet-Free Order Sizing

## Why this release exists

The 9 November 2026 v8.3.8 diagnostic export recorded an Alpha LIVE_DATA HTTP 429 on `/v1/live-data/quote` with 45 requests in the preceding minute and 3 in the preceding second. Groww did not provide a Retry-After header. Multify then applied a 10-minute LIVE_DATA cooldown. The published Groww category ceiling is much higher, so the observed rejection cannot be explained by the published aggregate limit alone. A stricter endpoint/account/IP limit or traffic from another process using the same credentials remains possible.

The export also showed that the original `api_usage_snapshot.json` represented Alpha only. Separate Beta telemetry recorded 165 LIVE_DATA and 19 historical requests. The two accounts were being used, but their traffic was not presented together in the main usage snapshot.

## Changes

### 1. One shared Alpha live-data lane
- Alpha quote, LTP and OHLC requests now share one 1.2-second minimum gap and a 40-request rolling-minute ceiling.
- This closes the gap where LTP was paced but quote requests could burst concurrently.
- When app-owned holdings exist, research requests are capped at 28/minute and 12/minute remain available for protective holding LTP requests.
- Beta retains its read-only lane, 1.2-second live-data spacing and 35/minute live-data ceiling.

### 2. Better use of both accounts
- The market-wide LTP sweep remains split approximately 27 Alpha / 26 Beta batches for the 50-symbol Groww LTP batch size.
- 60% of already-selected Fixed detail reads are routed deterministically to Beta when its live and historical lanes are healthy; the remaining 40% use Alpha.
- Candidate generation, ranking, filtering, factor scoring and order eligibility are unchanged.
- Beta historical capacity is 14/minute to support the new read-only detail allocation.
- A Beta cooldown is checked before routing, avoiding duplicate quote work followed by a fallback burst on Alpha.
- Remaining Beta historical access in the Univest research path now passes through Beta's governor.

### 3. Adaptive cooldown instead of a fixed ten minutes
- First 429 without Retry-After: 60 seconds.
- Second distinct incident in the rolling hour: 3 minutes.
- Third: 10 minutes.
- Fourth and later: 30 minutes.
- A longer broker Retry-After is respected (bounded to six hours).
- Alpha and Beta use the same recovery policy.
- Fixed outcome monitoring skips locally blocked requests while the category is cooling down instead of repeatedly attempting requests that cannot reach Groww.

### 4. Remove all wallet/margin API reads
- No Groww `margins` endpoint calls remain in TradingRepository.
- Authentication, dashboard refresh, ARM preparation, Multify/Forecast buys, and Fixed manual/automatic buys no longer fetch wallet balance.
- Quantity is calculated from the locally configured per-order budget and the fresh execution price, retaining the existing 2% execution buffer.
- Groww is the final funds validator: an insufficient-funds order is submitted and rejected by the broker.
- The ₹30,000 setting remains a per-stock/per-order cap, not a daily limit. No daily cumulative cap or wallet reservation was added.
- Dashboard positions use the latest locally monitored price instead of issuing another quote request for every open position.

### 5. Reduce idle broker polling
- When there are no app-owned positions, no pending entry and both inventory/GTT snapshots are verified empty, the empty-portfolio recheck interval is now five minutes instead of 45 seconds.
- If an app-owned position or unresolved entry exists, the original active position/GTT monitoring path is unchanged.
- Per-BUY fresh inventory/active-GTT ownership checks remain in place.

### 6. Complete two-account telemetry
- Separate Alpha and Beta usage snapshots plus a combined snapshot now include totals, rolling-minute counts and 429 counts.
- Fixed scan and trailing monitor profiles report both accounts and combined request deltas.
- Beta 429 logs include endpoint path, method and Retry-After header.

## Explicitly unchanged
- Fixed 90-second scan schedule.
- Full NSE universe discovery and 50-symbol LTP batching.
- Fixed candidate selection, 40-factor scoring, candle inputs, ranking, filtering, eligibility and target logic.
- Trailing-stop thresholds, GTT creation/update/reconciliation, SELL priority and -5% hard stop.
- Broker order recovery and actual filled-quantity accounting from v8.3.8.

## Verification required
- Android unit tests and backend tests.
- Strict byte-identity checks for the frozen Fixed selection/scoring/filtering and protective SELL/GTT policies.
- APK compilation, package version and signature verification.
- Live-session verification of actual 90-second scan cadence, Alpha/Beta rolling usage and trailing GTT behaviour.

This release reduces avoidable traffic and removes wallet polling; it cannot guarantee Groww will never return 429 if the same token/IP is also used by another application or if Groww applies an undocumented lower limit.
