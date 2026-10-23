# Multify Trader Pro v7.7.1 — Deep Telemetry

## Trading logic unchanged
- Fixed remains the byte-for-byte protected v7.4.1 reference contract.
- The shared stepped trailing policy is unchanged: arm +0.55%, protect +0.50%, ratchet +0.10%.
- Multify immediate execution, Fixed 2.5-minute priority and Forecast spare-capacity scheduling remain as v7.7.0.
- No new market-hours research or reconciliation work is introduced.

## Rate-limit forensics
- Every Groww HTTP 429 is logged with exact wall-clock timestamp/UTC timestamp, method, path, API category, network latency, Retry-After value, applied cooldown, rolling 1-second/60-second usage, app/hard limits, cumulative category 429s and governor pressure.
- Preventive local governor waits are separately accumulated. Significant waits (>=500 ms) are logged with endpoint/category/reason so broker throttling can be distinguished from app-side safety pacing.
- The rolling 30-second API budget log now includes last-429 timestamp, governor wait count/total/max, cumulative network time and asynchronous-audit drop count.

## Performance telemetry
- Fixed scan profile: total elapsed time, request counts, 429 deltas, governor wait time and cache hits.
- Forecast scan profile: total elapsed time, request counts, 429 deltas, governor wait time and cache hits.
- Trailing monitor profile: 30-second aggregated pass count, failures, average/max pass latency, API request counts, 429 deltas and governor wait time.
- Multify pipeline profile: notification post -> receipt -> process start -> completion, request counts, 429 deltas and governor wait time.
- Existing broker ORDER_ACK and ORDER_FILLED timing records remain and now use asynchronous logging.

## Zero critical-path disk I/O for new telemetry
- New telemetry uses a bounded single-thread low-priority asynchronous audit writer.
- Fixed/Forecast/Multify/trailing network paths enqueue telemetry and continue; JSON serialization and file I/O happen off the trading path.
- Audit rotation expanded to 12 MB per file (current + one rotated file).
- User-initiated export drains pending telemetry before reading the audit log.

## Export
Diagnostics now also include:
- api_usage_snapshot.json
- telemetry_metadata.json
- system_audit.jsonl
- existing execution_samples.json, signals, holdings, Fixed/Forecast research and learning data.

This release is intended to make future API, latency and Forecast improvements evidence-driven without slowing the live trading path.
