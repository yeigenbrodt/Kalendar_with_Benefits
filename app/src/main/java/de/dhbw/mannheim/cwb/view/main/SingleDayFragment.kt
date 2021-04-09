package de.dhbw.mannheim.cwb.view.main

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.util.formatTemporal
import de.dhbw.mannheim.cwb.util.formatTemporalRange
import de.dhbw.mannheim.cwb.util.toInstant
import de.dhbw.mannheim.cwb.util.toLocalDate
import de.dhbw.mannheim.cwb.view.calendar.EventViewActivity
import de.dhbw.mannheim.cwb.view.settings.SettingsActivity
import de.dhbw.mannheim.cwb.view.weather.SharedWeatherModel
import de.dhbw.mannheim.cwb.weather.OWM
import de.dhbw.mannheim.cwb.weather.model.OneCallWeather
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.JulianFields
import java.time.temporal.Temporal
import java.util.concurrent.FutureTask
import kotlin.math.roundToInt

class SingleDayFragment : Fragment(R.layout.main_single_day_fragment) {

    private lateinit var date: LocalDate
    private lateinit var entryAdapter: SingleDayEntryAdapter

    private val sharedWeatherModel: SharedWeatherModel by activityViewModels()

    // ----------------------------------------------------- //

    private val weather: LiveData<OneCallWeather> by lazy { sharedWeatherModel.oneCallWeather }
    private val events: LiveData<List<EventInstance>> by lazy {
        EventData(requireActivity().contentResolver, date)
    }

    // ----------------------------------------------------- //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        date = arguments?.getString("date")?.let { LocalDate.parse(it) } ?: LocalDate.now()

        entryAdapter = SingleDayEntryAdapter(layoutInflater).apply {
            addSublist("weather")
            addSublist("weather.alerts")
            addSublist("calendar.events")
        }

        weather.observe(this, this::updateWeather)
        events.observe(this, this::updateEvents)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view as RecyclerView

        view.layoutManager = object : LinearLayoutManager(context) {
            override fun onLayoutChildren(
                recycler: RecyclerView.Recycler?, state: RecyclerView.State?
            ) {
                try {
                    super.onLayoutChildren(recycler, state)
                } catch (e: IndexOutOfBoundsException) {
                    // ignored for now
                    // should be worked on later -> maybe with `androidx.recyclerview.widget.DiffUtil`
                    // probably caused by inconsistencies in `de.dhbw.mannheim.cwb.view.main.YourDayEntryAdapter`
                    System.err.println(e::class.qualifiedName + ": " + e.message)
                }
            }
        }

        entryAdapter.notifyDataSetChanged()

        view.adapter = entryAdapter

    }

    // ----------------------------------------------------- //

    private fun updateWeather(weatherResult: OneCallWeather?) {
        val weather = entryAdapter.sublist("weather").apply { clear() }
        val alerts = entryAdapter.sublist("weather.alerts").apply { clear() }

        if (weatherResult == null) {
            // no weather could be loaded

            if (sharedWeatherModel.location.value == null) {
                // no home address given, notify user of problem
                weather.add(
                    DayEntry(
                        title = getString(R.string.error_weather_missing_home_address_title),
                        text = getString(
                            R.string.error_weather_missing_home_address_description
                        ), onClick = {
                            requireContext().startActivity(
                                Intent(context, SettingsActivity::class.java)
                            )
                        }, buttons = mapOf(
                            getString(R.string.error_weather_missing_home_address_button) to null
                        )
                    )
                )
            } // else unknown -> could be improved later

            return
        }

        weatherResult.run {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)::format

            val formatTemperature = when (sharedWeatherModel.unit.value) {
                OWM.Unit.IMPERIAL -> { temp: Double -> "${temp.roundToInt()} °F" }
                OWM.Unit.METRIC -> { temp: Double -> "${temp.roundToInt()} °C" }
                else -> { temp: Double -> "${temp.roundToInt()} K" }
            }

            fun addWeatherEntry(title: String, subtitle: String, icon: String) {
                weather.add(
                    DayEntry(title, subtitle, icon = when (icon) {
                        "01d" -> R.drawable.lh_icon_weather_sun
                        "01n" -> R.drawable.lh_icon_moon
                        "02d", "02n" -> R.drawable.lh_icon_weather_cloud_moderate_sun
                        "03d", "03n" -> R.drawable.lh_icon_weather_cloud_light_sun
                        "04d", "04n" -> R.drawable.lh_icon_weather_cloud
                        "09d", "09n" -> R.drawable.lh_icon_weather_cloud_moderate_rain_shower
                        "10d", "10n" -> R.drawable.lh_icon_weather_cloud_strong_rain
                        "11d", "11n" -> R.drawable.lh_icon_weather_cloud_moderate_rain_flash_thunderstorm
                        "13d", "13n" -> R.drawable.lh_icon_weather_snowflake
                        "50d", "50n" -> R.drawable.lh_icon_weather_cloud_fog
                        else -> null
                    }?.let {
                        ResourcesCompat.getDrawable(resources, it, null)
                    })
                )
            }

            if (date == LocalDate.now()) currentWeather?.let {
                addWeatherEntry(
                    it.weather.first().description, formatTemperature(it.temperature),
                    it.weather.first().icon
                )
            } else dailyWeather?.first {
                it.time.atZone(timeZone).toLocalDate() == date
            }?.let {
                addWeatherEntry(
                    it.weather.first().description, it.temperatureRange.run {
                        formatTemperature(start) + " · " + formatTemperature(endInclusive)
                    }, it.weather.first().icon
                )
            }

            weatherAlerts?.filter { alert ->
                val start = alert.start.atZone(timeZone).toLocalDate()
                val end = alert.end.atZone(timeZone).toLocalDate()

                date in start..end
            }?.sortedBy { alert ->
                alert.start
            }?.forEach { alert ->
                alerts.add(
                    DayEntry(
                        title = alert.name, subtitle = formatTemporalRange(
                            alert.start.atZone(timeZone), alert.end.atZone(timeZone)
                        ), text = alert.description, icon = ResourcesCompat.getDrawable(
                            resources, R.drawable.ic_baseline_warning_24, null
                        )
                    )
                )
            }
        }
    }

    private fun updateEvents(events: List<EventInstance>?) {
        if (events == null) return

        val eventAdapter = entryAdapter.sublist("calendar.events").apply { clear() }

        events.sortedBy { it.begin.toInstant() }.map {
            it.run {
                DayEntry(title = title, subtitle = listOfNotNull(
                    if (end != null) formatTemporalRange(begin, end) else formatTemporal(begin),
                    location?.takeIf { it.isNotBlank() }).joinToString(
                    separator = "\n"
                ), icon = color?.let { ColorDrawable(it) }, onClick = {
                    requireContext().startActivity(
                        Intent(
                            Intent.ACTION_VIEW, ContentUris.withAppendedId(
                                CalendarContract.Events.CONTENT_URI, eventId
                            ), context, EventViewActivity::class.java
                        )
                    )
                })
            }
        }.let { eventAdapter.addAll(it) }
    }

}

// ----------------------------------------------------- //

private data class EventInstance(
    val id: Long, val eventId: Long, val title: String?, val allDay: Boolean, val begin: Temporal,
    val end: Temporal?, val location: String?, val color: Int?
)

private class EventData(
    private val contentResolver: ContentResolver, private val date: Temporal
) : LiveData<List<EventInstance>>() {

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = onChange(selfChange, null)
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            loadEvents()
        }
    }

    // ----------------------------------------------------- //

    override fun onActive() {
        super.onActive()
        contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        loadEvents()
    }

    override fun onInactive() {
        super.onInactive()
        contentResolver.unregisterContentObserver(observer)
    }

    // ----------------------------------------------------- //

    private fun loadEvents() = FutureTask<Unit> {

        fun Cursor.getZoneIdOrNull(index: Int): ZoneId? {
            return getStringOrNull(index)?.let { ZoneId.of(it) }
        }

        fun Cursor.getInstantOrNull(index: Int): Instant? {
            return getLongOrNull(index)?.let { Instant.ofEpochMilli(it) }
        }

        fun Instant.getTemporal(zone: ZoneId, allDay: Boolean): Temporal {
            return if (allDay) toLocalDate() else atZone(zone)
        }

        contentResolver.query(
            CalendarContract.Instances.CONTENT_BY_DAY_URI.buildUpon()
                .appendPath(date.getLong(JulianFields.JULIAN_DAY).toString())
                .appendPath(date.getLong(JulianFields.JULIAN_DAY).toString()).build(), arrayOf(
                CalendarContract.Instances._ID, CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.CALENDAR_TIME_ZONE,
                CalendarContract.Instances.EVENT_TIMEZONE,
                CalendarContract.Instances.EVENT_END_TIMEZONE, CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION,
                CalendarContract.Instances.DISPLAY_COLOR
            ), null, null, null
        )?.apply {
            postValue(mutableListOf<EventInstance>().also {
                while (moveToNext()) {
                    var i = 0

                    val id = getLong(i)
                    val eventId = getLong(++i)
                    val title = getStringOrNull(++i)
                    val allDay = getIntOrNull(++i)?.let { it != 0 } ?: false
                    val calendarTimeZone =
                        getZoneIdOrNull(++i).takeUnless { allDay } ?: ZoneOffset.UTC
                    val timezone = getZoneIdOrNull(++i).takeUnless { allDay } ?: calendarTimeZone
                    val endTimezone = getZoneIdOrNull(++i).takeUnless { allDay } ?: timezone
                    val begin = getInstantOrNull(++i)!!
                    val end = getInstantOrNull(++i)?.let { if (allDay) it.minusNanos(1) else it }
                    val location = getStringOrNull(++i)
                    val color = getIntOrNull(++i)

                    it += EventInstance(
                        id, eventId, title, allDay, begin.getTemporal(timezone, allDay),
                        end?.getTemporal(endTimezone, allDay), location, color
                    )
                }
            })
        }?.close()
    }.apply { Thread(this).start() }

}
