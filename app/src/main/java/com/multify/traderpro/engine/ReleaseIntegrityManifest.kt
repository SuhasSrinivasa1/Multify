package com.multify.traderpro.engine

/** Human/runtime-visible release invariants. CI still performs the authoritative source hash lock. */
object ReleaseIntegrityManifest {
    const val RELEASE_FAMILY = "v7.8.2-auth-recovery"
    const val FIXED_CONTRACT_ID = "FIXED-V741-20261021-190239-FINAL"
    const val FIXED_SOURCE_COMMIT = "ca0eecaf63d2cc3500e11fd73f8de9bf2d87e39f"
    const val TRAIL_ARM_PCT = 0.55
    const val TRAIL_INITIAL_PROTECTED_GAIN_PCT = 0.50
    const val TRAIL_STEP_PCT = 0.10
    const val HARD_STOP_LOSS_PCT = 5.0

    fun verifyRuntimeConstants(): Boolean =
        FixedFinalContract.CONTRACT_ID == FIXED_CONTRACT_ID &&
            FixedFinalContract.SOURCE_COMMIT == FIXED_SOURCE_COMMIT &&
            SteppedTrailingPolicy.ARM_TRIGGER_PCT == TRAIL_ARM_PCT &&
            SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT == TRAIL_INITIAL_PROTECTED_GAIN_PCT &&
            SteppedTrailingPolicy.STEP_PCT == TRAIL_STEP_PCT &&
            HardStopLossPolicy.MAX_LOSS_PCT == HARD_STOP_LOSS_PCT
}
