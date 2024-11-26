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

import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * A class that holds some helper methods for running tests that test classes should inherit from.
 */
public class TestClass {

    public Object getFieldFrom(Object o, String fieldName, Class<?> clazz) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(o);
    }

    public Object getFieldFrom(Object o, String fieldName) {
        Class clazz = o.getClass();
        boolean isDone = false;
        while (!isDone) {
            try {
                return getFieldFrom(o, fieldName, clazz);
            } catch (NoSuchFieldException e) {
                if (clazz.equals(Object.class)) {
                    throw new RuntimeException(e);
                } else {
                    clazz = clazz.getSuperclass();
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    public void setFieldFrom(Object o, String fieldName, Object value, Class<?> clazz) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(o, value);
    }

    public void setFieldFrom(Object o, String fieldName, Object value) {
        Class clazz = o.getClass();
        boolean isDone = false;
        while (!isDone) {
            try {
                setFieldFrom(o, fieldName, value, clazz);
                isDone = true;
            } catch (NoSuchFieldException e) {
                if (clazz.equals(Object.class)) {
                    isDone = true;
                } else {
                    clazz = clazz.getSuperclass();
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Constructor getConstructorWithNArguments(Class clazz, int n) {
        Constructor[] ctors = clazz.getDeclaredConstructors();
        Constructor ctor = null;
        for (int i = 0; i < ctors.length; i++) {
            ctor = ctors[i];
            if (ctor.getGenericParameterTypes().length == n) {
                break;
            }
        }
        return ctor;
    }

    public Object newInstance(Constructor ctor, Object... initargs) {
        try {
            ctor.setAccessible(true);
            return ctor.newInstance(initargs);
        } catch (RuntimeException e) {
            throw e;
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Object callConstructorWithArguments(Class clazz, Object... initargs) {
        Constructor ctor = getConstructorWithNArguments(clazz, initargs.length);
        return newInstance(ctor, initargs);
    }

    public Object callConstructorWithArguments(int argsOffset, Class clazz, Object... initargs) {
        Constructor ctor = getConstructorWithNArguments(clazz, initargs.length + argsOffset);
        return newInstance(ctor, initargs);
    }

    public GetSecretValueRequest requestWithName(String secretName) {
        return new GetSecretValueRequest().withSecretId(secretName);
    }

    public Object callMethodWithArguments(Object object, String methodName, Object... args) {
        try {
            LinkedList<Method> allMethods = new LinkedList<>();
            Class clazz = object.getClass();
            while (!clazz.equals(Object.class)) {
                Method[] methods = clazz.getDeclaredMethods();
                allMethods.addAll(Arrays.asList(methods));
                clazz = clazz.getSuperclass();
            }
            Method correctMethod = null;
            for (Method method : allMethods) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                    correctMethod = method;
                    break;
                }
            }
            if (correctMethod == null) {
                throw new NoSuchMethodException("No appropriate method.");
            }
            correctMethod.setAccessible(true);
            return correctMethod.invoke(object, args);
        } catch(RuntimeException e) {
            throw e;
        } catch(Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public interface throwingRunnable {
        void run() throws Exception;
    }

    public void assertThrows(Class<? extends Exception> exception, throwingRunnable code) {
        try  {
            code.run();
            throw new RuntimeException("Should have thrown a " + exception.getName() + " but threw nothing.");
        } catch (Exception e) {
            if (!exception.isAssignableFrom(e.getClass())) {
                e.printStackTrace();
                throw new RuntimeException("Should have thrown a " + exception.getName() + " but threw " + e.getClass().getName());
            }
        }
    }

    public void assertThrows(Exception exception, throwingRunnable code) {
        try  {
            code.run();
            throw new RuntimeException("Should have thrown a " + exception.getMessage() + " but threw nothing.");
        } catch (Exception e) {
            if (!exception.equals(e)) {
                e.printStackTrace();
                throw new RuntimeException("Should have thrown a " + exception.getMessage() + " but threw " + e.getClass().getName());
            }
        }
    }

    public void assertThrows(Class<? extends Exception> exception, String message, throwingRunnable code) {
        try  {
            code.run();
            throw new RuntimeException("Should have thrown a " + exception.getName() + " but threw nothing.");
        } catch (Exception e) {
            if (!exception.isAssignableFrom(e.getClass()) && !message.equals(e.getMessage())) {
                e.printStackTrace();
                throw new RuntimeException("Should have thrown a " + exception.getName() + " with message " + message + " but threw " + e.getClass().getName() + " with message " + e.getMessage());
            }
        }
    }

    public void assertNotThrows(throwingRunnable code) {
        try  {
            code.run();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Should not have thrown, but threw " + e.getClass().getName());
        }
    }
}

