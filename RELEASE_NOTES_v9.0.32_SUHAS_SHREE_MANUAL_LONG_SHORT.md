# v9.0.33 — Suhas Shree Trader Pro · Manual Long/Short TEST

## What is added from v9.0.30

- Dedicated Manual tab with locally indexed NSE CASH symbol/company-name search.
- Explicit operator-selected **BUY · MIS long** and **SELL · MIS short** actions.
- Alpha-only order execution. Beta remains read-only and is never used for manual execution.
- Manual MIS long uses a broker-pending SELL SL-M, the existing Fixed long stop/trailing policy, and independent MIS monitoring.
- Manual MIS short uses a broker-pending BUY SL-M, a mirrored favourable-price trail, and an independent BUY-to-cover path.
- Durable order references and intent records precede broker POSTs. Ambiguous acknowledgements are reconciled by exact reference; no blind order replay.
- The Manual page displays active short state and broker protection details for manual MIS longs.
- NSE company names and symbols are loaded from validated official NSE/Groww instrument CSVs; autocomplete is local and does not issue a Groww quote on every keystroke.

## Manual MIS lifecycle

- Manual long and short entries are permitted only before **14:30 IST**.
- The independent MIS monitors attempt square-off from **14:55 IST**.
- Manual long: initial inherited hard floor **-1%**, trail arms at **+0.55%**, initially protects **+0.50%**, then ratchets by **+0.10%** using the existing long policy.
- Manual short: mirrored initial hard stop **+1%**, then a downward BUY-stop trail after favourable movement using the existing stepped-trailing milestones.
- Manual short additionally requires verified upper-circuit headroom and a fresh liquid Alpha quote.
- MIS protection is an exchange/broker pending SL-M order, not a CNC GTT. The app verifies the broker order ID and pending status.
- Failed protection, ambiguous fills, inventory mismatch, or order-state uncertainty engages the safety halt and requires broker reconciliation. A local stop calculation alone is never called protection.

## Explicitly unchanged

- Fixed 40-factor model, 15-minute production inputs, confirmation and target calculations.
- Working Range selection and exit thresholds.
- Automated Fixed/Working Range/other existing entry paths remain **CNC** in this branch.
- Automatic short selling is not added. Only the operator-selected Manual SELL action opens a short.
- Existing CNC GTT protection and the v9.0.31 broker-flat terminal-GTT quarantine remain active.
- Alpha executes; Beta remains read-only.

## Monday TEST installation

- Application label: **Suhas Shree Trader Pro TEST**
- Application ID: `com.multify.traderpro.vivoy73final.v9033test`
- Separate debug package/database. It does not import or manage positions created by the existing installed app.
- Auto-Buy switches remain OFF by default.
- Keep the existing app responsible for existing live positions and GTTs while shadow-testing this app. Do not uninstall the existing app to install this TEST variant.
- CI verifies the frozen-policy parity, manual MIS ownership separation, Android unit tests, backend tests, APK identity and signature.
- A CI-built APK is not proof of live Groww execution. Broker-confirmed fills and active stops must be verified during supervised testing.


## v9.0.33 additions
- Nifty 100 large-cap equity radar for intraday relative strength (reuses completed Fixed NSE LTP sweeps; zero additional Groww calls) and daily swing research (5D/20D return, RSI14, volume ratio, SMA20/50, breakout/pullback/continuation/exhaustion stages).
- Daily NSE six-month-low dashboard for all NSE EQ-series cash shares with sufficient history: 0–10% above the lowest low over 126 trading sessions.
- After-hours WorkManager schedule at 18:15 IST. The watchlist uses the single market-wide NSE CM UDiFF final bhavcopy ZIP per trading date, not per-symbol Groww historical requests. A first install backfills up to 126 trading sessions.
- Raw UDiFF prices are not corporate-action adjusted; suspected >50% adjacent-close discontinuities are flagged for review, not silently adjusted.
- Large-cap signals are research-only; Fixed/Working Range scoring, entry thresholds, and auto-buy paths remain unchanged.
