# v7.8.1 Execution Architecture Verification

## Result

**PASS with one explicit market limitation:** the codebase now matches the requested execution/safety architecture while the frozen Fixed engine remains source-locked. Faster Fixed delivery reduces avoidable latency but cannot guarantee that a stock has not already made its intraday peak before a recommendation is published.

## Verified invariants

| Area | Result | Verification |
|---|---|---|
| Fixed recommendation engine | PASS | All `FIXED_FINAL_LOCK.gitsha` files match their locked Git blob IDs. |
| Fixed market-data/input semantics | PASS | All `FIXED_INPUT_SEMANTICS_V772.gitsha` files match. |
| Fixed coordinator region | PASS | `a187eefb4ec2703af24c6cefdb61bfc51489b623`. |
| Fixed profitable trailing constants | PASS | `SteppedTrailingPolicy.kt` is byte-identical to v7.8.0: +0.55 / +0.50 / +0.10. |
| Market priority policy | PASS | `MarketPriorityPolicy.kt` is byte-identical to v7.8.0; Forecast remains behind Fixed and the 45-second guard. |
| Trading switches | PASS | Settings exposes exactly 3 execution switches: Multify ARM, Forecast auto-buy, Fixed auto-buy. |
| Toggle independence | PASS | Fixed and Forecast use `preIndependentExecutionGuard`; neither requires Multify ARM. |
| Existing holding exits | PASS | Sell monitor is independent of the entry toggles; disabling a toggle prevents new entries but does not disable exits. |
| Multify order-first | PASS | `TRADE_RELEASE` calls `handleMultifyBuy` before live-learning and Fixed-snapshot maintenance; minimal fingerprint/idempotency reservation remains before execution. |
| Fixed order-first | PASS | `generateFixedForecasts` → `autoBuyFixedRecommendations` → delivery event/notification. |
| Forecast order-first | PASS | `autoBuyForecastRecommendations` runs before pending Forecast rows are marked notified; higher-priority arrivals defer delivery. |
| Common −5% hard stop | PASS | `HardStopLossPolicy.MAX_LOSS_PCT = 5.0`; initial stop is 95% of actual cost basis and common monitoring applies it to all app-owned LONGs. |
| Overnight protection | PASS | Closed-session monitor calls hard-stop recovery and preserves tighter profitable trailing GTTs. |
| No averaging down | PASS | No scale-in/downward-average execution path exists; live schema had prior averaging state removed. |
| Room schema/migrations | PASS | `AppDatabase.kt`, `ManagedPositionEntity.kt`, and `ManagedTradeDao.kt` are unchanged from v7.8.0. |
| Self-recovery | PASS | Foreground service is `START_STICKY`; `SupervisorJob` isolates workers; position worker retries; deterministic order references recover ambiguous broker responses. |

## Common hard-stop behaviour

For an actual fill of ₹100, the hard-stop trigger is ₹95.00. At ₹95.01 it is not breached; at ₹95.00 or below it is breached. When the holding reaches the existing +0.55% trail arm, the profitable stop at +0.50% becomes tighter than the −5% floor and then ratchets upward in the existing +0.10% steps. The stop never moves downward.

The hard stop is price/cost-basis protection. A gap, auction/opening move, illiquidity or market-order slippage can produce a fill below the trigger, so realised loss is not guaranteed to be exactly −5.00%.

## Priority and self-recovery

The runtime safety order is:

1. continuous SELL/holding protection;
2. an active Multify call/order event;
3. Fixed scan/recommendation/execution;
4. Forecast only when spare API capacity exists.

Forecast cannot consume capacity while Fixed priority is requested or a market-critical Multify event is in flight, and it is blocked during the 45-second pre-Fixed guard window. A broker-order exception is followed by lookup using the deterministic reference ID before an error is surfaced, preventing blind duplicate resubmission after an ambiguous acknowledgement.

## Local verification completed

- Backend tests: **2 passed**.
- Kotlin execution-policy compilation: **PASS**.
- Standalone hard-stop/trailing/runtime-constant behaviour assertions: **PASS**.
- v7.8.0 and v7.8.1 workflow YAML parsing: **PASS**.
- Fixed final source lock: **PASS**.
- Fixed input-semantics lock: **PASS**.
- Static execution-order / three-switch / self-recovery assertions: **PASS**.

A full Android APK build/signature run is not claimed locally because this sandbox does not include the Android SDK/Gradle build environment. `.github/workflows/multify-v781-verify.yml` performs Android unit tests, APK build, package/signature verification, Fixed locks, execution ordering checks and release packaging in GitHub Actions.

