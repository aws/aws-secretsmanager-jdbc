package com.amazonaws.secretsmanager.sql;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class EmptyDriver implements Driver {
    public static EmptyDriver instance;

    static {
        instance = new EmptyDriver();
        try {
            DriverManager.registerDriver(instance);
        } catch (SQLException e) {
            throw new RuntimeException("Driver could not be registered.", e);
        }
    }

    @Override
    public boolean acceptsURL(String arg0) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'acceptsURL'");
    }

    @Override
    public Connection connect(String arg0, Properties arg1) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'connect'");
    }

    @Override
    public int getMajorVersion() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMajorVersion'");
    }

    @Override
    public int getMinorVersion() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMinorVersion'");
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getParentLogger'");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String arg0, Properties arg1) throws SQLException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPropertyInfo'");
    }

    @Override
    public boolean jdbcCompliant() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'jdbcCompliant'");
    }
}
