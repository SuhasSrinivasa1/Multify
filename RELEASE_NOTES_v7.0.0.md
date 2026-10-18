# Multify Trader Pro v7.0.0 — Major Long Engine

## Production recommendation policy

- Long-only NSE CASH/CNC architecture remains mandatory.
- Maximum **five** forecast recommendations per trading day.
- Five is a hard ceiling, not a time-based quota. Every new candidate that satisfies PRIMARY production criteria is published on the next scanner pass until the five slots are full.
- SECONDARY and RESEARCH candidates are never presented as recommendations and can never place forecast orders; they remain in the learning ledger.
- Forecast target floor remains **1.00%** and continues to adapt from prior completed evidence without same-session hindsight.
- Production scans run every five minutes.

## Fixed stepped trailing — independent from Multify averages

Trailing no longer depends on Multify MFE or forecast averages.

- Entry +0.55% high-water → arm stop at entry +0.50%.
- Entry +0.65% high-water → stop at +0.60%.
- Entry +0.75% high-water → stop at +0.70%.
- Continue indefinitely in +0.10% protected-gain steps with a 0.05% trigger buffer.
- Stops never loosen.
- Existing pre-v7 holdings migrate conservatively and never lower an already-higher protected stop.
- Emergency loss failsafe remains independent.
- **Multify Book Profit/SELL notifications are learning-only in v7**. They close the Multify MFE measurement window but do not liquidate an app-owned holding. Profit exits are controlled by the fixed stepped trailing engine.
- Manual/external broker closes are still reconciled and recorded.

## Execution latency architecture

- The latency goal is **<100 ms app-side decision/notification → Groww order-dispatch preparation**. Broker/network/exchange ACK and fill latency are measured separately and cannot be guaranteed by the app.
- Multify BUY is the only notification type on the serialized latency-critical lane; Book Profit learning cannot queue ahead of a BUY.
- Groww token, settings and CNC buying-power state are pre-warmed every 20 seconds while ARM is active.
- BUY learning/audit writes run after the broker-dispatch path.
- Triggered trailing/failsafe SELLs dispatch before Room/audit persistence work.
- Active holding prices use Groww batch LTP, up to 50 symbols per request, and are sampled every 500 ms while holdings are open.
- Independent positions are evaluated concurrently and use per-position close locks.
- Stable order reference IDs and post-failure reconciliation remain in place for duplicate-order defense.
- Execution telemetry records app dispatch preparation, broker ACK and total fill time separately.

## 40-factor / 3-family Multify reverse engineering

The v7 research model uses exactly **40 observable market factors**, grouped into three fixed families:

1. **Momentum / Trend** — 14 factors.
2. **Participation / Liquidity** — 13 factors.
3. **Structure / Context** — 13 factors.

The factors include price/VWAP/EMA relationships and slopes, MACD/RSI state and acceleration, market structure, RVOL, volume acceleration and balance, spread, order-book imbalance, ATR capacity, volatility width, session positioning, breakout state, candle impulse, 52-week position, market-cap liquidity proxy, gap/range structure and strategy-vote consensus.

Market breadth changes the weights of the three families. The dominant family and family consensus contribute a bounded ranking adjustment; they never bypass PRIMARY safety criteria.

These are observable trading/market factors, not a claim that Multify's proprietary model or private accounting data is known.

## Five-session learning cohort

- A fresh v7 cohort begins **2026-10-19** and captures the next **five distinct trading sessions**.
- Up to 48 evaluated candidates per scan retain the complete feature snapshot, all 40 factor values/scores, three family scores, strategy votes, quality/rejection reasons and subsequent counterfactual MFE/MAE/close outcome.
- Later Multify calls mark earlier same-day snapshots of the same stock for direct comparison.
- After the fifth v7 session, new research-cohort capture stops automatically. Existing learned evidence remains available for analysis and bounded ranking support.

## Averages remain truthful and separate

- Multify headline average remains the rolling 30-trading-day MFE: recommendation entry → maximum price before Multify Book Profit/close.
- Forecast observed average remains based on published forecast full-session MFE.
- Neither average controls the v7 trailing ladder.

Version: `7.0.0-major-long-engine` (`versionCode 700`).
