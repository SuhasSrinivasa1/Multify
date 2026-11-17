# v9.0.8 Fixed scan timing guardrail — TEST / observe-only

## Evidence reviewed

Source: three cumulative v9.0.6 diagnostic ZIP exports from 17 Nov 2026
(09:24, 09:48 and 10:18 IST). The 10:18 export contains 37
`FIXED_SCAN_PROFILE` records and 15 recommendation records.

### Target-hit observations

Four of the 15 published PRIMARY recommendation records were marked
`TARGET_HIT` by the 10:18 export. The sampled maturity report records:

| Symbol | Recommendation scan start | First sampled full-target observation | Delay from scan start |
|---|---:|---:|---:|
| SPORTKING | 09:37:40 | 09:40:31 | 2.9 min |
| BANCOINDIA | 09:34:39 | 09:42:01 | 7.4 min |
| RAYMONDREL | 09:36:09 | 09:48:59 | 12.8 min |
| INOXINDIA | 09:33:08 | 09:53:12 | 20.1 min |

Median delay among these four first sampled full-target observations is
approximately 10.1 minutes. These are sampled LTP observations, not exact
exchange crossing timestamps, intrabar highs, actual fills or realized P&L.

The 15-minute horizon had 11 matured events and 3 full-target hits (27.3%).
The 30-minute horizon had only 8 matured events and 4 full-target hits (50%).
Seven of the 15 events were still immature at 30 minutes. The 50% figure
must not be represented as 4/15 or treated as a final session hit rate.
No events had matured to the 60-minute horizon by the 10:18 export.

### Does scan runtime predict a target hit?

No useful relationship is demonstrated by this small sample:

- The four hit-producing recommendation scans took 31.8–35.3 seconds
  (median 32.4 seconds).
- The four matured non-hit recommendation scans took 30.8–35.3 seconds
  (median 31.3 seconds).

A 30–38 second scan is therefore not a success predictor. A runtime-only
BUY veto would reject good and bad candidates alike.

### API/latency degradation

| Window (IST) | Scan profiles | Median elapsed | P90 elapsed |
|---|---:|---:|---:|
| 09:15–09:30 | 10 | 30.9 s | 74.4 s |
| 09:30–09:45 | 10 | 32.1 s | 35.6 s |
| 09:45–10:18 | 17 | 101.2 s | 142.2 s |

The first Alpha LIVE_DATA HTTP 429 was recorded at 09:45:33, followed by
429s at 09:50:10 and 10:05:34. The later slow scans are consistent with
broker cooldown/degraded throughput. This does not establish that slow
scans caused weaker target outcomes; later candidates were not mature
enough to compare fairly.

## Guardrail introduced

The guardrail is deliberately diagnostic only:

- Scan elapsed budget: 45 seconds. This is above the 38.1-second maximum
  observed in the post-startup-warmup, pre-429 09:18–09:45 scan sample.
- Scan start-spacing budget: 105 seconds (90-second target + 15-second
  tolerance).
- IST time-of-day buckets: opening 09:15–09:30, early 09:30–09:45,
  midmorning 09:45–11:00, midday 11:00–14:00, late 14:00–15:30,
  pre-market and after-market.
- Each official scan profile records elapsed/spacing health, time bucket,
  and whether an Alpha/Beta LIVE or historical 429 occurred.

Status values:
- `NORMAL`: elapsed and known start spacing within budget; no 429.
- `SLOW_SCAN`: elapsed exceeds 45 seconds.
- `CADENCE_DRIFT`: start spacing exceeds 105 seconds.
- `BROKER_RATE_LIMITED`: at least one LIVE or historical 429.
- `BASELINE_NO_SPACING`: no prior successful scan is available to measure
  start-to-start spacing.

When multiple conditions occur, broker rate limiting has display priority;
the individual boolean fields preserve the other conditions.

## Non-negotiable safety boundary

This guardrail does **not**:
- change the Fixed 40-factor model, 15-minute candles, candidate selection,
  PRIMARY confirmation, publication, target or risk parameters;
- suppress or approve recommendations based on time of day or scan duration;
- change the official 90-second scan target;
- create a new BUY/SELL/GTT rule or bypass broker cooldowns;
- treat sampled target touches as realized trade profit.

Existing recommendation freshness and fresh Alpha execution quote checks
remain the execution safeguards. The new telemetry exists to accumulate
multiple sessions of scan-time versus outcome evidence before any future
production timing rule is considered.

## Acceptance for a future production timing rule

Do not convert the 09:30–09:40 observation cluster into a time-of-day
trading filter based on one partial session. Collect multiple complete
sessions, include candidates that did not qualify as well as PRIMARY
candidates, and compare 15/30/60-minute target and adverse-move outcomes
with matched market conditions. Require enough matured events per bucket
and report uncertainty before considering a separate, explicitly approved
trading rule.
