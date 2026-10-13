# Multify Trader Pro v6.6.0 — Live Hardening

## What changed

- Cash-aware CNC order sizing now keeps a Groww RMS reserve and a 2% execution-price buffer before calculating quantity, preventing near-full-balance BUY rejections like the TIMEX case seen in the v6.5 logs.
- Both Multify ARM buys and forecast buys use the same safe sizing policy and emit `CNC_BUY_SIZED` audit evidence.
- One-time PGIL recovery restores the official 12-Oct-2026 Multify benchmark (entry midpoint ₹1,297.20, target ₹1,355, exit ₹1,321, reported return +1.83%) even when old notification rows are no longer in the local signal table. Existing PGIL rows are repaired instead of duplicated.
- Trailing audit now records every arm/₹0.25 ratchet evaluation with old/new high-water, previous ratchet high, old/new stop, whether the stop rose, ATR and trail distance.
- ARM hot-path readiness logs are throttled to material balance/ARM changes or a 15-minute heartbeat instead of every 30-second refresh.
- Notification listener connection/disconnection audit is throttled and disconnect storms collapse into a bounded rebind request.
- Version bumped to `6.6.0-live-hardening` (`versionCode 660`).
