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
import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

/**
 * Contract for localized string providers - given a key and placeholders, return a localized string.
 * <p>
 * Format is {@code "You are missing {{requiredFieldCount}} required fields."}
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public interface Strings {
  /**
   * Gets a localized string for the given key.
   * <p>
   * If no localized string is available, the key is returned.
   *
   * @param key the localization key, not null
   * @return a localized string for the key, not null
   */
  @Nonnull
  String get(@Nonnull String key);

  /**
   * Gets a localized string for the given key.
   * <p>
   * If no localized string is available, the key is returned.
   *
   * @param key    the localization key, not null
   * @param locale the preferred locale for the string, may be null
   * @return a localized string for the key, not null
   */
  @Nonnull
  String get(@Nonnull String key, @Nullable Locale locale);

  /**
   * Gets a localized string for the given key.
   * <p>
   * If no localized string is available, the key is returned.
   *
   * @param key          the localization key, not null
   * @param placeholders the placeholders to insert into the string, may be null
   * @return a localized string for the key, not null
   */
  @Nonnull
  String get(@Nonnull String key, @Nullable Map<String, Object> placeholders);

  /**
   * Gets a localized string for the given key.
   * <p>
   * If no localized string is available, the key is returned.
   *
   * @param key          the localization key, not null
   * @param placeholders the placeholders to insert into the string, may be null
   * @param locale       the preferred locale for the string, may be null
   * @return a localized string for the key, not null
   */
  @Nonnull
  String get(@Nonnull String key, @Nullable Map<String, Object> placeholders, @Nullable Locale locale);
}