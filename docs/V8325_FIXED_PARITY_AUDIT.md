# Multify v8.3.25 — v8.3.16 / both v8.3.19 selection and latency audit

## Priority: no less selection, no new veto, no slower production delivery

This release branched from v8.3.24. v8.3.16, BOTH v8.3.19
branches, v8.3.23, v8.3.24 and main remain untouched.

### Verified source facts

- The 40-factor scoring, Fixed contract, market ranking function, PRIMARY
  publication policy, target/stop/GTT protection files are unchanged from v8.3.19.
- In v8.3.21–24 a recent PRIMARY carry-forward list was prepended ahead
  of the original 48-candidate production universe. Up to five follow-ups
  could displace originally ranked symbols. v8.3.25 restores exactly the
  v8.3.19 production Fixed candidate universe, ranking and publication
  function, plus its market discovery input, byte-for-byte.
- The extra discovery-level ₹200–₹5,000 prefilter from v8.3.21 is removed
  so the entire original ranked candidate list is reconstructed. The
  EXISTING ₹200–₹5,000 publication and execution price eligibility is
  unchanged, not newly introduced.
- The v8.3.22 in-scan diagnostic computations were removed from the
  critical Fixed scoring path as part of the exact restoration; offline
  replay and the independently scheduled 5m research feature remain.
- The 90-second scan interval, 28 dynamic discovery slots and 48
  detailed candidates match v8.3.16/v8.3.19. No daily publication cap
  was added (original repeat-event and PRIMARY confirmation rules remain).
- v8.3.16 is NOT byte-identical to v8.3.19 on inputs and filtering:
  v8.3.17 began requiring a known valid spread, and corrected completed
  15-minute candle timestamp, VWAP, and session inputs. Because v8.3.19
  already has these changes, you cannot simultaneously reproduce both
  v8.3.16 and v8.3.19 decisions on incomplete quotes. This release keeps
  v8.3.19 for conservative price-data validation.

### Performance caveat

Exact source parity protects what gets considered on IDENTICAL data.
It does NOT guarantee market-data arrival time, quote availability,
actual recommendation counts, or faster scans. Source verification
and unit tests cannot prove an Android/Alpha/Beta live-session speed
gain. Use raw per-account request/429 logs and matched-day benchmarks
before claiming production speed improvement.

Acceptance: same or better completed market coverage; no additional
synthetic symbol exclusions; same Fixed ranking/publication on identical
inputs; no extra Fixed scan API request rate; median/p90 and
notification delivery latency no worse than reference with comparable
market conditions. If the benchmark fails, do not promote this build.

The 5-minute timing strategy remains shadow-only, not trading authority.
This TEST APK must be kept OFF for live automatic BUY until broker
orders/GTT and signing compatibility are verified.
