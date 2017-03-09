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
import javax.annotation.concurrent.Immutable;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
class LocalizedString {
	@Nonnull
	private final String key;
	@Nonnull
	private final String translation;
	@Nonnull
	private final Map<String, Map<LanguageForm, String>> languageFormTranslationsByPlaceholder;
	@Nonnull
	private final List<LocalizedString> alternatives;

	public LocalizedString() {
		throw new UnsupportedOperationException();
	}
}