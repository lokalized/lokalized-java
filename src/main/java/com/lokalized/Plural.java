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

	/**
	 * Plural forms grouped by language family.
	 * <p>
	 * Each family has a distinct plural calculation rule.
	 * <p>
	 * For example, Germanic languages can supply two {@link Plural} types: {@link Plural#ONE} for {@code 1}
	 * and {@link Plural#OTHER} for all other values.
	 * <p>
	 * See <a href="https://developer.mozilla.org/en-US/docs/Mozilla/Localization/Localization_and_Plurals">https://developer.mozilla.org/en-US/docs/Mozilla/Localization/Localization_and_Plurals</a>
	 * for a cheat sheet.
	 */
	private enum PluralFamily {
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Chinese</li>
		 * <li>Japanese</li>
		 * <li>Korean</li>
		 * <li>Persian</li>
		 * <li>Turkic/Altaic (Turkish)</li>
		 * <li>Thai</li>
		 * <li>Lao</li>
		 * </ul>
		 */
		ASIAN,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Danish</li>
		 * <li>Dutch</li>
		 * <li>English</li>
		 * <li>Faroese</li>
		 * <li>Frisian</li>
		 * <li>German</li>
		 * <li>Norwegian</li>
		 * <li>Swedish</li>
		 * <li>Estonian</li>
		 * <li>Finnish</li>
		 * <li>Hungarian</li>
		 * <li>Basque</li>
		 * <li>Greek</li>
		 * <li>Hebrew</li>
		 * <li>Italian</li>
		 * <li>Portuguese</li>
		 * <li>Spanish</li>
		 * <li>Catalan</li>
		 * <li>Vietnamese</li>
		 * </ul>
		 */
		GERMANIC,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>French</li>
		 * <li>Brazilian Portuguese</li>
		 * </ul>
		 */
		ROMANIC_1,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Belarusian</li>
		 * <li>Bosnian</li>
		 * <li>Croatian</li>
		 * <li>Serbian</li>
		 * <li>Russian</li>
		 * <li>Ukrainian</li>
		 * </ul>
		 */
		SLAVIC_1,
		/**
		 * Languages include:
		 * <p>
		 * <ul>
		 * <li>Slovak</li>
		 * <li>Czech</li>
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
		private static final Map<PluralFamily, Function<Number, Plural>> PLURAL_VALUE_FUNCTIONS_BY_PLURAL_FAMILY;

		@Nonnull
		private static final Map<String, PluralFamily> PLURAL_FAMILIES_BY_LANGUAGE_TAG;

		static {
			PLURAL_VALUE_FUNCTIONS_BY_PLURAL_FAMILY = Collections.unmodifiableMap(new HashMap<PluralFamily, Function<Number, Plural>>() {{
				put(PluralFamily.SLAVIC_1, (number) -> {
					if (number == null)
						return OTHER;

					// TODO: finish

					// Fallback
					return OTHER;
				});
			}});

			PLURAL_FAMILIES_BY_LANGUAGE_TAG = Collections.unmodifiableMap(new HashMap<String, PluralFamily>() {{
				put("ach", PluralFamily.ROMANIC_1); // Acholi
				put("af", PluralFamily.GERMANIC); // Afrikaans
				put("ak", PluralFamily.ROMANIC_1); // Akan
				put("am", PluralFamily.ROMANIC_1); // Amharic
				put("an", PluralFamily.GERMANIC); // Aragonese
				put("anp", PluralFamily.GERMANIC); // Angika
				put("ar", PluralFamily.ARABIC); // Arabic
				put("arn", PluralFamily.ROMANIC_1); // Mapudungun
				put("as", PluralFamily.GERMANIC); // Assamese
				put("ast", PluralFamily.GERMANIC); // Asturian
				put("ay", PluralFamily.ASIAN); // Aymará
				put("az", PluralFamily.GERMANIC); // Azerbaijani
				put("be", PluralFamily.SLAVIC_1); // Belarusian
				put("bg", PluralFamily.GERMANIC); // Bulgarian
				put("bn", PluralFamily.GERMANIC); // Bengali
				put("br", PluralFamily.BRETON); // Breton
				put("brx", PluralFamily.GERMANIC); // Bodo
				put("bs", PluralFamily.SLAVIC_1); // Bosnian
				put("ca", PluralFamily.GERMANIC); // Catalan
				put("cgg", PluralFamily.ASIAN); // Chiga
				put("cs", PluralFamily.SLAVIC_2); // Czech
				put("csb", PluralFamily.KASHUBIAN); // Kashubian
				put("cy", PluralFamily.WELSH); // Welsh
				put("da", PluralFamily.GERMANIC); // Danish
				put("de", PluralFamily.GERMANIC); // German
				put("doi", PluralFamily.GERMANIC); // Dogri
				put("dz", PluralFamily.ASIAN); // Dzongkha
				put("el", PluralFamily.GERMANIC); // Greek
				put("en", PluralFamily.GERMANIC); // English
				put("eo", PluralFamily.GERMANIC); // Esperanto
				put("es", PluralFamily.GERMANIC); // Spanish
				put("es-AR", PluralFamily.GERMANIC); // Argentinean Spanish
				put("et", PluralFamily.GERMANIC); // Estonian
				put("eu", PluralFamily.GERMANIC); // Basque
				put("fa", PluralFamily.ASIAN); // Persian
				put("ff", PluralFamily.GERMANIC); // Fulah
				put("fi", PluralFamily.GERMANIC); // Finnish
				put("fil", PluralFamily.ROMANIC_1); // Filipino
				put("fo", PluralFamily.GERMANIC); // Faroese
				put("fr", PluralFamily.ROMANIC_1); // French
				put("fur", PluralFamily.GERMANIC); // Friulian
				put("fy", PluralFamily.GERMANIC); // Frisian
				put("ga", PluralFamily.IRISH_GAELIC); // Irish Gaelic
				put("gd", PluralFamily.SCOTTISH_GAELIC); // Scottish Gaelic
				put("gl", PluralFamily.GERMANIC); // Galician
				put("gu", PluralFamily.GERMANIC); // Gujarati
				put("gun", PluralFamily.ROMANIC_1); // Gun
				put("ha", PluralFamily.GERMANIC); // Hausa
				put("he", PluralFamily.GERMANIC); // Hebrew
				put("hi", PluralFamily.GERMANIC); // Hindi
				put("hne", PluralFamily.GERMANIC); // Chhattisgarhi
				put("hr", PluralFamily.SLAVIC_1); // Croatian
				put("hu", PluralFamily.GERMANIC); // Hungarian
				put("hy", PluralFamily.GERMANIC); // Armenian
				put("ia", PluralFamily.GERMANIC); // Interlingua
				put("id", PluralFamily.ASIAN); // Indonesian
				put("is", PluralFamily.ICELANDIC); // Icelandic
				put("it", PluralFamily.GERMANIC); // Italian
				put("ja", PluralFamily.ASIAN); // Japanese
				put("jbo", PluralFamily.ASIAN); // Lojban
				put("jv", PluralFamily.JAVANESE); // Javanese
				put("ka", PluralFamily.ASIAN); // Georgian
				put("kk", PluralFamily.ASIAN); // Kazakh
				put("kl", PluralFamily.GERMANIC); // Greenlandic
				put("km", PluralFamily.ASIAN); // Khmer
				put("kn", PluralFamily.GERMANIC); // Kannada
				put("ko", PluralFamily.ASIAN); // Korean
				put("ku", PluralFamily.GERMANIC); // Kurdish
				put("kw", PluralFamily.CORNISH); // Cornish
				put("ky", PluralFamily.ASIAN); // Kyrgyz
				put("lb", PluralFamily.GERMANIC); // Letzeburgesch
				put("ln", PluralFamily.ROMANIC_1); // Lingala
				put("lo", PluralFamily.ASIAN); // Lao
				put("lt", PluralFamily.LITHUANIAN); // Lithuanian
				put("lv", PluralFamily.LATVIAN); // Latvian
				put("mai", PluralFamily.GERMANIC); // Maithili
				put("me", PluralFamily.MONTENEGRO); // Montenegro
				put("mfe", PluralFamily.ROMANIC_1); // Mauritian Creole
				put("mg", PluralFamily.ROMANIC_1); // Malagasy
				put("mi", PluralFamily.ROMANIC_1); // Maori
				put("mk", PluralFamily.MACEDONIAN); // Macedonian
				put("ml", PluralFamily.GERMANIC); // Malayalam
				put("mn", PluralFamily.GERMANIC); // Mongolian
				put("mni", PluralFamily.GERMANIC); // Manipuri
				put("mnk", PluralFamily.MANDINKA); // Mandinka
				put("mr", PluralFamily.GERMANIC); // Marathi
				put("ms", PluralFamily.ASIAN); // Malay
				put("mt", PluralFamily.MALTESE); // Maltese
				put("my", PluralFamily.ASIAN); // Burmese
				put("nah", PluralFamily.GERMANIC); // Nahuatl
				put("nap", PluralFamily.GERMANIC); // Neapolitan
				put("nb", PluralFamily.GERMANIC); // Norwegian Bokmal
				put("ne", PluralFamily.GERMANIC); // Nepali
				put("nl", PluralFamily.GERMANIC); // Dutch
				put("nn", PluralFamily.GERMANIC); // Norwegian Nyorsk
				put("nso", PluralFamily.GERMANIC); // Northern Sotho
				put("oc", PluralFamily.ROMANIC_1); // Occitan
				put("or", PluralFamily.GERMANIC); // Oriya
				put("pa", PluralFamily.GERMANIC); // Punjabi
				put("pap", PluralFamily.GERMANIC); // Papiamento
				put("pl", PluralFamily.POLISH); // Polish
				put("pms", PluralFamily.GERMANIC); // Piemontese
				put("ps", PluralFamily.GERMANIC); // Pashto
				put("pt", PluralFamily.GERMANIC); // Portuguese
				put("pt-BR", PluralFamily.ROMANIC_1); // Brazilian Portuguese
				put("rm", PluralFamily.GERMANIC); // Romansh
				put("ro", PluralFamily.ROMANIAN); // Romanian
				put("ru", PluralFamily.SLAVIC_1); // Russian
				put("rw", PluralFamily.GERMANIC); // Kinyarwanda
				put("sah", PluralFamily.ASIAN); // Yakut
				put("sat", PluralFamily.GERMANIC); // Santali
				put("sco", PluralFamily.GERMANIC); // Scots
				put("sd", PluralFamily.GERMANIC); // Sindhi
				put("se", PluralFamily.GERMANIC); // Northern Sami
				put("si", PluralFamily.GERMANIC); // Sinhala
				put("sk", PluralFamily.SLAVIC_2); // Slovak
				put("sl", PluralFamily.SLAVIC_3); // Slovenian
				put("so", PluralFamily.GERMANIC); // Somali
				put("son", PluralFamily.GERMANIC); // Songhay
				put("sq", PluralFamily.GERMANIC); // Albanian
				put("sr", PluralFamily.SLAVIC_1); // Serbian
				put("su", PluralFamily.ASIAN); // Sudanese
				put("sv", PluralFamily.GERMANIC); // Swedish
				put("sw", PluralFamily.GERMANIC); // Swahili
				put("ta", PluralFamily.GERMANIC); // Tamil
				put("te", PluralFamily.GERMANIC); // Telugu
				put("tg", PluralFamily.ROMANIC_1); // Tajik
				put("th", PluralFamily.ASIAN); // Thai
				put("ti", PluralFamily.ROMANIC_1); // Tigrinya
				put("tk", PluralFamily.GERMANIC); // Turkmen
				put("tr", PluralFamily.ROMANIC_1); // Turkish
				put("tt", PluralFamily.ASIAN); // Tatar
				put("ug", PluralFamily.ASIAN); // Uyghur
				put("uk", PluralFamily.SLAVIC_1); // Ukrainian
				put("ur", PluralFamily.GERMANIC); // Urdu
				put("uz", PluralFamily.ROMANIC_1); // Uzbek
				put("vi", PluralFamily.ASIAN); // Vietnamese
				put("wa", PluralFamily.ROMANIC_1); // Walloon
				put("wen", PluralFamily.SLAVIC_3); // Sorbian
				put("wo", PluralFamily.ASIAN); // Wolof
				put("yo", PluralFamily.GERMANIC); // Yoruba
				put("zh", PluralFamily.ASIAN); // Chinese
			}});
		}

		public static Optional<PluralFamily> pluralFamilyForLocale(@Nonnull Locale locale) {
			requireNonNull(locale);

			// TODO: finish

			throw new UnsupportedOperationException();
		}
	}

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

	/**
	 * Gets an appropriate plural value for the given number and locale.
	 * <p>
	 * See <a href="http://translate.sourceforge.net/wiki/l10n/pluralforms">http://translate.sourceforge.net/wiki/l10n/pluralforms</a>
	 * for a cheat sheet.
	 *
	 * @param number the number that drives pluralization, may be null
	 * @param locale the locale that drives pluralization, not null
	 * @return an appropriate plural value, not null
	 */
	@Nonnull
	public static Plural pluralForNumber(@Nullable Number number, @Nonnull Locale locale) {
		requireNonNull(locale);

		// Default to OTHER if no number provided
		if (number == null)
			return OTHER;

		// TODO: finish

		// Not sure what else to do?  Fall back to OTHER
		return OTHER;
	}
}