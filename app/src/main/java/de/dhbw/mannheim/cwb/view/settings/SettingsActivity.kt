package de.dhbw.mannheim.cwb.view.settings

import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import de.dhbw.mannheim.cwb.R
import de.dhbw.mannheim.cwb.databinding.MainActivityBinding

class SettingsActivity : FragmentActivity() {

    private val binding: MainActivityBinding by lazy {
        MainActivityBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.topAppBar.menu.clear()

        binding.topAppBar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
        obtainStyledAttributes(intArrayOf(R.attr.colorOnPrimary)).run {
            binding.topAppBar.navigationIcon?.colorFilter =
                PorterDuffColorFilter(this.getColor(0, Color.WHITE), PorterDuff.Mode.SRC_ATOP)
            recycle()
        }
        binding.topAppBar.setNavigationOnClickListener { onBackPressed() }

        setContentView(binding.root)
        supportFragmentManager.beginTransaction().replace(R.id.content, SettingsFragment()).commit()

    }

}