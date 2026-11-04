# Multify Trader Pro v8.0.4 — Fixed Speed & Candidate Watch TEST

## Deliverable
Android engineering TEST APK (versionCode 8040), based on verified v8.0.3.

### Implemented in the APK
- Increase Fixed full-NSE REST LTP transport lookahead **from 2 to 3 in-flight batches**. Preserve the exact 50-symbol REST request limit, original ordered response processing, current failure/coverage behavior, 1,200 ms minimum REST LTP permit spacing, 40/minute endpoint budget, and priority for existing holdings and broker protection. This can overlap network latency but CANNOT bypass the broker's quota floor.
- After a normal Fixed scan, read-only Fast Watch considers recent first-PRIMARY candidates and first market-pulse research candidates.
- Piggyback eligible monitored candidate symbols on the existing Fixed outcome LTP batch where possible (up to **50 total symbols**). When no Fixed recommendations are published, permit at most one candidate-only research LTP per minute, only when there are no managed holdings, trading actions or Fixed scan in progress, >=25 seconds before the next Fixed scan, >=12 live requests of headroom, and broker access is healthy.
- Record price samples separately from Fixed recommendations, with timestamps describing **client receipt**, not a broker exchange tick. Store up to five dates; export/restore as `fixed_fast_candidates.json`.
- Display Fixed Fast Watch on Fixed screen as a read-only research observation. No early BUY, no PRIMARY confirmation, no artificial official recommendation.
- Log REST discovery window and LTP rate-limit settings in Fixed completion telemetry.
- Extend optional Python Groww SDK stream **research only** to reject incomplete/stale coverage. The streaming probe has no authenticated live bridge to the Android APK and is not a production data source.

### Protected unchanged
- Frozen Fixed 40-factor model and exact 15-minute calculation.
- Frozen Fixed universe, market breadth, price ranking, confidence, target (1.1161043%), and repeat confirmation.
- Hard stop, trailing SELL/GTT protection and Multify BUY logic.
- Groww live-data permit budget and 429 safety behavior.
- App signing remains ephemeral debug TEST; an in-place update cannot be assumed between versions.

### What is **not** yet proven
- Full-market scan time reduction on a trading day.
- Improved entry timing, recommendation profitability or live execution safety.
- Groww Python SDK live feed suitable as the direct Android production scanner.

### Acceptance/benchmark for next trading session
- Compare median/p90 full-market discovery and detailed evaluation against v8.0.3.
- Confirm NSE coverage stays >=85%, batch errors and HTTP 429 do not increase, and protection LTP retains priority.
- Verify Fast Watch sampled only under spare broker capacity, and reports never become BUY signals.
- Keep all automatic buying OFF until broker fill -> protection GTT -> reconciliation is independently validated.
