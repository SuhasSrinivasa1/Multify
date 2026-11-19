# Multify Trader Pro v9.0.18 — Operator Clarity (TEST)

Source: v9.0.17 Working Range Evidence (`ab3d2293ad3f4b0744ffcb721c9d3df69c255797`).
Latest inspected log: `MultifyTraderPro-logs-v9.0.17-working-range-evidence-debug-20261119-105440.zip`, Nov 19 2026, 10:54 IST.

## Measured evidence before this release
- 48 completed Fixed scans; 49 scan attempts with one deferred during broker cooldown. Median duration 118.39 seconds (not the earlier 9:46 median, which covered fewer scans).
- 10 distinct published Fixed recommendation events; one VEEFIN repeat after a separate setup. Four stored sampled target-hit recommendations, **not** four executed profitable trades.
- 4 broker-confirmed CNC BUYs: CUPID (Univest), AZAD, DLINKINDIA and DICIND. DICIND requested 35 shares but broker filled 4; the consumed event prevents blind re-BUY.
- Two broker-confirmed closed trades: CUPID estimated net +₹11.75, DLINKINDIA estimated net −₹11.57; **combined estimated net +₹0.17**, not the return of the 10 Fixed recommendations.
- Open app-owned AZAD (16 shares) and DICIND (4 shares) had active broker GTTs at export. The maximum concurrent holdings was 2; repeated later order attempts were rejected by this *working* safety gate. Do not bypass it.
- Four observed actual HTTP 429s: Beta quote 09:41:58, Alpha holdings 09:42:03, Alpha LTP 09:42:12, Beta LTP 10:05:26. Prior account-wide wire snapshot incorrectly displayed 0 while category buckets displayed actual errors.
- The fixed hard stop remains **-3%** in the live code/broker GTTs, while the earlier project requirement was **-2%**. The discrepancy has NOT been resolved or silently changed.

## Operator changes
1. New presentation-only `FixedOperatorButtonPolicy` clearly distinguishes BUY_EXECUTED, TARGET_HIT, past events, existing app-held positions, full app-owned holding capacity, broker readiness and market cutoffs. The UI no longer mislabels an executed BUY as `Session ended`.
2. The Fixed page shows current **app-owned positions/count cap**, and current hard-stop policy. A broker inventory check remains authoritative for externally owned holdings, and the UI does not add any Groww network calls.
3. Repair `BrokerWireAttemptLedger.finish` so actual wire response status, including 429 and errors, updates **account, category and endpoint** counters. This changes telemetry only, not request scheduling.
4. Version stamp 9.0.18; added `FixedOperatorButtonPolicyTest` covering normal BUY, executed BUY, held quantity, occupied holdings, capacity, market, stale prior event and broker unready cases.
5. Explicit Android CI workflow compares byte-locked Fixed selection, discovery, entry and protection source against prior contractual commits. Full unit tests and APK signing verification required before labeling as CI verified.

## Completely unchanged
- Frozen 40 factors, 15-minute production candles, PRIMARY quality and confirmation, target (1.1161043%), ~90-second scan target.
- Production recommendation ranking, candidate universe, original trade execution and stale-source checks, quantity sizing, existing two-holding limit, BUY/SELL/GTT logic and recovery safeguards.
- Alpha-only orders, Beta strictly read-only, +0.55% trail arm / +0.50% protected gain / +0.10% ratchet, and Forecast freeze.
- Research-only working-range telemetry with no new broker calls and no trading veto based on RSI/VWAP extension.

## Known limitations / operator cautions
- This is **not** a scan-speed optimizer. Measured scan duration worsened after 10:00 and the ordinary response-cache hit count remained 0. Do not claim a speed improvement from this patch.
- App-held position count may lag until dashboard refresh. The backend still checks authoritative Groww inventory, funds, and pending orders. An externally held EXIDEIND will not necessarily appear in the app-owned position list.
- DICIND's partial fill is displayed by its **actual open position quantity**, not a fabricated requested/filled ratio. Requested quantity lives in audit telemetry.
- **Confirmed signing incompatibility with the previously supplied v9.0.17 APK:** prior signer SHA-256 `ad5947506afeff796564be693315ebcbe7be7dfbde61deca382059ceb43a5602`; v9.0.18 CI signer `9021b05e9a18113b689a4df2e6f242c9a70ad0cd10f8f2a8daa0b631e3b68e9c`. Android **will not permit an in-place update** between those APKs. This TEST APK must not be presented as an installable replacement for v9.0.17. Do not uninstall with active GTTs, positions, credentials or unexported evidence. Recover the original signing key or design and verify a safe broker-state migration before operator deployment.
- Keep new automatic BUYs disabled pending explicit -2% vs -3% risk-policy reconciliation. Existing broker GTT/SELL monitoring should continue.
- This is a draft operator build; it must not be merged or represented as live broker validated merely because CI passes.
