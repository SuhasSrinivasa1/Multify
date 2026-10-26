# Fixed v7.8.6 scheduling-only migration

The original `FIXED_FINAL_LOCK.gitsha` stays unchanged. Only the sequential LTP request dispatch block inside `TradingRepository.kt` was replaced with a two-request bounded scheduler. Validation restores the exact old block in memory and checks the entire protected coordinator matches the frozen reference byte-for-byte. All model source locks and the embedded 107-call historical reference remain unchanged.

The new scheduler preserves original batch index ordering, result mapping, failure/abort rules, complete 2629-symbol discovery scope, 48-stock detailed evaluation and 150-second interval. Live market snapshots may change when fetched at different times even with identical model logic; throughput improvements are not guaranteed.
