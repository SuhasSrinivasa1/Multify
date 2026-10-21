# Multify Trader Pro v7.4.0 — Forecast Auto-buy

## New Settings toggle: Auto-buy forecast recommendations

Settings → ARM now includes a separate **Auto-buy forecast recommendations** toggle.

Execution requires both:
- ARM ON
- Forecast Auto-buy ON

ARM remains the master safety switch. Forecasting and learning continue while ARM is OFF, but no automatic forecast BUY is sent.

## Fixed forecast entry and target

For every newly published, confirmed PRIMARY recommendation:

- the published forecast entry price is frozen as the BUY limit price
- Groww receives a CNC / DAY / LIMIT BUY
- the published forecast target price is frozen and stored unchanged
- target price is not recalculated from later LTP or actual fill price
- Groww may fill the LIMIT at the published entry price or a better price; it may remain unfilled if the market never trades at that price

The existing stepped trailing architecture is unchanged after fill:
- +0.55% gain arms profit protection
- +0.50% is protected
- +0.10% ratchet increments continue
- no downside-loss sell
- end-of-day ₹100 estimated-net recovery GTT remains unchanged

## Pending-order safety

- pending forecast LIMIT orders are persisted across Android process restarts
- deterministic broker reference IDs prevent duplicate submission after ambiguous network failures
- manual BUY is blocked while an auto-buy LIMIT for the same symbol is pending
- auto-buy creates a managed holding only after Groww confirms actual filled quantity
- partial fills cause cancellation of the remaining quantity before the filled shares are adopted
- unresolved partial-fill cancellation triggers the existing safety halt to stop new execution
- disabling Forecast Auto-buy cancels still-pending unfilled forecast orders
- disabling ARM or triggering a safety halt also cancels pending forecast auto-buy orders
- if a forecast stops being ACTIVE before fill, its pending auto-buy order is cancelled

## Recommendation policy unchanged

- no daily numeric recommendation cap
- PRIMARY quality criteria unchanged
- multi-scan confirmation unchanged
- discovery-health requirements unchanged
- same-day duplicate recommendation symbols remain blocked
- positive-close-only production learning unchanged

Version: `7.4.0-forecast-autobuy` (`versionCode 740`).
