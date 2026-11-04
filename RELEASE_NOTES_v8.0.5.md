# Multify Trader Pro v8.0.5 TEST — Fixed Peak/Entry Safety

Based on v8.0.4 Fixed Speed / Candidate Watch. **Engineering TEST**. Not an independently demonstrated improvement in executed profits.

## What is implemented
1. **Composite price-extension and peak-risk actionability overlay.** A Fixed PRIMARY that has advanced at least 1.5% (or 1.25× its target, whichever is greater) since its first observed PRIMARY, is within 0.50% of the observed session high, **and** is at least 5% up on the day is labeled `RESEARCH_ONLY` and not eligible for a Fixed BUY. An exceptionally large day rise with thin market quality, or extreme VWAP-to-ATR stretch with near-high and corroborating evidence, also triggers a hold. A stock being up >5% or near its day high **alone** is merely cautioned, not blocked. Prices and high observations are from captured broker quotes or a fallback based on timestamp-verified **same-day** 15-minute bars, without extra broker requests. The frozen VWAP/ATR features span the downloaded multi-day history and are labeled as such; they are not claimed to be pure intraday VWAP.
2. **BUY-only enforcement** for manually requested Fixed entries and automatic Fixed entries: re-evaluate the independent safety overlay against a fresh broker quote, and retain existing bid/ask spread, thin-turnover, +0.40% no-chase, market-price band, broker protection and cash checks.
3. **Late-session hold** from 15:15 IST onward. Late/post-close recommendation records remain in the Fixed research database, but display `RESEARCH_ONLY` / `Fixed WATCH` rather than an actionable BUY. The independent UI cutoff matches the authoritative Fixed BUY execution guard. Portfolio protection/GTT and SELL monitoring remain available for the normal session.
4. **Restart/reinstallation duplicate suppression:** import same-day recommendation identity from a prior `fixed_recommendations.json` ZIP without importing active positions/orders, and block previously recommended symbols from new Fixed BUY alerts and execution. Existing installation Fixed notification delivery now keys once by same-day symbol, not scan start. The safe identity archive `fixed_imported_same_day_dedup.json` is exported and restored.
5. **Evidence**: emit `FIXED_ENTRY_QUALITY/PUBLICATION_ACTIONABILITY` with existing detailed quote / first-PRIMARY run-up / day-high evidence provenance / multi-day-window VWAP-ATR / reasons / actionability; existing Fixed timing, Fast Watch, historical outcomes and scan profiles remain available for comparing winning vs losing setups.
6. **Speed infrastructure retained from v8.0.4:** full-market three-request bounded lookahead, 1,200ms Groww LTP spacing, 40-per-minute endpoint cap, safe incremental historical candles and read-only Fast Watch between full scans. No unverified authenticated Python-to-Android streaming connection.

## Frozen and untouched
- Fixed's 40-factor 15-minute scoring, market breadth, universe selection, ranking, independent confirmation and target calibration (1.1161043%).
- Existing trailing SELL/GTT, -2% hard stop, +0.55% activation / +0.50% protected / +0.10% ratchet, Multify and Forecast selection paths.
- Original Fixed recommendations remain research evidence; this new version **changes only actionable status and Fixed BUY execution** for high-risk entries. It may decline good winners as well as prevent bad entries until calibrated against sufficient follow-up sessions.

## Validation and next-day logs
- Observe `FIXED_ENGINE/LONG_SCAN_TIERED`, `FIXED_ENTRY_QUALITY/PUBLICATION_ACTIONABILITY`, `EXECUTION_LATENCY/FIXED_NOTIFICATION_POSTED`, `FIXED_EARLY_WATCH`, `FIXED_PULSE_RESEARCH`, `FIXED_FAST_CANDIDATE`, `RUNTIME/FIXED_SCAN_PROFILE`.
- Compare blocked/caution/allowed calls against subsequent target-first and stop-first sampled outcomes; compare matched scan durations and NSE coverage; include broker 429 counts. **Do not interpret sampled targets as realized P&L**.
- Verify that a stock already recommended on the same day is not posted/bought twice after importing an older ZIP. A screenshot or manual record alone is not enough to reconstruct missing broker quotes.
- Keep Auto BUY OFF until controlled BUY -> fill -> protective GTT -> SELL lifecycle is validated, and check all Groww holdings and active GTTs before changing app installation.
- Debug APK signing certificates can differ between builds. Do **not uninstall** while an app manages holdings/GTTs or holds unexported learning data.
