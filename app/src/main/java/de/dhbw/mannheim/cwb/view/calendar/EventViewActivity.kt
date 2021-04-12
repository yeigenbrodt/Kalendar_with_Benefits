package de.dhbw.mannheim.cwb.view.calendar

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.core.os.bundleOf
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.databinding.EventViewActivityBinding
import de.dhbw.mannheim.cwb.databinding.MaterialListEntryViewBinding
import de.dhbw.mannheim.cwb.transit.pojo.Stop
import de.dhbw.mannheim.cwb.transit.pojo.Trip
import de.dhbw.mannheim.cwb.transit.pojo.TripData
import de.dhbw.mannheim.cwb.transit.util.AsyncRoutePlanner
import de.dhbw.mannheim.cwb.util.formatLocalDateTime
import de.dhbw.mannheim.cwb.util.formatTemporal
import de.dhbw.mannheim.cwb.util.formatTemporalRange
import de.dhbw.mannheim.cwb.util.getBooleanOrNull
import de.dhbw.mannheim.cwb.util.getInstantOrNull
import de.dhbw.mannheim.cwb.util.getZoneIdOrNull
import de.dhbw.mannheim.cwb.util.toLocalDate
import de.dhbw.mannheim.cwb.util.toLocalDateTime
import de.dhbw.mannheim.cwb.util.toLocalTime
import de.dhbw.mannheim.cwb.view.transit.FindTransitDialog
import de.dhbw.mannheim.cwb.view.transit.TransitViewModel
import de.dhbw.mannheim.cwb.view.transit.ViewTransitTripDialog
import de.dhbw.mannheim.cwb.view.weather.SharedWeatherModel
import de.dhbw.mannheim.cwb.weather.OWM
import de.dhbw.mannheim.cwb.weather.model.OneCallWeather
import java.lang.Exception
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.temporal.Temporal
import java.util.*
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.reflect.KClass

class EventViewActivity : FragmentActivity() {

    private val eventId: Long by lazy { ContentUris.parseId(intent.data!!) }

    private val event: LiveData<Event> by lazy { EventData(contentResolver, eventId) }

    // ----------------------------------------------------------- //

    private val binding: EventViewActivityBinding by lazy {
        EventViewActivityBinding.inflate(layoutInflater)
    }

    // ----------------------------------------------------------- //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.topAppBar.setNavigationOnClickListener { onBackPressed() }

        event.observe(this, this::updateEvent)

        fun Fragment.withEvent() = this.apply {
            arguments = bundleOf("event_id" to eventId)
        }

        // append fragments if no instance of the same class was added
        operator fun <T : Fragment> FragmentTransaction.plusAssign(fragment: KClass<T>) {
            if (supportFragmentManager.findFragmentByTag(fragment.qualifiedName) == null) {
                val f = fragment.constructors.first { it.parameters.all { it.isOptional } }.call()
                this.add(R.id.content, f.withEvent(), fragment.qualifiedName)
            }
        }

        val it = supportFragmentManager.beginTransaction()

        it += EventFragment::class
        it += ReminderFragment::class
        it += AttendeeFragment::class
        it += RouteFragment::class
        it += WeatherFragment::class

        it.commit()

    }

    override fun onBackPressed() {
        // reload event on fragment close -> the only fragment that is pushed on the back stack
        // is the FindTransitDialog. For that the Color of the Navigation Scrim is changed
        // and must be reset. Should later be done in a more sophisticated way.
        if (supportFragmentManager.backStackEntryCount == 1) updateEvent(event.value)
        super.onBackPressed()
    }

    // ----------------------------------------------------------- //

    private fun updateEvent(event: Event?) {
        event?.run {
            binding.topAppBar.title = title ?: ""
            binding.topAppBar.menu.clear()

            // only allow access to edit if the user has right to edit
            if (calendarAccessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                binding.topAppBar.inflateMenu(R.menu.edit_menu)

                binding.topAppBar.menu.findItem(R.id.event_edit).setOnMenuItemClickListener {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_EDIT, ContentUris.withAppendedId(
                                    CalendarContract.Events.CONTENT_URI, event.id
                                )
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        System.err.println(e::class.qualifiedName + ": " + e.message)
                        Snackbar.make(
                            binding.root, R.string.error_no_activity_found_to_edit,
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                    false
                }
                binding.topAppBar.menu.findItem(R.id.route_find).setOnMenuItemClickListener {
                    val dialog = FindTransitDialog()

                    dialog.arguments = bundleOf(
                        FindTransitDialog.EXTRA_EVENT_ID to event.id,
                        FindTransitDialog.EXTRA_ORIGIN_ADDRESS to PreferenceManager.getDefaultSharedPreferences(
                            this@EventViewActivity
                        ).getString("location_home", null),
                        FindTransitDialog.EXTRA_DESTINATION_ADDRESS to event.location,
                        FindTransitDialog.EXTRA_ARRIVAL_DATE_TIME to (if (event.allDay) event.start.toLocalDate()
                            .atTime(LocalTime.NOON) else event.start.toLocalDateTime()).toString()
                    )

                    supportFragmentManager.beginTransaction().addToBackStack(null)
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .add(android.R.id.content, dialog, null).commit()

                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        val attr = obtainStyledAttributes(intArrayOf(R.attr.colorPrimaryDark))

                        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                        window.addFlags(
                            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        )
                        window.statusBarColor = attr.getColor(0, Color.BLACK)

                        attr.recycle()
                    }

                    true
                }
            }

            displayColorValue?.let { color ->
                // text / icon color according to https://www.w3.org/TR/WCAG20/
                val luminance =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        Color.valueOf(color).luminance()
                    } else listOf(
                        color and 0xFF0000 shr 16, color and 0xFF00 shr 8, color and 0xFF
                    ).map { it / 255.0 }.map {
                        if (it <= 0.3928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4)
                    }.let { (r, g, b) -> 0.2126 * r + 0.7152 * g + 0.0722 * b }.toFloat()

                val textColor = if (luminance > 0.179) Color.BLACK else Color.WHITE

                binding.topAppBar.apply {
                    setBackgroundColor(color or 0xFF000000.toInt()) // ignore alpha channel

                    val filter = PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_ATOP)

                    menu.forEach { it.icon.colorFilter = filter }
                    navigationIcon?.colorFilter = filter
                }

                binding.collapsingAppBar.apply {
                    setExpandedTitleColor(textColor)
                    setCollapsedTitleTextColor(textColor)
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                    )
                    window.statusBarColor = color or 0xFF000000.toInt()
                }
            }
        } ?: finish()
    }

}

// ------------------------------------------------ //

abstract class LiveFragment<T> : Fragment() {

    private val group: LinearLayout by lazy {
        LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
    }

    // ------------------------------------------------ //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        data.observe(this) {
            group.removeAllViews()
            if (it != null) onUpdate(it).forEach(group::addView)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = group

    // ------------------------------------------------ //

    protected abstract val data: LiveData<T>
    protected abstract fun onUpdate(t: T): List<View>

}

abstract class LiveListFragment<T> : LiveFragment<List<T>>() {

    override fun onUpdate(t: List<T>): List<View> {
        return if (t.isNotEmpty()) mutableListOf<View>().also {
            onFirstEntry()?.apply(it::add)
            it.addAll(t.map(this::onUpdateEntry))
        } else emptyList()
    }

    open fun onFirstEntry(): View? = null
    abstract fun onUpdateEntry(t: T): View

}

// ------------------------------------------------ //

class AttendeeFragment : LiveListFragment<Attendee>() {

    override val data: LiveData<List<Attendee>> by lazy {
        AttendeeData(requireContext().contentResolver, requireArguments().getLong("event_id"))
    }

    override fun onFirstEntry(): View {
        val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

        binding.title.text = getString(R.string.attendees)
        binding.subtitle.visibility = View.GONE
        binding.primary.visibility = View.GONE

        return binding.root
    }

    override fun onUpdateEntry(t: Attendee): View {
        val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

        binding.icon.setImageDrawable(
            ResourcesCompat.getDrawable(
                resources, when (t.status) {
                    Attendee.Status.NONE, Attendee.Status.ACCEPTED -> R.drawable.ic_baseline_person_24
                    Attendee.Status.INVITED, Attendee.Status.TENTATIVE -> R.drawable.ic_baseline_person_outline_24
                    Attendee.Status.DECLINED -> R.drawable.ic_baseline_person_off_24
                }, null
            )!!
        )

        binding.title.text = t.name ?: t.email ?: getString(R.string.attendee_name_unknown)
        binding.subtitle.text = listOfNotNull(
            t.email.takeIf { t.name != null },
            when (t.relationship) {
                Attendee.Relationship.ORGANIZER -> getString(
                    R.string.attendee_relationship_organizer
                )
                Attendee.Relationship.PERFORMER -> getString(
                    R.string.attendee_relationship_performer
                )
                Attendee.Relationship.SPEAKER -> getString(
                    R.string.attendee_relationship_speaker
                )
                else -> null
            },
        ).joinToString(separator = "\n")

        binding.primary.visibility = View.GONE

        return binding.root
    }

}

class EventFragment : LiveFragment<Event>() {

    override val data: LiveData<Event> by lazy {
        EventData(requireContext().contentResolver, requireArguments().getLong("event_id"))
    }

    override fun onUpdate(t: Event) = t.run {
        listOfNotNull(Triple(
            R.drawable.ic_baseline_access_time_24, R.string.time,
            if (end != null) formatTemporalRange(start, end) else formatTemporal(start)
        ), location.takeUnless { it.isNullOrBlank() }?.let { location ->
            Triple(R.drawable.ic_baseline_place_24, R.string.location, location)
        }).map { (icon, title, subtitle) ->
            val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

            binding.icon.setImageDrawable(ResourcesCompat.getDrawable(resources, icon, null)!!)
            binding.title.text = getString(title)
            binding.subtitle.text = subtitle

            binding.root
        }
    }

}

class ReminderFragment : LiveListFragment<Reminder>() {

    override val data: LiveData<List<Reminder>> by lazy {
        ReminderData(requireContext().contentResolver, requireArguments().getLong("event_id"))
    }

    override fun onFirstEntry(): View {
        val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

        binding.title.text = getString(R.string.reminders)
        binding.subtitle.visibility = View.GONE
        binding.primary.visibility = View.GONE

        return binding.root
    }

    override fun onUpdateEntry(t: Reminder): View {
        val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

        binding.icon.setImageDrawable(
            ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_alarm_24, null)!!
        )

        binding.title.text = when (t.method) {
            Reminder.Method.DEFAULT -> getString(R.string.reminder_method_default)
            Reminder.Method.ALERT -> getString(R.string.reminder_method_alert)
            Reminder.Method.EMAIL -> getString(R.string.reminder_method_email)
            Reminder.Method.SMS -> getString(R.string.reminder_method_sms)
            Reminder.Method.ALARM -> getString(R.string.reminder_method_alarm)
        }
        binding.subtitle.text = t.minutes.let {
            when {
                it >= 60 -> resources.getQuantityString(
                    R.plurals.reminder_time_hours, it / 60, it / 60
                )
                it > 0 -> resources.getQuantityString(
                    R.plurals.reminder_time_minutes, it, it
                )
                else -> getString(R.string.reminder_time_at_start)
            }
        }

        return binding.root
    }

}

class RouteFragment : LiveFragment<List<Route>>() {

    override val data: LiveData<List<Route>> by lazy {
        RouteData(requireContext(), requireArguments().getLong("event_id"))
    }

    override fun onUpdate(t: List<Route>) = t.takeIf { it.isNotEmpty() }?.let {
        listOf(let {
            val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

            binding.title.text = getString(R.string.route)
            binding.subtitle.visibility = View.GONE
            binding.primary.visibility = View.GONE

            binding.root
        }, *t.mapIndexed { index, trip ->
            val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

            binding.title.text = getString(R.string.numbered_trip, index + 1)

            binding.subtitle.text =
                listOf(trip.origin, trip.destination).joinToString(separator = "\n") {
                    it.name + " (" + formatLocalDateTime(it.time) + ")"
                }

            binding.icon.setImageDrawable(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_train_24, null)!!
            )

            binding.secondary.visibility = View.VISIBLE
            binding.secondaryIcon.setImageDrawable(
                ResourcesCompat.getDrawable(resources, R.drawable.ic_baseline_delete_24, null)!!
            )
            binding.secondaryIcon.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext()).setPositiveButton(
                    android.R.string.yes
                ) { _, _ ->
                    trip.run {
                        if (tripData.trips.all { it == this.source }) {
                            // the trip data only contains this route so it can be removed
                            AsyncRoutePlanner.deleteTripDataFromDatabase(
                                requireContext(), tripData
                            )
                        } else {
                            // the trip data contains other trips, that should be preserved
                            AsyncRoutePlanner.saveTripToDatabase(
                                requireContext(), tripData.eventId, tripData,
                                *tripData.trips.filterNot { it == source }.toTypedArray()
                            )
                        }
                    }
                }.setNegativeButton(android.R.string.no) { dialog, _ ->
                    dialog.cancel()
                }.setMessage(
                    getString(
                        R.string.alert_message_delete_route, trip.origin.name, trip.destination.name
                    )
                ).show()
            }

            val viewModel: TransitViewModel by activityViewModels()

            binding.root.setOnClickListener {
                viewModel.trip.value = trip.source

                ViewTransitTripDialog().show(
                    parentFragmentManager.beginTransaction().addToBackStack(null), null
                )
            }

            binding.root
        }.toTypedArray())
    } ?: emptyList()

}

class WeatherFragment : LiveFragment<OneCallWeather>() {

    private val sharedWeather: SharedWeatherModel by activityViewModels()

    private val event: LiveData<Event> by lazy {
        EventData(requireContext().contentResolver, requireArguments().getLong("event_id"))
    }

    override val data: LiveData<OneCallWeather> get() = sharedWeather.oneCallWeather

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            when (key) {
                "weather_unit" -> {
                    sharedWeather.unit.value =
                        OWM.Unit.valueOf(preferences.getString("weather_unit", "METRIC")!!)
                }
            }
        }

    // ------------------------------------------------------- //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        event.observe(this) {
            if (it.location == null || it.start.toLocalDate() > LocalDate.now()
                    .plusDays(6) || it.start.toLocalDateTime() < LocalDateTime.now()
            ) {
                sharedWeather.location.value = null
            } else thread {
                try {
                    sharedWeather.location.postValue(
                        Geocoder(requireContext()).getFromLocationName(it.location, 1)
                            .singleOrNull()
                    )
                } catch (e: Exception) {
                    System.err.println(e::class.qualifiedName + ": " + e.message)
                }
            }
        }

        PreferenceManager.getDefaultSharedPreferences(requireContext()).let {
            it.registerOnSharedPreferenceChangeListener(preferenceListener)
            preferenceListener.onSharedPreferenceChanged(it, "weather_unit")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    // ------------------------------------ //

    override fun onUpdate(t: OneCallWeather) = listOfNotNull(event.value?.let { event ->
        val start = event.start.toLocalDate()
        val today = LocalDate.now()

        var icon: String? = null
        var weather: String? = null
        var temperature: String? = null

        val formatTemperature = when (sharedWeather.unit.value) {
            OWM.Unit.IMPERIAL -> { temp: Double -> "${temp.roundToInt()} °F" }
            OWM.Unit.METRIC -> { temp: Double -> "${temp.roundToInt()} °C" }
            else -> { temp: Double -> "${temp.roundToInt()} K" }
        }

        if (start.toLocalDate() == today && !event.allDay) {
            val startTime = event.start.toLocalTime().truncatedTo(ChronoUnit.HOURS)

            if (startTime >= LocalTime.now().truncatedTo(ChronoUnit.HOURS)) {
                t.hourlyWeather?.firstOrNull {
                    it.time.atZone(t.timeZone).toLocalTime()
                        .truncatedTo(ChronoUnit.HOURS) == startTime
                }?.let {
                    weather = it.weather.firstOrNull()?.description
                    icon = it.weather.firstOrNull()?.icon
                    temperature = formatTemperature(it.temperature)
                }
            }
        } else {
            t.dailyWeather?.firstOrNull { it.time.atZone(t.timeZone).toLocalDate() == start }?.let {
                weather = it.weather.firstOrNull()?.description
                icon = it.weather.firstOrNull()?.icon
                temperature =
                    formatTemperature(it.temperatureRange.start) + " - " + formatTemperature(
                        it.temperatureRange.endInclusive
                    )
            }
        }

        if (weather != null && temperature != null) {
            val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

            when (icon) {
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

            }.let {
                if (it != null) binding.icon.setImageDrawable(
                    ResourcesCompat.getDrawable(resources, it, null)!!
                ) else {
                    binding.primary.visibility = View.GONE
                }
            }

            binding.title.text = weather
            binding.subtitle.text = temperature

            binding.root
        } else null
    })
}

// ------------------------------------------------ //

data class Attendee(
    val id: Long, val name: String? = null, val email: String? = null,
    val relationship: Relationship = Relationship.NONE, val status: Status = Status.NONE,
    val type: Type = Type.NONE, val identity: Pair<String, String>? = null
) {
    val identityNamespace get() = identity?.second
    val identityValue get() = identity?.first

    enum class Relationship(val flag: Int) {
        NONE(CalendarContract.Attendees.RELATIONSHIP_NONE),
        ATTENDEE(CalendarContract.Attendees.RELATIONSHIP_ATTENDEE),
        ORGANIZER(CalendarContract.Attendees.RELATIONSHIP_ORGANIZER),
        PERFORMER(CalendarContract.Attendees.RELATIONSHIP_PERFORMER),
        SPEAKER(CalendarContract.Attendees.RELATIONSHIP_SPEAKER);

        companion object {
            fun get(flag: Int) = values().firstOrNull { it.flag == flag }
        }
    }

    enum class Status(val flag: Int) {
        NONE(CalendarContract.Attendees.ATTENDEE_STATUS_NONE),
        ACCEPTED(CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED),
        DECLINED(CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED),
        INVITED(CalendarContract.Attendees.ATTENDEE_STATUS_INVITED),
        TENTATIVE(CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE);

        companion object {
            fun get(flag: Int) = values().firstOrNull { it.flag == flag }
        }
    }

    enum class Type(val flag: Int) {
        NONE(CalendarContract.Attendees.TYPE_NONE),
        REQUIRED(CalendarContract.Attendees.TYPE_REQUIRED),
        OPTIONAL(CalendarContract.Attendees.TYPE_OPTIONAL),
        RESOURCE(CalendarContract.Attendees.TYPE_RESOURCE);

        companion object {
            fun get(flag: Int) = values().firstOrNull { it.flag == flag }
        }
    }
}

data class Event(
    val id: Long, val displayColorValue: Int?, val title: String?, val description: String?,
    val calendarName: String?, val location: String?, val calendarTimezone: ZoneId?,
    val start: Temporal, val end: Temporal?, val allDay: Boolean, val accessLevel: AccessLevel,
    val calendarAccessLevel: Int, val availability: Availability?, val status: Status?,
    val hasAlarm: Boolean, val hasAttendeeData: Boolean, val originalId: Long?,
    val originalTime: Instant?, val rrule: String?, val rdate: String?, val exrule: String?,
    val exdate: String?, val lastDate: Instant?, val customAppPackage: String?,
    val customAppUri: Uri?
) {
    enum class AccessLevel(val flag: Int) {
        DEFAULT(CalendarContract.Events.ACCESS_DEFAULT),
        CONFIDENTIAL(CalendarContract.Events.ACCESS_CONFIDENTIAL),
        PRIVATE(CalendarContract.Events.ACCESS_PRIVATE),
        PUBLIC(CalendarContract.Events.ACCESS_PUBLIC);

        companion object {
            fun get(flag: Int) = values().firstOrNull { it.flag == flag }
        }
    }

    enum class Availability(val flag: Int) {
        BUSY(CalendarContract.Events.AVAILABILITY_BUSY),
        FREE(CalendarContract.Events.AVAILABILITY_FREE),
        TENTATIVE(CalendarContract.Events.AVAILABILITY_TENTATIVE);

        companion object {
            fun get(flag: Int) = values().firstOrNull { it.flag == flag }
        }
    }

    enum class Status(val flag: Int) {
        TENTATIVE(CalendarContract.Events.STATUS_TENTATIVE),
        CONFIRMED(CalendarContract.Events.STATUS_CONFIRMED),
        CANCELED(CalendarContract.Events.STATUS_CANCELED);

        companion object {
            fun get(flag: Int) = values().firstOrNull { it.flag == flag }
        }
    }
}

data class Reminder(
    val id: Long, val method: Method, val minutes: Int,
) {
    enum class Method(val flag: Int) {
        DEFAULT(CalendarContract.Reminders.METHOD_DEFAULT),
        ALERT(CalendarContract.Reminders.METHOD_ALERT),
        EMAIL(CalendarContract.Reminders.METHOD_EMAIL), SMS(CalendarContract.Reminders.METHOD_SMS),
        ALARM(CalendarContract.Reminders.METHOD_ALARM);

        companion object {
            fun get(flag: Int) = values().firstOrNull { it.flag == flag }
        }
    }
}

data class Route(
    val tripData: TripData, val source: Trip, val origin: Stop, val destination: Stop
) {
    data class Stop(val name: String, val time: LocalDateTime)
}

// ------------------------------------------------------ //

private abstract class AbstractEventData<T>(
    protected val contentResolver: ContentResolver, protected val eventId: Long
) : LiveData<T>() {

    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = onChange(selfChange, null)
        override fun onChange(selfChange: Boolean, uri: Uri?) = load()
    }

    // ----------------------------------------------------- //

    override fun onActive() {
        super.onActive()
        contentResolver.registerContentObserver(contentUri, false, observer)
        load()
    }

    override fun onInactive() {
        super.onInactive()
        contentResolver.unregisterContentObserver(observer)
    }

    // ------------------------------------------------------ //

    private fun load() = executor.execute { loadData().also(this::postValue) }

    companion object {
        // an executor with an daemon thread so that it does not prevent shutdown
        private val executor =
            Executors.newSingleThreadExecutor { Thread(it).also { it.isDaemon = true } }
    }

    // ------------------------------------------------------ //

    protected abstract val contentUri: Uri
    protected abstract fun loadData(): T

}

private class AttendeeData(contentResolver: ContentResolver, eventId: Long) :
    AbstractEventData<List<Attendee>>(contentResolver, eventId) {

    override val contentUri: Uri get() = CalendarContract.Attendees.CONTENT_URI

    override fun loadData(): MutableList<Attendee> {
        val attendees: MutableList<Attendee> = mutableListOf()

        CalendarContract.Attendees.query(contentResolver, eventId, PROJECTION)?.apply {
            if (moveToFirst()) {
                var i = 0

                attendees += Attendee(id = getLong(i), name = getStringOrNull(++i),
                    email = getStringOrNull(++i),
                    relationship = getIntOrNull(++i)?.let(Attendee.Relationship::get)
                        ?: Attendee.Relationship.NONE,
                    status = getIntOrNull(++i)?.let(Attendee.Status::get) ?: Attendee.Status.NONE,
                    type = getIntOrNull(++i)?.let(Attendee.Type::get) ?: Attendee.Type.NONE,
                    identity = getStringOrNull(++i)?.let { id ->
                        getStringOrNull(++i)?.let { ns -> id to ns }
                    })

            }
        }?.close()

        return attendees
    }

    companion object {
        private val PROJECTION = arrayOf(
            CalendarContract.Attendees._ID, CalendarContract.Attendees.ATTENDEE_NAME,
            CalendarContract.Attendees.ATTENDEE_EMAIL,
            CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
            CalendarContract.Attendees.ATTENDEE_STATUS, CalendarContract.Attendees.ATTENDEE_TYPE,
            CalendarContract.Attendees.ATTENDEE_IDENTITY,
            CalendarContract.Attendees.ATTENDEE_ID_NAMESPACE
        )
    }

}

private class EventData(contentResolver: ContentResolver, eventId: Long) :
    AbstractEventData<Event>(contentResolver, eventId) {

    override val contentUri: Uri
        get() = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

    override fun loadData(): Event {

        lateinit var event: Event

        contentResolver.query(
            contentUri, PROJECTION, null, null, null
        )?.apply {
            fun Instant.getTemporal(zone: ZoneId, allDay: Boolean): Temporal {
                return if (allDay) toLocalDate() else atZone(zone)
            }

            if (moveToFirst()) {
                var i = 0

                val id = getLong(i)
                val color = getIntOrNull(++i)
                val title = getStringOrNull(++i)
                val description = getStringOrNull(++i)
                val calendarName = getStringOrNull(++i)
                val location = getStringOrNull(++i)
                val calendarTimezone = getZoneIdOrNull(++i) ?: ZoneOffset.UTC
                val allDay = getBooleanOrNull(++i) ?: false
                val startTimezone = getZoneIdOrNull(++i).takeUnless { allDay } ?: calendarTimezone
                val start = getInstantOrNull(++i)!!
                val duration = getStringOrNull(++i)?.runCatching(Duration::parse)?.getOrNull()
                val endTimezone = getZoneIdOrNull(++i).takeUnless { allDay } ?: startTimezone
                val end = (getInstantOrNull(++i) ?: duration?.let {
                    start.plus(it)
                })?.let { if (allDay) it.minusNanos(1) else it }
                val accessLevel =
                    getIntOrNull(++i)?.let(Event.AccessLevel::get) ?: Event.AccessLevel.DEFAULT
                val calendarAccess = getIntOrNull(++i) ?: CalendarContract.Calendars.CAL_ACCESS_NONE
                val availability = getIntOrNull(++i)?.let(Event.Availability::get)
                val status = getIntOrNull(++i)?.let(Event.Status::get)
                val hasAlarm = getBooleanOrNull(++i) ?: false
                val hasAttendeeData = getBooleanOrNull(++i) ?: false
                val rrule = getStringOrNull(++i)
                val rdate = getStringOrNull(++i)
                val exrule = getStringOrNull(++i)
                val exdate = getStringOrNull(++i)
                val lastDate = getInstantOrNull(++i)
                val originalId = getLongOrNull(++i)
                val originalTime = getInstantOrNull(++i)
                val customAppPackage = getStringOrNull(++i)
                val customAppUri = getStringOrNull(++i)?.let(Uri::parse)

                event = Event(
                    id, color, title, description, calendarName, location, calendarTimezone,
                    start.getTemporal(startTimezone, allDay), end?.getTemporal(endTimezone, allDay),
                    allDay, accessLevel, calendarAccess, availability, status, hasAlarm,
                    hasAttendeeData, originalId, originalTime, rrule, rdate, exrule, exdate,
                    lastDate, customAppPackage, customAppUri
                )
            }
        }?.close()

        return event
    }

    companion object {
        private val PROJECTION = arrayOf(
            CalendarContract.Events._ID, CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.TITLE, CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME, CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.CALENDAR_TIME_ZONE, CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_TIMEZONE, CalendarContract.Events.DTSTART,
            CalendarContract.Events.DURATION, CalendarContract.Events.EVENT_END_TIMEZONE,
            CalendarContract.Events.DTEND, CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.CALENDAR_ACCESS_LEVEL, CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.STATUS, CalendarContract.Events.HAS_ALARM,
            CalendarContract.Events.HAS_ATTENDEE_DATA, CalendarContract.Events.RRULE,
            CalendarContract.Events.RDATE, CalendarContract.Events.EXRULE,
            CalendarContract.Events.EXDATE, CalendarContract.Events.LAST_DATE,
            CalendarContract.Events.ORIGINAL_ID, CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.CUSTOM_APP_PACKAGE, CalendarContract.Events.CUSTOM_APP_URI
        )
    }

}

private class ReminderData(contentResolver: ContentResolver, eventId: Long) :
    AbstractEventData<List<Reminder>>(contentResolver, eventId) {

    override val contentUri: Uri get() = CalendarContract.Reminders.CONTENT_URI

    override fun loadData(): MutableList<Reminder> {

        val attendees: MutableList<Reminder> = mutableListOf()

        CalendarContract.Reminders.query(contentResolver, eventId, PROJECTION)?.apply {
            if (moveToFirst()) {
                var i = 0

                attendees += Reminder(id = getLong(i),
                    minutes = getInt(++i).takeIf { it >= 0 } ?: 15,
                    method = getInt(++i).let(Reminder.Method::get) ?: Reminder.Method.DEFAULT)

            }
        }?.close()

        return attendees
    }

    companion object {
        private val PROJECTION = arrayOf(
            CalendarContract.Reminders._ID, CalendarContract.Reminders.MINUTES,
            CalendarContract.Reminders.METHOD
        )
    }

}

private class RouteData(
    private val context: Context, private val eventId: Long
) : MediatorLiveData<List<Route>>() {

    private val routeLiveData: LiveData<List<TripData>> by lazy {
        AsyncRoutePlanner.getJourneyDetails(context, eventId).get()
    }

    init {
        addSource(routeLiveData, this::updateData)
    }

    private fun updateData(data: List<TripData>?) {
        if (data.isNullOrEmpty()) postValue(emptyList())
        else thread {
            val geocoder = Geocoder(context)

            fun Stop.toStop(): Route.Stop = Route.Stop(
                geocoder.getFromLocation(lat, lon, 1).singleOrNull()?.locality ?: name,
                LocalDateTime.parse(date + "T" + time)
            )

            // query all trips that are associated with this event
            val trips = mutableListOf<Route>()

            data.forEach { tripData ->
                tripData.trips?.forEach { trip ->
                    val origin = trip.leglist.legs.firstOrNull()?.origin
                    val destination = trip.leglist.legs.lastOrNull()?.destination

                    if (origin != null && destination != null) trips.add(
                        Route(tripData, trip, origin.toStop(), destination.toStop())
                    )
                }
            }

            postValue(trips.sortedBy { it.origin.time })
        }
    }

}
