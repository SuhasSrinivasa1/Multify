# Multify LONG Holdings Gateway v6.1.0

This service is the static-IP broker boundary for the LONG-only holdings architecture.

The trading surface is intentionally narrow: the gateway accepts only `BUY_HOLDING` and `SELL_OWNED_HOLDING` intents. Both submit NSE CASH `CNC` orders. A sell intent is accepted only after the gateway verifies that the requested symbol exists in the broker holdings list with sufficient owned quantity. There is no generic direction field, no intraday product, no reversal endpoint, and no advanced two-sided order endpoint.

The gateway also provides authenticated profile, margin, holdings, position, quote, candle, order-status and kill-switch endpoints. New holding orders are idempotent, rate-limited, time-gated and audited. The kill switch blocks new holding purchases but does not prevent selling an already-owned holding.

## Production deployment

Run this only on a VPS/cloud host with a fixed outbound public IPv4 address registered for the broker API account. Put TLS in front of port 8080. Keep broker credentials, TOTP seeds, phone HMAC secrets, keystores and signing passwords outside Git.

Copy `.env.example` to `.env`, configure secrets, start with `docker compose up -d --build`, then verify `/health`, profile, margins, holdings and quotes before enabling ARM.

## HMAC format

Protected requests use `X-Client-Id`, `X-Timestamp`, `X-Nonce`, and `X-Signature`.

`METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256_HEX(BODY)`

Sign the canonical message with lower-case hex HMAC-SHA256 using `PHONE_HMAC_SECRET`.
