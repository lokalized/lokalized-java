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

/**
 * Describes the relationship used to select a locale.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
public enum LocaleMatchType {
	/** No acceptable supported locale was found. */
	NONE,
	/** The requested and supported locale tags matched exactly. */
	EXACT,
	/** The requested and supported locale tags matched after canonicalization. */
	CANONICAL,
	/** The supported locale matched through a CLDR parent-locale fallback. */
	CLDR_FALLBACK,
	/** The requested and supported locales matched after likely-subtag expansion. */
	LIKELY_SUBTAG,
	/** The supported locale matched an RFC 4647 extended language range. */
	EXTENDED_RANGE,
	/** The requested and supported locales matched by primary language. */
	PRIMARY_LANGUAGE,
	/** The supported locale was selected by an acceptable wildcard range. */
	WILDCARD
}
