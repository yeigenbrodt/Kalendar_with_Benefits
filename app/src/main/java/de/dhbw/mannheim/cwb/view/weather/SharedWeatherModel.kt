package de.dhbw.mannheim.cwb.view.weather

import android.location.Address
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.dhbw.mannheim.cwb.BuildConfig
import de.dhbw.mannheim.cwb.weather.OWM
import de.dhbw.mannheim.cwb.weather.model.OneCallWeather
import java.util.*
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class SharedWeatherModel : ViewModel() {

    private val executor = ScheduledThreadPoolExecutor(1)

    // ------------------------------------------------- //

    private val owmApi: OWM = OWM(
        BuildConfig.OWM_API_KEY, when (Locale.getDefault().language) {
            Locale("am").language -> OWM.Language.ARABIC
            Locale("bg").language -> OWM.Language.BULGARIAN
            Locale("ca").language -> OWM.Language.CATALAN
            Locale.SIMPLIFIED_CHINESE.language -> OWM.Language.CHINESE_SIMPLIFIED
            Locale.TRADITIONAL_CHINESE.language -> OWM.Language.CHINESE_TRADITIONAL
            Locale("hr").language -> OWM.Language.CROATIAN
            Locale("cs").language -> OWM.Language.CZECH
            Locale("nl").language -> OWM.Language.DUTCH
            Locale("fi").language -> OWM.Language.FINNISH
            Locale.FRENCH.language -> OWM.Language.FRENCH
            Locale("gl").language -> OWM.Language.GALICIAN
            Locale.GERMAN.language -> OWM.Language.GERMAN
            Locale("el").language -> OWM.Language.GREEK
            Locale("hu").language -> OWM.Language.HUNGARIAN
            Locale.ITALIAN.language -> OWM.Language.ITALIAN
            Locale.JAPANESE.language -> OWM.Language.JAPANESE
            Locale.KOREAN.language -> OWM.Language.KOREAN
            Locale("lv").language -> OWM.Language.LATVIAN
            Locale("lt").language -> OWM.Language.LITHUANIAN
            Locale("mk").language -> OWM.Language.MACEDONIAN
            Locale("fa").language -> OWM.Language.PERSIAN
            Locale("pl").language -> OWM.Language.POLISH
            Locale("pt").language -> OWM.Language.PORTUGUESE
            Locale("ro").language -> OWM.Language.ROMANIAN
            Locale("ru").language -> OWM.Language.RUSSIAN
            Locale("sk").language -> OWM.Language.SLOVAK
            Locale("sl").language -> OWM.Language.SLOVENIAN
            Locale("es").language -> OWM.Language.SPANISH
            Locale("sv").language -> OWM.Language.SWEDISH
            Locale("tr").language -> OWM.Language.TURKISH
            Locale("uk").language -> OWM.Language.UKRAINIAN
            Locale("vi").language -> OWM.Language.VIETNAMESE
            else -> OWM.Language.ENGLISH
        }
    )

    val location: MutableLiveData<Address> = MutableLiveData()
    val unit: MutableLiveData<OWM.Unit> = MutableLiveData(OWM.Unit.METRIC)

    // ------------------------------------------------- //

    val oneCallWeather: LiveData<OneCallWeather> = WeatherData(executor, location, unit) {
        // assert the unit of the api wrapper is up to date
        owmApi.unit = unit.value ?: OWM.Unit.METRIC

        location.value?.let { location ->
            if (location.hasLatitude() && location.hasLongitude()) owmApi.oneCallWeather(
                location.latitude, location.longitude
            ) else null
        }
    }

    // ------------------------------------------------- //

    override fun onCleared() {
        super.onCleared()
        executor.shutdown()
    }

}

private class WeatherData<T>(
    private val executor: ScheduledExecutorService, vararg sources: LiveData<*>,
    private val onUpdate: () -> T?
) : MediatorLiveData<T>() {

    init {
        // sources for e.g. Location updates
        // if a source changes a the weather should be reloaded instantaneously
        sources.forEach {
            addSource(it) {
                // invalidate any current weather
                value = null
                // stop the currently running updating chain
                stopTask()
                // invalidate the last update chain so that
                // no initial delay will be used on restart
                task = null
                // start a new update chain if there are active observers
                if (hasActiveObservers()) startTask()
            }
        }
    }

    private var task: ScheduledFuture<*>? = null

    override fun onActive() {
        super.onActive()
        startTask()
    }

    override fun onInactive() {
        super.onInactive()
        stopTask()
    }

    private fun startTask() {
        // assert the task has stopped so a new task can be scheduled
        stopTask()

        // if the previously scheduled task has updated the weather
        // less than 15 Minutes ago use the remaining time as the initial delay
        // else update the weather immediately. For that
        // we test if there was a task scheduled to be executed in the future (-> delay > 0)
        // and used the remaining delay as the initial delay. Else we use no initial delay.
        val initialDelay = task?.getDelay(TimeUnit.MINUTES)?.coerceAtLeast(0) ?: 0

        if (initialDelay > 0) postValue(value)
        task = executor.scheduleWithFixedDelay({
            try {
                if (hasActiveObservers()) postValue(onUpdate())
            } catch (e: Exception) {
                // don't throw the exception so that the weather update can be tried again later
                e.printStackTrace()
            }
        }, initialDelay, 15, TimeUnit.MINUTES)
    }

    private fun stopTask() {
        task?.run {
            if (!isDone) cancel(false)
        }
    }

}