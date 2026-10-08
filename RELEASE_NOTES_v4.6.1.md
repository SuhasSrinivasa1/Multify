# Multify Trader Pro v4.6.1 — Fixed Capital / No Averaging

## Core change

Auto Mode no longer averages down, adds ₹5,000 tranches, reserves cash for later adds, or increases campaign capital after entry.

Wave 2+ remains a LONG / SHORT / HOLD direction engine, but the capital rule is strict:

- same-side decision => HOLD the existing position; place no add-on order;
- opposite-side decision => flatten first, then reopen only within the existing campaign notional;
- HOLD => no order;
- no reversal, post-sell short, Shadow transition, or Fast Track transition may reset to a larger configured budget.

The new FixedCapitalPolicy is the hard cap used by reversals so an older position with a smaller actual notional cannot expand simply because the account's configured budget is larger.

## Removed

- down-averaging functions for Shadow and Fast Track;
- ₹5,000 wave add/probe execution;
- reserved-for-averaging capital;
- same-side quantity increases at 2% checkpoints;
- automatic campaign-cap growth during reversals.

## Preserved

- 2% direction checkpoints;
- LONG / SHORT / HOLD EV comparison;
- trajectory, VWAP, EMA, MACD, ATR, RVOL, candle and order-book/market-context scoring already present;
- rolling Multify target/retracement learning;
- risk gates, OCO/trailing protection and Shadow research;
- one real net direction at a time.
