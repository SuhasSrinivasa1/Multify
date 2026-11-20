# Multify Trader Pro v9.0.21 — Working Range Alpha Auto-Buy (TEST)

## Source log review

Input: `MultifyTraderPro-logs-v9.0.20-working-range-debug-20261120-102011.zip`.

### Working Range observations in the 10:20 IST export

| Symbol | First match | Confirmed | Exported state | Sampled MFE | Sampled MAE | Interpretation |
|---|---:|---:|---|---:|---:|---|
| ALICON | 09:36:04 | 09:37:44 | TARGET_HIT_SAMPLED | +4.03% | 0.00% | Sampled original target touch at 09:41:03 |
| KALPATARU | 09:46:53 | 09:48:44 | MOMENTUM_WEAKENING | 0.00% | -1.84% | Material adverse example; not a stop touch |
| NUVOCO | 10:01:03 | 10:02:33 | IN_RANGE | +0.49% | -0.81% | Unresolved/censored; no full target touch |
| SGMART | 10:04:09 | 10:05:39 | IN_RANGE | +0.41% | -0.11% | Unresolved/censored; no full target touch |
| TIPSFILMS | 09:39:35 | — | PENDING_DROPPED | — | — | Operator disabled Working Range while pending; not a performance failure |
| WINDMACHIN | 09:48:51 | — | PENDING_CONFIRMATION | — | — | Only one matching observation |
| AViENCE | 10:01:09 | — | PENDING_CONFIRMATION | — | — | Upper band/no sell offer; candidate remains untradeable until offer exists |

Only four events confirmed in this short sample: one sampled target touch, one clear weakening/adverse example, and two unresolved. This is not independent validation and is not evidence of a profitable working range. Sparse LTP samples do not prove intrabar paths or actual fills.

### OAL — near-alignment candidate outside Working Range

OAL was a Fixed PRIMARY at 10:01:28 IST, with reference entry ₹461.25 and original target ₹466.40 (+1.1161%). Its recorded recommendation-tracker MFE was +1.073%; the full-market sampled path had 11 observations and a sampled peak ₹465.95 (+1.019%). The target was not touched in the available export; +0.50% was reached, followed by a sampled move below entry. No Fixed Auto-Buy execution is recorded.

At its evaluation, OAL had RSI 79.05, RVOL 20.35, green-volume share 81.45%, volume acceleration 2.66, VWAP extension 0.90 ATR, positive MACD/EMA slopes and positive book imbalance. EMA9 441.18 > EMA20 434.54, but EMA50 434.75: EMA20 was ₹0.21 below EMA50. The current Working Range strict EMA9 > EMA20 > EMA50 rule rejected it.

**Decision:** do not loosen the production Working Range rule in this intraday patch. v9.0.21 records rejected candidate reasons and EMA values, including near-crosses, so OAL can be reviewed with the full end-of-day population. A possible 0.10 ATR EMA20/EMA50 near-alignment tolerance remains a shadow hypothesis only.

## v9.0.21 changes

1. The Settings switch remains named **Working Range**. When ON, it enables the independent Working Range stream and authorizes a live Alpha CNC BUY after two consecutive matching evaluations.
2. Existing v9.0.20 research-only ON state is reset to OFF once during upgrade. The operator must deliberately turn it ON again. This prevents a previously enabled research toggle from silently becoming real-money Auto-Buy.
3. Only confirmations with `confirmedAtMs > workingRangeActivatedAtMs` are eligible. Confirmation must be no older than five minutes, remain `IN_RANGE`, and have no prior order intent.
4. Before submission: fresh Alpha quote, price-band validation, quote liquidity and thin-liquidity checks, entry-price drift guard, target-not-already-reached check, broker authentication/static-IP/DDPI/session, cash/margin, deployed-capital, concurrent holdings, existing symbol, pending-order and unprotected-holding guards.
5. Alpha is the only execution account. Beta remains read-only.
6. A deterministic `WRM` reference is synchronously committed to local storage before order submission. Pending intents survive midnight and journal trimming. Known pre-POST guard blocks are recorded as blocked without submission. Ambiguous outcomes are reconciled by that exact broker reference; no blind resubmission. If the broker status/order ID cannot be reconciled, Safety Halt is set and manual broker reconciliation is required.
7. Filled Working Range positions are registered under the shared app-owned Fixed holding protection lifecycle, with the original hard -3% floor and existing +0.55% trail arm / +0.50% protected gain / +0.10% ratchet. If GTT protection is not verified, a critical alert is logged and Safety Halt is set to block further new entries.
8. Turning Working Range OFF blocks new entries and immediately attempts to cancel/reconcile any outstanding Working Range entry order. Existing holdings continue to be protected. If a pending order remains open after cancellation, Safety Halt is set.
9. Diagnostic export preserves prior v9.0.20 Working Range rows, adds auto-buy state/reference/order/fill, and stores bounded rejected-candidate reasons/EMA values. Discovery itself still adds zero broker requests; live execution adds calls only after a new confirmation.
10. Fixed 40-factor scoring, 15m production inputs, PRIMARY confirmation, Fixed candidate universe/rank, original Fixed target, Fixed Auto-Buy, Forecast, SELL/GTT/trailing and Alpha/Beta separation remain unchanged.

## Groww API observations from this upload

- Raw `ACTUAL_WIRE_ATTEMPT_V909`: 8,151 attempts (Alpha 6,005; Beta 2,146).
- Raw response records: Alpha 6,001 HTTP 200, one 401, two 201 and one 202; Beta 2,144 HTTP 200, one 429 and one response with missing status.
- The only 429 was Beta `GET /v1/live-data/quote`, started 09:41:07.095 IST, completed 09:41:07.656 IST (561 ms). At request start: 2 account/category/endpoint calls in the preceding second, 39 account calls and 38 Live Data calls in the preceding 60 seconds, and 8 calls to that endpoint. `Retry-After` was absent. The audit explicitly says broker quota breach is not proven.
- Completed Fixed scans: 38; median duration 86.681 s, 95th percentile 112.272 s, maximum 150.178 s. Median scan-start spacing 90.913 s; maximum gap 241.316 s.
- Cumulative local governor wait: Alpha 3,887,073 ms; Beta 2,578,949 ms. These durations overlap across concurrent requests and are not elapsed wall-clock time.
- NSE full-market discovery: 40 sweeps, 3,342/3,572 valid symbols at best (93.56%), zero batch errors. No extra discovery sweeps are added by Working Range.

## Deployment and trading caution

Working Range Auto-Buy is now a live-entry feature, not just a research toggle. The 20-Nov sample is too small to validate the strategy and includes the KALPATARU adverse example. The feature is included only because the operator explicitly requested it; enable it only if prepared for real Groww CNC orders from the current pilot rules.

The TEST APK uses a CI debug signature. In-place update compatibility must be checked against the installed v9.0.20 signer. If the certificate differs, do not uninstall an app with open holdings, GTTs, pending orders, credentials or unexported logs. Rebuild with the original signing key managed securely in CI; never paste private keystore material into chat.

No APK should be called release-ready until the full latest CI run passes unit tests, source locks, replay tests, assembly and signature verification.
