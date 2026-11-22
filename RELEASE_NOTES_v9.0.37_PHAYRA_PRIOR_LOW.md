# Phayra v9.0.37 TEST — signed prior-low 3M/6M screener

## Corrected reference-low calculation
- For each selected 3M or 6M window, calculate the historical reference low using completed NSE sessions strictly before the latest EOD session.
- The latest EOD session supplies the close being screened; its own low is excluded from the reference minimum, so a new low cannot reset the benchmark and hide itself.
- Signed distance = (latest EOD close / prior-period reference low - 1) × 100.
- Include every finite signed distance at or below +10%; there is no negative lower bound.
- A negative value is a breakdown below the previous period low; zero is exactly at the prior low; positive values up to +10% are above it.
- The 1M reference low is also calculated before the latest EOD session, and its distance is allowed to be negative.
- Per-symbol minimum observation counts are calculated against prior sessions only.
- Rank by absolute distance from the previous low (closest first); at equal distance, a breakdown is shown before a still-above-low name.
- Show latest close, prior 3M/6M reference low and date, prior 1M low and signed distance, latest EOD date and prior per-symbol session count.
- Both 3M and 6M remain research-only, with existing coverage gates, ticker filter and Scan Now actions.

## Frozen boundaries
- No Fixed scoring, 15m production inputs, confirmation, target, SELL/trailing, Forecast, Working Range, Manual MIS, Alpha/Beta routing, broker order, GTT or protection code changed.
- No Groww API calls added; archive source remains official NSE EOD and downloads remain after-hours only.
- EOD prices remain unadjusted for corporate actions. This screener is not a BUY recommendation.

## Build / install
- Version 9.0.37 TEST, package suffix `.v9037test`.
- Side-by-side install; retain v9.0.36 TEST and the live trading app until verification.
- CI runs frozen-policy parity, backend tests, Android unit tests, APK package identity and signing verification.
