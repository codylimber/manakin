package com.codylimber.fieldphenology.data.model

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * App-wide phenology preferences that the data layer reads. Mirrored from AppSettings so
 * non-Compose code (widget, worker) classifies species the same way the UI does.
 */
object PhenologyPrefs {
    const val KEY_SHOW_EARLY_LATE = "show_early_late"

    /** When false, EARLY/LATE collapse into ACTIVE for every species. */
    @Volatile var showEarlyLate: Boolean = true

    /** Widget and worker run without the activity, so they load this themselves. */
    fun load(prefs: android.content.SharedPreferences) {
        showEarlyLate = prefs.getBoolean(KEY_SHOW_EARLY_LATE, true)
    }
}

/**
 * A species' expected weekly activity, derived from its raw weekly counts.
 *
 * Raw weekly counts are noisy for all but the best-sampled species. A week with no records
 * sitting between two active weeks is almost always a sampling gap, not a real absence. We
 * therefore estimate activity with an adaptive circular Gaussian smoother (kernel regression):
 *
 *  - bandwidth shrinks as the species' total record count N grows
 *    (sigma = 3.5 / N^0.25, clamped to 0.6..2.5 weeks), so sparse species borrow strength
 *    from neighbouring weeks while well-sampled species keep their real shape;
 *  - ISO week 53 only exists in some years, so its counts are deflated; it is treated as
 *    missing and estimated from its neighbours;
 *  - the result is blended with the raw relative abundance, weighted by N / (N + 30), so a
 *    genuine high week in a well-sampled species is never smoothed away, while a lone record
 *    in a sparse species cannot claim "peak".
 *
 * Values are relative (0..1, max = 1), like [WeeklyEntry.relAbundance].
 *
 * A species is "seasonal" when its activity drops below [SEASONAL_LOW] for at least
 * [SEASONAL_MIN_LOW_WEEKS] weeks (roughly two months off). Residents that are recorded all
 * year are not seasonal, so EARLY/LATE labels make no sense for them.
 */
class PhenologyProfile private constructor(
    private val activity: FloatArray,
    private val recorded: BooleanArray,
    val isSeasonal: Boolean,
    val sigmaWeeks: Float
) {
    /** Expected relative activity for [week] (1..53); 0 outside that range. */
    fun at(week: Int): Float = activity.getOrElse(week - 1) { 0f }

    /** True when the species has at least one actual record in [week]. */
    fun hasRecords(week: Int): Boolean = recorded.getOrElse(week - 1) { false }

    /**
     * Active = actually recorded that week, or expected there once sampling gaps are smoothed
     * over. Smoothing only ever adds active weeks; a real record is never hidden.
     */
    fun isActive(week: Int): Boolean = hasRecords(week) || at(week) >= ACTIVE_FLOOR

    /**
     * True when activity is building toward [week]'s future (more activity in the next few
     * weeks than the last few). Handles two-peaked species (spring + fall migrants) correctly,
     * unlike "nearest peak".
     */
    fun isRising(week: Int): Boolean {
        var ahead = 0f
        var behind = 0f
        for (d in 1..3) {
            ahead += at(wrap(week + d))
            behind += at(wrap(week - d))
        }
        return ahead >= behind
    }

    companion object {
        const val WEEKS = 53
        /** Below this smoothed activity, a week with no records counts as not active. */
        const val ACTIVE_FLOOR = 0.08f
        const val SEASONAL_LOW = 0.15f
        const val SEASONAL_MIN_LOW_WEEKS = 8

        fun wrap(week: Int): Int = ((week - 1) % WEEKS + WEEKS) % WEEKS + 1

        fun compute(weekly: List<WeeklyEntry>): PhenologyProfile {
            val counts = DoubleArray(WEEKS)
            for (e in weekly) if (e.week in 1..WEEKS) counts[e.week - 1] += e.n.toDouble()
            val n = (0 until WEEKS - 1).sumOf { counts[it] }
            val recorded = BooleanArray(WEEKS) { counts[it] > 0 }
            if (n <= 0.0) return PhenologyProfile(FloatArray(WEEKS), recorded, isSeasonal = false, sigmaWeeks = 0f)

            val sigma = (3.5 / n.pow(0.25)).coerceIn(0.6, 2.5)
            val reach = (3 * sigma).toInt() + 1
            val smoothed = DoubleArray(WEEKS)
            for (w in 0 until WEEKS) {
                var num = 0.0
                var den = 0.0
                for (k in 0 until WEEKS - 1) { // index 52 = week 53, treated as missing
                    val d0 = kotlin.math.abs(w - k)
                    val d = min(d0, WEEKS - d0)
                    if (d > reach) continue
                    val kern = exp(-0.5 * (d / sigma) * (d / sigma))
                    num += kern * counts[k]
                    den += kern
                }
                smoothed[w] = if (den > 0) num / den else 0.0
            }
            val smoothMax = smoothed.max().takeIf { it > 0 } ?: 1.0
            val rawMax = (0 until WEEKS - 1).maxOf { counts[it] }.takeIf { it > 0 } ?: 1.0
            val rawWeight = n / (n + 30.0)

            val activity = FloatArray(WEEKS) { w ->
                val s = smoothed[w] / smoothMax
                val r = if (w == WEEKS - 1) 0.0 else rawWeight * counts[w] / rawMax
                min(1.0, max(s, r)).toFloat()
            }
            val lowWeeks = (0 until WEEKS - 1).count { activity[it] < SEASONAL_LOW }
            return PhenologyProfile(activity, recorded, lowWeeks >= SEASONAL_MIN_LOW_WEEKS, sigma.toFloat())
        }
    }
}

/** Smoothed relative activity of this species in [week] (see [PhenologyProfile]). */
fun Species.abundanceAt(week: Int): Float = profile.at(week)

/** Raw weekly entry, using the 1..53 ordering datasets are written in, with a safe fallback. */
fun Species.weekEntry(week: Int): WeeklyEntry? =
    weekly.getOrNull(week - 1)?.takeIf { it.week == week } ?: weekly.find { it.week == week }
