# Suhas Shree Trader Pro v9.0.33 — TEST

## New: Large-Cap NSE Cash Equity Radar

- Uses NSE Indices' Nifty 100 constituent universe as a published large-cap proxy (top 100 by full market capitalisation from the Nifty 500; constituents can number slightly above 100).
- Stores official company names and industry classification from the Nifty 100 constituent CSV, refreshed no more than weekly and only by the after-hours research worker.
- Intraday relative-strength leaders/laggards are derived from the existing complete Fixed market-wide NSE LTP sweep. The feature adds **zero Groww requests** to the intraday scanner and does not change Fixed scoring, rank, confirmation, targets or execution.
- Swing research uses NSE daily EOD bars and displays 5-session/20-session returns, RSI14, volume versus the prior 20-session average, SMA20/SMA50, distance from the six-month low, and descriptive stages: BREAKOUT, EARLY MOMENTUM, CONTINUATION, PULLBACK, EXHAUSTION, WATCH.
- These are research labels, not validated profit probabilities or automatic trade instructions. No Large-Cap Auto-Buy is enabled in this release.

## New: NSE Six-Month-Low Watchlist

- Covers NSE CM **EQ-series cash equities** present in the final bhavcopy, not only Nifty 100 names.
- Qualifies stocks whose latest EOD close is from 0% to 10% above the minimum daily low over the latest **126 market trading sessions**.
- Requires at least 100 daily observations per symbol to reduce misleading results from thin/incomplete histories.
- Sorted by distance from the six-month low, nearest first.
- Displays latest close, six-month low, low date, data date, available history count and possible corporate-action/price-gap review flag.
- Uses one market-wide NSE CM UDiFF final bhavcopy ZIP per trading date. Initial installation backfills up to 126 sessions; later updates fetch only missing trading dates.
- Daily refresh is scheduled for **18:15 IST**. Worker and repository both enforce after-hours execution; if WorkManager delays execution into the NSE session, the job skips network work and reschedules.
- The feature uses **zero Groww historical, LTP or quote calls**. It does not run during market hours.
- Raw bhavcopy OHLC prices are not corporate-action adjusted. Suspected >50% adjacent-close discontinuities are flagged for review, never silently adjusted.

## Existing strategy and execution boundaries

- Frozen Fixed 40-factor scoring, 15-minute production inputs, confirmation, target, hard-stop and trailing rules remain unchanged.
- Working Range rules remain unchanged.
- Existing Manual MIS long/short remains available from v9.0.32.
- Automated strategies remain CNC.
- Alpha remains the only execution/protection account; Beta remains read-only.
- Large-Cap radar and six-month-low watchlist are research-only. No new automatic BUY/SELL path is introduced.

## Installation

- App name: **Suhas Shree Trader Pro v9.0.33 TEST**
- Package: `com.multify.traderpro.vivoy73final.v9033test`
- Installs side-by-side with production and v9.0.32 test packages.
- Keep the existing app responsible for existing live positions and broker-side protection.
