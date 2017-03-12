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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class LocalizedStringLoaderTests {
	@Test
	public void testClasspathLoading() {
		verifyLocalizedStringsByLocale(LocalizedStringLoader.loadFromClasspath("strings"));
	}

	@Test
	public void testFilesystemLoading() {
		verifyLocalizedStringsByLocale(LocalizedStringLoader.loadFromFilesystem(Paths.get("src/test/resources/strings")));
	}

	protected void verifyLocalizedStringsByLocale(@Nonnull Map<Locale, Set<LocalizedString>> localizedStringsByLocale) {
		requireNonNull(localizedStringsByLocale);

		Assert.assertEquals("Unexpected number of strings files", 3, localizedStringsByLocale.size());

		for (Entry<Locale, Set<LocalizedString>> entry : localizedStringsByLocale.entrySet())
			Assert.assertTrue(format("The '%s' strings file has no data", entry.getKey().toLanguageTag()),
					entry.getValue().size() > 0);
	}
}
