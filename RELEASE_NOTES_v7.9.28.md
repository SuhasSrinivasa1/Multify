# Multify Trader Pro v7.9.28 — Forecast News & Durable Audit

Forecast-only public NSE corporate-announcement collection, 40 nullable news fields, company/symbol mapping, duplicate suppression, event timestamping and price-outcome sampling.

The 15-minute Multify post-call +1% successes now receive persisted pre-spike 40-factor Forecast research examples (only a confirmed 1% rise can be promoted to positive evidence). Other Multify outcomes remain historical data, not positive examples.

A 60-second news poll runs in the foreground trading service; WorkManager offers a best-effort 15-minute recovery fallback when Android stops the service. The phone cannot guarantee uninterrupted polling while offline, forcibly stopped or battery-restricted. News is fetched from public NSE RSS feeds, not undocumented Groww endpoints. No guaranteed real-time feed delivery.

News-only PRIMARY recommendations still require current-session baseline data, actual quote/1m candles, spread/volume/entry-quality checks and estimated positive economics. Research-only evidence never triggers trading. Five completed learning sessions are no longer needed to activate Forecast Auto Buy, but explicit user enablement and every existing execution/SELL safeguard remain necessary. Beta market-data calls remain read-only and Alpha retains all orders.

The ZIP export now includes a full durable Forecast news event/update journal, retained audit JSONL segments and 30 days of locally retained Fixed recommendations. Restore can merge news/audit/positive research without restoring trading/account credentials. For signature-changing reinstallations, export the ZIP **before uninstalling** and import it afterward. Uninstalling without a ZIP loses local history; this release does not provide cloud backup.

**Scope:** Fixed 40-factor scorer, feature builder, publication and ranking are unchanged from v7.9.27. Historical news backfills, independent 24/7 cloud-based scanning, formal cross-validated uplift and guaranteed 1%/3% predictions are not included; never claim them without test datasets and a persistent server.
