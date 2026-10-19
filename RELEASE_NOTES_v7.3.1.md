# Multify Trader Pro v7.3.1 — Resilient Forecast + Learning Restore

v7.3.1 contains all v7.3 resilience/publication changes and adds a safe migration path for debug-signed APK upgrades.

## Learning restore from diagnostic ZIP

Settings → Device & diagnostics includes **Restore learning from exported ZIP**.

The restore imports only:
- Multify long-learning/MFE rows
- forecast research snapshots and recorded outcomes
- published forecast history
- forecast champions
- after-hours research reports

It intentionally never imports holdings, orders, execution state, Groww credentials or tokens, ARM state, broker authentication/static-IP state, or other trading state.

This allows learning continuity when Android requires uninstall/reinstall because successive CI debug APKs use different debug certificates.

Duplicate research snapshots are ignored by date + scan timestamp + symbol + rank. Existing learning rows are merged. Imported historical forecasts are marked already-notified so restore cannot replay old recommendation notifications.

ZIP processing is bounded to 32 MiB per learning entry and 64 MiB total selected learning content.

## Forecast reliability inherited from v7.3

- same-day market discovery state survives Android process restarts
- cold broad-market scans are research-only
- market-wide PRIMARY names need current + one prior PRIMARY scan
- fallback/history PRIMARY names need current + two prior PRIMARY scans
- low-coverage/network-aborted broad scans cannot publish
- forecast outcomes use batch LTP
- after-hours research backs off during repeated transient Groww/DNS failures

Version: `7.3.1-resilient-forecast-restore` (`versionCode 731`).
