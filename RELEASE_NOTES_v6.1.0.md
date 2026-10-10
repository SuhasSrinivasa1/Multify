# Multify Trader Pro v6.1.0

This release hardens the app as a strict LONG-only holdings system.

- One ARM switch only. There is no AUTO/LONG/SHORT selector.
- Multify trade releases can only create app-owned LONG CNC holdings.
- Forecast recommendations are LONG-only and buy CNC holdings.
- Forecast persistence is now structurally LONG-only: the old generic direction/side column has been removed from forecast and champion tables.
- Strategy evaluation is intrinsically LONG; there is no short evaluator or direction switcher.
- The active broker layer exposes CNC BUY and SELL-owned-LONG operations rather than a generic strategy order path.
- SELL is used only to close an app-owned LONG holding. It is never used to create a short position.
- The holdings screen shows current unrealised holding state only; realised P&L is not shown.
- The averages screen contains LONG statistics only.
- The rolling LONG average remains the profit-trailing arming level; touching it does not force an immediate sale.
- Old non-LONG ledger rows are handled only by the upgrade safety guard so an existing broker exposure is not silently forgotten during migration.
