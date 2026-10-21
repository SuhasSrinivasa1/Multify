# Multify Trader Pro v7.5.3

## Fixed recommendation window

- Fixed recommendation generation runs from 09:15 IST until 15:30 IST on trading days.
- The last normal five-minute Fixed scan may therefore occur before the 15:30 market close.
- Fixed recommendations are no longer marked MISSED at 15:00; Fixed outcome expiry moves to 15:30 IST.
- Adaptive Forecast keeps its existing 15:00 recommendation/outcome window in this release.

## Existing Fixed guardrails retained

- Fixed recommends/executes only recommendation entries strictly above ₹100.
- Fixed may size from the remaining CNC wallet balance when it is below the configured per-call budget.
- Multify and Forecast still require the full configured per-call budget.
- Fixed remains isolated from future adaptive Forecast learning.
- Common stepped trailing sell logic remains unchanged.
