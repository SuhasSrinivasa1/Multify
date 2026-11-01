package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.ForecastNewsPatternPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Market-wide NSE public announcement ingest. Independent from Alpha and Beta credentials. */
@Singleton
class ForecastNewsMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val journal: ForecastNewsJournal,
    private val audit: AuditLogger
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(8)).readTimeout(Duration.ofSeconds(12)).build()
    private val prefs = context.getSharedPreferences("forecast_news_symbol_catalog", Context.MODE_PRIVATE)
    private val gson = Gson()
    @Volatile private var companyByName: Map<String, String> = emptyMap()
    private var lastCatalogAttempt = 0L
    private var lastFetchAtMs = 0L

    suspend fun poll(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastFetchAtMs < 55_000L) return@withContext 0
        lastFetchAtMs = now
        if (companyByName.isEmpty()) refreshCatalog(now)
        var examined = 0
        var newlyStored = 0
        var failures = 0
        for (feed in RSS_FEEDS) {
            try {
                val xml = fetch(feed.second)
                val entries = parseRss(xml)
                examined += entries.size
                for (entry in entries) {
                    val published = parseDate(entry.published) ?: continue
                    if (published > now + 60_000L || published < now - 3L * 24L * 60 * 60_000L) continue
                    val symbol = resolveSymbol(entry.title, entry.summary)
                    val id = sha256(feed.first + "|" + (entry.guid.ifBlank { entry.url.ifBlank { entry.title + published } }))
                    val near = journal.recent(500).filter { it.symbol == symbol && symbol.isNotBlank() &&
                        now - it.firstSeenAtMs in 0L..(24L * 60 * 60_000L) }
                    val canonicalTitle = normalize(entry.title).take(180)
                    if (near.any { normalize(it.headline).take(180) == canonicalTitle }) {
                        audit.logAsync("FORECAST_NEWS", "DUPLICATE_CATALYST_SKIPPED", mapOf(
                            "symbol" to symbol, "feed" to feed.first,
                            "reason" to "SAME_NSE_TITLE_IN_MULTIPLE_FEEDS"))
                        continue
                    }
                    // Distinct-title counts, not repeated RSS entries, are used for clustering.
                    fun uniqueIn(minutes: Long) = near.filter {
                        now - it.firstSeenAtMs <= minutes * 60_000L
                    }.distinctBy { normalize(it.headline).take(180) }.size + 1
                    val assess = ForecastNewsPatternPolicy.assess(entry.title, entry.summary,
                        ((now - published) / 60_000L).coerceAtLeast(0),
                        uniqueIn(5), uniqueIn(10), uniqueIn(15), sourceVerified = true)
                    val event = ForecastNewsEvent(id, feed.first, symbol, entry.title.take(300),
                        entry.summary.take(1200), entry.url.take(500), assess.category, assess.direction,
                        published, now, gson.toJson(assess.features), assess.confidence,
                        status = if (symbol.isBlank()) "UNRESOLVED_SYMBOL" else if (assess.category == "OTHER" ||
                            assess.direction != "POSITIVE_POSSIBLE") "RESEARCH_ONLY" else "NEW",
                        rationale = assess.reason)
                    if (journal.addIfAbsent(event)) {
                        newlyStored++
                        audit.logAsync("FORECAST_NEWS", "EVENT_CAPTURED", mapOf(
                            "id" to id, "symbol" to symbol, "category" to assess.category,
                            "direction" to assess.direction, "published_at_ms" to published,
                            "observed_at_ms" to now, "confidence" to assess.confidence,
                            "feature_count" to assess.features.size,
                            "status" to event.status, "source_exchange" to "NSE",
                            "reported_amount_crore" to assess.features["reported_amount_crore"],
                            "catalyst_count_15m" to assess.features["cluster_15m"],
                            "distinct_recent_nse_titles_24h" to near.distinctBy {
                                normalize(it.headline).take(180) }.size + 1,
                            "off_market_collection" to (java.time.Instant.ofEpochMilli(now)
                                .atZone(java.time.ZoneId.of("Asia/Kolkata")).hour !in 9..15)))
                    }
                }
            } catch (err: Exception) {
                failures++
                audit.logAsync("FORECAST_NEWS", "FEED_ERROR", mapOf(
                    "feed" to feed.first, "error_type" to err.javaClass.simpleName,
                    "message" to (err.message ?: "").take(140)))
            }
        }
        audit.logAsync("FORECAST_NEWS", "POLL_PROFILE", mapOf("items_examined" to examined,
            "new_events" to newlyStored, "source_failures" to failures,
            "catalog_size" to companyByName.size, "poll_started_at_ms" to now))
        newlyStored
    }

    private fun refreshCatalog(now: Long) {
        val cached = prefs.getString("catalog", null)
        if (cached != null && companyByName.isEmpty()) {
            companyByName = cached.lineSequence().mapNotNull { line ->
                val pos = line.indexOf('|'); if (pos < 1) null else line.substring(0,pos) to line.substring(pos + 1)
            }.toMap()
        }
        if (now - lastCatalogAttempt < 60L * 60 * 1000) return
        lastCatalogAttempt = now
        runCatching {
            val csv = fetch(NseSymbolRepository.EQUITY_URL)
            val rows = csv.lineSequence().drop(1).mapNotNull { line ->
                val cells = parseCsv(line)
                val symbol = cells.getOrNull(0)?.trim()?.uppercase(Locale.ROOT) ?: return@mapNotNull null
                val company = cells.getOrNull(1)?.trim() ?: return@mapNotNull null
                if (!symbol.matches(Regex("[A-Z0-9&._-]{1,32}"))) return@mapNotNull null
                normalize(company) to symbol
            }.toMap()
            require(rows.size > 500)
            companyByName = rows
            prefs.edit().putString("catalog", rows.entries.joinToString("\n") { it.key + "|" + it.value }).apply()
            audit.logAsync("FORECAST_NEWS", "SYMBOL_CATALOG_READY", mapOf("count" to rows.size))
        }.onFailure {
            audit.logAsync("FORECAST_NEWS", "CATALOG_ERROR", mapOf("type" to it.javaClass.simpleName))
        }
    }

    private fun resolveSymbol(title: String, summary: String): String {
        val catalog = companyByName
        val normalizedTitle = normalize(title.substringBefore(" - ").substringBefore(" has informed"))
        catalog[normalizedTitle]?.let { return it }
        // Exact bounded company-name prefix matching. Avoid fuzzy guessing that might cause a BUY on the wrong NSE symbol.
        return catalog.entries.firstOrNull { (name, _) ->
            name.length >= 8 && (normalizedTitle == name || normalizedTitle.startsWith("$name "))
        }?.value ?: ""
    }

    private fun parseDate(s: String): Long? = runCatching {
        ZonedDateTime.parse(s.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
    }.getOrElse {
        runCatching { Instant.parse(s.trim()).toEpochMilli() }.getOrNull()
    }

    internal data class RssItem(val title: String, val summary: String, val published: String, val url: String, val guid: String)
    internal fun parseRss(xml: String): List<RssItem> {
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(StringReader(xml)) }
        val results = ArrayList<RssItem>()
        var inside = false; var title = ""; var desc = ""; var date = ""; var link = ""; var guid = ""
        var tag = ""
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name.lowercase(Locale.ROOT)
                    if (tag == "item") { inside = true; title = ""; desc = ""; date = ""; link = ""; guid = "" }
                }
                XmlPullParser.TEXT -> if (inside) when (tag) {
                    "title" -> title += parser.text
                    "description" -> desc += parser.text
                    "pubdate", "published" -> date += parser.text
                    "link" -> link += parser.text
                    "guid" -> guid += parser.text
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("item", true) && inside) {
                        if (title.isNotBlank()) results += RssItem(title.trim(), desc.trim(), date.trim(), link.trim(), guid.trim())
                        inside = false
                    }
                    tag = ""
                }
            }
            parser.next()
        }
        return results
    }

    private fun fetch(url: String): String = http.newCall(Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0 MultifyForecastNews/1.0")
        .header("Accept", "application/rss+xml,application/xml,text/xml,text/csv,*/*")
        .build()).execute().use { r ->
        if (!r.isSuccessful) error("Source returned HTTP ${r.code}")
        val body = r.body?.string().orEmpty()
        require(body.isNotBlank() && body.length < 5_000_000) { "Empty or overlarge feed" }
        body
    }

    private fun parseCsv(text: String): List<String> {
        val result = ArrayList<String>(); var quote = false; val sb = StringBuilder(); var i = 0
        while (i < text.length) {
            when (text[i]) {
                '"' -> if (quote && i + 1 < text.length && text[i + 1] == '"') { sb.append('"'); i++ } else quote = !quote
                ',' -> if (quote) sb.append(',') else { result += sb.toString(); sb.setLength(0) }
                else -> sb.append(text[i])
            }
            i++
        }
        result += sb.toString()
        return result
    }

    private fun normalize(text: String): String = text.uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        private val RSS_FEEDS = listOf(
            "NSE_ANNOUNCEMENTS" to "https://nsearchives.nseindia.com/content/RSS/Online_announcements.xml",
            "NSE_FINANCIAL_RESULTS" to "https://nsearchives.nseindia.com/content/RSS/Financial_Results.xml"
        )
    }
}
