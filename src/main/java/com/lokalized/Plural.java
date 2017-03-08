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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Language plural forms.
 * <p>
 * See the <a href="http://cldr.unicode.org/index/cldr-spec/plural-rules">Unicode Common Locale Data Repository</a>
 * and its <a href="http://www.unicode.org/cldr/charts/latest/supplemental/language_plural_rules.html">Language Plural Rules</a> for details.
 * <p>
 * Per the CLDR:
 * <blockquote>
 * These categories are only mnemonics -- the names don't necessarily imply the exact contents of the category.
 * For example, for both English and French the number 1 has the category one (singular).
 * <p>
 * In English, every other number has a plural form, and is given the category other.
 * French is similar, except that the number 0 also has the category one and not other or zero, because the form of
 * units qualified by 0 is also singular.
 * <p>
 * This is worth emphasizing: A common mistake is to think that "one" is only for only the number 1.
 * Instead, "one" is a category for any number that behaves like 1. So in some languages, for example,
 * one → numbers that end in "1" (like 1, 21, 151) but that don't end in 11 (like "11, 111, 10311).
 * </blockquote>
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public enum Plural {
	/**
	 * Normally the form used with 0, if it is limited to numbers whose integer values end with 0.
	 * <p>
	 * For example: {@code 0 cŵn, 0 cathod}.
	 */
	ZERO,
	/**
	 * The form used with 1.
	 * <p>
	 * For example: {@code 1 ci, 1 gath}.
	 */
	ONE,
	/**
	 * Normally the form used with 2, if it is limited to numbers whose integer values end with 2.
	 * <p>
	 * For example: {@code 2 gi, 2 gath}.
	 */
	TWO,
	/**
	 * The form that falls between {@code TWO} and {@code MANY}.
	 * <p>
	 * For example: {@code  3 chi, 3 cath}.
	 */
	FEW,
	/**
	 * The form that falls between {@code FEW} and {@code OTHER}.
	 * <p>
	 * For example: {@code 6 chi, 6 chath}.
	 */
	MANY,
	/**
	 * General "catchall" form which comprises any cases not handled by the other forms.
	 * <p>
	 * For example: {@code 4 ci, 4 cath}.
	 */
	OTHER;

	@Nonnull
	private static final Map<String, Plural> PLURALS_BY_NAME;

	static {
		PLURALS_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
				Plural.values()).collect(Collectors.toMap(plural -> plural.name(), plural -> plural)));
	}

	/**
	 * Gets the mapping of plural names to plural values.
	 *
	 * @return the mapping of plural names to plural values, not null
	 */
	@Nonnull
	public static Map<String, Plural> getPluralsByName() {
		return PLURALS_BY_NAME;
	}
}