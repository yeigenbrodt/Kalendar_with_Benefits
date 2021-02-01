package de.dhbw.mannheim.cwb.transit.util;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.Collection;
import java.util.List;

import de.dhbw.mannheim.cwb.transit.pojo.TripData;

/**
 * @author Lukas Rothenbach
 * Interface between the database and the program code with which it is possible to make queries to the database.
 */
@Dao
public interface TripDataDAO {
    @Query("SELECT * FROM tripdata t where t.eventId = :eventId")
    LiveData<List<TripData>> getTripDataByEventId(long eventId);

    @Query("SELECT * FROM tripdata t where t.id = :id")
    LiveData<TripData> getTripDataById(long id);

    @Insert
    void insert(TripData tripData);

    @Update
    void update(TripData tripData);

    @Query("DELETE FROM tripdata where tripdata.eventId = :eventId")
    void deleteByEventId(long eventId);

    @Delete
    void delete(TripData data);

}
