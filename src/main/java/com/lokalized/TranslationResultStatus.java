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
 * Describes how a {@link TranslationResult} produced its returned string.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
public enum TranslationResultStatus {
	/** A catalog translation resolved successfully. */
	TRANSLATED,
	/** The failure handler requested the interpolated lookup key. */
	RETURNED_KEY,
	/** The failure handler supplied a replacement string. */
	RETURNED_STRING
}
