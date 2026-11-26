package com.multify.traderpro.engine

import java.util.Locale

/** Explicit headline evidence, not a guarantee of a stock move. */
object LiveNewsSentimentPolicy {
    data class Label(val sentiment: String, val evidence: String, val reportedPercentages: List<Double>)
    private val negative = listOf("fraud", "insolvency", "payment default", "defaulted", "rating downgrade",
        "downgraded", "order cancelled", "contract terminated", "guidance cut", "guidance lowered",
        "profit declined", "profit fell", "profit drops", "loss widened", "revenue declined", "revenue fell",
        "regulatory warning", "show cause notice", "penalty imposed", "net loss", "profit decreased")
    private val positive = listOf("credit rating upgraded", "rating upgraded", "rating upgrade",
        "regulatory approval received", "usfda approval", "patent granted", "order awarded", "contract awarded",
        "order win", "order secured", "orders secured", "receipt of order", "bags order", "bagged order",
        "letter of award", "guidance raised", "profit increased", "profit rose", "profit grew", "profit jumps",
        "profit up", "revenue increased", "revenue rose", "revenue grew", "revenue up", "debt reduced")
    fun classify(title: String, summary: String): Label {
        val text=(title+" "+summary).replace(Regex("<[^>]{1,200}>")," ").lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
        val good=positive.filter{it in text}.toMutableList()
        val bad=negative.filter{it in text}.toMutableList()
        if(Regex("""\b(?:net profit|pat|revenue|ebitda)\s+(?:has\s+)?(?:risen|increased|grew|rose|up|jumped)\b""").containsMatchIn(text)) good.add("explicit financial growth")
        if(Regex("""\b(?:net profit|pat|revenue|ebitda)\s+(?:has\s+)?(?:fallen|declined|decreased|fell|down|dropped)\b""").containsMatchIn(text)) bad.add("explicit financial decline")
        val uncertain=listOf("not received","not approved","not awarded","no fraud","no default","proposed","subject to approval","clarification","rumour","rumor","denied").any{it in text}
        val sentiment=when {
            uncertain || (good.isNotEmpty() && bad.isNotEmpty())->"MIXED"
            bad.isNotEmpty()->"NEGATIVE"
            good.isNotEmpty()->"POSITIVE"
            else->"NEUTRAL"
        }
        return Label(sentiment,(good+bad).distinct().joinToString("; ").take(240),
            Regex("""(?<![\d.])([+-]?\d+(?:\.\d+)?)\s*%""").findAll(text).mapNotNull{it.groupValues[1].toDoubleOrNull()}.filter{it.isFinite()}.take(12).toList())
    }
    fun cluster(now:Long, hours:Int, events:List<Pair<String,Long>>):Int =
        events.filter{now-it.second in 0L..hours*3_600_000L}.map{it.first}.distinct().size
    fun directionalReturn(sentiment:String, change:Double):Double?=when(sentiment) {
        "POSITIVE"->change
        "NEGATIVE"->-change
        else->null
    }
}
