/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2025 Revetware LLC.
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
 * Language formality forms.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 1.2.0
 */
public enum Formality implements LanguageForm {
  /**
   * Informal register.
   */
  INFORMAL,
  /**
   * Formal register.
   */
  FORMAL,
  /**
   * Honorific register.
   */
  HONORIFIC;

  @NonNull
  private static final Map<@NonNull String, @NonNull Formality> FORMALITIES_BY_NAME;

  static {
    FORMALITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Formality.values()).collect(Collectors.toMap(formality -> formality.name(), formality -> formality)));
  }

  /**
   * Gets the mapping of formality names to formality values.
   *
   * @return the mapping of formality names to formality values, not null
   */
  @NonNull
  static Map<@NonNull String, @NonNull Formality> getFormalitiesByName() {
    return FORMALITIES_BY_NAME;
  }
}
