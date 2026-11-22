# Phayra v9.0.36 TEST — immediate 3M and continued 6M lows

## Low-range screener
- Add 3M / 6M selector; default to 3M so validated existing history can be used immediately.
- 3M readiness requires at least 55 validated NSE EOD sessions spanning at least roughly 2.5 months.
- Each symbol must have at least 85% of the observed sessions, with a 50-session minimum.
- 6M remains separately gated at 105 validated sessions spanning roughly six months; its original gate is unchanged.
- Both modes rank 0%–10% above their own period low.
- Each result shows latest close, period low, date of period low, 1M low, distance above 1M low, valid symbol sessions and latest EOD date.
- Both modes include ticker filtering and a manual Scan Now button.
- 3M Scan Now is local-only and makes no network or Groww requests.
- 6M Scan Now requests at most 25 missing official NSE CM-UDiFF session archives only during 18:30–08:00 IST; otherwise it is local-only.
- Existing WorkManager best-effort after-hours six-month backfill remains active.

## Data integrity and frozen boundaries
- Only validated official NSE EQ EOD session archives are counted.
- 3M and 6M readiness are independent; incomplete windows show no low claims.
- NSE EOD prices remain unadjusted for corporate actions.
- No Groww calls are added to the screener.
- Fixed 40-factor scoring, 15-minute production inputs, confirmation, targets, SELL/trailing thresholds and Forecast remain unchanged.
- Working Range, Manual MIS, Alpha-only execution, Beta read-only, GTT protection and reconciliation remain unchanged.

## Build / install
- Version 9.0.36 TEST, package suffix `.v9036test`.
- Installs side-by-side with Phayra v9.0.35 and the live app; it does not inherit local databases.
- Keep the existing live app installed and responsible for live positions and broker-side GTTs.
- CI must pass and on-device 3M/6M screens must be checked before production use.
