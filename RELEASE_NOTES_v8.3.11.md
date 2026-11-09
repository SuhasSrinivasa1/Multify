# Multify Trader Pro v8.3.11 — Fixed Auto Buy independent of portfolio recovery

## Targeted issue

On November 9, 2026, Groww's holdings/positions snapshot did not yet reflect a just-filled REPCOHOME CNC BUY. Immediate SELL-GTT ownership verification therefore saw a null holding quantity and zero CNC quantity, although the app had a confirmed 75-share fill. The protection recovery state then blocked an unrelated WHIRLPOOL recommendation and subsequent Fixed Auto Buy scans.

## Targeted correction

- Fixed Auto Buy no longer requires the global `tradingRecoveryReady` flag. Portfolio reconciliation continues independently in the position-monitor worker.
- A pending protection issue on a different app-owned symbol no longer blocks a new Fixed Auto Buy. The new symbol must still pass a fresh Groww inventory check, no-existing-position check, account/IP/DDPI/safety checks, quote freshness, liquidity, price, budget and duplicate-order safeguards.
- Immediately after a confirmed Fixed Auto Buy, the hard-stop GTT may use a short-lived (120-second) post-fill ownership proof. This proof is available only to the Fixed auto-buy path after its pre-order broker inventory check verified zero existing quantity. App ownership, one local position for the symbol, and active-GTT conflict checks remain mandatory.
- If Groww still refuses or cannot verify the new GTT, the holding remains explicitly unprotected, triggers the existing critical alert, and is retried by portfolio monitoring. The recovery flag remains meaningful for other execution modes; it no longer freezes Fixed Auto Buy for unrelated symbols.
- Manual Fixed BUY, Multify ARM, and other execution paths retain their existing recovery gate.

## Unchanged

- Fixed selection, 40-factor scoring, PRIMARY confirmation and publication.
- Fixed 15-minute candle logic, target and scan cadence.
- Same-symbol broker/app ownership and duplicate checks.
- Hard stop, stepped trailing SELL and GTT lifecycle policies.
- Groww API rate-limit/cooldown policy.

## Verification

The dedicated GitHub Actions workflow runs frozen Fixed/SELL/GTT source checks, backend tests, Android unit tests, APK compilation, APK signature verification and artifact packaging.

A successful CI build is engineering verification only. The next live session must confirm that a protection-recovery delay on one symbol no longer suppresses later distinct-symbol Fixed BUYs, and that the 120-second proof resolves the observed Groww inventory visibility lag. Do not assume a new fill has verified protection until the broker GTT status is confirmed.
