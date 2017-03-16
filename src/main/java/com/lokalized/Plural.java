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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

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
public enum Plural implements LanguageForm {
	/**
	 * Normally the form used with 0, if it is limited to numbers whose integer values end with 0.
	 * <p>
	 * For example: in Welsh, {@code 0 cŵn, 0 cathod}.
	 */
	ZERO,
	/**
	 * The form used with 1.
	 * <p>
	 * For example: in Welsh, {@code 1 ci, 1 gath}.
	 */
	ONE,
	/**
	 * Normally the form used with 2, if it is limited to numbers whose integer values end with 2.
	 * <p>
	 * For example: in Welsh, {@code 2 gi, 2 gath}.
	 */
	TWO,
	/**
	 * The form that falls between {@code TWO} and {@code MANY}.
	 * <p>
	 * For example: in Welsh, {@code  3 chi, 3 cath}.
	 */
	FEW,
	/**
	 * The form that falls between {@code FEW} and {@code OTHER}.
	 * <p>
	 * For example: in Welsh, {@code 6 chi, 6 chath}.
	 */
	MANY,
	/**
	 * General "catchall" form which comprises any cases not handled by the other forms.
	 * <p>
	 * For example: in Welsh, {@code 4 ci, 4 cath}.
	 */
	OTHER;

	@Nonnull
	static final Map<String, Plural> PLURALS_BY_NAME;

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

	/**
	 * Gets an appropriate plural value for the given number and locale.
	 * <p>
	 * See <a href="http://translate.sourceforge.net/wiki/l10n/pluralforms">http://translate.sourceforge.net/wiki/l10n/pluralforms</a>
	 * for a cheat sheet.
	 *
	 * @param number the number that drives pluralization, may be null
	 * @param locale the locale that drives pluralization, not null
	 * @return an appropriate plural value, not null
	 * @throws UnsupportedLocaleException if the locale is not recognized
	 */
	@Nonnull
	public static Plural pluralForNumber(@Nullable Number number, @Nonnull Locale locale) {
		requireNonNull(locale);

		Optional<Function<Number, Plural>> pluralValueFunction = PluralFamily.pluralValueFunctionForLocale(locale);

		if (!pluralValueFunction.isPresent())
			throw new UnsupportedLocaleException(locale);

		return pluralValueFunction.get().apply(number);
	}

	/**
	 * Plural forms grouped by language family.
	 * <p>
	 * Each family has a distinct plural calculation rule.
	 * <p>
	 * For example, Germanic languages support two {@link Plural} types: {@link Plural#ONE} for {@code 1}
	 * and {@link Plural#OTHER} for all other values.
	 * <p>
	 * See <a href="https://developer.mozilla.org/en-US/docs/Mozilla/Localization/Localization_and_Plurals">https://developer.mozilla.org/en-US/docs/Mozilla/Localization/Localization_and_Plurals</a>
	 * for a cheat sheet.
	 */
	enum PluralFamily {
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Aymara</li>
		 * <li>Burmese</li>
		 * <li>Chiga</li>
		 * <li>Chinese</li>
		 * <li>Dzongkha</li>
		 * <li>Georgian</li>
		 * <li>Indonesian</li>
		 * <li>Japanese</li>
		 * <li>Kazakh</li>
		 * <li>Khmer</li>
		 * <li>Kirghiz</li>
		 * <li>Korean</li>
		 * <li>Lao</li>
		 * <li>Lojban</li>
		 * <li>Malay</li>
		 * <li>Persian</li>
		 * <li>Sundanese</li>
		 * <li>Tatar</li>
		 * <li>Thai</li>
		 * <li>Uighur</li>
		 * <li>Vietnamese</li>
		 * <li>Wolof</li>
		 * <li>Yakut</li>
		 * </ul>
		 */
		NO_PLURALS,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Afrikaans</li>
		 * <li>Albanian</li>
		 * <li>Angika</li>
		 * <li>Aragonese</li>
		 * <li>Armenian</li>
		 * <li>Assamese</li>
		 * <li>Asturian</li>
		 * <li>Azerbaijani</li>
		 * <li>Basque</li>
		 * <li>Bengali</li>
		 * <li>Bodo</li>
		 * <li>Bulgarian</li>
		 * <li>Catalan</li>
		 * <li>Chhattisgarhi</li>
		 * <li>Danish</li>
		 * <li>Dogri</li>
		 * <li>Dutch</li>
		 * <li>English</li>
		 * <li>Esperanto</li>
		 * <li>Estonian</li>
		 * <li>Faroese</li>
		 * <li>Finnish</li>
		 * <li>Frisian</li>
		 * <li>Friulian</li>
		 * <li>Fulah</li>
		 * <li>Gallegan</li>
		 * <li>German</li>
		 * <li>Greek</li>
		 * <li>Greenlandic</li>
		 * <li>Gujarati</li>
		 * <li>Hausa</li>
		 * <li>Hebrew</li>
		 * <li>Hindi</li>
		 * <li>Hungarian</li>
		 * <li>Interlingua</li>
		 * <li>Italian</li>
		 * <li>Kannada</li>
		 * <li>Kinyarwanda</li>
		 * <li>Kurdish</li>
		 * <li>Luxembourgish</li>
		 * <li>Maithili</li>
		 * <li>Malayalam</li>
		 * <li>Manipuri</li>
		 * <li>Marathi</li>
		 * <li>Mongolian</li>
		 * <li>Nahuatl</li>
		 * <li>Neapolitan</li>
		 * <li>Nepali</li>
		 * <li>Northern Sami</li>
		 * <li>Norwegian Bokmål</li>
		 * <li>Norwegian Nynorsk</li>
		 * <li>Oriya</li>
		 * <li>Panjabi</li>
		 * <li>Papiamento</li>
		 * <li>Pedi</li>
		 * <li>Piemontese</li>
		 * <li>Portuguese</li>
		 * <li>Pushto</li>
		 * <li>Raeto-Romance</li>
		 * <li>Santali</li>
		 * <li>Scots</li>
		 * <li>Sindhi</li>
		 * <li>Sinhalese</li>
		 * <li>Somali</li>
		 * <li>Songhai</li>
		 * <li>Spanish</li>
		 * <li>Spanish (Argentina)</li>
		 * <li>Swahili</li>
		 * <li>Swedish</li>
		 * <li>Tamil</li>
		 * <li>Telugu</li>
		 * <li>Turkmen</li>
		 * <li>Urdu</li>
		 * <li>Yoruba</li>
		 * </ul>
		 */
		PLURAL_IF_NOT_EQUAL_1,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Acoli</li>
		 * <li>Akan</li>
		 * <li>Amharic</li>
		 * <li>Filipino</li>
		 * <li>French</li>
		 * <li>Gun</li>
		 * <li>Lingala</li>
		 * <li>Malagasy</li>
		 * <li>Maori</li>
		 * <li>Mapudungun</li>
		 * <li>Mauritian Creole</li>
		 * <li>Occitan</li>
		 * <li>Portuguese (Brazil)</li>
		 * <li>Tajik</li>
		 * <li>Tigrinya</li>
		 * <li>Turkish</li>
		 * <li>Uzbek</li>
		 * <li>Walloon</li>
		 * </ul>
		 */
		PLURAL_IF_GREATER_THAN_1,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Belarusian</li>
		 * <li>Bosnian</li>
		 * <li>Croatian</li>
		 * <li>Russian</li>
		 * <li>Serbian</li>
		 * <li>Ukrainian</li>
		 * </ul>
		 */
		SLAVIC_1,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Czech</li>
		 * <li>Slovak</li>
		 * </ul>
		 */
		SLAVIC_2,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Slovenian</li>
		 * <li>Sorbian</li>
		 * </ul>
		 */
		SLAVIC_3,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Latvian</li>
		 * </ul>
		 */
		LATVIAN,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Scottish Gaelic</li>
		 * </ul>
		 */
		SCOTTISH_GAELIC,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Romanian</li>
		 * </ul>
		 */
		ROMANIAN,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Lithuanian</li>
		 * </ul>
		 */
		LITHUANIAN,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Polish</li>
		 * </ul>
		 */
		POLISH,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Irish Gaelic</li>
		 * </ul>
		 */
		IRISH_GAELIC,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Arabic</li>
		 * </ul>
		 */
		ARABIC,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Macedonian</li>
		 * </ul>
		 */
		MACEDONIAN,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Icelandic</li>
		 * </ul>
		 */
		ICELANDIC,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Breton</li>
		 * </ul>
		 */
		BRETON,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Welsh</li>
		 * </ul>
		 */
		WELSH,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Kashubian</li>
		 * </ul>
		 */
		KASHUBIAN,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Javanese</li>
		 * </ul>
		 */
		JAVANESE,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Cornish</li>
		 * </ul>
		 */
		CORNISH,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Montenegro</li>
		 * </ul>
		 */
		MONTENEGRO,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Mandinka</li>
		 * </ul>
		 */
		MANDINKA,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Maltese</li>
		 * </ul>
		 */
		MALTESE;

		@Nonnull
		static final Map<PluralFamily, Function<Number, Plural>> PLURAL_VALUE_FUNCTIONS_BY_PLURAL_FAMILY;

		@Nonnull
		static final Map<String, PluralFamily> PLURAL_FAMILIES_BY_LANGUAGE_TAG;

		static {
			PLURAL_VALUE_FUNCTIONS_BY_PLURAL_FAMILY = Collections.unmodifiableMap(new HashMap<PluralFamily, Function<Number, Plural>>() {{
				put(PluralFamily.ARABIC, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.BRETON, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.CORNISH, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.ICELANDIC, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.IRISH_GAELIC, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.JAVANESE, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.KASHUBIAN, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.LATVIAN, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.LITHUANIAN, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.MACEDONIAN, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.MALTESE, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.MANDINKA, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.MONTENEGRO, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.NO_PLURALS, (number) -> {
					return OTHER;
				});
				put(PluralFamily.PLURAL_IF_GREATER_THAN_1, (number) -> {
					if (number == null)
						return OTHER;

					return number.doubleValue() > 1 ? ONE : OTHER;
				});
				put(PluralFamily.PLURAL_IF_NOT_EQUAL_1, (number) -> {
					if (number == null)
						return OTHER;

					return number.doubleValue() == 1 ? ONE : OTHER;
				});
				put(PluralFamily.POLISH, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.ROMANIAN, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.SCOTTISH_GAELIC, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.SLAVIC_1, (number) -> {
					if (number == null)
						return OTHER;

					double value = number.doubleValue();

					if (value % 10 == 1 && value % 100 != 11)
						return ONE;
					if (value % 10 >= 2 && value % 10 <= 4 && value % 100 < 12 && value % 100 > 14)
						return FEW;
					if (value % 10 == 0 || (value % 10 >= 5 && value % 10 <= 9) || (value % 100 >= 11 && value % 100 <= 14))
						return MANY;

					// Fallback
					return OTHER;
				});
				put(PluralFamily.SLAVIC_2, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.SLAVIC_3, (number) -> {
					throw new UnsupportedOperationException();
				});
				put(PluralFamily.WELSH, (number) -> {
					throw new UnsupportedOperationException();
				});
			}});

			PLURAL_FAMILIES_BY_LANGUAGE_TAG = Collections.unmodifiableMap(new HashMap<String, PluralFamily>() {{
				put("ach", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Acholi
				put("af", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Afrikaans
				put("ak", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Akan
				put("am", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Amharic
				put("an", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Aragonese
				put("anp", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Angika
				put("ar", PluralFamily.ARABIC); // Arabic
				put("arn", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Mapudungun
				put("as", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Assamese
				put("ast", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Asturian
				put("ay", PluralFamily.NO_PLURALS); // Aymará
				put("az", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Azerbaijani
				put("be", PluralFamily.SLAVIC_1); // Belarusian
				put("bg", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Bulgarian
				put("bn", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Bengali
				put("br", PluralFamily.BRETON); // Breton
				put("brx", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Bodo
				put("bs", PluralFamily.SLAVIC_1); // Bosnian
				put("ca", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Catalan
				put("cgg", PluralFamily.NO_PLURALS); // Chiga
				put("cs", PluralFamily.SLAVIC_2); // Czech
				put("csb", PluralFamily.KASHUBIAN); // Kashubian
				put("cy", PluralFamily.WELSH); // Welsh
				put("da", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Danish
				put("de", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // German
				put("doi", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Dogri
				put("dz", PluralFamily.NO_PLURALS); // Dzongkha
				put("el", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Greek
				put("en", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // English
				put("eo", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Esperanto
				put("es", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Spanish
				put("es-AR", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Argentinean Spanish
				put("et", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Estonian
				put("eu", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Basque
				put("fa", PluralFamily.NO_PLURALS); // Persian
				put("ff", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Fulah
				put("fi", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Finnish
				put("fil", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Filipino
				put("fo", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Faroese
				put("fr", PluralFamily.PLURAL_IF_GREATER_THAN_1); // French
				put("fur", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Friulian
				put("fy", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Frisian
				put("ga", PluralFamily.IRISH_GAELIC); // Irish Gaelic
				put("gd", PluralFamily.SCOTTISH_GAELIC); // Scottish Gaelic
				put("gl", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Galician
				put("gu", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Gujarati
				put("gun", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Gun
				put("ha", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Hausa
				put("he", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Hebrew
				put("hi", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Hindi
				put("hne", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Chhattisgarhi
				put("hr", PluralFamily.SLAVIC_1); // Croatian
				put("hu", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Hungarian
				put("hy", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Armenian
				put("ia", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Interlingua
				put("id", PluralFamily.NO_PLURALS); // Indonesian
				put("is", PluralFamily.ICELANDIC); // Icelandic
				put("it", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Italian
				put("ja", PluralFamily.NO_PLURALS); // Japanese
				put("jbo", PluralFamily.NO_PLURALS); // Lojban
				put("jv", PluralFamily.JAVANESE); // Javanese
				put("ka", PluralFamily.NO_PLURALS); // Georgian
				put("kk", PluralFamily.NO_PLURALS); // Kazakh
				put("kl", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Greenlandic
				put("km", PluralFamily.NO_PLURALS); // Khmer
				put("kn", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Kannada
				put("ko", PluralFamily.NO_PLURALS); // Korean
				put("ku", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Kurdish
				put("kw", PluralFamily.CORNISH); // Cornish
				put("ky", PluralFamily.NO_PLURALS); // Kyrgyz
				put("lb", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Letzeburgesch
				put("ln", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Lingala
				put("lo", PluralFamily.NO_PLURALS); // Lao
				put("lt", PluralFamily.LITHUANIAN); // Lithuanian
				put("lv", PluralFamily.LATVIAN); // Latvian
				put("mai", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Maithili
				put("me", PluralFamily.MONTENEGRO); // Montenegro
				put("mfe", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Mauritian Creole
				put("mg", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Malagasy
				put("mi", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Maori
				put("mk", PluralFamily.MACEDONIAN); // Macedonian
				put("ml", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Malayalam
				put("mn", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Mongolian
				put("mni", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Manipuri
				put("mnk", PluralFamily.MANDINKA); // Mandinka
				put("mr", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Marathi
				put("ms", PluralFamily.NO_PLURALS); // Malay
				put("mt", PluralFamily.MALTESE); // Maltese
				put("my", PluralFamily.NO_PLURALS); // Burmese
				put("nah", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Nahuatl
				put("nap", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Neapolitan
				put("nb", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Norwegian Bokmål
				put("ne", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Nepali
				put("nl", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Dutch
				put("nn", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Norwegian Nyorsk
				put("nso", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Northern Sotho
				put("oc", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Occitan
				put("or", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Oriya
				put("pa", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Punjabi
				put("pap", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Papiamento
				put("pl", PluralFamily.POLISH); // Polish
				put("pms", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Piemontese
				put("ps", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Pashto
				put("pt", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Portuguese
				put("pt-BR", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Brazilian Portuguese
				put("rm", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Romansh
				put("ro", PluralFamily.ROMANIAN); // Romanian
				put("ru", PluralFamily.SLAVIC_1); // Russian
				put("rw", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Kinyarwanda
				put("sah", PluralFamily.NO_PLURALS); // Yakut
				put("sat", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Santali
				put("sco", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Scots
				put("sd", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Sindhi
				put("se", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Northern Sami
				put("si", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Sinhala
				put("sk", PluralFamily.SLAVIC_2); // Slovak
				put("sl", PluralFamily.SLAVIC_3); // Slovenian
				put("so", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Somali
				put("son", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Songhay
				put("sq", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Albanian
				put("sr", PluralFamily.SLAVIC_1); // Serbian
				put("su", PluralFamily.NO_PLURALS); // Sudanese
				put("sv", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Swedish
				put("sw", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Swahili
				put("ta", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Tamil
				put("te", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Telugu
				put("tg", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Tajik
				put("th", PluralFamily.NO_PLURALS); // Thai
				put("ti", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Tigrinya
				put("tk", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Turkmen
				put("tr", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Turkish
				put("tt", PluralFamily.NO_PLURALS); // Tatar
				put("ug", PluralFamily.NO_PLURALS); // Uyghur
				put("uk", PluralFamily.SLAVIC_1); // Ukrainian
				put("ur", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Urdu
				put("uz", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Uzbek
				put("vi", PluralFamily.NO_PLURALS); // Vietnamese
				put("wa", PluralFamily.PLURAL_IF_GREATER_THAN_1); // Walloon
				put("wen", PluralFamily.SLAVIC_3); // Sorbian
				put("wo", PluralFamily.NO_PLURALS); // Wolof
				put("yo", PluralFamily.PLURAL_IF_NOT_EQUAL_1); // Yoruba
				put("zh", PluralFamily.NO_PLURALS); // Chinese
			}});
		}

		/**
		 * Gets an appropriate plural family for the given locale.
		 *
		 * @param locale the locale to check, not null
		 * @return the appropriate plural family (if one exists) for the given locale, not null
		 */
		@Nonnull
		static Optional<PluralFamily> pluralFamilyForLocale(@Nonnull Locale locale) {
			requireNonNull(locale);

			String language = locale.getLanguage();
			String country = locale.getCountry();

			PluralFamily pluralFamily = null;

			if (language != null && country != null)
				pluralFamily = PLURAL_FAMILIES_BY_LANGUAGE_TAG.get(format("%s-%s", language, country));

			if (pluralFamily != null)
				return Optional.of(pluralFamily);

			if (language != null)
				pluralFamily = PLURAL_FAMILIES_BY_LANGUAGE_TAG.get(language);

			return Optional.ofNullable(pluralFamily);
		}

		/**
		 * Gets an appropriate plural value determination function for the given locale.
		 *
		 * @param locale the locale to check, not null
		 * @return the appropriate plural value determination function (if one exists) for the given locale, not null
		 */
		@Nonnull
		public static Optional<Function<Number, Plural>> pluralValueFunctionForLocale(@Nonnull Locale locale) {
			requireNonNull(locale);

			Optional<PluralFamily> pluralFamily = pluralFamilyForLocale(locale);

			if (!pluralFamily.isPresent())
				return Optional.empty();

			return Optional.ofNullable(PLURAL_VALUE_FUNCTIONS_BY_PLURAL_FAMILY.get(pluralFamily.get()));
		}
	}
}