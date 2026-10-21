# Multify Trader Pro v7.5.1 — Fixed Engine with Embedded Historical Intelligence

v7.5.1 makes the Fixed/Forecast architecture self-contained for a fresh installation.

## What was embedded

The build merges every Multify diagnostic export available during the build:

- v7.1.0 export from 2026-10-19
- v7.2.0 export from 2026-10-19
- v7.3.2 export from 2026-10-21 11:51 IST

The merged, de-duplicated seed contains:

- 108 historical Multify learning calls
- 10 historical published Forecast recommendations
- 4,370 Forecast research observations
- 2 champion states
- 2 after-market research reports

The latest exported recommendation state contains five October 21 PRIMARY Forecast rows:
SWARNSAR, LTTS, LAGNAM, GENSOL and MARALOVER.

No later v7.4.1 device export was available in the build inputs, so recommendations or learning created only on-device after the 11:51 IST export cannot be reconstructed.

## Why today's Forecast was not fully fresh

The adaptive Forecast engine combines live market data with historical state. In the frozen v7.4.1 mechanism, historical inputs can include:

- rolling Multify MFE history used in target calibration
- recent Multify symbol-frequency evidence
- frozen observable Multify reference cases
- historical Forecast outcomes that meet the positive-close learning rule
- champion state
- completed positive-close research evidence when available

The uploaded research observations had no completed outcome rows at the freeze point, so they did not contribute a research-evidence ranking bonus to the recommendations observed that day. They are still embedded so adaptive Forecast can later backfill and learn from them.

## Fresh-install behavior

A fresh v7.5.1 installation no longer requires a manual ZIP restore to initialize Fixed.

On first launch:

1. The embedded historical bundle is checksum verified.
2. Adaptive Forecast is seeded with the embedded learning calls, historical forecasts, research observations, champions and reports.
3. Fixed is restored from the deterministic immutable snapshot built from the same historical evidence.
4. Fixed never accepts future adaptive learning.
5. Forecast continues to learn normally.

If an upgraded installation already contains richer on-device learning, the app preserves that device state and only fills missing historical evidence from the embedded bundle before sealing Fixed.

## Fixed remains immutable

Fixed retains the frozen v7.4.1:

- local strategy/feature engine
- 40-factor, three-family model
- Forecast quality gates
- publication/confirmation policy
- market-discovery ranking
- positive-close policy
- target-learning math
- Multify observable reference cases
- embedded historical learning/reference state

Fixed outcome monitoring is diagnostic only and never changes Fixed ranking or targets.

## Execution remains independent

Forecast and Fixed retain independent auto-buy toggles under the common ARM master switch.

Both use:

- published recommendation entry as a Groww GTT trigger
- trigger direction UP
- CNC MARKET child order
- published target left fixed

Forecast and Fixed may independently recommend and own the same symbol.

Version: `7.5.1-fixed-embedded-history` (`versionCode 751`).
