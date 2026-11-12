# Multify Trader Pro v8.3.23 — Fixed-Preserving Pipeline Reliability (TEST)

**Source baseline:** v8.3.22 `v8322-pattern-research-timing-diagnostics` pinned at `2cf595d8d5fbfbda3eda2bd90750da9799f8fc37`. Android versionCode **8404**.

## Corrective changes (NO strategy changes)
1. **No-request prefetch handoff:** The next official Fixed scan atomically cancels a *scheduled, never-started* prefetch before it starts even one broker LTP request, and then runs its **original single full-NSE sweep**. This avoids a lost scan when preparatory work was scheduled but had not begun. If prefetch broker I/O has started, the existing await/timeout/no-duplicate fallback rule remains: no second full-market request burst.
2. **Protect official LTP capacity:** The optional single 5-minute historical *research* sample yields whenever an official full-market prefetch is scheduled or active (check before any research broker call). This removes competition for broker admission during the next official sweep. May reduce the number of optional 5-minute matched research pairs; does not change the production 15-minute model, discovery cadence, target or quality gates.
3. **Order-first Fixed notification scheduling:** Once the full Fixed scan and applicable auto-order/protection path have ended, enqueue the alert before the future-sweep I/O task. Never notify before order protection or before the scan; this is a scheduling optimization, not a claim of measured phone rendering speedup.
4. **Transport-only performance diagnostics:** Per-window p50/p95 batch latency, full-sweep duration and whether the original 27/26 Alpha/Beta split is functioning; also measure prefetch completion relative to the original 90-second due date. No extra broker call and no score changes.
5. **Retain working historical OHLCV incremental prefix reuse** (validated broker delta and >=2 overlapping candle timestamps), existing category governor and 429 cooldowns, historical retry guard, quote caching safety, API account ownership and return-path order protection.

## Strict constraints
- **The complete Fixed `generateFixedForecasts()` method is byte-identical to v8.3.22**: 40 factors, candidate universe (including .22's 5-slot PRIMARY carry-forward and price-band-aware dynamic discovery), selection ranking, PRIMARY confirmation, market breadth, 15-minute VWAP and stable prices all unchanged.
- Separate baseline v8.3.19 has a *different* candidate universe; this release freezes v8.3.22, not v8.3.19.
- Frozen 107 reference calls; +1.1161043030113955% full target; trailing arm +0.55%, initially protected +0.50%, +0.10% ratchet, -5% hard stop. No BUY, SELL, GTT, Auto Buy, protection or account-route change.
- Exactly one official 90-second-target NSE scan per cycle and the same full 53 LTP batches (27 Alpha / 26 Beta when both accounts are healthy); no extra scans or increased 48-detailed-candidate limit.
- Optional 5m research may be skipped under market prefetch pressure. No new RSI, volume, momentum or price filter.

## Validation required before live use
- CI frozen-source strategy parity to both pinned baseline and current v8.3.22, Android/backend tests, verified APK package/version/signature.
- A successful CI build does NOT guarantee higher target-hit rates, fewer broker 429s, or reduced live p95; validate with same-account, same-day broker log evidence. The previous 10,000-cycle load test gave hypothetical capacity sensitivities rather than measured v8.3.23 gains.
- Keep Auto Buy OFF for initial evaluation and verify broker inventory plus GTT positions independently.
- **Installation warning:** debug signing may differ from your installed .22, and in-place Android upgrade requires the same certificate. Do NOT uninstall/clear the app data just to force upgrade while active positions or GTTs may exist.

## Requested performance outcomes
Track `PREFETCH_NOT_STARTED_USE_OFFICIAL_SWEEP`, `PREFETCHED_SWEEP_CONSUMED`, `PREFETCH_INVALID_SCAN_DEFERRED`, `OFFICIAL_PREFETCH_HAS_PRIORITY`, `FULL_NSE_SWEEP_ACCOUNT_PROFILE`, and `FIXED_SCAN_PROFILE`. Compare completed scan count, cold/warm p50/p95, notification latency, 429s, missing market snapshots and target-hit rates against .22; this is a reliability release not a demonstrated selection improvement.
