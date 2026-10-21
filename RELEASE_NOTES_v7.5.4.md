# Multify Trader Pro v7.5.4 — Final Fixed v7.4.1 Lock

The final Fixed recommendation contract is grounded in:

- Log export: `MultifyTraderPro-logs-v7.4.1-forecast-gtt-autobuy-debug-20261021-190239.zip`
- Log SHA-256: `d807204a6d5d71a69f45ef4d21d7ef63063b85b56d401a6795e8f265a9f393fe`
- Exact v7.4.1 source commit: `ca0eecaf63d2cc3500e11fd73f8de9bf2d87e39f`
- Final contract id: `FIXED-V741-20261021-190239-FINAL`

## Log audit

The accepted session produced eight Forecast recommendations:
UTTAMSUGAR, CHEMBONDCH, BOHRAIND, ORIRAIL, DWARKESH, SHAHINVEST, TCC and IPCALAB.

Six of eight reached the published target. Under the user-requested Fixed-only >₹100 rule, five were eligible:
UTTAMSUGAR, CHEMBONDCH, ORIRAIL, SHAHINVEST and IPCALAB; four of those five reached target.

## Final corrections before freeze

1. The >₹100 rule is now a final Fixed publication/execution overlay only.
   - Stocks below ₹100 still participate in the exact v7.4.1 discovery universe, breadth calculation, factor model and ranking.
   - This is required for a one-to-one model reproduction because filtering them earlier materially changes market breadth and the discovered top-28 universe.

2. Fixed no longer inherits any prior Forecast, champion or completed-research feedback.
   - The accepted v7.4.1 session began without those feedback channels.
   - Fixed therefore cannot acquire future Forecast/champion/research learning.

3. The Fixed historical Multify state is deterministic on every installation.
   - Exactly 107 canonical learning calls are used.
   - The older merged-only SPORTKING row is excluded because it was absent from the accepted final v7.4.1 log.
   - Frozen rolling Multify MFE calibration: 1.1161043030113955%.

4. Fixed never captures richer on-device adaptive state on upgrade.
   - Every installation derives the same immutable Fixed snapshot.

## Deliberate Fixed-only overlays retained

- Recommendation entry price must be strictly above ₹100.
- Recommendation session: 09:15–15:30 IST.
- Fixed can use remaining CNC cash when it is below the configured per-call budget.
- The underlying v7.4.1 recommendation model remains unchanged.
- Common stepped trailing exit behavior remains unchanged.

## Source lock

The seven core Fixed forecasting modules are continuously compared in CI with the exact v7.4.1 source commit after only the `Fixed` naming prefix is normalized.

A Git-object lock manifest also pins the Fixed-only model files and the marked Fixed coordinator region in `TradingRepository.kt`. Any accidental change to those locked files/region causes the verification build to fail.

This is a source/build safeguard, not a claim that a repository owner with authority to rewrite source and CI can be made technically incapable of creating a different future application. A built and signed APK itself cannot change unless another APK is installed.
