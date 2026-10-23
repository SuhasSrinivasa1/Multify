# Multify Trader Pro v7.6.7 — Direct Auto Buy

- Keeps the frozen v7.4.1 Fixed recommendation model, calibration, gates and API-budget policy unchanged.
- Reduces the Fixed scan minimum interval from 5 minutes to 2.5 minutes while retaining the existing non-overlapping Fixed worker.
- Replaces new Fixed Auto-buy entry GTTs with direct Groww CNC MARKET BUY orders.
- Processes every eligible Fixed PRIMARY recommendation independently, so multiple recommendations in one scan can each execute subject to ARM, safety and available-cash checks.
- Uses the fresh quote for sizing, then stores the actual broker fill as the managed holding entry.
- Anchors the live execution target to the actual fill using the same frozen recommendation target percentage; the published recommendation itself remains unchanged.
- Reconciles/cancels legacy v7.6.6 Fixed entry GTTs before direct execution to prevent duplicate buys.
- Leaves the existing stepped sell/trailing architecture unchanged.
- Requests Android large-heap headroom for high-memory phones without increasing Groww request concurrency beyond the existing four-way cap.

Direct MARKET execution removes the delayed-entry behavior of an UP-trigger GTT, but it cannot guarantee the recommendation price in a fast-moving market; normal market-order slippage remains possible.
