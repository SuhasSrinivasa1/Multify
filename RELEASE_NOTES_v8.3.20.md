# Multify Trader Pro v8.3.20 — Rolling Research and Historical-Data Reliability (TEST)

Source base: v8.3.19-alpha-quote-account-pacing. Builds as Android versionCode 8401.

## Implemented
1. Non-trading rolling approximately-five-minute price movement calculated from existing full-NSE completed LTP sweeps (no new scans or API calls). Records top movers and metrics for original ranked candidates in `fixed_market_path.jsonl`; computes only when valid ~4–7-minute reference exists.
2. Read-only first observed market sweep timestamp is preserved in a separate research preference. Original production Fixed market-discovery state and 65%/35% ranking stay untouched.
3. Opening 09:15–09:30 research flags denote the missing completed same-session 15-minute opening candle. This does NOT relax the VWAP requirement or publish additional opening trades.
4. Keep validated incremental 15m history stitching. When an otherwise well-formed broker response has fewer than five historical candles, defer the SAME symbol's redundant full-history request until the next 15-minute candle completion (+5s grace). Valid histories with five or more bars are never deferred. No permanent symbol blacklisting. This changes network retry timing, not Fixed formula or quality criteria.
5. Improve Fixed audit labels: `FIXED_PRICE_BAND_OUTSIDE` is a normal deterministic skip; deferred insufficient candles are identified separately from genuine API failures. Original event remains in `original_event` for compatibility; existing decision journal records unchanged.
6. Export `fixed_sampled_maturity_and_repeat_research.json` from ALREADY RECORDED full-market LTP snapshots: +0.50%, +0.55%, +1.00%, full target rates at 15/30/60-minute horizons, mature versus immature outcomes and morning/midday time groups. Uses conservative post-scan spacing, counts only samples available, does not assume broker fills or intrabar extremes.
7. Export post-publication PRIMARY observations for further counterfactual study. These are NOT proof of suppressed executable second trades.
8. Preserve existing five-minute versus 15-minute SAME-ENTRY research pairs and actual later quote outcomes, with no additional API quota consumption.
9. Research-only opening session and available-factor evidence retained; no changes to production factor normalization.

## Strictly unchanged
- Frozen Fixed 107-reference learning snapshot, 40 factor scoring, production 15m candle evaluation, thresholds, VWAP requirement, same-stock requalification, 90s scan start target, market discovery ranking, and Alpha/Beta read routing.
- Full target +1.1161043030113955%; hard stop -5%; trailing +0.55% arm, +0.50% protected, +0.10% ratchet.
- BUY, SELL, GTT placement, modification, reconciliation, and risk permissions.

## Validation and deployment
- Source equality checks verify frozen Fixed selection and protective SELL paths against existing anchored commits.
- CI unit/backend tests, Android compilation, APK version/signature/sha256 checked before use.
- Broker API 429 avoidance and business outcomes require real-session validation; tests alone cannot guarantee improved hit rates.
- Source archive and comparison research are independent of any server/remote learning.
- Existing debug APK signing identity MAY differ from this CI debug APK. If Android refuses to update in-place, **DO NOT uninstall or clear data** while broker holdings or protective GTTs are active. Export logs and reconcile via Groww. Only same-key signing allows a data-preserving upgrade.
- Keep unattended Auto Buy disabled for initial validation and verify broker inventory and GTT state independently.
