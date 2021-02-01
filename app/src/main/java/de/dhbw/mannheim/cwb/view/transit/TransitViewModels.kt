package de.dhbw.mannheim.cwb.view.transit

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.dhbw.mannheim.cwb.transit.pojo.Trip
import de.dhbw.mannheim.cwb.transit.pojo.TripData
import java.time.LocalDateTime

class TransitSearchModel : ViewModel() {

    val date: MutableLiveData<LocalDateTime?> = MutableLiveData()
    val isArrival: MutableLiveData<Boolean> = MutableLiveData()

    val selectedTrips: MutableLiveData<List<Trip>> = MutableLiveData()
    val tripData: MutableLiveData<TripData?> = MutableLiveData()

}

class TransitViewModel : ViewModel() {

    val trip: MutableLiveData<Trip?> = MutableLiveData()

}