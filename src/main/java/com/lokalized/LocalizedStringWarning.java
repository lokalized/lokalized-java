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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Describes a non-fatal validation problem detected while loading a localized strings file.
 * <p>
 * Instances are supplied to a {@link LocalizedStringWarningHandler}. Lokalized constructs these objects;
 * application code should normally only inspect them. A warning never aborts loading on its own - the configured
 * handler decides what to do (log it, ignore it, collect it, or throw to fail the load).
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public final class LocalizedStringWarning {
	@NonNull
	private final Type type;
	@NonNull
	private final String source;
	@Nullable
	private final Locale locale;
	@Nullable
	private final String key;
	@Nullable
	private final String placeholder;
	@NonNull
	private final Set<@NonNull String> missingLanguageForms;
	@NonNull
	private final String message;

	/**
	 * The kind of problem a {@link LocalizedStringWarning} represents.
	 */
	public enum Type {
		/**
		 * A cardinality-driven placeholder omits one or more cardinal forms that its locale requires per CLDR.
		 */
		INCOMPLETE_CARDINALITY_TRANSLATIONS,
		/**
		 * An ordinality-driven placeholder omits one or more ordinal forms that its locale requires per CLDR.
		 */
		INCOMPLETE_ORDINALITY_TRANSLATIONS,
		/**
		 * A JSON resource in a classpath catalog package is not named with a valid IETF BCP 47 locale tag.
		 */
		INVALID_CLASSPATH_LOCALE_FILENAME
	}

	/**
	 * Constructs a validation warning.
	 *
	 * @param type                 the kind of problem, not null
	 * @param source               the file path or URL being loaded, not null
	 * @param locale               the locale the file is being loaded for, not null
	 * @param key                  the translation key that triggered the warning, not null
	 * @param placeholder          the placeholder within the key that triggered the warning, not null
	 * @param missingLanguageForms the missing language-form names in file format (e.g. {@code CARDINALITY_MANY}), not null
	 * @param message              a human-readable description of the warning, not null
	 */
	LocalizedStringWarning(@NonNull Type type,
																@NonNull String source,
																@NonNull Locale locale,
																@NonNull String key,
																@NonNull String placeholder,
																@NonNull Set<@NonNull String> missingLanguageForms,
																@NonNull String message) {
		requireNonNull(type);
		requireNonNull(source);
		requireNonNull(locale);
		requireNonNull(key);
		requireNonNull(placeholder);
		requireNonNull(missingLanguageForms);
		requireNonNull(message);

		this.type = type;
		this.source = source;
		this.locale = locale;
		this.key = key;
		this.placeholder = placeholder;
		this.missingLanguageForms = Collections.unmodifiableSet(new LinkedHashSet<>(missingLanguageForms));
		this.message = message;
	}

	LocalizedStringWarning(@NonNull Type type,
											@NonNull String source,
											@NonNull String message) {
		requireNonNull(type);
		requireNonNull(source);
		requireNonNull(message);

		this.type = type;
		this.source = source;
		this.locale = null;
		this.key = null;
		this.placeholder = null;
		this.missingLanguageForms = Collections.emptySet();
		this.message = message;
	}

	/**
	 * Gets the kind of problem this warning represents.
	 *
	 * @return the warning type, not null
	 */
	@NonNull
	public Type getType() {
		return this.type;
	}

	/**
	 * Gets the file path or URL that was being loaded.
	 *
	 * @return the source, not null
	 */
	@NonNull
	public String getSource() {
		return this.source;
	}

	/**
	 * Gets the locale the file was being loaded for, when the warning applies to a parsed locale catalog.
	 *
	 * @return the locale, if applicable, not null
	 */
	@NonNull
	public Optional<@NonNull Locale> getLocale() {
		return Optional.ofNullable(this.locale);
	}

	/**
	 * Gets the translation key that triggered the warning, when applicable.
	 *
	 * @return the translation key, if applicable, not null
	 */
	@NonNull
	public Optional<@NonNull String> getKey() {
		return Optional.ofNullable(this.key);
	}

	/**
	 * Gets the placeholder within the key that triggered the warning, when applicable.
	 *
	 * @return the placeholder, if applicable, not null
	 */
	@NonNull
	public Optional<@NonNull String> getPlaceholder() {
		return Optional.ofNullable(this.placeholder);
	}

	/**
	 * Gets the missing language-form names, in localized strings file format (e.g. {@code CARDINALITY_MANY}).
	 *
	 * @return the missing language-form names, not null
	 */
	@NonNull
	public Set<@NonNull String> getMissingLanguageForms() {
		return this.missingLanguageForms;
	}

	/**
	 * Gets a human-readable description of the warning.
	 *
	 * @return the message, not null
	 */
	@NonNull
	public String getMessage() {
		return this.message;
	}

	@Override
	@NonNull
	public String toString() {
		return this.message;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object)
			return true;

		if (!(object instanceof LocalizedStringWarning))
			return false;

		LocalizedStringWarning that = (LocalizedStringWarning) object;

		return this.type == that.type
				&& Objects.equals(this.source, that.source)
				&& Objects.equals(this.locale, that.locale)
				&& Objects.equals(this.key, that.key)
				&& Objects.equals(this.placeholder, that.placeholder)
				&& Objects.equals(this.missingLanguageForms, that.missingLanguageForms)
				&& Objects.equals(this.message, that.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.type, this.source, this.locale, this.key, this.placeholder, this.missingLanguageForms,
				this.message);
	}
}
