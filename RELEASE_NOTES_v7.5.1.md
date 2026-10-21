# Multify Trader Pro v7.5.1 — Seeded Immutable Fixed Engine

v7.5.1 is intended for a fresh install and includes the decision intelligence recovered from the diagnostic exports already supplied during development.

## Bundled prior intelligence

On a truly empty fresh-install database, before the Fixed snapshot is sealed, the app automatically imports a bundled decision-intelligence seed assembled from the uploaded v7.1, v7.2 and v7.3.2 diagnostic exports.

The bundled decision state contains:
- 108 unique historical Multify BUY learning rows, including the 103 historical seed rows plus APPENDED, RECOVERY and LIVE rows available in the uploaded logs
- 10 prior published Forecast recommendations/outcomes from October 19 and October 21
- 2 Forecast champion records present in the latest uploaded diagnostic state
- prior research-report summaries

The uploaded research-snapshot files contained thousands of raw scan observations, but none had completed outcome fields in those exports. Under the existing Forecast code those incomplete rows were diagnostic capture only and were not eligible as historical positive-close research evidence for today's ranking. They are therefore not promoted into the bundled learned seed.

The normal frozen v7.4.1 code/reference state remains included separately:
- 40 observable factors / 3 factor families
- strategy engine
- quality gates
- publication confirmation rules
- market-discovery ranking
- positive-close learning policy
- Multify observable reference cases
- target-calibration math

## Fresh-install sequence

1. Detect an empty learning database.
2. Import the bundled prior decision intelligence.
3. Seal the immutable Fixed snapshot from that state.
4. Continue running Forecast against the same seeded historical state.
5. Fixed never accepts later Forecast/Multify learning; Forecast continues adapting.

If the app already contains learning data, the bundled seed is not automatically merged over it.

All v7.5.0 Fixed/Forecast independence, separate auto-buy toggles, ARM master safety switch, GTT UP entries and long-only holding protections remain unchanged.

Version: `7.5.1-fixed-engine-seeded` (`versionCode 751`).
