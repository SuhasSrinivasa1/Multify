from pathlib import Path

root = Path(".")
pkg = root / "app/src/main/java/com/multify/traderpro"
policy = pkg / "engine/HardStopLossPolicy.kt"
manifest = pkg / "engine/ReleaseIntegrityManifest.kt"
repo = pkg / "data/repository/TradingRepository.kt"
ui = pkg / "ui/screens/TraderApp.kt"
version = root / "app/build.gradle.kts"
test = root / "app/src/test/java/com/multify/traderpro/engine/HardStopLossPolicyTest.kt"

def update(path, before, after, expected):
    s = path.read_text()
    count = s.count(before)
    if count != expected:
        raise AssertionError(f"{path}: {before!r} expected {expected} occurrences, found {count}")
    path.write_text(s.replace(before, after))

update(policy, "const val MAX_LOSS_PCT = 5.0", "const val MAX_LOSS_PCT = 2.0", 1)
update(manifest, "const val HARD_STOP_LOSS_PCT = 5.0", "const val HARD_STOP_LOSS_PCT = 2.0", 1)
update(repo, "hard -5%", "hard -2%", 8)
update(repo, "common -5%", "common -2%", 2)
update(repo, "COMMON_HARD_STOP_MINUS_5", "COMMON_HARD_STOP_MINUS_2", 5)
update(repo, "HARD_STOP_MINUS_5_ARMED", "HARD_STOP_MINUS_2_ARMED", 1)
update(ui, "hard -5.00%", "hard -2.00%", 1)
update(ui, "hard -5%", "hard -2%", 1)
update(version, "versionCode = 7910", "versionCode = 7911", 1)
update(version, 'versionName = "7.9.10-auto-buy-readiness-guards"', 'versionName = "7.9.11-common-minus-two-stop"', 1)

test.write_text("""package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardStopLossPolicyTest {
    @Test fun commonStopIsTwoPercentBelowActualFill() {
        assertEquals(2.0, HardStopLossPolicy.MAX_LOSS_PCT, 0.0)
        assertEquals(98.0, HardStopLossPolicy.stopPrice(100.0), 0.0001)
        assertEquals(245.0, HardStopLossPolicy.stopPrice(250.0), 0.0001)
        assertEquals(121.7748, HardStopLossPolicy.stopPrice(124.26), 0.0001)
        assertEquals(2.0, ReleaseIntegrityManifest.HARD_STOP_LOSS_PCT, 0.0)
        assertTrue(ReleaseIntegrityManifest.verifyRuntimeConstants())
    }

    @Test fun breachOccursAtOrBelowTwoPercentLoss() {
        assertFalse(HardStopLossPolicy.breached(100.0, 98.01))
        assertTrue(HardStopLossPolicy.breached(100.0, 98.0))
        assertTrue(HardStopLossPolicy.breached(100.0, 97.99))
        assertFalse(HardStopLossPolicy.breached(100.0, Double.NaN))
    }

    @Test fun profitableTrailingIsUnchangedAndAlwaysTighterOnceArmed() {
        assertEquals(0.55, SteppedTrailingPolicy.ARM_TRIGGER_PCT, 0.0)
        assertEquals(0.50, SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT, 0.0)
        assertEquals(0.10, SteppedTrailingPolicy.STEP_PCT, 0.0)
        assertEquals(null, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.54))
        assertEquals(0.50, SteppedTrailingPolicy.stopGainPctForHighWater(100.0, 100.55)!!, 0.0001)
        assertTrue(SteppedTrailingPolicy.stopPrice(100.0, 0.50) > HardStopLossPolicy.stopPrice(100.0))
    }
}
""")
assert "MAX_LOSS_PCT = 2.0" in policy.read_text()
assert "HARD_STOP_LOSS_PCT = 2.0" in manifest.read_text()
assert "COMMON_HARD_STOP_MINUS_5" not in repo.read_text()
assert "hard -5%" not in repo.read_text() and "hard -5%" not in ui.read_text()
print("v7.9.11 -2% stop: all LONG sources, GTT, local fallback, UI and tests updated")
