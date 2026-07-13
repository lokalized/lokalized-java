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
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.Arrays;
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
	public void publishedConstantsHaveDocumentedValues() {
		assertEquals(Integer.valueOf(1_024), TranslationRuntimeLimits.DEFAULT_MAXIMUM_NUMBER_PRECISION);
		assertEquals(Integer.valueOf(1_024), TranslationRuntimeLimits.DEFAULT_MAXIMUM_ABSOLUTE_NUMBER_SCALE);
		assertEquals(Integer.valueOf(1_024), TranslationRuntimeLimits.DEFAULT_MAXIMUM_VISIBLE_DECIMAL_PLACES);
		assertEquals(Integer.valueOf(64), TranslationRuntimeLimits.DEFAULT_MAXIMUM_COMPACT_EXPONENT);
		assertEquals(Integer.valueOf(2_048), TranslationRuntimeLimits.DEFAULT_MAXIMUM_EXPRESSION_CHARACTERS);
		assertEquals(Integer.valueOf(256), TranslationRuntimeLimits.DEFAULT_MAXIMUM_EXPRESSION_TOKENS);
		assertEquals(Integer.valueOf(32), TranslationRuntimeLimits.DEFAULT_MAXIMUM_EXPRESSION_NESTING_DEPTH);
		assertEquals(Integer.valueOf(32), TranslationRuntimeLimits.DEFAULT_MAXIMUM_GENERATED_PLACEHOLDER_DEPTH);
		assertEquals(Integer.valueOf(262_144),
				TranslationRuntimeLimits.DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS);
		assertEquals(Integer.valueOf(1_048_576),
				TranslationRuntimeLimits.DEFAULT_MAXIMUM_GENERATED_EXPANSION_CHARACTERS);

		assertEquals(Integer.valueOf(4_096), TranslationRuntimeLimits.MAXIMUM_NUMBER_PRECISION);
		assertEquals(Integer.valueOf(4_096), TranslationRuntimeLimits.MAXIMUM_ABSOLUTE_NUMBER_SCALE);
		assertEquals(Integer.valueOf(4_096), TranslationRuntimeLimits.MAXIMUM_VISIBLE_DECIMAL_PLACES);
		assertEquals(Integer.valueOf(4_096), TranslationRuntimeLimits.MAXIMUM_COMPACT_EXPONENT);
		assertEquals(Integer.valueOf(4_096), TranslationRuntimeLimits.MAXIMUM_EXPRESSION_CHARACTERS);
		assertEquals(Integer.valueOf(512), TranslationRuntimeLimits.MAXIMUM_EXPRESSION_TOKENS);
		assertEquals(Integer.valueOf(64), TranslationRuntimeLimits.MAXIMUM_EXPRESSION_NESTING_DEPTH);
		assertEquals(Integer.valueOf(64), TranslationRuntimeLimits.MAXIMUM_GENERATED_PLACEHOLDER_DEPTH);
		assertEquals(Integer.valueOf(1_048_576), TranslationRuntimeLimits.MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS);
		assertEquals(Integer.valueOf(8_388_608), TranslationRuntimeLimits.MAXIMUM_GENERATED_EXPANSION_CHARACTERS);
	}

	@Test
	public void defaultsAreConservative() {
		TranslationRuntimeLimits defaults = TranslationRuntimeLimits.defaults();

		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_NUMBER_PRECISION,
				defaults.getMaximumNumberPrecision());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_ABSOLUTE_NUMBER_SCALE,
				defaults.getMaximumAbsoluteNumberScale());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_VISIBLE_DECIMAL_PLACES,
				defaults.getMaximumVisibleDecimalPlaces());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_COMPACT_EXPONENT,
				defaults.getMaximumCompactExponent());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_EXPRESSION_CHARACTERS,
				defaults.getMaximumExpressionCharacters());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_EXPRESSION_TOKENS,
				defaults.getMaximumExpressionTokens());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_EXPRESSION_NESTING_DEPTH,
				defaults.getMaximumExpressionNestingDepth());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_GENERATED_PLACEHOLDER_DEPTH,
				defaults.getMaximumGeneratedPlaceholderDepth());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS,
				defaults.getMaximumInterpolatedOutputCharacters());
		assertEquals(TranslationRuntimeLimits.DEFAULT_MAXIMUM_GENERATED_EXPANSION_CHARACTERS,
				defaults.getMaximumGeneratedExpansionCharacters());
	}

	@Test
	public void hardCeilingsRemainAvailable() {
		TranslationRuntimeLimits hardCeilings = TranslationRuntimeLimits.builder()
				.maximumNumberPrecision(TranslationRuntimeLimits.MAXIMUM_NUMBER_PRECISION)
				.maximumAbsoluteNumberScale(TranslationRuntimeLimits.MAXIMUM_ABSOLUTE_NUMBER_SCALE)
				.maximumVisibleDecimalPlaces(TranslationRuntimeLimits.MAXIMUM_VISIBLE_DECIMAL_PLACES)
				.maximumCompactExponent(TranslationRuntimeLimits.MAXIMUM_COMPACT_EXPONENT)
				.maximumExpressionCharacters(TranslationRuntimeLimits.MAXIMUM_EXPRESSION_CHARACTERS)
				.maximumExpressionTokens(TranslationRuntimeLimits.MAXIMUM_EXPRESSION_TOKENS)
				.maximumExpressionNestingDepth(TranslationRuntimeLimits.MAXIMUM_EXPRESSION_NESTING_DEPTH)
				.maximumGeneratedPlaceholderDepth(TranslationRuntimeLimits.MAXIMUM_GENERATED_PLACEHOLDER_DEPTH)
				.maximumInterpolatedOutputCharacters(TranslationRuntimeLimits.MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS)
				.maximumGeneratedExpansionCharacters(TranslationRuntimeLimits.MAXIMUM_GENERATED_EXPANSION_CHARACTERS)
				.build();

		assertEquals(TranslationRuntimeLimits.MAXIMUM_NUMBER_PRECISION,
				hardCeilings.getMaximumNumberPrecision());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_ABSOLUTE_NUMBER_SCALE,
				hardCeilings.getMaximumAbsoluteNumberScale());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_VISIBLE_DECIMAL_PLACES,
				hardCeilings.getMaximumVisibleDecimalPlaces());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_COMPACT_EXPONENT,
				hardCeilings.getMaximumCompactExponent());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_EXPRESSION_CHARACTERS,
				hardCeilings.getMaximumExpressionCharacters());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_EXPRESSION_TOKENS,
				hardCeilings.getMaximumExpressionTokens());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_EXPRESSION_NESTING_DEPTH,
				hardCeilings.getMaximumExpressionNestingDepth());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_GENERATED_PLACEHOLDER_DEPTH,
				hardCeilings.getMaximumGeneratedPlaceholderDepth());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS,
				hardCeilings.getMaximumInterpolatedOutputCharacters());
		assertEquals(TranslationRuntimeLimits.MAXIMUM_GENERATED_EXPANSION_CHARACTERS,
				hardCeilings.getMaximumGeneratedExpansionCharacters());
		assertEquals(TranslationRuntimeLimits.hardCeilings(), hardCeilings);
	}

	@Test
	public void valueSemanticsAndCopyBuilder() {
		TranslationRuntimeLimits defaults = TranslationRuntimeLimits.defaults();
		TranslationRuntimeLimits copy = defaults.toBuilder().build();
		TranslationRuntimeLimits lower = defaults.toBuilder().maximumExpressionTokens(12).build();
		TranslationRuntimeLimits restored = TranslationRuntimeLimits.hardCeilings().toBuilder()
				.maximumNumberPrecision(null)
				.maximumAbsoluteNumberScale(null)
				.maximumVisibleDecimalPlaces(null)
				.maximumCompactExponent(null)
				.maximumExpressionCharacters(null)
				.maximumExpressionTokens(null)
				.maximumExpressionNestingDepth(null)
				.maximumGeneratedPlaceholderDepth(null)
				.maximumInterpolatedOutputCharacters(null)
				.maximumGeneratedExpansionCharacters(null)
				.build();

		assertEquals(defaults, copy);
		assertEquals(defaults, restored);
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
	public void applicationsCanOptUpAboveExpressionDefaults() {
		char[] padding = new char[TranslationRuntimeLimits.DEFAULT_MAXIMUM_EXPRESSION_CHARACTERS];
		Arrays.fill(padding, ' ');
		String expression = "count == 1" + new String(padding);
		String catalog = "{\"root\":{\"translation\":\"fallback\",\"alternatives\":[{\"" + expression +
				"\":\"one\"}]}}";
		Set<LocalizedString> localizedStrings = LocalizedStringLoader.parse(new StringReader(catalog), Locale.ENGLISH,
				"opt-up-expression", LocalizedStringWarningHandler.ignore(), LocalizedStringLoadingOptions.defaults());

		assertThrows(ExpressionEvaluationException.class, () -> Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, localizedStrings))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.build());

		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, localizedStrings))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.runtimeLimits(TranslationRuntimeLimits.builder()
						.maximumExpressionCharacters(expression.length())
						.build())
				.build();

		assertEquals("one", strings.get("root", Map.of("count", 1)));
	}

	@Test
	public void pluralOperandBuilderHonorsLowerLimits() {
		TranslationRuntimeLimits limits = TranslationRuntimeLimits.builder()
				.maximumNumberPrecision(2)
				.maximumAbsoluteNumberScale(2)
				.maximumVisibleDecimalPlaces(1)
				.maximumCompactExponent(3)
				.build();

		assertThrows(IllegalArgumentException.class,
				() -> PluralOperands.forNumber(123).runtimeLimits(limits).build());
		assertThrows(IllegalArgumentException.class,
				() -> PluralOperands.forNumber(new BigDecimal("0.001")).runtimeLimits(limits).build());
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
	public void stringsEagerlyCompileAllFragmentPredicatesAgainstLowerLimits() {
		LocalizedString lengthLimited = localizedStringWithUnusedFragment("count == 1");
		LocalizedString tokenLimited = localizedStringWithUnusedFragment("count == 1");
		LocalizedString nestingLimited = localizedStringWithUnusedFragment("(count == 1)");

		ExpressionEvaluationException lengthException = assertThrows(ExpressionEvaluationException.class,
				() -> stringsWithLimits(lengthLimited,
						TranslationRuntimeLimits.builder().maximumExpressionCharacters(5).build()));
		assertTrue(lengthException.getMessage().contains("generated-fragment alternative 0"));
		assertTrue(lengthException.getMessage().contains("placeholder 'unused'"));
		assertTrue(lengthException.getMessage().contains("root key 'Unused fragment'"));
		assertTrue(lengthException.getMessage().contains("locale 'en'"));
		assertThrows(ExpressionEvaluationException.class, () -> stringsWithLimits(tokenLimited,
				TranslationRuntimeLimits.builder().maximumExpressionTokens(2).build()));
		assertThrows(ExpressionEvaluationException.class, () -> stringsWithLimits(nestingLimited,
				TranslationRuntimeLimits.builder().maximumExpressionNestingDepth(0).build()));
	}

	@Test
	public void stringsCompilationErrorsIdentifyWholeMessageAlternativePaths() {
		LocalizedString localizedString = new LocalizedString.Builder("Whole-message limits")
				.translation("default")
				.alternatives(List.of(new LocalizedString.Builder("count == 1").translation("one").build()))
				.build();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> stringsWithLimits(localizedString, TranslationRuntimeLimits.builder()
						.maximumExpressionCharacters(5).build()));
		assertTrue(exception.getMessage().contains("whole-message alternative expression 'count == 1'"));
		assertTrue(exception.getMessage().contains("root key 'Whole-message limits'"));
	}

	@Test
	public void laterUnreachableFragmentPredicatesAreAlsoCompiledEagerly() {
		LocalizedString localizedString = new LocalizedString.Builder("Unreachable later predicate")
				.translation("Hello")
				.placeholderDefinitions(Map.of("unused", new LocalizedString.ExpressionTranslation("default", java.util.List.of(
						new LocalizedString.ExpressionAlternative("count == 1", "first"),
						new LocalizedString.ExpressionAlternative("anotherCount == 2", "later")))))
				.build();

		assertThrows(ExpressionEvaluationException.class, () -> stringsWithLimits(localizedString,
				TranslationRuntimeLimits.builder().maximumExpressionCharacters(12).build()));
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
	public void stringsHonorsGeneratedPlaceholderDepthLimit() {
		Map<String, LocalizedString.LanguageFormTranslation> generatedPlaceholders = Map.of(
				"p0", new LocalizedString.LanguageFormTranslation("count", Map.of(
						Cardinality.ONE, "{{p1}}", Cardinality.OTHER, "{{p1}}")),
				"p1", new LocalizedString.LanguageFormTranslation("count", Map.of(
						Cardinality.ONE, "{{p2}}", Cardinality.OTHER, "{{p2}}")),
				"p2", new LocalizedString.LanguageFormTranslation("count", Map.of(
						Cardinality.ONE, "item", Cardinality.OTHER, "items")));
		LocalizedString localizedString = new LocalizedString.Builder("Items")
				.translation("{{p0}}")
				.placeholderDefinitions(generatedPlaceholders)
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.runtimeLimits(TranslationRuntimeLimits.builder().maximumGeneratedPlaceholderDepth(1).build())
				.build();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> strings.get("Items", Map.of("count", 1)));
		assertTrue(exception.getMessage().contains("maximum depth of 1"));
	}

	@Test
	public void expressionFragmentsShareGeneratedDepthOutputAndExpansionLimits() {
		LocalizedString deep = new LocalizedString.Builder("Deep expression fragments")
				.translation("{{p0}}")
				.placeholderDefinitions(Map.of(
						"p0", new LocalizedString.ExpressionTranslation("{{p1}}"),
						"p1", new LocalizedString.ExpressionTranslation("{{p2}}"),
						"p2", new LocalizedString.ExpressionTranslation("done")
				))
				.build();
		IllegalStateException depthException = assertThrows(IllegalStateException.class,
				() -> stringsWithLimits(deep, TranslationRuntimeLimits.builder()
						.maximumGeneratedPlaceholderDepth(1).build()).get("Deep expression fragments"));
		assertTrue(depthException.getMessage().contains("maximum depth of 1"));

		LocalizedString output = new LocalizedString.Builder("Expression output")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("{{name}}")))
				.build();
		IllegalStateException outputException = assertThrows(IllegalStateException.class,
				() -> stringsWithLimits(output, TranslationRuntimeLimits.builder()
						.maximumInterpolatedOutputCharacters(4).build())
						.get("Expression output", Map.of("name", "Alice")));
		assertTrue(outputException.getMessage().contains("maximum of 4 characters"));

		LocalizedString expansion = new LocalizedString.Builder("Expression expansion")
				.translation("{{p0}}")
				.placeholderDefinitions(Map.of(
						"p0", new LocalizedString.ExpressionTranslation("{{p1}}{{p1}}"),
						"p1", new LocalizedString.ExpressionTranslation("abc")
				))
				.build();
		IllegalStateException expansionException = assertThrows(IllegalStateException.class,
				() -> stringsWithLimits(expansion, TranslationRuntimeLimits.builder()
						.maximumGeneratedExpansionCharacters(5).build()).get("Expression expansion"));
		assertTrue(expansionException.getMessage().contains("cumulative limit"));
	}

	@Test
	public void stringsHonorsNumericLimitsDuringPluralResolution() {
		LocalizedString localizedString = new LocalizedString.Builder("Items")
				.translation("{{noun}}")
				.placeholderDefinitions(Map.of("noun",
						new LocalizedString.LanguageFormTranslation("count", Map.of(
								Cardinality.ONE, "item", Cardinality.OTHER, "items"))))
				.build();
		Strings strings = Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.runtimeLimits(TranslationRuntimeLimits.builder()
						.maximumNumberPrecision(2)
						.maximumCompactExponent(PluralOperands.MAXIMUM_COMPACT_EXPONENT)
						.build())
				.build();

		assertThrows(IllegalArgumentException.class,
				() -> strings.get("Items", Map.of("count", new BigDecimal("123"))));
		assertThrows(IllegalArgumentException.class,
				() -> strings.get("Items", Map.of("count", PluralOperands.forNumber(123).build())));

		PluralOperands compactOperands = PluralOperands.forNumber(1)
				.compactExponent(PluralOperands.MAXIMUM_COMPACT_EXPONENT)
				.runtimeLimits(TranslationRuntimeLimits.hardCeilings())
				.build();
		assertEquals("items", strings.get("Items", Map.of("count", compactOperands)),
				"Expanded compact operands must be checked by their source number, not materialized precision");
	}

	@Test
	public void fragmentNumericLimitFailuresIdentifyTheirPredicate() {
		LocalizedString localizedString = new LocalizedString.Builder("Numeric predicate limits")
				.translation("{{fragment}}")
				.placeholderDefinitions(Map.of("fragment", new LocalizedString.ExpressionTranslation("other", List.of(
						new LocalizedString.ExpressionAlternative("count == 1", "one")))))
				.build();
		Strings strings = stringsWithLimits(localizedString, TranslationRuntimeLimits.builder()
				.maximumNumberPrecision(2)
				.build());

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> strings.get("Numeric predicate limits", Map.of("count", new BigDecimal("123"))));
		assertTrue(exception.getMessage().contains("generated-fragment expression 'count == 1'"));
	}

	private LocalizedString localizedStringWithUnusedFragment(String expression) {
		return new LocalizedString.Builder("Unused fragment")
				.translation("Hello")
				.placeholderDefinitions(Map.of("unused", new LocalizedString.ExpressionTranslation("default",
						java.util.List.of(new LocalizedString.ExpressionAlternative(expression, "selected")))))
				.build();
	}

	private Strings stringsWithLimits(LocalizedString localizedString, TranslationRuntimeLimits runtimeLimits) {
		return Strings.withFallbackLocale(Locale.ENGLISH)
				.localizedStringSupplier(() -> Map.of(Locale.ENGLISH, Set.of(localizedString)))
				.localeSupplier(matcher -> Locale.ENGLISH)
				.translationFailureHandler(TranslationFailureHandler.throwException())
				.runtimeLimits(runtimeLimits)
				.build();
	}
}
