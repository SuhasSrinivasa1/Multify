# Multify Trader Pro v9.0.20 — Working Range Toggle / API Evidence

**Build type:** TEST candidate.  
**Base:** v9.0.19 Recommendation Evidence.  
**Fixed production model:** v7.4.1, 40 factors, 15-minute inputs — unchanged.

## Operator behaviour

- New Settings toggle: **Working Range**, default OFF.
- OFF: existing Fixed continues as before.
- ON: a separate Working Range pilot observes the same already-fetched Fixed evaluation and broker quote. No additional Groww requests.
- Fixed recommendations and Working Range candidates are stored and displayed separately.
- Fixed cards are labelled `FIXED · FROZEN v7.4.1`.
- Pilot cards are labelled `WORKING RANGE · PILOT`.
- Turning Working Range off pauses new pilot observations; it does not remove existing Fixed recommendations.
- Working Range never enables Fixed Auto-Buy and has no automatic BUY, SELL or GTT. Cards are manual-review only.

## Working Range v0.1 pilot envelope

The boundaries are retrospective candidates from 19-Nov-2026 logs, not a statistically validated working range. The earlier 9/9 success statement was incorrect: the same candidate envelope also admits GPIL, which had no sampled target touch and a -2.44% adverse excursion by the 15:07 export. Under the currently deployed -3% stop, that sample did not establish a -3% stop touch. The pilot must retain this known false-positive example.

Technical conditions:
- RSI >= 75; no upper RSI ceiling.
- RVOL >= 1.0.
- Green-volume share >= 80%.
- Volume acceleration between -0.50 and +30.0.
- EMA9 > EMA20 > EMA50.
- EMA9 and EMA20 slopes positive.
- MACD histogram slope positive.
- Fixed reference target requirement <= 1.60 ATR.
- At least 38 minutes to NSE close.
- Two consecutive matching Fixed 15m-evaluation sweeps, no more than five minutes apart.
- Candidate age from first Working Range-qualified client observation <= 36 minutes.
- Price drift from first Working Range-qualified observation <= +1.60% at confirmation.

VWAP extension, 52-week range position, RSI absolute upper bound, order-book imbalance and spread are recorded for review but are not single-factor technical vetoes. Marketability is separate: a candidate can remain visible at upper circuit or when no sell offer is observed, but the card explicitly says entry is unavailable/unknown. A sell offer is not a fill guarantee. Client quote receipt time is not verified exchange tick age.

The pilot target uses the unchanged Fixed reference target percentage. The candidate filter does not change Fixed scoring, ranking, PRIMARY/SECONDARY confirmation, publication, or target logic. It is not independently validated and should not be interpreted as a 100%-accuracy strategy.

## Outcome evidence

Working Range cards retain entry and target references, sampled MFE/MAE, and sampled first target/stop-threshold timestamps. These are sparse observations from client LTP snapshots, not intrabar price paths or realized P&L. The currently deployed -3% hard-stop threshold is used for the research comparison. Unobserved outcomes remain unresolved/censored.

## Groww API diagnostics and safety

- Alpha permit-wait audit now records every positive local wait once per admitted request, matching the request-level permit-wait counter (previously only waits >=500ms were in the audit trail).
- Beta now exports per-account Live/Historical wait request counts and wait milliseconds.
- Beta 429 events record raw `Retry-After`, applied local cooldown, incident count and wire counter context; this does not imply the published Groww category limit was breached.
- Protection-priority quote requests bypass the hot market-data cache and in-flight coalescing, ensuring a fresh request reaches Groww. Ordinary research quotes retain cache/single-flight behaviour.
- Existing Alpha historical serialization, completed-history reuse, Alpha quote/LTP cache/single-flight, broker inventory read coalescing, Beta discovery reserve, Beta read-only separation and half-open recovery are preserved.
- Published Groww limits, local quota values, 90/300/900-second recovery policy, and Beta 40/minute allowance are not changed.
- No third account and no additional LTP universe sweep is introduced.

## Frozen and unchanged

- Fixed v7.4.1 40-factor scoring, 15m production inputs, candidate universe, rank, PRIMARY/SECONDARY confirmation, publication and reference calibration.
- Fixed original target and all existing Auto-Buy safeguards.
- Alpha-only real orders; Beta read-only.
- Actual BUY/SELL/GTT execution, broker reconciliation, holdings ownership and monitoring.
- Current deployed hard stop -3%, +0.55% trail arm, +0.50% protected gain and +0.10% ratchet.
- Forecast remains frozen.
- No source changes to Fixed model or target/SELL/GTT/trailing policy.

## Deployment / signing

The v9.0.19 TEST APK was signed with SHA-256 certificate `6e0fc9f69890c8a47eaeb3612e7fb71ea1bf567d3bd1bdae5ce19ed74afe982b`, which differed from the prior v9.0.18 stable APK signer `9021b05e9a18113b689a4df2e6f242c9a70ad0cd10f8f2a8daa0b631e3b68e9c`. v9.0.20 CI restores the stable signing key from GitHub Actions secret `MULTIFY_KEYSTORE_BASE64` when configured and reports compatibility against both fingerprints. Never uninstall the installed trading app to work around a signer mismatch while holdings, GTTs, credentials or unexported state exist.

## Required validation

- Android unit tests and source-level Frozen Fixed regression checks pass.
- Run the pilot over multiple independent sessions and holdout days.
- Track all candidates, including misses and untradeable circuit-locked events.
- Do not promote Working Range to Auto-Buy or claim a validated range until independent out-of-sample results support it and the operator explicitly approves.
