package com.codylimber.fieldphenology.data.model

import java.time.LocalDate
import java.time.temporal.IsoFields

/** ISO week-of-year (1..53) of the week before [date]'s week; correct for 52- and 53-week years. */
fun previousIsoWeek(date: LocalDate): Int =
    date.minusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

/** ISO week-of-year (1..53) of the week after [date]'s week; correct for 52- and 53-week years. */
fun nextIsoWeek(date: LocalDate): Int =
    date.plusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
