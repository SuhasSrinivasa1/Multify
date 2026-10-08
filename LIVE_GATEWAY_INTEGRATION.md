# Live Gateway integration contract

Version: 4.5.0-live-gateway

The intermediary is required for the production design because the phone should not own the broker secret and mobile IP addresses are not a reliable fixed outbound identity. The Android app remains the Multify notification listener, UI, wave/trajectory engine and local learning node. The gateway is the static-IP broker execution boundary.

For the final Android integration, create a `LiveGatewayApi` Retrofit interface and an OkHttp HMAC interceptor. Add encrypted settings for gateway base URL, client ID and HMAC secret. Remove Groww API key/TOTP from the Android live path. All live profile/margin/position/quote/candle/order/OCO calls must flow through the gateway. The server response must be reconciled against Room ledgers before a new live order can be submitted.

The first-wave priority rules stay inside the app state machine. A new Multify recommendation is an urgent event. Existing profitable first-wave exposure may be flattened and the new recommendation receives the fast lane. Losing first-wave exposure is frozen from further churn for the day, while its stop/protection remains managed. Waves 2-10 can operate on up to two symbols after first-wave completion. If only one wave is enabled, later waves remain live predictions only and must never place orders.

Every live request needs a deterministic idempotency key derived from trading date, Multify event ID, symbol, wave, side and logical action. Never generate a random idempotency key during a retry.
