# Multify v9.0.34 TEST — UI alignment and on-demand six-month-low scan

## UI changes
- Rename the visible app title and launcher label to **Multify**.
- Use a compact, left-aligned one-line top bar title and a single-line, ellipsized screen subtitle.
- Let Alpha/Beta status pills wrap cleanly on narrow screens instead of clipping horizontally.
- Shorten bottom navigation labels to keep all five tabs on one line.
- Standardize the six-month-low scan action as a full-width primary button with a visible scanning state.

## Six-month-low on-demand scan
- Add **Scan Now** to the 6M lows screen.
- A tap always recalculates the screen from the locally stored NSE EOD archive.
- During the permitted 18:30–08:00 IST window, it also imports up to 25 missing official NSE CM-UDiFF session archives per tap.
- During 08:00–18:30 IST, the action remains local-only; it does not bypass the existing network guard.
- Manual and WorkManager refreshes share a coroutine mutex to prevent concurrent duplicate archive downloads.
- No Groww API calls are added. The feature does not create BUY/SELL recommendations or orders.
- Six-month-low claims remain withheld until at least 105 valid sessions span roughly six calendar months.

## Unchanged
- Fixed scoring, 15-minute production inputs, confirmations, target and execution policies.
- Working Range, Manual MIS, Alpha-only execution, Beta read-only, SELL/GTT protection and reconciliation.
- NSE EOD prices remain unadjusted for corporate actions.

## Installation and data isolation
- Test version: 9.0.34, package suffix `.v9034test`.
- It installs side-by-side with v9.0.33 and does not inherit v9.0.33's local EOD archive or app database.
- Keep the previous app installed and responsible for any live positions or broker-side GTTs. Do not uninstall it to install this TEST build.
