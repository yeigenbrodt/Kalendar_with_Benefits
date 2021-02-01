package de.dhbw.mannheim.cwb.transit.util;

import android.content.Context;
import android.location.Address;

import androidx.lifecycle.LiveData;
import androidx.room.Room;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.dhbw.mannheim.cwb.transit.pojo.JourneyDatabase;
import de.dhbw.mannheim.cwb.transit.pojo.Leg;
import de.dhbw.mannheim.cwb.transit.pojo.Trip;
import de.dhbw.mannheim.cwb.transit.pojo.TripData;

/**
 * @author Lukas Rothenbach
 * Async Entry point to the route planning system. It is responsible for the trip database management as well as performing the RMV API requests.
 */
public class AsyncRoutePlanner {

    private static final Object LOCK = new Object();

    private static JourneyDatabase database = null;

    /**
     * Queries the RMV API returns a new TripData object with various route options.
     * In order to save a desired route option "saveTripToDatabase" needs to be called
     *
     * @param context          current context
     * @param origin           start address String
     * @param destination      destination address String
     * @param searchForArrival whether to use the time as arrival time or as departure time
     * @param time             the time in milliseconds at which one should arrive at the destination
     * @return
     */
    public static Future<TripData> queryJourneyDetails(Context context, String origin, String destination, boolean searchForArrival, long time) {
        FutureTask<TripData> task = new FutureTask<>(() -> {
            Address originAddress = LocationParser.getLocationFromAddress(context, origin);
            Address destinationAddress = LocationParser.getLocationFromAddress(context, destination);

            if (originAddress == null && destinationAddress == null)
                throw new IllegalArgumentException("No address could be found for requested addresses \"" + origin + "\" and \"" + destination + "\"");

            if (originAddress == null)
                throw new IllegalArgumentException("No address could be found for requested address \"" + origin + "\"");

            if (destinationAddress == null)
                throw new IllegalArgumentException("No address could be found for requested address \"" + destination + "\"");

            return queryJourneyDetails(context, originAddress, destinationAddress, searchForArrival, time).get();
        });

        new Thread(task).start();
        return task;
    }

    /**
     * Queries the RMV API returns a new TripData object with various route options.
     * In order to save a desired route option "saveTripToDatabase" needs to be called
     *
     * @param context            current context
     * @param originAddress      start address String
     * @param destinationAddress destination address String
     * @param searchForArrival   whether to use the time as arrival time or as departure time
     * @param time               the time in milliseconds at which one should arrive at the destination
     * @return
     */
    public static Future<TripData> queryJourneyDetails(Context context, Address originAddress, Address destinationAddress, boolean searchForArrival, long time) {
        if (originAddress == null || destinationAddress == null)
            throw new NullPointerException("Address must not be null");

        FutureTask<TripData> task = new FutureTask<>(() -> {
            RoutePlanner planner = new RoutePlanner();

            Address origin = LocationParser.queryCoordinatesForAddress(context, originAddress);
            Address destination = LocationParser.queryCoordinatesForAddress(context, destinationAddress);
            if (origin == null)
                throw new IOException("No Coordinates could be found for " + originAddress);
            if (destination == null)
                throw new IOException("No Coordinates could be found for " + destinationAddress);

            TripData tripdata = planner.getTripData(context, origin, destination, searchForArrival, time);
            removeCoordinates(context, tripdata);
            tripdata.setDataSource("RMV");

            return tripdata;
        });

        new Thread(task).start();
        return task;
    }

    /**
     * Queries the room database for an tripData object containing the provided key. If an entry is found it is returned.
     * Otherwise null will be returned
     *
     * @param context current context
     * @param eventId the identifier of the TripData object so that it can be linked to an calendar entry
     * @return
     */
    public static Future<LiveData<List<TripData>>> getJourneyDetails(Context context, long eventId) {
        FutureTask<LiveData<List<TripData>>> task = new FutureTask<>(() -> {
            JourneyDatabase database = getOrInitDatabase(context);
            return database.tripDataDAO().getTripDataByEventId(eventId);
        });

        new Thread(task).start();
        return task;
    }

    /**
     * Takes the chosen route and saves it to the database. It will be associated with the given
     * TripData source and the eventId (It will be modified).
     *
     * @param context current context
     * @param eventId the identifier of the associated event
     * @param source  the TripData Source that should be saved
     * @param trips   the Trip objects that are supposed to be stored in the database
     * @return
     */
    public static FutureTask<?> saveTripToDatabase(Context context, long eventId, TripData source, Trip... trips) {
        FutureTask<?> task = new FutureTask<>(() -> {
            JourneyDatabase database = getOrInitDatabase(context);

            TripData _source = source;
            if (_source == null) _source = new TripData();

            // override old (or not yet set) data with source
            _source.setEventId(eventId);
            _source.setTrips(trips);

            // save the new data
            TripDataDAO dao = database.tripDataDAO();

            if (_source.getId() == 0 || dao.getTripDataById(_source.getId()) == null) {
                dao.insert(_source);
            } else dao.update(_source);
        }, null);

        new Thread(task).start();

        return task;
    }

    /**
     * Takes the chosen route and saves it to the database.
     *
     * @param context current context
     * @param source  the TripData Source that should be removed
     * @return
     */
    public static FutureTask<?> deleteTripDataFromDatabase(Context context, TripData source) {
        FutureTask<?> task = new FutureTask<>(() -> {
            JourneyDatabase database = getOrInitDatabase(context);

            // save the new data
            database.tripDataDAO().delete(source);
        }, null);

        new Thread(task).start();

        return task;
    }

    /**
     * Removes the coordinates from the leg objects of each Stop and tries to find the address represented by the coordinates
     *
     * @param context  current context
     * @param tripData a TripData object containing route details
     */
    private static void removeCoordinates(Context context, TripData tripData) {
        for (Trip trip : tripData.getTrips()) {
            for (Leg leg : trip.getLeglist().getLegs()) {
                if (isCoordinate(leg.getOrigin().getName())) {
                    String[] coordinates = leg.getOrigin().getName().split(",");
                    String name = LocationParser.getAddressFromCoordinates(context, Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]));
                    if (name != null) {
                        leg.getOrigin().setName(name);
                    }
                }
                if (isCoordinate(leg.getDestination().getName())) {
                    String[] coordinates = leg.getDestination().getName().split(",");
                    String name = LocationParser.getAddressFromCoordinates(context, Double.parseDouble(coordinates[0]), Double.parseDouble(coordinates[1]));
                    if (name != null) {
                        leg.getDestination().setName(name);
                    }
                }
            }
        }
    }

    /**
     * Checks whether the provided String represents a coordinate
     *
     * @param name the String that is supposed to be checked
     * @return true if the provided string is a coordinate, false otherwise
     */
    private static boolean isCoordinate(String name) {
        Pattern pattern = Pattern.compile("^(-?\\d+(\\.\\d+)?),\\s*(-?\\d+(\\.\\d+)?)$");
        Matcher matcher = pattern.matcher(name);
        return matcher.matches();
    }

    /**
     * Returns the database field. If necessary a new JourneyDatabase is created with
     * the given Context through Room.databaseBuilder.
     *
     * @param context the context that should be used
     */
    private static JourneyDatabase getOrInitDatabase(Context context) {
        synchronized (LOCK) {
            if (database == null) {
                database = Room.databaseBuilder(context, JourneyDatabase.class, "journeyDatabase").build();
            }

            return database;
        }
    }

}
