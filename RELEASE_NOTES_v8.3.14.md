# Multify Trader Pro v8.3.14 — Independent Alpha/Beta API accounts, Fixed parity

Authoritative source lineage: v8.3.11 -> v8.3.13 PR #38 -> v8.3.14.
Unique package versionCode **8395**, versionName **8.3.14-independent-accounts-fixed-parity**. No experimental v8.3.12 anti-chase BUY veto is incorporated.

## Account correction
Groww publicly documents category ceilings: 10/s and 300/min LIVE_DATA, 10/s and 250/min ORDERS, 20/s and 500/min NON_TRADING for a Groww login. Alpha and Beta in this app are separately authenticated accounts, not two tokens of one account. Remove incorrect global 120 LIVE/min + 48 HIST/min cap, shared 429 pause, and secondary permits. The documented 600/min nominal aggregate is NOT a safe endpoint rate target: 429s were observed much lower for /ltp and /quote. Preserve conservative independent per-endpoint LTP caps: Alpha 40/min, Beta 35/min; existing account-level Alpha LIVE 210/min and Alpha order/holdings categories remain. Beta stays read-only.

## Broker transport improvements
- Isolate Beta 429 by category within Beta. Its live/historical cooldown never modifies Alpha's recovery gate.
- Beta cooldown uses 90s, 5m, 15m, 60m progression (matching existing Alpha policy), Retry-After, same-burst deduplication and one half-open probe. When Beta is unavailable, Alpha takes affected discovery batches only while Alpha remains ready with spare live-data headroom. Original Alpha /ltp pacing and Fixed API reservation remain.
- Eliminate the v8.3.13 issue where an Alpha/Beta account permit was consumed before a second, shared permit, which could reject locally and strand the first reservation.
- Per-sweep diagnostics count missing and invalid LTP symbols directly from the existing broker responses. This adds ZERO diagnostic market-data API calls.
- Replace misleading shared-budget dashboard with two independent account budget/recovery indicators.
- Correct Settings hard stop copy to -5%: unchanged HardStopLossPolicy remains at -5%, with the existing +0.55% arm, +0.50% protected gain and +0.10% ratchet.
- Fixed Auto Buy remains v8.3.11-based: only post-activation published ACTIVE PRIMARY recommendations; quote-before-order, own-symbol ownership, budget, spread, target, activation recency, and GTT safety still apply. Live cooldown does not retroactively veto a previously obtained fresh quote, while NON_TRADING and ORDERS readiness remain checked.

## Invariants
The frozen 40-factor Fixed calculation, confirmation, ranks, 15m candles, 107-call canonical reference, target calibration, price eligibility, and SELL/GTT formulas remain unchanged. CI compares immutable strategy source against the v8.3.4 commit e506db984f225a56852cf28a963ba4d463b62308 and checks explicitly allowed infrastructure-only differences. Frozen snapshot ID FIXED-V741-20261021-190239-FINAL.

## Limitations / deployment warnings
- No guarantee of full 100% NSE **valid-priced** coverage; missing LTPs on successful full sweeps were ~208-210 symbols out of 2,645. The app now classifies omissions using the existing response data, without blindly issuing more API calls.
- No cross-scan mixed-age cache or forced extra retries: preserving fresh Fixed selection inputs is more important than manufacturing coverage.
- Broker endpoint limits and an upstream limit may differ from documented per-account category caps. If live 429s persist, capture per-account 429 headers and stop short of unsafe escalation.
- CI can verify source parity, tests, compilation and APK signature. It cannot prove speed improvement or live order/GTT execution without a real trading session.
- The APK is NOT production-upgrade-signed unless GitHub Actions has the original approved stable signing secrets. A different debug signature cannot in-place update an existing installation. Do not uninstall while managing live positions or unexported trade state.

## Acceptance
Verify Alpha 429 does not affect Beta, Beta 429 does not affect Alpha, no permit leaks, no uncontrolled fallback storms, 107-call source parity, unchanged Fixed selection/trailing/stop/target rules, safe order and broker-owned duplicate checks, fresh-quote validation and GTT after actual fills. Compare 3+ matched trading days for median and p90 scan duration, 429 counts, valid-priced NSE coverage, candidate-to-BUY delivery and broker GTT confirmations.
