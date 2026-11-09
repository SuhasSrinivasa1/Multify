# Multify Trader Pro v8.3.12 — Final Fixed late-entry guard

Baseline: v8.3.11 source commit b652a57a183346c6929389d4bccc473ca503c385.

## What the v8.3.11 logs established

- Fixed Auto Buy was enabled at 14:51:28 IST.
- Pre-activation calls were correctly excluded.
- DHUNINV was not bought because its quoted spread exceeded 40 bps.
- EXIDEIND was not bought because Groww/app ownership checks reported the symbol already existed in holdings.
- TERASOFT was not bought because its quoted spread was approximately 100 bps.
- No Fixed Auto Buy order was submitted. These were explicit safety rejections, not a disabled toggle.
- Several new recommendations were near the top of the frozen multi-day 15-minute window and materially extended from the same window's VWAP/EMA20.

## Targeted correction

Activate a narrow execution-only anti-chase confluence rule inside FixedEntryActionabilityPolicy, shared by the Fixed presentation and Fixed Auto Buy paths.

A candidate is marked RESEARCH_ONLY / BUY BLOCKED only when all four are present:
1. Price is within 0.35% of the frozen multi-day 15-minute window high (explicitly not represented as today's NSE high).
2. Price is at least 3.0 ATR above the same multi-day 15-minute VWAP.
3. Price is at least 2.0 ATR above EMA20.
4. The latest 15-minute impulse is at least 1.2 ATR.

Missing evidence does not fabricate a peak and does not trigger this rule. No extra broker requests are made.

## Unchanged

- Frozen v7.4.1 / 40-factor Fixed scoring, ranking, selection and recommendation target.
- PRIMARY confirmation and scan cadence.
- Fixed Auto Buy activation timestamp / post-toggle-only rule.
- Same-symbol ownership and duplicate-order checks.
- 40-bps spread protection and existing liquidity checks.
- Fixed Auto Buy independent of other-symbol protection recovery.
- Hard-stop GTT, trailing SELL and GTT lifecycle policies.
- API governor and HTTP 429 cooldown policy.
- Budget-per-order sizing.

## Evidence and limitations

The November 9 diagnostic cohort is sparse sampled LTP evidence, not realized broker P&L or intrabar high/low. The four-way late-entry rule is a conservative execution-risk filter, not an out-of-sample-validated predictive-alpha model. It deliberately avoids blocking a strong continuation merely because it is extended; proximity to the multi-day window high and a large recent impulse are also required.

## Verification

The dedicated CI workflow checks the frozen Fixed/SELL/GTT source, activation logic, anti-chase policy/tests, backend tests, Android unit tests, APK build/signature and artifact packaging. CI success does not replace a live broker-side validation session.
