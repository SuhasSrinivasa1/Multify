# Fixed Pro Lite v1.0.1 TEST — Material 3 + replay acceptance

## Purpose
Replace the overlarge, text-heavy v1.0.0 experience with a compact Android Material 3 two-tab screen. Keep Fixed scoring, 15-minute inputs, Working Range selection and existing broker protection unchanged.

## UX changes
- Standard Material 3 top app bar, proper status bar contrast, light/dark theme, compact broker status indicators and only two bottom tabs.
- Fixed: compact frozen-strategy metadata, scanner/Auto-Buy permission status, session-aware discovery status, market-data age, broker GTT health, and actual recommendation rows.
- Working Range: two-scan pilot and independently confirmed candidates, with distinct research-only and Auto-Buy statuses; no automatic promotional win rate.
- Separate header actions for Averages and Settings.
- Responsive price/metric tiles for narrow devices or large accessibility font scale; horizontally scrollable chip rows; at least 48dp primary action targets.
- Default unconfigured/closed-session state no longer implies a zero-coverage scan failure: no baseline percentage is displayed until verified.
- Live BUY UI is disabled unless existing local Alpha/static-IP and NSE session predicates allow entry. Backend still revalidates all broker and order safety checks.
- API credentials/TOTP values no longer persist in Compose saved instance state. Existing encrypted storage remains.
- Disabled scanner does not disable broker-side GTT/SELL monitoring; Auto-Buy remains a separate explicit real-order permission.

## Replay/verification
- Byte-for-byte parity checks against v9.0.25 for 16 strategy/entry/exit/broker-account policy files, including frozen Fixed, Working Range, hard stop, trailing, acknowledgement and Beta read-only boundary.
- Full local Android unit suite and deterministic Fixed Lite replay tests include publication freshness, peak-risk overlay, Working Range two-scan confirmation, runup/late-entry limits, source-isolated -1% exit, null order ID, GTT verified/unverified, Alpha/Beta permissions, and NSE session cutoffs.
- Python backend tests: master-log deduplication fixtures, adaptive timeframe replay, first-PRIMARY peak evidence, and optional Fixed stream shadow research. All are offline; no broker calls.
- Android emulator Compose UI smoke tests for fixed waiting/closed-session state, PRIMARY/manual BUY presentation, Working Range rows, and only two bottom tabs.
- Build provenance, SHA-256 and signer certificate are captured in CI.

## Important limits
- This is a TEST candidate. Offline replay cannot guarantee 100% correctness, real Groww order ack/fills, broker-side GTT activity, source tick age, crash-free operation across devices, or superior trading outcomes.
- Scan speed must be measured on identical candidate universes and overlapping live market conditions with Alpha/Beta API budgets and broker 429 rates. No proven production scan speed increase is claimed by this UI update.
- Only the prior single-day diagnostic corpus is described in earlier reports. There are no new live v1.0.1 execution logs yet.
- The inherited Fixed hard stop remains -3% in the deployed source, despite an earlier -2% requirement; DO NOT silently change it.
- A CI runner-generated debug signing certificate is not guaranteed to match the installed Lite v1.0.0 APK. Do not uninstall either trading app while app-owned holdings, GTT, pending orders or unexported state exist. Use a stable signing key to permit safe upgrades.
- Alpha exclusively executes and protects app-owned holdings; Beta is read-only. Running two app instances against the same Alpha trading account is unsafe.
