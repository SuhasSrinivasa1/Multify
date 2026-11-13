# v8.3.28 TEST — app-owned CNC LONG hard stop from -5% to -2%

Based solely on v8.3.27 (commit 9d746e162453f9eacb0df4c5868011088e83e63a).
This is a new isolated TEST branch; previous branches and main remain unchanged.

## Exact change
The common app-owned LONG hard stop changes from 5.0% to 2.0% loss
measured from the actual average/confirmed filled entry price:
    trigger_raw = entry_price * (1.0 - 2.0 / 100.0)
GTT placement uses the unchanged safe broker tick-rounding protocol.
This is a stop *trigger* not a guaranteed sale/fill at exactly -2%.
Broker gaps and slippage can produce a loss worse than -2%.

A ₹100.00 fill uses ₹98.00 before broker tick rounding.
Existing +0.55% arm / +0.50% protected gross profit and subsequent
+0.10% stepped trailing increments remain entirely unchanged.
No averaging down or new stock selection gate is introduced.

## New and existing managed positions
All known app-owned Multify, Fixed and (legacy) Forecast CNC LONG
registration paths call HardStopLossPolicy.stopPrice(actualEntryPrice);
all now use the common -2% floor for new GTT protection.

For existing app-owned holdings:
- On verified active app-owned HARD_STOP_GTT or TRAILING_GTT
  where broker trigger is *below* the new -2% floor, an in-place
  modifyGtt attempts to raise it; broker ownership must be verified.
- If the modify fails, the previous broker SELL GTT is retained, and
  the independent managed-holding monitor can retry. No cancel-first
  exposure for this verified-active migration path.
- Existing verified tighter hard/positive trailing GTTs are not
  loosened or reset; active profit-trailing protection wins.
- When a GTT is not verified active, existing protected order
  reconciliation/fallback logic stays in control.
- The application's local position state or an install cannot
  automatically change an order already sitting on Groww's server.
  A successful broker GTT modification and updated ACTIVE/OPEN order
  must be confirmed in Groww. Manual or non-app-owned holdings
  are not silently adopted or modified.

## Scope, safety and regression proof
- HardStopLossPolicy.MAX_LOSS_PCT and the ReleaseIntegrityManifest
  hard-loss invariant are changed to 2.0.
- All stale user-visible strategy descriptions in the app and
  TradingRepository are updated from hard -5% to hard -2%.
- No change to original 15-minute Fixed candle and 40-factor strategy,
  90-second schedule, 28 discovery slots, 48 detail slots, PRIMARY
  confirmation, publication, Auto Buy/SELL control, broker
  throttling, duplicate-worker fix or 5-minute research shadow.
- CI compares frozen recommendation functions and nine policies
  against BOTH v8.3.19 branches; checks that service and trailing
  policies are byte-identical to v8.3.27; restricts changed files.
- Unit tests validate exact -2% trigger/breach and active broker GTT
  tightening rules; existing +0.55/+0.50 gain trailing tests remain.
- v8.3.28 debug APK is a TEST: keep Auto Buy OFF until broker
  GTT status and source parity have been inspected on a device.

## Important operational warning
- Open Groww and confirm that every relevant app-owned holding has
  an active, correct quantity, correct trigger-price protective SELL
  GTT at the new limit; do not assume installation alone changed it.
- This tighter loss threshold can result in more stop-outs, and any
  resulting reduction in net profit requires prospective testing.
- Do not uninstall the live app just to change signing; uninstalling
  may lose local position tracking or recovery information.
