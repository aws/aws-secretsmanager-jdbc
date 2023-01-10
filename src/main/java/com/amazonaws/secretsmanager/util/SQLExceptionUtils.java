package com.amazonaws.secretsmanager.util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SQLExceptionUtils {

    /**
     * Checks the thrown exception and all parent exceptions and returns true if
     *   a SQLException with a matching error code is found.
     *
     * @param t The SQLException to check
     * @param errorCode The error code to check for.
     * @return True if the exception or any parent exception is a SQL Exception
     *      and getErrorCode matches the error code.  Otherwise, false.
     */
    public static boolean unwrapAndCheckForCode(Throwable t, int errorCode) {
        final List<Throwable> list = new ArrayList<>();
        while (t != null && list.contains(t) == false) {
            list.add(t);
            if ( t instanceof SQLException && ((SQLException)t).getErrorCode() == errorCode ) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }


    /**
     * Hide constructor for static class
     */
    private SQLExceptionUtils() { }
}
