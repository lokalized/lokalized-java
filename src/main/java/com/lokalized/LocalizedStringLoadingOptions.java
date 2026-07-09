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

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;

/**
 * Resource limits applied while loading a localized strings file.
 * <p>
 * The byte limit applies to {@link java.nio.file.Path} and {@link java.io.InputStream} inputs. The character limit
 * applies to {@link java.io.Reader} inputs, whose original byte representation is not available to Lokalized.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
@Immutable
public final class LocalizedStringLoadingOptions {
	/** Default maximum number of bytes read from one localized strings resource: 16 MiB. */
	public static final int DEFAULT_MAXIMUM_INPUT_BYTES = 16 * 1024 * 1024;
	/** Default maximum number of characters read from one {@link java.io.Reader}: 16 MiB characters. */
	public static final int DEFAULT_MAXIMUM_READER_CHARACTERS = 16 * 1024 * 1024;
	/** Default maximum JSON object/array nesting depth. */
	public static final int DEFAULT_MAXIMUM_JSON_NESTING_DEPTH = 128;
	/** Highest configurable JSON nesting depth supported by the recursive parser. */
	public static final int MAXIMUM_JSON_NESTING_DEPTH = 128;

	@NonNull
	private static final LocalizedStringLoadingOptions DEFAULTS = new Builder().build();

	private final int maximumInputBytes;
	private final int maximumReaderCharacters;
	private final int maximumJsonNestingDepth;

	private LocalizedStringLoadingOptions(int maximumInputBytes,
															 int maximumReaderCharacters,
															 int maximumJsonNestingDepth) {
		this.maximumInputBytes = maximumInputBytes;
		this.maximumReaderCharacters = maximumReaderCharacters;
		this.maximumJsonNestingDepth = maximumJsonNestingDepth;
	}

	/**
	 * Gets the default loading options.
	 *
	 * @return the default immutable options, not null
	 */
	@NonNull
	public static LocalizedStringLoadingOptions defaults() {
		return DEFAULTS;
	}

	/**
	 * Creates a builder initialized with the default limits.
	 *
	 * @return a new builder, not null
	 */
	@NonNull
	public static Builder builder() {
		return new Builder();
	}

	/** @return the maximum bytes accepted from a path or input stream */
	public int getMaximumInputBytes() {
		return maximumInputBytes;
	}

	/** @return the maximum characters accepted from a reader */
	public int getMaximumReaderCharacters() {
		return maximumReaderCharacters;
	}

	/** @return the maximum JSON object/array nesting depth */
	public int getMaximumJsonNestingDepth() {
		return maximumJsonNestingDepth;
	}

	/** Builder for {@link LocalizedStringLoadingOptions}. */
	@NotThreadSafe
	public static final class Builder {
		private int maximumInputBytes = DEFAULT_MAXIMUM_INPUT_BYTES;
		private int maximumReaderCharacters = DEFAULT_MAXIMUM_READER_CHARACTERS;
		private int maximumJsonNestingDepth = DEFAULT_MAXIMUM_JSON_NESTING_DEPTH;

		private Builder() {
		}

		/**
		 * Sets the maximum bytes accepted from a path or input stream.
		 *
		 * @param maximumInputBytes positive byte limit, at most {@link Integer#MAX_VALUE} - 1
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumInputBytes(int maximumInputBytes) {
			if (maximumInputBytes <= 0 || maximumInputBytes == Integer.MAX_VALUE)
				throw new IllegalArgumentException("maximumInputBytes must be between 1 and Integer.MAX_VALUE - 1");

			this.maximumInputBytes = maximumInputBytes;
			return this;
		}

		/**
		 * Sets the maximum characters accepted from a reader.
		 *
		 * @param maximumReaderCharacters positive character limit
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumReaderCharacters(int maximumReaderCharacters) {
			if (maximumReaderCharacters <= 0)
				throw new IllegalArgumentException("maximumReaderCharacters must be positive");

			this.maximumReaderCharacters = maximumReaderCharacters;
			return this;
		}

		/**
		 * Sets the maximum JSON object/array nesting depth.
		 *
		 * @param maximumJsonNestingDepth nesting limit from 1 through {@link #MAXIMUM_JSON_NESTING_DEPTH}
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumJsonNestingDepth(int maximumJsonNestingDepth) {
			if (maximumJsonNestingDepth <= 0 || maximumJsonNestingDepth > MAXIMUM_JSON_NESTING_DEPTH)
				throw new IllegalArgumentException("maximumJsonNestingDepth must be between 1 and " +
						MAXIMUM_JSON_NESTING_DEPTH);

			this.maximumJsonNestingDepth = maximumJsonNestingDepth;
			return this;
		}

		/** @return immutable loading options, not null */
		@NonNull
		public LocalizedStringLoadingOptions build() {
			return new LocalizedStringLoadingOptions(maximumInputBytes, maximumReaderCharacters,
					maximumJsonNestingDepth);
		}
	}
}
