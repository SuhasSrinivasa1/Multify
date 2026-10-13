# Multify Trader Pro v6.5.0

- Records Multify Book Profit returns independently from Groww SELL execution; the reported Multify return is authoritative for the rolling 30-day average.
- Repairs previously captured LIVE Buy/Book Profit learning from stored Multify notifications on startup, including PGIL-style comma-formatted prices.
- Uses the midpoint of Multify entry ranges for Multify benchmark learning; broker fills remain separate execution/P&L data.
- Reconciles app-owned CNC positions against Groww holdings/positions every ~5 seconds and marks manual/external full or partial exits without sending a duplicate SELL.
- Continuous adaptive trailing evaluates each ₹0.25 high-water improvement, never loosens, raises the stop by at least 50% of each new high-water move, and tightens as gains expand.
- Active holding cards expose high-water, current trail stop, trail gap and last ratchet.
- Comma-formatted prices (for example ₹1,355) parse correctly.
- Blank package filter now defaults to the official Multify package (`com.multyfi.invest`).
- Critical processing failures are persisted on the signal and exported; complete exports now include `system_audit.jsonl`.
- LONG holding monitoring remains active through the regular NSE session until 15:30 IST.
