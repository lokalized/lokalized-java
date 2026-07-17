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

/**
 * Immutable safety limits for translation construction and evaluation.
 * <p>
 * The defaults are deliberately generous enough for normal localized strings while bounding work that is proportional to
 * untrusted localized strings data or runtime values. Builders may customize a limit through the library's hard ceiling.
 * Localized strings loaders validate expressions against hard ceilings because an application's runtime policy is not yet
 * available; {@link Strings} construction then enforces its configured limits. A single instance is safe to share
 * between {@link Strings} instances and threads.
 * <p>
 * Caller-supplied {@link CharSequence} values are length-checked before Lokalized scans, copies, bidi-isolates, or
 * materializes them for phonetic resolution. The interpolated-output limit also bounds one phonetic input. For other
 * placeholder object types, Lokalized must invoke application-defined {@link Object#toString()} code before it can
 * measure the result; these limits bound Lokalized's subsequent work but cannot bound work performed inside that
 * application method. Use a pre-bounded {@code String} or {@code CharSequence} when runtime values originate from
 * untrusted input.
 * <p>
 * Expression source, token, and nesting limits apply equally to whole-message alternative predicates and
 * {@link LocalizedString.ExpressionAlternative expression-fragment predicates}. Generated-placeholder depth and
 * cumulative expansion limits are shared by {@link LocalizedString.LanguageFormTranslation language-form fragments}
 * and {@link LocalizedString.ExpressionTranslation expression-selected fragments}, including dependencies that cross
 * between the two kinds.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 * @since 3.0.0
 */
@Immutable
public final class TranslationRuntimeLimits {
	/** Default limit for decimal precision: 1,024. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_NUMBER_PRECISION = 1_024;
	/** Default limit for the absolute value of a decimal scale: 1,024. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_ABSOLUTE_NUMBER_SCALE = 1_024;
	/** Default limit for explicitly visible decimal places: 1,024. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_VISIBLE_DECIMAL_PLACES = 1_024;
	/** Default limit for compact-decimal exponents: 64. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_COMPACT_EXPONENT = 64;
	/** Default limit for characters in one whole-message or generated-fragment expression: 2,048. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_EXPRESSION_CHARACTERS = 2_048;
	/** Default limit for tokens in one whole-message or generated-fragment expression: 256. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_EXPRESSION_TOKENS = 256;
	/** Default limit for nested groups in one whole-message or generated-fragment expression: 32. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_EXPRESSION_NESTING_DEPTH = 32;
	/** Default limit for nested generated placeholders across both definition kinds: 32. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_GENERATED_PLACEHOLDER_DEPTH = 32;
	/** Default limit for one interpolated message, generated fragment, or phonetic input: 262,144 UTF-16 code units. */
	@NonNull public static final Integer DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS = 256 * 1_024;
	/**
	 * Default cumulative expansion limit across both generated-placeholder kinds: 1,048,576 UTF-16 code units per
	 * locale fallback attempt.
	 */
	@NonNull public static final Integer DEFAULT_MAXIMUM_GENERATED_EXPANSION_CHARACTERS = 1_024 * 1_024;

	/** Hard ceiling for decimal precision: 4,096. */
	@NonNull public static final Integer MAXIMUM_NUMBER_PRECISION = 4_096;
	/** Hard ceiling for the absolute value of a decimal scale: 4,096. */
	@NonNull public static final Integer MAXIMUM_ABSOLUTE_NUMBER_SCALE = 4_096;
	/** Hard ceiling for explicitly visible decimal places: 4,096. */
	@NonNull public static final Integer MAXIMUM_VISIBLE_DECIMAL_PLACES = 4_096;
	/** Hard ceiling for compact-decimal exponents: 4,096. */
	@NonNull public static final Integer MAXIMUM_COMPACT_EXPONENT = 4_096;
	/** Hard ceiling for characters in one whole-message or generated-fragment expression: 4,096. */
	@NonNull public static final Integer MAXIMUM_EXPRESSION_CHARACTERS = 4_096;
	/** Hard ceiling for tokens in one whole-message or generated-fragment expression: 512. */
	@NonNull public static final Integer MAXIMUM_EXPRESSION_TOKENS = 512;
	/** Hard ceiling for nested groups in one whole-message or generated-fragment expression: 64. */
	@NonNull public static final Integer MAXIMUM_EXPRESSION_NESTING_DEPTH = 64;
	/** Hard ceiling for nested generated placeholders across both definition kinds: 64. */
	@NonNull public static final Integer MAXIMUM_GENERATED_PLACEHOLDER_DEPTH = 64;
	/** Hard ceiling for one interpolated message, generated fragment, or phonetic input: 1,048,576 UTF-16 code units. */
	@NonNull public static final Integer MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS = 1_024 * 1_024;
	/**
	 * Hard ceiling for cumulative expansion across both generated-placeholder kinds: 8,388,608 UTF-16 code units per
	 * locale fallback attempt.
	 */
	@NonNull public static final Integer MAXIMUM_GENERATED_EXPANSION_CHARACTERS = 8 * 1_024 * 1_024;

	@NonNull
	private static final TranslationRuntimeLimits DEFAULTS = new Builder().build();
	@NonNull
	private static final TranslationRuntimeLimits HARD_CEILINGS = new Builder()
			.maximumNumberPrecision(MAXIMUM_NUMBER_PRECISION)
			.maximumAbsoluteNumberScale(MAXIMUM_ABSOLUTE_NUMBER_SCALE)
			.maximumVisibleDecimalPlaces(MAXIMUM_VISIBLE_DECIMAL_PLACES)
			.maximumCompactExponent(MAXIMUM_COMPACT_EXPONENT)
			.maximumExpressionCharacters(MAXIMUM_EXPRESSION_CHARACTERS)
			.maximumExpressionTokens(MAXIMUM_EXPRESSION_TOKENS)
			.maximumExpressionNestingDepth(MAXIMUM_EXPRESSION_NESTING_DEPTH)
			.maximumGeneratedPlaceholderDepth(MAXIMUM_GENERATED_PLACEHOLDER_DEPTH)
			.maximumInterpolatedOutputCharacters(MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS)
			.maximumGeneratedExpansionCharacters(MAXIMUM_GENERATED_EXPANSION_CHARACTERS)
			.build();

	@NonNull private final Integer maximumNumberPrecision;
	@NonNull private final Integer maximumAbsoluteNumberScale;
	@NonNull private final Integer maximumVisibleDecimalPlaces;
	@NonNull private final Integer maximumCompactExponent;
	@NonNull private final Integer maximumExpressionCharacters;
	@NonNull private final Integer maximumExpressionTokens;
	@NonNull private final Integer maximumExpressionNestingDepth;
	@NonNull private final Integer maximumGeneratedPlaceholderDepth;
	@NonNull private final Integer maximumInterpolatedOutputCharacters;
	@NonNull private final Integer maximumGeneratedExpansionCharacters;

	private TranslationRuntimeLimits(@NonNull Builder builder) {
		this.maximumNumberPrecision = builder.maximumNumberPrecision;
		this.maximumAbsoluteNumberScale = builder.maximumAbsoluteNumberScale;
		this.maximumVisibleDecimalPlaces = builder.maximumVisibleDecimalPlaces;
		this.maximumCompactExponent = builder.maximumCompactExponent;
		this.maximumExpressionCharacters = builder.maximumExpressionCharacters;
		this.maximumExpressionTokens = builder.maximumExpressionTokens;
		this.maximumExpressionNestingDepth = builder.maximumExpressionNestingDepth;
		this.maximumGeneratedPlaceholderDepth = builder.maximumGeneratedPlaceholderDepth;
		this.maximumInterpolatedOutputCharacters = builder.maximumInterpolatedOutputCharacters;
		this.maximumGeneratedExpansionCharacters = builder.maximumGeneratedExpansionCharacters;
	}

	/**
	 * Gets the library-default limits.
	 *
	 * @return the library-default limits, not null
	 */
	@NonNull public static TranslationRuntimeLimits defaults() { return DEFAULTS; }

	/**
	 * Gets the library hard ceilings.
	 *
	 * @return the library hard ceilings, not null
	 */
	@NonNull static TranslationRuntimeLimits hardCeilings() { return HARD_CEILINGS; }

	/**
	 * Creates a builder initialized to the library defaults.
	 *
	 * @return a builder initialized to the library defaults, not null
	 */
	@NonNull public static Builder builder() { return new Builder(); }

	/**
	 * Creates a builder initialized from this instance.
	 *
	 * @return a builder initialized from this instance, not null
	 */
	@NonNull public Builder toBuilder() { return new Builder(this); }

	/**
	 * Gets the maximum decimal precision.
	 *
	 * @return maximum decimal precision, not null
	 */
	@NonNull public Integer getMaximumNumberPrecision() { return maximumNumberPrecision; }
	/**
	 * Gets the maximum absolute decimal scale.
	 *
	 * @return maximum absolute decimal scale, not null
	 */
	@NonNull public Integer getMaximumAbsoluteNumberScale() { return maximumAbsoluteNumberScale; }
	/**
	 * Gets the maximum number of explicitly visible decimal places.
	 *
	 * @return maximum explicitly visible decimal places, not null
	 */
	@NonNull public Integer getMaximumVisibleDecimalPlaces() { return maximumVisibleDecimalPlaces; }
	/**
	 * Gets the maximum compact-decimal exponent.
	 *
	 * @return maximum compact-decimal exponent, not null
	 */
	@NonNull public Integer getMaximumCompactExponent() { return maximumCompactExponent; }
	/**
	 * Gets the maximum characters in one whole-message or generated-fragment expression.
	 *
	 * @return maximum characters in one whole-message or generated-fragment expression, not null
	 */
	@NonNull public Integer getMaximumExpressionCharacters() { return maximumExpressionCharacters; }
	/**
	 * Gets the maximum tokens in one whole-message or generated-fragment expression.
	 *
	 * @return maximum tokens in one whole-message or generated-fragment expression, not null
	 */
	@NonNull public Integer getMaximumExpressionTokens() { return maximumExpressionTokens; }
	/**
	 * Gets the maximum nested groups in one whole-message or generated-fragment expression.
	 *
	 * @return maximum nested groups in one whole-message or generated-fragment expression, not null
	 */
	@NonNull public Integer getMaximumExpressionNestingDepth() { return maximumExpressionNestingDepth; }
	/**
	 * Gets the maximum generated-placeholder nesting depth across both definition kinds.
	 *
	 * @return maximum generated-placeholder nesting depth across both definition kinds, not null
	 */
	@NonNull public Integer getMaximumGeneratedPlaceholderDepth() { return maximumGeneratedPlaceholderDepth; }
	/**
	 * Gets the maximum UTF-16 code units in one interpolated message, generated fragment, or caller-supplied phonetic
	 * input.
	 *
	 * @return maximum UTF-16 code units in one interpolated message, generated fragment, or phonetic input, not null
	 */
	@NonNull public Integer getMaximumInterpolatedOutputCharacters() { return maximumInterpolatedOutputCharacters; }
	/**
	 * Gets the maximum cumulative expansion across both generated-placeholder kinds per locale fallback attempt.
	 *
	 * @return maximum cumulative expansion across both generated-placeholder kinds in UTF-16 code units per
	 * locale fallback attempt, not null
	 */
	@NonNull public Integer getMaximumGeneratedExpansionCharacters() { return maximumGeneratedExpansionCharacters; }

	@Override
	public boolean equals(@Nullable Object object) {
		if (this == object)
			return true;
		if (!(object instanceof TranslationRuntimeLimits))
			return false;
		TranslationRuntimeLimits that = (TranslationRuntimeLimits) object;
		return Objects.equals(maximumNumberPrecision, that.maximumNumberPrecision)
				&& Objects.equals(maximumAbsoluteNumberScale, that.maximumAbsoluteNumberScale)
				&& Objects.equals(maximumVisibleDecimalPlaces, that.maximumVisibleDecimalPlaces)
				&& Objects.equals(maximumCompactExponent, that.maximumCompactExponent)
				&& Objects.equals(maximumExpressionCharacters, that.maximumExpressionCharacters)
				&& Objects.equals(maximumExpressionTokens, that.maximumExpressionTokens)
				&& Objects.equals(maximumExpressionNestingDepth, that.maximumExpressionNestingDepth)
				&& Objects.equals(maximumGeneratedPlaceholderDepth, that.maximumGeneratedPlaceholderDepth)
				&& Objects.equals(maximumInterpolatedOutputCharacters, that.maximumInterpolatedOutputCharacters)
				&& Objects.equals(maximumGeneratedExpansionCharacters, that.maximumGeneratedExpansionCharacters);
	}

	@Override
	public int hashCode() {
		return Objects.hash(maximumNumberPrecision, maximumAbsoluteNumberScale, maximumVisibleDecimalPlaces,
				maximumCompactExponent, maximumExpressionCharacters, maximumExpressionTokens,
				maximumExpressionNestingDepth, maximumGeneratedPlaceholderDepth,
				maximumInterpolatedOutputCharacters, maximumGeneratedExpansionCharacters);
	}

	@Override
	@NonNull
	public String toString() {
		return format("%s{maximumNumberPrecision=%d, maximumAbsoluteNumberScale=%d, " +
				"maximumVisibleDecimalPlaces=%d, maximumCompactExponent=%d, maximumExpressionCharacters=%d, " +
				"maximumExpressionTokens=%d, maximumExpressionNestingDepth=%d, " +
				"maximumGeneratedPlaceholderDepth=%d, maximumInterpolatedOutputCharacters=%d, " +
				"maximumGeneratedExpansionCharacters=%d}", getClass().getSimpleName(), maximumNumberPrecision,
				maximumAbsoluteNumberScale, maximumVisibleDecimalPlaces, maximumCompactExponent,
				maximumExpressionCharacters, maximumExpressionTokens, maximumExpressionNestingDepth,
				maximumGeneratedPlaceholderDepth, maximumInterpolatedOutputCharacters,
				maximumGeneratedExpansionCharacters);
	}

	/**
	 * Builder for {@link TranslationRuntimeLimits}.
	 *
	 * @since 3.0.0
	 */
	@NotThreadSafe
	public static final class Builder {
		@NonNull private Integer maximumNumberPrecision = DEFAULT_MAXIMUM_NUMBER_PRECISION;
		@NonNull private Integer maximumAbsoluteNumberScale = DEFAULT_MAXIMUM_ABSOLUTE_NUMBER_SCALE;
		@NonNull private Integer maximumVisibleDecimalPlaces = DEFAULT_MAXIMUM_VISIBLE_DECIMAL_PLACES;
		@NonNull private Integer maximumCompactExponent = DEFAULT_MAXIMUM_COMPACT_EXPONENT;
		@NonNull private Integer maximumExpressionCharacters = DEFAULT_MAXIMUM_EXPRESSION_CHARACTERS;
		@NonNull private Integer maximumExpressionTokens = DEFAULT_MAXIMUM_EXPRESSION_TOKENS;
		@NonNull private Integer maximumExpressionNestingDepth = DEFAULT_MAXIMUM_EXPRESSION_NESTING_DEPTH;
		@NonNull private Integer maximumGeneratedPlaceholderDepth = DEFAULT_MAXIMUM_GENERATED_PLACEHOLDER_DEPTH;
		@NonNull private Integer maximumInterpolatedOutputCharacters = DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS;
		@NonNull private Integer maximumGeneratedExpansionCharacters = DEFAULT_MAXIMUM_GENERATED_EXPANSION_CHARACTERS;

		private Builder() {}

		private Builder(@NonNull TranslationRuntimeLimits limits) {
			this.maximumNumberPrecision = limits.maximumNumberPrecision;
			this.maximumAbsoluteNumberScale = limits.maximumAbsoluteNumberScale;
			this.maximumVisibleDecimalPlaces = limits.maximumVisibleDecimalPlaces;
			this.maximumCompactExponent = limits.maximumCompactExponent;
			this.maximumExpressionCharacters = limits.maximumExpressionCharacters;
			this.maximumExpressionTokens = limits.maximumExpressionTokens;
			this.maximumExpressionNestingDepth = limits.maximumExpressionNestingDepth;
			this.maximumGeneratedPlaceholderDepth = limits.maximumGeneratedPlaceholderDepth;
			this.maximumInterpolatedOutputCharacters = limits.maximumInterpolatedOutputCharacters;
			this.maximumGeneratedExpansionCharacters = limits.maximumGeneratedExpansionCharacters;
		}

		/**
		 * Sets the decimal-precision limit.
		 *
		 * @param value value from 1 through {@link #MAXIMUM_NUMBER_PRECISION}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumNumberPrecision(@Nullable Integer value) {
			this.maximumNumberPrecision = value == null ? DEFAULT_MAXIMUM_NUMBER_PRECISION : value;
			return this;
		}
		/**
		 * Sets the absolute decimal-scale limit.
		 *
		 * @param value value from 0 through {@link #MAXIMUM_ABSOLUTE_NUMBER_SCALE}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumAbsoluteNumberScale(@Nullable Integer value) {
			this.maximumAbsoluteNumberScale = value == null ? DEFAULT_MAXIMUM_ABSOLUTE_NUMBER_SCALE : value;
			return this;
		}
		/**
		 * Sets the explicitly visible-decimal-place limit.
		 *
		 * @param value value from 0 through {@link #MAXIMUM_VISIBLE_DECIMAL_PLACES}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumVisibleDecimalPlaces(@Nullable Integer value) {
			this.maximumVisibleDecimalPlaces = value == null ? DEFAULT_MAXIMUM_VISIBLE_DECIMAL_PLACES : value;
			return this;
		}
		/**
		 * Sets the compact-decimal-exponent limit.
		 *
		 * @param value value from 0 through {@link #MAXIMUM_COMPACT_EXPONENT}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumCompactExponent(@Nullable Integer value) {
			this.maximumCompactExponent = value == null ? DEFAULT_MAXIMUM_COMPACT_EXPONENT : value;
			return this;
		}
		/**
		 * Sets the source-length limit for each whole-message or generated-fragment expression.
		 *
		 * @param value value from 1 through {@link #MAXIMUM_EXPRESSION_CHARACTERS}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumExpressionCharacters(@Nullable Integer value) {
			this.maximumExpressionCharacters = value == null ? DEFAULT_MAXIMUM_EXPRESSION_CHARACTERS : value;
			return this;
		}
		/**
		 * Sets the token-count limit for each whole-message or generated-fragment expression.
		 *
		 * @param value value from 1 through {@link #MAXIMUM_EXPRESSION_TOKENS}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumExpressionTokens(@Nullable Integer value) {
			this.maximumExpressionTokens = value == null ? DEFAULT_MAXIMUM_EXPRESSION_TOKENS : value;
			return this;
		}
		/**
		 * Sets the nested-group limit for each whole-message or generated-fragment expression.
		 *
		 * @param value value from 0 through {@link #MAXIMUM_EXPRESSION_NESTING_DEPTH}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumExpressionNestingDepth(@Nullable Integer value) {
			this.maximumExpressionNestingDepth = value == null ? DEFAULT_MAXIMUM_EXPRESSION_NESTING_DEPTH : value;
			return this;
		}
		/**
		 * Sets the generated-placeholder nesting limit shared by language-form and expression-selected fragments.
		 *
		 * @param value value from 0 through {@link #MAXIMUM_GENERATED_PLACEHOLDER_DEPTH}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumGeneratedPlaceholderDepth(@Nullable Integer value) {
			this.maximumGeneratedPlaceholderDepth = value == null ? DEFAULT_MAXIMUM_GENERATED_PLACEHOLDER_DEPTH : value;
			return this;
		}
		/**
		 * Sets the maximum UTF-16 code-unit count for one interpolated message, generated fragment, or caller-supplied
		 * phonetic input.
		 *
		 * @param value value from 1 through {@link #MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumInterpolatedOutputCharacters(@Nullable Integer value) {
			this.maximumInterpolatedOutputCharacters = value == null ? DEFAULT_MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS : value;
			return this;
		}
		/**
		 * Sets the cumulative UTF-16 code-unit budget shared by language-form and expression-selected generated fragments
		 * in one locale fallback attempt. Locale fallback starts a fresh budget for each candidate.
		 *
		 * @param value value from 0 through {@link #MAXIMUM_GENERATED_EXPANSION_CHARACTERS}, or null to restore the default
		 * @return this builder, not null
		 */
		@NonNull public Builder maximumGeneratedExpansionCharacters(@Nullable Integer value) {
			this.maximumGeneratedExpansionCharacters = value == null ? DEFAULT_MAXIMUM_GENERATED_EXPANSION_CHARACTERS : value;
			return this;
		}

		/**
		 * Builds immutable runtime limits.
		 *
		 * @return immutable runtime limits, not null
		 * @throws IllegalArgumentException if a configured value is outside its documented range
		 */
		@NonNull
		public TranslationRuntimeLimits build() {
			validatePositive("maximumNumberPrecision", maximumNumberPrecision, MAXIMUM_NUMBER_PRECISION);
			validateNonNegative("maximumAbsoluteNumberScale", maximumAbsoluteNumberScale, MAXIMUM_ABSOLUTE_NUMBER_SCALE);
			validateNonNegative("maximumVisibleDecimalPlaces", maximumVisibleDecimalPlaces, MAXIMUM_VISIBLE_DECIMAL_PLACES);
			validateNonNegative("maximumCompactExponent", maximumCompactExponent, MAXIMUM_COMPACT_EXPONENT);
			validatePositive("maximumExpressionCharacters", maximumExpressionCharacters, MAXIMUM_EXPRESSION_CHARACTERS);
			validatePositive("maximumExpressionTokens", maximumExpressionTokens, MAXIMUM_EXPRESSION_TOKENS);
			validateNonNegative("maximumExpressionNestingDepth", maximumExpressionNestingDepth, MAXIMUM_EXPRESSION_NESTING_DEPTH);
			validateNonNegative("maximumGeneratedPlaceholderDepth", maximumGeneratedPlaceholderDepth, MAXIMUM_GENERATED_PLACEHOLDER_DEPTH);
			validatePositive("maximumInterpolatedOutputCharacters", maximumInterpolatedOutputCharacters, MAXIMUM_INTERPOLATED_OUTPUT_CHARACTERS);
			validateNonNegative("maximumGeneratedExpansionCharacters", maximumGeneratedExpansionCharacters, MAXIMUM_GENERATED_EXPANSION_CHARACTERS);
			return new TranslationRuntimeLimits(this);
		}

		private static void validatePositive(@NonNull String name, @NonNull Integer value, @NonNull Integer ceiling) {
			if (value <= 0)
				throw new IllegalArgumentException(format("%s must be positive, but was %d", name, value));
			validateCeiling(name, value, ceiling);
		}

		private static void validateNonNegative(@NonNull String name, @NonNull Integer value, @NonNull Integer ceiling) {
			if (value < 0)
				throw new IllegalArgumentException(format("%s must be non-negative, but was %d", name, value));
			validateCeiling(name, value, ceiling);
		}

		private static void validateCeiling(@NonNull String name, @NonNull Integer value, @NonNull Integer ceiling) {
			if (value > ceiling)
				throw new IllegalArgumentException(format("%s %d exceeds the hard ceiling of %d", name, value, ceiling));
		}
	}
}
