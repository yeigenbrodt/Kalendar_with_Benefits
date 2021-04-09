package de.dhbw.mannheim.cwb.view.main

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.location.Address
import android.os.Bundle
import android.provider.CalendarContract
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.databinding.MainFragmentBinding
import de.dhbw.mannheim.cwb.databinding.TabDayOfWeekBinding
import de.dhbw.mannheim.cwb.view.weather.SharedWeatherModel
import de.dhbw.mannheim.cwb.weather.OWM
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

class MainFragment : Fragment(R.layout.main_fragment) {

    private lateinit var binding: MainFragmentBinding
    private val sharedWeatherModel: SharedWeatherModel by activityViewModels()

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            when (key) {
                "location_home_coordinates" -> {
                    sharedWeatherModel.location.value = preferences.getString(key, null)?.let {
                        Address(Locale.ROOT).apply {
                            val (lat, lon) = it.split(",").map { it.toDouble() }
                            latitude = lat
                            longitude = lon
                        }
                    }
                }
                "weather_unit" -> {
                    sharedWeatherModel.unit.value =
                        OWM.Unit.valueOf(preferences.getString("weather_unit", "METRIC")!!)
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = MainFragmentBinding.bind(view)

        val today = LocalDate.now()
        binding.content.adapter = object : FragmentStateAdapter(childFragmentManager, lifecycle) {
            override fun getItemCount() = 5
            override fun createFragment(position: Int) = SingleDayFragment().apply {
                arguments = bundleOf("date" to today.plusDays(position.toLong()).toString())
            }
        }

        TabLayoutMediator(binding.dayTabs, binding.content, true, true) { tab, position ->
            val date = today.plusDays(position.toLong())
            val dayOfWeekBinding = TabDayOfWeekBinding.inflate(layoutInflater)
            dayOfWeekBinding.monthOfYear.text =
                date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            dayOfWeekBinding.dayOfMonth.text = date.dayOfMonth.toString()
            dayOfWeekBinding.dayOfWeek.text =
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            tab.customView = dayOfWeekBinding.root

            binding.content.currentItem = tab.position
        }.attach()

        binding.addIcon.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI))
            } catch (e: ActivityNotFoundException) {
                System.err.println(e::class.qualifiedName + ": " + e.message)
                Snackbar.make(it, R.string.error_no_activity_found_to_edit, Snackbar.LENGTH_SHORT)
                    .show()
            }
        }

        PreferenceManager.getDefaultSharedPreferences(requireContext()).let {
            it.registerOnSharedPreferenceChangeListener(preferenceListener)
            preferenceListener.onSharedPreferenceChanged(it, "location_home_coordinates")
            preferenceListener.onSharedPreferenceChanged(it, "weather_unit")
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()

        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

}
