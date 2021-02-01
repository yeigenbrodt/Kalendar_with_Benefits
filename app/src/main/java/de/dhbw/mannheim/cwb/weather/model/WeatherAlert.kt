package de.dhbw.mannheim.cwb.weather.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class WeatherAlert @JsonCreator constructor(
    @JsonProperty("sender_name") val sender: String,
    @JsonProperty("event") val name: String,
    @JsonProperty("start") start: Long,
    @JsonProperty("end") end: Long,
    @JsonProperty("description") val description: String?,
) {
    val start: Instant = Instant.ofEpochSecond(start)
    val end: Instant = Instant.ofEpochSecond(end)
}