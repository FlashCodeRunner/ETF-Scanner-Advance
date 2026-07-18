package com.swapnil.etfscanner

import android.content.Context

/**
 * ===========================================================================
 *  DEFAULTS + FIXED CONFIG
 * ===========================================================================
 *  The four filter numbers are editable at runtime (see Thresholds /
 *  SettingsStore). The values below are just the starting defaults.
 */
object StrategyConfig {
    // Data now comes from Yahoo Finance (see MarketRepository), covering the
    // same ETF universe as the old sheet (see EtfUniverse).

    // Defaults (used until you change them in the app)
    const val DEF_MAX_ONE_DAY = -1.0        // 1-day change <= -1%
    const val DEF_THIRTY_SPLIT = -2.5       // 30-day dividing line
    const val DEF_MIN_VOLUME = 500_000.0    // > 5 Lakh
    const val DEF_MIN_VALUE = 20_000_000.0  // > 2 Crore

    // Daily alert, pinned to India time (correct even from Dubai)
    const val ALERT_HOUR_IST = 15
    const val ALERT_MINUTE_IST = 15
    const val IST_ZONE = "Asia/Kolkata"
}

/** The four editable filter values. */
data class Thresholds(
    val maxOneDay: Double,      // buy only if 1-day <= this
    val thirtyDaySplit: Double, // Set A: 30-day > this ; Set B: 30-day < this
    val minVolume: Double,      // volume > this
    val minValue: Double        // traded value > this
) {
    companion object {
        val DEFAULT = Thresholds(
            StrategyConfig.DEF_MAX_ONE_DAY,
            StrategyConfig.DEF_THIRTY_SPLIT,
            StrategyConfig.DEF_MIN_VOLUME,
            StrategyConfig.DEF_MIN_VALUE
        )
    }
}

/** Persists the editable thresholds so they survive app restarts. */
object SettingsStore {
    private const val PREF = "etf_thresholds"

    fun load(ctx: Context): Thresholds {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        fun g(k: String, d: Double) = p.getString(k, null)?.toDoubleOrNull() ?: d
        return Thresholds(
            g("maxOneDay", StrategyConfig.DEF_MAX_ONE_DAY),
            g("thirtySplit", StrategyConfig.DEF_THIRTY_SPLIT),
            g("minVolume", StrategyConfig.DEF_MIN_VOLUME),
            g("minValue", StrategyConfig.DEF_MIN_VALUE)
        )
    }

    fun save(ctx: Context, t: Thresholds) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("maxOneDay", t.maxOneDay.toString())
            .putString("thirtySplit", t.thirtyDaySplit.toString())
            .putString("minVolume", t.minVolume.toString())
            .putString("minValue", t.minValue.toString())
            .apply()
    }
}

data class Etf(
    val name: String,
    val price: Double,
    val oneDayChange: Double,
    val thirtyDayChange: Double,
    val volume: Double,
    val category: String,
    val tradedValue: Double
) {
    /** Shared base: a genuine dip with enough liquidity. */
    fun isLiquidDip(t: Thresholds) =
        oneDayChange <= t.maxOneDay &&
        volume > t.minVolume &&
        tradedValue > t.minValue

    /** SET A — dip in a stable ETF: 30-day > split (the sheet's own rule). */
    fun qualifiesStable(t: Thresholds) =
        isLiquidDip(t) && thirtyDayChange > t.thirtyDaySplit

    /** SET B — dip in a falling ETF: 30-day < split (your original filter). */
    fun qualifiesFalling(t: Thresholds) =
        isLiquidDip(t) && thirtyDayChange < t.thirtyDaySplit

    fun qualifies(t: Thresholds) = qualifiesStable(t) || qualifiesFalling(t)

    /** Why an ETF down >=1% today still lands in neither set. */
    fun failReasons(t: Thresholds): List<String> {
        val r = mutableListOf<String>()
        if (oneDayChange > t.maxOneDay)
            r.add("1-day ${fmtPct(oneDayChange)} not <= ${fmtPct(t.maxOneDay)}")
        if (volume <= t.minVolume)
            r.add("volume ${volume.toLong()} not > ${t.minVolume.toLong()}")
        if (tradedValue <= t.minValue)
            r.add("value ${crore(tradedValue)} not > ${crore(t.minValue)}")
        return r
    }
}

fun fmtPct(v: Double): String = String.format("%+.2f%%", v)

/** Formats a rupee amount as crores, e.g. 466278059 -> "46.6 Cr". */
fun crore(v: Double): String = String.format("%.1f Cr", v / 1_00_00_000.0)
