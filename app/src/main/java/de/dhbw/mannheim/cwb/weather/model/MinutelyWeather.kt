package de.dhbw.mannheim.cwb.weather.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class MinutelyWeather @JsonCreator constructor(
    @JsonProperty("dt") timestamp: Long,
    @JsonProperty("precipitation") val precipitation: Double,
) {
    val time: Instant = Instant.ofEpochSecond(timestamp)
}