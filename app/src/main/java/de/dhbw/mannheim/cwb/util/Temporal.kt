package de.dhbw.mannheim.cwb.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.Temporal
import kotlin.reflect.KClass

private val DATE_FORMAT = FormatStyle.MEDIUM
private val TIME_FORMAT = FormatStyle.SHORT

// ---- TRANSFORMATION FUNCTIONS ---- //

fun Temporal.isDate() = this is LocalDate
fun Temporal.isTime() = this is OffsetTime || this is LocalTime
fun Temporal.isDateTime() = this is ZonedDateTime || this is OffsetDateTime || this is LocalDateTime

fun Temporal.toLocalDate(): LocalDate = when (this) {
    is ZonedDateTime -> toLocalDate()
    is OffsetDateTime -> toLocalDate()
    is LocalDateTime -> toLocalDate()
    is LocalDate -> this
    is Instant -> atOffset(ZoneOffset.UTC).toLocalDate()
    else -> unsupportedType(this)
}

fun Temporal.toLocalTime(): LocalTime = when (this) {
    is ZonedDateTime -> toLocalTime()
    is OffsetDateTime -> toLocalTime()
    is LocalDateTime -> toLocalTime()
    is LocalTime -> this
    is Instant -> atOffset(ZoneOffset.UTC).toLocalTime()
    else -> unsupportedType(this)
}

fun Temporal.toLocalDateTime(): LocalDateTime = when (this) {
    is ZonedDateTime -> toLocalDateTime()
    is OffsetDateTime -> toLocalDateTime()
    is LocalDateTime -> this
    is LocalDate -> atStartOfDay()
    is Instant -> atOffset(ZoneOffset.UTC).toLocalDateTime()
    else -> unsupportedType(this)
}

fun Temporal.toInstant(): Instant = when (this) {
    is ZonedDateTime -> toInstant()
    is OffsetDateTime -> toInstant()
    is LocalDateTime -> toInstant(ZoneOffset.UTC)
    is LocalDate -> atStartOfDay().toInstant(ZoneOffset.UTC)
    else -> unsupportedType(this)
}

// ---- ERROR UTIL FUNCTIONS ---- //

private val KClass<*>.name get() = qualifiedName ?: java.name
private fun unsupportedType(temporal: Temporal): Nothing = throw IllegalArgumentException(
    "Unsupported Temporal type ${temporal::class.name}"
)

// ---- FORMAT SINGLE DATE ---- //

fun formatLocalDate(temporal: Temporal): String = DateTimeFormatter.ofLocalizedDate(DATE_FORMAT)
    .format(temporal)

fun formatLocalTime(temporal: Temporal): String = DateTimeFormatter.ofLocalizedTime(TIME_FORMAT)
    .format(temporal)

fun formatLocalDateTime(temporal: Temporal): String = DateTimeFormatter.ofLocalizedDateTime(
    DATE_FORMAT, TIME_FORMAT
).format(temporal)

fun formatTemporal(temporal: Temporal): String = when {
    temporal.isDateTime() -> formatLocalDateTime(temporal)
    temporal.isDate() -> formatLocalDate(temporal)
    temporal.isTime() -> formatLocalTime(temporal)
    else -> temporal.toString()
}

// ---- FORMAT DATE RANGE ---- //

fun formatTemporalRange(start: Temporal, end: Temporal): String {

    val simpleEnd = when {
        (start.isDate() && end.isDate()) || (start.isTime() && end.isTime()) -> {
            if (start == end) null else end
        }
        start.isDateTime() && end.isDateTime() -> {
            val startDate = start.toLocalDate()
            val endDate = end.toLocalDate()

            if (startDate != endDate) end
            else {
                val startTime = start.toLocalTime()
                val endTime = end.toLocalTime()

                if (startTime != endTime) endTime
                else null
            }
        }
        else -> throw IllegalArgumentException(
            "Unsupported temporal type pair " + (start::class.name to end::class.name)
        )
    }

    return setOfNotNull(start, simpleEnd).joinToString(
        separator = " - ", transform = ::formatTemporal
    )
}
