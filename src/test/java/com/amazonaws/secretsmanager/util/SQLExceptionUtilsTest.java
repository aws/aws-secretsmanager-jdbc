package com.amazonaws.secretsmanager.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;

import org.junit.Test;

public class SQLExceptionUtilsTest {

    @Test
    public void test_unwrapAndCheckForCode_nullReturnsFalse() {
        assertFalse(SQLExceptionUtils.unwrapAndCheckForCode(null, 1045));
    }

    @Test
    public void test_unwrapAndCheckForCode_wrappedException_returnsTrue() {
        SQLException e = new SQLException("", "", 1045);
        SQLException wrapper = new SQLException("", "", 0, e);

        assertTrue(SQLExceptionUtils.unwrapAndCheckForCode(wrapper, 1045));
    }

    @Test
    public void test_unwrapAndCheckForCode_wrappedException_returnsFalse() {
        SQLException e = new SQLException("", "", 42);
        SQLException wrapper = new SQLException("", "", 0, e);

        assertFalse(SQLExceptionUtils.unwrapAndCheckForCode(wrapper, 1045));
    }

    @Test
    public void test_unwrapAndCheckForCode_loopInWrappedExceptions_returnsFalse() {
        SQLException e1 = new SQLException("", "", 42);
        SQLException e2 = new SQLException("", "", 0, e1);
        e1.initCause(e2);

        assertFalse(SQLExceptionUtils.unwrapAndCheckForCode(e1, 1046));
    }

    @Test
    public void test_unwrapAndCheckForCode_nonSqlException_parentStillGetsFound() {
        SQLException e0 = new SQLException("", "", 1046);
        Exception e1 = new Exception("test", e0);
        SQLException e2 = new SQLException("", "", 42,e1);

        assertTrue(SQLExceptionUtils.unwrapAndCheckForCode(e2, 1046));
    }

    @Test
    public void test_unwrapAndCheckForCode_nonSqlExceptionWithParent_parentGetsFound() {
        Exception e1 = new SQLException("", "", 1046);
        Exception e2 = new Exception(e1);

        assertTrue(SQLExceptionUtils.unwrapAndCheckForCode(e2, 1046));
    }

    @Test
    public void test_unwrapAndCheckForCode_nonSqlException_returnsFalse() {
        Exception exception = new Exception();

        assertFalse(SQLExceptionUtils.unwrapAndCheckForCode(exception, 1046));
    }
}
