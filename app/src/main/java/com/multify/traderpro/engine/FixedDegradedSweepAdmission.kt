package com.multify.traderpro.engine

/** Advisory conservative throughput guard only; existing category gates remain authoritative. */
object FixedDegradedSweepAdmission {
    fun betaOnlyFeasible(batchCount:Int,limitPerMinute:Int,spacingMs:Long,windowMs:Long=90_000L):Boolean {
        if(batchCount<=0||limitPerMinute<=0||spacingMs<=0||windowMs<=0)return false
        val quotaCapacity=limitPerMinute.toLong()*windowMs/60_000L
        val paceCapacity=1+windowMs/spacingMs
        return batchCount<=minOf(quotaCapacity,paceCapacity)
    }
}
