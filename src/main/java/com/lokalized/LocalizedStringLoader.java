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
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class LocalizedStringLoader {
	@Nonnull
	private static final Set<String> SUPPORTED_LANGUAGE_TAGS;
	@Nonnull
	private static final Logger LOGGER = Logger.getLogger(LocalizedStringLoader.class.getName());

	static {
		SUPPORTED_LANGUAGE_TAGS = Collections.unmodifiableSet(Arrays.stream(Locale.getAvailableLocales())
				.map(locale -> locale.toLanguageTag())
				.collect(Collectors.toSet()));
	}

	@Nonnull
	public static Map<Locale, Set<LocalizedString>> loadFromClasspath(@Nonnull String path) throws IOException {
		requireNonNull(path);

		ClassLoader classLoader = LocalizedStringLoader.class.getClassLoader();
		URL url = classLoader.getResource(path);

		if (url == null)
			throw new IOException(format("Unable to find location '%s' on the classpath", path));

		return loadFromDirectory(new File(url.getFile()));
	}

	@Nonnull
	public static Map<Locale, Set<LocalizedString>> loadFromFilesystem(@Nonnull Path directory) throws IOException {
		requireNonNull(directory);
		return loadFromDirectory(directory.toFile());
	}

	@Nonnull
	private static Map<Locale, Set<LocalizedString>> loadFromDirectory(@Nonnull File directory) throws IOException {
		requireNonNull(directory);

		if (!directory.isDirectory())
			throw new IOException(format("Classpath location '%s' exists but is not a directory", directory));

		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = new HashMap<>();

		for (File file : directory.listFiles()) {
			String languageTag = file.getName();

			if (SUPPORTED_LANGUAGE_TAGS.contains(languageTag)) {
				LOGGER.fine(format("Loading localized strings file '%s'...", languageTag));
				Locale locale = Locale.forLanguageTag(file.getName());

				String localizedStringsFileContents = new String(Files.readAllBytes(file.toPath()), UTF_8);
				localizedStringsByLocale.put(locale, parseLocalizedStringsFileContents(localizedStringsFileContents));
			} else {
				LOGGER.fine(format("File '%s' does not correspond to a known language tag, skipping...", languageTag));
			}
		}

		return Collections.unmodifiableMap(localizedStringsByLocale);
	}

	@Nonnull
	private static Set<LocalizedString> parseLocalizedStringsFileContents(@Nonnull String localizedStringsFileContents) {
		requireNonNull(localizedStringsFileContents);

		Set<LocalizedString> localizedStrings = new HashSet<>();

		throw new UnsupportedOperationException();

		// return Collections.unmodifiableSet(localizedStrings);
	}
}