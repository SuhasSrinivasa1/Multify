# Multify Trader Pro v8.0.7 — GTT Protection Reconciliation & UI TEST

## Scope
Focused reliability TEST update based on the November 5, 2026 v8.0.6 diagnostic export. It preserves the v8.0.6 Fixed repeat-event implementation and frozen v7.4.1 Fixed model.

## P0 — Broker-side protection lifecycle
- Serialize each symbol's confirmed BUY fill registration and trailing management with a per-symbol lifecycle mutex.
- Replace the portfolio monitor's full-entity price-mark write with a Room SQL update that changes only lastPrice and lastEvaluatedAtMs. A stale snapshot can no longer erase a newer smartOrderId, mode, trigger or status.
- Reload the authoritative managed-position row before applying trailing decisions.
- If local GTT association is missing, recover only a unique ACTIVE/OPEN SELL CNC GTT whose exact MHS/MTG/MTR reference matches the position ID, symbol and openedAtMs. Same-symbol or same-quantity manual GTTs are never adopted.
- If the exact app GTT is found, attach its broker ID/status/trigger to the existing position and block duplicate creation.
- Preserve existing ownership, quantity, duplicate-close, broker order reconciliation and non-loosening trailing rules.

## Multify entry quality
- A fresh Groww quote is checked against the notification's upper entry-band reference before the market BUY.
- The existing 0.40% maximum extension guard is applied; slow/invalid quotes or excessive extension block the BUY before submission.
- Actual average fill deviation is recorded separately. A MARKET order can still slip after the quote, so the app records that distinction rather than claiming an absolute fill-price cap.

## UI simplification
- Fixed screen now shows open holdings and broker GTT status prominently.
- Fixed Fast Watch, Market Pulse, Early Watch and historical research are summarized in one compact background-research card.
- Factor breakdowns, candidate rejections, API timings, cache diagnostics and worker traces remain in exported backend logs.
- Fixed 40-factor scoring, 15-minute candles, target calibration and SELL policies remain unchanged.

## Intentionally unchanged
- Fixed market-discovery pacing remains under the existing Groww rate governor; no concurrency/rate-limit shortcut was introduced.
- Forecast one-minute candle qualification and outcome reconstruction remain conservative. Missing historical rows (including older dates where Groww returns no candles) are not fabricated or marked complete.
- The 0.40% Multify entry check is a pre-trade quote guard, not a guaranteed market-order fill ceiling.

## Verification and limitations
CI must pass protected Fixed/SELL source checks, backend tests, Android unit tests, APK build and signature/package verification. This remains a TEST APK. CI cannot prove live Groww order/GTT behavior or profitability. Keep automatic BUY disabled until exact GTT recovery and trailing update behavior has been observed safely with broker-side records.

## Installation caution
Debug signing certificates can differ between builds. Do not uninstall or replace an app while holdings, GTTs or unexported learning history exist. Verify open broker positions and active GTTs directly in Groww before any reinstall. If Android refuses an in-place update due to signer mismatch, do not uninstall until the existing holding and GTT have been safely reconciled.


## Stable signing
- The v8.0.7 CI workflow now requires the documented stable signing secrets and verifies the expected certificate SHA-256 before uploading an APK.
- The CI source archive explicitly excludes the temporary keystore file.
- This certificate is different from the v8.0.6 TEST APK's Android Debug certificate. Android will not permit an in-place update from v8.0.6; do not uninstall the old app while any managed holding, active GTT or unexported local history remains.


## Follow-up hardening
- The portfolio monitor now attempts exact-reference recovery before classifying an active GTT as an unrelated broker-order conflict. This allows an existing app GTT to be re-associated on restart even when the local ID was previously lost.
- The holdings card labels the trigger explicitly as the app's broker GTT trigger; it does not misrepresent that value as Multify's original notification stop.
- When stable signing secrets are absent, CI still produces a debug TEST APK but records that it cannot update a differently signed installation. The v8.0.6 debug signer cannot be recovered from its APK.
