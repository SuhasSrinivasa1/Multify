# v9.0.25 — Always-on Working Range research and separate Auto-Buy permission

## Working Range behavior

The Working Range research observer now consumes the existing completed 15-minute Fixed evaluation and quote on every eligible Indian scan regardless of the Working Range Auto-Buy switch.

The Auto-Buy switch now controls only execution permission:
- OFF: research observations, candidate confirmation, dashboard rows, and sampled outcomes continue. No new Working Range BUY is permitted.
- ON: only a newly confirmed event whose confirmation timestamp is after the explicit ON activation timestamp may enter the existing Alpha-only execution path.
- Turning OFF no longer invalidates or drops a pending research candidate. Turning ON does not revive a confirmation that predates the activation timestamp.
- Existing pending order intents continue to reconcile; existing holdings and broker-side GTT protection remain monitored.
- Dashboard rows are returned and rendered in both toggle states. Each row distinguishes research-only status from event-level Auto-Buy permission.

No additional Groww API calls are added for Working Range research; it reuses the already fetched Fixed 15-minute evaluation and quote. The Fixed scoring/selection and the Working Range candidate/exit parameters are unchanged.

## US-market research request — API capability gate

Groww's US Stocks product is live, but the public Groww Trade API documentation currently documents:
- Live quote/LTP/OHLC request parameters using NSE/BSE exchange symbols.
- Historical candle/backtesting exchange identifiers NSE and BSE.
- No documented US market data endpoint, US instrument master format, US market data entitlement, or US-specific API rate-limit category.

The Groww US Stocks product is a separate brokerage feature and its published market hours differ with US DST. This does not by itself establish that a user's regular Groww Trade API access token can call US market-data endpoints.

Therefore v9.0.25 does NOT send speculative exchange codes, add US requests against undocumented endpoints, or connect the Indian Fixed/Working Range scanner to US symbols. A US read-only engine is blocked pending an official endpoint/SDK specification and entitlement/authentication details (or a separately provisioned market-data provider key). When available, it must be a separate provider, instrument master, calendar (America/New_York with DST and US holidays), currency-aware feature/target/outcome ledger, API budget, and UI section. All US order, position, and GTT endpoints must be absent from the US research execution graph, with a hard no-order invariant.

## Validation

- CI asserts the Working Range observer has no Auto-Buy gate.
- CI asserts dashboard data is not conditional on the Auto-Buy setting.
- CI asserts the toggle only updates the store's execution permission.
- Existing Working Range activation timestamp, durable order-intent, no-blind-resubmit, Alpha-only execution, broker protection, and frozen Fixed checks remain in place.
