package de.dhbw.mannheim.cwb.view.settings

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.transit.util.LocationParser
import de.dhbw.mannheim.cwb.weather.OWM
import java.io.IOException
import java.util.concurrent.FutureTask

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.root_preferences)

        findPreference<EditTextPreference>("location_home")?.apply {
            summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()

            setOnPreferenceChangeListener { preference, newValue ->
                if (preference.isPersistent) {
                    val keyCoordinates = "location_home_coordinates"

                    fun setValue(key: String, value: String?) =
                        sharedPreferences?.edit()?.putString(key, value)?.apply()
                            ?: preferenceDataStore?.putString(
                                key, value
                            )

                    when (newValue) {
                        is String -> {
                            // should be improved
                            // because the executing thread needs to wait for network traffic

                            // The FutureTask is just a work around so that the otherwise thrown
                            // android.os.NetworkOnMainThreadException
                            FutureTask {
                                try {
                                    val address = newValue.takeUnless { it.isBlank() }?.let {
                                        LocationParser.getLocationFromAddress(
                                            preference.context, it
                                        )
                                    }

                                    setValue(keyCoordinates,
                                        address?.let { "${address.latitude},${address.longitude}" })

                                    true
                                } catch (e: IOException) {
                                    false
                                }
                            }.also { Thread(it).start() }.get()
                        }
                        null -> {
                            setValue(keyCoordinates, null)
                            true
                        }
                        else -> false
                    }
                } else false
            }
        }

        findPreference<ListPreference>("weather_unit")?.apply {
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()

            if (value == null) value = OWM.Unit.METRIC.toString()

            // Specific units are not suitable for users because
            //  1. they have no name that is relevant for users
            //  2. they are virtually not in use in every day live
            // Those Units (currently only Unit.STANDARD) are filtered
            val units = OWM.Unit.values().filter { it.isSuitableForUser() }

            entries = units.map { it.getDisplayName(resources) }.toTypedArray()
            entryValues = units.map { it.name }.toTypedArray()
        }

    }

}

