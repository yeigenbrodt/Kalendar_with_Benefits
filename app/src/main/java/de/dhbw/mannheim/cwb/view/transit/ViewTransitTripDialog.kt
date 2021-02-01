package de.dhbw.mannheim.cwb.view.transit

import android.location.Geocoder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.databinding.MaterialListEntryViewBinding
import de.dhbw.mannheim.cwb.databinding.TransitTripLegStopViewBinding
import de.dhbw.mannheim.cwb.databinding.TransitTripLegViewBinding
import de.dhbw.mannheim.cwb.databinding.TransitTripViewFragmentBinding
import de.dhbw.mannheim.cwb.transit.pojo.Stop
import de.dhbw.mannheim.cwb.transit.pojo.Trip
import de.dhbw.mannheim.cwb.util.formatLocalDate
import de.dhbw.mannheim.cwb.util.formatLocalTime
import java.time.LocalDate
import java.time.LocalTime
import kotlin.concurrent.thread

class ViewTransitTripDialog : DialogFragment() {

    private val viewModel: TransitViewModel by activityViewModels()

    private val binding: TransitTripViewFragmentBinding by lazy {
        TransitTripViewFragmentBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.trip.observe(this, this::updateTrip)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ) = binding.root

    override fun onResume() {
        super.onResume()

        // maximize the dialog
        dialog?.window?.attributes?.also {
            it.width = WindowManager.LayoutParams.MATCH_PARENT
            it.height = WindowManager.LayoutParams.WRAP_CONTENT
        }?.let { dialog?.window?.attributes = it }
    }

    // ----------------------------------- //

    private fun updateTrip(trip: Trip?) {
        if (trip == null) requireActivity().onBackPressed()
        else {
            binding.content.removeAllViews()

            thread {
                val geocoder = Geocoder(requireContext())
                fun TransitTripLegStopViewBinding.apply(stop: Stop) {
                    stopTime.text = formatLocalTime(LocalTime.parse(stop.time))

                    stopName.text = when (stop.type) {
                        "ST", "POI" -> null
                        else -> geocoder.getFromLocation(stop.lat, stop.lon, 1).singleOrNull()
                            ?.let {
                                listOfNotNull(
                                    it.featureName, it.locality, it.countryName
                                ).joinToString(", ")
                            }
                    } ?: stop.name

                    if (stop.track != null) stop.track.let { track ->
                        stopInfo.text = getString(R.string.trip_stop_info_track, track)
                    } else stopInfo.visibility = View.GONE
                }

                val list = mutableListOf<View>()
                var date: LocalDate? = null
                trip.leglist.legs.forEach {
                    LocalDate.parse(it.origin.date).let {
                        if (date == null || date!! > it) {
                            date = it

                            val binding = MaterialListEntryViewBinding.inflate(
                                layoutInflater, binding.content, false
                            )
                            binding.title.text = formatLocalDate(it)
                            binding.subtitle.visibility = View.GONE
                            binding.primary.visibility = View.GONE

                            list.add(binding.root)
                        }
                    }

                    val binding =
                        TransitTripLegViewBinding.inflate(layoutInflater, binding.content, false)

                    binding.transitOrigin.apply(it.origin)

                    val tripLegData = binding.tripLegData

                    when (it.type) {
                        "JNY" -> R.drawable.ic_baseline_train_24
                        "WALK" -> R.drawable.ic_baseline_directions_walk_24
                        "BIKE" -> R.drawable.ic_baseline_directions_bike_24
                        "KISS" -> R.drawable.ic_baseline_directions_car_24
                        "TAXI", "TETA" -> R.drawable.ic_baseline_local_taxi_24
                        else -> null
                    }?.let {
                        tripLegData.icon.setImageDrawable(
                            ResourcesCompat.getDrawable(resources, it, null)
                        )
                        tripLegData.icon.visibility = View.VISIBLE
                    }

                    tripLegData.title.visibility = View.GONE
                    tripLegData.subtitle.text = it.name

                    binding.transitDestination.apply(it.destination)

                    list.add(binding.root)
                }

                binding.content.post { list.forEach(binding.content::addView) }
            }
        }

    }

}