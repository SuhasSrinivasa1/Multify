# Multify Trader Pro v8.3.21 — PRIMARY Follow-up and Entry Timing (TEST)

Base: verified v8.3.20. Version code 8402.

## Evidence from November 12, 2026 v8.3.20 exports
- Repeatedly qualifying PRIMARYs were often displaced from the dynamic 28-name candidate shortlist; GABRIEL and JARO required ~9 and ~14 min respectively from first PRIMARY to publication, with appreciable run-up.
- Only ~4–8 of 28 ranked candidates frequently fell inside the already-enforced ₹200–₹5,000 evaluation band.
- Some recommendations had significant day gains and VWAP/RSI extension at publication. Existing `FixedEntryActionabilityPolicy` labels this CAUTION/RESEARCH_ONLY, but the user-facing BUY title was unclear.
- Two Alpha /v1/live-data/ltp HTTP429s at 14:50 and 15:00 IST occurred at 48/41 local 60-second request counts. Broker endpoint quota unverified; cannot guarantee full cure by pacing.

## Changes
1. Bounded 5-symbol PRIMARY carry-forward. The **latest stored observation** must remain eligible PRIMARY, be within 6 minutes and be in the pre-existing price band. Carry into the next Fixed detailed-evaluation universe **within the existing 48-name cap**, no extra broker batch or periodical scan. A fresh quote and 15-minute evaluation, unchanged confirmation and repeat publication policy, unchanged baseline readiness and broker protections remain mandatory.
2. Preserve evidence that the candidate was previously discovered in the dynamic NSE list for up to six minutes (process-local; process death falls back to stronger existing fallback confirmation). Do not grant a new publication based on previous prices alone. Explicitly annotate follow-up evidence in Fixed scan audit.
3. Apply the EXISTING price-band eligibility BEFORE `FixedMarketDiscoveryPolicy.rank` uses 28 dynamic slots. Master symbol universe, full NSE scan/health, 65/35 ranking formula, evaluation, score, target and price band remain unchanged. This *does change which eligible stocks receive detailed evaluation*; track performance before claiming improvement.
4. Give notifications a distinct `Fixed BUY · CAUTION` title when existing extension diagnostics indicate caution; `Fixed WATCH` remains for research-only. Existing actionability and BUY permissions are untouched. Caution is not a stop-order or trade block.
5. Conservatively pace only non-protective Alpha LTP GET requests at a minimum 1,500ms while retaining adaptive 429 cooldowns, protection priority and all order/SELL/GTT APIs. Broker 429s may still occur.
6. Retain v8.3.20 5-minute rolling LTP research, 15/30/60-minute sampled outcomes, historical candle cache/retry and logging. No new momentum veto, no hard RSI filter and no changing first 15-minute VWAP production rule.

## Frozen safeguards
- 107 Fixed learning references, full target +1.1161043030113955%, hard stop -5%, trailing arm +0.55%, protected gain +0.50%, ratchet +0.10%.
- Frozen 40-factor scoring and PRIMARY/SECONDARY thresholds, original `FixedForecastPublicationPolicy` confirmation counts, production VWAP/15-minute candles, same-stock reset and duplicate checks.
- No BETA order placement; ALPHA handles broker BUY/SELL/GTT. No new API calls or more than 48 detailed evaluations per cycle.
- No automatic trading on prior PRIMARY data. Auto BUY **OFF during initial live evaluation**.

## Test and deployment restrictions
- CI unit tests and static frozen-source normalization are necessary, but they do not show improved trading hit rates or establish broker quotas.
- This is a CI-signed TEST build. A changed debug signing certificate usually prevents data-preserving in-place updates. **Do not uninstall/clear existing live broker/trade and GTT state** to install a new test APK; use same-key signed upgrade only after checking current positions, GTT and backups.
- Review the debug-test artifact's actual certificate fingerprint versus the installed APK before installation.
