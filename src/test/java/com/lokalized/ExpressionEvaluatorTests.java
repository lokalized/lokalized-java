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

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Exercises {@link ExpressionEvaluator}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class ExpressionEvaluatorTests {
	private static final Locale LOCALE = Locale.forLanguageTag("en-US");

	@Test
	public void identityExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertTrue(expressionEvaluator.evaluate("12.5 == 12.5", LOCALE), "Number identity failed");
		assertFalse(expressionEvaluator.evaluate("12.5 == 12.6", LOCALE), "Unequal numbers evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("12.5 != 12.6", LOCALE), "Unequal numbers evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("MASCULINE == MASCULINE", LOCALE), "Gender identity failed");
		assertFalse(expressionEvaluator.evaluate("MASCULINE == FEMININE", LOCALE), "Unequal genders evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("MASCULINE != FEMININE", LOCALE), "Unequal genders evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("CARDINALITY_ONE == CARDINALITY_ONE", LOCALE), "Cardinality identity failed");
		assertFalse(expressionEvaluator.evaluate("CARDINALITY_ONE == CARDINALITY_MANY", LOCALE), "Unequal plurals evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("CARDINALITY_ONE != CARDINALITY_MANY", LOCALE), "Unequal plurals evaluate as equal");
	}

	@Test
	public void numericOperators() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertTrue(expressionEvaluator.evaluate("12.5 <= 12.5", LOCALE), "Number <= failed");
		assertTrue(expressionEvaluator.evaluate("12.4 < 12.5", LOCALE), "Number < failed");
		assertTrue(expressionEvaluator.evaluate("12.5 >= 12.5", LOCALE), "Number >= failed");
		assertTrue(expressionEvaluator.evaluate("12.6 > 12.5", LOCALE), "Number > failed");
		assertTrue(expressionEvaluator.evaluate(".5 == 0.5", LOCALE), "Leading decimal comparison failed");
		assertTrue(expressionEvaluator.evaluate("1. == 1", LOCALE), "Trailing decimal comparison failed");
		assertTrue(expressionEvaluator.evaluate("+1 == 1", LOCALE), "Signed integer comparison failed");
		assertTrue(expressionEvaluator.evaluate("1e3 == 1000", LOCALE), "Exponent comparison failed");
	}

	@Test
	public void largeIntegerComparisonsPreservePrecision() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertFalse(expressionEvaluator.evaluate("9007199254740993 == 9007199254740992", LOCALE),
				"Large integer equality should not lose precision");
		assertTrue(expressionEvaluator.evaluate("9007199254740993 > 9007199254740992", LOCALE),
				"Large integer comparisons should preserve precision");
	}

	@Test
	public void contextualExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertTrue(expressionEvaluator.evaluate("12.5 == x", Map.of(
				"x", 12.5
		), LOCALE), "Number-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("gender == MASCULINE", Map.of(
				"gender", Gender.MASCULINE
		), LOCALE), "Gender-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("CARDINALITY_OTHER == bigNumber", Map.of(
				"bigNumber", 1_000
		), LOCALE), "Cardinality-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("CARDINALITY_ONE == exactlyOne", Map.of(
				"exactlyOne", 1
		), LOCALE), "Cardinality-variable comparison failed");
	}

	@Test
	public void unknownOperandTypesThrow() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("x == 1", Map.of("x", "not-a-number"), LOCALE),
				"Unsupported operand types should raise an error");
	}

	@Test
	public void missingPlaceholderValuesThrow() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("missing == 1", Map.of(), LOCALE),
				"Missing placeholder values should raise an error");
	}

	@Test
	public void shortCircuitOrSkipsMissingPlaceholder() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertTrue(expressionEvaluator.evaluate("a == 1 || missing == 1", Map.of(
				"a", 1
		), LOCALE), "Expected OR short-circuit to skip missing placeholders");
	}

	@Test
	public void shortCircuitAndSkipsMissingPlaceholder() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertFalse(expressionEvaluator.evaluate("a == 1 && missing == 1", Map.of(
				"a", 0
		), LOCALE), "Expected AND short-circuit to skip missing placeholders");
	}

	@Test
	public void ordinalityExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertTrue(expressionEvaluator.evaluate("ORDINALITY_ONE == ORDINALITY_ONE", LOCALE), "Ordinality identity failed");
		assertFalse(expressionEvaluator.evaluate("ORDINALITY_ONE == ORDINALITY_OTHER", LOCALE), "Unequal ordinalities evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("ORDINALITY_ONE != ORDINALITY_OTHER", LOCALE), "Unequal ordinalities evaluate as equal");
	}

	@Test
	public void phoneticExpressions() {
		PhoneticResolver phoneticResolver = term -> {
			String value = term.toString().toLowerCase(Locale.ROOT);
			return value.startsWith("hon") ? Phonetic.VOWEL : Phonetic.CONSONANT;
		};

		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(null, phoneticResolver);

		assertTrue(expressionEvaluator.evaluate("term == PHONETIC_VOWEL", Map.of(
				"term", "honor"
		), LOCALE), "Phonetic vowel comparison failed");

		assertTrue(expressionEvaluator.evaluate("term == PHONETIC_CONSONANT", Map.of(
				"term", "user"
		), LOCALE), "Phonetic consonant comparison failed");

		assertFalse(expressionEvaluator.evaluate("term == PHONETIC_VOWEL", Map.of(
				"term", "user"
		), LOCALE), "Phonetic comparison unexpectedly matched");

		assertTrue(new ExpressionEvaluator().evaluate("term == PHONETIC_VOWEL", Map.of(
				"term", Phonetic.VOWEL
		), LOCALE), "Explicit phonetic values should be comparable without a resolver");
	}

	@Test
	public void invalidPhoneticOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(null, term -> Phonetic.CONSONANT);

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("term < PHONETIC_VOWEL", Map.of("term", "honor"), LOCALE),
				"Expected invalid phonetic operator to throw");
	}

	@Test
	public void missingPhoneticResolverThrows() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("term == PHONETIC_VOWEL", Map.of("term", "honor"), LOCALE),
				"Expected missing phonetic resolver to throw");
	}

	@Test
	public void operatorPrecedence() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		boolean result = expressionEvaluator.evaluate("a == 1 || b == 1 && c == 1", Map.of(
				"a", 1,
				"b", 0,
				"c", 0
		), LOCALE);

		assertTrue(result, "Expected && to bind tighter than ||");
	}

	@Test
	public void hyphenatedVariableExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertTrue(expressionEvaluator.evaluate("user-name == 2", Map.of(
				"user-name", 2
		), LOCALE), "Hyphenated variable comparison failed");
	}

	@Test
	public void invalidCardinalityOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("CARDINALITY_ONE < CARDINALITY_TWO", LOCALE),
				"Expected invalid cardinality operator to throw");
	}

	@Test
	public void invalidOrdinalityOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("ORDINALITY_ONE > ORDINALITY_TWO", LOCALE),
				"Expected invalid ordinality operator to throw");
	}

	@Test
	public void customTokenizerIsUsed() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(new ExpressionTokenizer() {
			@Override
		public List<Token> extractTokens(String expression) {
				return List.of(new Token(TokenType.NUMBER, "1"), new Token(TokenType.EQUAL_TO), new Token(TokenType.NUMBER, "1"));
			}
		});

		assertTrue(expressionEvaluator.evaluate("ignored", LOCALE), "Custom tokenizer output should be evaluated");
	}
}
