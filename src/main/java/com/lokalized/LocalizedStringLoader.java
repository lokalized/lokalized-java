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

import com.lokalized.LocalizedString.ExpressionAlternative;
import com.lokalized.LocalizedString.ExpressionTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslation;
import com.lokalized.LocalizedString.LanguageFormTranslationRange;
import com.lokalized.LocalizedString.PlaceholderDefinition;
import com.lokalized.MinimalJson.Json;
import com.lokalized.MinimalJson.JsonArray;
import com.lokalized.MinimalJson.JsonObject;
import com.lokalized.MinimalJson.JsonObject.Member;
import com.lokalized.MinimalJson.JsonValue;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;
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
import java.util.Comparator;
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
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipException;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Utility methods for loading localized strings files.
 * <p>
 * A generated placeholder may be language-form-driven ({@link LocalizedString.LanguageFormTranslation}; localized strings file
 * members {@code value} or {@code range}, plus {@code translations}) or template-driven
 * ({@link LocalizedString.ExpressionTranslation}; a required default {@code translation}, plus optional ordered
 * expression {@code alternatives}). Template alternatives select string fragments only; the first matching
 * expression wins and the required default is used when none match. Placeholder modes are mutually exclusive, and
 * all expressions and fragment placeholder references are validated while loading.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class LocalizedStringLoader {
  private static final int MAXIMUM_JSON_DIAGNOSTIC_PATH_CHARACTERS = 4096;
  @NonNull
  private static final Map<@NonNull String, @NonNull LanguageForm> SUPPORTED_LANGUAGE_FORMS_BY_NAME;
  @NonNull
  private static final ExpressionEvaluator EXPRESSION_EVALUATOR;
  @NonNull
  private static final Pattern LANGUAGE_TAG_PATTERN;
  @NonNull
  private static final String JSON_EXTENSION;
  private static final char UTF_8_BOM;

  static {
    EXPRESSION_EVALUATOR = new ExpressionEvaluator(null, null, TranslationRuntimeLimits.hardCeilings());

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
    }

    SUPPORTED_LANGUAGE_FORMS_BY_NAME = Collections.unmodifiableMap(supportedLanguageFormsByName);
    LANGUAGE_TAG_PATTERN = Pattern.compile("^[A-Za-z]{1,8}(-[A-Za-z0-9]{1,8})*$");
    JSON_EXTENSION = ".json";
    UTF_8_BOM = '\uFEFF';
  }

  private LocalizedStringLoader() {
    // Non-instantiable
  }

  /**
   * Loads all localized strings files present in the specified package on the classpath.
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
   * with a warning; explicitly loaded filesystem directories containing localized strings files retain strict filename
   * validation.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized strings files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull String classpathPackage) {
    return loadFromClasspath(classpathPackage, LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized strings files from a classpath package using the specified loading and discovery options.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if loading, discovery, validation, or a configured limit fails
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(
      @NonNull String classpathPackage, @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromClasspath(classpathPackage, LocalizedStringWarningHandler.ignore(), loadingOptions);
  }

  /**
   * Loads all localized strings files present in the specified package, routing validation warnings to the given handler.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized strings files
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull String classpathPackage,
                                                                                               @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromClasspath(classpathPackage, warningHandler, LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized strings files from a classpath package with validation-warning, loading, and discovery policies.
   *
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if loading, discovery, validation, or a configured limit fails
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(
      @NonNull String classpathPackage, @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(classpathPackage);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    return loadFromClasspath(defaultClassLoader(), classpathPackage, warningHandler, loadingOptions);
  }

  /**
   * Loads all localized strings files present in the specified package using the specified classloader.
   * <p>
   * This is useful for containers, plugin systems, test harnesses, and other environments where the
   * desired localized string resources are not visible to Lokalized's own defining classloader.
   *
   * @param classLoader classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized strings files
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull ClassLoader classLoader,
                                                                                               @NonNull String classpathPackage) {
    return loadFromClasspath(classLoader, classpathPackage, LocalizedStringWarningHandler.ignore(),
        LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized strings files using the specified classloader and loading/discovery options.
   *
   * @param classLoader      classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if loading, discovery, validation, or a configured limit fails
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(
      @NonNull ClassLoader classLoader, @NonNull String classpathPackage,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromClasspath(classLoader, classpathPackage, LocalizedStringWarningHandler.ignore(), loadingOptions);
  }

  /**
   * Loads all localized strings files present in the specified package using the specified classloader, routing
   * validation warnings to the given handler.
   *
   * @param classLoader      classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized strings files
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspath(@NonNull ClassLoader classLoader,
                                                                                               @NonNull String classpathPackage,
                                                                                               @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromClasspath(classLoader, classpathPackage, warningHandler, LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized strings files using the specified classloader, validation-warning policy, and loading/discovery
   * options.
   *
   * @param classLoader      classloader to search, not null
   * @param classpathPackage location of a package on the classpath, not null
   * @param warningHandler   handler for non-fatal validation warnings, not null
   * @param loadingOptions   loading and classpath-discovery options to apply, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if loading, discovery, validation, or a configured limit fails
   * @since 3.0.0
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
    LoadingSession loadingSession = new LoadingSession(loadingOptions, warningHandler);

    Enumeration<@NonNull URL> urls;

    try {
      urls = classLoader.getResources(classpathPackage);
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to search classpath for '%s'", classpathPackage), e);
    }

    Map<@NonNull Locale, @NonNull Map<@NonNull String, @NonNull SourceLocalizedString>> mergedByLocale = createSourceLocaleKeyMap();
    Set<@NonNull String> processedLocations = new LinkedHashSet<>();
    String packageDiscoverySource = format("classpath package '%s'", classpathPackage);

    while (urls.hasMoreElements()) {
      URL url = urls.nextElement();
      loadingSession.discoverEntry(packageDiscoverySource);

      if (!processedLocations.add(classpathLocationIdentity(url, classpathPackage)))
        continue;

      Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> localizedStringsByLocale =
          loadFromUrl(url, classpathPackage, loadingSession);
      mergeLocalizedStrings(mergedByLocale, localizedStringsByLocale);
    }

    if (loadingOptions.isExhaustiveClasspathSearchEnabled()) {
      for (Path classpathRoot : classpathRootsFor(classLoader, loadingSession)) {
        if (Files.isDirectory(classpathRoot)) {
          Path packageDirectory = classpathRoot.resolve(classpathPackage);

          if (!Files.isDirectory(packageDirectory))
            continue;

          String locationIdentity = canonicalPathForPath(packageDirectory);

          if (!processedLocations.add(locationIdentity))
            continue;

          mergeLocalizedStrings(mergedByLocale,
              loadFromDirectoryWithOrigins(packageDirectory, loadingSession));
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
              loadFromJarFile(jarFile, packagePath, loadingSession);

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

  /**
   * Loads explicitly mapped resources from the current thread context classloader using
   * {@link ClassLoader#getResourceAsStream(String)}. If no context classloader is set, Lokalized's defining classloader
   * is used.
   * <p>
   * Unlike package discovery, this API does not enumerate directories or depend on {@code file}/{@code jar} URL
   * protocols. It is therefore appropriate for containers, module systems, and plugin classloaders that can open known
   * resources but cannot expose a scannable package URL. Resource paths must be nonempty slash-relative paths.
   *
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if the mapping or any mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale) {
    return loadFromClasspathResources(defaultClassLoader(), resourcePathByLocale);
  }

  /**
   * Loads explicitly mapped resources from the current thread context classloader with loading limits.
   *
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @param loadingOptions resource limits to apply across the mapped resources, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if any argument or mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromClasspathResources(defaultClassLoader(), resourcePathByLocale, loadingOptions);
  }

  /**
   * Loads explicitly mapped resources from the current thread context classloader with a validation-warning policy.
   *
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if any argument or mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale,
      @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromClasspathResources(defaultClassLoader(), resourcePathByLocale, warningHandler);
  }

  /**
   * Loads explicitly mapped resources from the current thread context classloader with warning and loading policies.
   *
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @param loadingOptions resource limits to apply across the mapped resources, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if any argument or mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale,
      @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromClasspathResources(defaultClassLoader(), resourcePathByLocale, warningHandler, loadingOptions);
  }

  /**
   * Loads explicitly mapped classpath resources using {@link ClassLoader#getResourceAsStream(String)}.
   *
   * @param classLoader classloader from which to open resources, not null
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if any argument or mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull ClassLoader classLoader, @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale) {
    return loadFromClasspathResources(classLoader, resourcePathByLocale, LocalizedStringWarningHandler.ignore(),
        LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads explicitly mapped classpath resources with loading limits.
   *
   * @param classLoader classloader from which to open resources, not null
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @param loadingOptions resource limits to apply across the mapped resources, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if any argument or mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull ClassLoader classLoader, @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromClasspathResources(classLoader, resourcePathByLocale, LocalizedStringWarningHandler.ignore(),
        loadingOptions);
  }

  /**
   * Loads explicitly mapped classpath resources with a validation-warning policy.
   *
   * @param classLoader classloader from which to open resources, not null
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if any argument or mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull ClassLoader classLoader, @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale,
      @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromClasspathResources(classLoader, resourcePathByLocale, warningHandler,
        LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads explicitly mapped classpath resources with validation-warning and aggregate loading-limit policies.
   *
   * @param classLoader classloader from which to open resources, not null
   * @param resourcePathByLocale exact classpath resource path for each locale, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @param loadingOptions resource limits to apply across the mapped resources, not null
   * @return per-locale sets of localized strings, not null
   * @throws NullPointerException if any argument or mapping key or value is null
   * @throws IllegalArgumentException if a locale key or resource path is invalid
   * @throws LocalizedStringLoadingException if rendered locale tags collide, a resource cannot be loaded, parsed, or
   * validated, or a configured loading limit is exceeded
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromClasspathResources(
      @NonNull ClassLoader classLoader, @NonNull Map<@NonNull Locale, @NonNull String> resourcePathByLocale,
      @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(classLoader);
    requireNonNull(resourcePathByLocale);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    List<Map.@NonNull Entry<@NonNull Locale, @NonNull String>> sortedResourcePathsByLocale = new ArrayList<>();
    Map<@NonNull String, @NonNull String> resourcePathByLanguageTag = new LinkedHashMap<>();

    for (Map.Entry<@NonNull Locale, @NonNull String> resourcePathEntry : resourcePathByLocale.entrySet()) {
      Locale locale = requireNonNull(resourcePathEntry.getKey());
      String resourcePath = requireNonNull(resourcePathEntry.getValue());
      validateExplicitLocale(locale);
      validateClasspathResourcePath(resourcePath);
      String languageTag = locale.toLanguageTag();
      @Nullable String existingResourcePath = resourcePathByLanguageTag.putIfAbsent(languageTag, resourcePath);

      if (existingResourcePath != null)
        throw new LocalizedStringLoadingException(format(
            "Duplicate localized strings resource mapping for locale '%s' found at '%s' and '%s'",
            languageTag, existingResourcePath, resourcePath));

      sortedResourcePathsByLocale.add(new java.util.AbstractMap.SimpleImmutableEntry<>(locale, resourcePath));
    }

    sortedResourcePathsByLocale.sort(Comparator.comparing(entry -> entry.getKey().toLanguageTag()));

    LoadingSession loadingSession = new LoadingSession(loadingOptions, warningHandler);
    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = createLocaleMap();

    for (Map.Entry<@NonNull Locale, @NonNull String> resourcePathEntry : sortedResourcePathsByLocale) {
      Locale locale = resourcePathEntry.getKey();
      String resourcePath = resourcePathEntry.getValue();
      String source = "classpath:" + resourcePath;

      try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
        if (inputStream == null)
          throw new LocalizedStringLoadingException(format(
              "Unable to find localized strings resource '%s' on the classpath", resourcePath));

        localizedStringsByLocale.put(locale, parse(inputStream, locale, source, loadingSession));
      } catch (IOException e) {
        throw new LocalizedStringLoadingException(format(
            "Unable to load localized strings resource '%s' from the classpath", resourcePath), e);
      }
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  @NonNull
  private static ClassLoader defaultClassLoader() {
    @Nullable ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    if (classLoader == null)
      classLoader = LocalizedStringLoader.class.getClassLoader();

    return classLoader;
  }

  @NonNull
  private static Set<@NonNull Path> classpathRootsFor(@NonNull ClassLoader classLoader,
                                                       @NonNull LoadingSession loadingSession) {
    requireNonNull(classLoader);
    requireNonNull(loadingSession);

    Set<@NonNull Path> classpathRoots = new LinkedHashSet<>();
    boolean delegatesToSystemClassLoader = false;
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

    for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
      if (current == systemClassLoader)
        delegatesToSystemClassLoader = true;

      if (!(current instanceof URLClassLoader))
        continue;

      for (URL url : ((URLClassLoader) current).getURLs()) {
        loadingSession.discoverEntry("classpath roots");

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

      for (int entryStart = 0; entryStart <= classpath.length();) {
        int separatorIndex = classpath.indexOf(File.pathSeparatorChar, entryStart);
        int entryEnd = separatorIndex < 0 ? classpath.length() : separatorIndex;

        if (entryEnd > entryStart) {
          // Charge the candidate before allocating a token or asking Path to parse it.
          loadingSession.discoverEntry("system classpath roots");
          String entry = classpath.substring(entryStart, entryEnd);
          classpathRoots.add(Paths.get(entry).toAbsolutePath().normalize());
        }

        if (separatorIndex < 0)
          break;
        entryStart = separatorIndex + 1;
      }
    }

    addManifestClasspathRoots(classpathRoots, loadingSession);

    return Collections.unmodifiableSet(classpathRoots);
  }

  /**
   * Expands the transitive {@code Class-Path} entries of classpath JAR manifests. {@link URLClassLoader#getURLs()}
   * reports only the URLs supplied to the loader, even though its resource lookup also follows these manifest links.
   */
  private static void addManifestClasspathRoots(@NonNull Set<@NonNull Path> classpathRoots,
                                                @NonNull LoadingSession loadingSession) {
    requireNonNull(classpathRoots);
    requireNonNull(loadingSession);

    List<@NonNull Path> pendingRoots = new ArrayList<>(classpathRoots);

    for (int rootIndex = 0; rootIndex < pendingRoots.size(); ++rootIndex) {
      Path classpathRoot = pendingRoots.get(rootIndex);

      if (!Files.isRegularFile(classpathRoot))
        continue;

      try (JarFile jarFile = new JarFile(classpathRoot.toFile())) {
        @Nullable Manifest manifest = jarFile.getManifest();

        if (manifest == null)
          continue;

        @Nullable String manifestClasspath = manifest.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);

        if (manifestClasspath == null)
          continue;

        URL manifestBase = classpathRoot.toUri().toURL();
        String manifestDiscoverySource = format("manifest Class-Path for '%s'", classpathRoot);

        for (int entryStart = 0; entryStart < manifestClasspath.length();) {
          while (entryStart < manifestClasspath.length()
              && isManifestClasspathWhitespace(manifestClasspath.charAt(entryStart)))
            ++entryStart;
          if (entryStart >= manifestClasspath.length())
            break;

          int entryEnd = entryStart + 1;
          while (entryEnd < manifestClasspath.length()
              && !isManifestClasspathWhitespace(manifestClasspath.charAt(entryEnd)))
            ++entryEnd;

          // Charge the candidate before allocating a token or resolving its URL.
          loadingSession.discoverEntry(manifestDiscoverySource);
          String manifestEntry = manifestClasspath.substring(entryStart, entryEnd);
          entryStart = entryEnd;
          URL resolvedEntry = new URL(manifestBase, manifestEntry);

          // Exhaustive discovery operates on filesystem roots. Non-file resources remain available through ordinary
          // ClassLoader resource lookup or the explicit locale-to-resource mapping API.
          if (!"file".equals(resolvedEntry.getProtocol()))
            continue;

          Path resolvedRoot = Paths.get(resolvedEntry.toURI()).toAbsolutePath().normalize();

          if (classpathRoots.add(resolvedRoot))
            pendingRoots.add(resolvedRoot);
        }
      } catch (ZipException e) {
        // A regular classpath file need not be a JAR.
      } catch (IOException | URISyntaxException e) {
        throw new LocalizedStringLoadingException(format(
            "Unable to inspect manifest Class-Path for classpath root '%s'", classpathRoot), e);
      }
    }
  }

  private static boolean isManifestClasspathWhitespace(char character) {
    return character == ' ' || character == '\t' || character == '\n' || character == '\u000B'
        || character == '\f' || character == '\r';
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

    boolean hasWindowsDrivePrefix = classpathPackage.length() >= 2 &&
        Character.isLetter(classpathPackage.charAt(0)) && classpathPackage.charAt(1) == ':';

    if (hasWindowsDrivePrefix || packagePath.getRoot() != null || packagePath.isAbsolute() ||
        packagePath.normalize().startsWith(".."))
      throw new IllegalArgumentException(format(
          "Classpath package '%s' must remain beneath its classpath root", classpathPackage));
  }

  private static void validateClasspathResourcePath(@NonNull String resourcePath) {
    requireNonNull(resourcePath);
    validateClasspathPackage(resourcePath);

    if (resourcePath.endsWith("/"))
      throw new IllegalArgumentException(format(
          "Classpath resource '%s' must identify a file, not a package", resourcePath));
  }

  /**
   * Loads all localized strings files present in the specified directory.
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
   * @param directory directory in which to search for localized strings files, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized strings files
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(@NonNull Path directory) {
    return loadFromFilesystem(directory, LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized strings files from a directory using the specified resource limits.
   *
   * @param directory      directory in which to search, not null
   * @param loadingOptions resource limits to apply, not null
   * @return per-locale sets of localized strings, not null
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(
      @NonNull Path directory, @NonNull LocalizedStringLoadingOptions loadingOptions) {
    return loadFromFilesystem(directory, LocalizedStringWarningHandler.ignore(), loadingOptions);
  }

  /**
   * Loads all localized strings files present in the specified directory, routing validation warnings to the given handler.
   *
   * @param directory      directory in which to search for localized strings files, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized strings files
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(@NonNull Path directory,
                                                                                                @NonNull LocalizedStringWarningHandler warningHandler) {
    return loadFromFilesystem(directory, warningHandler, LocalizedStringLoadingOptions.defaults());
  }

  /**
   * Loads localized strings files from a directory with validation-warning and resource-limit policies.
   *
   * @param directory      directory in which to search, not null
   * @param warningHandler handler for non-fatal validation warnings, not null
   * @param loadingOptions resource limits to apply, not null
   * @return per-locale sets of localized strings, not null
   * @since 3.0.0
   */
  @NonNull
  public static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromFilesystem(
      @NonNull Path directory, @NonNull LocalizedStringWarningHandler warningHandler,
      @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(directory);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);
    return loadFromDirectory(directory, new LoadingSession(loadingOptions, warningHandler));
  }

  /**
   * Parses one localized strings file for the given locale.
   *
   * @param path   file to parse, not null
   * @param locale locale represented by the file, not null
   * @return localized strings contained in the file, not null
   * @throws LocalizedStringLoadingException if the file cannot be read or is invalid
   * @since 3.0.0
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull Path path, @NonNull Locale locale) {
    return parse(path, locale, LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults());
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
   * @since 3.0.0
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
   * @since 3.0.0
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull InputStream inputStream, @NonNull Locale locale,
                                                    @NonNull String source) {
    return parse(inputStream, locale, source, LocalizedStringWarningHandler.ignore(),
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
   * @since 3.0.0
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

    return parse(inputStream, locale, source, new LoadingSession(loadingOptions, warningHandler));
  }

  /**
   * Parses one localized strings character resource for the given locale. This method does not close the reader.
   *
   * @param reader character resource contents, not null
   * @param locale locale represented by the resource, not null
   * @param source human-readable source identifier used in diagnostics, not null
   * @return localized strings contained in the resource, not null
   * @throws LocalizedStringLoadingException if the resource cannot be read or is invalid
   * @since 3.0.0
   */
  @NonNull
  public static Set<@NonNull LocalizedString> parse(@NonNull Reader reader, @NonNull Locale locale,
                                                    @NonNull String source) {
    return parse(reader, locale, source, LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults());
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
   * @since 3.0.0
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

    LoadingSession loadingSession = new LoadingSession(loadingOptions, warningHandler);
    loadingSession.beginLocalizedStringsFile(source);
    String contents = readCharacters(reader, source, loadingOptions);
    return parseLocalizedStrings(source, contents, locale, loadingSession);
  }

  /**
   * Loads all localized strings files present in the specified directory.
   *
   * @param directory directory in which to search for localized strings files, not null
   * @return per-locale sets of localized strings, not null
   * @throws LocalizedStringLoadingException if an error occurs while loading localized strings files
   */
  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> loadFromDirectory(@NonNull Path directory,
                                                                                               @NonNull LoadingSession loadingSession) {
    requireNonNull(directory);
    requireNonNull(loadingSession);

    if (!Files.exists(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' does not exist",
          directory));

    if (!Files.isDirectory(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' exists but is not a directory",
          directory));

    Map<@NonNull Locale, @NonNull Set<@NonNull LocalizedString>> localizedStringsByLocale = createLocaleMap();
    String discoverySource = format("filesystem directory '%s'", directory);

    try (DirectoryStream<@NonNull Path> directoryStream = Files.newDirectoryStream(directory)) {
      for (Path file : directoryStream) {
        loadingSession.discoverEntry(discoverySource);

        if (Files.isDirectory(file))
          continue;

        @Nullable Path fileNamePath = file.getFileName();

        if (fileNamePath == null)
          continue;

        String fileName = fileNamePath.toString();

        if (isHiddenFileName(fileName))
          continue;

        String languageTag = languageTagForFileName(fileName);

        if (languageTag != null) {
          Locale locale = Locale.forLanguageTag(languageTag);

          if (localizedStringsByLocale.containsKey(locale))
            throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found at '%s'",
                locale.toLanguageTag(), file));

          localizedStringsByLocale.put(locale, parseLocalizedStringsFile(file, locale, loadingSession));
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
                                                                                                                @NonNull LoadingSession loadingSession) {
    requireNonNull(directory);
    requireNonNull(loadingSession);

    if (!Files.exists(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' does not exist",
          directory));

    if (!Files.isDirectory(directory))
      throw new LocalizedStringLoadingException(format("Location '%s' exists but is not a directory",
          directory));

    Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> localizedStringsByLocale = createSourceLocaleMap();
    Map<@NonNull Locale, @NonNull String> originByLocale = createLocaleOriginMap();
    String discoverySource = format("classpath directory '%s'", directory);

    try (DirectoryStream<@NonNull Path> directoryStream = Files.newDirectoryStream(directory)) {
      for (Path file : directoryStream) {
        loadingSession.discoverEntry(discoverySource);

        if (Files.isDirectory(file))
          continue;

        @Nullable Path fileNamePath = file.getFileName();

        if (fileNamePath == null)
          continue;

        String fileName = fileNamePath.toString();

        if (isHiddenFileName(fileName))
          continue;

        String unresolvedPath = file.toAbsolutePath().normalize().toString();
        @Nullable String languageTag = languageTagForClasspathFileName(fileName, unresolvedPath, loadingSession);

        if (languageTag != null) {
          String canonicalPath = canonicalPathForPath(file);
          Locale locale = Locale.forLanguageTag(languageTag);
          @Nullable String existingOrigin = originByLocale.get(locale);

          if (existingOrigin != null)
            throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found in '%s' and '%s'",
                locale.toLanguageTag(), existingOrigin, canonicalPath));

          localizedStringsByLocale.put(locale, sourceLocalizedStrings(
              parseLocalizedStringsFile(file, locale, loadingSession), canonicalPath));
          originByLocale.put(locale, canonicalPath);
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
                                                                                               @NonNull LoadingSession loadingSession) {
    requireNonNull(url);
    requireNonNull(classpathPackage);
    requireNonNull(loadingSession);

    String protocol = url.getProtocol();

    if ("file".equals(protocol)) {
      try {
        return loadFromDirectoryWithOrigins(Paths.get(url.toURI()), loadingSession);
      } catch (URISyntaxException e) {
        throw new LocalizedStringLoadingException(format("Unable to resolve classpath location '%s'", url), e);
      }
    }

    if ("jar".equals(protocol))
      return loadFromJar(url, classpathPackage, loadingSession);

    throw new LocalizedStringLoadingException(format("Unsupported classpath protocol '%s' for location '%s'",
        protocol, url));
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> loadFromJar(@NonNull URL jarUrl,
                                                               @NonNull String classpathPackage,
                                                               @NonNull LoadingSession loadingSession) {
    requireNonNull(jarUrl);
    requireNonNull(classpathPackage);
    requireNonNull(loadingSession);

    try {
      JarURLConnection connection = (JarURLConnection) jarUrl.openConnection();
      connection.setUseCaches(false);

      try (JarFile jarFile = connection.getJarFile()) {
        String packagePath = connection.getEntryName();

        if (packagePath == null || packagePath.isEmpty())
          packagePath = classpathPackage;

        return loadFromJarFile(jarFile, packagePath, loadingSession);
      }
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings from '%s'", jarUrl), e);
    }
  }

  @NonNull
  private static Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> loadFromJarFile(
      @NonNull JarFile jarFile, @NonNull String packagePath,
      @NonNull LoadingSession loadingSession) throws IOException {
    requireNonNull(jarFile);
    requireNonNull(packagePath);
    requireNonNull(loadingSession);

    Map<@NonNull Locale, @NonNull Set<@NonNull SourceLocalizedString>> localizedStringsByLocale = createSourceLocaleMap();
    Map<@NonNull Locale, @NonNull String> originByLocale = createLocaleOriginMap();
    packagePath = normalizedJarPackagePath(packagePath) + "/";
    Map<@NonNull String, @NonNull JarEntry> entriesByRelativeName =
        effectiveJarEntriesInPackage(jarFile, packagePath, loadingSession);

    for (Map.Entry<@NonNull String, @NonNull JarEntry> entryByRelativeName : entriesByRelativeName.entrySet()) {
      String relativeName = entryByRelativeName.getKey();
      JarEntry entry = entryByRelativeName.getValue();
      String entryName = entry.getName();

      if (isHiddenFileName(relativeName))
        continue;

      String canonicalPath = format("jar:%s!/%s", jarFile.getName(), entryName);
      @Nullable String languageTag = languageTagForClasspathFileName(relativeName, canonicalPath, loadingSession);

      if (languageTag == null)
        continue;

      Locale locale = Locale.forLanguageTag(languageTag);
      @Nullable String existingOrigin = originByLocale.get(locale);

      if (existingOrigin != null)
        throw new LocalizedStringLoadingException(format("Duplicate localized strings file for locale '%s' found in '%s' and '%s'",
            locale.toLanguageTag(), existingOrigin, canonicalPath));

      try (InputStream inputStream = jarFile.getInputStream(entry)) {
        Set<@NonNull LocalizedString> localizedStrings = parse(inputStream, locale, canonicalPath, loadingSession);
        localizedStringsByLocale.put(locale, sourceLocalizedStrings(localizedStrings, canonicalPath));
        originByLocale.put(locale, canonicalPath);
      }
    }

    return Collections.unmodifiableMap(localizedStringsByLocale);
  }

  /**
   * Selects the effective direct children of a JAR package, honoring the runtime view of multi-release JARs. Directly
   * iterating {@link JarFile#entries()} exposes physical base and versioned entries and therefore bypasses the resource
   * selection that a classloader would perform.
   */
  @NonNull
  private static Map<@NonNull String, @NonNull JarEntry> effectiveJarEntriesInPackage(
      @NonNull JarFile jarFile, @NonNull String packagePath, @NonNull LoadingSession loadingSession) {
    requireNonNull(jarFile);
    requireNonNull(packagePath);
    requireNonNull(loadingSession);

    Map<@NonNull String, @NonNull JarEntrySelection> selectionsByRelativeName = new TreeMap<>();
    Enumeration<@NonNull JarEntry> entries = jarFile.entries();
    boolean multiRelease = jarFile.isMultiRelease();
    int runtimeMajorVersion = JarFile.runtimeVersion().major();
    String versionedPrefix = "META-INF/versions/";
    String discoverySource = format("JAR '%s'", jarFile.getName());

    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      loadingSession.discoverEntry(discoverySource);

      if (entry.isDirectory())
        continue;

      String logicalEntryName = entry.getName();
      int version = 0;

      if (multiRelease && logicalEntryName.startsWith(versionedPrefix)) {
        int versionEnd = logicalEntryName.indexOf('/', versionedPrefix.length());

        if (versionEnd < 0)
          continue;

        try {
          version = Integer.parseInt(logicalEntryName.substring(versionedPrefix.length(), versionEnd));
        } catch (NumberFormatException e) {
          continue;
        }

        if (version < 9 || version > runtimeMajorVersion)
          continue;

				String versionedLogicalEntryName = logicalEntryName.substring(versionEnd + 1);

				// The multi-release JAR contract never overlays resources whose logical name is itself under META-INF/.
				if (versionedLogicalEntryName.startsWith("META-INF/"))
					continue;

				logicalEntryName = versionedLogicalEntryName;
      }

      if (!logicalEntryName.startsWith(packagePath))
        continue;

      String relativeName = logicalEntryName.substring(packagePath.length());

      if (relativeName.isEmpty() || relativeName.contains("/"))
        continue;

      @Nullable JarEntrySelection existingSelection = selectionsByRelativeName.get(relativeName);

      if (existingSelection == null || version > existingSelection.getVersion())
        selectionsByRelativeName.put(relativeName, new JarEntrySelection(entry, version));
    }

    Map<@NonNull String, @NonNull JarEntry> entriesByRelativeName = new LinkedHashMap<>();

    for (Map.Entry<@NonNull String, @NonNull JarEntrySelection> selection : selectionsByRelativeName.entrySet())
      entriesByRelativeName.put(selection.getKey(), selection.getValue().getJarEntry());

    return Collections.unmodifiableMap(entriesByRelativeName);
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
          if (existing.getLocalizedString().equals(localizedString))
            continue;

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

    boolean explicitlyUndetermined = "und".equalsIgnoreCase(languageTag) ||
        languageTag.toLowerCase(Locale.ROOT).startsWith("und-");

    if ("".equals(locale.getLanguage()) && !explicitlyUndetermined)
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

  private static void validateExplicitLocale(@NonNull Locale locale) {
    requireNonNull(locale);

    try {
      new Locale.Builder().setLocale(locale).build();
    } catch (IllformedLocaleException e) {
      throw new IllegalArgumentException(format(
          "Locale key '%s' is not a well-formed IETF BCP 47 locale", locale), e);
    }
  }

  /**
   * Parses out a set of localized strings from the given path.
   *
   * @param path the path to parse, not null
   * @param locale the locale represented by the file, not null
   * @return the set of localized strings contained in the file, not null
   * @throws LocalizedStringLoadingException if an error occurs while parsing the localized strings file
   */
  @NonNull
  private static Set<@NonNull LocalizedString> parseLocalizedStringsFile(@NonNull Path path, @NonNull Locale locale,
                                                                         @NonNull LocalizedStringWarningHandler warningHandler,
                                                                         @NonNull LocalizedStringLoadingOptions loadingOptions) {
    requireNonNull(path);
    requireNonNull(locale);
    requireNonNull(warningHandler);
    requireNonNull(loadingOptions);

    return parseLocalizedStringsFile(path, locale, new LoadingSession(loadingOptions, warningHandler));
  }

  @NonNull
  private static Set<@NonNull LocalizedString> parseLocalizedStringsFile(@NonNull Path path, @NonNull Locale locale,
                                                                         @NonNull LoadingSession loadingSession) {
    requireNonNull(path);
    requireNonNull(locale);
    requireNonNull(loadingSession);

    String canonicalPath = canonicalPathForPath(path);

    if (!Files.isRegularFile(path))
      throw new LocalizedStringLoadingException(format("%s is not a regular file", canonicalPath));

    try (InputStream inputStream = Files.newInputStream(path)) {
      return parse(inputStream, locale, canonicalPath, loadingSession);
    } catch (IOException e) {
      throw new LocalizedStringLoadingException(format("Unable to load localized strings file contents for %s",
          canonicalPath), e);
    }
  }

  @NonNull
  private static Set<@NonNull LocalizedString> parse(@NonNull InputStream inputStream, @NonNull Locale locale,
                                                     @NonNull String source, @NonNull LoadingSession loadingSession) {
    requireNonNull(inputStream);
    requireNonNull(locale);
    requireNonNull(source);
    requireNonNull(loadingSession);

    loadingSession.beginLocalizedStringsFile(source);
    String contents = readStrictUtf8(inputStream, source, loadingSession.getLoadingOptions(), loadingSession);
    return parseLocalizedStrings(source, contents, locale, loadingSession);
  }

  @NonNull
  private static String readStrictUtf8(@NonNull InputStream inputStream, @NonNull String source,
                                       @NonNull LocalizedStringLoadingOptions loadingOptions,
                                       @NonNull LoadingSession loadingSession) {
    requireNonNull(inputStream);
    requireNonNull(source);
    requireNonNull(loadingOptions);
    requireNonNull(loadingSession);

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

        if (bytesRead == 0) {
          int nextByte = inputStream.read();

          if (nextByte == -1)
            break;

          loadingSession.addInputBytes(1, source);
          outputStream.write(nextByte);
          continue;
        }

        loadingSession.addInputBytes(bytesRead, source);

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
                                                                     @NonNull LoadingSession loadingSession) {
    requireNonNull(canonicalPath);
    requireNonNull(localizedStringsFileContents);
    requireNonNull(locale);
    requireNonNull(loadingSession);

    LocalizedStringLoadingOptions loadingOptions = loadingSession.getLoadingOptions();

    if (isJsonWhitespaceOnly(localizedStringsFileContents))
      throw new LocalizedStringLoadingException(format(
          "%s: a localized strings file may not be blank; use an empty JSON object ({}) for an empty file", canonicalPath));

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

    loadingSession.addTranslationNodes(outerJsonObject.size(), canonicalPath);

    Set<String> keys = new HashSet<>();

    for (Member member : outerJsonObject) {
      String key = member.getName();

      if (!keys.add(key))
        throw new LocalizedStringLoadingException(format("%s: duplicate localized string key '%s' encountered", canonicalPath, key));

      JsonValue value = member.getValue();
      validateNoDuplicateObjectMembers(canonicalPath, value, jsonObjectMemberPath("$", key));
      LocalizedString localizedString = parseLocalizedString(canonicalPath, key, key, key, value, loadingSession);

      try {
        LocalizedStringValidator.validate(locale, localizedString);
      } catch (IllegalArgumentException e) {
        throw new LocalizedStringLoadingException(format(
            "%s: semantic validation failed for localized string key '%s'", canonicalPath, key), e);
      }

      warnOnIncompleteLanguageFormTranslations(canonicalPath, locale, key, localizedString, loadingSession);
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
   * receive a subset of values may legitimately supply a subset of forms. Range-driven translations are not checked
   * because they are expected to be partial by design. Values that resolve to a
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

    for (Map.Entry<@NonNull String, @NonNull PlaceholderDefinition> entry :
        localizedString.getPlaceholderDefinitions().entrySet()) {
      String placeholderKey = entry.getKey();
      PlaceholderDefinition placeholderDefinition = entry.getValue();

      if (!(placeholderDefinition instanceof LanguageFormTranslation))
        continue;

      LanguageFormTranslation languageFormTranslation = (LanguageFormTranslation) placeholderDefinition;

      // Range-driven translations legitimately supply a subset of forms; do not check them.
      if (languageFormTranslation.getRange().isPresent())
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
   * @param canonicalPath   the unique path to the file (or URL) being parsed, used for error reporting, not null
   * @param rootKey         the root translation key, not null
   * @param key             the root translation key or nested alternative expression, not null
   * @param declarationPath root key followed by the whole-message alternatives leading to this node, not null
   * @param jsonValue       the translation value, which may be a simple string or a complex object, not null
   * @param loadingSession  load-wide resource budget, not null
   * @return a localized string instance, not null
   * @throws LocalizedStringLoadingException if an error occurs while parsing the localized strings file
   */
  @NonNull
  private static LocalizedString parseLocalizedString(@NonNull String canonicalPath, @NonNull String rootKey,
                                                      @NonNull String key, @NonNull String declarationPath,
                                                      @NonNull JsonValue jsonValue,
                                                      @NonNull LoadingSession loadingSession) {
    requireNonNull(canonicalPath);
    requireNonNull(rootKey);
    requireNonNull(key);
    requireNonNull(declarationPath);
    requireNonNull(jsonValue);
    requireNonNull(loadingSession);

    LocalizedString.Builder localizedStringBuilder = new LocalizedString.Builder(key);

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

      validatePlaceholderReferences(canonicalPath, rootKey, translation,
          descriptionAtDeclarationPath("translation", rootKey, declarationPath));
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
          Set.of("translation", "commentary", "placeholders", "alternatives"));

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

      Map<@NonNull String, @NonNull PlaceholderDefinition> placeholderDefinitions = new LinkedHashMap<>();

      JsonValue placeholdersJsonValue = localizedStringObject.get("placeholders");

      if (placeholdersJsonValue != null) {
        if (!placeholdersJsonValue.isObject())
          throw new LocalizedStringLoadingException(format("%s: the placeholders value must be an object. Key is '%s'", canonicalPath, key));

        JsonObject placeholdersJsonObject = placeholdersJsonValue.asObject();

        for (Member placeholderMember : placeholdersJsonObject) {
          String placeholderKey = placeholderMember.getName();
          JsonValue placeholderJsonValue = placeholderMember.getValue();
          loadingSession.addTranslationNodes(1, canonicalPath);

          ensureValidPlaceholderName(canonicalPath, key, placeholderKey, "placeholder");

          if (!placeholderJsonValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: the placeholder value must be an object. Key is '%s'", canonicalPath, key));

          JsonObject placeholderJsonObject = placeholderJsonValue.asObject();
          PlaceholderDefinition placeholderDefinition = parsePlaceholderDefinition(canonicalPath, rootKey,
              placeholderKey, declarationPath, placeholderJsonObject, loadingSession);
          placeholderDefinitions.put(placeholderKey, placeholderDefinition);
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
          loadingSession.addTranslationNodes(1, canonicalPath);

          if (alternativeJsonValue == null || alternativeJsonValue.isNull())
            throw new LocalizedStringLoadingException(format("%s: alternative values cannot be null. Key is '%s'",
                canonicalPath, key));

          if (!alternativeJsonValue.isObject())
            throw new LocalizedStringLoadingException(format("%s: alternative value must be an object. Key is '%s'", canonicalPath, key));

          JsonObject outerJsonObject = alternativeJsonValue.asObject();

          if (outerJsonObject.isEmpty())
            throw new LocalizedStringLoadingException(format("%s: alternative objects must contain at least one expression. Key is '%s'",
                canonicalPath, key));

          if (outerJsonObject.size() > 1)
            throw new LocalizedStringLoadingException(format(
                "%s: each alternative object must contain exactly one expression so array order defines first-match precedence. Key is '%s'",
                canonicalPath, key));

          for (Member member : outerJsonObject) {
            String alternativeKey = member.getName();
            JsonValue alternativeValue = member.getValue();
            validateWholeMessageAlternativeExpression(canonicalPath, rootKey, alternativeKey);
            String alternativePath = boundedJsonPath(declarationPath, " -> alternative[", alternativeKey, "]");
            alternatives.add(parseLocalizedString(canonicalPath, rootKey, alternativeKey, alternativePath,
                alternativeValue, loadingSession));
          }
        }
      }

      if (translation == null && alternatives.isEmpty())
        throw new LocalizedStringLoadingException(format("%s: either a translation or at least one alternative expression is required for key '%s'",
            canonicalPath, key));

      if (translation != null)
        validatePlaceholderReferences(canonicalPath, rootKey, translation,
            descriptionAtDeclarationPath("translation", rootKey, declarationPath));

      return localizedStringBuilder.translation(translation)
          .commentary(commentary)
          .placeholderDefinitions(placeholderDefinitions)
          .alternatives(alternatives)
          .build();
    } else {
      throw new LocalizedStringLoadingException(format("%s: either a translation string or object value is required for key '%s'",
          canonicalPath, key));
    }
  }

  @NonNull
  private static String descriptionAtDeclarationPath(@NonNull String description, @NonNull String rootKey,
                                                     @NonNull String declarationPath) {
    requireNonNull(description);
    requireNonNull(rootKey);
    requireNonNull(declarationPath);
    return rootKey.equals(declarationPath) ? description : format("%s declared at %s", description, declarationPath);
  }

  private static void validatePlaceholderReferences(@NonNull String canonicalPath,
                                                    @NonNull String rootKey,
                                                    @NonNull String translation,
                                                    @NonNull String description) {
    requireNonNull(canonicalPath);
    requireNonNull(rootKey);
    requireNonNull(translation);
    requireNonNull(description);

    Set<@NonNull String> referencedPlaceholderNames;

    try {
      referencedPlaceholderNames = StringInterpolator.placeholderNamesIn(translation);
    } catch (IllegalArgumentException e) {
      throw new LocalizedStringLoadingException(format("%s: invalid placeholder reference in %s for key '%s': %s",
          canonicalPath, description, rootKey, e.getMessage()), e);
    }

    for (String placeholderName : referencedPlaceholderNames)
      ensureValidPlaceholderName(canonicalPath, rootKey, placeholderName, description + " placeholder reference");
  }

  private static void validateWholeMessageAlternativeExpression(@NonNull String canonicalPath,
                                                                @NonNull String rootKey,
                                                                @NonNull String expression) {
    requireNonNull(canonicalPath);
    requireNonNull(rootKey);
    requireNonNull(expression);

    try {
      EXPRESSION_EVALUATOR.parseAndValidateExpressionTokens(expression);
    } catch (ExpressionEvaluationException e) {
      throw new LocalizedStringLoadingException(format(
          "%s: unable to parse whole-message alternative expression '%s' for root key '%s': %s",
          canonicalPath, expression, rootKey, e.getMessage()), e);
    }
  }

  @NonNull
  private static PlaceholderDefinition parsePlaceholderDefinition(@NonNull String canonicalPath,
                                                                  @NonNull String rootKey,
                                                                  @NonNull String placeholderKey,
                                                                  @NonNull String declarationPath,
                                                                  @NonNull JsonObject placeholderJsonObject,
                                                                  @NonNull LoadingSession loadingSession) {
    requireNonNull(canonicalPath);
    requireNonNull(rootKey);
    requireNonNull(placeholderKey);
    requireNonNull(declarationPath);
    requireNonNull(placeholderJsonObject);
    requireNonNull(loadingSession);

    validateNoUnexpectedObjectMembers(canonicalPath, rootKey, placeholderJsonObject,
        format("placeholder '%s'", placeholderKey),
        Set.of("value", "range", "translations", "translation", "alternatives"));

    JsonValue valueJsonValue = placeholderJsonObject.get("value");
    JsonValue rangeJsonValue = placeholderJsonObject.get("range");
    JsonValue translationsJsonValue = placeholderJsonObject.get("translations");
    JsonValue translationJsonValue = placeholderJsonObject.get("translation");
    JsonValue alternativesJsonValue = placeholderJsonObject.get("alternatives");

    boolean hasLanguageFormMember = valueJsonValue != null || rangeJsonValue != null ||
        translationsJsonValue != null;
    boolean hasTemplateMember = translationJsonValue != null || alternativesJsonValue != null;

    if (hasLanguageFormMember && hasTemplateMember)
      throw new LocalizedStringLoadingException(format(
          "%s: placeholder '%s' for root key '%s' mixes language-form members [value, range, translations] " +
              "with template members [translation, alternatives]; placeholder modes are mutually exclusive",
          canonicalPath, placeholderKey, rootKey));

    if (!hasLanguageFormMember && !hasTemplateMember)
      throw new LocalizedStringLoadingException(format(
          "%s: placeholder '%s' for root key '%s' must define either a language-form translation " +
              "or a template translation", canonicalPath, placeholderKey, rootKey));

    if (hasTemplateMember)
      return parseExpressionTranslation(canonicalPath, rootKey, placeholderKey, declarationPath, translationJsonValue,
          alternativesJsonValue, loadingSession);

    return parseLanguageFormTranslation(canonicalPath, rootKey, placeholderKey, declarationPath, valueJsonValue,
        rangeJsonValue, translationsJsonValue);
  }

  @NonNull
  private static LanguageFormTranslation parseLanguageFormTranslation(@NonNull String canonicalPath,
                                                                      @NonNull String rootKey,
                                                                      @NonNull String placeholderKey,
                                                                      @NonNull String declarationPath,
                                                                      @Nullable JsonValue valueJsonValue,
                                                                      @Nullable JsonValue rangeJsonValue,
                                                                      @Nullable JsonValue translationsJsonValue) {
    requireNonNull(canonicalPath);
    requireNonNull(rootKey);
    requireNonNull(placeholderKey);
    requireNonNull(declarationPath);

    rejectExplicitNullPlaceholderMember(canonicalPath, rootKey, placeholderKey, "value", valueJsonValue);
    rejectExplicitNullPlaceholderMember(canonicalPath, rootKey, placeholderKey, "range", rangeJsonValue);
    rejectExplicitNullPlaceholderMember(canonicalPath, rootKey, placeholderKey, "translations", translationsJsonValue);
    boolean hasValue = valueJsonValue != null;
    boolean hasRangeValue = rangeJsonValue != null;

    if (!hasValue && !hasRangeValue)
      throw new LocalizedStringLoadingException(format("%s: a placeholder translation value or range is required. Key is '%s'",
          canonicalPath, rootKey));

    if (hasValue && hasRangeValue)
      throw new LocalizedStringLoadingException(format(
          "%s: a placeholder translation cannot have both a value and a range. Key is '%s'",
          canonicalPath, rootKey));

    return parseSingleAxisLanguageFormTranslation(canonicalPath, rootKey, placeholderKey, declarationPath,
        valueJsonValue, rangeJsonValue, translationsJsonValue);
  }

  @NonNull
  private static ExpressionTranslation parseExpressionTranslation(@NonNull String canonicalPath,
                                                                  @NonNull String rootKey,
                                                                  @NonNull String placeholderKey,
                                                                  @NonNull String declarationPath,
                                                                  @Nullable JsonValue translationJsonValue,
                                                                  @Nullable JsonValue alternativesJsonValue,
                                                                  @NonNull LoadingSession loadingSession) {
    requireNonNull(canonicalPath);
    requireNonNull(rootKey);
    requireNonNull(placeholderKey);
    requireNonNull(declarationPath);
    requireNonNull(loadingSession);

    if (translationJsonValue == null)
      throw new LocalizedStringLoadingException(format(
          "%s: a default template translation is required for placeholder '%s' in root key '%s'",
          canonicalPath, placeholderKey, rootKey));

    if (translationJsonValue.isNull())
      throw new LocalizedStringLoadingException(format(
          "%s: default template translation may not be null for placeholder '%s' in root key '%s'",
          canonicalPath, placeholderKey, rootKey));

    if (!translationJsonValue.isString())
      throw new LocalizedStringLoadingException(format(
          "%s: default template translation must be a string for placeholder '%s' in root key '%s'",
          canonicalPath, placeholderKey, rootKey));

    String translation = translationJsonValue.asString();
    validatePlaceholderReferences(canonicalPath, rootKey, translation,
        descriptionAtDeclarationPath(format("default fragment for generated placeholder '%s'", placeholderKey),
            rootKey, declarationPath));

    if (alternativesJsonValue == null)
      return new ExpressionTranslation(translation);

    if (alternativesJsonValue.isNull())
      throw new LocalizedStringLoadingException(format(
          "%s: fragment alternatives may not be null for placeholder '%s' in root key '%s'",
          canonicalPath, placeholderKey, rootKey));

    if (!alternativesJsonValue.isArray())
      throw new LocalizedStringLoadingException(format(
          "%s: fragment alternatives must be an array for placeholder '%s' in root key '%s'",
          canonicalPath, placeholderKey, rootKey));

    JsonArray alternativesJsonArray = alternativesJsonValue.asArray();

    if (alternativesJsonArray.isEmpty())
      throw new LocalizedStringLoadingException(format(
          "%s: fragment alternatives must contain at least one expression for placeholder '%s' in root key '%s'",
          canonicalPath, placeholderKey, rootKey));

    List<@NonNull ExpressionAlternative> alternatives = new ArrayList<>(alternativesJsonArray.size());
    int alternativeIndex = 0;

    for (JsonValue alternativeJsonValue : alternativesJsonArray) {
      loadingSession.addTranslationNodes(1, canonicalPath);

      if (alternativeJsonValue == null || alternativeJsonValue.isNull())
        throw new LocalizedStringLoadingException(format(
            "%s: fragment alternative %d may not be null for placeholder '%s' in root key '%s'",
            canonicalPath, alternativeIndex, placeholderKey, rootKey));

      if (!alternativeJsonValue.isObject())
        throw new LocalizedStringLoadingException(format(
            "%s: fragment alternative %d must be an object for placeholder '%s' in root key '%s'",
            canonicalPath, alternativeIndex, placeholderKey, rootKey));

      JsonObject alternativeJsonObject = alternativeJsonValue.asObject();

      if (alternativeJsonObject.size() != 1)
        throw new LocalizedStringLoadingException(format(
            "%s: fragment alternative %d must contain exactly one expression so array order defines " +
                "first-match precedence. Placeholder is '%s' in root key '%s'",
            canonicalPath, alternativeIndex, placeholderKey, rootKey));

      Member alternativeMember = alternativeJsonObject.iterator().next();
      String expression = alternativeMember.getName();
      JsonValue alternativeTranslationJsonValue = alternativeMember.getValue();

      if (!alternativeTranslationJsonValue.isString())
        throw new LocalizedStringLoadingException(format(
            "%s: fragment alternative %d for expression '%s' must have a string result. " +
                "Placeholder is '%s' in root key '%s'",
            canonicalPath, alternativeIndex, expression, placeholderKey, rootKey));

      String alternativeTranslation = alternativeTranslationJsonValue.asString();
      validateFragmentAlternativeExpression(canonicalPath, rootKey, placeholderKey, alternativeIndex, expression);
      validatePlaceholderReferences(canonicalPath, rootKey, alternativeTranslation,
          descriptionAtDeclarationPath(
              format("fragment alternative %d for expression '%s' and generated placeholder '%s'",
                  alternativeIndex, expression, placeholderKey), rootKey, declarationPath));
      alternatives.add(new ExpressionAlternative(expression, alternativeTranslation));
      ++alternativeIndex;
    }

    return new ExpressionTranslation(translation, alternatives);
  }

  private static void validateFragmentAlternativeExpression(@NonNull String canonicalPath,
                                                            @NonNull String rootKey,
                                                            @NonNull String placeholderKey,
                                                            int alternativeIndex,
                                                            @NonNull String expression) {
    requireNonNull(canonicalPath);
    requireNonNull(rootKey);
    requireNonNull(placeholderKey);
    requireNonNull(expression);

    try {
      EXPRESSION_EVALUATOR.parseAndValidateExpressionTokens(expression);
    } catch (ExpressionEvaluationException e) {
      throw new LocalizedStringLoadingException(format(
          "%s: unable to parse fragment alternative %d expression '%s' for placeholder '%s' in root key '%s': %s",
          canonicalPath, alternativeIndex, expression, placeholderKey, rootKey, e.getMessage()), e);
    }
  }

  private static void rejectExplicitNullPlaceholderMember(@NonNull String canonicalPath, @NonNull String key,
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
                                                                                @NonNull String placeholderKey,
                                                                                @NonNull String declarationPath,
                                                                                @Nullable JsonValue valueJsonValue,
                                                                                @Nullable JsonValue rangeJsonValue,
                                                                                @Nullable JsonValue translationsJsonValue) {
    requireNonNull(canonicalPath);
    requireNonNull(key);
    requireNonNull(placeholderKey);
    requireNonNull(declarationPath);

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
      validatePlaceholderReferences(canonicalPath, key, languageFormTranslation,
          descriptionAtDeclarationPath(format("placeholder translation for generated placeholder '%s'", placeholderKey),
              key, declarationPath));
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
          "and contain only Unicode letters, Unicode numbers, Unicode combining marks, underscores, or hyphens. Key is '%s'",
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

  @NotThreadSafe
  private static final class LoadingSession implements LocalizedStringWarningHandler {
    @NonNull
    private final LocalizedStringLoadingOptions loadingOptions;
    @NonNull
    private final LocalizedStringWarningHandler warningHandler;
    private long inputBytes;
    private int localizedStringsFiles;
    private int translationNodes;
    private int warnings;
    private int discoveryEntries;

    private LoadingSession(@NonNull LocalizedStringLoadingOptions loadingOptions,
                           @NonNull LocalizedStringWarningHandler warningHandler) {
      this.loadingOptions = requireNonNull(loadingOptions);
      this.warningHandler = requireNonNull(warningHandler);
    }

    @NonNull
    private LocalizedStringLoadingOptions getLoadingOptions() {
      return loadingOptions;
    }

    private void beginLocalizedStringsFile(@NonNull String source) {
      requireNonNull(source);

      if (localizedStringsFiles >= loadingOptions.getMaximumLocalizedStringsFiles())
        throw new LocalizedStringLoadingException(format(
            "%s: localized strings load exceeds the aggregate localized strings file limit of %d", source,
            loadingOptions.getMaximumLocalizedStringsFiles()));

      ++localizedStringsFiles;
    }

    private void addInputBytes(int bytes, @NonNull String source) {
      requireNonNull(source);

      long maximumInputBytes = loadingOptions.getMaximumTotalInputBytes();

      if (bytes < 0 || inputBytes > maximumInputBytes - bytes)
        throw new LocalizedStringLoadingException(format(
            "%s: localized strings load exceeds the aggregate maximum of %d input bytes", source,
            maximumInputBytes));

      inputBytes += bytes;
    }

    private void addTranslationNodes(int translationNodeCount, @NonNull String source) {
      requireNonNull(source);

      int maximumTranslationNodes = loadingOptions.getMaximumTranslationNodes();

      if (translationNodeCount < 0 || translationNodes > maximumTranslationNodes - translationNodeCount)
        throw new LocalizedStringLoadingException(format(
            "%s: localized strings load exceeds the aggregate maximum of %d translation nodes", source,
            maximumTranslationNodes));

      translationNodes += translationNodeCount;
    }

    private void discoverEntry(@NonNull String source) {
      requireNonNull(source);

      if (discoveryEntries >= loadingOptions.getMaximumDiscoveryEntries())
        throw new LocalizedStringLoadingException(format(
            "%s: localized strings load exceeds the aggregate maximum of %d discovery entries", source,
            loadingOptions.getMaximumDiscoveryEntries()));

      ++discoveryEntries;
    }

    @Override
    public void handle(@NonNull LocalizedStringWarning warning) {
      requireNonNull(warning);

      if (warnings >= loadingOptions.getMaximumWarnings())
        throw new LocalizedStringLoadingException(format(
            "%s: localized strings load exceeds the aggregate maximum of %d warnings", warning.getSource(),
            loadingOptions.getMaximumWarnings()));

      ++warnings;
      warningHandler.handle(warning);
    }
  }

  private static final class JarEntrySelection {
    @NonNull
    private final JarEntry jarEntry;
    private final int version;

    private JarEntrySelection(@NonNull JarEntry jarEntry, int version) {
      this.jarEntry = requireNonNull(jarEntry);
      this.version = version;
    }

    @NonNull
    private JarEntry getJarEntry() {
      return jarEntry;
    }

    private int getVersion() {
      return version;
    }
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
