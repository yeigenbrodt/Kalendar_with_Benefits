package de.dhbw.mannheim.cwb.weather.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

class DailyWeather @JsonCreator constructor(
    @JsonProperty("dt") timestamp: Long, @JsonProperty("sunrise") sunrise: Long,
    @JsonProperty("sunset") sunset: Long,

    @JsonProperty("temp") temperature: Map<String, Double>,
    @JsonProperty("feels_like") val temperatureFeelsLike: DailyTemperature,

    @JsonProperty("pressure") val pressure: Int, @JsonProperty("humidity") val humidity: Int,
    @JsonProperty("dew_point") val dewPoint: Double,

    @JsonProperty("clouds") val clouds: Int, @JsonProperty("uvi") val uvIndex: Int,
    @JsonProperty("visibility") val visibility: Int,
    @JsonProperty("wind_speed") val windSpeed: Double?,
    @JsonProperty("wind_gust") val windGust: Double?,
    @JsonProperty("wind_deg") val windDirection: Double?,

    @JsonProperty("pop") val probabilityOfPrecipitation: Double,
    @JsonProperty("rain") val rain: Double?, @JsonProperty("snow") val snow: Double?,

    @JsonProperty("weather") val weather: List<Weather>
) {

    val time: Instant = Instant.ofEpochSecond(timestamp)
    val sunrise: Instant = Instant.ofEpochSecond(sunrise)
    val sunset: Instant = Instant.ofEpochSecond(sunset)

    val temperatureRange: ClosedRange<Double> =
        temperature.getValue("min")..temperature.getValue("max")
    val temperature: DailyTemperature = DailyTemperature(
        temperature.getValue("morn"), temperature.getValue("day"), temperature.getValue("eve"),
        temperature.getValue("night")
    )
}

data class DailyTemperature @JsonCreator constructor(
    @JsonProperty("morn") val morning: Double,
    @JsonProperty("day") val noon: Double,
    @JsonProperty("eve") val evening: Double,
    @JsonProperty("night") val night: Double,
)