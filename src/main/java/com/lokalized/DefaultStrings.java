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
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of a localized string provider.
 * <p>
 * It is recommended to use a single instance of this class across your entire application.
 * <p>
 * In multi-tenant systems like a web application where each user might have a different locale,
 * your {@code localeSupplier} might return the locale specified by current request.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class DefaultStrings implements Strings {
	@Nonnull
	private final String fallbackLanguageCode;
	@Nonnull
	private final Map<Locale, Set<LocalizedString>> localizedStringsByLocale;
	@Nonnull
	private final Supplier<Locale> localeSupplier;
	@Nonnull
	private final FailureMode failureMode;
	@Nonnull
	private final Locale fallbackLocale;
	@Nonnull
	private final Logger logger;

	/**
	 * Cache of most appropriate strings for the given locale (populated on-demand per request at runtime)
	 */
	@Nonnull
	private final Map<Locale, Map<String, LocalizedString>> localizedStringsByKeyByFixedLocale;

	/**
	 * Cache of strings by key for the given locale (populated on-demand per request at runtime).
	 * <p>
	 * List elements are ordered by most to least specific, e.g. if your locale is {@code en-US}, the first list element
	 * might be {@code en-US} strings and the second would be {@code en} strings.
	 */
	@Nonnull
	private final ConcurrentHashMap<Locale, List<Map<String, LocalizedString>>> localizedStringsByKeysByDynamicLocale;

	/**
	 * Constructs a localized string provider with builder-supplied data.
	 * <p>
	 * The fallback language code must be an ISO 639 alpha-2 or alpha-3 language code.
	 * When a language has both an alpha-2 code and an alpha-3 code, the alpha-2 code must be used.
	 *
	 * @param fallbackLanguageCode    fallback language code, not null
	 * @param localizedStringSupplier supplier of localized strings, not null
	 * @param localeSupplier          standard locale supplier, may be null
	 * @param failureMode             strategy for dealing with lookup failures, may be null
	 */
	protected DefaultStrings(@Nonnull String fallbackLanguageCode,
													 @Nonnull Supplier<Map<Locale, ? extends Iterable<LocalizedString>>> localizedStringSupplier,
													 @Nullable Supplier<Locale> localeSupplier,
													 @Nullable FailureMode failureMode) {
		requireNonNull(fallbackLanguageCode);
		requireNonNull(localizedStringSupplier);

		this.logger = Logger.getLogger(LoggerType.STRINGS.getLoggerName());

		Map<Locale, ? extends Iterable<LocalizedString>> suppliedLocalizedStringsByLocale = localizedStringSupplier.get();

		if (suppliedLocalizedStringsByLocale == null)
			suppliedLocalizedStringsByLocale = Collections.emptyMap();

		// Defensive copy of iterator to unmodifiable set
		// TODO: should probably use LinkedHashSet to preserve order in the default case since we are doing that elsewhere.
		// Not required by the spec but nice to have
		Map<Locale, Set<LocalizedString>> localizedStringsByLocale = suppliedLocalizedStringsByLocale.entrySet().stream()
				.collect(Collectors.toMap(
						entry -> entry.getKey(),
						entry -> {
							Set<LocalizedString> localizedStrings = new LinkedHashSet<>();
							entry.getValue().forEach(localizedStrings::add);
							return Collections.unmodifiableSet(localizedStrings);
						}
				));

		this.fallbackLocale = Locale.forLanguageTag(fallbackLanguageCode);
		this.fallbackLanguageCode = fallbackLanguageCode;
		this.localizedStringsByLocale = Collections.unmodifiableMap(localizedStringsByLocale);
		this.localeSupplier = localeSupplier == null ? () -> fallbackLocale : localeSupplier;
		this.failureMode = failureMode == null ? FailureMode.USE_FALLBACK : failureMode;

		this.localizedStringsByKeyByFixedLocale = Collections.unmodifiableMap(localizedStringsByLocale.entrySet().stream()
				.collect(Collectors.toMap(
						entry1 -> entry1.getKey(),
						entry1 ->
								Collections.unmodifiableMap(entry1.getValue().stream()
										.collect(Collectors.toMap(
												entry2 -> entry2.getKey(),
												entry2 -> entry2
												)
										)))));

		this.localizedStringsByKeysByDynamicLocale = new ConcurrentHashMap<>();

		if (!localizedStringsByLocale.containsKey(getFallbackLocale()))
			throw new IllegalArgumentException(format("Specified fallback language code is '%s' but no matching " +
							"localized strings locale was found. Known locales: [%s]", fallbackLanguageCode,
					localizedStringsByLocale.keySet().stream()
							.map(locale -> locale.toLanguageTag())
							.sorted()
							.collect(Collectors.joining(", "))));
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key) {
		requireNonNull(key);
		return get(key, Collections.emptyMap(), getImplicitLocale());
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key, @Nonnull Locale locale) {
		requireNonNull(key);
		requireNonNull(locale);

		return get(key, Collections.emptyMap(), locale);
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key, @Nonnull Map<String, Object> placeholders) {
		requireNonNull(key);
		requireNonNull(placeholders);

		return get(key, placeholders, getImplicitLocale());
	}

	@Nonnull
	@Override
	public String get(@Nonnull String key, @Nonnull Map<String, Object> placeholders, @Nonnull Locale locale) {
		requireNonNull(key);
		requireNonNull(placeholders);
		requireNonNull(locale);

		@SuppressWarnings("unused")
		List<Map<String, LocalizedString>> localizedStringsByKeys = getLocalizedStringsByKeysForLocale(locale);

		for (Map<String, LocalizedString> localizedStringMap : localizedStringsByKeys) {
			System.out.println("OK");
			System.out.println(localizedStringMap);
		}

		throw new UnsupportedOperationException();
	}

	@Immutable
	static class MatchingLocalizedStrings {
		@Nonnull
		private final Locale locale;
		@Nonnull
		private final Map<String, LocalizedString> localizedStringsByKey;

		public MatchingLocalizedStrings(@Nonnull Locale locale, @Nonnull Map<String, LocalizedString> localizedStringsByKey) {
			requireNonNull(locale);
			requireNonNull(localizedStringsByKey);

			this.locale = locale;
			this.localizedStringsByKey = localizedStringsByKey;
		}

		@Nonnull
		public Locale getLocale() {
			return locale;
		}

		@Nonnull
		public Map<String, LocalizedString> getLocalizedStringsByKey() {
			return localizedStringsByKey;
		}
	}

	/**
	 * Gets the fallback language code.
	 *
	 * @return the fallback language code, not null
	 */
	@Nonnull
	public String getFallbackLanguageCode() {
		return fallbackLanguageCode;
	}

	/**
	 * Gets the set of localized strings for each locale.
	 *
	 * @return the set of localized strings for each locale, not null
	 */
	@Nonnull
	public Map<Locale, Set<LocalizedString>> getLocalizedStringsByLocale() {
		return localizedStringsByLocale;
	}

	/**
	 * Gets the standard locale supplier.
	 *
	 * @return the standard locale supplier, not null
	 */
	@Nonnull
	public Supplier<Locale> getLocaleSupplier() {
		return localeSupplier;
	}

	/**
	 * Gets the strategy for handling string lookup failures.
	 *
	 * @return the strategy for handling string lookup failures, not null
	 */
	@Nonnull
	public FailureMode getFailureMode() {
		return failureMode;
	}

	/**
	 * Gets the fallback locale.
	 *
	 * @return the fallback locale, not null
	 */
	@Nonnull
	protected Locale getFallbackLocale() {
		return fallbackLocale;
	}

	/**
	 * Gets the locale to use if one was not explicitly provided.
	 *
	 * @return the implicit locale to use, not null
	 */
	@Nonnull
	protected Locale getImplicitLocale() {
		Locale locale = getLocaleSupplier().get();
		return locale == null ? getFallbackLocale() : locale;
	}

	@Nonnull
	protected Map<Locale, Map<String, LocalizedString>> getLocalizedStringsByKeyByFixedLocale() {
		return localizedStringsByKeyByFixedLocale;
	}

	@Nonnull
	protected ConcurrentHashMap<Locale, List<Map<String, LocalizedString>>> getLocalizedStringsByKeysByDynamicLocale() {
		return localizedStringsByKeysByDynamicLocale;
	}

	@Nonnull
	protected List<Map<String, LocalizedString>> getLocalizedStringsByKeysForLocale(@Nonnull Locale locale) {
		requireNonNull(locale);

		return getLocalizedStringsByKeysByDynamicLocale().computeIfAbsent(locale, (ignored) -> {
			String language = locale.getLanguage();
			String script = locale.getScript();
			String country = locale.getCountry();
			String variant = locale.getVariant();
			Set<Character> extensionKeys = locale.hasExtensions() ? locale.getExtensionKeys() : Collections.emptySet();
			Set<LocalizedString> localizedStrings;
			Set<Locale> matchingLocales = new HashSet<>(5);
			List<Map<String, LocalizedString>> localizedStringsByKeys = new ArrayList<>();

			if (logger.isLoggable(Level.FINER))
				logger.finer(format("Finding strings files that match locale %s...", locale.toLanguageTag()));

			// Try most specific (matches all 5 criteria) and move back to least specific
			Locale.Builder extensionsLocaleBuilder =
					new Locale.Builder().setLanguage(language).setScript(script).setRegion(country).setVariant(variant);

			for (Character extensionKey : extensionKeys)
				extensionsLocaleBuilder.setExtension(extensionKey, locale.getExtension(extensionKey));

			Locale extensionsLocale = extensionsLocaleBuilder.build();
			matchingLocales.add(extensionsLocale);
			localizedStrings = getLocalizedStringsByLocale().get(extensionsLocale);

			if (localizedStrings != null) {
				localizedStringsByKeys.add(getLocalizedStringsByKeyByFixedLocale().get(extensionsLocale));

				if (logger.isLoggable(Level.FINER))
					logger.finer(format("A matching strings file for locale %s is %s", locale.toLanguageTag(),
							extensionsLocale.toLanguageTag()));
			}

			// Variant (4)
			Locale variantLocale =
					new Locale.Builder().setLanguage(language).setScript(script).setRegion(country).setVariant(variant)
							.build();

			if (!matchingLocales.contains(variantLocale)) {
				matchingLocales.add(variantLocale);

				localizedStrings = getLocalizedStringsByLocale().get(variantLocale);

				if (localizedStrings != null) {
					localizedStringsByKeys.add(getLocalizedStringsByKeyByFixedLocale().get(variantLocale));

					if (logger.isLoggable(Level.FINER))
						logger.finer(format("A matching strings file for locale %s is %s", locale.toLanguageTag(),
								variantLocale.toLanguageTag()));
				}
			}

			// Region (3)
			Locale regionLocale = new Locale.Builder().setLanguage(language).setScript(script).setRegion(country).build();

			if (!matchingLocales.contains(regionLocale)) {
				matchingLocales.add(regionLocale);

				localizedStrings = getLocalizedStringsByLocale().get(regionLocale);

				if (localizedStrings != null) {
					localizedStringsByKeys.add(getLocalizedStringsByKeyByFixedLocale().get(regionLocale));

					if (logger.isLoggable(Level.FINER))
						logger.finer(format("A matching strings file for locale %s is %s", locale.toLanguageTag(),
								regionLocale.toLanguageTag()));
				}
			}

			// Script (2)
			Locale scriptLocale = new Locale.Builder().setLanguage(language).setScript(script).build();

			if (!matchingLocales.contains(scriptLocale)) {
				matchingLocales.add(scriptLocale);

				localizedStrings = getLocalizedStringsByLocale().get(scriptLocale);

				if (localizedStrings != null) {
					localizedStringsByKeys.add(getLocalizedStringsByKeyByFixedLocale().get(scriptLocale));

					if (logger.isLoggable(Level.FINER))
						logger.finer(format("A matching strings file for locale %s is %s", locale.toLanguageTag(),
								scriptLocale.toLanguageTag()));
				}
			}

			// Language (1)
			Locale languageLocale = new Locale.Builder().setLanguage(language).build();

			if (!matchingLocales.contains(languageLocale)) {
				matchingLocales.add(languageLocale);

				localizedStrings = getLocalizedStringsByLocale().get(languageLocale);

				if (localizedStrings != null) {
					localizedStringsByKeys.add(getLocalizedStringsByKeyByFixedLocale().get(languageLocale));

					if (logger.isLoggable(Level.FINER))
						logger.finer(format("A matching strings file for locale %s is %s", locale.toLanguageTag(),
								languageLocale.toLanguageTag()));
				}
			}

			// Finally, add the default locale if necessary
			Locale fallbackLocale = getFallbackLocale();

			if (!matchingLocales.contains(fallbackLocale)) {
				matchingLocales.add(fallbackLocale);

				localizedStrings = getLocalizedStringsByLocale().get(fallbackLocale);

				if (localizedStrings != null) {
					localizedStringsByKeys.add(getLocalizedStringsByKeyByFixedLocale().get(fallbackLocale));

					if (logger.isLoggable(Level.FINER))
						logger.finer(format("A matching strings file for locale %s is fallback %s",
								locale.toLanguageTag(), fallbackLocale.toLanguageTag()));
				}
			}

			return Collections.unmodifiableList(localizedStringsByKeys);
		});
	}

	/**
	 * Strategies for handling localized string lookup failures.
	 */
	public enum FailureMode {
		/**
		 * The system will attempt a series of fallbacks in order to not throw an exception at runtime.
		 * <p>
		 * This mode is useful for production, where we often want program execution to continue in the face of
		 * localization errors.
		 */
		USE_FALLBACK,
		/**
		 * The system will throw an exception if a localization is missing for the specified locale.
		 * <p>
		 * This mode is useful for testing, since problems are uncovered right away when execution halts.
		 */
		FAIL_FAST
	}

	/**
	 * Builder used to construct instances of {@link DefaultStrings}.
	 * <p>
	 * This class is intended for use by a single thread.
	 *
	 * @author <a href="https://revetkn.com">Mark Allen</a>
	 */
	@NotThreadSafe
	public static class Builder {
		@Nonnull
		private final String fallbackLanguageCode;
		@Nonnull
		private final Supplier<Map<Locale, ? extends Iterable<LocalizedString>>> localizedStringSupplier;
		@Nullable
		private Supplier<Locale> localeSupplier;
		@Nullable
		private FailureMode failureMode;

		/**
		 * Constructs a strings builder with a default language code and localized string supplier.
		 * <p>
		 * The fallback language code must be an ISO 639 alpha-2 or alpha-3 language code.
		 * When a language has both an alpha-2 code and an alpha-3 code, the alpha-2 code must be used.
		 *
		 * @param fallbackLanguageCode    fallback language code, not null
		 * @param localizedStringSupplier supplier of localized strings, not null
		 */
		public Builder(@Nonnull String fallbackLanguageCode, @Nonnull Supplier<Map<Locale, ? extends Iterable<LocalizedString>>> localizedStringSupplier) {
			requireNonNull(fallbackLanguageCode);
			requireNonNull(localizedStringSupplier);

			this.fallbackLanguageCode = fallbackLanguageCode;
			this.localizedStringSupplier = localizedStringSupplier;
		}

		/**
		 * Applies a locale supplier to this builder.
		 *
		 * @param localeSupplier standard locale supplier, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@Nonnull
		public Builder localeSupplier(@Nullable Supplier<Locale> localeSupplier) {
			this.localeSupplier = localeSupplier;
			return this;
		}

		/**
		 * Applies a failure mode to this builder.
		 *
		 * @param failureMode strategy for dealing with lookup failures, may be null
		 * @return this builder instance, useful for chaining. not null
		 */
		@Nonnull
		public Builder failureMode(@Nullable FailureMode failureMode) {
			this.failureMode = failureMode;
			return this;
		}

		/**
		 * Constructs an instance of {@link DefaultStrings}.
		 *
		 * @return an instance of {@link DefaultStrings}, not null
		 */
		@Nonnull
		public DefaultStrings build() {
			return new DefaultStrings(fallbackLanguageCode, localizedStringSupplier, localeSupplier, failureMode);
		}
	}
}