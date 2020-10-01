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
import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Utility methods for loading localized strings files.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class LocalizedStringLoader {
  @Nonnull
  private static final Set<String> SUPPORTED_LANGUAGE_TAGS;
  @Nonnull
  private static final LocalizedStringParser LOCALIZED_STRING_PARSER;
  @Nonnull
  private static final Logger LOGGER;

  static {
    LOGGER = Logger.getLogger(LoggerType.LOCALIZED_STRING_LOADER.getLoggerName());
    LOCALIZED_STRING_PARSER = new LocalizedStringParser();
    SUPPORTED_LANGUAGE_TAGS = Collections.unmodifiableSet(Arrays.stream(Locale.getAvailableLocales())
        .map(locale -> locale.toLanguageTag())
        .collect(Collectors.toSet()));
  }

  private LocalizedStringLoader() {
    // Non-instantiable
  }

  /**
   * Loads all localized string files present in the specified package on the classpath.
   * <p>
   * Filenames must correspond to the IETF BCP 47 language tag format.
   * <p>
   * Example filenames:
   * <ul>
   * <li>{@code en}</li>
   * <li>{@code es-MX}</li>
   * <li>{@code nan-Hant-TW}</li>
   * </ul>
   * <p>
   * Like any classpath reference, packages are separated using the {@code /} character.
   * <p>
   * Example package names:
   * <ul>
   * <li>{@code strings}
   * <li>{@code com/lokalized/strings}
   * </ul>
   * <p>
   * Note: this implementation only scans the specified package, it does not descend into child packages.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @Nonnull
  public static Map<Locale, Set<LocalizedString>> loadFromClasspath(@Nonnull String classpathPackage) {
    requireNonNull(classpathPackage);

    ClassLoader classLoader = LocalizedStringLoader.class.getClassLoader();
    URL url = classLoader.getResource(classpathPackage);

    if (url == null)
      throw new LocalizedStringLoadingException(format("Unable to find package '%s' on the classpath", classpathPackage));

    return loadFromDirectory(new File(url.getFile()));
  }

  /**
   * Loads all localized string files present in the specified directory.
   * <p>
   * Filenames must correspond to the IETF BCP 47 language tag format.
   * <p>
   * Example filenames:
   * <ul>
   * <li>{@code en}</li>
   * <li>{@code es-MX}</li>
   * <li>{@code nan-Hant-TW}</li>
   * </ul>
   * <p>
   * Note: this implementation only scans the specified directory, it does not descend into child directories.
   *
   * @param directory directory in which to search for localized string files, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @Nonnull
  public static Map<Locale, Set<LocalizedString>> loadFromFilesystem(@Nonnull Path directory) {
    requireNonNull(directory);
    return loadFromDirectory(directory.toFile());
  }

  // TODO: should we expose methods for loading a single file?

  /**
   * Loads all localized string files present in the specified directory.
   *
   * @param directory directory in which to search for localized string files, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @Nonnull
  private static Map<Locale, Set<LocalizedString>> loadFromDirectory(@Nonnull File directory) {
    requireNonNull(directory);

    if (!directory.exists())
      throw new LocalizedStringLoadingException(format("Location '%s' does not exist",
          directory));

    if (!directory.isDirectory())
      throw new LocalizedStringLoadingException(format("Location '%s' exists but is not a directory",
          directory));

    Map<Locale, Set<LocalizedString>> localizedStringsByLocale =
        new TreeMap<>((locale1, locale2) -> locale1.toLanguageTag().compareTo(locale2.toLanguageTag()));

    File[] files = directory.listFiles();

    if (files != null) {
      for (File file : files) {
        String languageTag = file.getName();

        if (SUPPORTED_LANGUAGE_TAGS.contains(languageTag)) {
          LOGGER.fine(format("Loading localized strings file '%s'...", languageTag));
          Locale locale = Locale.forLanguageTag(file.getName());
          localizedStringsByLocale.put(locale, LOCALIZED_STRING_PARSER.parseLocalizedStringsFile(file.toPath()));
        } else {
          LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", languageTag));
        }
      }
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }
}