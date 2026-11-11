# Multify Trader Pro v8.3.18 — Export & Research Stability

Base: v8.3.17 (source commit cbc6bc3de71efb64243d101f60150c99130a7936).

## Changes made in v8.3.18

- Large diagnostic ZIP exports run on Dispatchers.IO, away from the Android main UI thread.
- Gson writes each JSON file directly into the ZIP stream instead of constructing a huge intermediate JSON string.
- Decision evidence and system audit history stream from disk rather than being concatenated into memory.
- Export UI reports the current file and the number of completed entries.
- Final ZIP contains export_manifest.json and a corrected file count.
- Fixed notification audit reports read-only quote-to-notification time buckets, so after-hours replay can compare outcomes.
- Session-candle metadata correctly labels v8.3.17's session-only verified-completed-bar calculations.
- No new recommendation thresholds, hide rules, BUY filters, targets, SELL or GTT changes.

## v8.3.16 versus v8.3.17 — do not confuse source lock with identical recommendations

v8.3.17 changed Fixed candle input semantics (session VWAP, opening range and completed candles), adjusted the overbought RSI vote, and treats unavailable spread as unverified. It also reduces some Beta-to-Alpha fallback traffic and increases LTP pacing gaps. Therefore v8.3.17 may publish or execute a different set of recommendations than v8.3.16 even though the protected family/target/trailing contract stays unchanged. The current release does NOT assert exact v8.3.16 decision parity.

## Operational caution

This is a diagnostic/test build. A different debug signing certificate cannot install in-place over the previously installed application. Never uninstall an existing trading installation with unexported learning records, active holdings, or unreconciled protective GTTs. Live order, export, performance, and parity tests remain required before unattended automatic buying.
