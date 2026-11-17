# v9.0.8 Fixed scan timing guardrail — TEST / observe-only

## Evidence reviewed

Three cumulative v9.0.6 diagnostic ZIP exports from 17 Nov 2026 (09:24,
09:48 and 10:18 IST). The 10:18 export contains 37 `FIXED_SCAN_PROFILE`
records and 15 Fixed PRIMARY recommendation records.

### Target-hit observations

Four of the 15 recommendations were marked `TARGET_HIT` by 10:18. The
first sampled full-target observations were:

| Symbol | Recommendation scan start | First sampled target observation | Delay |
|---|---:|---:|---:|
| SPORTKING | 09:37:40 | 09:40:31 | 2.9 min |
| BANCOINDIA | 09:34:39 | 09:42:01 | 7.4 min |
| RAYMONDREL | 09:36:09 | 09:48:59 | 12.8 min |
| INOXINDIA | 09:33:08 | 09:53:12 | 20.1 min |

Median delay is approximately 10.1 minutes. These are sampled LTP observations,
not exact exchange crossing timestamps, intrabar highs, actual fills or P&L.

The 15-minute horizon had 11 matured events and 3 full-target hits (27.3%).
The 30-minute horizon had only 8 matured events and 4 full-target hits (50%).
Seven of 15 were still immature at 30 minutes. This is not a final session
hit rate; no events had matured to 60 minutes by export time.

### Does scan runtime predict a target hit?

No useful relationship is demonstrated:

- Four hit-producing recommendation scans: 31.8–35.3 seconds; median 32.4.
- Four matured non-hit scans: 30.8–35.3 seconds; median 31.3.

A 30–38 second scan is not a success predictor. A runtime-only BUY veto would
reject winners and non-winners alike.

### Scan degradation and broker throttling

| Window (IST) | Profiles | Median elapsed | P90 |
|---|---:|---:|---:|
| 09:15–09:30 | 10 | 30.9 s | 74.4 s |
| 09:30–09:45 | 10 | 32.1 s | 35.6 s |
| 09:45–10:18 | 17 | 101.2 s | 142.2 s |

Alpha LIVE_DATA HTTP 429s occurred at 09:45:33, 09:50:10 and 10:05:34.
Later scans were slower, but later recommendations had not matured enough for
a fair target-outcome comparison.

## Guardrail introduced

Diagnostic-only classifications:
- Scan elapsed budget: 45 seconds. This is above the 38.1-second maximum
  observed after startup warm-up in the healthy 09:18–09:45 pre-429 sample.
- Scan start-spacing budget: 105 seconds (90-second target + 15-second tolerance).
- IST buckets: PRE_MARKET, OPENING_0915_0930, EARLY_0930_0945,
  MIDMORNING_0945_1100, MIDDAY_1100_1400, LATE_1400_1530, AFTER_MARKET.
- Each scan profile records elapsed/spacing status, time bucket and 429 state.

Statuses: `NORMAL`, `SLOW_SCAN`, `CADENCE_DRIFT`,
`BROKER_RATE_LIMITED`, `BASELINE_NO_SPACING`.

## Safety boundary

This guardrail does not change Fixed scoring, candles, ranking, PRIMARY
confirmation, publication, Auto Buy, BUY/SELL/GTT, the 90-second cadence or
broker cooldown handling. It does not veto production trades. Existing
recommendation freshness and fresh Alpha execution quote checks remain
authoritative.

Do not convert the 09:30–09:40 observation cluster into a time-of-day trading
filter from one partial session. Collect multiple complete sessions and
matched matured outcomes first.
