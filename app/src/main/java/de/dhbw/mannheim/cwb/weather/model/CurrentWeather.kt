package de.dhbw.mannheim.cwb.weather.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class CurrentWeather @JsonCreator constructor(
    @JsonProperty("dt") timestamp: Long, @JsonProperty("sunrise") sunrise: Long,
    @JsonProperty("sunset") sunset: Long,

    @JsonProperty("temp") val temperature: Double,
    @JsonProperty("feels_like") val temperatureFeelsLike: Double,

    @JsonProperty("pressure") val pressure: Int, @JsonProperty("humidity") val humidity: Int,
    @JsonProperty("dew_point") val dewPoint: Double,

    @JsonProperty("clouds") val clouds: Int, @JsonProperty("uvi") val uvIndex: Int,
    @JsonProperty("visibility") val visibility: Int,
    @JsonProperty("wind_speed") val windSpeed: Double?,
    @JsonProperty("wind_gust") val windGust: Double?,
    @JsonProperty("wind_deg") val windDirection: Double?,

    @JsonProperty("rain") rain: Map<String, Double>?,
    @JsonProperty("snow") snow: Map<String, Double>?,

    @JsonProperty("weather") val weather: List<Weather>
) {

    val time: Instant = Instant.ofEpochSecond(timestamp)
    val sunrise = Instant.ofEpochSecond(sunrise)
    val sunset = Instant.ofEpochSecond(sunset)

    val rain: Double? = rain?.get("1h")
    val snow: Double? = snow?.get("1h")

}