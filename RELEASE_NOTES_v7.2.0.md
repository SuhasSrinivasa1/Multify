# Multify Trader Pro v7.2.0 — Market-Wide Forecast

## Why v7.1 produced zero recommendations on 2026-10-19

The exported v7.1 logs showed the forecast engine itself was alive: 18 scans and 856 research snapshots were produced while ARM was OFF. The failure was candidate promotion and coverage, not trading-state gating.

Two root causes were identified:

1. **Weak market breadth was a hard PRIMARY veto.** Every scan was classified `BREADTH_WEAK`. Strong stocks such as KARURVYSYA reached ~0.92 confidence, 1.00 directional score and multi-x RVOL but were still demoted solely because the broader scanned basket was weak.
2. **The expensive forecast universe was only ~48 names out of a 2,605-stock NSE master.** SPORTKING, the actual Multify call that morning, never entered the pre-call forecast universe at all.

## Market-wide two-stage discovery

- Every production forecast cycle first sweeps the full NSE equity master with Groww batch LTP (up to 50 symbols/request).
- The sweep is paced below Groww Live Data per-second limits.
- The first scan establishes session baselines; subsequent scans rank the strongest live session/pulse movers.
- Up to 28 live discovery names receive first priority for the expensive quote + four-day 15-minute candles + 40-factor evaluation.
- Historical Multify-frequency/DNA names and a smaller rotating fallback set fill the remaining detailed-evaluation slots.
- Detailed evaluation remains capped at 48 names per scan to control API load and latency.
- Audit logs include full NSE master size, LTP coverage, batch errors, discovery candidates, detailed-universe size and top market-wide movers.

## Weak breadth is context, not a global veto

- `BREADTH_WEAK` no longer automatically prevents every PRIMARY recommendation.
- In a weak market, a stock can still become PRIMARY when it is a true counter-breadth leader: at least +0.35% day strength, confidence >=0.68 and directional score >=0.35, while still passing the normal TREND, VWAP/EMA, RVOL, spread, structure and volatility-capacity rules.
- Weak stocks in a weak market remain SECONDARY/RESEARCH.
- Market breadth still changes factor-family weights; it simply no longer blinds the entire long scanner.

## Forecasting remains independent from trading

- ARM OFF disables real orders only.
- Forecast scans, market-wide discovery, research capture, Multify reverse engineering and outcome learning continue normally.
- The Forecast screen explicitly shows `Forecast engine · ACTIVE` and explains that ARM OFF does not stop learning/recommendations.
- Scan audit records `forecasting_independent_of_arm=true` and the contemporaneous ARM state.

## Existing v7.1 protections remain unchanged

- Long-only CNC.
- No downward averaging.
- No downside-loss sell.
- One authoritative trailing/recovery GTT per holding.
- Positive-close-only forecast learning.
- Maximum five production recommendations per day; five is a ceiling, not a quota.

Version: `7.2.0-marketwide-forecast` (`versionCode 720`).
