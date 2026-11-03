# Multify Trader Pro v8.0.0 — Fixed Early Intelligence Major Step

This release is a **research and reliability upgrade**, not a revision of the
accepted frozen Fixed strategy. The production Fixed 15-minute candles,
40-factor scoring, discovery ranking (65% session move / 35% pulse), repeat
PRIMARY confirmation, 1.1161043% reference target, BUY eligibility, and
hard-stop/trailing/GTT logic are not modified. Forecast remains independently
adaptive.

## Implemented in the Android APK

| Area | Implementation | Production effect |
|---|---|---|
| Precise Fixed quote HTTP timing | Alpha NSE CASH quote requests record client start, response receipt, elapsed and HTTP status; correlated to factor snapshot before async queue | Diagnostic only; no new broker request |
| Market depth and volume | Capture genuine broker bid/ask (with depth fallback), spread in bps, absolute reported daily shares, approximate traded notional and quote-provided high distance | Shadow flags only; no liquidity-based rejection |
| First PRIMARY and late entry | After a complete Fixed scan and order path, save the earliest recorded PRIMARY qualifier, its evaluated price and time; compare to later official Fixed entry | Early Watch is not an official BUY or a shortcut past confirmation |
| Early Watch UI | Visible read-only section under Fixed showing first price, latest evaluated price, appreciation, confirmation count, spread/volume and provisional warnings | No BUY action for Early Watch |
| Local persistence | Separate bounded Fixed Early Watch journal in app-private storage, included in exported diagnostics and optional research-only ZIP restore | Survives same-signer app updates, not uninstall |
| Mobile watchdog | Foreground-service worker tracks network validation, holding-monitor success, Fixed scan progress and active workers; restarts exited workers, never cancels an active order/scan | Improves service observability and safe exited-worker recovery |
| Phone power | Retain existing position-protection partial wake lock and Android foreground-service lifecycle | Screen is not forced on; Android may still impose OS limits |
| Baseline provenance | Records first observed app scan date/time independently; warns that a fresh afternoon startup is not NSE's 9:15 opening price | No change to frozen ranking inputs |
| Permanent logs | Integrate early-watch and health events into curated decision journal; factor exports contain quote timing and quality shadows | All statuses are evidence, not trade instructions |

## Implemented as separately opt-in research, not active on Android

`backend/app/fixed_stream_research.py` is an experimental Groww **official
Python SDK** feed adapter. It selects up to 1,000 NSE CASH instruments, checks
exchange timestamps, rejects data over 10 seconds old and emits only research
observations. It requires separately provisioned SDK credentials, deployment,
and live-feed validation. It is **not enabled by default**, not linked into
Fixed's production inputs, cannot replace all ~2,623 REST symbols, and cannot
be claimed to reduce Android Fixed's full-universe discovery duration yet.

See `backend/FIXED_V8_STREAM_RESEARCH.md`.

The `backend/app/fixed_peak_report.py` script analyzes exported
`fixed_early_watch.json` and `fixed_recommendations.json` files without
guessing exchange quote freshness or simulated execution fills. Example:
`python -m backend.app.fixed_peak_report your-export.zip`.

## Safety / correctness contract

1. Fixed evaluation, factor families, quality, publication and SELL/GTT
   protected policy files must match v7.9.44 exactly.
2. Locked Fixed sections in `TradingRepository.kt` must match v7.9.44.
3. The first-PRIMARY journal never calls a broker, creates orders, suppresses
   Fixed recommendations, or changes the Fixed target or cadence.
4. Missing or crossed bid/ask is **unknown**, never assumed zero spread in
   the new shadow telemetry. This release does not alter the inherited frozen
   production spread treatment.
5. Network timestamps represent **client request/response timing**; Groww's
   REST quote payload has no verified exchange source timestamp. Do not
   mislabel client freshness as market-data age.
6. The watchdog never force-kills an active worker or tries to bypass Groww
   rate limits. Device force-stop, Doze restrictions and outages are not
   universally recoverable from inside the app.
7. Before an in-place install, the app package and signing certificate **must**
   match. Ephemeral debug signatures cannot migrate Android private data.
   Do not uninstall an app managing open holdings/GTTs.

## Next live-session acceptance criteria

Compare the complete same-date Fixed records, not disjoint morning/afternoon
samples: 15-minute source bars, client quote latency, scan discover/detail
phases, time from first PRIMARY to publication, price appreciation while
confirming, actual spread/absolute volume, target-hit timing and MFE/MAE,
protection-monitor health, cooldowns and CPU/battery behavior.

A successful CI build proves source regression and compilation, **not**
improved real trading profits. A production streaming replacement and any
optional liquidity/late-entry BUY safety filter require separate live
validation and explicit approval.
