# Multify Trader Pro v8.2.0 — FAST FIXED ONLY (TEST)

## Baseline
Reverts the active v8.1 Early Conviction/BUY IDEA direction. Uses the proven v8.0.7
GTT safety baseline, with byte-locked v7.4.1 Fixed 40-factor model and the
original v8.0.0 15-minute evaluation and target. The independently authorised
v8.0.6 repeat-event qualification remains.

## Focused changes
1. **Remove active Early/Fast Watch/Market Pulse research.**
   No early BUY idea notifications, no after-scan Early Watch/Pulse research tasks,
   no research card on the main Fixed screen. No candidate-only LTP polling or
   piggybacked Fast Watch candidates from the Fixed outcome worker.
   Only **published Fixed** stocks retain their existing outcome LTP monitoring.
   Historical diagnostic files are kept for migration/analysis, not active sampling.
2. **Pipeline full-NSE Fixed discovery.**
   After a completed official Fixed scan and order path, prepare the NEXT 50-stock
   LTP full-market sweep between scans. The next official scan consumes this
   once if the same account, same NSE master, same IST date, at least 85% NSE
   coverage and no more than 45 seconds old. If the sweep is incomplete,
   failed, stale, or unavailable, use the original full blocking scan.
   If preparation is still underway, wait for that one sweep instead of
   launching a duplicate full-market burst.
3. **Keep broker pacing untouched.**
   Groww's published LTP cap is 50 stocks per request. The app retains the
   existing 1.2s Alpha LTP endpoint gap, 40/min conservative policy,
   original 53-batch minimum for ~2630 stocks, reserve for GTT/holding
   protection and broker 429 cooldown. No speed-up by raising broker rate.
4. **Give Fixed priority over optional Forecast.**
   Forecast background scanning yields while the Fixed full-market sweep
   is being prepared. The post-Fixed opportunistic Forecast task is not
   launched immediately after Fixed. It resumes when research capacity
   becomes available. Forecast scoring is not altered.

## What did NOT change
- Frozen Fixed 40-factor/15-minute strategy, price-band universe,
  NSE discovery ranking, original confirmation, original target
  (1.1161043%) and recommendations remain intact.
- Independent same-stock confirmed requalification from v8.0.6 is intact.
- All broker GTT recovery, hard stop, trailing policy, ownership and
  anti-duplicate BUY/SELL safeguards from v8.0.7 remain intact.
- BUY still uses a fresh execution quote and MARKET order; the displayed
  historical signal price is not a guaranteed fill price.
- This does not guarantee improved profitability or claim to have solved
  Tonbo's earlier market-data timing discrepancy.

## Expected outcome / measurement
The original full NSE discovery takes around 75–77 seconds because of
the 53 LTP requests at conservative pacing. In a successful prepared
cycle, most of that work is performed before the official scan starts.
The first scan after startup is still normal-speed. The new logs contain:
- FIXED_PERFORMANCE/NEXT_FULL_NSE_SWEEP_PREPARED
- FIXED_PERFORMANCE/PREFETCHED_SWEEP_CONSUMED
- FIXED_PERFORMANCE/PREFETCH_INVALID_NORMAL_SCAN_FALLBACK
- RUNTIME/FIXED_SCAN_PROFILE

Actual speed, freshness, broker 429 frequency, recommendation count and
repeated-stock qualification must be validated with a live session export.
If Groww throttles or the prepared result becomes stale, the fallback
may take as long as the existing v8 scanner and some duplicate network
requests can occur after a failed preparation. There is no guaranteed
fixed-scan time reduction.

## Upgrade safety
TEST APK. Because ephemeral debug APK signing identities vary, Android
may reject in-place upgrade. Never uninstall while app-managed holdings,
active broker GTTs or unexported local history exist. Verify open broker
positions and sell GTTs directly in Groww and export app history first.
Keep Fixed Auto Buy disabled until complete broker-side GTT lifecycle
and timing validation is successful.
