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

import org.junit.Assert;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.util.HashMap;
import java.util.Locale;

/**
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class StringsTests {
	@Test
	public void placeholderTest() {
		Strings strings = new DefaultStrings.Builder("en", () ->
				LocalizedStringLoader.loadFromClasspath("strings")
		).build();

		String translation = strings.get("I read {{bookCount}} books",
				new HashMap<String, Object>() {{
					put("bookCount", 3);
				}}, Locale.forLanguageTag("ru"));

		Assert.assertEquals("I прочитал 3 книги", translation);
	}
}
