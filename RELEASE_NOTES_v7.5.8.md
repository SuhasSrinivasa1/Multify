# Multify Trader Pro v7.5.8

## High-end Android performance optimization

- Fixed detailed symbol evaluation now uses adaptive bounded concurrency.
- Devices reporting 8 or more available processors use 6 concurrent Fixed evaluations.
- Smaller devices continue using 4 concurrent evaluations.
- Evaluation results are restored to the original Fixed universe order before breadth, factor-family scoring, ranking, confirmation, and publication.
- The optimization is performance-only and does not change the frozen v7.4.1 selection formulas or thresholds.

## Frozen Fixed contract retained

- 40-factor model unchanged.
- Strategy votes and factor-family weights unchanged.
- Quality gates and PRIMARY publication rules unchanged.
- >₹100 Fixed publication/execution overlay unchanged.
- Frozen target calibration and ≥1.00% target floor unchanged.
- 09:15–15:30 IST Fixed window unchanged.
- Fixed learning remains disabled.
- Existing stepped trailing exit logic unchanged.
