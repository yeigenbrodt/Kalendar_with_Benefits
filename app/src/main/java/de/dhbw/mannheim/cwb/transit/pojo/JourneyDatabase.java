package de.dhbw.mannheim.cwb.transit.pojo;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import de.dhbw.mannheim.cwb.transit.util.TripDataDAO;

/**
 * @author Lukas Rothenbach
 */
@Database(entities = {TripData.class}, version = 1, exportSchema = false)
public abstract class JourneyDatabase extends RoomDatabase {
    public abstract TripDataDAO tripDataDAO();
}
