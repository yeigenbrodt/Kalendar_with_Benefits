package de.dhbw.mannheim.cwb.weather.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.zone.ZoneRulesException

class OneCallWeather @JsonCreator constructor(
    @JsonProperty("lat") latitude: Double, @JsonProperty("lon") longitude: Double,
    @JsonProperty("timezone") timeZoneName: String,
    @JsonProperty("timezone_offset") timeZoneOffset: Int,
    @JsonProperty("current") val currentWeather: CurrentWeather? = null,
    @JsonProperty("minutely") val minutelyWeather: List<MinutelyWeather>? = null,
    @JsonProperty("hourly") val hourlyWeather: List<HourlyWeather>? = null,
    @JsonProperty("daily") val dailyWeather: List<DailyWeather>? = null,
    @JsonProperty("alerts") val weatherAlerts: List<WeatherAlert>? = null
) {
    val position: Pair<Double, Double> = latitude to longitude
    val timeZone: ZoneId = try {
        ZoneId.of(timeZoneName)
    } catch (e: ZoneRulesException) {
        ZoneOffset.ofTotalSeconds(timeZoneOffset)
    }
}
