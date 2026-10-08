# MASTER PROMPT — Build, Commit, Integrate and Generate Multify Trader Pro v4.5.0 Live APK

You are responsible for completing, validating, committing and building the entire Multify Trader Pro Android application and its live-trading intermediary service. Do not return a design-only answer. Work directly on the supplied source tree, preserve existing behavior unless this specification explicitly changes it, run tests, commit all non-secret source to GitHub, trigger the build, and return the resulting APK artifact plus commit SHA and validation results.

## Starting inputs

Use the supplied `Multify-TraderPro-v4.5.0-live-gateway-source.zip` as the canonical starting source. The Android package/application ID is already established in the project. The target GitHub repository is `SuhasSrinivasa1/Suhas-Private-AI-Trader`. Put the complete Multify Trader Pro project in a dedicated path such as `android-stable/multify-traderpro-v450-live-gateway/` unless the repository already contains a more appropriate canonical Multify Trader Pro path. Do not overwrite unrelated projects in that repository.

The existing source already contains the notification listener, Room learning/history, rolling Multify averages, Wave 1 logic, the 10-wave trajectory engine, priority fast-track behavior, risk controls, Groww models, OCO/trailing-stop logic and the new `backend/` live gateway scaffold. Treat those files as source code to complete, not documentation to ignore.

## Mandatory architecture

Production LIVE mode must use the intermediary/static-IP gateway. The Android phone must not directly hold the Groww API key or Groww TOTP seed and must not submit live broker orders directly from a mobile-network public IP. The backend runs on a host with fixed outbound IPv4, owns the Groww credentials, authenticates the phone, performs broker API calls, enforces server-side idempotency/risk/rate limits, logs decisions and acts as the only live-order execution boundary.

The Android app remains responsible for receiving Multify notifications immediately, parsing the recommendation, maintaining the UI, computing Wave 1 rolling-average behavior, computing Waves 2-10, maintaining stock-specific learning and deciding the requested logical action. The backend receives a deterministic trade intent and independently applies hard risk/position/idempotency checks before submitting it to Groww.

Use HTTPS only. Every protected phone→gateway request must be HMAC-SHA256 signed using the exact contract documented in `backend/README.md`: client ID, Unix timestamp, unique nonce and body hash. Store the phone HMAC secret with Android Keystore-backed encryption. The Groww API key and TOTP seed must exist only in backend environment/secrets. Never commit them.

Complete the Android transport integration before building the final APK. Add a `LiveGatewayApi` Retrofit client and HMAC signing interceptor. In LIVE mode, route all Groww account/position/margin/quote/candle/order/OCO traffic through the gateway. Paper/shadow mode may use the same gateway for market data or a safe direct market-data path, but no direct phone-to-Groww live order submission may remain reachable. Broker position state is authoritative; any mismatch between broker net MIS position and app-owned ledger halts new trading.

## Trading behavior that must be preserved and implemented

Wave 1 is special and must remain separate from Waves 2-10. Wave 1 is driven by the rolling daily Multify averages. Seed from the historical spreadsheet/history already in the project and then maintain a rolling window of the most recent 30 trading days, dropping the oldest trading day when a new trading day is added. Keep distinct long behavior and post-sell/short behavior. Continue learning every completed Multify call and every shadow/live outcome.

The user has three Wave 1 modes. `AUTO` allows the Wave 1 long campaign and the eligible Wave 1 short campaign according to the learned averages. `LONG` allows only the long campaign. `SHORT` allows only the short campaign. The UI must still show live observations and predictions even when a side is disabled. Trailing stop protection is mandatory for every live Wave 1 position in every mode. A filled position must never remain unprotected while the app waits for later logic.

The Wave 1 processing lane has the highest runtime priority. The critical sequence is: new Multify notification → parse/qualify → Wave 1 entry if eligible → protect with stop/trailing/OCO → manage exit → if AUTO and post-sell short is eligible, short entry → protect short → short exit. Notification processing and exit/protection tasks must not wait behind low-priority research, UI refresh, learning replay or Waves 2-10 calculations.

A new Multify stock recommendation is a priority event. Apply this exact handoff rule. If an older stock's active Wave 1 campaign can be flattened with a positive net result, close its profitable Wave 1 exposure and immediately fast-track the new Multify recommendation. If the older stock's Wave 1 exposure is currently negative/red, do not force-close it only to free capital. Freeze that older stock from any new averaging, new flips or new churn for the remainder of that trading day, while continuing its protective trailing stop/exit management. The new Multify recommendation still enters the priority queue and should receive available capital according to server risk limits. If the older stock has already completed Wave 1 and is profitably churning in later waves, it may continue while the new stock gets Wave 1 priority, subject to the maximum concurrent-symbol limit.

After a symbol has completed Wave 1, Waves 2-10 may be evaluated. There are exactly 10 proper waves total, not 20. Every next-wave checkpoint is a new LONG-versus-SHORT-versus-HOLD decision, not blind averaging. The engine must use price trajectory, VWAP, EMA structure, MACD/slope, ATR/volatility, relative volume, candles, opening-range/Donchian structure, bid/offer or available order-book imbalance, day percentage movement, market/index direction, sector relative strength, historical behavior of that exact stock and similar regime history. Fundamentals may be contextual but must not dominate minute-level execution timing.

The direction model must classify at least `UP_TREND`, `DOWN_TREND`, `OSCILLATING` and `NEUTRAL`. Up-trending names should favor long continuation, down-trending names should favor shorts, and oscillating names may profit from alternating directions across different waves. Only one broker net direction may exist for a symbol at a time. A reversal requires flatten/reconcile first and then a stronger confidence/EV hurdle than same-direction continuation. HOLD is valid and must be chosen when neither side clears costs, slippage and risk thresholds.

If the settings say only one active wave, only Wave 1 can execute. Waves 2-10 must still calculate continuously as shadow/live predictions and remain visible in the table. The predicted LONG or SHORT cell for the next wave should be green when it clears the decision threshold; HOLD remains neutral. Preview waves must never place an order. If more waves are enabled, only waves `<= activeWaveCount` are eligible for live execution.

After Wave 1 completion the engine may manage up to two Multify symbols concurrently. In AUTO mode it chooses the better expected-net-value opportunity; in manual LONG/SHORT mode the table still displays both predictions so the user can choose the permitted side. The new Multify recommendation's Wave 1 always has scheduling priority over an older symbol's Wave 2-10 work.

Build persistent per-stock wave profiles. For each symbol and each wave record direction, entry context, MFE, MAE, realized net P&L, reversal probability, time-to-peak, volume/RVOL, VWAP/ATR state, market regime, sector regime, successful long percentage and successful short percentage. When the same stock appears again, use its history as a prior but let current live evidence override old history as fresh observations accumulate. Do not overfit a single previous day.

Trailing stops are mandatory. They must ratchet only in the favorable direction and never widen once tightened. Use volatility/structure-aware trailing rather than a single arbitrary percentage. Preserve emergency flatten behavior if protection cannot be created after a fill. Every live position needs a hard invalidation stop, a target/exit plan, a time stop, slippage guard, stale-data guard and end-of-day force-flat buffer.

## UI/table requirements

Keep the existing Wave table and make it useful even when execution is limited. For each symbol show the current live price/context, Wave 1 learned long/short averages, the selected first-wave mode, and Waves 1-10 with separate LONG and SHORT cells. The algorithmically preferred side should turn green; HOLD should not color either side green. Show whether a wave is `LIVE`, `EXECUTABLE`, `PREVIEW`, `FROZEN`, `PROTECTED`, `EXITING` or `COMPLETED`. Show a concise reason/confidence and expected-net-value difference without cluttering the main table.

When a new Multify recommendation arrives, visibly identify it as `PRIORITY WAVE 1`. If an old losing Wave 1 symbol is frozen from new churn, show `FROZEN — PROTECTION ONLY`, not as an active averaging candidate. Do not hide live prediction data merely because only one wave is enabled.

## Live gateway completion requirements

Finish the supplied `backend/` service and harden it. It must expose health, broker profile, margins, positions, holdings, quote/candles, idempotent order submission, order status, OCO create/modify/cancel and a kill switch. Keep all secrets outside Git. Persist nonce replay protection, idempotency records and audit events. Enforce a server-side rate limit comfortably below broker/exchange limits, a maximum order notional, symbol exposure cap, maximum open symbols, daily loss halt, market-time gate and no-new-order cutoff. The kill switch must block new risk immediately but still allow protective exits/force-flat actions.

Before every risk-changing order, reconcile the broker position and the app's strategy intent. Do not blindly retry an ambiguous order submission. Recover using deterministic order reference/idempotency state and broker order status. Never allow two different request bodies to reuse the same idempotency key.

A deterministic live idempotency key must be derived from trading date + Multify event ID + symbol + wave + side + logical action. A network retry must reuse that same key.

## Security and secrets

Do not commit broker secrets, TOTP seeds, HMAC secrets, `.env`, access tokens, signing passwords or keystores. Add/verify `.gitignore`. Because the prior Android app was uninstalled, a new signing certificate may be used now, but it must be persistent for future upgrades. Generate one release keystore exactly once if no persistent keystore already exists, then store the keystore as base64 in GitHub Actions secret `MULTIFY_KEYSTORE_BASE64` and its password in `MULTIFY_KEYSTORE_PASSWORD`. Never print either secret in logs.

Use GitHub Actions for repeatable builds. The workflow must run backend tests and Android unit tests, then build a signed release APK and upload it as an artifact. Until live validation is complete, do not optimize for minimum APK size; keep release minification/resource shrinking disabled if they introduce any risk to Hilt/Room/Retrofit behavior. A larger reliable APK is preferred over an aggressively shrunk build.

## GitHub procedure

Create a feature branch from the current `main`, copy the entire project source into the canonical Multify Trader Pro directory, integrate the gateway transport, add/update tests and workflow, and commit all non-secret files. Use a clear commit message such as `Multify Trader Pro v4.5.0 live gateway and priority wave engine`. Run tests before pushing. Push the feature branch, open a PR if repository practice requires it, otherwise merge/fast-forward to `main` only after tests are green. Report the final commit SHA and repository path.

After the code is committed, trigger the `Multify Trader Pro v4.5.0 Build` GitHub Actions workflow. Wait for completion. If the build fails, inspect the failing job logs, fix the code, recommit and rerun until it passes. Download the final signed release APK artifact. Verify the APK package name, versionCode `450`, versionName `4.5.0-live-gateway`, signing certificate, manifest permissions and that it installs as a fresh install.

## Required tests and validation

At minimum, run Android unit tests for notification parsing, rolling-learning math, Wave direction math, market trajectory classification, first-wave priority handoff, frozen-loser behavior, activeWaveCount preview-only enforcement, deterministic idempotency generation, trailing-stop monotonicity, risk halt, and new-recommendation fast lane. Add backend tests for HMAC validation, nonce replay rejection, idempotency replay, conflicting idempotency rejection, kill switch, time gate, order-notional cap, and mocked Groww order/position reconciliation. Do not use the real broker account in automated CI tests.

Perform a final static review to confirm there is no code path that can submit a live order directly from the Android phone in LIVE mode and no code path that can execute Wave 2-10 when the wave is only marked PREVIEW. Confirm every filled live position immediately enters protection or emergency flatten logic.

## Final deliverables

Return the GitHub repository path and final commit SHA, a concise test/build report, the GitHub Actions run/artifact details, the signed release APK file, its SHA-256 checksum, and a short deployment checklist for the static-IP live gateway. If a required secret or static-IP deployment value is unavailable, complete everything else and state exactly which secret/value the owner must provide; do not invent credentials or silently fall back to insecure direct phone trading.
