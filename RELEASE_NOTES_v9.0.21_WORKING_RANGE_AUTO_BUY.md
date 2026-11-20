# v9.0.21 Working Range Alpha Auto-Buy — TEST / operator rollout gate

## Evidence reviewed
Uploaded `MultifyTraderPro-logs-v9.0.20-working-range-debug-20261120-102011.zip`, exported 2026-11-20 10:20:16 IST.
Working Range toggle was enabled but Auto-Buy was **not** implemented in that installed release. Four pilot names had confirmed twice: ALICON (sampled target), KALPATARU (weakened, -1.84% sampled MAE), NUVOCO (+0.49% MFE / -0.81% MAE), SGMART (+0.41% MFE / -0.11% MAE). This is NOT a validated or reliably profitable strategy. Three other names were pending/dropped.
OAL was **Fixed**, entry ₹461.25, target ₹466.40, sampled MFE +1.073% (<+1.116% full target) and sampled MAE -0.455%; unresolved at export. Around 10:02 IST OAL had a green-volume share 62.2% (below the pilot's 80%), and EMA20 was below EMA50. Do not widen the live Working Range envelope from this one near-miss. Retain its rejection evidence for end-of-day review.
Two **Fixed**, not Working Range, broker-confirmed closed trades were ALICON (net estimated -₹29.69) and KALPATARU (net estimated -₹1,841.65). ALICON's positive sampled recommendation path is not proof of a profitable broker trade.

## New behavior
- Existing **Working Range** Settings switch is now the explicit permission for BOTH the Working Range pilot shortlist AND **new** Alpha CNC Auto-Buy orders, after TWO matching evaluations from distinct official Fixed scans. Fixed and Fixed Auto-Buy remain fully independent.
- A one-time migration switches an old v9.0.20 research-only ON setting to **OFF**. Operator must manually turn ON again to authorize live BUY; never silently turn the old research toggle into real trades. Activation timestamp is persisted and pre-activation signals are not traded.
- The Working Range remains the same strict candidate envelope as v9.0.20; no change to EMA alignment, RSI, RVOL, momentum, target/ATR, target percent, time window, Fixed selection or rank. OAL stays OUT for now.
- The order path is Alpha-only with already proven preflight: fresh quote with verified sell offer & price band, no stale/overextended entry, current Alpha cash, holdings/positions/pending-orders, deployed capital, concurrent holdings, no unprotected app position, and session window. Beta stays read-only.
- One deterministic `WRM` order reference per confirmed event. Durable synchronous local `commit()` of intent **before** broker POST; if persistence fails, order is NOT submitted. An ambiguous outcome is reconciled by the exact broker reference; **never** blindly resubmitted. Unresolved WRM references from any date block new CNC BUY across engines.
- Confirmed/partially-filled orders enter the existing app-owned holdings lifecycle: immediate GTT attempt, broker verification, unprotected holding alert and priority recovery. Current production −3% stop, +0.55% trail arm, +0.50% protected gain and +0.10% ratchet are preserved. The older requirement stated −2%; still unresolved — do NOT alter risk policy silently.
- UI shows `WORKING RANGE · ALPHA AUTO-BUY`, execution state and broker fill after confirmation; Fixed cards remain labelled `FIXED · FROZEN`. Diagnostic export includes per-event intent/order states and preserves v9.0.20 pilot records as **read-only historical evidence**, never as executable new events.
- Actual entry is asynchronous and may be delayed or skipped by the 90-second scan/confirmation cycle, broker cooldown, cash/quote checks or protection priority. It is **not** guaranteed to BUY within 1–3 seconds or to fill at the recommendation price. Nothing bypasses market or broker safety.

## Must remain frozen
Fixed 40-factor scoring, 15-minute production inputs, market-wide discovery and top 28 candidate ranking, PRIMARY/SECONDARY confirmation, original 1.1161043% target, Alpha-only executable path, Beta read-only, SELL/GTT/trailing, Forecast, original Alpha/Beta budgets, and market session checks. No extra NSE LTP sweeps to feed Working Range.

## CI acceptance / deployment
- Compare frozen Fixed source byte-for-byte with historical contractual commits and replay accepted reference cases.
- Run all Android/JUnit tests including Working Range opt-in freshness, 2 distinct scans, one-shot order reservation, broker safety invariants and APK signature verification.
- No live Groww orders can be tested in CI. The APK is an isolated TEST candidate until an on-device broker-read-only smoke check, a carefully supervised first live BUY, and broker GTT verification.
- **Android signing identity is required for in-place install.** Compare the v9.0.21 APK signer against the **actual installed v9.0.20 APK**; don't uninstall to bypass signer failure with holdings, GTTs, credentials or unexported evidence. Rebuild with the trusted installation key in a secure CI environment if necessary. Never transmit keystore/private signing secrets through this chat.
- Risk note: observed KALPATARU Fixed broker-realized approx -₹1,841.65 and Working Range KALPATARU sampled -1.84% show the pilot can lose. Turning ON expressly permits live real-money trading; this is NOT a validated 100% working range.
