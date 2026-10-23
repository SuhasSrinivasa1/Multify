# Multify Trader Pro v7.7.0 — Market Priority Reliability

## Non-negotiable invariants
- Fixed remains the byte-for-byte protected v7.4.1 reference contract.
- Fixed still scans every 2.5 minutes and keeps the v7.6.9 hot-cache / eight-way high-end acceleration.
- The shared stepped trailing architecture is unchanged for Multify, Fixed and Forecast holdings: arm +0.55%, protect +0.50%, ratchet +0.10%.
- Immediate Multify BUY processing remains on the serialized latency-critical listener path.

## Market-hours priority order
1. Managed-position / trailing SELL protection.
2. Immediate Multify trade execution.
3. Fixed scan and Fixed direct market auto-buy.
4. Forecast only from spare API capacity.

Forecast yields if Fixed requests priority, a Multify trade is in flight, the next Fixed scan is within 45 seconds, or Live/Historical/Non-Trading API utilization is above conservative Forecast thresholds.

## Five-session Forecast calibration
- v7.7.0 starts a fresh release-specific calibration counter.
- The first five completed live trading sessions are shadow-learning only: no live Forecast publication or Forecast auto-buy.
- A session counts only after at least five Forecast research rows are completed after market close.
- After session 5, live Forecast publication can unlock under the same existing PRIMARY quality/publication gates.
- Positive prior Fixed outcomes remain the main one-way transfer signal.
- Positive Multify outcomes add only a smaller assistance/ranking bonus.
- Negative/flat Fixed and Multify outcomes never become ranking penalties.
- Fixed never reads Forecast feedback.

## Market-hours API cleanup
- Forecast outcome polling, legacy entry-order reconciliation and Multify post-signal research traffic are removed from the market-hours maintenance lane.
- Multify MFE learning is backfilled after market close from historical data.
- Heavy research and legacy reconciliation run after market hours.
- ARM hot-state maintenance remains live because it reduces latency for sudden Multify BUY calls.

## Recovery and durability
- The service marks broker recovery pending at startup and new BUYs are blocked until the first successful managed-position / broker reconciliation pass completes.
- Trailing/position monitoring starts immediately and remains independent.
- A boot/package-replacement receiver restarts the monitoring foreground service.
- Room destructive fallback is removed. A missing future migration fails rather than silently deleting local trading state.
- Room schema export is enabled for migration auditing.
- Runtime release invariants verify the frozen Fixed contract and the shared trailing constants at service start; CI remains the authoritative source-hash lock.

## Signing
The workflow still uses the existing stable-signing-secret mechanism when configured. A permanent signing key cannot be created safely in source code; if the GitHub signing secret is absent, CI falls back to a runner debug key as before.
