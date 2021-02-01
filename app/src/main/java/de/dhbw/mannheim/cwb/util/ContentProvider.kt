package de.dhbw.mannheim.cwb.util

import android.database.Cursor
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import java.time.Instant
import java.time.ZoneId

fun Cursor.getBooleanOrNull(index: Int): Boolean? {
    return getIntOrNull(index)?.let { it != 0 }
}

fun Cursor.getZoneIdOrNull(index: Int): ZoneId? {
    return getStringOrNull(index)?.let { ZoneId.of(it) }
}

fun Cursor.getInstantOrNull(index: Int): Instant? {
    return getLongOrNull(index).takeIf { it != 0L }?.let { Instant.ofEpochMilli(it) }
}