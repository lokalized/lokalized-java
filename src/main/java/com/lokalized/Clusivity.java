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

import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Language clusivity forms.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 1.2.0
 */
public enum Clusivity implements LanguageForm {
	/**
	 * Inclusive "we/us" (includes the addressee).
	 */
	INCLUSIVE,
	/**
	 * Exclusive "we/us" (excludes the addressee).
	 */
	EXCLUSIVE;

	@NonNull
	private static final Map<@NonNull String, @NonNull Clusivity> CLUSIVITIES_BY_NAME;

	static {
		CLUSIVITIES_BY_NAME = Collections.unmodifiableMap(Arrays.stream(
				Clusivity.values()).collect(Collectors.toMap(clusivity -> clusivity.name(), clusivity -> clusivity)));
	}

	/**
	 * Gets the mapping of clusivity names to clusivity values.
	 *
	 * @return the mapping of clusivity names to clusivity values, not null
	 */
	@NonNull
	static Map<@NonNull String, @NonNull Clusivity> getClusivitiesByName() {
		return CLUSIVITIES_BY_NAME;
	}
}
