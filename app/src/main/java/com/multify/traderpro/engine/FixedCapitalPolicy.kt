package com.multify.traderpro.engine

import kotlin.math.min

/**
 * Hard invariant for Auto/Shadow/Fast-Track campaign transitions:
 * once a position is open, later decisions may reuse or reduce its notional,
 * but must never increase the campaign's capital exposure.
 */
object FixedCapitalPolicy {
    fun cap(campaignBudget: Double, capitalDeployed: Double, fallbackNotional: Double): Double {
        val deployed = when {
            capitalDeployed.isFinite() && capitalDeployed > 0.0 -> capitalDeployed
            fallbackNotional.isFinite() && fallbackNotional > 0.0 -> fallbackNotional
            else -> 0.0
        }
        if (deployed <= 0.0) return 0.0
        val configured = campaignBudget.takeIf { it.isFinite() && it > 0.0 } ?: deployed
        return min(configured, deployed).coerceAtLeast(0.0)
    }
}
