# Multify v8.3.26 — Scan-Latency and Real 5-Minute Candle Shadow

## Production invariants
Fixed LIVE production remains on completed 15-minute candles.
NO slow-scan veto, extra stock filter, changed 40-factor formula,
new daily recommendation limit, changed confirmation, order, SELL,
GTT or trailing safety gate. Original 90-second scan schedule,
28 dynamic discovery slots and 48 detailed candidates are intact.
All prior branches (including v8.3.16, both v8.3.19 and v8.3.25)
remain untouched.

## New observable scan cohorts
Each completed official scan emits FIXED_LATENCY_SHADOW /
SCAN_COHORT_V8326 in exported system_audit.jsonl:
- <=90 seconds FAST; 90 to <120 INTERMEDIATE; >=120 SLOW.
- New recommendation IDs, symbols and counts per completed scan.
- Accumulated Live + Historical governor wait (sum across
  concurrent calls, NOT the real elapsed time).
- Client reference quote capture to scan-ready delay or null.
  Not an exchange quote age / exchange timestamp.
- Warning is research-only. No publication or Auto Buy veto.
Post-notification target touch must be joined to existing sampled
recommendation outcome telemetry; do not label an immature or
unobserved event as a failure.

## Why previous live 5-minute shadow data was empty
The 10:11 AM v8.3.25 log showed 0 paired 5m samples.
The optional after-scan fetch was skipped due to pending official
full-market prefetch, insufficient time before next Fixed scan, and
conservative post-429 quiet-period safeguards.

## v8.3.26 real historical 5-minute comparisons
- Keep the existing tightly guarded in-session attempt.
- Add a 15:35 to 18:00 IST after-market capture window on weekdays,
  when no Fixed market scan is scheduled; one Alpha historical
  five-minute request per successful sample, at most one symbol
  per sample and minimum 12 minutes between successful samples.
  Separate scheduling retry attempts are throttled to 3 minutes.
- Historical 5m capture is STILL skipped if official Fixed
  prefetch, protected trade, API cool-down, Alpha rate limits or
  historical budget protection forbids it. No guaranteed 5m
  pairs when broker API or in-memory reference data is absent.
- Prefer previously recommended symbols, then other evaluated
  symbols; exclude exact reference pairs already sampled today.
- Reuse the original in-memory 15m evaluated quote. Five-minute
  feature calculations explicitly exclude all bars not fully
  completed at the original quote timestamp (prevent lookahead).
- At post-market time, genuine completed 5m candle highs/lows
  from AFTER the quote provide 15m and 60m counterfactual
  target-touch and 2% adverse-touch observations. Immature
  or missing follow-up data is CENSORED, not recorded as losses.
- If target and risk are touched within the SAME five-minute
  bar, ordering is unknown and target-before-risk is false.
- Add retrospective fields to fixed_5m_shadow_pairs.json and
  per-group counts in fixed_5m_shadow_comparison_summary.json.

## Limits on inference
The 15m and 5m variants initially share the SAME reference quote
and same forward OHLC price path. Their groups can measure how
often 5m versus 15m label PRIMARY prior to target touches,
NOT whether earlier live 5m entry would improve realized profits.
For a true earlier-entry comparison, timestamped prospective
signals, chronological candles and brokerage cost replay are needed.
A future production switch needs out-of-sample evidence.

This test branch does NOT claim faster scan completion or
improved accuracy. Compare median/p90 scan duration,
notification delay, full-market coverage, broker 429s and
target rates against the v8.3.19/v8.3.25 baseline before adoption.
