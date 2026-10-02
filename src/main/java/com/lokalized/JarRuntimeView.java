/*
 * Copyright 2026 Revetware LLC.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND.
 */
package com.lokalized;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

/**
 * Accesses the JVM's multi-release JAR view without linking Android to Runtime.Version.
 * Android has neither of these JarFile methods and sees only base entries. On a JVM,
 * invoke the actual methods so jdk.util.jar.version and enableMultiRelease retain their
 * existing semantics. No reflection is used in parsing or translation.
 */
final class JarRuntimeView {
  private JarRuntimeView() {
  }

  static boolean isMultiRelease(JarFile jarFile) {
    try {
      return (Boolean) JarFile.class.getMethod("isMultiRelease").invoke(jarFile);
    } catch (NoSuchMethodException exception) {
      return false;
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw failure(exception);
    }
  }

  static int majorVersion() {
    try {
      Method runtimeVersion = JarFile.class.getMethod("runtimeVersion");
      Object version = runtimeVersion.invoke(null);
      return (Integer) version.getClass().getMethod("major").invoke(version);
    } catch (NoSuchMethodException exception) {
      return 8;
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw failure(exception);
    }
  }

  private static LocalizedStringLoadingException failure(ReflectiveOperationException exception) {
    Throwable cause = exception instanceof InvocationTargetException
        ? ((InvocationTargetException) exception).getTargetException() : exception;
    return new LocalizedStringLoadingException("Unable to inspect the runtime's multi-release JAR view", cause);
  }
}
