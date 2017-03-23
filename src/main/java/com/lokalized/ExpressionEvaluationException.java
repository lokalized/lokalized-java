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

import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Exception which indicates a problem encountered while evaluating a localized string expression.
 * <p>
 * This class is intended for use by a single thread.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@NotThreadSafe
public class ExpressionEvaluationException extends RuntimeException {
	/**
	 * Constructs a new exception with the specified message.
	 *
	 * @param message the message to use for this exception, may be null
	 */
	public ExpressionEvaluationException(@Nullable String message) {
		super(message);
	}

	/**
	 * Constructs a new exception with the specified message and cause.
	 *
	 * @param message the message to use for this exception, may be null
	 * @param cause   the cause of the exception, may be null
	 */
	public ExpressionEvaluationException(@Nullable String message, @Nullable Throwable cause) {
		super(message, cause);
	}
}