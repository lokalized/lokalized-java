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

import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslationRange;
import com.lokalized.MinimalJson.Json;
import com.lokalized.MinimalJson.JsonArray;
import com.lokalized.MinimalJson.JsonObject;
import com.lokalized.MinimalJson.JsonObject.Member;
import com.lokalized.MinimalJson.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Utility methods for loading localized strings files.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class LocalizedStringLoader {
  @NonNull
  private static final Map<@NonNull String, @NonNull LanguageForm> SUPPORTED_LANGUAGE_FORMS_BY_NAME;
  @NonNull
  private static final Logger LOGGER;
  @NonNull
  private static final ExpressionEvaluator EXPRESSION_EVALUATOR;
  @NonNull
  private static final Pattern PLACEHOLDER_NAME_PATTERN;
  @NonNull
  private static final Pattern LANGUAGE_TAG_PATTERN;
  @NonNull
  private static final String JSON_EXTENSION;

  static {
    LOGGER = Logger.getLogger(LoggerType.LOCALIZED_STRING_LOADER.getLoggerName());
    EXPRESSION_EVALUATOR = new ExpressionEvaluator();

    Set<@NonNull LanguageForm> supportedLanguageForms = new LinkedHashSet<>();
    supportedLanguageForms.addAll(Arrays.asList(Gender.values()));
    supportedLanguageForms.addAll(Arrays.asList(Formality.values()));
    supportedLanguageForms.addAll(Arrays.asList(Clusivity.values()));
    supportedLanguageForms.addAll(Arrays.asList(Animacy.values()));
    supportedLanguageForms.addAll(Arrays.asList(Cardinality.values()));
    supportedLanguageForms.addAll(Arrays.asList(Ordinality.values()));
    supportedLanguageForms.addAll(Arrays.asList(Phonetic.values()));

    Map<@NonNull String, @NonNull LanguageForm> supportedLanguageFormsByName = new LinkedHashMap<>();

    for (LanguageForm languageForm : supportedLanguageForms) {
      if (!languageForm.getClass().isEnum())
        throw new IllegalArgumentException(format("The %s interface must be implemented by enum types. %s is not an enum",
            LanguageForm.class.getSimpleName(), languageForm.getClass().getSimpleName()));

      String languageFormName = ((Enum<?>) languageForm).name();
      LanguageForm existingLanguageForm = supportedLanguageFormsByName.get(languageFormName);

      if (existingLanguageForm != null)
        throw new IllegalArgumentException(format("There is already a language form %s.%s whose name collides with %s.%s. " +
                "Language form names must be unique", existingLanguageForm.getClass().getSimpleName(), languageFormName,
            languageForm.getClass().getSimpleName(), languageFormName));

      // Massage Cardinality to match file format, e.g. "ONE" -> "CARDINALITY_ONE"
      if (languageForm instanceof Cardinality)
        languageFormName = LocalizedStringUtils.localizedStringNameForCardinalityName(languageFormName);

      // Massage Ordinality to match file format, e.g. "ONE" -> "ORDINALITY_ONE"
      if (languageForm instanceof Ordinality)
        languageFormName = LocalizedStringUtils.localizedStringNameForOrdinalityName(languageFormName);

      // Massage Gender to match file format, e.g. "MASCULINE" -> "GENDER_MASCULINE"
      if (languageForm instanceof Gender)
        languageFormName = LocalizedStringUtils.localizedStringNameForGenderName(languageFormName);

      // Massage Formality to match file format, e.g. "FORMAL" -> "FORMALITY_FORMAL"
      if (languageForm instanceof Formality)
        languageFormName = LocalizedStringUtils.localizedStringNameForFormalityName(languageFormName);

      // Massage Clusivity to match file format, e.g. "INCLUSIVE" -> "CLUSIVITY_INCLUSIVE"
      if (languageForm instanceof Clusivity)
        languageFormName = LocalizedStringUtils.localizedStringNameForClusivityName(languageFormName);

      // Massage Animacy to match file format, e.g. "ANIMATE" -> "ANIMACY_ANIMATE"
      if (languageForm instanceof Animacy)
        languageFormName = LocalizedStringUtils.localizedStringNameForAnimacyName(languageFormName);

      // Massage Phonetic to match file format, e.g. "VOWEL" -> "PHONETIC_VOWEL"
      if (languageForm instanceof Phonetic)
        languageFormName = LocalizedStringUtils.localizedStringNameForPhoneticName(languageFormName);

      supportedLanguageFormsByName.put(languageFormName, languageForm);
    }

    SUPPORTED_LANGUAGE_FORMS_BY_NAME = Collections.unmodifiableMap(supportedLanguageFormsByName);
    PLACEHOLDER_NAME_PATTERN = Pattern.compile("^[\\p{Alpha}_][\\p{Alnum}_-]*$");
    LANGUAGE_TAG_PATTERN = Pattern.compile("^[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*$");
    JSON_EXTENSION = ".json";
  }

  private LocalizedStringLoader() {
    // Non-instantiable
  }

  /**
   * Loads all localized string files present in the specified package on the classpath.
   * <p>
   * Filenames must correspond to the IETF BCP 47 language tag format, optionally suffixed with {@code .json}.
   * <p>
   * Example filenames:
   * <ul>
   * <li>{@code en}</li>
   * <li>{@code en.json}</li>
   * <li>{@code es-MX}</li>
   * <li>{@code es-MX.json}</li>
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
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull String classpathPackage) {
    return loadFromClasspath(LocalizedStringLoader.class.getClassLoader(), classpathPackage);
  }

  @NonNull
  static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull ClassLoader classLoader,
                                                             @NonNull String classpathPackage) {
    requireNonNull(classpathPackage);
    requireNonNull(classLoader);

    Enumeration<URL> urls;

    try {
      urls = classLoader.getResources(classpathPackage);
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to search classpath for '%s'", classpathPackage), e);
    }

    if (!urls.hasMoreElements())
      throw new LocalizedStringLoadingException(format("Unable to find package '%s' on the classpath", classpathPackage));

    Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> mergedByLocale = createLocaleKeyMap();

    while (urls.hasMoreElements()) {
      URL url = urls.nextElement();
      Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = loadFromUrl(url, classpathPackage);
      mergeLocalizedStrings(mergedByLocale, localizedStringsByLocale);
    }

    return toLocalizedStringsByLocale(mergedByLocale);
  }

  /**
   * Loads all localized string files present in the specified directory.
   * <p>
   * Filenames must correspond to the IETF BCP 47 language tag format, optionally suffixed with {@code .json}.
   * <p>
   * Example filenames:
   * <ul>
   * <li>{@code en}</li>
   * <li>{@code en.json}</li>
   * <li>{@code es-MX}</li>
   * <li>{@code es-MX.json}</li>
   * <li>{@code nan-Hant-TW}</li>
   * </ul>
   * <p>
   * Note: this implementation only scans the specified directory, it does not descend into child directories.
   *
   * @param directory directory in which to search for localized string files, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(@NonNull Path directory) {
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
  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromDirectory(@NonNull File directory) {
    requireNonNull(directory);

    if (!directory.exists())
      throw new LocalizedStringLoadingException(format("Location '%s' does not exist",
          directory));

    if (!directory.isDirectory())
      throw new LocalizedStringLoadingException(format("Location '%s' exists but is not a directory",
          directory));

    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = createLocaleMap();

    File[] files = directory.listFiles();

    if (files == null)
      throw new LocalizedStringLoadingException(format("Unable to list files in directory '%s'", directory));

    if (files != null) {
      for (File file : files) {
        if (file.isDirectory())
          continue;

        String fileName = file.getName();
        String languageTag = languageTagForFileName(fileName);

        if (languageTag != null) {
          LOGGER.fine(format("Loading localized strings file '%s'...", fileName));
          Locale locale = Locale.forLanguageTag(languageTag);

          if (localizedStringsByLocale.containsKey(locale))
            throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found at '%s'",
                locale.toLanguageTag(), file.getPath()));

          localizedStringsByLocale.put(locale, parseLocalizedStringsFile(file));
        } else {
          LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", fileName));
        }
      }
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromUrl(@NonNull URL url, @NonNull String classpathPackage) {
    requireNonNull(url);
    requireNonNull(classpathPackage);

    String protocol = url.getProtocol();

    if ("file".equals(protocol)) {
      try {
        return loadFromDirectory(Paths.get(url.toURI()).toFile());
      } catch (URISyntaxException e) {
        throw new LocalizedStringLoadingException(format("Unable to resolve classpath location '%s'", url), e);
      }
    }

    if ("jar".equals(protocol))
      return loadFromJar(url, classpathPackage);

    throw new LocalizedStringLoadingException(format("Unsupported classpath protocol '%s' for location '%s'",
        protocol, url));
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromJar(@NonNull URL jarUrl,
                                                               @NonNull String classpathPackage) {
    requireNonNull(jarUrl);
    requireNonNull(classpathPackage);

    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = createLocaleMap();

    try {
      JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
      connection.setUseCaches(false);

      try (JarFile jarFile = connection.getJarFile()) {
        String packagePath = connection.getEntryName();

        if (packagePath == null || packagePath.isEmpty())
          packagePath = classpathPackage;

        if (!packagePath.endsWith("/"))
          packagePath = packagePath + "/";

        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();

          if (entry.isDirectory())
            continue;

          String entryName = entry.getName();

          if (!entryName.startsWith(packagePath))
            continue;

          String relativeName = entryName.substring(packagePath.length());

          if ("".equals(relativeName) || relativeName.contains("/"))
            continue;

          String languageTag = languageTagForFileName(relativeName);

          if (languageTag != null) {
            LOGGER.fine(format("Loading localized strings file '%s' from %s...", relativeName, jarFile.getName()));
            Locale locale = Locale.forLanguageTag(languageTag);

            if (localizedStringsByLocale.containsKey(locale))
              throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found in %s",
                  locale.toLanguageTag(), jarFile.getName()));

            try (InputStream inputStream = jarFile.getInputStream(entry)) {
              String contents = new String(inputStream.readAllBytes(), UTF_8).trim();
              String canonicalPath = format("jar:%s!/%s", jarFile.getName(), entryName);
              localizedStringsByLocale.put(locale, parseLocalizedStrings(canonicalPath, contents));
            }
          } else {
            LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", relativeName));
          }
        }
      }
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings from '%s'", jarUrl), e);
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> createLocaleMap() {
    return new TreeMap<>((locale1, locale2) -> locale1.toLanguageTag().compareTo(locale2.toLanguageTag()));
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> createLocaleKeyMap() {
    return new TreeMap<>((locale1, locale2) -> locale1.toLanguageTag().compareTo(locale2.toLanguageTag()));
  }

  private static void mergeLocalizedStrings(
      @NonNull Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> target,
      @NonNull Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> source) {
    requireNonNull(target);
    requireNonNull(source);

    for (Map.Entry<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> entry : source.entrySet()) {
      Locale locale = entry.getKey();
      Map<@NonNull String, @NonNull LocalizedString> localizedStringsByKey = target.get(locale);

      if (localizedStringsByKey == null) {
        localizedStringsByKey = new LinkedHashMap<>();
        target.put(locale, localizedStringsByKey);
      }

      for (LocalizedString localizedString : entry.getValue()) {
        String key = localizedString.getKey();
        LocalizedString existing = localizedStringsByKey.get(key);

        if (existing != null)
          throw new LocalizedStringLoadingException(format("Duplicate localized string key '%s' found for locale '%s' while merging classpath resources",
              key, locale.toLanguageTag()));

        localizedStringsByKey.put(key, localizedString);
      }
    }
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> toLocalizedStringsByLocale(
      @NonNull Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> localizedStringsByKeyByLocale) {
    requireNonNull(localizedStringsByKeyByLocale);

    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = createLocaleMap();

    for (Map.Entry<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull LocalizedString>> entry : localizedStringsByKeyByLocale.entrySet()) {
      localizedStringsByLocale.put(entry.getKey(),
          Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue().values())));
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  private static boolean isLanguageTag(@NonNull String languageTag) {
    requireNonNull(languageTag);

    if (!LANGUAGE_TAG_PATTERN.matcher(languageTag).matches())
      return false;

    Locale locale = Locale.forLanguageTag(languageTag);
    if (!"".equals(locale.getLanguage()))
      return true;

    return languageTag.toLowerCase(Locale.ROOT).startsWith("x-");
  }

  @Nullable
  private static String languageTagForFileName(@NonNull String fileName) {
    requireNonNull(fileName);

    String languageTag = fileName;

    if (fileName.toLowerCase(Locale.ROOT).endsWith(JSON_EXTENSION))
      languageTag = fileName.substring(0, fileName.length() - JSON_EXTENSION.length());

    return isLanguageTag(languageTag) ? languageTag : null;
  }

  /**
   * Parses out a set of localized strings from the given file.
   *
   * @param file the file to parse, not null
   * @return the set of localized strings contained in the file, not null
   * @throws LocalizedStringLoadingException if an error occurs while parsing the localized string file
   */
  @NonNull
  private static Set<@NonNull LocalizedString> parseLocalizedStringsFile(@NonNull File file) {
    requireNonNull(file);

    String canonicalPath;

    try {
      canonicalPath = file.getCanonicalPath();
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(
          format("Unable to determine canonical path for localized strings file %s", file), e);
    }

    if (!Files.isRegularFile(file.toPath()))
      throw new LocalizedStringLoadingException(format("%s is not a regular file", canonicalPath));

    String localizedStringsFileContents;

    try {
      localizedStringsFileContents = new String(Files.readAllBytes(file.toPath()), UTF_8).trim();
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings file contents for %s",
          canonicalPath), e);
    }

    return parseLocalizedStrings(canonicalPath, localizedStringsFileContents);
  }

  @NonNull
  private static Set<@NonNull LocalizedString> parseLocalizedStrings(@NonNull String canonicalPath,
                                                            @NonNull String localizedStringsFileContents) {
    requireNonNull(canonicalPath);
    requireNonNull(localizedStringsFileContents);

    if ("".equals(localizedStringsFileContents))
      return Collections.emptySet();

    Set<@NonNull LocalizedString> localizedStrings = new HashSet<>();
    JsonValue outerJsonValue;

    try {
      outerJsonValue = Json.parse(localizedStringsFileContents);
    } catch (MinimalJson.ParseException e) {
      throw new LocalizedStringLoadingException(
          format("%s: unable to parse localized strings file", canonicalPath), e);
    }

    if (!outerJsonValue.isObject())
      throw new LocalizedStringLoadingException(format("%s: a localized strings file must be comprised of a single JSON object", canonicalPath));

    JsonObject outerJsonObject = outerJsonValue.asObject();
    Set<String> keys = new HashSet<>();

    for (Member member : outerJsonObject) {
      String key = member.getName();

      if (!keys.add(key))
        throw new LocalizedStringLoadingException(format("%s: duplicate localized string key '%s' encountered", canonicalPath, key));

      JsonValue value = member.getValue();
      localizedStrings.add(parseLocalizedString(canonicalPath, key, value, null));
    }

    return Collections.unmodifiableSet(localizedStrings);
  }

  /**
   * Parses "toplevel" localized string data.
   * <p>
   * Operates recursively if alternatives are encountered.
   *
   * @param canonicalPath the unique path to the file (or URL) being parsed, used for error reporting. not null
   * @param key           the toplevel translation key, not null
   * @param jsonValue     the toplevel translation value - might be a simple string, might be a complex object. not null
   * @return a localized string instance, not null
   * @throws LocalizedStringLoadingException if an error occurs while parsing the localized string file
   */
  @NonNull
  private static LocalizedString parseLocalizedString(@NonNull String canonicalPath, @NonNull String key, @NonNull JsonValue jsonValue,
                                                      @Nullable List<@NonNull Token> expressionTokens) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(jsonValue);

    LocalizedString.Builder localizedStringBuilder = new LocalizedString.Builder(key).expressionTokens(expressionTokens);

    if (jsonValue.isString()) {
      // Simple case - just a key and a value, no translation rules
      //
      // Example format:
      //
      // {
      //   "Hello, world!" : "Приветствую, мир"
      // }

      String translation = jsonValue.asString();

      if (translation == null)
        throw new LocalizedStringLoadingException(format("%s: a translation is required for key '%s'", canonicalPath, key));

      return localizedStringBuilder.translation(translation).build();
    } else if (jsonValue.isObject()) {
      // More complex case, there can be placeholders and alternatives.
      //
      // Example format:
      //
      // {
      //   "I read {{bookCount}} books" : {
      //     "translation" : "I read {{bookCount}} {{books}}",
      //     "commentary" : "Message shown when user achieves her book-reading goal for the month",
      //     "placeholders" : {
      //       "books" : {
      //         "value" : "bookCount",
      //         "translations" : {
      //           "ONE" : "book",
      //           "OTHER" : "books"
      //         }
      //       }
      //     },
      //     "alternatives" : [
      //       {
      //         "bookCount == 0" : {
      //           "translation" : "I haven't read any books"
      //         }
      //       }
      //     ]
      //   }
      // }

      JsonObject localizedStringObject = jsonValue.asObject();

      String translation = null;

      JsonValue translationJsonValue = localizedStringObject.get("translation");

      if (translationJsonValue != null && !translationJsonValue.isNull()) {
        if (!translationJsonValue.isString())
          throw new LocalizedStringLoadingException(format("%s: translation must be a string for key '%s'", canonicalPath, key));

        translation = translationJsonValue.asString();
      }

      String commentary = null;

      JsonValue commentaryJsonValue = localizedStringObject.get("commentary");

      if (commentaryJsonValue != null && !commentaryJsonValue.isNull()) {
        if (!commentaryJsonValue.isString())
          throw new LocalizedStringLoadingException(format("%s: commentary must be a string for key '%s'", canonicalPath, key));

        commentary = commentaryJsonValue.asString();
      }

      Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder = new LinkedHashMap<>();

      JsonValue placeholdersJsonValue = localizedStringObject.get("placeholders");

      if (placeholdersJsonValue != null && !placeholdersJsonValue.isNull()) {
        if (!placeholdersJsonValue.isObject())
          throw new LocalizedStringLoadingException(format("%s: the placeholders value must be an object. Key is '%s'", canonicalPath, key));

        JsonObject placeholdersJsonObject = placeholdersJsonValue.asObject();

        for (Member placeholderMember : placeholdersJsonObject) {
          String placeholderKey = placeholderMember.getName();
          JsonValue placeholderJsonValue = placeholderMember.getValue();
          String value = null;
          LanguageFormTranslationRange rangeValue = null;

          ensureValidPlaceholderName(canonicalPath, key, placeholderKey, "placeholder");

          if (!placeholderJsonValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: the placeholder value must be an object. Key is '%s'", canonicalPath, key));

          JsonObject placeholderJsonObject = placeholderJsonValue.asObject();

          JsonValue valueJsonValue = placeholderJsonObject.get("value");
          JsonValue rangeJsonValue = placeholderJsonObject.get("range");
          boolean hasValue = valueJsonValue != null && !valueJsonValue.isNull();
          boolean hasRangeValue = rangeJsonValue != null && !rangeJsonValue.isNull();

          if (!hasValue && !hasRangeValue)
            throw new LocalizedStringLoadingException(format("%s: a placeholder translation value or range is required. Key is '%s'", canonicalPath, key));

          if (hasValue && hasRangeValue)
            throw new LocalizedStringLoadingException(format("%s: a placeholder translation cannot have both a value and a range. Key is '%s'", canonicalPath, key));

          if (hasRangeValue) {
            if (!rangeJsonValue.isObject())
              throw new LocalizedStringLoadingException(format("%s: the placeholder translation range must be an object. Key is '%s'", canonicalPath, key));

            JsonObject rangeJsonObject = rangeJsonValue.asObject();
            JsonValue rangeValueStartJsonValue = rangeJsonObject.get("start");
            JsonValue rangeValueEndJsonValue = rangeJsonObject.get("end");

            if (rangeValueStartJsonValue == null || rangeValueStartJsonValue.isNull())
              throw new LocalizedStringLoadingException(format("%s: a placeholder translation range start is required. Key is '%s'", canonicalPath, key));

            if (rangeValueEndJsonValue == null || rangeValueEndJsonValue.isNull())
              throw new LocalizedStringLoadingException(format("%s: a placeholder translation range end is required. Key is '%s'", canonicalPath, key));

            if (!rangeValueStartJsonValue.isString())
              throw new LocalizedStringLoadingException(format("%s: a placeholder translation range start must be a string. Key is '%s'", canonicalPath, key));

            if (!rangeValueEndJsonValue.isString())
              throw new LocalizedStringLoadingException(format("%s: a placeholder translation range end must be a string. Key is '%s'", canonicalPath, key));

            String rangeStartValue = rangeValueStartJsonValue.asString();
            String rangeEndValue = rangeValueEndJsonValue.asString();

            ensureValidPlaceholderName(canonicalPath, key, rangeStartValue, "range start");
            ensureValidPlaceholderName(canonicalPath, key, rangeEndValue, "range end");

            rangeValue = new LanguageFormTranslationRange(rangeStartValue, rangeEndValue);
          } else {
            if (!valueJsonValue.isString())
              throw new LocalizedStringLoadingException(format("%s: a placeholder translation value must be a string. Key is '%s'", canonicalPath, key));

            value = valueJsonValue.asString();
            ensureValidPlaceholderName(canonicalPath, key, value, "placeholder value");
          }

          JsonValue translationsJsonValue = placeholderJsonObject.get("translations");

          if (translationsJsonValue == null || translationsJsonValue.isNull())
            throw new LocalizedStringLoadingException(format("%s: placeholder translations are required. Key is '%s'", canonicalPath, key));

          if (!translationsJsonValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: the placeholder translations value must be an object. Key is '%s'", canonicalPath, key));

          Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm = new LinkedHashMap<>();

          JsonObject translationsJsonObject = translationsJsonValue.asObject();

          for (Member translationMember : translationsJsonObject) {
            String languageFormTranslationKey = translationMember.getName();
            JsonValue languageFormTranslationJsonValue = translationMember.getValue();
            LanguageForm languageForm = SUPPORTED_LANGUAGE_FORMS_BY_NAME.get(languageFormTranslationKey);

            if (languageForm == null)
              throw new LocalizedStringLoadingException(format("%s: unexpected placeholder translation language form encountered. Key is '%s'. " +
                      "You provided '%s', valid values are [%s]", canonicalPath, key, languageFormTranslationKey,
                  SUPPORTED_LANGUAGE_FORMS_BY_NAME.keySet().stream().collect(Collectors.joining(", "))));

            if (!languageFormTranslationJsonValue.isString())
              throw new LocalizedStringLoadingException(format("%s: the placeholder translation value must be a string. Key is '%s'", canonicalPath, key));

            translationsByLanguageForm.put(languageForm, languageFormTranslationJsonValue.asString());
          }

          if (translationsByLanguageForm.isEmpty())
            throw new LocalizedStringLoadingException(format("%s: placeholder translations are required. Key is '%s'", canonicalPath, key));

          Set<Class<?>> languageFormTypes = new HashSet<>();

          for (LanguageForm languageForm : translationsByLanguageForm.keySet())
            languageFormTypes.add(languageForm.getClass());

          if (languageFormTypes.size() > 1)
            throw new LocalizedStringLoadingException(format("%s: you cannot mix-and-match language forms in placeholder translations. " +
                "Placeholder is '%s' for key '%s'", canonicalPath, placeholderKey, key));

          if (rangeValue != null) {
            boolean hasNonCardinality = translationsByLanguageForm.keySet().stream()
                .anyMatch(languageForm -> !(languageForm instanceof Cardinality));

            if (hasNonCardinality)
              throw new LocalizedStringLoadingException(format("%s: range-based translations only support %s. Placeholder is '%s' for key '%s'",
                  canonicalPath, Cardinality.class.getSimpleName(), placeholderKey, key));
          }

          LanguageFormTranslation languageFormTranslation = rangeValue != null
              ? new LanguageFormTranslation(rangeValue, translationsByLanguageForm)
              : new LanguageFormTranslation(value, translationsByLanguageForm);

          languageFormTranslationsByPlaceholder.put(placeholderKey, languageFormTranslation);
        }
      }

      List<@NonNull LocalizedString> alternatives = new ArrayList<>();

      JsonValue alternativesJsonValue = localizedStringObject.get("alternatives");

      if (alternativesJsonValue != null && !alternativesJsonValue.isNull()) {
        if (!alternativesJsonValue.isArray())
          throw new LocalizedStringLoadingException(format("%s: alternatives must be an array. Key is '%s'", canonicalPath, key));

        JsonArray alternativesJsonArray = alternativesJsonValue.asArray();

        for (JsonValue alternativeJsonValue : alternativesJsonArray) {
          if (alternativeJsonValue == null || alternativeJsonValue.isNull())
            continue;

          if (!alternativeJsonValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: alternative value must be an object. Key is '%s'", canonicalPath, key));

          JsonObject outerJsonObject = alternativeJsonValue.asObject();

          for (Member member : outerJsonObject) {
            String alternativeKey = member.getName();
            JsonValue alternativeValue = member.getValue();
            List<@NonNull Token> alternativeTokens = parseExpressionTokens(canonicalPath, alternativeKey);
            alternatives.add(parseLocalizedString(canonicalPath, alternativeKey, alternativeValue, alternativeTokens));
          }
        }
      }

      if (translation == null && alternatives.isEmpty())
        throw new LocalizedStringLoadingException(format("%s: either a translation or at least one alternative expression is required for key '%s'",
            canonicalPath, key));

      return localizedStringBuilder.translation(translation)
          .commentary(commentary)
          .languageFormTranslationsByPlaceholder(languageFormTranslationsByPlaceholder)
          .alternatives(alternatives)
          .build();
    } else {
      throw new LocalizedStringLoadingException(format("%s: either a translation string or object value is required for key '%s'",
          canonicalPath, key));
    }
  }

  @NonNull
  private static List<@NonNull Token> parseExpressionTokens(@NonNull String canonicalPath, @NonNull String expression) {
    requireNonNull(canonicalPath);
    requireNonNull(expression);

    try {
      List<@NonNull Token> tokens = EXPRESSION_EVALUATOR.getExpressionTokenizer().extractTokens(expression);
      List<@NonNull Token> rpnTokens = EXPRESSION_EVALUATOR.convertTokensToReversePolishNotation(tokens);
      EXPRESSION_EVALUATOR.validateReversePolishNotationTokens(rpnTokens);
      return rpnTokens;
    } catch (ExpressionEvaluationException e) {
      throw new LocalizedStringLoadingException(
          format("%s: unable to parse alternative expression '%s'", canonicalPath, expression), e);
    }
  }

  private static void ensureValidPlaceholderName(@NonNull String canonicalPath, @NonNull String key,
                                                 @NonNull String placeholderName, @NonNull String description) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderName);
    requireNonNull(description);

    if (!PLACEHOLDER_NAME_PATTERN.matcher(placeholderName).matches())
      throw new LocalizedStringLoadingException(format("%s: invalid %s '%s'. Placeholder names must start with a letter or underscore " +
          "and contain only letters, digits, underscores, or hyphens. Key is '%s'", canonicalPath, description, placeholderName, key));
  }
}
