# Multify Trader Pro v8.0.2 TEST — Reliability & Early Intelligence

Base: verified v8.0.1 Forecast/Univest recovery branch. This is an engineering **TEST** release; not live-trading validated.

## Implemented
- **Univest delivery diagnostics:** separate Android notification access, listener connected state, last official Univest notification delivery, last parsed symbol and exact rejection reason. Settings includes a 15-second refresh and Android notification-access shortcut. Official-package verification and strict research-only isolation remain.
- **Screenshots as test cases:** YATHARTH at 09:27 IST and RGL at 13:32 IST, 4 Nov 2026. The RGL potential percentage was not visible, and neither screenshot is evidence of a broker quote or completed trade.
- **Broker API:** retain centralized groww recovery/rate controls. After a 429, discretionary Alpha Forecast work stays quiet for five additional minutes, without blocking exits, broker protection or frozen Fixed. Watchdog reports BROKER_API_COOLDOWN rather than misclassifying intentional deferral as a stalled Fixed scan.
- **Forecast:** retain recent PRIMARY confirmation priority from v8.0.1 and skip detailed work for stale-unambiguous, very-far-out-of-band candidates using fresh Fixed market snapshots. Near-boundary/stale data continues to be evaluated. Deterministic price-band exclusions are not incorrectly counted as API errors.
- **Execution-only entry guard:** for Fixed and Forecast direct/manual BUY, require broker quote request round trip <=10 s and disallow buying >0.40% above recorded recommendation. Existing 40bp spread rule, price band, buying power requirements and protective GTT safeguards remain. No entry filter changes the Fixed *recommendation* itself or Multify immediate notification behavior.
- **Chronological research outcomes:** append-only sampled-price first-target/first-2%-stop records for Fixed/Forecast, exported in `research_outcome_chronology.jsonl`, with separate read-only learning restore. These are *not* broker fills or realized profit. Forecast after-hours one-minute replay records separate first-touch classification, with same-candle target/stop and missing-candle gaps marked ambiguous. The historical research `outcomeTargetHit` label now requires target observed first in the one-minute replay; old source evidence remains distinct.
- **Five-minute research:** aggregate already retrieved Forecast one-minute candles locally and log EMA9/20, VWAP, volume and three-bar breakout context. No added market-data request and no automatic trade signal.
- **News reliability:** bounded streaming RSS/XML parser with per-field and feed limits; retain complete parsed items after malformed trailing XML. Bound NSE catalog downloads.

## Explicitly not claimed
- The physical Groww Alpha full-NSE discovery pacing floor is **not removed**. There is no verified faster production scan without provider-side transport change.
- Zero historical Fixed five-minute shadow pairs are not retroactively fabricated.
- No live BUY, partial-fill, GTT or SELL broker execution has been verified in this update. Unit/static checks cannot substitute for a controlled broker test.
- Screenshots cannot supply missing historical quote values or 48-factor entry-time features, and potential 5% is not realized profit.
- User data migration/in-place install depends on the app's Android signing certificate; debug signing may prevent updating a prior installation without data loss.

## Protected
The existing 40-factor Fixed 15-minute selection and 1.1161043% target, original Fixed cadence/publication, hard stop (-2%), +0.55% arm / +0.50% protected gain / +0.10% ratchet and existing Multify BUY/Sell/GTT logic are not modified. CI asserts frozen source against `v7944-fixed-api-protection-latency`.

## Suggested validation before unattended trading
1. Export current log ZIP, check Groww holdings and GTT orders directly, and compare signing certificates.
2. Verify Notification Access and listener health, then inspect the next official Univest advisory in capture logs.
3. Run market-hours paper/simulation diagnostics; measure actual Forecast publication and First-touch labels separately from real profit.
4. Perform controlled broker tests for fill -> verified GTT -> partial fill -> trailing SELL before enabling Auto BUY.
