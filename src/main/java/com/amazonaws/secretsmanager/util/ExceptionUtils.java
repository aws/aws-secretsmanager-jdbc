package com.amazonaws.secretsmanager.util;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ExceptionUtils {

    /**
     * Will check the thrown SQLException and all parent exceptions until the errorCode is found,
     *  or the root exception is reached.
     *
     * @param t The SQLException to check
     * @param errorCode The error code to check for.
     * @return
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
    private ExceptionUtils() { }
}
