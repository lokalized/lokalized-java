/*
 * Copyright 2017-2022 Product Mog LLC, 2022-2025 Revetware LLC.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents the phonetic onset category of a word, used to select
 * context-appropriate word forms in localized strings.
 * <p>
 * Many languages require different word forms based on the sound that
 * follows. For example, English uses "a" before consonant sounds and
 * "an" before vowel sounds. Italian has more complex rules requiring
 * different articles before vowels, s+consonant clusters, and certain
 * other onsets.
 * <p>
 * The phonetic category of a word is determined at runtime by a
 * user-supplied resolver function, since correct classification often
 * requires language-specific knowledge and exception handling.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 1.2.0
 */
public enum Phonetic implements LanguageForm {
	/**
	 * Word begins with a vowel sound.
	 * <p>
	 * Applies to most languages. In English, triggers "an" instead of "a".
	 * In French, triggers elision (le/la → l'). In Italian, triggers
	 * l' for singular articles.
	 * <p>
	 * Note: Classification is by <em>sound</em>, not spelling. English
	 * "hour" is phonetically vowel-initial; "university" is not.
	 */
	VOWEL,

	/**
	 * Word begins with a typical consonant sound.
	 * <p>
	 * This is the default category for words not matching any other
	 * phonetic pattern. In English, triggers "a" instead of "an".
	 * In Italian, triggers "il" for masculine singular nouns.
	 */
	CONSONANT,

	/**
	 * Word begins with a silent H, making it phonetically vowel-initial.
	 * <p>
	 * Primarily applies to <strong>English</strong> and <strong>French</strong>.
	 * <p>
	 * English examples: "hour", "honor", "heir" → "an hour"
	 * <p>
	 * French examples (h muet): "homme", "heure" → "l'homme"
	 */
	H_SILENT,

	/**
	 * Word begins with an aspirated (pronounced) H.
	 * <p>
	 * Primarily applies to <strong>English</strong> and <strong>French</strong>.
	 * <p>
	 * English examples: "house", "happy" → "a house"
	 * <p>
	 * French examples (h aspiré): "héros", "haricot" → "le héros" (no elision)
	 */
	H_ASPIRATED,

	/**
	 * Word begins with s + consonant cluster (s impura).
	 * <p>
	 * Primarily applies to <strong>Italian</strong>. Triggers "lo/gli"
	 * instead of "il/i" for masculine nouns, and "uno" instead of "un".
	 * <p>
	 * Examples: "studente", "spaghetti", "sbaglio", "scuola"
	 * → "lo studente", "gli spaghetti"
	 */
	S_IMPURE,

	/**
	 * Word begins with Z sound or affricates /ts/, /dz/.
	 * <p>
	 * Primarily applies to <strong>Italian</strong>. Triggers "lo/gli"
	 * instead of "il/i" for masculine nouns.
	 * <p>
	 * Examples: "zio", "zero", "zucchero" → "lo zio", "gli zii"
	 */
	Z,

	/**
	 * Word begins with the palatal nasal cluster GN.
	 * <p>
	 * Primarily applies to <strong>Italian</strong>. Triggers "lo/gli"
	 * instead of "il/i" for masculine nouns.
	 * <p>
	 * Examples: "gnomo", "gnocco" → "lo gnomo", "gli gnocchi"
	 */
	GN,

	/**
	 * Word begins with the PS cluster.
	 * <p>
	 * Primarily applies to <strong>Italian</strong>, typically in words
	 * of Greek origin. Triggers "lo/gli" instead of "il/i".
	 * <p>
	 * Examples: "psicologo", "pseudonimo" → "lo psicologo"
	 */
	PS,

	/**
	 * Word begins with the PN cluster.
	 * <p>
	 * Primarily applies to <strong>Italian</strong>, typically in words
	 * of Greek origin. Triggers "lo/gli" instead of "il/i".
	 * <p>
	 * Examples: "pneumatico", "pneumologo" → "lo pneumatico"
	 */
	PN,

	/**
	 * Word begins with X (/ks/ sound).
	 * <p>
	 * Primarily applies to <strong>Italian</strong>. Triggers "lo/gli"
	 * instead of "il/i". Rare in Italian vocabulary.
	 * <p>
	 * Examples: "xilofono", "xenofobo" → "lo xilofono"
	 */
	X,

	/**
	 * Word begins with Y functioning as a consonantal glide /j/.
	 * <p>
	 * Primarily applies to <strong>Italian</strong>, where loanwords
	 * starting with Y take "lo/gli" instead of "il/i".
	 * <p>
	 * Examples: "yogurt", "yacht" → "lo yogurt"
	 * <p>
	 * Note: In English, Y is typically treated as {@link #CONSONANT}.
	 */
	GLIDE_Y,

	/**
	 * Word begins with W functioning as a consonantal glide /w/.
	 * <p>
	 * May apply to <strong>Italian</strong> and other languages where
	 * W-initial words (typically loanwords) require special handling.
	 * <p>
	 * Examples: "whisky", "weekend"
	 */
	GLIDE_W,

	/**
	 * Word begins with a stressed A or HA sound.
	 * <p>
	 * Primarily applies to <strong>Spanish</strong> and <strong>Catalan</strong>.
	 * Feminine nouns with stressed initial A take masculine singular articles
	 * for euphonic reasons, while remaining grammatically feminine.
	 * <p>
	 * Examples: "agua", "águila", "hacha", "alma"
	 * → "el agua" (not "la agua"), but "las aguas" in plural
	 * <p>
	 * Note: The noun remains feminine—adjectives still agree femininely:
	 * "el agua fría" (the cold water).
	 */
	STRESSED_A,

	/**
	 * Arabic sun letter (الحروف الشمسية).
	 * <p>
	 * Applies to <strong>Arabic</strong>. When the definite article "al-"
	 * precedes a sun letter, the L assimilates to the following consonant.
	 * <p>
	 * Sun letters: ت ث د ذ ر ز س ش ص ض ط ظ ل ن
	 * <p>
	 * Example: "al-shams" is pronounced "ash-shams" (the sun)
	 */
	SOLAR,

	/**
	 * Arabic moon letter (الحروف القمرية).
	 * <p>
	 * Applies to <strong>Arabic</strong>. When the definite article "al-"
	 * precedes a moon letter, the L is pronounced normally without assimilation.
	 * <p>
	 * Moon letters: ب ج ح خ ع غ ف ق ك م هـ و ي ء
	 * <p>
	 * Example: "al-qamar" is pronounced as written (the moon)
	 */
	LUNAR,

	/**
	 * Fallback category for edge cases not covered by other values.
	 * <p>
	 * Use this when a word doesn't fit cleanly into any other phonetic
	 * category, allowing graceful degradation in localized string selection.
	 */
	OTHER;

	@NonNull
	private static final Map<@NonNull String, @NonNull Phonetic> PHONETICS_BY_NAME;

	static {
		PHONETICS_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
				Phonetic.values()).collect(Collectors.toMap(phonetic -> phonetic.name(), phonetic -> phonetic)));
	}

	/**
	 * Gets the mapping of phonetic names to phonetic values.
	 *
	 * @return the mapping of phonetic names to phonetic values, not null
	 */
	@NonNull
	static Map<@NonNull String, @NonNull Phonetic> getPhoneticsByName() {
		return PHONETICS_BY_NAME;
	}
}
