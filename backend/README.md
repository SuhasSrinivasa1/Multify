# Multify Live Gateway v4.5.0

This service is the intermediary/static-IP layer between the Android app and Groww. Broker API key and TOTP secret stay on the server. The phone authenticates to the gateway with HMAC-SHA256 signed requests and never needs the broker secret.

The gateway supplies quote/candle/profile/margin/position reads and separate risk-gated live order/OCO endpoints. New orders are idempotent, rate-limited, time-gated, logged, and subject to a server-side kill switch. Broker positions are re-read before risk-changing orders so the broker remains the source of truth.

## Production deployment

Run this only on a VPS/cloud host with a fixed outbound public IPv4 address that is registered/allowed for the broker API account. Put TLS in front of port 8080 using Caddy, nginx, a cloud load balancer, or equivalent. Do not expose the raw HTTP port publicly.

Copy `.env.example` to `.env` on the server and populate the secrets there or use a secret manager. Never commit `.env`, broker credentials, TOTP seeds, phone HMAC secrets, keystores, or signing passwords.

Start with `docker compose up -d --build`. Check `/health`, then validate profile, margins, positions, quotes, a tiny paper/shadow path, and only then enable live execution from the Android app.

## HMAC format

Every protected request uses `X-Client-Id`, `X-Timestamp` (Unix seconds), `X-Nonce`, and `X-Signature`. Signature input is exactly:

`METHOD\nPATH\nTIMESTAMP\nNONCE\nSHA256_HEX(BODY)`

The signature is lower-case hex HMAC-SHA256 with `PHONE_HMAC_SECRET`. Nonces are persisted for replay protection.

## Android integration target

Add a gateway transport in the app. In live mode route all broker API traffic through this service; paper/shadow mode may use the gateway for market data too. Keep the app-side strategy/learning logic, but make the gateway the only component allowed to hold Groww credentials or submit live orders.
