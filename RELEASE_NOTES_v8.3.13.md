# Multify v8.3.13 — Unified Fixed API readiness (TEST candidate)

Base: exact v8.3.11 commit b652a57a183346c6929389d4bccc473ca503c385.
This release intentionally DOES NOT inherit v8.3.12's separate anti-chase / late-entry publication changes.

## Changes
1. Alpha and Beta read-only LIVE/HIST requests pass one additional aggregate, process-wide admission gate. Original per-account and per-endpoint limits remain, as do Alpha-only ORDERS, authentication and non-trading protections.
2. Any Alpha or Beta LIVE/HIST HTTP 429 activates a shared market-data cooldown. Permits are reduced conservatively, Retry-After is honoured where available, and a single half-open probe restores traffic gradually. Market scans fail fast on long cooldown instead of occupying an entire scan interval.
3. Stop Beta-to-Alpha fallback for quota-related failures, avoiding retry storms. Other Beta failures retain controlled Alpha fallback.
4. Fixed Auto Buy checks LIVE_DATA before requesting the execution quote, then NON_TRADING and ORDERS before placing the order; a later unrelated LIVE_DATA cooldown does not retroactively invalidate a previously obtained fresh quote.
5. Preserve broker-owned-symbol checks, live quote freshness, spread <=40bps, eligibility, target and extension protections, per-order budget, one-order deduplication, immediate GTT attempt and existing recovery handling.
6. Display the shared Alpha/Beta LIVE scheduler state separately from Fixed's persisted NSE price coverage percentage.
7. Frozen scoring/ranking/PRIMARY confirmation/candle interval/targets, reference selection, sell/trailing and GTT policies are source locked in CI.

## Known limitations / unimplemented proposals
- This change does not promise 100% NSE price coverage, increased recommendation count, or a speedup in every session. The instrument master can contain symbols without valid LTP on a given day. Source-rate limits and broker-side throttling still apply.
- The scanner still restarts an unsuccessful wide sweep on the next valid scan; it does not implement persistent, cross-scan partial market-price caching, because publishing mixed-age prices would compromise Fixed inputs. Successful prefetch sweeps and existing incremental candle caching remain.
- Exact production trading efficacy and endpoint/account shared-quota semantics are not verified outside live sessions.
- The Mac's historical 100% claim has no comparable timestamped source here. Backend Python SDK streaming module is research-only and capped at 1000 instruments per subscription.
- The CI APK is debug-signed unless the matching stable signing secret is present and verified. Do NOT uninstall an existing install managing holdings/GTTs or unexported diagnostics. Obtain a matching signature before in-place upgrade.

## Required acceptance
- Compare median and p90 discovery + detail time against v8.3.11 under comparable market sessions, including rate limit and valid symbol counts.
- Verify no duplicate order, no pre-activation order, no bypass of spread/ownership/protection, no Beta trading.
- Verify 429 restores shared market-data transport without unintended orders or repeated Alpha fallback.
- Confirm broker GTT after each live fill before considering protection complete.
