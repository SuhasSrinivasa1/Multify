# Multify Trader Pro v4.5.0-live-gateway

Adds a deployable static-IP intermediary layer for live broker connectivity. The gateway owns Groww credentials/TOTP, HMAC-authenticates the Android client, provides broker market-data/account reads, exposes idempotent live order and OCO endpoints, includes replay protection, a server-side kill switch, order-rate control, time-window guards and immutable audit storage.

The app trading model remains: Wave 1 is based on rolling learned Multify averages and mandatory trailing protection; AUTO may use long and eligible short legs, LONG only long, SHORT only short. Waves 2-10 use the trajectory/EV engine and can recommend or execute one side only. New Multify recommendations receive the highest processing priority. A prior Wave-1 loser is frozen from new churn while protection continues; a profitable prior Wave-1 campaign may be flattened/handed off so the new call can fast-track.

The Android app still requires the final transport refactor described in `LIVE_GATEWAY_INTEGRATION.md` so live mode uses the gateway instead of direct phone-to-broker credentials.
