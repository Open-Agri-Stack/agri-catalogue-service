package com.catalogue.verg.core.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * <p>
 * A utility class to cache and retrieve properties.
 * It loads properties from specified files and provides methods to access them.
 * Also handles environment variable overrides for properties.
 */
public class PropertiesCache {
    // Logger for logging messages
    private final Logger logger = LogManager.getLogger(getClass());

    // Array of file names from which properties are loaded
    private final String[] fileName = {
            "application.properties",
            "customerror.properties"
    };
    // Properties object to store loaded properties
    private final Properties configProp = new Properties();

    /**
     * Private constructor to prevent instantiation from outside
     */
    private PropertiesCache() {
        // Load properties from each file
        for (String file : fileName) {
            InputStream in = this.getClass().getClassLoader().getResourceAsStream(file);
            try {
                configProp.load(in);
            } catch (IOException e) {
            }
        }
    }

    /**
     * Method to get singleton instance of PropertiesCache
     *
     * @return - returns the instance of PropertiesCache
     */
    public static PropertiesCache getInstance() {

        // change the lazy holder implementation to simple singleton implementation ...
        return PropertiesCacheHolder.propertiesCache;
    }

    private static final class PropertiesCacheHolder {
        static final PropertiesCache propertiesCache = new PropertiesCache();
    }

    /**
     * Method to get a property value
     *
     * @param key -key to be fetched.
     * @return - returns the values.
     */
    public String getProperty(String key) {
        String value = System.getenv(key);
        if (StringUtils.isNotBlank(value)) return value;
        return configProp.getProperty(key) != null ? configProp.getProperty(key) : key;
    }

    /**
     * Method to read a property value
     *
     * @param key - key to be read
     * @return - returns the value.
     */
    public String readProperty(String key) {
        String value = System.getenv(key);
        if (StringUtils.isNotBlank(value)) return value;
        return configProp.getProperty(key);
    }
}