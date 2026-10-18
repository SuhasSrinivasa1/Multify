# Multify Trader Pro v7.1.0 — Trailing GTT Recovery

## One GTT per holding — no duplicate sell architecture

- v7.1 uses **one authoritative app-owned GTT per CNC holding**, never a ladder of multiple independent GTTs.
- Before the trail arms, there is **no downside sell stop**. A falling stock remains held.
- At +0.55% high-water the app creates a Groww CASH/CNC GTT SELL at +0.50% protected gain.
- At +0.65% the same GTT is modified to +0.60%; +0.75% modifies it to +0.70%; the +0.10% ladder continues indefinitely.
- The server-side GTT is the exit authority once active. This avoids relying on the phone-side sampler to notice the downward crossing; active holdings are sampled about every 350 ms only to discover new high-water ratchets.
- Local market SELL is only a fallback when no active GTT exists. If a GTT is active or already triggered, direct SELL is blocked to prevent an accidental short/duplicate sell.
- When an app-owned holding closes, the known GTT state is cancelled/cleared and the position is removed. Completed/triggered GTTs are reconciled before any new exit action.

## Explicit stock-by-stock trailing logs

Every actively monitored stock now emits a structured `TRAILING_STATE / STOCK_TRAIL_STATE` heartbeat (rate-limited to five seconds, plus every arm/ratchet/exit transition) including:

- entry, LTP and current gain %
- high-water price and high-water gain %
- +0.55% arm threshold
- current protected gain and stop price
- next +0.10% ratchet threshold and price
- GTT id, mode, status, trigger and direction
- explicit `downward_sell_disabled=true`
- explicit `averaging_down_disabled=true`
- whether the local fallback is eligible

GTT create/modify/status/cancel/recovery transitions are separately audited.

## No downward averaging — removed from the live schema

- Legacy `anchorPrice`, `capitalDeployed`, `campaignBudget` and `addCount` position fields are removed by Room migration 13→14.
- There is no add-on-buy loop, decline threshold, averaging quantity, or downward averaging action in the production engine.
- Legacy non-long exposure can only be flattened; v7.1 never creates a SHORT position.

## No downside liquidation

- The previous independent −12% emergency failsafe is removed.
- Price weakness alone never generates an automated SELL.
- Profit exits during the session are the stepped trailing GTT only.

## End-of-day ₹100-net recovery GTT

For every app-owned CNC holding still open after the regular market session:

1. Any app-owned live trailing GTT is cancelled first. A second GTT is never created while an old one remains active/triggered.
2. The app calculates the sell price required for at least **₹100 estimated net profit** after standard Groww NSE delivery charges.
3. The charge model includes buy + sell brokerage, delivery STT, stamp duty, NSE transaction charge, SEBI turnover charge, IPFT, conservative DP debit charge and GST.
4. The target is rounded upward to a ₹0.05-safe price step.
5. One CASH/CNC GTT SELL is created with a **LIMIT SELL at the calculated recovery price**. If price is below the recovery target it uses `UP`; if already above the target it uses `DOWN`. A gap below the limit waits rather than deliberately filling below the ₹100-net recovery floor.
6. The holding enters `RECOVERY_GTT` mode. The live trailing engine stops touching it; the app only verifies/reconciles that GTT until the holding closes.
7. Failed/cancelled/expired recovery GTTs are recreated while the holding still exists.

Groww contract-note rounding/funding-mode differences can make exact final charges differ slightly; the trigger is therefore rounded upward rather than downward.

## Positive-close-only forecast learning

- Negative and flat close days remain stored for diagnostics and counterfactual review.
- They are **excluded** from forecast MFE target learning, strategy evidence bonuses and champion learning.
- Forecast ranking examples are now drawn only from prior positive-close days.
- After-hours reports explicitly show how many negative/flat rows were excluded from learning.

## Version

`7.1.0-trailing-gtt-recovery` (`versionCode 710`).

## Execution sampling

- Open non-recovery holdings are batch-LTP sampled about every **350 ms** to discover new highs and ratchet the one server GTT.
- The downward trigger itself is server-side once the GTT is active; polling is not the sell trigger.
- No OCO create/modify API remains in the Android app; CNC exit automation is GTT-only.
