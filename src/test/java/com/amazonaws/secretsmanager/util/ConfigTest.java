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

import java.util.NoSuchElementException;
import java.util.Properties;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for the Config.
 */
public class ConfigTest extends TestClass {

    /*******************************************************************************************************************
     * loadConfigFrom Tests
     *
     * File exists
     * File does not exist (should just load from system properties)
     ******************************************************************************************************************/
    @Test
    public void test_loadConfigFrom_badFile() {
        System.setProperty("test", "asdfasdf");
        assertNotThrows(() -> {
            Config config = Config.loadConfigFrom("asdfasdf");
            assertEquals("asdfasdf", config.getStringPropertyWithDefault("test", null));
        });
    }

    @Test
    public void test_loadConfigFrom_goodFile() {
        assertNotThrows(() -> {
            Config config = Config.loadConfigFrom(Config.CONFIG_FILE_NAME);
            assertEquals("asfd", config.getStringPropertyWithDefault("testProperty", null));
        });
    }

    /*******************************************************************************************************************
     * loadMainConfig Tests
     *
     * Just test happy path; it calls loadConfigFrom
     ******************************************************************************************************************/
    @Test
    public void test_loadMainConfig_goodFile() {
        assertNotThrows(() -> {
            Config config = Config.loadMainConfig();
            assertEquals("asfd", config.getStringPropertyWithDefault("testProperty", null));
        });
    }

    /*******************************************************************************************************************
     * getSubconfig Tests
     *
     * No subproperties
     * Has subproperties and null prefix
     * Has subproperties and nonnull prefix
     ******************************************************************************************************************/
    @Test
    public void test_getSubconfig_noSubproperies() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(null, config.getSubconfig("asdf"));
    }

    @Test
    public void test_getSubconfig_hasSubproperties_nullPrefix() {
        Properties props = new Properties();
        props.setProperty("asdf.hey", "hello");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        Config subconfig = config.getSubconfig("asdf");
        assertFalse(subconfig.equals(null));
        assertEquals("hello", subconfig.getStringPropertyWithDefault("hey", null));
        assertEquals("asdf", getFieldFrom(subconfig, "prefix"));
    }

    @Test
    public void test_getSubconfig_hasSubproperties_nonnullPrefix() {
        Properties props = new Properties();
        props.setProperty("asdf.hey", "hello");
        Config config = (Config) callConstructorWithArguments(Config.class, "top", props);
        Config subconfig = config.getSubconfig("asdf");
        assertFalse(subconfig.equals(null));
        assertEquals("hello", subconfig.getStringPropertyWithDefault("hey", null));
        assertEquals("top.asdf", getFieldFrom(subconfig, "prefix"));
    }

    /*******************************************************************************************************************
     * fullPropertyName Tests
     *
     * null prefix
     * nonnull prefix
     ******************************************************************************************************************/
    @Test
    public void test_fullPropertyName_nullPrefix() {
        Properties props = new Properties();
        props.setProperty("asdf.hey", "hello");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals("asdf.hey", config.fullPropertyName("asdf.hey"));
    }

    @Test
    public void test_fullPropertyName_nonnullPrefix() {
        Properties props = new Properties();
        props.setProperty("asdf.hey", "hello");
        Config config = (Config) callConstructorWithArguments(Config.class, "top", props);
        assertEquals("top.asdf.hey", config.fullPropertyName("asdf.hey"));
    }

    /*******************************************************************************************************************
     * getStringPropertyWithDefault Tests
     *
     * has it
     * doesn't have it
     ******************************************************************************************************************/
    @Test
    public void test_getStringPropertyWithDefault_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "hello");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals("hello", config.getStringPropertyWithDefault("hey", "ho"));
    }

    @Test
    public void test_getStringPropertyWithDefault_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals("ho", config.getStringPropertyWithDefault("hey", "ho"));
    }

    /*******************************************************************************************************************
     * getIntPropertyWithDefault Tests
     *
     * has it
     * doesn't have it
     * NumberFormatException
     ******************************************************************************************************************/
    @Test
    public void test_getIntPropertyWithDefault_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "2");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(2, config.getIntPropertyWithDefault("hey", 3));
    }

    @Test
    public void test_getIntPropertyWithDefault_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(3, config.getIntPropertyWithDefault("hey", 3));
    }

    @Test
    public void test_getIntPropertyWithDefault_propertySetBadly() {
        Properties props = new Properties();
        props.setProperty("hey", "asdf");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(PropertyException.class, () -> config.getIntPropertyWithDefault("hey", 3));
    }

    /*******************************************************************************************************************
     * getLongPropertyWithDefault Tests
     *
     * has it
     * doesn't have it
     * NumberFormatException
     ******************************************************************************************************************/
    @Test
    public void test_getLongPropertyWithDefault_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "2");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(2, config.getLongPropertyWithDefault("hey", 3));
    }

    @Test
    public void test_getLongPropertyWithDefault_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(3, config.getLongPropertyWithDefault("hey", 3));
    }

    @Test
    public void test_getLongPropertyWithDefault_propertySetBadly() {
        Properties props = new Properties();
        props.setProperty("hey", "asdf");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(PropertyException.class, () -> config.getLongPropertyWithDefault("hey", 3));
    }

    /*******************************************************************************************************************
     * getClassPropertyWithDefault Tests
     *
     * has it
     * doesn't have it
     * ClassNotFoundException
     ******************************************************************************************************************/
    @Test
    public void test_getClassPropertyWithDefault_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "com.amazonaws.secretsmanager.util.ConfigTest");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(this.getClass(), config.getClassPropertyWithDefault("hey", Object.class));
    }

    @Test
    public void test_getClassPropertyWithDefault_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(Object.class, config.getClassPropertyWithDefault("hey", Object.class));
    }

    @Test
    public void test_getClassPropertyWithDefault_propertySetBadly() {
        Properties props = new Properties();
        props.setProperty("hey", "comm.amazonaws.secretsmanager.util.ConfigTest");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(PropertyException.class, () -> config.getClassPropertyWithDefault("hey", Object.class));
    }

    /*******************************************************************************************************************
     * getRequiredStringProperty Tests
     *
     * has it
     * doesn't have it
     ******************************************************************************************************************/
    @Test
    public void test_getRequiredStringProperty_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "hello");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals("hello", config.getRequiredStringProperty("hey"));
    }

    @Test
    public void test_getRequiredStringProperty_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(NoSuchElementException.class, () -> config.getRequiredStringProperty("hey"));
    }

    /*******************************************************************************************************************
     * getRequiredIntProperty Tests
     *
     * has it
     * doesn't have it
     * NumberFormatException
     ******************************************************************************************************************/
    @Test
    public void test_getRequiredIntProperty_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "2");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(2, config.getRequiredIntProperty("hey"));
    }

    @Test
    public void test_getRequiredIntProperty_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(NoSuchElementException.class, () -> config.getRequiredIntProperty("hey"));
    }

    @Test
    public void test_getRequiredIntProperty_propertySetBadly() {
        Properties props = new Properties();
        props.setProperty("hey", "asdf");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(PropertyException.class, () -> config.getRequiredIntProperty("hey"));
    }

    /*******************************************************************************************************************
     * getRequiredLongProperty Tests
     *
     * has it
     * doesn't have it
     * NumberFormatException
     ******************************************************************************************************************/
    @Test
    public void test_getRequiredLongProperty_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "2");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(2, config.getRequiredLongProperty("hey"));
    }

    @Test
    public void test_getRequiredLongProperty_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(NoSuchElementException.class, () -> config.getRequiredLongProperty("hey"));
    }

    @Test
    public void test_getRequiredLongProperty_propertySetBadly() {
        Properties props = new Properties();
        props.setProperty("hey", "asdf");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(PropertyException.class, () -> config.getRequiredLongProperty("hey"));
    }

    /*******************************************************************************************************************
     * getRequiredClassProperty Tests
     *
     * has it
     * doesn't have it
     * ClassNotFoundException
     ******************************************************************************************************************/
    @Test
    public void test_getRequiredClassProperty_propertySet() {
        Properties props = new Properties();
        props.setProperty("hey", "com.amazonaws.secretsmanager.util.ConfigTest");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertEquals(this.getClass(), config.getRequiredClassProperty("hey"));
    }

    @Test
    public void test_getRequiredClassProperty_propertyNotSet() {
        Properties props = new Properties();
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(NoSuchElementException.class, () -> config.getRequiredClassProperty("hey"));
    }

    @Test
    public void test_getRequiredClassProperty_propertySetBadly() {
        Properties props = new Properties();
        props.setProperty("hey", "comm.amazonaws.secretsmanager.util.ConfigTest");
        Config config = (Config) callConstructorWithArguments(Config.class, null, props);
        assertThrows(PropertyException.class, () -> config.getRequiredClassProperty("hey"));
    }
}
