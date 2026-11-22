# Phayra v9.0.38 TEST — AMFI market-cap badges

## What changed
- Every 3M and 6M prior-low screener result displays LARGE CAP, MID CAP, SMALL CAP or UNCLASSIFIED.
- The badge includes the AMFI rank when available.
- The screen displays the AMFI classification period and the rank-band legend.
- Classification is sourced from the latest dated workbook linked on AMFI's official categorisation page at build time.
- The build records the workbook URL, period, SHA-256 and NSE-symbol counts.
- Unmatched symbols remain UNCLASSIFIED; no rupee threshold or guessed category is used.

## Source and refresh model
- Official AMFI workbook is fetched only by the GitHub TEST build workflow, not by the Android app.
- The generated CSV is bundled as an APK asset. No runtime network request, NSE quote call or Groww API call is added.
- AMFI/SEBI rank bands: large 1–100, mid 101–250, small 251 onward.
- The displayed period is the source workbook's actual six-month classification period. The current official page's latest available list is Jan-Jun 2026; the build selects a newer dated workbook automatically if AMFI publishes one.
- Symbols absent from the AMFI NSE-symbol column, including unmatched or newly listed names, are marked UNCLASSIFIED.

## Frozen boundaries
- Prior-low selection, 3M/6M reference-low calculations, ordering and +10% ceiling are unchanged.
- Market-cap category does not alter eligibility, distance ranking, trading signals or execution.
- Fixed 40-factor strategy, production 15m inputs, Forecast, Working Range, Manual MIS, Alpha/Beta routing, BUY/SELL/GTT and protection logic are unchanged.

## Build / install
- Version 9.0.38 TEST, package suffix `.v9038test`, side-by-side installation.
- CI verifies AMFI source parsing, Kotlin classification tests, frozen-policy parity, Android unit tests, APK identity and signing.
