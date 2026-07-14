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
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Options applied while discovering and loading localized strings files.
 * <p>
 * The byte limit applies to {@link java.nio.file.Path} and {@link java.io.InputStream} inputs. The UTF-16 code-unit
 * limit applies to {@link java.io.Reader} inputs, whose original byte representation is not available to Lokalized. The
 * localized strings file-count, translation-node, and warning limits apply to every load, including single-resource {@code parse(...)}
 * operations. The aggregate byte limit also applies to path and input-stream parsing; it cannot apply to a
 * {@link java.io.Reader} because the original byte representation is unavailable.
 * The translation-node limit bounds parsed model structure; the byte and character limits separately bound the
 * localized strings file text containing that structure.
 * <p>
 * Exhaustive classpath searching is disabled by default because it inspects every filesystem and JAR root visible to
 * a classloader. Enable it only when localized strings are packaged in a JAR that omits directory entries and ordinary
 * {@link ClassLoader#getResources(String)} discovery therefore cannot find the requested package.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
@Immutable
public final class LocalizedStringLoadingOptions {
	/** Default maximum read from one localized strings resource: 8,388,608 bytes (8 MiB). */
	@NonNull
	public static final Integer DEFAULT_MAXIMUM_INPUT_BYTES = 8 * 1024 * 1024;
	/** Default maximum read from one {@link java.io.Reader}: 8,388,608 UTF-16 code units. */
	@NonNull
	public static final Integer DEFAULT_MAXIMUM_READER_CHARACTERS = 8 * 1024 * 1024;
	/** Default maximum JSON object/array nesting depth: 64. */
	@NonNull
	public static final Integer DEFAULT_MAXIMUM_JSON_NESTING_DEPTH = 64;
	/** Whether exhaustive classpath-root searching is enabled by default: false. */
	@NonNull
	public static final Boolean DEFAULT_EXHAUSTIVE_CLASSPATH_SEARCH = false;
	/** Highest configurable JSON nesting depth supported by the loader: 128. */
	@NonNull
	public static final Integer MAXIMUM_JSON_NESTING_DEPTH = 128;
	/** Default maximum total read by one load: 33,554,432 bytes (32 MiB). */
	@NonNull
	public static final Long DEFAULT_MAXIMUM_TOTAL_INPUT_BYTES = 32L * 1024L * 1024L;
	/** Default maximum number of localized strings files accepted by one load: 256. */
	@NonNull
	public static final Integer DEFAULT_MAXIMUM_LOCALIZED_STRINGS_FILES = 256;
	/**
	 * Default maximum translation nodes accepted by one load: 100,000. Translation nodes comprise root entries,
	 * whole-message alternatives, placeholder definitions, and expression-fragment alternatives.
	 */
	@NonNull
	public static final Integer DEFAULT_MAXIMUM_TRANSLATION_NODES = 100_000;
	/** Default maximum number of warnings emitted by one load: 1,000. */
	@NonNull
	public static final Integer DEFAULT_MAXIMUM_WARNINGS = 1_000;

	@NonNull
	private static final LocalizedStringLoadingOptions DEFAULTS = new Builder().build();

	private final int maximumInputBytes;
	private final int maximumReaderCharacters;
	private final int maximumJsonNestingDepth;
	private final boolean exhaustiveClasspathSearch;
	private final long maximumTotalInputBytes;
	private final int maximumLocalizedStringsFiles;
	private final int maximumTranslationNodes;
	private final int maximumWarnings;

	private LocalizedStringLoadingOptions(
			int maximumInputBytes,
			int maximumReaderCharacters,
			int maximumJsonNestingDepth,
			boolean exhaustiveClasspathSearch,
			long maximumTotalInputBytes,
			int maximumLocalizedStringsFiles,
			int maximumTranslationNodes,
			int maximumWarnings) {
		this.maximumInputBytes = maximumInputBytes;
		this.maximumReaderCharacters = maximumReaderCharacters;
		this.maximumJsonNestingDepth = maximumJsonNestingDepth;
		this.exhaustiveClasspathSearch = exhaustiveClasspathSearch;
		this.maximumTotalInputBytes = maximumTotalInputBytes;
		this.maximumLocalizedStringsFiles = maximumLocalizedStringsFiles;
		this.maximumTranslationNodes = maximumTranslationNodes;
		this.maximumWarnings = maximumWarnings;
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

	/** @return a builder initialized from this instance, not null */
	@NonNull
	public Builder toBuilder() {
		return builder()
				.maximumInputBytes(maximumInputBytes)
				.maximumReaderCharacters(maximumReaderCharacters)
				.maximumJsonNestingDepth(maximumJsonNestingDepth)
				.exhaustiveClasspathSearch(exhaustiveClasspathSearch)
				.maximumTotalInputBytes(maximumTotalInputBytes)
				.maximumLocalizedStringsFiles(maximumLocalizedStringsFiles)
				.maximumTranslationNodes(maximumTranslationNodes)
				.maximumWarnings(maximumWarnings);
	}

	/** @return the maximum bytes accepted from a path or input stream */
	@NonNull
	public Integer getMaximumInputBytes() {
		return maximumInputBytes;
	}

	/** @return the maximum UTF-16 code units accepted from a reader */
	@NonNull
	public Integer getMaximumReaderCharacters() {
		return maximumReaderCharacters;
	}

	/** @return the maximum JSON object/array nesting depth */
	@NonNull
	public Integer getMaximumJsonNestingDepth() {
		return maximumJsonNestingDepth;
	}

	/**
	 * Reports whether classpath loading should inspect every filesystem and JAR root visible to the classloader after
	 * ordinary package-resource discovery.
	 *
	 * @return true when exhaustive classpath-root searching is enabled
	 */
	@NonNull
	public Boolean isExhaustiveClasspathSearchEnabled() {
		return exhaustiveClasspathSearch;
	}

	/**
	 * Gets the maximum total bytes accepted by one filesystem, classpath, path, or input-stream load.
	 * This limit does not apply to reader input because its original byte representation is unavailable.
	 *
	 * @return the aggregate byte limit, not null
	 */
	@NonNull
	public Long getMaximumTotalInputBytes() {
		return maximumTotalInputBytes;
	}

	/** @return the maximum localized strings files accepted by one load, not null */
	@NonNull
	public Integer getMaximumLocalizedStringsFiles() {
		return maximumLocalizedStringsFiles;
	}

	/**
	 * Gets the maximum translation nodes accepted by one load. Translation nodes comprise root entries,
	 * whole-message alternatives, placeholder definitions, and expression-fragment alternatives.
	 *
	 * @return the aggregate translation-node limit, not null
	 */
	@NonNull
	public Integer getMaximumTranslationNodes() {
		return maximumTranslationNodes;
	}

	/** @return the maximum warnings emitted by one load, not null */
	@NonNull
	public Integer getMaximumWarnings() {
		return maximumWarnings;
	}

	@Override
	public boolean equals(@Nullable Object object) {
		if (this == object)
			return true;
		if (!(object instanceof LocalizedStringLoadingOptions))
			return false;
		LocalizedStringLoadingOptions that = (LocalizedStringLoadingOptions) object;
		return maximumInputBytes == that.maximumInputBytes
				&& maximumReaderCharacters == that.maximumReaderCharacters
				&& maximumJsonNestingDepth == that.maximumJsonNestingDepth
				&& exhaustiveClasspathSearch == that.exhaustiveClasspathSearch
				&& maximumTotalInputBytes == that.maximumTotalInputBytes
				&& maximumLocalizedStringsFiles == that.maximumLocalizedStringsFiles
				&& maximumTranslationNodes == that.maximumTranslationNodes
				&& maximumWarnings == that.maximumWarnings;
	}

	@Override
	public int hashCode() {
		return Objects.hash(maximumInputBytes, maximumReaderCharacters, maximumJsonNestingDepth,
				exhaustiveClasspathSearch, maximumTotalInputBytes, maximumLocalizedStringsFiles, maximumTranslationNodes,
				maximumWarnings);
	}

	@Override
	@NonNull
	public String toString() {
		return format("%s{maximumInputBytes=%d, maximumReaderCharacters=%d, maximumJsonNestingDepth=%d, " +
				"exhaustiveClasspathSearch=%s, maximumTotalInputBytes=%d, maximumLocalizedStringsFiles=%d, " +
				"maximumTranslationNodes=%d, maximumWarnings=%d}", getClass().getSimpleName(), maximumInputBytes,
				maximumReaderCharacters, maximumJsonNestingDepth, exhaustiveClasspathSearch, maximumTotalInputBytes,
				maximumLocalizedStringsFiles, maximumTranslationNodes, maximumWarnings);
	}

	/** Builder for {@link LocalizedStringLoadingOptions}. */
	@NotThreadSafe
	public static final class Builder {
		private int maximumInputBytes = DEFAULT_MAXIMUM_INPUT_BYTES;
		private int maximumReaderCharacters = DEFAULT_MAXIMUM_READER_CHARACTERS;
		private int maximumJsonNestingDepth = DEFAULT_MAXIMUM_JSON_NESTING_DEPTH;
		private boolean exhaustiveClasspathSearch = DEFAULT_EXHAUSTIVE_CLASSPATH_SEARCH;
		private long maximumTotalInputBytes = DEFAULT_MAXIMUM_TOTAL_INPUT_BYTES;
		private int maximumLocalizedStringsFiles = DEFAULT_MAXIMUM_LOCALIZED_STRINGS_FILES;
		private int maximumTranslationNodes = DEFAULT_MAXIMUM_TRANSLATION_NODES;
		private int maximumWarnings = DEFAULT_MAXIMUM_WARNINGS;

		private Builder() {
		}

		/**
		 * Sets the maximum bytes accepted from a path or input stream.
		 *
		 * @param maximumInputBytes positive byte limit, at most {@link Integer#MAX_VALUE} - 1
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumInputBytes(@NonNull Integer maximumInputBytes) {
			requireNonNull(maximumInputBytes);
			if (maximumInputBytes <= 0 || maximumInputBytes == Integer.MAX_VALUE)
				throw new IllegalArgumentException("maximumInputBytes must be between 1 and Integer.MAX_VALUE - 1");

			this.maximumInputBytes = maximumInputBytes;
			return this;
		}

		/**
		 * Sets the maximum UTF-16 code units accepted from a reader.
		 *
		 * @param maximumReaderCharacters positive UTF-16 code-unit limit
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumReaderCharacters(@NonNull Integer maximumReaderCharacters) {
			requireNonNull(maximumReaderCharacters);
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
		public Builder maximumJsonNestingDepth(@NonNull Integer maximumJsonNestingDepth) {
			requireNonNull(maximumJsonNestingDepth);
			if (maximumJsonNestingDepth <= 0 || maximumJsonNestingDepth > MAXIMUM_JSON_NESTING_DEPTH)
				throw new IllegalArgumentException("maximumJsonNestingDepth must be between 1 and " +
						MAXIMUM_JSON_NESTING_DEPTH);

			this.maximumJsonNestingDepth = maximumJsonNestingDepth;
			return this;
		}

		/**
		 * Controls whether classpath loading should inspect every filesystem and JAR root visible to the classloader when
		 * ordinary package-resource discovery is insufficient. This is disabled by default to avoid sweeping unrelated
		 * dependencies. It is primarily useful for JARs that omit directory entries.
		 *
		 * @param exhaustiveClasspathSearch true to enable exhaustive classpath-root searching
		 * @return this builder, not null
		 */
		@NonNull
		public Builder exhaustiveClasspathSearch(@NonNull Boolean exhaustiveClasspathSearch) {
			requireNonNull(exhaustiveClasspathSearch);
			this.exhaustiveClasspathSearch = exhaustiveClasspathSearch;
			return this;
		}

		/**
		 * Sets the maximum total bytes accepted by one filesystem, classpath, path, or input-stream load.
		 * This limit does not apply to reader input because its original byte representation is unavailable.
		 *
		 * @param maximumTotalInputBytes positive aggregate byte limit, not null
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumTotalInputBytes(@NonNull Long maximumTotalInputBytes) {
			if (requireNonNull(maximumTotalInputBytes) <= 0)
				throw new IllegalArgumentException("maximumTotalInputBytes must be positive");

			this.maximumTotalInputBytes = maximumTotalInputBytes;
			return this;
		}

		/**
		 * Sets the maximum localized strings files accepted by one load.
		 *
		 * @param maximumLocalizedStringsFiles positive localized strings file limit, not null
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumLocalizedStringsFiles(@NonNull Integer maximumLocalizedStringsFiles) {
			if (requireNonNull(maximumLocalizedStringsFiles) <= 0)
				throw new IllegalArgumentException("maximumLocalizedStringsFiles must be positive");

			this.maximumLocalizedStringsFiles = maximumLocalizedStringsFiles;
			return this;
		}

		/**
		 * Sets the maximum translation nodes accepted by one load. Translation nodes comprise root entries,
		 * whole-message alternatives, placeholder definitions, and expression-fragment alternatives.
		 *
		 * @param maximumTranslationNodes nonnegative translation-node limit, not null
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumTranslationNodes(@NonNull Integer maximumTranslationNodes) {
			if (requireNonNull(maximumTranslationNodes) < 0)
				throw new IllegalArgumentException("maximumTranslationNodes must be nonnegative");

			this.maximumTranslationNodes = maximumTranslationNodes;
			return this;
		}

		/**
		 * Sets the maximum warnings emitted by one load.
		 *
		 * @param maximumWarnings nonnegative warning limit, not null
		 * @return this builder, not null
		 */
		@NonNull
		public Builder maximumWarnings(@NonNull Integer maximumWarnings) {
			if (requireNonNull(maximumWarnings) < 0)
				throw new IllegalArgumentException("maximumWarnings must be nonnegative");

			this.maximumWarnings = maximumWarnings;
			return this;
		}

		/** @return immutable loading options, not null */
		@NonNull
		public LocalizedStringLoadingOptions build() {
			return new LocalizedStringLoadingOptions(maximumInputBytes, maximumReaderCharacters,
					maximumJsonNestingDepth, exhaustiveClasspathSearch, maximumTotalInputBytes,
					maximumLocalizedStringsFiles,
					maximumTranslationNodes, maximumWarnings);
		}
	}
}
