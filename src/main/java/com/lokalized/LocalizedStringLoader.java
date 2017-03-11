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

import com.lokalized.MinimalJson.Json;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * A collection of utility methods for loading localized strings from a file.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class LocalizedStringLoader {
	@Nonnull
	private static final Set<String> SUPPORTED_LANGUAGE_TAGS;
	@Nonnull
	private static final Logger LOGGER = Logger.getLogger(LocalizedStringLoader.class.getName());

	static {
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
	 * Like any classpath references, packages are separated using the {@code /} character.
	 * <p>
	 * Example package names:
	 * <ul>
	 * <li>{@code "strings"}
	 * <li>{@code "com/lokalized/strings"}
	 * </ul>
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

	/**
	 * @param directory
	 * @return
	 * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
	 */
	@Nonnull
	private static Map<Locale, Set<LocalizedString>> loadFromDirectory(@Nonnull File directory) {
		requireNonNull(directory);

		if (!directory.isDirectory())
			throw new LocalizedStringLoadingException(format("Classpath location '%s' exists but is not a directory",
					directory));

		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new HashMap<>();

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
	 * @param file
	 * @return
	 * @throws LocalizedStringLoadingException if an error occurs while loading localized string files
	 */
	@Nonnull
	private static Set<LocalizedString> parseLocalizedStringsFile(@Nonnull File file) {
		requireNonNull(file);

		String canonicalPath = null;

		try {
			canonicalPath = file.getCanonicalPath();
		} catch (IOException e) {
			throw new LocalizedStringLoadingException(
					format("Unable to determine canonical path for localized string file %s", file), e);
		}

		if (!Files.isRegularFile(file.toPath()))
			throw new LocalizedStringLoadingException(format("%s is not a regular file", canonicalPath));

		String localizedStringsFileContents = null;

		try {
			localizedStringsFileContents = new String(Files.readAllBytes(file.toPath()), UTF_8).trim();
		} catch (IOException e) {
			throw new LocalizedStringLoadingException(format("Unable to load localized string file contents for %s",
					canonicalPath), e);
		}

		if ("".equals(localizedStringsFileContents))
			return Collections.emptySet();

		Set<LocalizedString> localizedStrings = new HashSet<>();
		JsonObject outerJsonObject = Json.parse(localizedStringsFileContents).asObject();

		for (Member member : outerJsonObject) {
			String key = member.getName();
			JsonValue value = member.getValue();
			localizedStrings.add(parseLocalizedString(canonicalPath, key, value));
		}

		return Collections.unmodifiableSet(localizedStrings);
	}

	@Nonnull
	private static LocalizedString parseLocalizedString(@Nonnull String canonicalPath, @Nonnull String key, @Nonnull JsonValue value) {
		requireNonNull(canonicalPath);
		requireNonNull(key);
		requireNonNull(value);

		if (value.isString()) {
			// Simple case - just a key and a value, no translation rules
			//
			// Example format:
			//
			// {
			//   "Hello, world!" : "Приветствую, мир"
			// }

			String translation = value.asString();

			if (translation == null)
				throw new LocalizedStringLoadingException(format("%s: a translation is required for key '%s'", canonicalPath, key));

			return new LocalizedString(key, translation);
		} else if (value.isObject()) {
			// More complex case, there can be placeholders and alternatives.
			//
			// Example format:
			//
			// {
			//   "I read {{bookCount}} books" : {
			//     "translation" : "I read {{bookCount}} {{books}}",
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

			JsonObject localizedStringObject = value.asObject();
			String translation = localizedStringObject.getString("translation", null);

			if (translation == null)
				throw new LocalizedStringLoadingException(format("%s: a translation is required for key '%s'", canonicalPath, key));

			// TODO: parse placeholders and alternatives
			return new LocalizedString(key, translation);
		} else {
			throw new LocalizedStringLoadingException(format("%s: either a translation string or object value is required for key '%s'",
					canonicalPath, key));
		}
	}
}