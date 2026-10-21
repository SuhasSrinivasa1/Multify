# Multify Trader Pro v7.4.1 — Forecast GTT Auto-buy

## Forecast Auto-buy uses a directional GTT entry

Settings → ARM contains the separate **Auto-buy forecast recommendations** toggle.

Real forecast execution requires both:
- ARM ON
- Forecast Auto-buy ON

For every newly published confirmed PRIMARY recommendation:

- the published forecast entry is frozen as the GTT trigger
- trigger direction is **UP**
- the post-trigger order is a **CNC MARKET BUY**
- the published forecast target remains frozen and is never recalculated from later LTP
- the existing quality gates, multi-scan confirmation and uncapped recommendation policy remain unchanged

The directional GTT avoids the previous passive LIMIT behavior: simply falling down through the forecast entry does not trigger the BUY. The entry trigger must be crossed upward.

Because the post-trigger order is MARKET, the actual broker fill can differ slightly from the fixed forecast entry trigger. The strategy entry reference and target remain the published forecast values.

## GTT lifecycle safety

Groww GTT monitoring can outlive the trading day, so the app keeps pending GTT state across Android restarts and trading-date changes instead of deleting it blindly.

- untriggered GTTs are cancelled when Forecast Auto-buy is turned OFF
- untriggered GTTs are cancelled when ARM is turned OFF or safety halt is activated
- untriggered GTTs are cancelled when the forecast is no longer ACTIVE
- stale prior-date GTTs are cancelled during reconciliation
- if a GTT has already triggered, the app waits for broker terminal status rather than pretending it can undo an already-sent child order
- a COMPLETED GTT becomes an app-owned LONG CNC holding
- deterministic references plus broker smart-order recovery reduce duplicate submission risk after ambiguous network failures
- old v7.4.0 pending LIMIT auto-buy rows are cancelled/migrated during upgrade reconciliation

## Existing holding exits remain unchanged

After a forecast GTT completes:
- long-only CNC holding
- no downward averaging
- no downside-loss sell
- +0.55% arm / +0.50% protect / +0.10% ratchet
- one authoritative exit GTT after trailing arms
- end-of-day ₹100 estimated-net recovery GTT if still carried

Version: `7.4.1-forecast-gtt-autobuy` (`versionCode 741`).
