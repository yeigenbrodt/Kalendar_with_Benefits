package de.dhbw.mannheim.cwb.weather

import android.content.res.Resources
import androidx.annotation.StringRes
import com.fasterxml.jackson.databind.ObjectMapper
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.weather.model.OneCallWeather
import java.net.URL

open class OWM(
    apiKey: String, var language: Language = Language.ENGLISH, var unit: Unit = Unit.STANDARD
) {

    var apiKey: String = apiKey
        set(value) {
            if (value.isEmpty() || value.isBlank()) throw IllegalArgumentException(
                "API key can't be empty. Get a free API key from OpenWeatherMap.org."
            )
            field = value
        }

    fun oneCallWeather(
        latitude: Double, longitude: Double, vararg exclude: OneCallWeatherData
    ): OneCallWeather {
        return ObjectMapper().readValue(
            URL("https://api.openweathermap.org/data/2.5/onecall?" + "lat=$latitude&lon=$longitude&" + "appid=$apiKey&units=${unit.value}&lang=${language.value}" + if (exclude.isNotEmpty()) "&exclude=${
                exclude.joinToString(",") { it.value }
            }" else ""), OneCallWeather::class.java)
    }

    enum class Unit(val value: String, @StringRes private val displayRes: Int? = null) {
        IMPERIAL("imperial", R.string.weather_units_imperial),
        METRIC("metric", R.string.weather_units_metric), STANDARD("standard");

        fun isSuitableForUser() = displayRes != null
        fun getDisplayName(resources: Resources) = displayRes?.let { resources.getString(it) }
            ?: name
    }

    enum class Language(val value: String) {
        ARABIC("ar"), BULGARIAN("bg"), CATALAN("ca"), CHINESE_SIMPLIFIED("zh_cn"),
        CHINESE_TRADITIONAL("zh_tw"), CROATIAN("hr"), CZECH("cz"), DUTCH("nl"), ENGLISH("en"),
        FINNISH("fi"), FRENCH("fr"), GALICIAN("gl"), GREEK("el"), GERMAN("de"), HUNGARIAN("hu"),
        ITALIAN("it"), JAPANESE("ja"), KOREAN("kr"), LATVIAN("la"), LITHUANIAN("lt"),
        MACEDONIAN("mk"), PERSIAN("fa"), POLISH("pl"), PORTUGUESE("pt"), ROMANIAN("ro"),
        RUSSIAN("ru"), SLOVAK("sk"), SLOVENIAN("sl"), SPANISH("es"), SWEDISH("se"), TURKISH("tr"),
        UKRAINIAN("ua"), VIETNAMESE("vi")
    }

    enum class OneCallWeatherData(val value: String) {
        CURRENT("current"), MINUTELY("minutely"), HOURLY("hourly"), DAILY("daily"), ALERTS("alerts")
    }
}