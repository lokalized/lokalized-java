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

import org.junit.Assert;
import org.junit.Test;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.Locale;
import java.util.List;
import java.util.Map;

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

		Assert.assertTrue("Number identity failed", expressionEvaluator.evaluate("12.5 == 12.5", LOCALE));
		Assert.assertFalse("Unequal numbers evaluate as equal", expressionEvaluator.evaluate("12.5 == 12.6", LOCALE));
		Assert.assertTrue("Unequal numbers evaluate as equal", expressionEvaluator.evaluate("12.5 != 12.6", LOCALE));

		Assert.assertTrue("Gender identity failed", expressionEvaluator.evaluate("MASCULINE == MASCULINE", LOCALE));
		Assert.assertFalse("Unequal genders evaluate as equal", expressionEvaluator.evaluate("MASCULINE == FEMININE", LOCALE));
		Assert.assertTrue("Unequal genders evaluate as equal", expressionEvaluator.evaluate("MASCULINE != FEMININE", LOCALE));

		Assert.assertTrue("Cardinality identity failed", expressionEvaluator.evaluate("CARDINALITY_ONE == CARDINALITY_ONE", LOCALE));
		Assert.assertFalse("Unequal plurals evaluate as equal", expressionEvaluator.evaluate("CARDINALITY_ONE == CARDINALITY_MANY", LOCALE));
		Assert.assertTrue("Unequal plurals evaluate as equal", expressionEvaluator.evaluate("CARDINALITY_ONE != CARDINALITY_MANY", LOCALE));
	}

	@Test
	public void numericOperators() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		Assert.assertTrue("Number <= failed", expressionEvaluator.evaluate("12.5 <= 12.5", LOCALE));
		Assert.assertTrue("Number < failed", expressionEvaluator.evaluate("12.4 < 12.5", LOCALE));
		Assert.assertTrue("Number >= failed", expressionEvaluator.evaluate("12.5 >= 12.5", LOCALE));
		Assert.assertTrue("Number > failed", expressionEvaluator.evaluate("12.6 > 12.5", LOCALE));
	}

	@Test
	public void contextualExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		Assert.assertTrue("Number-variable comparison failed", expressionEvaluator.evaluate("12.5 == x", Map.of(
				"x", 12.5
		), LOCALE));

		Assert.assertTrue("Gender-variable comparison failed", expressionEvaluator.evaluate("gender == MASCULINE", Map.of(
				"gender", Gender.MASCULINE
		), LOCALE));

		Assert.assertTrue("Cardinality-variable comparison failed", expressionEvaluator.evaluate("CARDINALITY_OTHER == bigNumber", Map.of(
				"bigNumber", 1_000
		), LOCALE));

		Assert.assertTrue("Cardinality-variable comparison failed", expressionEvaluator.evaluate("CARDINALITY_ONE == exactlyOne", Map.of(
				"exactlyOne", 1
		), LOCALE));
	}

	@Test
	public void ordinalityExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		Assert.assertTrue("Ordinality identity failed", expressionEvaluator.evaluate("ORDINALITY_ONE == ORDINALITY_ONE", LOCALE));
		Assert.assertFalse("Unequal ordinalities evaluate as equal", expressionEvaluator.evaluate("ORDINALITY_ONE == ORDINALITY_OTHER", LOCALE));
		Assert.assertTrue("Unequal ordinalities evaluate as equal", expressionEvaluator.evaluate("ORDINALITY_ONE != ORDINALITY_OTHER", LOCALE));
	}

	@Test
	public void operatorPrecedence() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		boolean result = expressionEvaluator.evaluate("a == 1 || b == 1 && c == 1", Map.of(
				"a", 1,
				"b", 0,
				"c", 0
		), LOCALE);

		Assert.assertTrue("Expected && to bind tighter than ||", result);
	}

	@Test
	public void hyphenatedVariableExpressions() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		Assert.assertTrue("Hyphenated variable comparison failed", expressionEvaluator.evaluate("user-name == 2", Map.of(
				"user-name", 2
		), LOCALE));
	}

	@Test
	public void invalidCardinalityOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		try {
			expressionEvaluator.evaluate("CARDINALITY_ONE < CARDINALITY_TWO", LOCALE);
			Assert.fail("Expected invalid cardinality operator to throw");
		} catch (ExpressionEvaluationException expected) {
			// Expected
		}
	}

	@Test
	public void invalidOrdinalityOperator() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();

		try {
			expressionEvaluator.evaluate("ORDINALITY_ONE > ORDINALITY_TWO", LOCALE);
			Assert.fail("Expected invalid ordinality operator to throw");
		} catch (ExpressionEvaluationException expected) {
			// Expected
		}
	}

	@Test
	public void customTokenizerIsUsed() {
		ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(new ExpressionTokenizer() {
			@Override
		public List<Token> extractTokens(String expression) {
				return List.of(new Token(TokenType.NUMBER, "1"), new Token(TokenType.EQUAL_TO), new Token(TokenType.NUMBER, "1"));
			}
		});

		Assert.assertTrue("Custom tokenizer output should be evaluated", expressionEvaluator.evaluate("ignored", LOCALE));
	}
}
