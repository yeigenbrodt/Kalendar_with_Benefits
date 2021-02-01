package de.dhbw.mannheim.cwb.transit.util;

import androidx.room.TypeConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

import de.dhbw.mannheim.cwb.transit.pojo.Trip;

/**
 * @author Lukas Rothenbach
 * Responsible for the type conversion in order to store an array into the room database
 */
public class DataConverter {

    /**
     * Parses the provided Trip array into a json String
     *
     * @param trips the Trip array that is supposed to be parsed
     * @return the formed json String
     */
    @TypeConverter
    public String fromTripList(Trip[] trips) {
        if (trips == null) {
            return (null);
        }
        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writeValueAsString(trips);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

    /**
     * Parsed the provided json String into a Trip array
     *
     * @param tripString the json String that is supposed to be parsed
     * @return the parsed Trip array
     */
    @TypeConverter
    public Trip[] toTripList(String tripString) {
        if (tripString == null) {
            return (null);
        }
        ObjectMapper mapper = new ObjectMapper();
        Trip[] trips = null;
        try {
            trips = mapper.readValue(tripString, Trip[].class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return trips;
    }

    /**
     * Parses the provided Coordinates array into a json string
     *
     * @param coords the Coordinates array that is supposed to be parsed
     * @return the formed json array
     */
    @TypeConverter
    public String fromCoordinates(double[] coords) {
        if (coords == null) {
            return (null);
        }
        ObjectMapper mapper = new ObjectMapper();
        String json = null;
        try {
            json = mapper.writeValueAsString(coords);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

    /**
     * Parsed the provided json String into a Trip array
     *
     * @param coordsString the json String that is supposed to be parsed
     * @return the parsed Trip array
     */
    @TypeConverter
    public double[] toCoordinates(String coordsString) {
        if (coordsString == null) {
            return (null);
        }
        ObjectMapper mapper = new ObjectMapper();
        double[] coords = null;
        try {
            coords = mapper.readValue(coordsString, double[].class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return coords;
    }

}
