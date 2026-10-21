# Multify Trader Pro v7.5.0 — Immutable Fixed Engine

v7.5.0 adds a second recommendation engine named **Fixed** while leaving the adaptive Forecast engine intact.

## Immutable Fixed engine

On first launch after upgrading from v7.4.1, the app seals an immutable snapshot of the recommendation state already on the device:

- Multify learning-call reference rows
- historical Forecast recommendations and outcomes
- forecast research snapshots used as evidence
- champion state used by ranking
- the frozen v7.4.1 Multify reference cases

The snapshot has a stable snapshot ID and SHA-256 fingerprint and is written once. Future Forecast/Multify learning cannot rewrite it.

The Fixed implementation has frozen copies of the v7.4.1:
- local strategy/feature engine
- 40-factor / 3-family model
- Forecast quality policy
- publication/confirmation policy
- market-discovery ranking policy
- positive-close filtering policy
- adaptive target math used by that snapshot
- Multify observable reference cases

Fixed still consumes current market prices, quotes, candles and volume. Those are live inputs; the decision intelligence and reference state are immutable.

## Independent Fixed recommendations

A new bottom navigation tab named **Fixed** mirrors the recommendation workflow:
- stock
- entry
- target
- quality tier
- confidence/score/reason
- manual BUY
- independent discovery health
- visible immutable snapshot ID/fingerprint

The installation-day recommendations inside the sealed snapshot are copied into Fixed once. Future days are generated only by the frozen Fixed engine. Fixed never copies future adaptive Forecast recommendations.

There is no daily numeric recommendation cap.

Fixed outcomes are tracked for diagnostics only. They never update Fixed ranking, target learning, reference cases or champions.

## Forecast remains adaptive

The existing Forecast engine remains separate and continues its current:
- market-wide discovery
- Multify reverse-engineering/research
- positive-close production learning
- champion/evidence updates
- target calibration
- Forecast auto-buy

Future Forecast learning does not change the Fixed snapshot.

## Independent execution

Settings now has two independent toggles:
- **Auto-buy Forecast recommendations**
- **Auto-buy Fixed recommendations**

ARM remains the master real-money switch for both.

Fixed auto-buy uses the same entry architecture as Forecast:
- recommendation entry = fixed Groww GTT trigger
- trigger direction = UP
- post-trigger child = CNC MARKET BUY
- recommendation target remains fixed

A price falling through the entry does not satisfy the UP trigger.

Forecast and Fixed may both recommend the same symbol. Duplicate suppression is engine-scoped, so each engine may own its own allocation for that symbol while still blocking repeated execution of the same engine's recommendation.

## Existing holding safeguards remain

- long-only NSE CASH/CNC
- no downward averaging
- no downside-loss sell
- +0.55% arm / +0.50% protect / +0.10% ratchet
- end-of-day ₹100 estimated-net recovery GTT
- ARM/safety-halt cancellation of untriggered entry GTTs
- no shorting

Version: `7.5.0-fixed-engine` (`versionCode 750`).
