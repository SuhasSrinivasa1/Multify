# Suhas Shree Trader Pro v9.0.33 TEST

## Added
- **Large caps** tab: officially sourced Nifty-100 constituents where available, Banking/IT peer groups, daily 5D/20D price strength, EMA20/EMA50, volume acceleration, sector-peer breadth, and read-only INTRADAY WATCH / SWING WATCH classifications.
- **6M lows** tab: all eligible NSE **EQ cash shares** whose latest validated EOD close is between **0% and 10% above the lowest official NSE daily low in six calendar months**.
- Research-only intraday pulse reuses the already completed full-market LTP snapshots from Fixed. **No additional Groww API calls** for this path.
- Off-market NSE CM-UDiFF bhavcopy importer: downloads an entire daily equity file at once, keeps a rolling six-month local SQLite EOD archive; initial backfill in batches of 25 days.
- Best-effort WorkManager nightly scheduler for 20:00 IST, with an immutable network admission restriction to **18:30–08:00 IST**. The app schedules a catch-up when launched during off-hours.
- Downloads are separate official NSE EOD archives, not Groww historical requests. The app refuses missing, partial or malformed files and shows incomplete-history status rather than incorrect "6M LOW" claims.
- Archived EOD OHLC data is **unadjusted** for splits, bonuses and corporate actions. Verify corporate actions before trading on a low.
- Independent diagnostic and shadow research; no automatic trading, SELL, targets or capital allocation rules are added for large caps.

## Unchanged
- Frozen Fixed 40-factor production model, 15-minute source, confirmations, target.
- Fixed and Working Range rules, hard stop, trailing stop and broker protections.
- Manual v9.0.32 MIS long and short, Alpha-only execution, Beta read-only.
- Original trading Room schema. EOD research uses a separate database file.
- Existing app's position database is not copied into this isolated test app.

## Coverage and operational limitations
- Official Nifty-100 CSV may be temporarily unavailable on a mobile network. A small **curated seed** universe is labeled unverified and is not a substitute for full index membership.
- The first import requires six months of archive history (roughly 120+ market sessions). Data may take multiple off-market worker passes or several nights if the phone or exchange limits access.
- Historical NSE files may be blocked (403), unavailable (404), or missing on holidays. Show error/completeness, never hallucinate rows or six-month rankings.
- WorkManager can be delayed by Android battery settings, Doze, force-stop, low connectivity and manufacturer restrictions. It cannot guarantee exactly 20:00 IST; it guarantees only that any launched network work checks the after-hours window before requests.
- All six-month lows use the **latest completed EOD close**, not a live quote.
- Large-cap intraday "watch" requires a fresh verified Fixed LTP snapshot and sector-peer observations; this is not a new live Auto-Buy strategy.
- Live Groww positions, cash balance and protective orders are not integrated with the EOD research worker.

## Installation
- Name: **Suhas Shree Trader Pro v9.0.33 TEST**
- Separate package ID: `com.multify.traderpro.vivoy73final.v9033test`, protecting the currently installed v9.0.32 and production app.
- Keep v9.0.32/production responsible for its existing live Alpha positions and verified broker-side protection.
- Keep new application's Auto-Buy switches OFF during Monday testing. Intraday/swing research never submits orders itself.
- Observe the two new tabs after the first nightly archive pass and export logs if the exchange blocks the importer.
