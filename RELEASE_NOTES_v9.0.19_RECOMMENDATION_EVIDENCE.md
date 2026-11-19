# Multify Trader Pro v9.0.19 — Recommendation Evidence (TEST)

**Base:** `v9018-operator-clarity-20261119`, snapshot `08beb06581844df3fa2c7cf18fa5542bb50bce49`.
**Log source inspected:** `MultifyTraderPro-logs-v9.0.18-operator-clarity-debug-20261119-135934.zip` (19 Nov 2026, 13:59 IST), in addition to earlier overlapping exports. 12 published Fixed events, 4 sampled target hits; most recent KARURVYSYA and GLOBUSSPR repeats remained censored/unresolved.

## Additive changes — no frozen core changes
1. **Official Fixed PRIMARY identity retained on-screen.** The recommendation can remain confirmed even when a new BUY is blocked, executed or its target reached. UI does not claim the session ended after an Auto-Buy.
2. **Only official confirmed Fixed recommendations in the main trading list.** Earlier unconfirmed PRIMARY watch remains in diagnostic storage/exports; removed from the operator Fixed screen only.
3. **Marketability hint from already held quote.** Show upper-band/no-sell-offer and no-sell-offer observations distinctly. The short client-receipt observation window is 8s; older quotes become `STALE_CLIENT_QUOTE` rather than asserting the upper circuit still holds. Actual BUY still performs its own fresh Alpha broker preflight; displayed quote is NOT exchange tick age or a promise of fill.
4. **Existing broker BUY execution guard unchanged.** `CircuitAwareBuyPolicy` still fails closed for missing seller, missing circuit, stale quote and out-of-band offer. A no-seller observation can disable the operator's manual button; nothing is submitted to Groww from this display classification.
5. **MCQ = Momentum Continuation Quality, shadow-only.** Existing 15m evaluations are analyzed asynchronously for volume participation, 15m volume ratio, RSI and slope, EMA9/20/50 alignment, EMA/VWAP slopes, MACD slope and 52-week coordinate. Globus/Liberty vs Karurvysya observations are *hypotheses*; all labels are unverified until multiple independent sessions and matched horizons demonstrate prospective utility. Extremely high RSI is NOT a solo negative filter.
6. **Null-safe raw broker-book shadow.** Missing ask/spread are explicitly null, not spuriously interpreted as perfect liquidity merely because frozen feature placeholders were zero. Frozen factors remain byte-for-byte unchanged.
7. **UI transition audit.** A bounded client-Compose (not physical pixel paint) event trail captures changes in button label, enablement, marketability observation and official recommendation state. Exported as `fixed_operator_ui_transitions_v9019.json`. No continuous broker polling.
8. **Frozen core regression tests.** Source byte locks for scoring/discovery, 15m production, publication, original target, original execution/GTT/trailing, Alpha-only orders and Beta read-only. Android unit + replay tests and APK signature verification in CI.

## Deliberately not changed
- Frozen 40-factor model, 15m production candles, PRIMARY/SECONDARY qualification, candidate universe, rank, original target.
- Auto-Buy enable/disable settings, actual broker BUY/SELL/GTT flows, trailing arm 0.55%, gain 0.50%, ratchet 0.10%, currently deployed hard stop -3%.
- Max concurrent holdings, capital limits, Alpha/Beta routing, request pacing, quota values, recovery rules or NSE full-universe LTP batches. Existing API 429s, slow scans and caching improvements are **not** claimed solved in v9.0.19.
- The discrepancy between stated historical -2% initial stop and deployed -3% remains pending approval; do not quietly change live protection.

## Safety / installation
This is a **TEST package**, not a live broker verification. Before any in-place update compare its signer certificate against the installed **v9.0.18 APK**. Different debug signing certificates mean Android update is impossible. Never uninstall to work around an update error with open positions, pending GTTs, credentials, unreconciled orders or unexported logs. Existing position protection must continue independently of the Fixed operator UI.

### Confirmed TEST APK signing compatibility — blocked
The CI artifact `Multify-v9.0.19-Recommendation-Evidence-TEST-debug` has a valid Android v2 signature, but signer certificate SHA-256 `6e0fc9f69890c8a47eaeb3612e7fb71ea1bf567d3bd1bdae5ce19ed74afe982b`.
The previously distributed v9.0.18 Operator Clarity TEST APK uses signer SHA-256 `9021b05e9a18113b689a4df2e6f242c9a70ad0cd10f8f2a8daa0b631e3b68e9c`.
**These certificates do not match. Normal in-place installation over the supplied v9.0.18 APK will fail.** This is a TEST package for isolated environments, not a safe production upgrade. Do not uninstall the live app to bypass this while broker GTTs, holdings, credentials or unexported state exist. Recover the current signing private key in your secure build environment, or plan an explicit no-open-holdings migration with verified broker-side state and backups. Never post private signing keys in chat.

## Research acceptance gates for a separate later release
- Upper circuit classification must correlate with a documented broker upper price limit and actual sell-book availability; +5%/+20% move alone never implies a locked band.
- For each candidate/repeat, reconstruct recommendation ID, first PRIMARY, publication, quote-client times, sampled +0.5/+0.55/full target and stop-first with matched horizons; unresolved stays censored.
- Track independent day counts, net costs and out-of-sample results. A perfect score on a tiny postselected set is not a certified working range.
- Measure API 429 and scan durations after any future scheduler optimization; never trade on cached/stale quote.
