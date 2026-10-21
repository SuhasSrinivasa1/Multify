# Multify Trader Pro v7.5.5

## Fixed reference state made explicit in UI

- Fixed initializes its immutable embedded reference snapshot even when Groww is not authenticated.
- Fixed no longer shows misleading zero history simply because the broker dashboard has not connected.
- The Fixed tab now shows:
  - 107 frozen Multify calibration calls.
  - Frozen Multify MFE benchmark: 1.1161043030113955%.
  - Hard target floor: 1.00%.
  - Frozen Fixed target/average upside: max(1.00%, 1.1161043030113955%) = 1.1161043030113955%.
- Prior Forecast feedback rows, prior research-feedback rows and prior champion bonuses remain zero intentionally. They are not missing phone history; the accepted v7.4.1 reference session is frozen without post-hoc feedback contamination.
- The daily Fixed discovery baseline remains live session state and is rebuilt from current NSE/Groww market data.

## Fixed model remains locked

- No changes to the v7.4.1 Fixed strategy implementation.
- No changes to the 40-factor model, factor-family rules, quality gates, publication rules or discovery model.
- No changes to the >₹100 publication/execution overlay.
- No changes to the 09:15–15:30 IST recommendation window.
- No changes to Fixed remaining-wallet execution sizing.
- No changes to stepped trailing exits.
- CI still enforces the final Fixed source-lock fingerprints and exact v7.4.1 source equivalence.
