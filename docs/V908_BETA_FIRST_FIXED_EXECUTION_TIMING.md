# Multify Trader Pro v9.0.8 — Beta-first Fixed / source-safe Auto Buy (TEST)

## Incident evidence, 17 November 2026
- v9.0.7 export at 12:11 IST: SEJALLTD PRIMARY / execution eligible,
  source-reference price ₹934, full reference target 1.1161043%; reported
  `NO_FRESH_POST_ACTIVATION_BUYS` and no new BUY. Its scan started about
  11:57:22 and reached Auto Buy only about 12:08:53 (691 seconds later).
- The original recommendation `generatedAtMs` is intentionally scan start,
  needed for the frozen Fixed confirmation / same-day event identity.
- Client quote-source receipt was about 12:04:36 and was roughly 257
  seconds old when Fixed published. The account's Alpha LIVE_DATA cooldown
  through about 12:22 prevented any fresh execution quote independently.
- v9.0.7 Beta LIVE retry pacing rose near 7-8 seconds after 429, creating
  11-12 minute scans. Simply giving Beta *all* requests blindly would be
  worse; each account still needs independent budget/cooldown admission.
- 12:11 export contains TWO app-managed holdings (LODHA manual; TRIVENIPT
  automatic), each with an ACTIVE broker-verified hard-stop GTT. The
  `SteppedTrailingPolicy` coefficients are 0.55% arm, 0.50% protected gain,
  0.10% subsequent steps; stop floor is 2% below fill. No broker-side
  ratchet modification was proven in these records. Repeated
  `GTT_OWNERSHIP_CONFLICT_MANUAL_REVIEW` warnings require separate broker
  reconciliation; never bypass that conflict to force a trade.

## Scope of this release (transport and execution timing only)
1. **Read-only Beta-first Fixed market discovery and detailed quotes**:
   healthy Beta gets 4/5 original NSE LTP batch slots and approximately
   4/5 detail quote symbols. When its own admitted pacing exceeds 2.4s,
   the optional research allocation reduces to 3/5 Beta; when Beta alone
   has severe 4s+ pacing, defer the incomplete sweep instead of spinning
   for many minutes. No extra NSE batches and no changes to the broker
   enforcement. Alpha keeps trading/quote/inventory/protective GTT authority.
2. **Independent Alpha historical candles when healthy**: Alpha's
   LIVE_DATA cooldown does not force 48 histories into Beta's 8/min bucket.
   The existing historical quality, finished-15m-bar, candidate and coverage
   rules are preserved exactly.
3. **Correct event publication evidence outside the frozen strategy**:
   original immutable `generatedAtMs` remains scan-start/confirmation;
   record separate actual source quote client receipt and scan completion/
   publication in a bounded, process-local journal for only newly emitted
   recommendation IDs. Client receipt is not claimed to be exchange time.
4. **BUY freshness requires TWO independent timestamps**: both source
   quote receipt and publication must be <= original 5-minute age. Both
   clocks must match the original event ID and scan start, with the old
   pre-activation requirement unchanged. Process restart or missing
   evidence fails closed. There is no arbitrary 38/45/90-second scan-time
   trading cutoff. A fresh Alpha *execution* quote is still mandatory.
5. **Short cooldown retry remains bounded**: pending recommendation
   intents use the earlier of quote-source expiry or publication expiry;
   still need at least 20s to re-run the entire existing Alpha execution
   flow. Never retry old signals after a 15- or 60-minute cooldown.
6. **Better diagnostics**: scan duration warnings (>90s WARNING,
   >120s CRITICAL, >300s EXTREME) are audit only. Every skipped event
   logs ID, symbol and specific reason and can post a one-time
   informational skipped-BUY notification.
7. **Trailing and GTT untouched**: runtime service order/position
   monitoring, post-fill hard-stop verification, hard-stop policy,
   stepped trail and net-gain policy remain exact original source.
   Build gates compare them bytewise and run existing unit tests.

## No modifications to Fixed trade selection/filtering
- Exact frozen v8.3.19 `generateFixedForecasts`, discovery, 40 factor
  scorer, price-band, tiering, PRIMARY repeat-confirmation, target,
  ranking and publication.
- Existing Alpha BUY filters: liquidity, price extension/anti-chase,
  activation, budget sizing, duplicate holdings, session, broker readiness,
  current recommendation, protection and post-fill GTT.
- Existing threshold: 1.1161043% target; -2% stop; +0.55/+0.50/+0.10 trail.
- Beta is strictly research only. No credential borrowing or Beta orders.

## Acceptance and limitations
- Source parity and Android unit tests must pass alongside replay/backend
  tests. APK must be verified and hashed by GitHub CI.
- This is a TEST build; API request acceptance and live trading/ratchet
  transitions cannot be guaranteed without a new full market session.
- Latest 12:11 export still shows GTT ownership conflict alerts: verify
  live Groww GTTs for existing holdings before changing installs.
- A new signing cert may prevent an in-place update. Do not uninstall an
  app managing holdings without backing up local order metadata and
  reconciling all Groww positions/GTTs.
