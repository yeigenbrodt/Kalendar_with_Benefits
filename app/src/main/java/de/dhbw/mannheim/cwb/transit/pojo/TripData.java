package de.dhbw.mannheim.cwb.transit.pojo;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverters;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.dhbw.mannheim.cwb.transit.util.DataConverter;

/**
 * @author Lukas Rothenbach
 */
@Entity
@JsonIgnoreProperties(ignoreUnknown = true)
public class TripData {

    @PrimaryKey(autoGenerate = true)
    private final long id;

    @ColumnInfo(name = "eventId", index = true)
    private long eventId;
    private String dataSource;

    @TypeConverters(DataConverter.class)
    @JsonProperty("Trip")
    private Trip[] trips;

    @Ignore
    public TripData() {
        this(0);
    }

    public TripData(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public long getEventId() {
        return eventId;
    }

    public void setEventId(long eventId) {
        this.eventId = eventId;
    }

    @JsonProperty("Trip")
    public Trip[] getTrips() {
        return trips;
    }

    @JsonProperty("Trip")
    public void setTrips(Trip[] trips) {
        this.trips = trips;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

}
