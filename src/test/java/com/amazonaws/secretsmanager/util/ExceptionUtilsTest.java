package com.amazonaws.secretsmanager.util;

import org.junit.Test;

import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class ExceptionUtilsTest {


    @Test
    public void test_unwrapAndCheckForCode_nullReturnsFalse() {
        assertFalse(ExceptionUtils.unwrapAndCheckForCode(null, 1045));
    }


    @Test
    public void test_unwrapAndCheckForCode_wrappedException_returnsTrue() {
        SQLException e = new SQLException("", "", 1045);
        SQLException wrapper = new SQLException("", "", 0, e);

        assertTrue(ExceptionUtils.unwrapAndCheckForCode(wrapper, 1045));
    }

    @Test
    public void test_unwrapAndCheckForCode_wrappedException_returnsFalse() {
        SQLException e = new SQLException("", "", 42);
        SQLException wrapper = new SQLException("", "", 0, e);

        assertFalse(ExceptionUtils.unwrapAndCheckForCode(wrapper, 1045));
    }


    @Test
    public void test_unwrapAndCheckForCode_loopInWrappedExceptions_returnsFalse() {
        SQLException e1 = new SQLException("", "", 42);
        SQLException e2 = new SQLException("", "", 0, e1);
        e1.initCause(e2);

        assertFalse(ExceptionUtils.unwrapAndCheckForCode(e1, 1046));
    }
}
