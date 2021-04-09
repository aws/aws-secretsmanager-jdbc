/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance with
 * the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.amazonaws.secretsmanager.util;

import java.io.InputStream;
import java.io.IOException;

import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Properties;

import lombok.EqualsAndHashCode;

/**
 * <p>
 * A class for accessing configuration information from a properties file or from the System properties. Properties
 * defined in the file will override the properties set in the System properties. The properties file should be
 * located somewhere on the class path for the class loader used to load this class.
 * </p>
 *
 * <p>
 * The default file that properties will be fetched from is referred to by <code>Config.CONFIG_FILE_NAME</code>.
 * </p>
 */
@EqualsAndHashCode
public final class Config {

    /**
     * The name of the properties file used for configuration; "secretsmanager.properties".
     */
    public static final String CONFIG_FILE_NAME = "secretsmanager.properties";

    private Properties config;

    private String prefix;

    /**
     * Private constructor to load the properties.
     *
     * @param prefix                                            The prefix of the properties used by the config that
     *                                                          this subconfig was extracted from.
     * @param config                                            The properties that this config should contain.
     */
    private Config(String prefix, Properties config) {
        this.config = config;
        this.prefix = prefix;
    }

    /**
     * Loads the configuration properties from the specified config file. Defaults will be the System properties if the
     * file is not present.
     *
     * @param resourceName                                      The name of the config file to load from.
     *
     * @return Properties                                       The properties that this object should serve.
     */
    private static Properties loadPropertiesFromConfigFile(String resourceName) {
        Properties newConfig = new Properties(System.getProperties());

        try (InputStream configFile = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName)) {
            if (configFile != null) {
                newConfig.load(configFile);
                configFile.close();
            }
        } catch (IOException e) {
            throw new PropertyException("An error occured when loading the property file, " + CONFIG_FILE_NAME, e);
        }
        return newConfig;
    }

    /**
     * Loads a configuration from a specific configuration file. Will use the System properties as defaults.
     *
     * @param resourceName                                      The name of the config file to load from.
     *
     * @return Config                                           A new <code>Config</code> with the properties from the
     *                                                          file and the system properties as defaults.
     */
    public static Config loadConfigFrom(String resourceName) {
        return new Config(null, Config.loadPropertiesFromConfigFile(resourceName));
    }

    /**
     * Loads a configuration from <code>CONFIG_FILE_NAME</code>. Will use the System properties as defaults.
     *
     * @return Config                                           A new <code>Config</code> with the properties from the
     *                                                          <code>CONFIG_FILE_NAME</code> file and the system
     *                                                          properties as defaults.
     */
    public static Config loadMainConfig() {
        return loadConfigFrom(CONFIG_FILE_NAME);
    }

    /**
     * Consumes a full property name and checks if it lies beneath the given subprefix.
     *
     * @param propertyName                                      The full property name to check.
     * @param subprefix                                         The subprefix to check for membership in.
     *
     * @return boolean                                          Whether or not the <code>propertyName</code> falls under
     *                                                          the <code>subprefix</code>.
     */
    private boolean isSubproperty(String propertyName, String subprefix) {
        return propertyName.indexOf(subprefix + ".") == 0;
    }

    /**
     * Get the subproperty from the property by removing the subprefix.
     *
     * @param fullPropertyName                                  The property name to remove the subprefix from.
     * @param subprefix                                         The subprefix to remove.
     *
     * @return String                                           The property name with the subprefix removed from the
     *                                                          beginning.
     */
    private String getSubproperty(String fullPropertyName, String subprefix) {
        return fullPropertyName.substring(subprefix.length() + 1);
    }

    /**
     * Extracts all of the properties for a given subprefix into its own <code>Config</code> object. The property
     * names will be changed to no longer have the subprefix.
     *
     * @param subprefix                                         The subprefix to get all of the properties for.
     *
     * @return Config                                           Configuration properties for the subprefix
     */
    public Config getSubconfig(String subprefix) {
        Enumeration<String> propertyNames = (Enumeration<String>) config.propertyNames();
        Properties subconfig = null;
        while (propertyNames.hasMoreElements()) {
            String name = propertyNames.nextElement();
            if (isSubproperty(name, subprefix)) {
                if (subconfig == null) {
                    subconfig = new Properties();
                }
                String subpropertyName = getSubproperty(name, subprefix);
                subconfig.setProperty(subpropertyName, config.getProperty(name));
            }
        }
        if (subconfig == null) {
            return null;
        } else if (prefix != null) {
            return new Config(prefix + "." + subprefix, subconfig);
        } else {
            return new Config(subprefix, subconfig);
        }
    }

    /**
     * Extends a property name to be the full version written in the configuration file. This full name is not
     * necessarily the name that the property is indexed with in this <code>Config</code> object.
     *
     * @param propertyName                                      The property name to extend.
     *
     * @return String                                           The full property name as written in the configuration
     *                                                          file.
     */
    public String fullPropertyName(String propertyName) {
        if (prefix != null) {
            return prefix + "." + propertyName;
        } else  {
            return propertyName;
        }
    }

    /**
     * Returns a <code>String</code> property or a default value if the property is not set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     * @param defaultValue                                      The default value to use.
     *
     * @return String                                           The <code>String</code> property or a default value if
     *                                                          the property is not set.
     */
    public String getStringPropertyWithDefault(String propertyName, String defaultValue) {
        String propertyValue = config.getProperty(propertyName);
        if (propertyValue == null) {
            return defaultValue;
        } else {
            return propertyValue;
        }
    }

    /**
     * Returns a <code>int</code> property or a default value if the property is not set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     * @param defaultValue                                      The default value to use.
     *
     * @return int                                              The <code>int</code> property or a default value if
     *                                                          the property is not set.
     *
     * @throws PropertyException                                If the property value is not a decimal <code>int</code>.
     */
    public int getIntPropertyWithDefault(String propertyName, int defaultValue) {
        String propertyValue = config.getProperty(propertyName);
        if (propertyValue == null) {
            return defaultValue;
        } else {
            try  {
                return Integer.parseInt(propertyValue);
            } catch (NumberFormatException e) {
                throw new PropertyException(fullPropertyName(propertyName) + " must be of type int. Please check "
                                            + Config.CONFIG_FILE_NAME + " or your system properties for typos.", e);
            }
        }
    }

    /**
     * Returns a <code>long</code> property or a default value if the property is not set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     * @param defaultValue                                      The default value to use.
     *
     * @return long                                             The <code>long</code> property or a default value if
     *                                                          the property is not set.
     *
     * @throws PropertyException                                If the property value is not a decimal
     *                                                          <code>long</code>.
     */
    public long getLongPropertyWithDefault(String propertyName, long defaultValue) {
        String propertyValue = config.getProperty(propertyName);
        if (propertyValue == null) {
            return defaultValue;
        } else {
            try  {
                return Long.parseLong(propertyValue);
            } catch (NumberFormatException e) {
                throw new PropertyException(fullPropertyName(propertyName) + " must be of type long. Please check "
                                            + Config.CONFIG_FILE_NAME + " or your system properties for typos.", e);
            }
        }
    }

    /**
     * Returns a <code>Class</code> property or a default value if the property is not set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     * @param defaultValue                                      The default value to use.
     *
     * @return Class                                            The <code>Class</code> property or a default value if
     *                                                          the property is not set.
     *
     * @throws PropertyException                                If the class name does not exist in this class loader.
     */
    public Class getClassPropertyWithDefault(String propertyName, Class defaultValue) {
        String propertyValue = config.getProperty(propertyName);
        if (propertyValue == null) {
            return defaultValue;
        } else {
            try  {
                return Class.forName(propertyValue);
            } catch (ClassNotFoundException e) {
                throw new PropertyException(fullPropertyName(propertyName) + " must be a valid class name. Please check"
                                           + " " + Config.CONFIG_FILE_NAME + " or your system properties for typos.",
                                           e);
            }
        }
    }

    /**
     * Throws a <code>NoSuchElementException</code> if a value is not set for the given property name.
     *
     * @param propertyName                                      The property to check.
     *
     * @throws NoSuchElementException                           If the property is not set.
     */
    private void throwIfPropertyIsNotSet(String propertyName) {
        if (config.getProperty(propertyName) == null) {
            throw new NoSuchElementException(fullPropertyName(propertyName)
                                             + " property must be specified either in " + Config.CONFIG_FILE_NAME
                                             + " or in the system properties.");
        }
    }

    /**
     * Returns a <code>String</code> property or throws a <code>NoSuchElementException</code> if the property is not
     * set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     *
     * @return String                                           The <code>String</code> property or a default value if
     *                                                          the property is not set.
     *
     * @throws NoSuchElementException                           If the property is not set.
     */
    public String getRequiredStringProperty(String propertyName) {
        throwIfPropertyIsNotSet(propertyName);
        String propertyValue = config.getProperty(propertyName);
        return propertyValue;
    }

    /**
     * Returns a <code>int</code> property or throws a <code>NoSuchElementException</code> if the property is not set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     *
     * @return int                                              The <code>int</code> property or a default value if
     *                                                          the property is not set.
     *
     * @throws PropertyException                                If the property value is not a decimal <code>int</code>.
     * @throws NoSuchElementException                           If the property is not set.
     */
    public int getRequiredIntProperty(String propertyName) {
        throwIfPropertyIsNotSet(propertyName);
        return getIntPropertyWithDefault(propertyName, 0);
    }

    /**
     * Returns a <code>long</code> property or throws a <code>NoSuchElementException</code> if the property is not set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     *
     * @return long                                             The <code>long</code> property or a default value if
     *                                                          the property is not set.
     *
     * @throws PropertyException                                If the property value is not a decimal
     *                                                          <code>long</code>.
     * @throws NoSuchElementException                           If the property is not set.
     */
    public long getRequiredLongProperty(String propertyName) {
        throwIfPropertyIsNotSet(propertyName);
        return getLongPropertyWithDefault(propertyName, 0);
    }

    /**
     * Returns a <code>Class</code> property or throws a <code>NoSuchElementException</code> if the property is not set.
     *
     * @param propertyName                                      The name of the property to retrieve.
     *
     * @return Class                                            The <code>Class</code> property or a default value if
     *                                                          the property is not set.
     *
     * @throws PropertyException                                If the class name does not exist in this class loader.
     * @throws NoSuchElementException                           If the property is not set.
     */
    public Class getRequiredClassProperty(String propertyName) {
        throwIfPropertyIsNotSet(propertyName);
        return getClassPropertyWithDefault(propertyName, null);
    }
}
