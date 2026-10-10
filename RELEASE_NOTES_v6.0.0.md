# Multify Trader Pro v6.0.0

This release is a long-only holdings architecture.

- One ARM switch only. No AUTO/LONG/SHORT selector.
- Multify equity calls can open app-owned LONG positions only.
- New orders use Groww CNC so positions are holdings and are not force-flattened at the end of the session.
- DDPI and static-IP verification are required before ARM can be enabled.
- Forecasting is LONG-only and can buy CNC holdings manually while ARM is enabled.
- The Execution screen does not display realised P&L.
- The learning screen shows LONG averages only.
- Bearish evaluators, direction switching, post-exit reversal, wave execution, and short-specific learning/runtime monitors were removed.
- The rolling LONG average remains a profit-trailing arming level rather than an immediate sell target.
- Legacy database compatibility is retained only to open an existing v5 installation safely; no new non-LONG order path exists.
