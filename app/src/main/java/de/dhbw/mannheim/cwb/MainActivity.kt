package de.dhbw.mannheim.cwb

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.fragment.app.FragmentActivity
import de.dhbw.mannheim.cwb.databinding.MainActivityBinding
import de.dhbw.mannheim.cwb.view.calendar.CalendarActivity
import de.dhbw.mannheim.cwb.view.main.MainFragment
import de.dhbw.mannheim.cwb.view.settings.SettingsActivity
import kotlin.reflect.KClass

class MainActivity : FragmentActivity() {

    private lateinit var binding: MainActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = MainActivityBinding.inflate(layoutInflater)

        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.calendar_button -> {
                    if (getCalendarPermission()) startActivity(CalendarActivity::class) else false
                }
                R.id.settings_button -> startActivity(SettingsActivity::class)

                // unknown menu item
                else -> false
            }
        }

        openCalendar()
        setContentView(binding.root)
    }

    // --------------------------------------------- //

    private fun openCalendar() {
        if (getCalendarPermission() && supportFragmentManager.findFragmentByTag(
                MainFragment::class.qualifiedName
            ) == null
        ) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, MainFragment(), MainFragment::class.qualifiedName).commit()
        }
    }

    private fun getCalendarPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR).let {
            it == PackageManager.PERMISSION_GRANTED
        }.also {
            if (!it) ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.READ_CALENDAR), PERMISSION_CALENDAR_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_CALENDAR_REQUEST_CODE) {
            // no need to check for permission because that will be done in `openCalendar()`
            openCalendar()
        }
    }

    private fun <T : Activity> startActivity(clazz: KClass<T>): Boolean {
        val intent = Intent(this, clazz.java)
        startActivity(intent)
        return true
    }

    companion object {
        private const val PERMISSION_CALENDAR_REQUEST_CODE = 677606650
    }

}