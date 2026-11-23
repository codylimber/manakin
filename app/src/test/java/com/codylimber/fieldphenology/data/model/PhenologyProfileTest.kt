package com.codylimber.fieldphenology.data.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhenologyProfileTest {

    @After fun reset() { PhenologyPrefs.showEarlyLate = true }

    private fun species(counts: Map<Int, Int>): Species {
        val max = counts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        return Species(
            taxonId = 1, scientificName = "Test", totalObs = counts.values.sum(), rarity = "Common",
            peakWeek = counts.maxByOrNull { it.value }?.key ?: 0, firstWeek = 0, lastWeek = 0, periodCount = 1,
            weekly = (1..53).map { w -> WeeklyEntry(w, counts[w] ?: 0, (counts[w] ?: 0).toFloat() / max) }
        )
    }

    @Test fun gapWeekBetweenActiveWeeksIsActive() {
        // Sparse species: records in weeks 20-22 and 24-26 but none in week 23.
        val sp = species(mapOf(20 to 2, 21 to 3, 22 to 2, 24 to 2, 25 to 3, 26 to 1))
        assertTrue(SpeciesStatus.classify(sp, 23) != SpeciesStatus.INACTIVE)
    }

    @Test fun farOffSeasonIsInactive() {
        val sp = species(mapOf(20 to 2, 21 to 3, 22 to 2, 24 to 2, 25 to 3, 26 to 1))
        assertEquals(SpeciesStatus.INACTIVE, SpeciesStatus.classify(sp, 45))
    }

    @Test fun wellSampledPeakSurvivesSmoothing() {
        val counts = (1..52).associateWith { 0 }.toMutableMap()
        counts[30] = 1000; counts[29] = 200; counts[31] = 200
        val sp = species(counts)
        assertEquals(SpeciesStatus.PEAK, SpeciesStatus.classify(sp, 30))
    }

    @Test fun residentIsNotSeasonalAndNeverEarlyOrLate() {
        // Recorded all year with a summer bump: low winter weeks should read Active.
        val counts = (1..52).associateWith { w -> if (w in 18..30) 100 else 15 }
        val sp = species(counts)
        assertFalse(sp.profile.isSeasonal)
        assertEquals(SpeciesStatus.ACTIVE, SpeciesStatus.classify(sp, 2))
    }

    @Test fun seasonalSpeciesGetsEarlyThenLate() {
        val counts = mapOf(14 to 2, 15 to 10, 16 to 40, 17 to 100, 18 to 100, 19 to 40, 20 to 10, 21 to 2)
        val sp = species(counts)
        assertTrue(sp.profile.isSeasonal)
        assertEquals(SpeciesStatus.EARLY, SpeciesStatus.classify(sp, 14))
        assertEquals(SpeciesStatus.LATE, SpeciesStatus.classify(sp, 21))
    }

    @Test fun earlyLateToggleOffShowsActive() {
        val counts = mapOf(14 to 2, 15 to 10, 16 to 40, 17 to 100, 18 to 100, 19 to 40, 20 to 10, 21 to 2)
        val sp = species(counts)
        PhenologyPrefs.showEarlyLate = false
        assertEquals(SpeciesStatus.ACTIVE, SpeciesStatus.classify(sp, 14))
    }

    @Test fun twoPeakedMigrantUsesTrendNotNearestPeak() {
        // Spring peak at 20, fall peak at 38: the tail of spring is falling (LATE) and the
        // start of fall is rising (EARLY), whichever peak happens to be nearer.
        val counts = mapOf(
            18 to 10, 19 to 80, 20 to 100, 21 to 80, 22 to 10, 23 to 3,
            35 to 3, 36 to 20, 37 to 80, 38 to 100, 39 to 80, 40 to 20
        )
        val sp = species(counts)
        assertEquals(SpeciesStatus.LATE, SpeciesStatus.classify(sp, 23))
        assertEquals(SpeciesStatus.EARLY, SpeciesStatus.classify(sp, 35))
    }

    @Test fun yearWrapIsContinuous() {
        // Winter species active across the new year.
        val counts = mapOf(50 to 30, 51 to 60, 52 to 80, 1 to 100, 2 to 70, 3 to 30)
        val sp = species(counts)
        assertTrue(SpeciesStatus.classify(sp, 53) != SpeciesStatus.INACTIVE)
        assertTrue(SpeciesStatus.classify(sp, 1) == SpeciesStatus.PEAK)
    }

    @Test fun emptySpeciesIsInactive() {
        val sp = species(emptyMap())
        assertEquals(SpeciesStatus.INACTIVE, SpeciesStatus.classify(sp, 10))
    }
}
