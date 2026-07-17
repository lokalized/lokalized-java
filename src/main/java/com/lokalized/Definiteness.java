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
 * Definiteness forms.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 2.1.0
 */
public enum Definiteness implements LanguageForm {
  /**
   * Definite form, such as "the book" or Arabic nouns with the definite article.
   */
  DEFINITE,
  /**
   * Indefinite form, such as "a book".
   */
  INDEFINITE,
  /**
   * Construct or bound-state form used by languages such as Arabic and Hebrew.
   */
  CONSTRUCT;

  @NonNull
  private static final Map<@NonNull String, @NonNull Definiteness> DEFINITENESS_BY_NAME;

  static {
    DEFINITENESS_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Definiteness.values()).collect(Collectors.toMap(definiteness -> definiteness.name(), definiteness -> definiteness)));
  }

  /**
   * Gets the mapping of definiteness names to definiteness values.
   *
   * @return the mapping of definiteness names to definiteness values, not null
   */
  @NonNull
  static Map<@NonNull String, @NonNull Definiteness> getDefinitenessByName() {
    return DEFINITENESS_BY_NAME;
  }
}
