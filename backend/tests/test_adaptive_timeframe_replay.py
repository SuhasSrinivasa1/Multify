import unittest
from datetime import datetime
from zoneinfo import ZoneInfo
from backend.app.adaptive_timeframe_replay import (
    BAR_MS, Bar, Signal, confirmation_index, replay, simulate, validate_bars,
)


def at(hour, minute):
    return int(datetime(2026, 11, 12, hour, minute, tzinfo=ZoneInfo('Asia/Kolkata')).timestamp() * 1000)


def bars(prices=None, opens=None, volumes=None):
    prices = prices or [100, 100.2, 100.4, 101, 102, 101.5, 101.3, 101.2, 101, 100.8, 100.7, 100.6, 100.5, 100.4, 100.3, 100.2]
    opens = opens or prices
    volumes = volumes or list(range(100, 100 + len(prices) * 10, 10))
    return [Bar('ABC', at(9, 15) + i * BAR_MS, op, max(op, p) + .02,
                min(op, p) - .02, p, volumes[i]) for i, (p, op) in enumerate(zip(prices, opens))]


class ReplayTests(unittest.TestCase):
    def test_post_signal_five_minute_confirmation_cannot_enter_same_bar(self):
        b = bars()
        idx = confirmation_index(b, at(9, 20))
        self.assertIsNotNone(idx)
        confirmation = b[idx - 1]
        self.assertGreaterEqual(confirmation.at_ms + BAR_MS, at(9, 20))
        self.assertEqual(b[idx].at_ms, confirmation.at_ms + BAR_MS)

    def test_boundary_and_zero_when_no_confirmation(self):
        b = bars(prices=[100] * 16)
        a = replay(b, [Signal('before', 'ABC', at(9, 40)),
                       Signal('after', 'ABC', at(9, 45))])
        self.assertEqual(a['events'][0]['time_phase'], 'BEFORE_0945_IST')
        self.assertEqual(a['events'][1]['time_phase'], 'AFTER_0945_IST')
        self.assertEqual(a['events'][1]['comparison']['SCHEDULED_0945']['state'], 'NO_ENTRY')
        self.assertNotEqual(a['events'][0]['comparison']['SCHEDULED_0945']['state'], 'NO_ENTRY')

    def test_ambiguous_bar_assumes_stop_not_target(self):
        b = [Bar('ABC', at(9, 30), 100, 106, 94, 101, 100)]
        hit = simulate(b, 0, 1.1, 5, 0, 0, 5)
        self.assertEqual(hit['state'], 'AMBIGUOUS_ASSUME_STOP')
        self.assertLess(hit['net_pct'], 0)

    def test_missing_candle_is_not_imputed(self):
        b = bars()
        missing = b[:2] + b[3:]
        self.assertEqual(simulate(missing, 0, 15, 5, 5, 20, 60)['state'], 'MISSING_BARS')

    def test_fee_and_slippage_lower_net(self):
        b = bars(prices=[100] * 16)
        zero = simulate(b, 0, 10, 5, 0, 0, 60)
        costly = simulate(b, 0, 10, 5, 5, 20, 60)
        self.assertLess(costly['net_pct'], zero['net_pct'])

    def test_reject_duplicate_and_malformed_bars(self):
        b = bars()
        with self.assertRaises(ValueError):
            validate_bars(b + [b[0]])
        with self.assertRaises(ValueError):
            replay(b, [Signal('x', 'ABC', at(9, 30)), Signal('x', 'ABC', at(9, 40))])

    def test_no_historic_bars_does_not_claim_pnl(self):
        out = replay([], [Signal('x', 'ABC', at(9, 30))])
        self.assertEqual(out['events'][0]['comparison']['FIXED_REFERENCE_DELAYED_OPEN']['state'], 'NO_ENTRY')
        self.assertTrue(out['research_only'])
        self.assertFalse(out['realized_pnl'])


if __name__ == '__main__':
    unittest.main()
