# Fixed v8 early-market research feed (opt-in, non-trading)

The production Fixed engine **still uses its frozen 15-minute broker candles,
40 factors, complete NSE market-wide discovery, repeat confirmation and original
target**. It does not connect to this feed and does not read its files.

Groww's published Python SDK exposes GrowwFeed.subscribe_ltp(...) and
GrowwFeed.consume() for streaming source-timestamped LTP observations; the
documented limit is **up to 1,000 instruments per subscription**. No official
raw Android WebSocket handshake is assumed. This module deliberately requires
the separately installed optional `growwapi` package and a user-supplied
access token; neither is added to the Android APK or written to logs.

The `backend.app.fixed_stream_research` module:
- selects only explicit NSE CASH exchange tokens from the official instruments CSV;
- caps observation to 1,000 tokens per opt-in subscription;
- rejects invalid, future-skewed or stale (>10 seconds) source timestamps;
- emits read-only JSON market observations through a caller-owned sink;
- never submits orders, never touches Android Fixed scoring, and is not
  deployed/enabled by the default backend service.

**Activation remains disabled** until a Groww subscription, feed lifecycle,
subscription limits, credentials, static IP, user deployment topology and
end-to-end timestamp/freshness checks are verified. It cannot claim 2,623-symbol
coverage or shorten official Fixed scan time without a validated source and
separately audited feed integration.

Useful benchmark: compare (1) first observed stream LTP timestamp,
(2) first complete REST market-discovery appearance,
(3) first PRIMARY entry-time quote and (4) actual notification time. These
measure a potential earlier research opportunity without look-ahead.
