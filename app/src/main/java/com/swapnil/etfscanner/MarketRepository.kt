package com.swapnil.etfscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

sealed class ScanResult {
    data class Success(val all: List<Etf>) : ScanResult() {
        fun stable(t: Thresholds): List<Etf> = all.filter { it.qualifiesStable(t) }
        fun falling(t: Thresholds): List<Etf> = all.filter { it.qualifiesFalling(t) }
    }
    data class Error(val message: String) : ScanResult()
}

/**
 * Pulls live-ish quotes from Yahoo Finance (one chart call per ETF), then
 * derives the same columns the sheet had:
 *   price, 1-day %, 30-day %, volume, traded value (= price x volume).
 * Yahoo India data is delayed ~15 min, same ballpark as GoogleFinance.
 */
object MarketRepository {

    // Limit how many requests hit Yahoo at once so we don't get rate-limited.
    private val gate = Semaphore(8)

    suspend fun fetchAndScan(): ScanResult = try {
        val etfs = coroutineScope {
            EtfUniverse.list.map { ref ->
                async(Dispatchers.IO) { gate.withPermit { fetchOne(ref) } }
            }.awaitAll()
        }.filterNotNull()

        if (etfs.isEmpty())
            ScanResult.Error("Couldn't load any quotes from Yahoo. Check your connection and try again.")
        else
            ScanResult.Success(etfs)
    } catch (e: Exception) {
        ScanResult.Error(e.message ?: "Unknown network error")
    }

    private fun fetchOne(ref: EtfRef): Etf? {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/" +
                    "${ref.symbol}.NS?range=1mo&interval=1d"
            val json = httpGet(url) ?: return null
            parse(ref, json)
        } catch (e: Exception) {
            null // skip this ETF; a few misses won't break the scan
        }
    }

    private fun parse(ref: EtfRef, body: String): Etf? {
        val root = JSONObject(body)
        val result = root.optJSONObject("chart")
            ?.optJSONArray("result")?.optJSONObject(0) ?: return null
        val meta = result.optJSONObject("meta") ?: return null

        val quote = result.optJSONObject("indicators")
            ?.optJSONArray("quote")?.optJSONObject(0)
        val closes = quote?.optJSONArray("close")
        val volumes = quote?.optJSONArray("volume")

        fun lastNonNull(arr: org.json.JSONArray?): Double? {
            if (arr == null) return null
            for (i in arr.length() - 1 downTo 0) {
                if (!arr.isNull(i)) return arr.optDouble(i)
            }
            return null
        }
        fun firstNonNull(arr: org.json.JSONArray?): Double? {
            if (arr == null) return null
            for (i in 0 until arr.length()) {
                if (!arr.isNull(i)) return arr.optDouble(i)
            }
            return null
        }
        fun secondLastNonNull(arr: org.json.JSONArray?): Double? {
            if (arr == null) return null
            var seen = 0
            for (i in arr.length() - 1 downTo 0) {
                if (!arr.isNull(i)) {
                    seen++
                    if (seen == 2) return arr.optDouble(i)
                }
            }
            return null
        }

        val price = meta.optDouble("regularMarketPrice", Double.NaN)
            .let { if (it.isNaN()) lastNonNull(closes) else it } ?: return null
        val volume = meta.optDouble("regularMarketVolume", Double.NaN)
            .let { if (it.isNaN()) lastNonNull(volumes) else it } ?: 0.0

        val yesterday = secondLastNonNull(closes) ?: return null
        val monthAgo = firstNonNull(closes) ?: return null

        val oneDay = if (yesterday != 0.0) (price / yesterday - 1.0) * 100.0 else 0.0
        val thirty = if (monthAgo != 0.0) (price / monthAgo - 1.0) * 100.0 else 0.0
        val tradedValue = price * volume

        return Etf(
            name = "NSE:${ref.symbol}",
            price = round2(price),
            oneDayChange = round2(oneDay),
            thirtyDayChange = round2(thirty),
            volume = volume,
            category = ref.category,
            tradedValue = tradedValue
        )
    }

    private fun round2(v: Double) = Math.round(v * 100.0) / 100.0

    private fun httpGet(urlStr: String): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
            )
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
