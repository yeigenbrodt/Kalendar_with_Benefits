package de.dhbw.mannheim.cwb.transit.util;

import android.content.Context;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import de.dhbw.mannheim.cwb.BuildConfig;

/**
 * @author Lukas Rothenbach
 * Responsible for accessing the application.properties file
 */
public class PropertyReader {

    /**
     * Queries the RMV API key
     * @return the key found
     */
    public static String getProperty() {
        return BuildConfig.RMV_API_KEY;
    }
}
