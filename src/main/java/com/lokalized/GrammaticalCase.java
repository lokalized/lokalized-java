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
 * Grammatical case forms.
 * <p>
 * This enum intentionally models a high-coverage set of cases that appear across many language families.
 * Languages with more specialized case inventories can map to the closest matching value.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 2.1.0
 */
public enum GrammaticalCase implements LanguageForm {
  /**
   * Citation or subject form.
   */
  NOMINATIVE,
  /**
   * Direct object form.
   */
  ACCUSATIVE,
  /**
   * Possessive, source, or partitive-adjacent form.
   */
  GENITIVE,
  /**
   * Indirect object or recipient form.
   */
  DATIVE,
  /**
   * Instrument or accompaniment form.
   */
  INSTRUMENTAL,
  /**
   * Location or place form.
   */
  LOCATIVE,
  /**
   * Preposition-governed form used in languages such as Russian.
   */
  PREPOSITIONAL,
  /**
   * Direct address form.
   */
  VOCATIVE,
  /**
   * Source, motion-away-from, or separation form.
   */
  ABLATIVE;

  @NonNull
  private static final Map<@NonNull String, @NonNull GrammaticalCase> GRAMMATICAL_CASES_BY_NAME;

  static {
    GRAMMATICAL_CASES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        GrammaticalCase.values()).collect(Collectors.toMap(grammaticalCase -> grammaticalCase.name(), grammaticalCase -> grammaticalCase)));
  }

  /**
   * Gets the mapping of grammatical case names to grammatical case values.
   *
   * @return the mapping of grammatical case names to grammatical case values, not null
   */
  @NonNull
  static Map<@NonNull String, @NonNull GrammaticalCase> getGrammaticalCasesByName() {
    return GRAMMATICAL_CASES_BY_NAME;
  }
}
