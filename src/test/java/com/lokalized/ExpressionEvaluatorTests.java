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

		assertTrue(expressionEvaluator.evaluate("GENDER_MASCULINE == GENDER_MASCULINE", LOCALE), "Gender identity failed");
		assertFalse(expressionEvaluator.evaluate("GENDER_MASCULINE == GENDER_FEMININE", LOCALE), "Unequal genders evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("GENDER_MASCULINE != GENDER_FEMININE", LOCALE), "Unequal genders evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("GENDER_COMMON == GENDER_COMMON", LOCALE), "Common gender identity failed");
		assertFalse(expressionEvaluator.evaluate("GENDER_COMMON == GENDER_NEUTER", LOCALE), "Unequal genders evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("GENDER_COMMON != GENDER_NEUTER", LOCALE), "Unequal genders evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("CASE_DATIVE == CASE_DATIVE", LOCALE), "Grammatical case identity failed");
		assertFalse(expressionEvaluator.evaluate("CASE_DATIVE == CASE_ACCUSATIVE", LOCALE), "Unequal grammatical cases evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("CASE_DATIVE != CASE_ACCUSATIVE", LOCALE), "Unequal grammatical cases evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("DEFINITENESS_DEFINITE == DEFINITENESS_DEFINITE", LOCALE), "Definiteness identity failed");
		assertFalse(expressionEvaluator.evaluate("DEFINITENESS_DEFINITE == DEFINITENESS_INDEFINITE", LOCALE), "Unequal definiteness values evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("DEFINITENESS_DEFINITE != DEFINITENESS_INDEFINITE", LOCALE), "Unequal definiteness values evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("CLASSIFIER_BOUND == CLASSIFIER_BOUND", LOCALE), "Classifier identity failed");
		assertFalse(expressionEvaluator.evaluate("CLASSIFIER_BOUND == CLASSIFIER_GENERAL", LOCALE), "Unequal classifiers evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("CLASSIFIER_BOUND != CLASSIFIER_GENERAL", LOCALE), "Unequal classifiers evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("FORMALITY_CASUAL == FORMALITY_CASUAL", LOCALE), "Casual formality identity failed");
		assertFalse(expressionEvaluator.evaluate("FORMALITY_CASUAL == FORMALITY_HUMBLE", LOCALE), "Unequal formalities evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("FORMALITY_HUMBLE == FORMALITY_HUMBLE", LOCALE), "Humble formality identity failed");

		assertTrue(expressionEvaluator.evaluate("FORMALITY_FORMAL == FORMALITY_FORMAL", LOCALE), "Formality identity failed");
		assertFalse(expressionEvaluator.evaluate("FORMALITY_FORMAL == FORMALITY_INFORMAL", LOCALE), "Unequal formalities evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("FORMALITY_FORMAL != FORMALITY_INFORMAL", LOCALE), "Unequal formalities evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("FORMALITY_HONORIFIC == FORMALITY_HONORIFIC", LOCALE), "Honorific identity failed");
		assertFalse(expressionEvaluator.evaluate("FORMALITY_HONORIFIC == FORMALITY_FORMAL", LOCALE), "Unequal formalities evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("CLUSIVITY_INCLUSIVE == CLUSIVITY_INCLUSIVE", LOCALE), "Clusivity identity failed");
		assertFalse(expressionEvaluator.evaluate("CLUSIVITY_INCLUSIVE == CLUSIVITY_EXCLUSIVE", LOCALE), "Unequal clusivities evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("CLUSIVITY_INCLUSIVE != CLUSIVITY_EXCLUSIVE", LOCALE), "Unequal clusivities evaluate as equal");

		assertTrue(expressionEvaluator.evaluate("ANIMACY_ANIMATE == ANIMACY_ANIMATE", LOCALE), "Animacy identity failed");
		assertFalse(expressionEvaluator.evaluate("ANIMACY_ANIMATE == ANIMACY_INANIMATE", LOCALE), "Unequal animacy evaluate as equal");
		assertTrue(expressionEvaluator.evaluate("ANIMACY_ANIMATE != ANIMACY_INANIMATE", LOCALE), "Unequal animacy evaluate as equal");

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

		assertTrue(expressionEvaluator.evaluate("gender == GENDER_MASCULINE", Map.of(
				"gender", Gender.MASCULINE
		), LOCALE), "Gender-variable comparison failed");
		assertTrue(expressionEvaluator.evaluate("gender == GENDER_COMMON", Map.of(
				"gender", Gender.COMMON
		), LOCALE), "Gender-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("grammaticalCase == CASE_DATIVE", Map.of(
				"grammaticalCase", GrammaticalCase.DATIVE
		), LOCALE), "Grammatical case-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("definiteness == DEFINITENESS_CONSTRUCT", Map.of(
				"definiteness", Definiteness.CONSTRUCT
		), LOCALE), "Definiteness-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("classifier == CLASSIFIER_BOUND", Map.of(
				"classifier", Classifier.BOUND
		), LOCALE), "Classifier-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("formality == FORMALITY_FORMAL", Map.of(
				"formality", Formality.FORMAL
		), LOCALE), "Formality-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("formality == FORMALITY_HUMBLE", Map.of(
				"formality", Formality.HUMBLE
		), LOCALE), "Expanded formality-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("clusivity == CLUSIVITY_INCLUSIVE", Map.of(
				"clusivity", Clusivity.INCLUSIVE
		), LOCALE), "Clusivity-variable comparison failed");

		assertTrue(expressionEvaluator.evaluate("animacy == ANIMACY_ANIMATE", Map.of(
				"animacy", Animacy.ANIMATE
		), LOCALE), "Animacy-variable comparison failed");

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
		PhoneticResolver phoneticResolver = (term, locale) -> {
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
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(null, (term, locale) -> Phonetic.CONSONANT);

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
	public void unicodeVariableExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertTrue(expressionEvaluator.evaluate("caféCount == количество2", Map.of(
				"caféCount", 2,
				"количество2", 2
		), LOCALE), "Unicode variable comparison failed");
	}

	@Test
	public void invalidCardinalityOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("CARDINALITY_ONE < CARDINALITY_TWO", LOCALE),
				"Expected invalid cardinality operator to throw");
	}

	@Test
	public void invalidMixedOrderingOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("a < CARDINALITY_FEW", Map.of("a", 3), LOCALE),
				"Expected ordering against language forms to throw");

		assertTrue(exception.getMessage().contains("requires numeric operands"),
				"Expected invalid ordering message to explain numeric operands");
	}

	@Test
	public void bareVariablesAreNotBooleanExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("a && b", Map.of("a", 1, "b", 1), LOCALE),
				"Expected bare variables in boolean expressions to throw");

		assertTrue(exception.getMessage().contains("requires boolean operands"),
				"Expected boolean operator message to explain required operands");
		assertTrue(exception.getMessage().contains("a && b"),
				"Expected boolean operator message to include the original expression");
	}

	@Test
	public void chainedComparisonsAreRejected() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("a == b == c", Map.of("a", 1, "b", 1, "c", 1), LOCALE),
				"Expected chained comparisons to throw");

		assertTrue(exception.getMessage().contains("Chained comparisons are not supported"),
				"Expected chained comparison message to be explicit");
		assertFalse(exception.getMessage().contains("TRUE"),
				"Expected chained comparison message not to expose internal boolean sentinels");
		assertFalse(exception.getMessage().contains("boolean result"),
				"Expected chained comparison message not to expose internal validation sentinels");
	}

	@Test
	public void comparisonResultsCannotBeCompared() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("(x == 2) == 1", Map.of("x", 2), LOCALE),
				"Expected comparison results to be rejected as comparison operands");
	}

	@Test
	public void emptyExpressionsAreRejectedClearly() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("", LOCALE),
				"Expected empty expressions to throw");

		assertTrue(exception.getMessage().contains("must not be empty"),
				"Expected empty expression message to be clear");
	}

	@Test
	public void expressionSourceLengthIsLimited() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate(overlongExpression(), Map.of("a", 1), LOCALE),
				"Expected overlong expressions to throw");

		assertTrue(exception.getMessage().contains("maximum supported length"),
				"Expected expression length message to be clear");
	}

	@Test
	public void expressionTokenCountIsLimited() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate(orExpressionWithClauseCount(129), Map.of("a", 1), LOCALE),
				"Expected expressions with too many tokens to throw");

		assertTrue(exception.getMessage().contains("maximum supported token count"),
				"Expected expression token-count message to be clear");
	}

	@Test
	public void expressionGroupingDepthIsLimited() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		ExpressionEvaluationException exception = assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate(nestedExpression(65), Map.of("a", 1), LOCALE),
				"Expected overly deep expressions to throw");

		assertTrue(exception.getMessage().contains("maximum supported depth"),
				"Expected expression depth message to be clear");
	}

	@Test
	public void invalidOrdinalityOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("ORDINALITY_ONE > ORDINALITY_TWO", LOCALE),
				"Expected invalid ordinality operator to throw");
	}

	@Test
	public void invalidGrammaticalCaseOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("CASE_DATIVE < CASE_ACCUSATIVE", LOCALE),
				"Expected invalid grammatical case operator to throw");
	}

	@Test
	public void invalidDefinitenessOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("DEFINITENESS_DEFINITE > DEFINITENESS_INDEFINITE", LOCALE),
				"Expected invalid definiteness operator to throw");
	}

	@Test
	public void invalidClassifierOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		assertThrows(ExpressionEvaluationException.class,
				() -> expressionEvaluator.evaluate("CLASSIFIER_BOUND < CLASSIFIER_GENERAL", LOCALE),
				"Expected invalid classifier operator to throw");
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

	private String overlongExpression() {
		StringBuilder expression = new StringBuilder(4_103);
		expression.append("a == 1");

		for (int i = 0; i < 4_096; ++i)
			expression.append(' ');

		return expression.toString();
	}

	private String orExpressionWithClauseCount(int clauseCount) {
		StringBuilder expression = new StringBuilder(clauseCount * 10);

		for (int i = 0; i < clauseCount; ++i) {
			if (i > 0)
				expression.append(" || ");

			expression.append("a == 1");
		}

		return expression.toString();
	}

	private String nestedExpression(int depth) {
		StringBuilder expression = new StringBuilder(depth * 2 + 6);

		for (int i = 0; i < depth; ++i)
			expression.append('(');

		expression.append("a == 1");

		for (int i = 0; i < depth; ++i)
			expression.append(')');

		return expression.toString();
	}
}
