package de.dhbw.mannheim.cwb.view.calendar

import android.content.ActivityNotFoundException
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.ShapeDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.snackbar.Snackbar
import com.kizitonwose.calendarview.model.CalendarDay
import com.kizitonwose.calendarview.model.CalendarMonth
import com.kizitonwose.calendarview.model.DayOwner
import com.kizitonwose.calendarview.ui.DayBinder
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder
import com.kizitonwose.calendarview.ui.ViewContainer
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.databinding.CalendarActivityBinding
import de.dhbw.mannheim.cwb.databinding.CalendarCvDayViewBinding
import de.dhbw.mannheim.cwb.databinding.CalendarCvMonthHeaderBinding
import de.dhbw.mannheim.cwb.databinding.MaterialCardViewBinding
import de.dhbw.mannheim.cwb.util.formatTemporal
import de.dhbw.mannheim.cwb.util.formatTemporalRange
import de.dhbw.mannheim.cwb.util.toLocalDate
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.format.SignStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoField
import java.time.temporal.JulianFields
import java.time.temporal.Temporal
import java.time.temporal.WeekFields
import java.util.*
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

class CalendarActivity : FragmentActivity() {

    private val binding: CalendarActivityBinding by lazy {
        CalendarActivityBinding.inflate(layoutInflater)
    }

    private val selectedDate = MutableLiveData(LocalDate.now())
    private val selectedMonth = MutableLiveData(YearMonth.now())

    private val eventDays: LiveData<Set<LocalDate>> by lazy {
        EventDaysData(contentResolver, selectedMonth)
    }
    private val events: LiveData<List<EventInstance>> by lazy {
        InstanceData(contentResolver, selectedDate)
    }

    // ------------------------------------------ //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        intent.run {
            when (action) {
                Intent.ACTION_VIEW -> data?.let {
                    if (it.scheme == "content" && it.host == CalendarContract.AUTHORITY && it.path?.matches(
                            "/time/\\d+".toRegex()
                        ) == true
                    ) {
                        val selectedInstant = Instant.ofEpochMilli(ContentUris.parseId(it))
                        selectedInstant.toLocalDate().let { date ->
                            selectedDate.value = date
                            selectedMonth.value = date.query(YearMonth::from)
                        }
                    }
                }

                // other possible intents can be added here

                else -> Unit // do nothing special
            }
        }

        val thisMonth = selectedMonth.value!!
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val formatYearMonth =
            DateTimeFormatterBuilder().appendText(ChronoField.MONTH_OF_YEAR, TextStyle.FULL)
                .appendLiteral(' ').appendValue(ChronoField.YEAR, 4, 4, SignStyle.NORMAL)
                .toFormatter()::format

        selectedMonth.observe(this) { binding.topAppBar.title = formatYearMonth(it) }

        binding.eventList.layoutManager = LinearLayoutManager(this)
        binding.topAppBar.title = formatYearMonth(thisMonth)

        binding.calendarView.run {
            val period = Period.ofYears(1)

            dayBinder = MyDayBinder(selectedDate, eventDays, theme)
            monthHeaderBinder = MonthHeaderBinder(layoutInflater, firstDayOfWeek)

            monthScrollListener = { month ->
                binding.calendarView.updateMonthRange(
                    month.yearMonth - period, month.yearMonth + period
                )

                selectedMonth.postValue(month.yearMonth)
            }

            val oldDate = AtomicReference<LocalDate>(selectedDate.value)
            selectedDate.observe(this@CalendarActivity) {
                if (it != oldDate.get()) {
                    fun notifyChanged(date: LocalDate) = DayOwner.values().forEach { owner ->
                        notifyDateChanged(date, owner)
                    }
                    notifyChanged(it)
                    notifyChanged(oldDate.get())
                    oldDate.set(it)
                }
            }
            eventDays.observe(this@CalendarActivity) { notifyCalendarChanged() }

            setup(
                thisMonth - period, thisMonth + period, firstDayOfWeek
            )
            scrollToDate(selectedDate.value!!)
        }

        val adapter = EventInstancesAdapter(this, layoutInflater)
        events.observe(this) { list -> adapter.submitList(list) }
        binding.eventList.adapter = adapter

        obtainStyledAttributes(intArrayOf(R.attr.colorOnPrimary)).run {
            binding.topAppBar.navigationIcon?.colorFilter =
                PorterDuffColorFilter(this.getColor(0, Color.WHITE), PorterDuff.Mode.SRC_ATOP)
            recycle()
        }
        binding.topAppBar.setNavigationOnClickListener { onBackPressed() }

        binding.addIcon.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI))
            } catch (e: ActivityNotFoundException) {
                System.err.println(e::class.qualifiedName + ": " + e.message)
                Snackbar.make(
                    it, R.string.error_no_activity_found_to_create, Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        setContentView(binding.root)
    }

}

// --------------------------------------------- //

private data class EventInstance(
    val id: Long, val eventId: Long, val title: String?, val allDay: Boolean, val begin: Temporal,
    val end: Temporal?, val location: String?, val color: Int?
)

private class InstanceData(
    private val contentResolver: ContentResolver, private val date: LiveData<LocalDate>
) : MediatorLiveData<List<EventInstance>>() {

    init {
        addSource(date, this::loadEvents)
    }

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

    private fun loadEvents(date: LocalDate = this.date.value!!) = FutureTask<Unit> {

        fun Cursor.getZoneIdOrNull(index: Int): ZoneId? {
            return getStringOrNull(index)?.let { ZoneId.of(it) }
        }

        fun Cursor.getInstantOrNull(index: Int): Instant? {
            return getLongOrNull(index)?.let { Instant.ofEpochMilli(it) }
        }

        fun Instant.getTemporal(zone: ZoneId, allDay: Boolean): Temporal {
            return atZone(zone).let { if (allDay) it.toLocalDate() else it }
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

private class EventDaysData(
    private val contentResolver: ContentResolver, private val month: LiveData<YearMonth>
) : MediatorLiveData<Set<LocalDate>>() {

    init {
        addSource(month, this::loadEvents)
    }

    private val monthRange = Period.ofMonths(2)

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

    private fun loadEvents(month: YearMonth = this.month.value!!) = FutureTask<Unit> {
        contentResolver.query(
            CalendarContract.Instances.CONTENT_BY_DAY_URI.buildUpon().appendPath(
                (month - monthRange).atDay(1).getLong(JulianFields.JULIAN_DAY).toString()
            ).appendPath(
                (month + monthRange).atEndOfMonth().getLong(JulianFields.JULIAN_DAY).toString()
            ).build(), arrayOf(
                CalendarContract.Instances.START_DAY, CalendarContract.Instances.END_DAY
            ), null, null, null
        )?.apply {
            val dates = mutableSetOf<LocalDate>()
            val today = LocalDate.now()

            while (moveToNext()) {
                val start = JulianFields.JULIAN_DAY.adjustInto(today, getLong(0))
                val end = JulianFields.JULIAN_DAY.adjustInto(today, getLong(1))

                var date = start
                while (date <= end) {
                    dates += date
                    date = date.plusDays(1)
                }
            }

            postValue(dates)
        }?.close()
    }.also { Thread(it).start() }
}

// --------------------------------------------- //

private class EventInstancesAdapter(
    private val context: Context, private val layoutInflater: LayoutInflater
) : ListAdapter<EventInstance, EventInstancesViewHolder>(object :
    DiffUtil.ItemCallback<EventInstance>() {
    override fun areItemsTheSame(
        oldItem: EventInstance, newItem: EventInstance
    ) = oldItem.id == newItem.id

    override fun areContentsTheSame(
        oldItem: EventInstance, newItem: EventInstance
    ) = oldItem == newItem

}) {

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = EventInstancesViewHolder(
        MaterialCardViewBinding.inflate(layoutInflater, parent, false)
    )

    override fun onBindViewHolder(holder: EventInstancesViewHolder, position: Int) {
        val event = getItem(position)

        val title = event.title
        if (title == null) holder.binding.title.visibility = View.GONE
        else holder.binding.title.visibility = View.VISIBLE
        holder.binding.title.text = title

        holder.binding.secondaryText.visibility = View.VISIBLE
        holder.binding.secondaryText.text = listOfNotNull(
            if (event.end != null) formatTemporalRange(event.begin, event.end) else formatTemporal(
                event.begin
            ), event.location?.takeIf { it.isNotBlank() }).joinToString(
            separator = "\n"
        )

        holder.binding.supportingText.visibility = View.GONE

        event.color?.let { ColorDrawable(it) }.let { icon ->
            holder.binding.icon.setImageDrawable(icon)
            holder.binding.icon.visibility = if (icon == null) View.GONE else View.VISIBLE
        }

        holder.binding.root.setOnClickListener {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW, ContentUris.withAppendedId(
                        CalendarContract.Events.CONTENT_URI, event.eventId
                    ), context, EventViewActivity::class.java
                )
            )
        }
    }

    override fun getItemId(position: Int) = getItem(position).id

}

private class EventInstancesViewHolder(val binding: MaterialCardViewBinding) :
    RecyclerView.ViewHolder(binding.root)

// --------------------------------------------- //

private class DayViewContainer(view: View) : ViewContainer(view) {
    val binding = CalendarCvDayViewBinding.bind(view)
}

private class MyDayBinder(
    private val selectedDay: MutableLiveData<LocalDate>,
    private val eventDays: LiveData<Set<LocalDate>>, theme: Resources.Theme
) : DayBinder<DayViewContainer> {

    @ColorInt private val colorSelectedActive: Int
    @ColorInt private val backgroundSelectedActive: Int
    @ColorInt private val colorSelectedInactive: Int
    @ColorInt private val backgroundSelectedInactive: Int
    @ColorInt private val colorUnselectedActive: Int
    @ColorInt private val colorUnselectedInactive: Int

    init {
        theme.obtainStyledAttributes(
            intArrayOf(
                R.attr.colorOnPrimary, R.attr.colorPrimary, R.attr.colorOnSecondary,
                R.attr.colorSecondary,
            )
        ).run {
            var i = -1

            colorSelectedActive = getColor(++i, Color.WHITE)
            backgroundSelectedActive = getColor(++i, Color.BLACK)
            colorSelectedInactive = getColor(++i, Color.BLACK)
            backgroundSelectedInactive = getColor(++i, Color.LTGRAY)

            colorUnselectedActive = Color.BLACK
            colorUnselectedInactive = Color.GRAY
        }
    }

    override fun create(view: View) = DayViewContainer(view)

    override fun bind(container: DayViewContainer, day: CalendarDay) {
        val active = day.owner == DayOwner.THIS_MONTH
        val selected = day.date == selectedDay.value

        container.binding.content.apply {
            text = day.date.dayOfMonth.toString()

            setTextColor(
                when {
                    selected and active -> colorSelectedActive
                    selected and !active -> colorSelectedInactive
                    active -> colorUnselectedActive
                    else -> colorUnselectedInactive
                }
            )

            background = when {
                selected -> ShapeDrawable().also {
                    it.shape = android.graphics.drawable.shapes.OvalShape()
                    it.shape.resize(1f, 1f)

                    it.colorFilter = PorterDuffColorFilter(
                        if (active) backgroundSelectedActive else backgroundSelectedInactive,
                        PorterDuff.Mode.SRC
                    )
                }
                eventDays.value?.contains(day.date) == true -> BadgeDrawable.create(context).also {
                    it.backgroundColor =
                        if (active) colorUnselectedActive else colorUnselectedInactive
                    it.badgeGravity = BadgeDrawable.BOTTOM_END

                    it.updateBadgeCoordinates(this, null)

                    it.horizontalOffset = this.width / 2 // min(it.intrinsicWidth, it.minimumWidth)
                    it.verticalOffset = min(it.intrinsicHeight, it.minimumHeight) / 2
                }
                else -> null
            }

            setOnClickListener { selectedDay.postValue(day.date) }

        }
    }

}

private class MonthHeaderContainer(view: View) : ViewContainer(view) {
    val binding = CalendarCvMonthHeaderBinding.bind(view)
}

private class MonthHeaderBinder(
    private val layoutInflater: LayoutInflater, firstDayOfWeek: DayOfWeek
) : MonthHeaderFooterBinder<MonthHeaderContainer> {

    private val daysOfWeek: List<DayOfWeek> by lazy {
        val list = mutableListOf<DayOfWeek>()
        var pointer = firstDayOfWeek

        while (pointer !in list) {
            list += pointer
            pointer += 1
        }

        list
    }

    override fun create(view: View): MonthHeaderContainer {
        return MonthHeaderContainer(view).apply {
            binding.root.run {
                daysOfWeek.forEach {
                    val dayOfWeek = layoutInflater.inflate(
                        R.layout.calendar_cv_week_header, this, false
                    ) as TextView
                    dayOfWeek.text = it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    addView(dayOfWeek)
                }
            }
        }
    }

    override fun bind(container: MonthHeaderContainer, month: CalendarMonth) = Unit

}