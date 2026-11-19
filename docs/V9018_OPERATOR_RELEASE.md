# Multify Trader Pro v9.0.18 Operator

## Release intent

This is a forward-only operator update based on the v9.0.17 Working Range Evidence branch. It preserves the accepted Fixed recommendation engine and adds explicit execution-state reporting, correct event-local repeat telemetry, accurate broker wire error roll-ups, and restoration of the approved -2% hard-stop baseline.

## Frozen production contract

- Fixed 40-factor scoring and factor-family rules are unchanged.
- Production inputs remain 15-minute candles.
- PRIMARY confirmation, publication and candidate ranking are unchanged.
- Original Fixed target calibration is unchanged.
- +0.55% trail activation, +0.50% protected gain and +0.10% ratchet are unchanged.
- Forecast remains frozen; working-range, exhaustion and 5-minute research remain shadow-only.
- Alpha remains the only execution/protection account; Beta remains strictly read-only.
- No new broker calls are introduced by execution-state presentation or event-local telemetry.

## Operator changes

### Execution status is separate from recommendation status

The app now persists an event-keyed execution state independently from the frozen recommendation state. The Fixed table can distinguish:
- BUY EXECUTED
- PARTIAL FILL with filled/requested quantity
- EXTERNAL HOLDING
- MAX HOLDINGS
- EXISTING APP-OWNED HOLDING
- PENDING LEGACY GTT
- LIQUIDITY / ENTRY QUALITY / PRICE EXTENSION blocks
- BROKER COOLDOWN / retry pending
- BROKER REJECTED
- EXPIRED

Blocked or already-consumed events do not offer a second manual BUY. Automatic retries remain governed by the existing freshness, broker, holding-cap and event-consumption rules. Partial fill status does not claim the unfilled balance was executed.

### Repeat-event timing is event-local

A repeat recommendation now anchors first PRIMARY to the first qualified PRIMARY after the explicit non-PRIMARY reset following the previous event. Future observations are excluded from the anchor calculation. This corrects the VEEFIN-style case where a repeat could inherit the original setup's first PRIMARY timestamp.

### Broker wire error aggregation

Each actual network response updates account, category and endpoint counters. Previously only category counters were incremented, causing account-wide and endpoint summaries to under-report HTTP 429 and failure totals.

### Hard stop restored to approved -2%

The previous runtime used -3% despite the operator contract specifying -2%. New holdings use -2%. Existing verified app-owned SELL GTTs below the new -2% stop are tightened through the existing GTT modify pathway; the active GTT is not cancelled first. Stronger/trailing stops are not loosened. Failed modifications retain the existing active broker GTT and remain in the managed-monitor retry path.

## Latest log evidence used

The 10:55 IST v9.0.17 export contained 48 completed scans, 10 recommendation events across 9 unique symbols, 3 Fixed BUY fills (one partial), 2 open Fixed holdings and 1 closed Fixed trade. Four sampled recommendation-price paths reached the original target; these are not four realized profits.

Scan duration median was 118.4 seconds overall, with the 9:45-10:15 bucket at 140.1 seconds. Four actual Groww HTTP 429 responses were recorded across Alpha and Beta. This release corrects 429 aggregation but does not claim to have solved scan latency or broker-side throttling.

The latest export showed two active app-owned GTTs at approximately -3%. The operator update changes the desired stop to -2% and uses the existing safe GTT-tightening monitor to upgrade those orders after install. Confirm broker-side trigger/status after updating.

## Explicitly not changed

- No Fixed selection, confirmation, scoring, target or trailing threshold was relaxed.
- No VWAP/ATR or RSI exhaustion filter was promoted into production.
- No 5-minute entry logic was promoted.
- No concurrent holding limit was increased.
- No API concurrency was increased.
- No third broker account was added.
- No automatic stale-quote BUY or blind order resubmission was introduced.

## Install and operator safety

This CI build is debug-signed. A valid APK signature does not prove in-place update compatibility with the installed v9.0.17 signer. Compare the certificate before updating. Do not uninstall or replace the app while it owns open holdings, active GTTs or unexported learning/evidence.

After installation:
1. Authenticate Alpha and verify static IP/DDPI readiness; confirm Beta is read-only.
2. Confirm existing AZAD/DICIND (or any current app-owned) holdings are present.
3. Confirm each active broker GTT is ACTIVE and its trigger is at the approved -2% level or tighter.
4. Confirm the UI reports execution states correctly and no stale event offers a manual BUY.
5. Export a fresh log and verify account/category/endpoint 429 totals reconcile to actual wire events.
6. Keep new automatic BUY disabled until the existing holdings' GTT triggers have been broker-verified at -2% and any unresolved safety halt is cleared.

## Performance follow-up

The 90-second scan intent was not consistently achieved after 9:45. Do not increase concurrency to chase the interval. The next isolated optimization should profile and reduce duplicate inventory/order reads and reuse only completed 15-minute candles with freshness validation, then replay identical inputs to prove Fixed parity and preserve SELL/GTT priority.
