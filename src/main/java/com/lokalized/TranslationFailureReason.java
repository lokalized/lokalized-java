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

/**
 * Reasons a localized string lookup can fail.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
public enum TranslationFailureReason {
	/**
	 * No attempted candidate locale contained the requested key.
	 */
	MISSING_TRANSLATION,
	/**
	 * An attempted candidate locale contained the requested key, but no whole-message alternative matched and no default
	 * translation was provided.
	 * <p>
	 * A {@link LocalizedString.ExpressionTranslation} generated fragment cannot produce this reason: it always has a
	 * required default translation when none of its ordered alternatives matches.
	 */
	NO_MATCHING_ALTERNATIVE,
	/**
	 * A candidate translation existed, but placeholder, expression, interpolation, or language-form resolution failed.
	 * <p>
	 * This includes a failure while evaluating a reachable
	 * {@link LocalizedString.ExpressionAlternative expression-fragment predicate} or interpolating its selected/default
	 * fragment. A selected fragment's failure does not fall through to a later predicate or its default.
	 */
	RESOLUTION_FAILURE
}
