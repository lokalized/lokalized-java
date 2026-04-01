/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2026 Revetware LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lokalized;

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generic classifier categories for languages that require measure words or counters.
 * <p>
 * This enum is intentionally non-exhaustive. It captures common cross-language semantic buckets used by classifier
 * systems while remaining small enough to be practical in application code.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public enum Classifier implements LanguageForm {
  /**
   * General-purpose classifier.
   */
  GENERAL,
  /**
   * People or human referents.
   */
  PERSON,
  /**
   * Animals or living creatures.
   */
  ANIMAL,
  /**
   * Long, thin, or cylindrical objects.
   */
  LONG_THIN,
  /**
   * Flat, thin, or sheet-like objects.
   */
  FLAT,
  /**
   * Bound volumes such as books or magazines.
   */
  BOUND,
  /**
   * Machines, devices, or large pieces of equipment.
   */
  MACHINE,
  /**
   * Vehicles.
   */
  VEHICLE;

  @NonNull
  private static final Map<@NonNull String, @NonNull Classifier> CLASSIFIERS_BY_NAME;

  static {
    CLASSIFIERS_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Classifier.values()).collect(Collectors.toMap(classifier -> classifier.name(), classifier -> classifier)));
  }

  /**
   * Gets the mapping of classifier names to classifier values.
   *
   * @return the mapping of classifier names to classifier values, not null
   */
  @NonNull
  static Map<@NonNull String, @NonNull Classifier> getClassifiersByName() {
    return CLASSIFIERS_BY_NAME;
  }
}
