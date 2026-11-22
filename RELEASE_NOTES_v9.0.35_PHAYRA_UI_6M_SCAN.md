# Phayra v9.0.35 TEST — UI alignment and on-demand six-month-low scan

## App identity and UI
- Set the one-word in-app title and Android launcher label to **Phayra**.
- Use a compact, left-aligned one-line title with a single-line, ellipsized page subtitle.
- Let Alpha/Beta account status pills wrap on narrow screens instead of clipping horizontally.
- Shorten bottom navigation labels (Working Range → Range; Large caps → Large) to keep all five tabs aligned.
- Keep a full-width **Scan Now** button with an in-button scanning state on the six-month-low screen.

## Six-month-low on-demand scan
- Every tap recalculates the screen from the locally stored official NSE EOD archive.
- During the permitted 18:30–08:00 IST window, it also imports up to 25 missing official NSE CM-UDiFF session archives per tap.
- During 08:00–18:30 IST, it remains local-only and does not bypass the existing network restriction.
- Manual and WorkManager archive refreshes share a coroutine mutex to prevent concurrent duplicate downloads.
- No Groww API calls are added. This research action cannot create BUY/SELL recommendations or orders.
- Six-month-low claims remain withheld until at least 105 valid sessions span roughly six calendar months.

## Frozen trading boundaries
- Fixed scoring, 15-minute production inputs, confirmation, target and SELL/trailing thresholds are unchanged.
- Forecast remains frozen.
- Working Range and Manual MIS policies, Alpha-only execution, Beta read-only, broker GTT protection and reconciliation remain unchanged.
- NSE EOD lows remain unadjusted for corporate actions.

## Build and installation
- TEST version: 9.0.35, package suffix `.v9035test`.
- It installs side-by-side with the existing app; it does not replace or share the previous app's local database.
- Keep the existing live app installed and responsible for any open positions and broker-side GTTs. Do not uninstall it to install this TEST build.
- This is a TEST build; CI and on-device verification must pass before considering production use.
