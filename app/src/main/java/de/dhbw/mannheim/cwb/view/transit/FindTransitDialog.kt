package de.dhbw.mannheim.cwb.view.transit

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.text.format.DateFormat.is24HourFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.databinding.MaterialListEntryViewBinding
import de.dhbw.mannheim.cwb.databinding.TransitFindFragmentBinding
import de.dhbw.mannheim.cwb.transit.pojo.Trip
import de.dhbw.mannheim.cwb.transit.pojo.TripData
import de.dhbw.mannheim.cwb.transit.util.AsyncRoutePlanner
import de.dhbw.mannheim.cwb.util.formatLocalDate
import de.dhbw.mannheim.cwb.util.formatLocalTime
import de.dhbw.mannheim.cwb.util.formatTemporalRange
import de.dhbw.mannheim.cwb.util.toInstant
import de.dhbw.mannheim.cwb.util.toLocalDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.concurrent.thread

class FindTransitDialog : Fragment() {

    private val binding: TransitFindFragmentBinding by lazy {
        TransitFindFragmentBinding.inflate(layoutInflater)
    }

    // ------------------------------------------ //

    private val transitSearchModel: TransitSearchModel by activityViewModels()

    private val date: MutableLiveData<LocalDateTime?> by lazy {
        transitSearchModel.date.apply {
            value = (arguments?.let { arguments ->
                (when {
                    arguments.containsKey(EXTRA_ARRIVAL_DATE_TIME) -> {
                        arguments.getString(EXTRA_ARRIVAL_DATE_TIME)
                    }
                    arguments.containsKey(EXTRA_DEPARTURE_DATE_TIME) -> {
                        arguments.getString(EXTRA_DEPARTURE_DATE_TIME)
                    }

                    else -> null
                })?.let(LocalDateTime::parse)
            } ?: LocalDateTime.now()).truncatedTo(ChronoUnit.MINUTES)
        }
    }
    private val isArrival: MutableLiveData<Boolean> by lazy {
        transitSearchModel.isArrival.apply {
            value = arguments?.containsKey(EXTRA_ARRIVAL_DATE_TIME) == true
        }
    }
    private val eventId: Long? by lazy {
        arguments?.run { if (containsKey(EXTRA_EVENT_ID)) getLong(EXTRA_EVENT_ID) else null }
    }

    private val selectedTrips: MutableLiveData<List<Trip>> get() = transitSearchModel.selectedTrips
    private val tripData: MutableLiveData<TripData?> get() = transitSearchModel.tripData

    // ------------------------------------------ //

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        date.observe(this, this::updateDate)
        isArrival.observe(this, this::updateIsArrival)
        tripData.observe(this, this::updateTripData)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.topAppBar.setNavigationOnClickListener { requireActivity().onBackPressed() }

        binding.topAppBar.menu.findItem(R.id.save).let {
            if (eventId == null) {
                it.isVisible = false
                it.isEnabled = false
            } else {
                it.icon.colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP)
                it.setOnMenuItemClickListener {
                    thread {
                        try {
                            AsyncRoutePlanner.saveTripToDatabase(
                                requireContext(), eventId!!, tripData.value!!,
                                *selectedTrips.value!!.toTypedArray()
                            ).get()

                            selectedTrips.postValue(null)
                        } catch (e: ExecutionException) {
                            (e.cause ?: e).let {
                                System.err.println(it::class.qualifiedName + ": " + it.message)
                            }
                        } catch (e: Exception) {
                            System.err.println(e::class.qualifiedName + ": " + e.message)
                        }
                    }
                    requireActivity().onBackPressed()
                    true
                }
            }
        }

        if (arguments?.containsKey(EXTRA_ORIGIN_ADDRESS) == true) {
            binding.transitOrigin.editText!!.setText(
                arguments?.getString(EXTRA_ORIGIN_ADDRESS), TextView.BufferType.EDITABLE
            )
        }
        if (arguments?.containsKey(EXTRA_DESTINATION_ADDRESS) == true) {
            binding.transitDestination.editText!!.setText(
                arguments?.getString(EXTRA_DESTINATION_ADDRESS), TextView.BufferType.EDITABLE
            )
        }

        binding.transitDate.setOnClickListener {
            date.value?.toLocalDate().let { date ->
                MaterialDatePicker.Builder.datePicker()
                    .setSelection(date?.toInstant()?.toEpochMilli()).setCalendarConstraints(
                        CalendarConstraints.Builder()
                            .setStart(LocalDate.now().toInstant().toEpochMilli()).build()
                    ).build()
            }.let {
                it.addOnPositiveButtonClickListener { _ ->
                    date.postValue(
                        (date.value ?: LocalDateTime.now()).with(
                            Instant.ofEpochMilli(it.selection!!).toLocalDate()
                        )
                    )
                }

                it.show(parentFragmentManager.beginTransaction().addToBackStack(null), null)
            }
        }
        binding.transitTime.setOnClickListener {
            date.value?.toLocalTime().let { time ->
                MaterialTimePicker.Builder().setTimeFormat(
                    if (is24HourFormat(requireContext())) {
                        TimeFormat.CLOCK_24H
                    } else TimeFormat.CLOCK_12H
                ).setHour(time?.hour ?: 12).setMinute(time?.minute ?: 0).build()
            }.let {
                it.addOnPositiveButtonClickListener { _ ->
                    date.postValue(
                        (date.value ?: LocalDateTime.now()).with(LocalTime.of(it.hour, it.minute))
                    )
                }

                it.show(parentFragmentManager.beginTransaction().addToBackStack(null), null)
            }
        }

        binding.transitToggleArrivalDeparture.setOnClickListener {
            isArrival.value = !isArrival.value!!
        }

        binding.transitSearchSubmit.setOnClickListener {
            binding.transitSearchSubmit.isEnabled = false
            binding.transitSearchSubmitLoading!!.visibility = View.VISIBLE
            thread {
                try {
                    selectedTrips.postValue(null)
                    tripData.postValue(
                        AsyncRoutePlanner.queryJourneyDetails(
                            requireContext(), binding.transitOrigin.editText!!.text.toString(),
                            binding.transitDestination.editText!!.text.toString(),
                            isArrival.value!!,
                            date.value!!.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        ).get()
                    )
                } catch (e: ExecutionException) {
                    System.err.println(
                        (e.cause ?: e)::class.qualifiedName + ": " + (e.cause ?: e).message
                    )

                    Snackbar.make(
                        binding.root, R.string.error_not_trips_found, Snackbar.LENGTH_SHORT
                    ).show()
                }

                binding.root.post {
                    binding.transitSearchSubmitLoading!!.visibility = View.GONE
                    binding.transitSearchSubmit.isEnabled = true
                }
            }
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        selectedTrips.value = null
        tripData.value = null
    }

    // ------------------------------------------ //

    private fun updateDate(date: LocalDateTime?) {
        if (date == null) this.date.value = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        else {
            binding.transitDate.text = formatLocalDate(date)
            binding.transitTime.text = formatLocalTime(date)
        }
    }

    private fun updateIsArrival(isArrival: Boolean) {
        binding.transitToggleArrivalDeparture.text = if (isArrival) {
            getString(R.string.trip_time_arrival)
        } else {
            getString(R.string.trip_time_departure)
        }
    }

    private fun updateTripData(data: TripData?) {
        data?.trips?.sortedWith(if (isArrival.value == true) Comparator.comparing { trip: Trip ->
            trip.leglist.legs.last().destination.run { LocalDateTime.parse(date + "T" + time) }
        }.reversed() else Comparator.comparing { trip: Trip ->
            trip.leglist.legs.first().origin.run { LocalDateTime.parse(date + "T" + time) }
        })?.mapNotNull { trip ->
            val start = trip.leglist.legs.firstOrNull()?.origin?.run {
                LocalDateTime.parse(date + "T" + time)
            }
            val end = trip.leglist.legs.lastOrNull()?.destination?.run {
                LocalDateTime.parse(date + "T" + time)
            }

            if (start != null && end != null) {
                val binding = MaterialListEntryViewBinding.inflate(layoutInflater)

                binding.title.text = formatTemporalRange(start, end)
                binding.subtitle.text = trip.leglist.legs.mapNotNull { leg ->
                    leg.name?.trim().takeIf { leg.type == "JNY" && !it.isNullOrBlank() }
                }.joinToString(" - ")

                binding.icon.visibility = View.GONE
                binding.primaryAction.visibility = View.VISIBLE
                binding.primaryAction.isChecked = selectedTrips.value?.contains(trip) == true

                binding.primaryAction.setOnCheckedChangeListener { _, isChecked ->
                    val wasChecked = selectedTrips.value?.contains(trip) ?: false
                    if (wasChecked && !isChecked) {
                        // deselect trip
                        selectedTrips.value = selectedTrips.value?.minus(trip) ?: emptyList()
                    } else if (!wasChecked && isChecked) {
                        // select trip
                        selectedTrips.value = selectedTrips.value?.plus(trip) ?: listOf(trip)
                    } // else state not changed
                }

                binding.secondary.visibility = View.VISIBLE
                binding.secondaryIcon.setImageDrawable(
                    ResourcesCompat.getDrawable(
                        resources, R.drawable.ic_baseline_open_in_new_24, null
                    )
                )
                binding.secondary.setOnClickListener {
                    val transitViewModel: TransitViewModel by activityViewModels()

                    transitViewModel.trip.value = trip

                    ViewTransitTripDialog().show(
                        parentFragmentManager.beginTransaction().addToBackStack(null), null
                    )
                }

                binding.root
            } else null
        }.let {
            val content = binding.content

            content.post {
                content.removeAllViews()
                it?.forEach(content::addView)
            }
        }
    }

    // ------------------------------------------ //

    companion object {

        const val EXTRA_EVENT_ID = "EXTRA_EVENT_ID"

        const val EXTRA_DEPARTURE_DATE_TIME = "EXTRA_ARRIVAL_DATE_TIME"
        const val EXTRA_ARRIVAL_DATE_TIME = "EXTRA_ARRIVAL_DATE_TIME"

        const val EXTRA_ORIGIN_ADDRESS = "EXTRA_ORIGIN_ADDRESS"
        const val EXTRA_DESTINATION_ADDRESS = "EXTRA_DESTINATION_ADDRESS"

    }

}