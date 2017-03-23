/*
 * Copyright 2017 Product Mog LLC.
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

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Language gender forms.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public enum Gender implements LanguageForm {
  /**
   * Masculine gender.
   */
  MASCULINE,
  /**
   * Feminine gender.
   */
  FEMININE,
  /**
   * Neutral/unspecified gender.
   */
  NEUTER;

  @Nonnull
  private static final Map<String, Gender> GENDERS_BY_NAME;

  static {
    GENDERS_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
        Gender.values()).collect(Collectors.toMap(gender -> gender.name(), gender -> gender)));
  }

  /**
   * Gets the mapping of gender names to gender values.
   *
   * @return the mapping of gender names to gender values, not null
   */
  @Nonnull
  public static Map<String, Gender> getGendersByName() {
    return GENDERS_BY_NAME;
  }
}
