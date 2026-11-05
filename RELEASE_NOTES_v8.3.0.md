# Multify Trader Pro v8.3.0 — FIXED Alpha + Beta Dual-Data (TEST)

## Scope
Major Fixed-first network architecture upgrade, based on verified v8.2.0 and v8.0.7 broker GTT recovery. This is NOT a new scoring formula.

## Forecast hard freeze
- Forecast scanning worker and Forecast news worker are not started. The watchdog does not restart either.
- The legacy Forecast 15-minute WorkManager news job is cancelled on startup, and an already queued job is now a no-op.
- After-market Forecast replay and outcome monitoring are not scheduled.
- Optional Multify post-notification feature research is paused; real Multify BUY/GTT processing is retained.
- Forecast Auto Buy and Forecast scanning toggles are forced OFF on app start, blocked from being reenabled, and displayed as FROZEN. Manual Forecast generation and Forecast execution buttons are disabled. Existing research history is preserved.
- Alpha-managed legacy holdings are still monitored by the independent broker position/GTT worker.

## Dual-account Fixed data
- **Alpha is the ONLY order/position/protection account:** CNC MARKET BUY, SELL, GTT creation/modification/cancellation, position reconciliation, margins, broker inventory and safety gates remain on Alpha.
- **Beta is READ ONLY:** its Retrofit client has a dedicated `BetaReadOnlyInterceptor` which permits market-data GET and authentication/profile calls but rejects all order/portfolio/margin endpoints.
- Beta must already be manually authenticated as a separate Groww UCC; invalid/expired/same-account Beta credentials lead to Alpha-only Fixed operation. There is no unattended Beta authentication and no order fallback to Beta.
- The same full NSE instrument list is chunked into 50-symbol requests. Deterministic odd-numbered LTP batches route to Beta (26 of 53) when healthy; even batches use Alpha. Batch order, universe, minimum 85% coverage, persistence, original breadth selection and 150-second Fixed cadence are retained.
- Each symbol's detailed 15-minute evaluation uses Beta only for a deterministic 1-in-6 allocation when Beta is healthy; otherwise Alpha. The full 15m quote/history evaluation uses one account per attempt and a token-scoped historical cache. Beta errors trigger a fresh Alpha-only evaluation, not mixed partial 15m market data.
- Beta has conservative live-data admission of 1 request/1.2s and at most 35/minute, historical 1/sec and at most 8/minute; rate-limit cooldowns are independent from Alpha's protected trading limits.
- A Beta budget/cooldown wait exceeding 10 seconds fails quickly back to Alpha. Beta 401 invalidates only Beta's token; Beta 429 does not bypass Alpha's governor.
- Existing v8.2 prefetch continues to prepare the **next** full NSE sweep using both read-only lanes, accepts only complete/recent snapshots and falls back to the original full scan when invalid. Account/token scope is included in the staged snapshot key.

## Invariants
- The original v7.4.1 Fixed 40-factor scoring, universe, 15-minute features, independent PRIMARY confirmation, 1.1161043% target and published recommendation thresholds are unchanged.
- v8.0.6 same-stock reset/requalification event logic remains; no daily symbol cap introduced.
- v8.0.7 broker protection recovery, hard stop, stepped trailing GTT, duplicate-order protection and stop/SELL calculations remain intact.
- Early Watch, Market Pulse, Fast Watch and candidate-only research API polling remain DISABLED.
- Order execution uses a separate fresh **Alpha** quote and Alpha-only CNC MARKET order. Historical Fixed displayed signal entry is not a guaranteed execution price.

## Measurable tests / limitations
- CI verifies all protected Fixed scorer and SELL policy sources, hard Forecast worker freeze, separate Beta read-only client, route invariants, unit tests, backend tests, APK package and signing.
- Runtime test must verify FIXED_DUAL_ACCOUNT/FULL_NSE_SWEEP_ACCOUNT_PROFILE shows both accounts (if Beta authenticated) and no Beta trading requests. Verify number of 429s, median/p90 full scan latency, 85% coverage, and Fixed published calls on comparable market sessions.
- Two independent Groww API credentials and permitted use of market data on both accounts are prerequisites to a true dual-lane benchmark. Do not assume broker-side quotas are independently additive without live evidence and broker approval.
- This test build has no proof of faster actual scan times, higher profitability, or live GTT recovery. Fallback to Alpha can still be slow or receive 429.
- Same market inputs must produce the same Fixed scores; data from two accounts may differ by observation time and consequently change which candidates qualify. Never claim that live recommendation counts will be identical without a controlled replay.

## Signing and installation
This is a TEST APK. If CI lacks the stable keystore secrets, it is signed with an ephemeral Android Debug certificate, potentially incompatible with the existing installed v8.2.0 package. Do not uninstall an app with open Alpha holdings, active protective GTTs, or unexported local history; first verify all broker-side positions/GTTs directly in Groww and export logs. Keep Fixed Auto Buy OFF pending broker-side end-to-end validation.
