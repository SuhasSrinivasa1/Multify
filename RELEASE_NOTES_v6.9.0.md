# Multify Trader Pro v6.9.0 — Production Learning

## Why this release exists

The v6.8 logs proved the scanner was alive but over-filtered: 22 scans evaluated 15–42 stocks each and admitted zero candidates. They also exposed repeated Android notification-listener reconnects, redundant NSE master refresh attempts, cancelled holding-monitor jobs, Groww timeouts/DNS failures and HTTP 429s.

v6.9 separates production execution from research and removes the listener lifecycle as the owner of the trading engine.

## Production engine

- A persistent foreground trading-engine service owns holding monitoring, forecast scans, outcome tracking, ARM hot-path maintenance and after-market research.
- Notification-listener reconnects no longer cancel holdings monitoring or forecasting.
- Listener and foreground-engine heartbeats are tracked separately.
- Listener lifecycle audit noise is throttled while reconnect counts remain observable.
- NSE master refresh skips the network when the cached master is still fresh.
- Broker/API cadence is reduced: 5-second active holding loop, 30-second broker-inventory cache, 60-second ARM hot refresh, 15-minute forecast scans and explicit per-symbol pacing.
- Closed-session engine cadence drops to 60 seconds to preserve battery.
- Cancellation is no longer misreported as a holding failure.
- App-owned SELLs are serialized and reconciled against Groww immediately before submission.
- Every close uses one stable client reference per position, reducing duplicate-order risk after ambiguous network failures.

## Forecast architecture

- Forecast target floor remains **1.00%**.
- Target remains adaptive from prior evidence: max(1.00%, rolling Multify MFE average, robust prior full-session forecast MFE).
- Same-day outcomes cannot inflate later targets during the same session.
- The v6.8 all-or-nothing filter is replaced by three explicit tiers:
  - **PRIMARY** — production-qualified TREND setup; only tier allowed to place a forecast BUY.
  - **SECONDARY** — analyst watchlist/observation only; never allowed to place a forecast BUY.
  - **RESEARCH** — rejected counterfactual candidate used only for learning.
- If a scan has no PRIMARY/SECONDARY setup, the best RESEARCH candidate is still shown as observation-only so the application never becomes blind.
- Five remains a maximum, not a quota.
- Weak market breadth demotes otherwise strong candidates out of PRIMARY.
- Ranking still uses recurring Multify symbols, reverse-engineered Multify DNA, champion evidence and technical quality.

## Five-session reverse-engineering capture

The next **five distinct trading sessions** are a fixed research window. After five sessions, the research cohort freezes automatically.

For every scan, up to the full 48 evaluated candidates are persisted with timestamp, quality tier, rejection reasons, target, entry, regime, market breadth regime, strategy, directional score, confidence, spread, order-book imbalance, day momentum, 52-week range position, VWAP, EMA9/20/50, indicator slopes, MACD, RSI, RVOL, volume acceleration, green/red volume share, recent market structure, session-high/low distance, ATR, Donchian/opening-range levels, current/prior candle OHLC and every strategy vote.

After market close, Groww candles reconstruct each captured candidate’s counterfactual MFE, MAE, session-close return and whether the adaptive target was reached. Incomplete reconstruction resumes later if Groww times out or rate-limits.

When Multify later reveals a stock, all earlier same-day research snapshots for that symbol are marked as **Multify matched**, allowing direct comparison of pre-call characteristics.

Every actual Multify BUY/Book Profit event also captures a technical snapshot and post-signal prices at 1s, 2s, 5s, 10s, 15s, 30s, 1m, 2m, 5m, 10m and 15m.

Completed prior-day research contributes only a bounded ranking bonus/penalty. It never bypasses PRIMARY execution safeguards and never uses same-day hindsight.

## Multify MFE and trailing

- Multify’s rolling 30-trading-day benchmark remains entry → maximum price before Multify Book Profit/close.
- Historical timestamped Multify calls continue progressive MFE backfill.
- Dynamic trail arm remains **max(0.50%, rolling Multify MFE average)** and remains cost-aware.
- The ₹0.25 high-water ratchet, adaptive ATR/price trail, non-loosening stop and emergency failsafe remain intact.
- Genuine Multify Book Profit remains independent and can close the app-owned Multify holding.

## Diagnostics

Exports additionally contain `signal_price_observations.json` and `forecast_research_snapshots.json`. The Forecast screen shows PRIMARY/SECONDARY/RESEARCH explicitly and disables BUY for non-PRIMARY candidates. The Execution screen reports foreground-engine health independently from notification-listener health.

Version: `6.9.0-production-learning` (`versionCode 690`).
