package de.dhbw.mannheim.cwb.transit.util;

import android.content.Context;
import android.location.Address;
import android.text.format.DateFormat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutionException;

import de.dhbw.mannheim.cwb.transit.pojo.TripData;

/**
 * @author Lukas Rothenbach
 * Responsible for the RMV API query
 */
public class RoutePlanner {

    /**
     * Queries the RMV APi for suitable routes for the transferred start address, destination address and arrival time
     *
     * @param context          current context
     * @param origin           start address String
     * @param destination      destination address String
     * @param searchForArrival whether to use the time as arrival time or as departure time
     * @param time             the time in milliseconds at which one should arrive at the destination
     * @return the found TripData object containing multiple route options
     */
    public TripData getTripData(Context context, Address origin, Address destination, boolean searchForArrival, long time) {
        String apiKey = PropertyReader.getProperty();

        if (origin == null || destination == null) {
            throw new IllegalArgumentException();
        } else {
            String url = buildUrl(origin.getLatitude(), origin.getLongitude(), destination.getLatitude(), destination.getLongitude(), searchForArrival, time, apiKey);
            OkHttpClient client = new OkHttpClient();
            TripData tripdata = doGetRequest(url, client);
            if (tripdata == null || tripdata.getTrips() == null) { //No trips found
                throw new IllegalArgumentException();
            } else {
                return tripdata;
            }
        }
    }

    /**
     * Generates the URL with which the RMV API can be queried
     *
     * @param originCoordLat   Latitude  of the start address
     * @param originCoordLong  Longitude of the start address
     * @param destCoordLat     Latitude  of the destination address
     * @param destCoordLong    Latitude  of the destination address
     * @param searchForArrival whether to use the time as arrival time or as departure time
     * @param time             the time in milliseconds at which one should arrive at the destination
     * @param key              the RMV API key
     * @return the formed URL String
     */
    private String buildUrl(double originCoordLat, double originCoordLong, double destCoordLat, double destCoordLong, boolean searchForArrival, long time, String key) {
        String url = "https://www.rmv.de/hapi/trip?";
        url += "originCoordLat=" + originCoordLat + "&";
        url += "originCoordLong=" + originCoordLong + "&";
        url += "destCoordLat=" + destCoordLat + "&";
        url += "destCoordLong=" + destCoordLong + "&";
        url += "date=" + DateFormat.format("yyyy-MM-dd", new Date(time)) + "&";
        url += "time=" + DateFormat.format("HH:mm", new Date(time)) + "&";
        url += "searchForArrival=" + (searchForArrival ? 1: 0) + "&";
        url += "accessId=" + key + "&";
        url += "originCar=0&destCar=0&originBike=0&destBike=0&originTaxi=0&destTaxi=0&originPark=0&destPark=0&format=json";
        return url;
    }

    /**
     * Responsible for performing the GET-Request to the RMV API
     *
     * @param url    the URL that is supposed to be queried
     * @param client the okHttp Client that is supposed to perform the request
     * @return
     */
    private TripData doGetRequest(String url, OkHttpClient client) {
        Request request = new Request.Builder().url(url).build();
        WebRequest webrequest = new WebRequest();
        client.newCall(request).enqueue(webrequest);
        try {
            Response response = webrequest.get();
            return parseResponse(response);
        } catch (ExecutionException | InterruptedException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets the JSON data from the response body of the okHttp request and tries to parse it into a TripData object
     *
     * @param response okkHttp RepsponseObject containing the response body of the RMV API call
     * @return the parsed Trip Data Object
     * @throws IOException if it is not possible to parse the response
     */
    private TripData parseResponse(Response response) throws IOException {
        String jsonString = response.body().string();
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonString, TripData.class);
    }
}
