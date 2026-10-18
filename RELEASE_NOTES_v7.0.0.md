# Multify Trader Pro v7.0.0 — Major Production

## Trading architecture

- LONG-only NSE CASH/CNC application. There is no intraday or short-selling mode.
- Production responsibilities are split into three independent loops:
  - 1-second app-owned holding monitor using one batched Groww LTP request for up to 50 holdings.
  - 10-second execution hot-path maintainer so authentication and CNC buying power stay pre-warmed.
  - lower-priority forecast/research loop so scanning can never block holding exits or hot-path maintenance.
- BUY and staircase/failsafe SELL paths target **<100 ms app-side trigger/notification to Groww order POST start** when the hot path is available.
- Broker ACK and exchange fill time are measured separately and are not represented as app-controlled latency.
- Synchronous audit-file writes were moved out of the order-dispatch critical path.
- Normal trailing SELLs use a recently refreshed broker-ownership cache rather than waiting for a new broker reconciliation network call before POSTing the order. Stable order references and local ownership checks remain in place.

## Fixed independent trailing

Trailing no longer depends on Multify MFE, forecast average, ATR, or same-day performance.

- High-water below +0.55%: no profit trail is armed.
- High-water reaches +0.55%: lock +0.50%.
- High-water reaches +0.65%: lock +0.60%.
- High-water reaches +0.75%: lock +0.70%.
- Every additional +0.10% high-water step increases the locked-profit stop by +0.10%.
- Profit stops never loosen.
- The emergency downside failsafe remains a separate capital-protection exception.
- Multify Book Profit is **learning-only** in v7. It records Multify exit/MFE evidence but does not sell an app-owned holding.
- There is no automatic end-of-day flatten.

## Forecast recommendations

- Maximum **five** published recommendations per trading day.
- There is no hourly or time-based quota. Every PRIMARY candidate that meets production criteria can be published immediately until five have been published.
- Weak SECONDARY/RESEARCH candidates remain research evidence only; they are not presented as production recommendations and cannot place a forecast BUY.
- Forecast target floor remains at least 1.00%.
- Multify 30-trading-day MFE average and forecast full-session MFE remain visible analytics. Neither controls trailing.

## Five-session Multify reverse engineering

v7 starts a new fixed cohort: `V7_FIVE_SESSION_40X3`. Old v6.9 research sessions do not consume the five-session window.

Every researched candidate captures **exactly 40 pre-call pointers** and is classified into **exactly three fixed strategy families**:

1. `MOMENTUM_CONTINUATION`
2. `BREAKOUT_EXPANSION`
3. `PULLBACK_RECLAIM`

The 40 pointers cover price location, spread/order-book state, day/52-week context, VWAP/EMA distances and slopes, MACD/RSI, volume/RVOL, candle structure, ATR/volatility, Donchian/opening ranges, wick/body/gap structure, and prior-candle relationships.

For five distinct trading sessions the app stores candidates, family scores, quality decisions, strategy votes, Multify matches, and reconstructed post-scan MFE/MAE/close/target outcomes. Prior completed evidence can add a bounded ranking bonus but can never bypass PRIMARY production safeguards.

## Version

- Version name: `7.0.0-major-production`
- Version code: `700`
