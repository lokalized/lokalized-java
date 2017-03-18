/*
 * Copyright 2017 Transmogrify LLC.
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
import com.lokalized.MinimalJson.Json;
import com.lokalized.MinimalJson.JsonArray;
import com.lokalized.MinimalJson.JsonObject;
import com.lokalized.MinimalJson.JsonObject.Member;
import com.lokalized.MinimalJson.JsonValue;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
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
	@Nonnull
	private static final Set<String> SUPPORTED_LANGUAGE_TAGS;
	@Nonnull
	private static final Map<String, LanguageForm> SUPPORTED_LANGUAGE_FORMS_BY_NAME;
	@Nonnull
	private static final Logger LOGGER;

	static {
		LOGGER = Logger.getLogger(LoggerType.LOCALIZED_STRING_LOADER.getLoggerName());

		SUPPORTED_LANGUAGE_TAGS = Collections.unmodifiableSet(Arrays.stream(Locale.getAvailableLocales())
				.map(locale -> locale.toLanguageTag())
				.collect(Collectors.toSet()));

		Set<LanguageForm> supportedLanguageForms = new LinkedHashSet<>();
		supportedLanguageForms.addAll(Arrays.asList(Gender.values()));
		supportedLanguageForms.addAll(Arrays.asList(Plural.values()));

		Map<String, LanguageForm> supportedLanguageFormsByName = new LinkedHashMap<>();

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

			supportedLanguageFormsByName.put(languageFormName, languageForm);
		}

		SUPPORTED_LANGUAGE_FORMS_BY_NAME = Collections.unmodifiableMap(supportedLanguageFormsByName);
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

		for (File file : directory.listFiles()) {
			String languageTag = file.getName();

			if (SUPPORTED_LANGUAGE_TAGS.contains(languageTag)) {
				LOGGER.fine(format("Loading localized strings file '%s'...", languageTag));
				Locale locale = Locale.forLanguageTag(file.getName());
				localizedStringsByLocale.put(locale, parseLocalizedStringsFile(file));
			} else {
				LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", languageTag));
			}
		}

		return Collections.unmodifiableMap(localizedStringsByLocale);
	}

	/**
	 * Parses out a set of localized strings from the given file.
	 *
	 * @param file the file to parse, not null
	 * @return the set of localized strings contained in the file, not null
	 * @throws LocalizedStringLoadingException if an error occurs while parsing the localized string file
	 */
	@Nonnull
	private static Set<LocalizedString> parseLocalizedStringsFile(@Nonnull File file) {
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

		if ("".equals(localizedStringsFileContents))
			return Collections.emptySet();

		Set<LocalizedString> localizedStrings = new HashSet<>();
		JsonValue outerJsonValue = Json.parse(localizedStringsFileContents);

		if (!outerJsonValue.isObject())
			throw new LocalizedStringLoadingException(format("%s: a localized strings file must be comprised of a single JSON object", canonicalPath));

		JsonObject outerJsonObject = outerJsonValue.asObject();

		for (Member member : outerJsonObject) {
			String key = member.getName();
			JsonValue value = member.getValue();
			localizedStrings.add(parseLocalizedString(canonicalPath, key, value));
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
	@Nonnull
	private static LocalizedString parseLocalizedString(@Nonnull String canonicalPath, @Nonnull String key, @Nonnull JsonValue jsonValue) {
		requireNonNull(canonicalPath);
		requireNonNull(key);
		requireNonNull(jsonValue);

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

			return new LocalizedString.Builder(key, translation).build();
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

			JsonValue translationJsonValue = localizedStringObject.get("translation");

			if (translationJsonValue == null || translationJsonValue.isNull())
				throw new LocalizedStringLoadingException(format("%s: a translation is required for key '%s'", canonicalPath, key));

			if (!translationJsonValue.isString())
				throw new LocalizedStringLoadingException(format("%s: translation must be a string for key '%s'", canonicalPath, key));

			String translation = translationJsonValue.asString();

			String commentary = null;

			JsonValue commentaryJsonValue = localizedStringObject.get("commentary");

			if (commentaryJsonValue != null && !commentaryJsonValue.isNull()) {
				if (!commentaryJsonValue.isString())
					throw new LocalizedStringLoadingException(format("%s: commentary must be a string for key '%s'", canonicalPath, key));

				commentary = commentaryJsonValue.asString();
			}

			Map<String, LanguageFormTranslation> languageFormTranslationsByPlaceholder = new LinkedHashMap<>();

			JsonValue placeholdersJsonValue = localizedStringObject.get("placeholders");

			if (placeholdersJsonValue != null && !placeholdersJsonValue.isNull()) {
				if (!placeholdersJsonValue.isObject())
					throw new LocalizedStringLoadingException(format("%s: the placeholders value must be an object. Key was '%s'", canonicalPath, key));

				JsonObject placeholdersJsonObject = placeholdersJsonValue.asObject();

				for (Member placeholderMember : placeholdersJsonObject) {
					String placeholderKey = placeholderMember.getName();
					JsonValue placeholderJsonValue = placeholderMember.getValue();
					String value;

					if (!placeholderJsonValue.isObject())
						throw new LocalizedStringLoadingException(format("%s: the placeholder value must be an object. Key was '%s'", canonicalPath, key));

					JsonObject placeholderJsonObject = placeholderJsonValue.asObject();

					JsonValue valueJsonValue = placeholderJsonObject.get("value");

					if (valueJsonValue == null || valueJsonValue.isNull())
						throw new LocalizedStringLoadingException(format("%s: a placeholder translation value is required. Key was '%s'", canonicalPath, key));

					if (!valueJsonValue.isString())
						throw new LocalizedStringLoadingException(format("%s: a placeholder translation value must be a string. Key was '%s'", canonicalPath, key));

					value = valueJsonValue.asString();

					JsonValue translationsJsonValue = placeholderJsonObject.get("translations");

					if (translationsJsonValue == null || translationsJsonValue.isNull())
						continue;

					if (!translationsJsonValue.isObject())
						throw new LocalizedStringLoadingException(format("%s: the placeholder translations value must be an object. Key was '%s'", canonicalPath, key));

					Map<LanguageForm, String> translationsByLanguageForm = new LinkedHashMap<>();

					JsonObject translationsJsonObject = translationsJsonValue.asObject();

					for (Member translationMember : translationsJsonObject) {
						String languageFormTranslationKey = translationMember.getName();
						JsonValue languageFormTranslationJsonValue = translationMember.getValue();
						LanguageForm languageForm = SUPPORTED_LANGUAGE_FORMS_BY_NAME.get(languageFormTranslationKey);

						if (languageForm == null)
							throw new LocalizedStringLoadingException(format("%s: unexpected placeholder translation language form encountered. Key was '%s'. " +
											"You provided '%s', valid values are [%s]", canonicalPath, key, languageFormTranslationKey,
									SUPPORTED_LANGUAGE_FORMS_BY_NAME.keySet().stream().collect(Collectors.joining(", "))));

						if (!languageFormTranslationJsonValue.isString())
							throw new LocalizedStringLoadingException(format("%s: the placeholder translation value must be a string. Key was '%s'", canonicalPath, key));

						translationsByLanguageForm.put(languageForm, languageFormTranslationJsonValue.asString());
					}

					languageFormTranslationsByPlaceholder.put(placeholderKey, new LanguageFormTranslation(value, translationsByLanguageForm));
				}
			}

			List<LocalizedString> alternatives = new ArrayList<>();

			JsonValue alternativesJsonValue = localizedStringObject.get("alternatives");

			if (alternativesJsonValue != null && !alternativesJsonValue.isNull()) {
				if (!alternativesJsonValue.isArray())
					throw new LocalizedStringLoadingException(format("%s: alternatives must be an array. Key was '%s'", canonicalPath, key));

				JsonArray alternativesJsonArray = alternativesJsonValue.asArray();

				for (JsonValue alternativeJsonValue : alternativesJsonArray) {
					if (alternativeJsonValue == null || alternativeJsonValue.isNull())
						continue;

					JsonObject outerJsonObject = alternativeJsonValue.asObject();

					if (!outerJsonObject.isObject())
						throw new LocalizedStringLoadingException(format("%s: alternative value must be an object. Key was '%s'", canonicalPath, key));

					for (Member member : outerJsonObject) {
						String alternativeKey = member.getName();
						JsonValue alternativeValue = member.getValue();
						alternatives.add(parseLocalizedString(canonicalPath, alternativeKey, alternativeValue));
					}
				}
			}

			return new LocalizedString.Builder(key, translation)
					.commentary(commentary)
					.languageFormTranslationsByPlaceholder(languageFormTranslationsByPlaceholder)
					.alternatives(alternatives)
					.build();
		} else {
			throw new LocalizedStringLoadingException(format("%s: either a translation string or object value is required for key '%s'",
					canonicalPath, key));
		}
	}
}