package com.amazonaws.secretsmanager.util;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class URLBuilderTest {

    @Test
    public void test_append_parameter_with_one_parameter() {
        URLBuilder builder = new URLBuilder("jdbc:mysql://test-endpoint:1234/dev");
        String url = builder.appendParameter("sslMode", "REQUIRED", true).build();
        assertEquals("jdbc:mysql://test-endpoint:1234/dev?sslMode=REQUIRED", url);
    }

    @Test
    public void test_append_parameter_with_multiple_parameters() {
        URLBuilder builder = new URLBuilder("jdbc:mysql://test-endpoint:1234/dev");
        String url = builder.appendParameter("user", "root", true)
                            .appendParameter("password", "password", false)
                            .appendParameter("sslMode", "REQUIRED", false).build();
        assertEquals("jdbc:mysql://test-endpoint:1234/dev?user=root&password=password&sslMode=REQUIRED", url);
    }

    @Test
    public void test_append_property_with_one_property() {
        URLBuilder builder = new URLBuilder("jdbc:sqlserver://test-endpoint:1234;databaseName=dev;");
        String url = builder.appendProperty("sslProtocol", "TLS", true).build();
        assertEquals("jdbc:sqlserver://test-endpoint:1234;databaseName=dev;sslProtocol=TLS;", url);
    }

    @Test
    public void test_append_property_with_multiple_properties() {
        URLBuilder builder = new URLBuilder("jdbc:sqlserver://test-endpoint:1234;databaseName=dev;");
        String url = builder.appendProperty("sslProtocol", "TLS", true)
                            .appendProperty("trustStore", "truststore.jks", true)
                            .appendProperty("trustStorePassword", "password", true).build();
        assertEquals("jdbc:sqlserver://test-endpoint:1234;databaseName=dev;sslProtocol=TLS;trustStore=truststore.jks;trustStorePassword=password;", url);
    }

    @Test
    public void test_urlbuilder_with_no_modification(){
        URLBuilder builder = new URLBuilder("jdbc:postgresql://test-endpoint:1234/");
        String url = builder.build();
        assertEquals("jdbc:postgresql://test-endpoint:1234/", url);
    }  
}