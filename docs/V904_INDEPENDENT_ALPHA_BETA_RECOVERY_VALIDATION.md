# Multify Trader Pro v9.0.4 — Fixed independent-account recovery (TEST)

## Source of this release
User diagnostic ZIP: `MultifyTraderPro-logs-v9.0.3-fixed-60pct-account-quota-test-debug-20261116-120912.zip`.
Its 514 system-audit events were inspected directly. 16 November 2026 IST:
- 12:00:03 official Alpha-only 53/53 LTP batches and 2,436 / 2,646 symbols (92.1%) succeeded, but it was only the *first* baseline sample. The next Alpha quote HTTP 429 at 12:00:13 stopped detailed evaluation at 8/48.
- 12:01:53 Alpha LTP 429, no Retry-After, 5-minute locally imposed cooldown. The 12:02:21 sweep had Alpha 6/27 successes, Beta 26/26, 55.52% coverage.
- 12:07:06 Alpha LTP 429, no Retry-After, 15-minute locally imposed cooldown. The 12:07:34 sweep had Alpha 5/27 successes, Beta 26/26, 53.82% coverage. This is a **last completed request sweep result**, not a progress meter.
- All four HTTP 429s in this ZIP were Alpha (three LIVE_DATA, one NON_TRADING); none were Beta, none showed observed per-category rates near Groww's public LIVE_DATA ceilings.
- No official Fixed recommendation was produced in this export because no valid two-sweep baseline was re-established.

## Immutable decision constraints
Keep the byte-identical v8.3.19 Fixed `generateFixedForecasts` and `discoverFixedMarketWideCandidates` source, plus the original 40-factor, 15-minute candle, 28 discovery candidates, 48 detailed candidates, price band, primary/secondary quality, two-scan confirmation, target, stop and publication code. Preserve exact read-only v8.3.16 shadow. Nothing here improves a signal by weakening the model.

## Corrections
1. Account-aware read-only readiness: a genuinely healthy independently authenticated Beta (LIVE and HISTORICAL) can continue Fixed market research while Alpha LIVE_DATA or HISTORICAL is cooling down. No Alpha BUY, SELL, GTT or broker protections are rerouted or bypassed.
2. Discovery: each of the original 53 symbol batches is assigned once per sweep. During an Alpha cooldown, its remaining read-only batches route to Beta; a *failed* Alpha batch may receive **at most one** Beta recovery attempt after the Alpha request failed. Recovery does not cause concurrent duplicate requests for a successful batch. Beta's atomic 6/s, 180/rolling 60s LIVE_DATA admission, adaptive endpoint gap and independent cooldown remain authoritative. A Beta 429 stops that sweep; no unbounded fallback to Alpha. Incomplete (<85%) or stale sweep cannot publish.
3. Detailed evaluation: when Alpha is known cooling down, use Beta quote + 15m historical data if Beta's separate history gate permits. On an Alpha 429 arriving during detail, permit at most one Beta retry. If Beta historical capacity is exhausted or the transport fails, flag the incomplete scan and cause the original frozen Fixed loop to defer rather than publish a partial result. No change to 48 candidate ordering, factor scoring or publication.
4. Prefetch: Beta-only market sweeps get a conservative 76s planning lead (as distinct from 50s dual and 82s Alpha-only); existing 45s freshness, 85% coverage, once-per-due-slot ownership and order-first safety remain.
5. Fixed UI: show actual dispatched Alpha/Beta LIVE_DATA 60s counters; correct Alpha Fixed cap to 180/min with 120/min protected reserve; show Beta 180/min; explain the last sweep as a coverage **result**, not warming progress; distinguish DEGRADED, WARMING, ALPHA cooldown pause, and BETA read-only recovery.
6. Beta status display decays cached local cooldown time and discards snapshots idle over 60 seconds; authoritative actual dispatched counts continue to come from the independent HTTP trace ledger.

## Hard limits and known operational constraints
- This is a research transport recovery, *not* a fix for Groww's unexplained actual HTTP 429 responses. Request rates can be far below published limits yet still receive 429.
- While Alpha is completely blocked, Beta can produce a complete 53-batch **discovery** sweep, but Beta HISTORICAL remains at 1/s and 8/min. Forty-eight independent detailed historical requests cannot fit in a 90-second interval at 8/min. We must not claim a complete 48-stock <=90s **Beta-only** evaluation or fabricate prior-session data. This build fails closed instead of publishing from quota-starved details.
- Auto BUY is to remain OFF until a fully supervised live session validates both accounts and SELL/GTT protection; no APK alone can establish a recommendation count or market performance.
- Debug signing certificates can differ between CI runs; never uninstall an app with live holdings/GTT or unexported state to force an update.
- The 60% Fixed live-data limit is an app-local cap, not a Groww quota guarantee and not a system-wide quota across other devices or credentials.
- Do not increase Beta historical traffic above its conservative governor without verified broker evidence.

## CI release gates
- Frozen v8.3.19 LIVE source parity against both historical v19 references and unchanged v8.3.16 research clone.
- Original trading service code byte-identical after stripping precisely marked v9.0.2 prefetch and v9.0.4 read-only-gate extensions.
- Source-scope allowlist, original -2% hard stop and trailing/GTT invariants.
- Unit tests for 53-batch routing, Beta recovery, separate readiness, incomplete-sweep display, two-sweep confirmation, Beta-only prefetch.
- Full Android unit suite and debug APK build. Passing these is not live-market proof.

## Required live acceptance test
During a supervised NSE session, record:
- 53 original discovery slots, >=85% valid LTP coverage, both baseline samples valid, no stale snapshot consumption.
- 6/s 180/min FIXED live-data research upper limits on each independently governed account; source-specific actual 429 vs local governor waits.
- At least one deliberate/staged Alpha 429/cooldown scenario: Beta-only read-only 53-batch recovery, no new Alpha research calls, Beta quota never breached, no incomplete recommendation/Auto BUY.
- Completed 48-candidate eval cycle with Alpha healthy; median under 60s and p90 under 90s **if observed**, not assumed.
- PRIMARY confirmation, publication and trade-safety invariants; any missed BUY/SELL/GTT protection is release-blocking.
- Reconcile matched recommendation/cohort quality with v8.3.19; no guarantee of identical live counts from nonidentical market snapshots.
