/*
 * Copyright 2017 Product Mog LLC.
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
import java.util.HashMap;
import java.util.Locale;

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

		Assert.assertTrue("Plural identity failed", expressionEvaluator.evaluate("ONE == ONE", LOCALE));
		Assert.assertFalse("Unequal plurals evaluate as equal", expressionEvaluator.evaluate("ONE == MANY", LOCALE));
		Assert.assertTrue("Unequal plurals evaluate as equal", expressionEvaluator.evaluate("ONE != MANY", LOCALE));
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

		Assert.assertTrue("Number-variable comparison failed", expressionEvaluator.evaluate("12.5 == x", new HashMap<String, Object>() {{
			put("x", 12.5);
		}}, LOCALE));

		Assert.assertTrue("Gender-variable comparison failed", expressionEvaluator.evaluate("gender == MASCULINE", new HashMap<String, Object>() {{
			put("gender", Gender.MASCULINE);
		}}, LOCALE));

		Assert.assertTrue("Plural-variable comparison failed", expressionEvaluator.evaluate("OTHER == bigNumber", new HashMap<String, Object>() {{
			put("bigNumber", 1_000);
		}}, LOCALE));

		Assert.assertTrue("Plural-variable comparison failed", expressionEvaluator.evaluate("ONE == exactlyOne", new HashMap<String, Object>() {{
			put("exactlyOne", 1);
		}}, LOCALE));
	}
}