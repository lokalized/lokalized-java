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

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link TranslationRuntimeLimits}. */
@ThreadSafe
public class TranslationRuntimeLimitsTests {
	@Test
	public void valueSemanticsAndCopyBuilder() {
		TranslationRuntimeLimits defaults = TranslationRuntimeLimits.defaults();
		TranslationRuntimeLimits copy = defaults.toBuilder().build();
		TranslationRuntimeLimits lower = defaults.toBuilder().maximumExpressionTokens(12).build();

		assertEquals(defaults, copy);
		assertEquals(defaults.hashCode(), copy.hashCode());
		assertNotEquals(defaults, lower);
		assertTrue(lower.toString().contains("maximumExpressionTokens=12"));
	}

	@Test
	public void hardCeilingsAndInvalidMinimumsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> TranslationRuntimeLimits.builder()
				.maximumNumberPrecision(TranslationRuntimeLimits.MAXIMUM_NUMBER_PRECISION + 1).build());
		assertThrows(IllegalArgumentException.class, () -> TranslationRuntimeLimits.builder()
				.maximumNumberPrecision(0).build());
		assertThrows(IllegalArgumentException.class, () -> TranslationRuntimeLimits.builder()
				.maximumInterpolatedOutputCharacters(0).build());
		assertThrows(IllegalArgumentException.class, () -> TranslationRuntimeLimits.builder()
				.maximumGeneratedPlaceholderDepth(-1).build());
	}

	@Test
	public void pluralOperandBuilderHonorsLowerLimits() {
		TranslationRuntimeLimits limits = TranslationRuntimeLimits.builder()
				.maximumNumberPrecision(2)
				.maximumVisibleDecimalPlaces(1)
				.maximumCompactExponent(3)
				.build();

		assertThrows(IllegalArgumentException.class,
				() -> PluralOperands.forNumber(123).runtimeLimits(limits).build());
		assertThrows(IllegalArgumentException.class,
				() -> PluralOperands.forNumber(1).visibleDecimalPlaces(2).runtimeLimits(limits).build());
		assertThrows(IllegalArgumentException.class,
				() -> PluralOperands.forNumber(1).compactExponent(4).runtimeLimits(limits).build());
	}

	@Test
	public void expressionEvaluatorHonorsLowerLimits() {
		TranslationRuntimeLimits lengthLimit = TranslationRuntimeLimits.builder()
				.maximumExpressionCharacters(5).build();
		TranslationRuntimeLimits tokenLimit = TranslationRuntimeLimits.builder()
				.maximumExpressionTokens(2).build();
		TranslationRuntimeLimits nestingLimit = TranslationRuntimeLimits.builder()
				.maximumExpressionNestingDepth(0).build();
		TranslationRuntimeLimits numberLimit = TranslationRuntimeLimits.builder()
				.maximumNumberPrecision(2).build();

		assertThrows(ExpressionEvaluationException.class,
				() -> new ExpressionEvaluator(null, null, lengthLimit).evaluate("1 == 1", Locale.ENGLISH));
		assertThrows(ExpressionEvaluationException.class,
				() -> new ExpressionEvaluator(null, null, tokenLimit).evaluate("1 == 1", Locale.ENGLISH));
		assertThrows(ExpressionEvaluationException.class,
				() -> new ExpressionEvaluator(null, null, nestingLimit).evaluate("(1 == 1)", Locale.ENGLISH));
		assertThrows(ExpressionEvaluationException.class,
				() -> new ExpressionEvaluator(null, null, numberLimit).evaluate("123 == 123", Locale.ENGLISH));
	}

	@Test
	public void stringsHonorsOutputLimit() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting")
				.translation("{{name}}")
				.build();
		TranslationRuntimeLimits limits = TranslationRuntimeLimits.builder()
				.maximumInterpolatedOutputCharacters(4)
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.runtimeLimits(limits)
				.build();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> strings.get("Greeting", Map.of("name", "Alice")));
		assertTrue(exception.getMessage().contains("maximum of 4 characters"));
	}

	@Test
	public void zeroGeneratedExpansionBudgetAllowsOrdinaryTranslations() {
		LocalizedString localizedString = new LocalizedString.Builder("Greeting").translation("Hello").build();
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.runtimeLimits(TranslationRuntimeLimits.builder().maximumGeneratedExpansionCharacters(0).build())
				.build();

		assertEquals("Hello", strings.get("Greeting"));
	}

	@Test
	public void stringsHonorsSelectorRuleLimitAtBuildTime() {
		LocalizedString.LanguageFormTranslation selectorTranslation =
				new LocalizedString.LanguageFormTranslation(
						List.of(new LocalizedString.LanguageFormSelector("count", LanguageFormType.CARDINALITY)),
						List.of(new LocalizedString.LanguageFormTranslationRule("items")));
		LocalizedString localizedString = new LocalizedString.Builder("Items")
				.translation("{{noun}}")
				.languageFormTranslationsByPlaceholder(Map.of("noun", selectorTranslation))
				.build();

		assertThrows(IllegalArgumentException.class, () -> Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.runtimeLimits(TranslationRuntimeLimits.builder().maximumSelectorRules(0).build())
				.build());
	}

	@Test
	public void stringsHonorsNumericLimitsDuringPluralResolution() {
		LocalizedString localizedString = new LocalizedString.Builder("Items")
				.translation("{{noun}}")
				.languageFormTranslationsByPlaceholder(Map.of("noun",
						new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item", Cardinality.OTHER, "items"))))
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.runtimeLimits(TranslationRuntimeLimits.builder().maximumNumberPrecision(2).build())
				.build();

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("Items", Map.of("count", new BigDecimal("123"))));
		assertThrows(IllegalArgumentException.class,
				() -> strings.get("Items", Map.of("count", PluralOperands.forNumber(123).build())));

		PluralOperands compactOperands = PluralOperands.forNumber(1)
				.compactExponent(PluralOperands.MAXIMUM_COMPACT_EXPONENT)
				.build();
		assertEquals("items", strings.get("Items", Map.of("count", compactOperands)),
				"Expanded compact operands must be checked by their source number, not materialized precision");
	}
}
