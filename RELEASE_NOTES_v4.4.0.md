# Multify Trader Pro v4.4.0 — Priority Fast Track + Live Wave Table

## First-wave priority
- New paid Multify stock recommendations enter the critical processing lane immediately.
- First-wave lifecycle order is prioritized: long entry -> long exit -> short entry -> short exit.
- Fast Track notification-follow entry is attempted before slower independent strategy qualification.
- Active first-wave positions are monitored before wave-2+ secondary positions.

## New recommendation focus policy
When a different Multify stock arrives:
- an older first-wave position that is net green after estimated costs is exited so capital/focus can move to the new call;
- an older first-wave position that is net red is not forcibly sold. It becomes RECOVERY HOLD: broker protection and safety monitoring remain active, but no new averaging, flips or strategy churn are allowed for that stock;
- one older wave-2+ stock may continue alongside the new priority stock; additional older wave-2+ stocks become prediction-only until focus is available.

## Live wave table
- Dashboard now exposes a live next-wave decision for up to two active intraday symbols.
- Even with Active waves = 1, Wave 2 is calculated continuously as PREVIEW ONLY.
- LONG or SHORT is shown green only when the live expected-value selector prefers that side; HOLD remains valid when neither side has adequate edge.
- Wave 1 continues to show the rolling long average and learned short retracement average.

## Existing rules retained
- Maximum 10 proper waves.
- 2% anchor spacing from Wave 2 onward.
- ₹5,000 incremental wave add/probe.
- Mandatory trailing-stop ratchet for first-wave long and short positions in AUTO, LONG and SHORT modes.
- AUTO Wave 1 remains long then eligible short; LONG executes only long; SHORT executes only short.
