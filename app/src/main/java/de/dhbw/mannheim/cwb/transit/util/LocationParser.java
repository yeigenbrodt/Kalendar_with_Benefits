package de.dhbw.mannheim.cwb.transit.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * @author Lukas Rothenbach#
 * Responsebile for parsing an address String to an actual address object containing the address details
 * as well as parse coordinates to an valid address.
 */
public class LocationParser {

    /**
     * Tries to parse the provided address string into an address object containing longitude and latitude
     *
     * @param context current context
     * @param address the address that is supposed to be searched for
     * @return the address found or null if no matching address exists
     */
    public static Address getLocationFromAddress(Context context, String address) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(address, 1);
            return (addresses.size() > 0) ? addresses.get(0) : null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tries to parse the provided address string into an address object containing longitude and latitude
     *
     * @param context current context
     * @param address the address that is supposed to be searched for
     * @return the address found or null if no matching address exists
     */
    public static Address queryCoordinatesForAddress(Context context, Address address) {
        if (address.hasLatitude() && address.hasLongitude()) return address;

        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            StringJoiner addressBuilder = new StringJoiner(",");

            appendIfNotNull(addressBuilder, address.getFeatureName());
            appendIfNotNull(addressBuilder, address.getThoroughfare());
            appendIfNotNull(addressBuilder, address.getLocality());
            appendIfNotNull(addressBuilder, address.getPostalCode());
            appendIfNotNull(addressBuilder, address.getSubAdminArea());
            appendIfNotNull(addressBuilder, address.getAdminArea());
            appendIfNotNull(addressBuilder, address.getCountryName());

            List<Address> addresses = geocoder.getFromLocationName(addressBuilder.toString(), 1);
            return (addresses.size() > 0) ? addresses.get(0) : null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Takes a coordinate an tries to parse it into a valid address
     *
     * @param context   current context
     * @param latitude  the latitude that is supposed to be searched for
     * @param longitude the longitude that is supposed to be searched for
     * @return the Address String, a feature Name (e.g. Golden Gate Bridge) or null if no matching address can be found
     */
    public static String getAddressFromCoordinates(Context context, double latitude, double longitude) {
        Geocoder geocoder = new Geocoder(context, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
            if (addresses.size() > 0) {
                Address address = addresses.get(0);
                if (address.getLocality() != null && address.getThoroughfare() != null && address.getSubThoroughfare() != null) {
                    return address.getLocality() + ", " + address.getThoroughfare() + " " + address.getSubThoroughfare();
                } else if (address.getFeatureName() != null) {
                    return address.getFeatureName();
                }
            }
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static void appendIfNotNull(StringJoiner joiner, CharSequence newElement) {
        if (newElement != null) joiner.add(newElement);
    }

}
