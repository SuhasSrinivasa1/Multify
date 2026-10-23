# Multify Trader Pro v7.6.8 — API Governed Positive Transfer

- Frozen Fixed v7.4.1 source-lock region remains byte-for-byte unchanged.
- Fixed and Forecast auto-buy both use immediate CNC MARKET orders.
- Legacy entry GTTs are migrated by the maintenance loop, not on the normal BUY hot path.
- Deterministic order references recover ambiguous order submissions through order-status-by-reference.
- Process-wide Groww category governor caps Live Data at 7/sec and 210/min, Orders at 6/sec and 150/min, Non Trading at 12/sec and 360/min, Authentication at 3/sec and 20/min, and Historical at 4/sec and 180/min.
- Active holding market-data monitoring runs once per second using batched LTP.
- Fixed has scan-cycle priority. Forecast defaults ON but runs opportunistically and yields when Fixed requests the API cycle.
- Forecast learns one-way from prior positive Fixed outcomes only. Negative/flat Fixed outcomes add no ranking bonus.
- Positive Fixed transfer augments Forecast ranking; it cannot bypass Forecast quality/publication gates and can never alter Fixed.
