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

import com.lokalized.LocalizedString.LanguageFormSelector;
import com.lokalized.LocalizedString.PlaceholderMetadata;
import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslationRule;
import com.lokalized.LocalizedString.LanguageFormTranslationRange;
import com.lokalized.MinimalJson.Json;
import com.lokalized.MinimalJson.JsonArray;
import com.lokalized.MinimalJson.JsonObject;
import com.lokalized.MinimalJson.JsonObject.Member;
import com.lokalized.MinimalJson.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

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
  private static final int MAXIMUM_JSON_DIAGNOSTIC_PATH_CHARACTERS = 4096;
  @NonNull
  private static final Map<@NonNull String, @NonNull LanguageForm> SUPPORTED_LANGUAGE_FORMS_BY_NAME;
  @NonNull
  private static final Map<@NonNull String, @NonNull LanguageFormType> SUPPORTED_LANGUAGE_FORM_TYPES_BY_NAME;
  @NonNull
  private static final Map<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> SUPPORTED_LANGUAGE_FORM_NAMES_BY_TYPE;
  @NonNull
  private static final Logger LOGGER;
  @NonNull
  private static final ExpressionEvaluator EXPRESSION_EVALUATOR;
  @NonNull
  private static final Pattern LANGUAGE_TAG_PATTERN;
  @NonNull
  private static final String JSON_EXTENSION;
  private static final char UTF_8_BOM;

  static {
    LOGGER = Logger.getLogger(LoggerType.LOCALIZED_STRING_LOADER.getLoggerName());
    EXPRESSION_EVALUATOR = new ExpressionEvaluator();

    Set<@NonNull LanguageForm> supportedLanguageForms = new LinkedHashSet<>();
    supportedLanguageForms.addAll(Arrays.asList(Gender.values()));
    supportedLanguageForms.addAll(Arrays.asList(GrammaticalCase.values()));
    supportedLanguageForms.addAll(Arrays.asList(Definiteness.values()));
    supportedLanguageForms.addAll(Arrays.asList(Classifier.values()));
    supportedLanguageForms.addAll(Arrays.asList(Formality.values()));
    supportedLanguageForms.addAll(Arrays.asList(Clusivity.values()));
    supportedLanguageForms.addAll(Arrays.asList(Animacy.values()));
    supportedLanguageForms.addAll(Arrays.asList(Cardinality.values()));
    supportedLanguageForms.addAll(Arrays.asList(Ordinality.values()));
    supportedLanguageForms.addAll(Arrays.asList(Phonetic.values()));

    Map<@NonNull String, @NonNull LanguageForm> supportedLanguageFormsByName = new LinkedHashMap<>();
    Map<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> supportedLanguageFormNamesByType = new LinkedHashMap<>();

    for (LanguageFormType languageFormType : LanguageFormType.values())
      supportedLanguageFormNamesByType.put(languageFormType, new LinkedHashSet<>());

    for (LanguageForm languageForm : supportedLanguageForms) {
      if (!languageForm.getClass().isEnum())
        throw new IllegalArgumentException(format("The %s interface must be implemented by enum types. %s is not an enum",
            LanguageForm.class.getSimpleName(), languageForm.getClass().getSimpleName()));

      String languageFormName = ((Enum<?>) languageForm).name();

      // Massage Cardinality to match file format, e.g. "ONE" -> "CARDINALITY_ONE"
      if (languageForm instanceof Cardinality)
        languageFormName = LocalizedStringUtils.localizedStringNameForCardinalityName(languageFormName);

      // Massage Ordinality to match file format, e.g. "ONE" -> "ORDINALITY_ONE"
      if (languageForm instanceof Ordinality)
        languageFormName = LocalizedStringUtils.localizedStringNameForOrdinalityName(languageFormName);

      // Massage Gender to match file format, e.g. "MASCULINE" -> "GENDER_MASCULINE"
      if (languageForm instanceof Gender)
        languageFormName = LocalizedStringUtils.localizedStringNameForGenderName(languageFormName);

      // Massage GrammaticalCase to match file format, e.g. "DATIVE" -> "CASE_DATIVE"
      if (languageForm instanceof GrammaticalCase)
        languageFormName = LocalizedStringUtils.localizedStringNameForGrammaticalCaseName(languageFormName);

      // Massage Definiteness to match file format, e.g. "DEFINITE" -> "DEFINITENESS_DEFINITE"
      if (languageForm instanceof Definiteness)
        languageFormName = LocalizedStringUtils.localizedStringNameForDefinitenessName(languageFormName);

      // Massage Classifier to match file format, e.g. "GENERAL" -> "CLASSIFIER_GENERAL"
      if (languageForm instanceof Classifier)
        languageFormName = LocalizedStringUtils.localizedStringNameForClassifierName(languageFormName);

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

      LanguageForm existingLanguageForm = supportedLanguageFormsByName.get(languageFormName);

      if (existingLanguageForm != null)
        throw new IllegalArgumentException(format("There is already a language form %s.%s whose localized string name collides with %s.%s. " +
                "Localized string language form names must be unique", existingLanguageForm.getClass().getSimpleName(), languageFormName,
            languageForm.getClass().getSimpleName(), languageFormName));

      supportedLanguageFormsByName.put(languageFormName, languageForm);
      supportedLanguageFormNamesByType.get(LanguageFormType.forLanguageForm(languageForm)).add(languageFormName);
    }

    SUPPORTED_LANGUAGE_FORMS_BY_NAME = Collections.unmodifiableMap(supportedLanguageFormsByName);
    SUPPORTED_LANGUAGE_FORM_TYPES_BY_NAME = Collections.unmodifiableMap(new LinkedHashMap<>(LanguageFormType.getLanguageFormTypesByName()));
    Map<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> immutableSupportedLanguageFormNamesByType = new LinkedHashMap<>();

    for (Map.Entry<@NonNull LanguageFormType, @NonNull Set<@NonNull String>> entry : supportedLanguageFormNamesByType.entrySet())
      immutableSupportedLanguageFormNamesByType.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));

    SUPPORTED_LANGUAGE_FORM_NAMES_BY_TYPE = Collections.unmodifiableMap(immutableSupportedLanguageFormNamesByType);
    LANGUAGE_TAG_PATTERN = Pattern.compile("^[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*$");
    JSON_EXTENSION = ".json";
    UTF_8_BOM = '\uFEFF';
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
   * <li>{@code com/example/myapp/strings} (recommended to avoid collisions with dependencies)
   * </ul>
   * <p>
   * Note: this implementation only scans the specified package, it does not descend into child packages.
   * A trailing slash is optional and is normalized before lookup.
   * <p>
   * By default, discovery uses {@link ClassLoader#getResources(String)} and does not inspect unrelated classpath roots.
   * Use a {@link LocalizedStringLoadingOptions} overload with exhaustive classpath search enabled only for JARs that
   * omit package directory entries. A classpath {@code .json} resource whose filename is not a locale tag is ignored
   * with a warning; explicitly loaded filesystem catalog directories retain strict filename validation.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull String classpathPackage) {
    return loadFromClasspath(classpathPackage, LocalizedStringWarningHandler.log(), LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized string files from a classpath package using the specified loading and discovery options.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(
      @NonNull String classpathPackage, @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromClasspath(classpathPackage, LocalizedStringWarningHandler.log(), loadingOptions);
  }

  /**
   * Loads all localized string files present in the specified package, routing validation warnings to the given handler.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull String classpathPackage,
                                                                                               @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromClasspath(classpathPackage, warningHandler, LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized string files from a classpath package with validation-warning, loading, and discovery policies.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(
      @NonNull String classpathPackage, @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(classpathPackage);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    if (classLoader == null)
      classLoader = LocalizedStringLoader.class.getClassLoader();

    return loadFromClasspath(classLoader, classpathPackage, warningHandler, loadingOptions);
  }

  /**
   * Loads all localized string files present in the specified package using the specified classloader.
   * <p>
   * This is useful for containers, plugin systems, test harnesses, and other environments where the
   * desired localized string resources are not visible to Lokalized's own defining classloader.
   *
   * @param classLoader classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull ClassLoader classLoader,
                                                                                               @NonNull String classpathPackage) {
    return loadFromClasspath(classLoader, classpathPackage, LocalizedStringWarningHandler.log(),
        LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized string files using the specified classloader and loading/discovery options.
   *
   * @param classLoader      classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(
      @NonNull ClassLoader classLoader, @NonNull String classpathPackage,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromClasspath(classLoader, classpathPackage, LocalizedStringWarningHandler.log(), loadingOptions);
  }

  /**
   * Loads all localized string files present in the specified package using the specified classloader, routing
   * validation warnings to the given handler.
   *
   * @param classLoader      classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull ClassLoader classLoader,
                                                                                               @NonNull String classpathPackage,
                                                                                               @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromClasspath(classLoader, classpathPackage, warningHandler, LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized string files using the specified classloader, validation-warning policy, and loading/discovery
   * options.
   *
   * @param classLoader      classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(
      @NonNull ClassLoader classLoader, @NonNull String classpathPackage,
      @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(classpathPackage);
    requireNonNull(classLoader);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);
    classpathPackage = normalizeClasspathPackage(classpathPackage);
    validateClasspathPackage(classpathPackage);

    Enumeration<URL> urls;

    try {
      urls = classLoader.getResources(classpathPackage);
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to search classpath for '%s'", classpathPackage), e);
    }

    Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull SourceLocalizedString>> mergedByLocale = createSourceLocaleKeyMap();
    Set<@NonNull String> processedLocations = new LinkedHashSet<>();

    while (urls.hasMoreElements()) {
      URL url = urls.nextElement();

      if (!processedLocations.add(classpathLocationIdentity(url, classpathPackage)))
        continue;

      Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> localizedStringsByLocale =
          loadFromUrl(url, classpathPackage, warningHandler, loadingOptions);
      mergeLocalizedStrings(mergedByLocale, localizedStringsByLocale);
    }

    if (loadingOptions.isExhaustiveClasspathSearchEnabled()) {
      for (Path classpathRoot : classpathRootsFor(classLoader)) {
        if (Files.isDirectory(classpathRoot)) {
          Path packageDirectory = classpathRoot.resolve(classpathPackage);

          if (!Files.isDirectory(packageDirectory))
            continue;

          String locationIdentity = canonicalPathForPath(packageDirectory);

          if (!processedLocations.add(locationIdentity))
            continue;

          mergeLocalizedStrings(mergedByLocale,
              loadFromDirectoryWithOrigins(packageDirectory, warningHandler, loadingOptions));
          continue;
        }

        if (!Files.isRegularFile(classpathRoot))
          continue;

        String packagePath = normalizedJarPackagePath(classpathPackage);
        String locationIdentity = canonicalPathForPath(classpathRoot) + "!/" + packagePath;

        if (!processedLocations.add(locationIdentity))
          continue;

        try (JarFile jarFile = new JarFile(classpathRoot.toFile())) {
          Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> localizedStringsByLocale =
              loadFromJarFile(jarFile, packagePath, warningHandler, loadingOptions);

          if (localizedStringsByLocale.isEmpty()) {
            processedLocations.remove(locationIdentity);
            continue;
          }

          mergeLocalizedStrings(mergedByLocale, localizedStringsByLocale);
        } catch (ZipException e) {
          processedLocations.remove(locationIdentity);
        } catch (IOException e) {
          throw new LocalizedStringLoadingException(format(
              "Unable to load localized strings from classpath root '%s'", classpathRoot), e);
        }
      }
    }

    if (processedLocations.isEmpty())
      throw new LocalizedStringLoadingException(format("Unable to find package '%s' on the classpath", classpathPackage));

    return toLocalizedStringsByLocale(mergedByLocale);
  }

  @NonNull
  private static Set<@NonNull Path> classpathRootsFor(@NonNull ClassLoader classLoader) {
    requireNonNull(classLoader);

    Set<@NonNull Path> classpathRoots = new LinkedHashSet<>();
    boolean delegatesToSystemClassLoader = false;
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

    for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
      if (current == systemClassLoader)
        delegatesToSystemClassLoader = true;

      if (!(current instanceof URLClassLoader))
        continue;

      for (URL url : ((URLClassLoader) current).getURLs()) {
        if (!"file".equals(url.getProtocol()))
          continue;

        try {
          classpathRoots.add(Paths.get(url.toURI()).toAbsolutePath().normalize());
        } catch (URISyntaxException e) {
          throw new LocalizedStringLoadingException(format("Unable to resolve classpath root '%s'", url), e);
        }
      }
    }

    if (delegatesToSystemClassLoader) {
      String classpath = System.getProperty("java.class.path", "");

      for (String entry : classpath.split(Pattern.quote(File.pathSeparator)))
        if (!entry.isEmpty())
          classpathRoots.add(Paths.get(entry).toAbsolutePath().normalize());
    }

    return Collections.unmodifiableSet(classpathRoots);
  }

  @NonNull
  private static String classpathLocationIdentity(@NonNull URL url, @NonNull String classpathPackage) {
    requireNonNull(url);
    requireNonNull(classpathPackage);

    if ("file".equals(url.getProtocol())) {
      try {
        return canonicalPathForPath(Paths.get(url.toURI()));
      } catch (URISyntaxException e) {
        throw new LocalizedStringLoadingException(format("Unable to resolve classpath location '%s'", url), e);
      }
    }

    if ("jar".equals(url.getProtocol())) {
      try {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        URL jarFileUrl = connection.getJarFileURL();
        String jarIdentity = jarFileUrl.toExternalForm();

        if ("file".equals(jarFileUrl.getProtocol()))
          jarIdentity = canonicalPathForPath(Paths.get(jarFileUrl.toURI()));

        String entryName = connection.getEntryName();

        if (entryName == null || entryName.isEmpty())
          entryName = normalizedJarPackagePath(classpathPackage);

        return jarIdentity + "!/" + normalizedJarPackagePath(entryName);
      } catch (IOException | URISyntaxException e) {
        throw new LocalizedStringLoadingException(format("Unable to resolve classpath location '%s'", url), e);
      }
    }

    return url.toExternalForm();
  }

  @NonNull
  private static String normalizedJarPackagePath(@NonNull String classpathPackage) {
    requireNonNull(classpathPackage);

    String packagePath = classpathPackage;

    while (!packagePath.isEmpty() && packagePath.startsWith("/"))
      packagePath = packagePath.substring(1);

    while (!packagePath.isEmpty() && packagePath.endsWith("/"))
      packagePath = packagePath.substring(0, packagePath.length() - 1);

    return packagePath;
  }

  @NonNull
  private static String normalizeClasspathPackage(@NonNull String classpathPackage) {
    requireNonNull(classpathPackage);

    while (classpathPackage.length() > 1 && classpathPackage.endsWith("/"))
      classpathPackage = classpathPackage.substring(0, classpathPackage.length() - 1);

    return classpathPackage;
  }

  private static void validateClasspathPackage(@NonNull String classpathPackage) {
    requireNonNull(classpathPackage);

    if (classpathPackage.isEmpty() || classpathPackage.startsWith("/") ||
        classpathPackage.indexOf('\\') >= 0)
      throw new IllegalArgumentException(format(
          "Classpath package '%s' must be a nonempty slash-relative resource path", classpathPackage));

    for (String segment : classpathPackage.split("/", -1))
      if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment))
        throw new IllegalArgumentException(format(
            "Classpath package '%s' may not contain empty, '.' or '..' path segments", classpathPackage));

    Path packagePath = Paths.get(classpathPackage);

    if (packagePath.isAbsolute() || packagePath.normalize().startsWith(".."))
      throw new IllegalArgumentException(format(
          "Classpath package '%s' must remain beneath its classpath root", classpathPackage));
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
    return loadFromFilesystem(directory, LocalizedStringWarningHandler.log(), LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized string files from a directory using the specified resource limits.
   *
   * @param directory      directory in which to search, not null
   * @param loadingOptions resource limits to apply, not null
   * @return per-locale sets of localized strings, not null
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(
      @NonNull Path directory, @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromFilesystem(directory, LocalizedStringWarningHandler.log(), loadingOptions);
  }

  /**
   * Loads all localized string files present in the specified directory, routing validation warnings to the given handler.
   *
   * @param directory      directory in which to search for localized string files, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(@NonNull Path directory,
                                                                                                @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromFilesystem(directory, warningHandler, LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized string files from a directory with validation-warning and resource-limit policies.
   *
   * @param directory      directory in which to search, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @param loadingOptions resource limits to apply, not null
   * @return per-locale sets of localized strings, not null
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(
      @NonNull Path directory, @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(directory);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);
    return loadFromDirectory(directory, warningHandler, loadingOptions);
  }

  /**
   * Parses one localized strings file for the given locale.
   *
   * @param path   file to parse, not null
   * @param locale locale represented by the file, not null
   * @return localized strings contained in the file, not null
   * @throws LocalizedStringLoadingException if the file cannot be read or is invalid
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull Path path, @NonNull Locale locale) {
    return parse(path, locale, LocalizedStringWarningHandler.log(), LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Parses one localized strings file with validation-warning and resource-limit policies.
   *
   * @param path           file to parse, not null
   * @param locale         locale represented by the file, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @param loadingOptions resource limits to apply, not null
   * @return localized strings contained in the file, not null
   * @throws LocalizedStringLoadingException if the file cannot be read or is invalid
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull Path path, @NonNull Locale locale,
                                                    @NonNull LocalizedStringWarningHandler warningHandler,
                                                    @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(path);
    requireNonNull(locale);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);
    return parseLocalizedStringsFile(path, locale, warningHandler, loadingOptions);
  }

  /**
   * Parses one UTF-8 localized strings resource for the given locale. This method does not close the stream.
   *
   * @param inputStream UTF-8 resource contents, not null
   * @param locale      locale represented by the resource, not null
   * @param source      human-readable source identifier used in diagnostics, not null
   * @return localized strings contained in the resource, not null
   * @throws LocalizedStringLoadingException if the resource cannot be read or is invalid UTF-8/JSON
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull InputStream inputStream, @NonNull Locale locale,
                                                    @NonNull String source) {
    return parse(inputStream, locale, source, LocalizedStringWarningHandler.log(),
        LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Parses one UTF-8 localized strings resource with validation-warning and resource-limit policies.
   * This method does not close the stream.
   *
   * @param inputStream    UTF-8 resource contents, not null
   * @param locale         locale represented by the resource, not null
   * @param source         human-readable source identifier used in diagnostics, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @param loadingOptions resource limits to apply, not null
   * @return localized strings contained in the resource, not null
   * @throws LocalizedStringLoadingException if the resource cannot be read or is invalid UTF-8/JSON
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull InputStream inputStream, @NonNull Locale locale,
                                                    @NonNull String source,
                                                    @NonNull LocalizedStringWarningHandler warningHandler,
                                                    @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(inputStream);
    requireNonNull(locale);
    requireNonNull(source);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    String contents = readStrictUtf8(inputStream, source, loadingOptions);
    return parseLocalizedStrings(source, contents, locale, warningHandler, loadingOptions);
  }

  /**
   * Parses one localized strings character resource for the given locale. This method does not close the reader.
   *
   * @param reader character resource contents, not null
   * @param locale locale represented by the resource, not null
   * @param source human-readable source identifier used in diagnostics, not null
   * @return localized strings contained in the resource, not null
   * @throws LocalizedStringLoadingException if the resource cannot be read or is invalid
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull Reader reader, @NonNull Locale locale,
                                                    @NonNull String source) {
    return parse(reader, locale, source, LocalizedStringWarningHandler.log(), LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Parses one localized strings character resource with validation-warning and resource-limit policies.
   * This method does not close the reader.
   *
   * @param reader         character resource contents, not null
   * @param locale         locale represented by the resource, not null
   * @param source         human-readable source identifier used in diagnostics, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @param loadingOptions resource limits to apply, not null
   * @return localized strings contained in the resource, not null
   * @throws LocalizedStringLoadingException if the resource cannot be read or is invalid
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull Reader reader, @NonNull Locale locale,
                                                    @NonNull String source,
                                                    @NonNull LocalizedStringWarningHandler warningHandler,
                                                    @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(reader);
    requireNonNull(locale);
    requireNonNull(source);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    String contents = readCharacters(reader, source, loadingOptions);
    return parseLocalizedStrings(source, contents, locale, warningHandler, loadingOptions);
  }

  /**
   * Loads all localized string files present in the specified directory.
   *
   * @param directory directory in which to search for localized string files, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
   */
  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromDirectory(@NonNull Path directory,
                                                                                               @NonNull LocalizedStringWarningHandler warningHandler,
                                                                                               @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(directory);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    if (!Files.exists(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' does not exist",
          directory));

    if (!Files.isDirectory(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' exists but is not a directory",
          directory));

    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = createLocaleMap();

    try (DirectoryStream<@NonNull Path> directoryStream = Files.newDirectoryStream(directory)) {
      for (Path file : directoryStream) {
        if (Files.isDirectory(file))
          continue;

        @Nullable Path fileNamePath = file.getFileName();

        if (fileNamePath == null)
          continue;

        String fileName = fileNamePath.toString();

        if (isHiddenFileName(fileName)) {
          LOGGER.fine(format("File '%s' is hidden, skipping...", fileName));
          continue;
        }

        String languageTag = languageTagForFileName(fileName);

        if (languageTag != null) {
          LOGGER.fine(format("Loading localized strings file '%s'...", fileName));
          Locale locale = Locale.forLanguageTag(languageTag);

          if (localizedStringsByLocale.containsKey(locale))
            throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found at '%s'",
                locale.toLanguageTag(), file));

          localizedStringsByLocale.put(locale, parseLocalizedStringsFile(file, locale, warningHandler, loadingOptions));
        } else {
          LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", fileName));
        }
      }
    } catch (DirectoryIteratorException e) {
      throw new LocalizedStringLoadingException(format("Unable to list files in directory '%s'", directory), e.getCause());
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to list files in directory '%s'", directory), e);
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> loadFromDirectoryWithOrigins(@NonNull Path directory,
                                                                                                                @NonNull LocalizedStringWarningHandler warningHandler,
                                                                                                                @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(directory);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    if (!Files.exists(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' does not exist",
          directory));

    if (!Files.isDirectory(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' exists but is not a directory",
          directory));

    Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> localizedStringsByLocale = createSourceLocaleMap();
    Map<@NonNull Locale, @NonNull String> originByLocale = createLocaleOriginMap();

    try (DirectoryStream<@NonNull Path> directoryStream = Files.newDirectoryStream(directory)) {
      for (Path file : directoryStream) {
        if (Files.isDirectory(file))
          continue;

        @Nullable Path fileNamePath = file.getFileName();

        if (fileNamePath == null)
          continue;

        String fileName = fileNamePath.toString();

        if (isHiddenFileName(fileName)) {
          LOGGER.fine(format("File '%s' is hidden, skipping...", fileName));
          continue;
        }

        String canonicalPath = canonicalPathForPath(file);
        String languageTag = languageTagForClasspathFileName(fileName, canonicalPath, warningHandler);

        if (languageTag != null) {
          LOGGER.fine(format("Loading localized strings file '%s'...", fileName));
          Locale locale = Locale.forLanguageTag(languageTag);
          String existingOrigin = originByLocale.get(locale);

          if (existingOrigin != null)
            throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found in '%s' and '%s'",
                locale.toLanguageTag(), existingOrigin, canonicalPath));

          localizedStringsByLocale.put(locale, sourceLocalizedStrings(
              parseLocalizedStringsFile(file, locale, warningHandler, loadingOptions), canonicalPath));
          originByLocale.put(locale, canonicalPath);
        } else {
          LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", fileName));
        }
      }
    } catch (DirectoryIteratorException e) {
      throw new LocalizedStringLoadingException(format("Unable to list files in directory '%s'", directory), e.getCause());
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to list files in directory '%s'", directory), e);
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> loadFromUrl(@NonNull URL url, @NonNull String classpathPackage,
                                                                                               @NonNull LocalizedStringWarningHandler warningHandler,
                                                                                               @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(url);
    requireNonNull(classpathPackage);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    String protocol = url.getProtocol();

    if ("file".equals(protocol)) {
      try {
        return loadFromDirectoryWithOrigins(Paths.get(url.toURI()), warningHandler, loadingOptions);
      } catch (URISyntaxException e) {
        throw new LocalizedStringLoadingException(format("Unable to resolve classpath location '%s'", url), e);
      }
    }

    if ("jar".equals(protocol))
      return loadFromJar(url, classpathPackage, warningHandler, loadingOptions);

    throw new LocalizedStringLoadingException(format("Unsupported classpath protocol '%s' for location '%s'",
        protocol, url));
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> loadFromJar(@NonNull URL jarUrl,
                                                               @NonNull String classpathPackage,
                                                               @NonNull LocalizedStringWarningHandler warningHandler,
                                                               @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(jarUrl);
    requireNonNull(classpathPackage);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    try {
      JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
      connection.setUseCaches(false);

      try (JarFile jarFile = connection.getJarFile()) {
        String packagePath = connection.getEntryName();

        if (packagePath == null || packagePath.isEmpty())
          packagePath = classpathPackage;

        return loadFromJarFile(jarFile, packagePath, warningHandler, loadingOptions);
      }
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings from '%s'", jarUrl), e);
    }
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> loadFromJarFile(
      @NonNull JarFile jarFile, @NonNull String packagePath,
      @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) throws IOException {
    requireNonNull(jarFile);
    requireNonNull(packagePath);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> localizedStringsByLocale = createSourceLocaleMap();
    Map<@NonNull Locale, @NonNull String> originByLocale = createLocaleOriginMap();
    packagePath = normalizedJarPackagePath(packagePath) + "/";
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

      if (isHiddenFileName(relativeName)) {
        LOGGER.fine(format("File '%s' is hidden, skipping...", relativeName));
        continue;
      }

      String canonicalPath = format("jar:%s!/%s", jarFile.getName(), entryName);
      String languageTag = languageTagForClasspathFileName(relativeName, canonicalPath, warningHandler);

      if (languageTag == null) {
        LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", relativeName));
        continue;
      }

      LOGGER.fine(format("Loading localized strings file '%s' from %s...", relativeName, jarFile.getName()));
      Locale locale = Locale.forLanguageTag(languageTag);
      String existingOrigin = originByLocale.get(locale);

      if (existingOrigin != null)
        throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found in '%s' and '%s'",
            locale.toLanguageTag(), existingOrigin, canonicalPath));

      try (InputStream inputStream = jarFile.getInputStream(entry)) {
        Set<@NonNull LocalizedString> localizedStrings = parse(inputStream, locale, canonicalPath,
            warningHandler, loadingOptions);
        localizedStringsByLocale.put(locale, sourceLocalizedStrings(localizedStrings, canonicalPath));
        originByLocale.put(locale, canonicalPath);
      }
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> createLocaleMap() {
    return new TreeMap<>((locale1, locale2) -> locale1.toLanguageTag().compareTo(locale2.toLanguageTag()));
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> createSourceLocaleMap() {
    return new TreeMap<>((locale1, locale2) -> locale1.toLanguageTag().compareTo(locale2.toLanguageTag()));
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull String> createLocaleOriginMap() {
    return new TreeMap<>((locale1, locale2) -> locale1.toLanguageTag().compareTo(locale2.toLanguageTag()));
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull SourceLocalizedString>> createSourceLocaleKeyMap() {
    return new TreeMap<>((locale1, locale2) -> locale1.toLanguageTag().compareTo(locale2.toLanguageTag()));
  }

  private static void mergeLocalizedStrings(
      @NonNull Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull SourceLocalizedString>> target,
      @NonNull Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> source) {
    requireNonNull(target);
    requireNonNull(source);

    for (Map.Entry<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> entry : source.entrySet()) {
      Locale locale = entry.getKey();
      Map<@NonNull String, @NonNull SourceLocalizedString> localizedStringsByKey = target.get(locale);

      if (localizedStringsByKey == null) {
        localizedStringsByKey = new LinkedHashMap<>();
        target.put(locale, localizedStringsByKey);
      }

      for (SourceLocalizedString sourceLocalizedString : entry.getValue()) {
        LocalizedString localizedString = sourceLocalizedString.getLocalizedString();
        String key = localizedString.getKey();
        SourceLocalizedString existing = localizedStringsByKey.get(key);

        if (existing != null) {
          if (existing.getLocalizedString().equals(localizedString)) {
            LOGGER.fine(format("Ignoring equivalent localized string key '%s' for locale '%s' found in both '%s' and '%s'",
                key, locale.toLanguageTag(), existing.getOrigin(), sourceLocalizedString.getOrigin()));
            continue;
          }

          throw new LocalizedStringLoadingException(format("Duplicate localized string key '%s' found for locale '%s' while merging classpath resources. " +
                  "Conflicting resources are '%s' and '%s'", key, locale.toLanguageTag(), existing.getOrigin(), sourceLocalizedString.getOrigin()));
        }

        localizedStringsByKey.put(key, sourceLocalizedString);
      }
    }
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> toLocalizedStringsByLocale(
      @NonNull Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull SourceLocalizedString>> localizedStringsByKeyByLocale) {
    requireNonNull(localizedStringsByKeyByLocale);

    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = createLocaleMap();

    for (Map.Entry<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull SourceLocalizedString>> entry : localizedStringsByKeyByLocale.entrySet()) {
      Set<@NonNull LocalizedString> localizedStrings = new LinkedHashSet<>();

      for (SourceLocalizedString sourceLocalizedString : entry.getValue().values())
        localizedStrings.add(sourceLocalizedString.getLocalizedString());

      localizedStringsByLocale.put(entry.getKey(), Collections.unmodifiableSet(localizedStrings));
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  @NonNull
  private static Set<@NonNull SourceLocalizedString> sourceLocalizedStrings(@NonNull Set<@NonNull LocalizedString> localizedStrings,
                                                                            @NonNull String origin) {
    requireNonNull(localizedStrings);
    requireNonNull(origin);

    Set<@NonNull SourceLocalizedString> sourceLocalizedStrings = new LinkedHashSet<>();

    for (LocalizedString localizedString : localizedStrings)
      sourceLocalizedStrings.add(new SourceLocalizedString(localizedString, origin));

    return Collections.unmodifiableSet(sourceLocalizedStrings);
  }

  private static boolean isLanguageTag(@NonNull String languageTag) {
    requireNonNull(languageTag);

    if (!LANGUAGE_TAG_PATTERN.matcher(languageTag).matches())
      return false;

    Locale locale;

    try {
      locale = new Locale.Builder().setLanguageTag(languageTag).build();
    } catch (IllformedLocaleException e) {
      return false;
    }

    if (languageTag.toLowerCase(Locale.ROOT).startsWith("x-"))
      return true;

    if ("".equals(locale.getLanguage()))
      return false;

    return CldrLocaleData.isKnownLanguageTag(languageTag);
  }

  private static boolean hasJsonExtension(@NonNull String fileName) {
    requireNonNull(fileName);
    return fileName.toLowerCase(Locale.ROOT).endsWith(JSON_EXTENSION);
  }

  private static boolean isHiddenFileName(@NonNull String fileName) {
    requireNonNull(fileName);
    return fileName.startsWith(".");
  }

  @Nullable
  private static String languageTagForFileName(@NonNull String fileName) {
    requireNonNull(fileName);

    String languageTag = fileName;
    boolean hasJsonExtension = hasJsonExtension(fileName);

    if (hasJsonExtension)
      languageTag = fileName.substring(0, fileName.length() - JSON_EXTENSION.length());

    if (isLanguageTag(languageTag))
      return languageTag;

    if (hasJsonExtension)
      throw new LocalizedStringLoadingException(format("File '%s' ends with %s but is not named with a valid IETF BCP 47 language tag. " +
          "Use names like 'en', 'en.json', or 'en-US.json'", fileName, JSON_EXTENSION));

    return null;
  }

  @Nullable
  private static String languageTagForClasspathFileName(@NonNull String fileName,
                                                        @NonNull String source,
                                                        @NonNull LocalizedStringWarningHandler warningHandler) {
    requireNonNull(fileName);
    requireNonNull(source);
    requireNonNull(warningHandler);

    try {
      return languageTagForFileName(fileName);
    } catch (LocalizedStringLoadingException e) {
      warningHandler.handle(new LocalizedStringWarning(
          LocalizedStringWarning.Type.INVALID_CLASSPATH_LOCALE_FILENAME,
          source,
          format("Ignoring classpath resource '%s': %s", source, e.getMessage())));
      return null;
    }
  }

  /**
   * Parses out a set of localized strings from the given path.
   *
   * @param path the path to parse, not null
   * @param locale the locale represented by the file, not null
   * @return the set of localized strings contained in the file, not null
   * @throws LocalizedStringLoadingException if an error occurs while parsing the localized string file
   */
  @NonNull
  private static Set<@NonNull LocalizedString> parseLocalizedStringsFile(@NonNull Path path, @NonNull Locale locale,
                                                                         @NonNull LocalizedStringWarningHandler warningHandler,
                                                                         @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(path);
    requireNonNull(locale);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    String canonicalPath = canonicalPathForPath(path);

    if (!Files.isRegularFile(path))
      throw new LocalizedStringLoadingException(format("%s is not a regular file", canonicalPath));

    try (InputStream inputStream = Files.newInputStream(path)) {
      return parse(inputStream, locale, canonicalPath, warningHandler, loadingOptions);
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings file contents for %s",
          canonicalPath), e);
    }
  }

  @NonNull
  private static String readStrictUtf8(@NonNull InputStream inputStream, @NonNull String source,
                                       @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(inputStream);
    requireNonNull(source);
    requireNonNull(loadingOptions);

    int maximumInputBytes = loadingOptions.getMaximumInputBytes();
    byte[] bytes;

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(Math.min(maximumInputBytes, 8192))) {
      byte[] buffer = new byte[Math.min(maximumInputBytes + 1, 8192)];
      int maximumBytesToRead = maximumInputBytes + 1;

      while (outputStream.size() < maximumBytesToRead) {
        int bytesRead = inputStream.read(buffer, 0,
            Math.min(buffer.length, maximumBytesToRead - outputStream.size()));

        if (bytesRead == -1)
          break;

        if (bytesRead == 0)
          continue;

        outputStream.write(buffer, 0, bytesRead);
      }

      bytes = outputStream.toByteArray();
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings resource contents for %s", source), e);
    }

    if (bytes.length > maximumInputBytes)
      throw new LocalizedStringLoadingException(format(
          "%s: localized strings resource exceeds the maximum size of %d bytes", source, maximumInputBytes));

    try {
      return normalizeLocalizedStringsFileContents(UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes)).toString());
    } catch (CharacterCodingException e) {
      throw new LocalizedStringLoadingException(format("%s: localized strings resource is not valid UTF-8", source), e);
    }
  }

  @NonNull
  private static String readCharacters(@NonNull Reader reader, @NonNull String source,
                                       @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(reader);
    requireNonNull(source);
    requireNonNull(loadingOptions);

    int maximumCharacters = loadingOptions.getMaximumReaderCharacters();
    char[] buffer = new char[Math.min(8192, maximumCharacters)];
    StringBuilder contents = new StringBuilder(Math.min(maximumCharacters, 8192));

    try {
      int charactersRead;

      while ((charactersRead = reader.read(buffer)) != -1) {
        if (charactersRead == 0) {
          int character = reader.read();

          if (character == -1)
            break;

          if (contents.length() == maximumCharacters)
            throw new LocalizedStringLoadingException(format(
                "%s: localized strings resource exceeds the maximum size of %d characters", source, maximumCharacters));

          contents.append((char) character);
          continue;
        }

        if (contents.length() > maximumCharacters - charactersRead)
          throw new LocalizedStringLoadingException(format(
              "%s: localized strings resource exceeds the maximum size of %d characters", source, maximumCharacters));

        contents.append(buffer, 0, charactersRead);
      }
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings resource contents for %s", source), e);
    }

    return normalizeLocalizedStringsFileContents(contents.toString());
  }

  @NonNull
  private static String canonicalPathForPath(@NonNull Path path) {
    requireNonNull(path);

    try {
      return path.toRealPath().toString();
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(
          format("Unable to determine canonical path for localized strings file %s", path), e);
    }
  }

  @NonNull
  private static Set<@NonNull LocalizedString> parseLocalizedStrings(@NonNull String canonicalPath,
                                                                     @NonNull String localizedStringsFileContents,
                                                                     @NonNull Locale locale,
                                                                     @NonNull LocalizedStringWarningHandler warningHandler,
                                                                     @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(canonicalPath);
    requireNonNull(localizedStringsFileContents);
    requireNonNull(locale);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    if (isJsonWhitespaceOnly(localizedStringsFileContents))
      throw new LocalizedStringLoadingException(format(
          "%s: a localized strings file may not be blank; use an empty JSON object ({}) for an empty catalog", canonicalPath));

    validateJsonNestingDepth(canonicalPath, localizedStringsFileContents,
        loadingOptions.getMaximumJsonNestingDepth());

    Set<@NonNull LocalizedString> localizedStrings = new HashSet<>();
    JsonValue outerJsonValue;

    try {
      outerJsonValue = Json.parse(localizedStringsFileContents);
    } catch (MinimalJson.ParseException e) {
      throw new LocalizedStringLoadingException(format("%s:%d:%d: unable to parse localized strings file",
          canonicalPath, e.getLocation().line, e.getLocation().column), e);
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
      validateNoDuplicateObjectMembers(canonicalPath, value, jsonObjectMemberPath("$", key));
      LocalizedString localizedString = parseLocalizedString(canonicalPath, key, value, null);

      try {
        LocalizedStringValidator.validate(locale, localizedString);
      } catch (IllegalArgumentException e) {
        throw new LocalizedStringLoadingException(format(
            "%s: semantic validation failed for localized string key '%s'", canonicalPath, key), e);
      }

      warnOnIncompleteLanguageFormTranslations(canonicalPath, locale, key, localizedString, warningHandler);
      localizedStrings.add(localizedString);
    }

    return Collections.unmodifiableSet(localizedStrings);
  }

  /**
   * Emits a validation warning if a cardinality- or ordinality-driven placeholder omits a language form that its
   * locale requires per CLDR (for example, a Russian file that provides {@code CARDINALITY_ONE}/{@code FEW}/
   * {@code OTHER} but omits {@code CARDINALITY_MANY}).
   * <p>
   * This is intentionally a warning rather than a hard failure: a translation whose placeholder can only ever
   * receive a subset of values may legitimately supply a subset of forms. Selector-driven and range-driven
   * translations are not checked because they are expected to be partial by design. Values that resolve to a
   * missing form are treated as resolution failures at runtime according to the configured
   * {@link TranslationFailureHandler}, so surfacing the gap here turns a silent runtime degradation into a visible
   * validation signal.
   *
   * @param canonicalPath   the unique path to the file (or URL) being parsed, used for reporting, not null
   * @param locale          the locale the file is being loaded for, not null
   * @param rootKey         root translation key used to identify warnings for nested alternatives, not null
   * @param localizedString the parsed localized string to inspect (recursively, including alternatives), not null
   * @param warningHandler  handler to receive any warnings, not null
   */
  private static void warnOnIncompleteLanguageFormTranslations(@NonNull String canonicalPath,
                                                               @NonNull Locale locale,
                                                               @NonNull String rootKey,
                                                               @NonNull LocalizedString localizedString,
                                                               @NonNull LocalizedStringWarningHandler warningHandler) {
    requireNonNull(canonicalPath);
    requireNonNull(locale);
    requireNonNull(rootKey);
    requireNonNull(localizedString);
    requireNonNull(warningHandler);

    for (Map.Entry<@NonNull String, @NonNull LanguageFormTranslation> entry :
        localizedString.getLanguageFormTranslationsByPlaceholder().entrySet()) {
      String placeholderKey = entry.getKey();
      LanguageFormTranslation languageFormTranslation = entry.getValue();

      // Selector-driven and range-driven translations legitimately supply a subset of forms; do not check them.
      if (languageFormTranslation.isSelectorDriven() || languageFormTranslation.getRange().isPresent())
        continue;

      warnOnIncompleteCardinalityTranslations(canonicalPath, locale, rootKey, placeholderKey,
          languageFormTranslation, warningHandler);
      warnOnIncompleteOrdinalityTranslations(canonicalPath, locale, rootKey, placeholderKey,
          languageFormTranslation, warningHandler);
    }

    for (LocalizedString alternative : localizedString.getAlternatives())
      warnOnIncompleteLanguageFormTranslations(canonicalPath, locale, rootKey, alternative, warningHandler);
  }

  private static void warnOnIncompleteCardinalityTranslations(@NonNull String canonicalPath,
                                                              @NonNull Locale locale,
                                                              @NonNull String rootKey,
                                                              @NonNull String placeholderKey,
                                                              @NonNull LanguageFormTranslation languageFormTranslation,
                                                              @NonNull LocalizedStringWarningHandler warningHandler) {
    Set<@NonNull Cardinality> providedCardinalities = new TreeSet<>();

    for (LanguageForm languageForm : languageFormTranslation.getTranslationsByLanguageForm().keySet())
      if (languageForm instanceof Cardinality)
        providedCardinalities.add((Cardinality) languageForm);

    // An empty set means this placeholder is not cardinality-driven; nothing to check.
    if (providedCardinalities.isEmpty())
      return;

    Set<@NonNull Cardinality> supportedCardinalities = new TreeSet<>(Cardinality.supportedCardinalitiesForLocale(locale));

    if (supportedCardinalities.isEmpty())
      return;

    Set<@NonNull Cardinality> missingCardinalities = new TreeSet<>(supportedCardinalities);
    missingCardinalities.removeAll(providedCardinalities);

    if (missingCardinalities.isEmpty())
      return;

    Set<@NonNull String> missingLanguageForms = new LinkedHashSet<>();

    for (Cardinality missingCardinality : missingCardinalities)
      missingLanguageForms.add(LocalizedStringUtils.localizedStringNameForCardinalityName(missingCardinality.name()));

    String message = format("%s: placeholder '%s' for key '%s' is missing %s translation[s] for locale '%s': [%s]. " +
            "Supported forms are [%s]. Values that resolve to a missing form are treated as resolution failures at runtime.",
        canonicalPath, placeholderKey, rootKey, Cardinality.class.getSimpleName(),
        locale.toLanguageTag(), cardinalityNamesFor(missingCardinalities), cardinalityNamesFor(supportedCardinalities));

    warningHandler.handle(new LocalizedStringWarning(
        LocalizedStringWarning.Type.INCOMPLETE_CARDINALITY_TRANSLATIONS, canonicalPath, locale,
        rootKey, placeholderKey, missingLanguageForms, message));
  }

  private static void warnOnIncompleteOrdinalityTranslations(@NonNull String canonicalPath,
                                                             @NonNull Locale locale,
                                                             @NonNull String rootKey,
                                                             @NonNull String placeholderKey,
                                                             @NonNull LanguageFormTranslation languageFormTranslation,
                                                             @NonNull LocalizedStringWarningHandler warningHandler) {
    Set<@NonNull Ordinality> providedOrdinalities = new TreeSet<>();

    for (LanguageForm languageForm : languageFormTranslation.getTranslationsByLanguageForm().keySet())
      if (languageForm instanceof Ordinality)
        providedOrdinalities.add((Ordinality) languageForm);

    // An empty set means this placeholder is not ordinality-driven; nothing to check.
    if (providedOrdinalities.isEmpty())
      return;

    Set<@NonNull Ordinality> supportedOrdinalities = new TreeSet<>(Ordinality.supportedOrdinalitiesForLocale(locale));

    if (supportedOrdinalities.isEmpty())
      return;

    Set<@NonNull Ordinality> missingOrdinalities = new TreeSet<>(supportedOrdinalities);
    missingOrdinalities.removeAll(providedOrdinalities);

    if (missingOrdinalities.isEmpty())
      return;

    Set<@NonNull String> missingLanguageForms = new LinkedHashSet<>();

    for (Ordinality missingOrdinality : missingOrdinalities)
      missingLanguageForms.add(LocalizedStringUtils.localizedStringNameForOrdinalityName(missingOrdinality.name()));

    String message = format("%s: placeholder '%s' for key '%s' is missing %s translation[s] for locale '%s': [%s]. " +
            "Supported forms are [%s]. Values that resolve to a missing form are treated as resolution failures at runtime.",
        canonicalPath, placeholderKey, rootKey, Ordinality.class.getSimpleName(),
        locale.toLanguageTag(), ordinalityNamesFor(missingOrdinalities), ordinalityNamesFor(supportedOrdinalities));

    warningHandler.handle(new LocalizedStringWarning(
        LocalizedStringWarning.Type.INCOMPLETE_ORDINALITY_TRANSLATIONS, canonicalPath, locale,
        rootKey, placeholderKey, missingLanguageForms, message));
  }

  @NonNull
  private static String cardinalityNamesFor(@NonNull Set<@NonNull Cardinality> cardinalities) {
    requireNonNull(cardinalities);
    return cardinalities.stream()
        .map(cardinality -> LocalizedStringUtils.localizedStringNameForCardinalityName(cardinality.name()))
        .collect(Collectors.joining(", "));
  }

  @NonNull
  private static String ordinalityNamesFor(@NonNull Set<@NonNull Ordinality> ordinalities) {
    requireNonNull(ordinalities);
    return ordinalities.stream()
        .map(ordinality -> LocalizedStringUtils.localizedStringNameForOrdinalityName(ordinality.name()))
        .collect(Collectors.joining(", "));
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

      validateTranslationPlaceholders(canonicalPath, key, translation, Collections.emptyMap(), Collections.emptyMap());
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
      //           "CARDINALITY_ONE" : "book",
      //           "CARDINALITY_OTHER" : "books"
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
      validateNoUnexpectedObjectMembers(canonicalPath, key, localizedStringObject, "localized string",
          Set.of("translation", "commentary", "placeholderMetadata", "placeholders", "alternatives"));

      String translation = null;

      JsonValue translationJsonValue = localizedStringObject.get("translation");

      if (translationJsonValue != null) {
        if (!translationJsonValue.isString())
          throw new LocalizedStringLoadingException(format("%s: translation must be a string for key '%s'", canonicalPath, key));

        translation = translationJsonValue.asString();
      }

      String commentary = null;

      JsonValue commentaryJsonValue = localizedStringObject.get("commentary");

      if (commentaryJsonValue != null) {
        if (!commentaryJsonValue.isString())
          throw new LocalizedStringLoadingException(format("%s: commentary must be a string for key '%s'", canonicalPath, key));

        commentary = commentaryJsonValue.asString();
      }

      Map<@NonNull String, @NonNull PlaceholderMetadata> placeholderMetadataByPlaceholder = new LinkedHashMap<>();

      JsonValue placeholderMetadataJsonValue = localizedStringObject.get("placeholderMetadata");

      if (placeholderMetadataJsonValue != null) {
        if (!placeholderMetadataJsonValue.isObject())
          throw new LocalizedStringLoadingException(format("%s: the placeholderMetadata value must be an object. Key is '%s'", canonicalPath, key));

        JsonObject placeholderMetadataJsonObject = placeholderMetadataJsonValue.asObject();

        for (Member placeholderMetadataMember : placeholderMetadataJsonObject) {
          String placeholderKey = placeholderMetadataMember.getName();
          JsonValue placeholderMetadataValue = placeholderMetadataMember.getValue();

          ensureValidPlaceholderName(canonicalPath, key, placeholderKey, "placeholder metadata");

          if (!placeholderMetadataValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: placeholder metadata must be an object. Key is '%s'", canonicalPath, key));

          JsonObject placeholderMetadataObject = placeholderMetadataValue.asObject();
          validateNoUnexpectedObjectMembers(canonicalPath, key, placeholderMetadataObject,
              format("placeholder metadata '%s'", placeholderKey), Set.of("type", "commentary", "example", "allowedValues"));
          JsonValue typeJsonValue = placeholderMetadataObject.get("type");
          JsonValue commentaryJsonValueForPlaceholder = placeholderMetadataObject.get("commentary");
          JsonValue exampleJsonValue = placeholderMetadataObject.get("example");
          JsonValue allowedValuesJsonValue = placeholderMetadataObject.get("allowedValues");
          String type = null;
          String placeholderCommentary = null;
          String example = null;
          Set<@NonNull String> allowedValues = new LinkedHashSet<>();

          if (typeJsonValue != null) {
            if (!typeJsonValue.isString())
              throw new LocalizedStringLoadingException(format("%s: placeholder metadata type must be a string. Placeholder is '%s' for key '%s'",
                  canonicalPath, placeholderKey, key));

            type = typeJsonValue.asString();
          }

          if (commentaryJsonValueForPlaceholder != null) {
            if (!commentaryJsonValueForPlaceholder.isString())
              throw new LocalizedStringLoadingException(format("%s: placeholder metadata commentary must be a string. Placeholder is '%s' for key '%s'",
                  canonicalPath, placeholderKey, key));

            placeholderCommentary = commentaryJsonValueForPlaceholder.asString();
          }

          if (exampleJsonValue != null) {
            if (!exampleJsonValue.isString())
              throw new LocalizedStringLoadingException(format("%s: placeholder metadata example must be a string. Placeholder is '%s' for key '%s'",
                  canonicalPath, placeholderKey, key));

            example = exampleJsonValue.asString();
          }

          if (allowedValuesJsonValue != null) {
            if (!allowedValuesJsonValue.isArray())
              throw new LocalizedStringLoadingException(format("%s: placeholder metadata allowedValues must be an array. Placeholder is '%s' for key '%s'",
                  canonicalPath, placeholderKey, key));

            for (JsonValue allowedValueJsonValue : allowedValuesJsonValue.asArray()) {
              if (allowedValueJsonValue == null || allowedValueJsonValue.isNull() || !allowedValueJsonValue.isString())
                throw new LocalizedStringLoadingException(format("%s: placeholder metadata allowedValues entries must be strings. Placeholder is '%s' for key '%s'",
                    canonicalPath, placeholderKey, key));

              String allowedValue = allowedValueJsonValue.asString();

              if (!allowedValues.add(allowedValue))
                throw new LocalizedStringLoadingException(format("%s: placeholder metadata allowedValues may not contain duplicates. " +
                    "Duplicate value '%s' encountered for placeholder '%s' in key '%s'", canonicalPath, allowedValue, placeholderKey, key));
            }
          }

          if (type != null) {
            LanguageFormType languageFormType = SUPPORTED_LANGUAGE_FORM_TYPES_BY_NAME.get(type);

            if (languageFormType != null) {
              Set<@NonNull String> supportedLanguageFormsForType = SUPPORTED_LANGUAGE_FORM_NAMES_BY_TYPE.get(languageFormType);

              for (String allowedValue : allowedValues) {
                if (!supportedLanguageFormsForType.contains(allowedValue))
                  throw new LocalizedStringLoadingException(format("%s: placeholder metadata allowed value '%s' is invalid for type '%s'. " +
                          "Placeholder is '%s' for key '%s', valid values are [%s]", canonicalPath, allowedValue, type, placeholderKey, key,
                      supportedLanguageFormsForType.stream().collect(Collectors.joining(", "))));
              }
            }
          }

          if (type == null && placeholderCommentary == null && example == null && allowedValues.isEmpty())
            throw new LocalizedStringLoadingException(format("%s: placeholder metadata must define at least one field. Placeholder is '%s' for key '%s'",
                canonicalPath, placeholderKey, key));

          placeholderMetadataByPlaceholder.put(placeholderKey,
              new PlaceholderMetadata(type, placeholderCommentary, example, allowedValues));
        }
      }

      Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder = new LinkedHashMap<>();

      JsonValue placeholdersJsonValue = localizedStringObject.get("placeholders");

      if (placeholdersJsonValue != null) {
        if (!placeholdersJsonValue.isObject())
          throw new LocalizedStringLoadingException(format("%s: the placeholders value must be an object. Key is '%s'", canonicalPath, key));

        JsonObject placeholdersJsonObject = placeholdersJsonValue.asObject();

        for (Member placeholderMember : placeholdersJsonObject) {
          String placeholderKey = placeholderMember.getName();
          JsonValue placeholderJsonValue = placeholderMember.getValue();

          ensureValidPlaceholderName(canonicalPath, key, placeholderKey, "placeholder");

          if (!placeholderJsonValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: the placeholder value must be an object. Key is '%s'", canonicalPath, key));

          JsonObject placeholderJsonObject = placeholderJsonValue.asObject();
          LanguageFormTranslation languageFormTranslation = parseLanguageFormTranslation(canonicalPath, key, placeholderKey, placeholderJsonObject);
          languageFormTranslationsByPlaceholder.put(placeholderKey, languageFormTranslation);
        }
      }

      List<@NonNull LocalizedString> alternatives = new ArrayList<>();

      JsonValue alternativesJsonValue = localizedStringObject.get("alternatives");

      if (alternativesJsonValue != null) {
        if (!alternativesJsonValue.isArray())
          throw new LocalizedStringLoadingException(format("%s: alternatives must be an array. Key is '%s'", canonicalPath, key));

        JsonArray alternativesJsonArray = alternativesJsonValue.asArray();

        if (alternativesJsonArray.isEmpty())
          throw new LocalizedStringLoadingException(format("%s: alternatives must contain at least one expression. Key is '%s'",
              canonicalPath, key));

        for (JsonValue alternativeJsonValue : alternativesJsonArray) {
          if (alternativeJsonValue == null || alternativeJsonValue.isNull())
            throw new LocalizedStringLoadingException(format("%s: alternative values cannot be null. Key is '%s'",
                canonicalPath, key));

          if (!alternativeJsonValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: alternative value must be an object. Key is '%s'", canonicalPath, key));

          JsonObject outerJsonObject = alternativeJsonValue.asObject();

          if (outerJsonObject.isEmpty())
            throw new LocalizedStringLoadingException(format("%s: alternative objects must contain at least one expression. Key is '%s'",
                canonicalPath, key));

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

      if (translation != null)
        validateTranslationPlaceholders(canonicalPath, key, translation, placeholderMetadataByPlaceholder,
            languageFormTranslationsByPlaceholder);

      return localizedStringBuilder.translation(translation)
          .commentary(commentary)
          .placeholderMetadataByPlaceholder(placeholderMetadataByPlaceholder)
          .languageFormTranslationsByPlaceholder(languageFormTranslationsByPlaceholder)
          .alternatives(alternatives)
          .build();
    } else {
      throw new LocalizedStringLoadingException(format("%s: either a translation string or object value is required for key '%s'",
          canonicalPath, key));
    }
  }

  private static void validateTranslationPlaceholders(@NonNull String canonicalPath,
                                                      @NonNull String key,
                                                      @NonNull String translation,
                                                      @NonNull Map<@NonNull String, @NonNull PlaceholderMetadata> placeholderMetadataByPlaceholder,
                                                      @NonNull Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(translation);
    requireNonNull(placeholderMetadataByPlaceholder);
    requireNonNull(languageFormTranslationsByPlaceholder);

    Set<@NonNull String> referencedPlaceholderNames =
        validatePlaceholderReferences(canonicalPath, key, translation, "translation");

    Set<@NonNull String> languageFormTranslationInputPlaceholderNames =
        languageFormTranslationInputPlaceholderNames(languageFormTranslationsByPlaceholder);

    for (String placeholderName : languageFormTranslationsByPlaceholder.keySet())
      if (!referencedPlaceholderNames.contains(placeholderName))
        LOGGER.fine(format("%s: placeholder '%s' is declared for key '%s' but is not referenced by its translation",
            canonicalPath, placeholderName, key));

    for (String placeholderName : placeholderMetadataByPlaceholder.keySet())
      if (!referencedPlaceholderNames.contains(placeholderName) &&
          !languageFormTranslationInputPlaceholderNames.contains(placeholderName))
        LOGGER.fine(format("%s: placeholder metadata for '%s' is declared for key '%s' but is not referenced by its translation or placeholder rules",
            canonicalPath, placeholderName, key));
  }

  @NonNull
  private static Set<@NonNull String> validatePlaceholderReferences(@NonNull String canonicalPath,
                                                                    @NonNull String key,
                                                                    @NonNull String translation,
                                                                    @NonNull String description) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(translation);
    requireNonNull(description);

    Set<@NonNull String> referencedPlaceholderNames;

    try {
      referencedPlaceholderNames = StringInterpolator.placeholderNamesIn(translation);
    } catch (IllegalArgumentException e) {
      throw new LocalizedStringLoadingException(format("%s: invalid placeholder reference in %s for key '%s': %s",
          canonicalPath, description, key, e.getMessage()), e);
    }

    for (String placeholderName : referencedPlaceholderNames)
      ensureValidPlaceholderName(canonicalPath, key, placeholderName, description + " placeholder reference");

    return referencedPlaceholderNames;
  }

  @NonNull
  private static Set<@NonNull String> languageFormTranslationInputPlaceholderNames(
      @NonNull Map<@NonNull String, @NonNull LanguageFormTranslation> languageFormTranslationsByPlaceholder) {
    requireNonNull(languageFormTranslationsByPlaceholder);

    Set<@NonNull String> placeholderNames = new LinkedHashSet<>();

    for (LanguageFormTranslation languageFormTranslation : languageFormTranslationsByPlaceholder.values()) {
      languageFormTranslation.getValue().ifPresent(placeholderNames::add);
      languageFormTranslation.getRange().ifPresent(range -> {
        placeholderNames.add(range.getStart());
        placeholderNames.add(range.getEnd());
      });

      for (LanguageFormSelector selector : languageFormTranslation.getSelectors())
        placeholderNames.add(selector.getValue());
    }

    return Collections.unmodifiableSet(placeholderNames);
  }

  @NonNull
  private static List<@NonNull Token> parseExpressionTokens(@NonNull String canonicalPath, @NonNull String expression) {
    requireNonNull(canonicalPath);
    requireNonNull(expression);

    try {
      return EXPRESSION_EVALUATOR.parseAndValidateExpressionTokens(expression);
    } catch (ExpressionEvaluationException e) {
      throw new LocalizedStringLoadingException(
          format("%s: unable to parse alternative expression '%s': %s", canonicalPath, expression, e.getMessage()), e);
    }
  }

  @NonNull
  private static LanguageFormTranslation parseLanguageFormTranslation(@NonNull String canonicalPath, @NonNull String key,
                                                                      @NonNull String placeholderKey, @NonNull JsonObject placeholderJsonObject) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderKey);
    requireNonNull(placeholderJsonObject);

    JsonValue valueJsonValue = placeholderJsonObject.get("value");
    JsonValue rangeJsonValue = placeholderJsonObject.get("range");
    JsonValue selectorsJsonValue = placeholderJsonObject.get("selectors");
    JsonValue translationsJsonValue = placeholderJsonObject.get("translations");
    validateNoUnexpectedObjectMembers(canonicalPath, key, placeholderJsonObject,
        format("placeholder '%s'", placeholderKey), Set.of("value", "range", "selectors", "translations"));
    rejectExplicitNullPlaceholderMode(canonicalPath, key, placeholderKey, "value", valueJsonValue);
    rejectExplicitNullPlaceholderMode(canonicalPath, key, placeholderKey, "range", rangeJsonValue);
    rejectExplicitNullPlaceholderMode(canonicalPath, key, placeholderKey, "selectors", selectorsJsonValue);
    boolean hasValue = valueJsonValue != null;
    boolean hasRangeValue = rangeJsonValue != null;
    boolean hasSelectors = selectorsJsonValue != null;

    if (!hasValue && !hasRangeValue && !hasSelectors)
      throw new LocalizedStringLoadingException(format("%s: a placeholder translation value, range, or selectors block is required. Key is '%s'",
          canonicalPath, key));

    if (hasSelectors) {
      if (hasValue || hasRangeValue)
        throw new LocalizedStringLoadingException(format("%s: selector-based placeholder translations cannot define value or range. " +
            "Placeholder is '%s' for key '%s'", canonicalPath, placeholderKey, key));

      return parseSelectorDrivenLanguageFormTranslation(canonicalPath, key, placeholderKey, selectorsJsonValue, translationsJsonValue);
    }

    if (hasValue && hasRangeValue)
      throw new LocalizedStringLoadingException(format("%s: a placeholder translation cannot have both a value and a range. Key is '%s'", canonicalPath, key));

    return parseSingleAxisLanguageFormTranslation(canonicalPath, key, placeholderKey, valueJsonValue, rangeJsonValue, translationsJsonValue);
  }

  private static void rejectExplicitNullPlaceholderMode(@NonNull String canonicalPath, @NonNull String key,
                                                        @NonNull String placeholderKey, @NonNull String memberName,
                                                        @Nullable JsonValue memberValue) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderKey);
    requireNonNull(memberName);

    if (memberValue != null && memberValue.isNull())
      throw new LocalizedStringLoadingException(format(
          "%s: placeholder member '%s' may not be null. Placeholder is '%s' for key '%s'",
          canonicalPath, memberName, placeholderKey, key));
  }

  @NonNull
  private static LanguageFormTranslation parseSingleAxisLanguageFormTranslation(@NonNull String canonicalPath, @NonNull String key,
                                                                                @NonNull String placeholderKey, @Nullable JsonValue valueJsonValue,
                                                                                @Nullable JsonValue rangeJsonValue,
                                                                                @Nullable JsonValue translationsJsonValue) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderKey);

    boolean hasValue = valueJsonValue != null && !valueJsonValue.isNull();
    boolean hasRangeValue = rangeJsonValue != null && !rangeJsonValue.isNull();
    LanguageFormTranslationRange rangeValue = null;
    String value = null;

    if (hasRangeValue) {
      if (!rangeJsonValue.isObject())
        throw new LocalizedStringLoadingException(format("%s: the placeholder translation range must be an object. Key is '%s'", canonicalPath, key));

      JsonObject rangeJsonObject = rangeJsonValue.asObject();
      validateNoUnexpectedObjectMembers(canonicalPath, key, rangeJsonObject,
          format("range for placeholder '%s'", placeholderKey), Set.of("start", "end"));
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
      if (!hasValue)
        throw new LocalizedStringLoadingException(format("%s: a placeholder translation value or range is required. Key is '%s'", canonicalPath, key));

      if (!valueJsonValue.isString())
        throw new LocalizedStringLoadingException(format("%s: a placeholder translation value must be a string. Key is '%s'", canonicalPath, key));

      value = valueJsonValue.asString();
      ensureValidPlaceholderName(canonicalPath, key, value, "placeholder value");
    }

    if (translationsJsonValue == null || translationsJsonValue.isNull())
      throw new LocalizedStringLoadingException(format("%s: placeholder translations are required. Key is '%s'", canonicalPath, key));

    if (!translationsJsonValue.isObject())
      throw new LocalizedStringLoadingException(format("%s: the placeholder translations value must be an object. Key is '%s'", canonicalPath, key));

    Map<@NonNull LanguageForm, @NonNull String> translationsByLanguageForm = new LinkedHashMap<>();

    for (Member translationMember : translationsJsonValue.asObject()) {
      String languageFormTranslationKey = translationMember.getName();
      JsonValue languageFormTranslationJsonValue = translationMember.getValue();
      LanguageForm languageForm = SUPPORTED_LANGUAGE_FORMS_BY_NAME.get(languageFormTranslationKey);

      if (languageForm == null)
        throw new LocalizedStringLoadingException(format("%s: unexpected placeholder translation language form encountered. Key is '%s'. " +
                "You provided '%s', valid values are [%s]", canonicalPath, key, languageFormTranslationKey,
            SUPPORTED_LANGUAGE_FORMS_BY_NAME.keySet().stream().collect(Collectors.joining(", "))));

      if (!languageFormTranslationJsonValue.isString())
        throw new LocalizedStringLoadingException(format("%s: the placeholder translation value must be a string. Key is '%s'", canonicalPath, key));

      String languageFormTranslation = languageFormTranslationJsonValue.asString();
      validatePlaceholderReferences(canonicalPath, key, languageFormTranslation, "placeholder translation");
      translationsByLanguageForm.put(languageForm, languageFormTranslation);
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

    return rangeValue != null
        ? new LanguageFormTranslation(rangeValue, translationsByLanguageForm)
        : new LanguageFormTranslation(value, translationsByLanguageForm);
  }

  @NonNull
  private static LanguageFormTranslation parseSelectorDrivenLanguageFormTranslation(@NonNull String canonicalPath, @NonNull String key,
                                                                                    @NonNull String placeholderKey, @NonNull JsonValue selectorsJsonValue,
                                                                                    @Nullable JsonValue translationsJsonValue) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderKey);
    requireNonNull(selectorsJsonValue);

    if (!selectorsJsonValue.isArray())
      throw new LocalizedStringLoadingException(format("%s: selector-based placeholder translations require selectors to be an array. " +
          "Placeholder is '%s' for key '%s'", canonicalPath, placeholderKey, key));

    List<@NonNull LanguageFormSelector> selectors = new ArrayList<>();
    Set<@NonNull LanguageFormType> selectorTypes = new LinkedHashSet<>();

    for (JsonValue selectorJsonValue : selectorsJsonValue.asArray()) {
      if (selectorJsonValue == null || selectorJsonValue.isNull())
        throw new LocalizedStringLoadingException(format("%s: selector entries cannot be null. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      if (!selectorJsonValue.isObject())
        throw new LocalizedStringLoadingException(format("%s: selector entries must be objects. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      JsonObject selectorJsonObject = selectorJsonValue.asObject();
      validateNoUnexpectedObjectMembers(canonicalPath, key, selectorJsonObject,
          format("selector for placeholder '%s'", placeholderKey), Set.of("value", "form"));
      JsonValue selectorValueJsonValue = selectorJsonObject.get("value");
      JsonValue selectorFormJsonValue = selectorJsonObject.get("form");

      if (selectorValueJsonValue == null || selectorValueJsonValue.isNull())
        throw new LocalizedStringLoadingException(format("%s: selector value is required. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      if (selectorFormJsonValue == null || selectorFormJsonValue.isNull())
        throw new LocalizedStringLoadingException(format("%s: selector form is required. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      if (!selectorValueJsonValue.isString())
        throw new LocalizedStringLoadingException(format("%s: selector value must be a string. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      if (!selectorFormJsonValue.isString())
        throw new LocalizedStringLoadingException(format("%s: selector form must be a string. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      String selectorValue = selectorValueJsonValue.asString();
      String selectorFormName = selectorFormJsonValue.asString();

      ensureValidPlaceholderName(canonicalPath, key, selectorValue, "selector value");

      LanguageFormType languageFormType = SUPPORTED_LANGUAGE_FORM_TYPES_BY_NAME.get(selectorFormName);

      if (languageFormType == null)
        throw new LocalizedStringLoadingException(format("%s: unexpected selector form encountered. Placeholder is '%s' for key '%s'. " +
                "You provided '%s', valid values are [%s]", canonicalPath, placeholderKey, key, selectorFormName,
            SUPPORTED_LANGUAGE_FORM_TYPES_BY_NAME.keySet().stream().collect(Collectors.joining(", "))));

      if (!selectorTypes.add(languageFormType))
        throw new LocalizedStringLoadingException(format("%s: duplicate selector form '%s' encountered. Placeholder is '%s' for key '%s'",
            canonicalPath, selectorFormName, placeholderKey, key));

      selectors.add(new LanguageFormSelector(selectorValue, languageFormType));
    }

    if (selectors.isEmpty())
      throw new LocalizedStringLoadingException(format("%s: selector-based placeholder translations require at least one selector. Placeholder is '%s' for key '%s'",
          canonicalPath, placeholderKey, key));

    if (translationsJsonValue == null || translationsJsonValue.isNull())
      throw new LocalizedStringLoadingException(format("%s: placeholder translations are required. Key is '%s'", canonicalPath, key));

    if (!translationsJsonValue.isArray())
      throw new LocalizedStringLoadingException(format("%s: selector-based placeholder translations require translations to be an array. " +
          "Placeholder is '%s' for key '%s'", canonicalPath, placeholderKey, key));

    List<@NonNull LanguageFormTranslationRule> translationRules = new ArrayList<>();

    for (JsonValue translationJsonValue : translationsJsonValue.asArray()) {
      if (translationJsonValue == null || translationJsonValue.isNull())
        throw new LocalizedStringLoadingException(format("%s: selector-based translation rules cannot be null. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      if (!translationJsonValue.isObject())
        throw new LocalizedStringLoadingException(format("%s: selector-based translation rules must be objects. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      JsonObject translationJsonObject = translationJsonValue.asObject();
      validateNoUnexpectedObjectMembers(canonicalPath, key, translationJsonObject,
          format("selector-based translation rule for placeholder '%s'", placeholderKey), Set.of("when", "value"));
      JsonValue ruleValueJsonValue = translationJsonObject.get("value");
      JsonValue whenJsonValue = translationJsonObject.get("when");

      if (ruleValueJsonValue == null || ruleValueJsonValue.isNull())
        throw new LocalizedStringLoadingException(format("%s: selector-based translation rules require a value. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      if (!ruleValueJsonValue.isString())
        throw new LocalizedStringLoadingException(format("%s: selector-based translation rule values must be strings. Placeholder is '%s' for key '%s'",
            canonicalPath, placeholderKey, key));

      Map<@NonNull LanguageFormType, @NonNull LanguageForm> whenByLanguageFormType = new LinkedHashMap<>();

      if (whenJsonValue != null) {
        if (!whenJsonValue.isObject())
          throw new LocalizedStringLoadingException(format("%s: selector-based translation rule conditions must be an object. Placeholder is '%s' for key '%s'",
              canonicalPath, placeholderKey, key));

        for (Member whenMember : whenJsonValue.asObject()) {
          String selectorFormName = whenMember.getName();
          JsonValue selectorLanguageFormJsonValue = whenMember.getValue();
          LanguageFormType languageFormType = SUPPORTED_LANGUAGE_FORM_TYPES_BY_NAME.get(selectorFormName);

          if (languageFormType == null)
            throw new LocalizedStringLoadingException(format("%s: unexpected selector condition form encountered. Placeholder is '%s' for key '%s'. " +
                    "You provided '%s', valid values are [%s]", canonicalPath, placeholderKey, key, selectorFormName,
                SUPPORTED_LANGUAGE_FORM_TYPES_BY_NAME.keySet().stream().collect(Collectors.joining(", "))));

          if (!selectorTypes.contains(languageFormType))
            throw new LocalizedStringLoadingException(format("%s: selector condition '%s' is not declared in selectors. Placeholder is '%s' for key '%s'",
                canonicalPath, selectorFormName, placeholderKey, key));

          if (!selectorLanguageFormJsonValue.isString())
            throw new LocalizedStringLoadingException(format("%s: selector condition values must be strings. Placeholder is '%s' for key '%s'",
                canonicalPath, placeholderKey, key));

          String selectorLanguageFormName = selectorLanguageFormJsonValue.asString();
          LanguageForm languageForm = SUPPORTED_LANGUAGE_FORMS_BY_NAME.get(selectorLanguageFormName);

          if (languageForm == null)
            throw new LocalizedStringLoadingException(format("%s: unexpected selector condition language form encountered. Placeholder is '%s' for key '%s'. " +
                    "You provided '%s', valid values are [%s]", canonicalPath, placeholderKey, key, selectorLanguageFormName,
                SUPPORTED_LANGUAGE_FORM_NAMES_BY_TYPE.get(languageFormType).stream().collect(Collectors.joining(", "))));

          if (!languageFormType.equals(LanguageFormType.forLanguageForm(languageForm)))
            throw new LocalizedStringLoadingException(format("%s: selector condition '%s' must use one of [%s]. Placeholder is '%s' for key '%s'",
                canonicalPath, selectorFormName, SUPPORTED_LANGUAGE_FORM_NAMES_BY_TYPE.get(languageFormType).stream().collect(Collectors.joining(", ")),
                placeholderKey, key));

          whenByLanguageFormType.put(languageFormType, languageForm);
        }
      }

      String ruleValue = ruleValueJsonValue.asString();
      validatePlaceholderReferences(canonicalPath, key, ruleValue, "selector-based translation rule");
      translationRules.add(new LanguageFormTranslationRule(whenByLanguageFormType, ruleValue));
    }

    if (translationRules.isEmpty())
      throw new LocalizedStringLoadingException(format("%s: selector-based placeholder translations require at least one rule. Placeholder is '%s' for key '%s'",
          canonicalPath, placeholderKey, key));

    validateSelectorTranslationRules(canonicalPath, key, placeholderKey, translationRules);

    return new LanguageFormTranslation(selectors, translationRules);
  }

  private static void validateSelectorTranslationRules(@NonNull String canonicalPath, @NonNull String key,
                                                       @NonNull String placeholderKey,
                                                       @NonNull List<@NonNull LanguageFormTranslationRule> translationRules) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderKey);
    requireNonNull(translationRules);

    for (int i = 0; i < translationRules.size(); i++) {
      LanguageFormTranslationRule leftRule = translationRules.get(i);

      for (int j = i + 1; j < translationRules.size(); j++) {
        LanguageFormTranslationRule rightRule = translationRules.get(j);

        if (leftRule.getWhenByLanguageFormType().size() != rightRule.getWhenByLanguageFormType().size())
          continue;

        if (!selectorRuleConditionsOverlap(leftRule.getWhenByLanguageFormType(), rightRule.getWhenByLanguageFormType()))
          continue;

        throw new LocalizedStringLoadingException(format("%s: selector-based translation rules are ambiguous for placeholder '%s' in key '%s'. " +
                "Rules %s and %s can both match with the same specificity", canonicalPath, placeholderKey, key, leftRule, rightRule));
      }
    }
  }

  private static boolean selectorRuleConditionsOverlap(@NonNull Map<@NonNull LanguageFormType, @NonNull LanguageForm> leftConditions,
                                                       @NonNull Map<@NonNull LanguageFormType, @NonNull LanguageForm> rightConditions) {
    requireNonNull(leftConditions);
    requireNonNull(rightConditions);

    for (Map.Entry<@NonNull LanguageFormType, @NonNull LanguageForm> leftCondition : leftConditions.entrySet()) {
      LanguageForm rightLanguageForm = rightConditions.get(leftCondition.getKey());

      if (rightLanguageForm != null && !rightLanguageForm.equals(leftCondition.getValue()))
        return false;
    }

    return true;
  }

  private static void validateNoUnexpectedObjectMembers(@NonNull String canonicalPath,
                                                        @NonNull String key,
                                                        @NonNull JsonObject jsonObject,
                                                        @NonNull String description,
                                                        @NonNull Set<@NonNull String> expectedMemberNames) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(jsonObject);
    requireNonNull(description);
    requireNonNull(expectedMemberNames);

    for (Member member : jsonObject)
      if (!expectedMemberNames.contains(member.getName()))
        throw new LocalizedStringLoadingException(format("%s: unexpected field '%s' in %s for key '%s'. Valid fields are [%s]",
            canonicalPath, member.getName(), description, key, expectedMemberNames.stream().collect(Collectors.joining(", "))));
  }

  private static void ensureValidPlaceholderName(@NonNull String canonicalPath, @NonNull String key,
                                                 @NonNull String placeholderName, @NonNull String description) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderName);
    requireNonNull(description);

    if (!LocalizedStringUtils.isValidLocalizedStringIdentifier(placeholderName))
      throw new LocalizedStringLoadingException(format("%s: invalid %s '%s'. Placeholder names must start with a Unicode letter or underscore " +
          "and contain only Unicode letters, Unicode digits, Unicode combining marks, underscores, or hyphens. Key is '%s'",
          canonicalPath, description, placeholderName, key));

    if (SUPPORTED_LANGUAGE_FORMS_BY_NAME.containsKey(placeholderName))
      throw new LocalizedStringLoadingException(format("%s: invalid %s '%s'. Placeholder names may not use reserved expression constants. " +
          "Key is '%s'", canonicalPath, description, placeholderName, key));
  }

  @NonNull
  private static String normalizeLocalizedStringsFileContents(@NonNull String localizedStringsFileContents) {
    requireNonNull(localizedStringsFileContents);

    String normalizedLocalizedStringsFileContents = localizedStringsFileContents;

    if (!normalizedLocalizedStringsFileContents.isEmpty() && normalizedLocalizedStringsFileContents.charAt(0) == UTF_8_BOM)
      normalizedLocalizedStringsFileContents = normalizedLocalizedStringsFileContents.substring(1);

    return normalizedLocalizedStringsFileContents;
  }

  private static boolean isJsonWhitespaceOnly(@NonNull String value) {
    requireNonNull(value);

    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);

      if (character != ' ' && character != '\t' && character != '\n' && character != '\r')
        return false;
    }

    return true;
  }

  private static void validateJsonNestingDepth(@NonNull String source, @NonNull String json,
                                               int maximumJsonNestingDepth) {
    requireNonNull(source);
    requireNonNull(json);

    int depth = 0;
    boolean insideString = false;
    boolean escaped = false;

    for (int i = 0; i < json.length(); i++) {
      char character = json.charAt(i);

      if (insideString) {
        if (escaped) {
          escaped = false;
        } else if (character == '\\') {
          escaped = true;
        } else if (character == '"') {
          insideString = false;
        }

        continue;
      }

      if (character == '"') {
        insideString = true;
      } else if (character == '{' || character == '[') {
        ++depth;

        if (depth > maximumJsonNestingDepth)
          throw new LocalizedStringLoadingException(format(
              "%s: JSON nesting depth exceeds the maximum of %d", source, maximumJsonNestingDepth));
      } else if (character == '}' || character == ']') {
        --depth;
      }
    }
  }

  private static void validateNoDuplicateObjectMembers(@NonNull String canonicalPath, @NonNull JsonValue jsonValue,
                                                       @NonNull String jsonPath) {
    requireNonNull(canonicalPath);
    requireNonNull(jsonValue);
    requireNonNull(jsonPath);

    if (jsonValue.isObject()) {
      Set<@NonNull String> memberNames = new LinkedHashSet<>();

      for (Member member : jsonValue.asObject()) {
        String memberName = member.getName();

        if (!memberNames.add(memberName))
          throw new LocalizedStringLoadingException(format("%s: duplicate JSON object member '%s' encountered at %s",
              canonicalPath, boundedDiagnosticValue(memberName), jsonPath));

        validateNoDuplicateObjectMembers(canonicalPath, member.getValue(),
            jsonObjectMemberPath(jsonPath, memberName));
      }
    } else if (jsonValue.isArray()) {
      int index = 0;

      for (JsonValue arrayElementJsonValue : jsonValue.asArray()) {
        if (arrayElementJsonValue != null && !arrayElementJsonValue.isNull())
          validateNoDuplicateObjectMembers(canonicalPath, arrayElementJsonValue,
              jsonArrayElementPath(jsonPath, index));

        ++index;
      }
    }
  }

  @NonNull
  private static String jsonObjectMemberPath(@NonNull String parentPath, @NonNull String memberName) {
    requireNonNull(parentPath);
    requireNonNull(memberName);
    return boundedJsonPath(parentPath, ".", memberName, "");
  }

  @NonNull
  private static String jsonArrayElementPath(@NonNull String parentPath, int index) {
    requireNonNull(parentPath);
    return boundedJsonPath(parentPath, "[", Integer.toString(index), "]");
  }

  @NonNull
  private static String boundedJsonPath(@NonNull String parentPath, @NonNull String prefix,
                                        @NonNull String component, @NonNull String suffix) {
    requireNonNull(parentPath);
    requireNonNull(prefix);
    requireNonNull(component);
    requireNonNull(suffix);

    if (parentPath.length() >= MAXIMUM_JSON_DIAGNOSTIC_PATH_CHARACTERS)
      return parentPath;

    StringBuilder path = new StringBuilder(Math.min(MAXIMUM_JSON_DIAGNOSTIC_PATH_CHARACTERS,
        parentPath.length() + prefix.length() + Math.min(component.length(), 64) + suffix.length()));
    appendBoundedPathPart(path, parentPath);
    appendBoundedPathPart(path, prefix);
    appendBoundedPathPart(path, component);
    appendBoundedPathPart(path, suffix);
    return path.toString();
  }

  private static void appendBoundedPathPart(@NonNull StringBuilder path, @NonNull String part) {
    requireNonNull(path);
    requireNonNull(part);

    int remaining = MAXIMUM_JSON_DIAGNOSTIC_PATH_CHARACTERS - path.length();

    if (remaining <= 0)
      return;

    if (part.length() <= remaining) {
      path.append(part);
      return;
    }

    if (remaining > 1)
      path.append(part, 0, remaining - 1);

    path.append('\u2026');
  }

  @NonNull
  private static String boundedDiagnosticValue(@NonNull String value) {
    requireNonNull(value);

    if (value.length() <= 256)
      return value;

    return value.substring(0, 255) + '\u2026';
  }

  private static final class SourceLocalizedString {
    @NonNull
    private final LocalizedString localizedString;
    @NonNull
    private final String origin;

    private SourceLocalizedString(@NonNull LocalizedString localizedString, @NonNull String origin) {
      requireNonNull(localizedString);
      requireNonNull(origin);

      this.localizedString = localizedString;
      this.origin = origin;
    }

    @NonNull
    private LocalizedString getLocalizedString() {
      return localizedString;
    }

    @NonNull
    private String getOrigin() {
      return origin;
    }
  }
}
