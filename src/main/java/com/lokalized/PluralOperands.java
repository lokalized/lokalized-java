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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Unicode CLDR plural operands for cardinality and ordinality evaluation.
 * <p>
 * Most applications should use {@link Cardinality#forNumber(Number, java.util.Locale)} or
 * {@link Ordinality#forNumber(Number, java.util.Locale)}. Use this type when the displayed number has details that
 * are not fully represented by the Java {@link Number}, such as an explicitly visible decimal count or a compact-decimal exponent.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@Immutable
public final class PluralOperands {
  /**
   * Maximum precision accepted for a number used in plural operands or an alternative-expression numeric literal.
   *
   * @since 3.0.0
   */
  @NonNull
  public static final Integer MAXIMUM_NUMBER_PRECISION = TranslationRuntimeLimits.MAXIMUM_NUMBER_PRECISION;
  /**
   * Maximum absolute {@link BigDecimal#scale()} accepted for a number used in plural operands or an
   * alternative-expression numeric literal.
   *
   * @since 3.0.0
   */
  @NonNull
  public static final Integer MAXIMUM_ABSOLUTE_NUMBER_SCALE = TranslationRuntimeLimits.MAXIMUM_ABSOLUTE_NUMBER_SCALE;
  /**
   * Maximum number of explicitly visible decimal places accepted by {@link Builder#visibleDecimalPlaces(Integer)}.
   *
   * @since 3.0.0
   */
  @NonNull
  public static final Integer MAXIMUM_VISIBLE_DECIMAL_PLACES = TranslationRuntimeLimits.MAXIMUM_VISIBLE_DECIMAL_PLACES;
  /**
   * Maximum compact-decimal exponent accepted by {@link Builder#compactExponent(Integer)}.
   *
   * @since 3.0.0
   */
  @NonNull
  public static final Integer MAXIMUM_COMPACT_EXPONENT = TranslationRuntimeLimits.MAXIMUM_COMPACT_EXPONENT;

  // Input scale, visible scale, and compact shifting can each add zeroes to the materialized unscaled value.
  private static final int MAXIMUM_MATERIALIZED_PRECISION = 12_288;

  @NonNull
  private final BigDecimal n;
  @NonNull
  private final BigDecimal i;
  @NonNull
  private final BigDecimal v;
  @NonNull
  private final BigDecimal w;
  @NonNull
  private final BigDecimal f;
  @NonNull
  private final BigDecimal t;
  @NonNull
  private final BigDecimal c;
  @NonNull
  private final BigDecimal e;
	@NonNull
	private final BigDecimal sourceNumber;
	@Nullable
	private final Integer explicitVisibleDecimalPlaces;

  private PluralOperands(@NonNull BigDecimal sourceNumber, @NonNull BigDecimal number, int compactExponent,
								 @Nullable Integer explicitVisibleDecimalPlaces) {
    requireNonNull(sourceNumber);
    requireNonNull(number);

    @NonNull BigDecimal operandNumber = number.movePointRight(compactExponent);
    @NonNull BigDecimal strippedNumber = operandNumber.stripTrailingZeros();
    @NonNull BigDecimal exponent = BigDecimal.valueOf(compactExponent);

    this.n = operandNumber;
    this.i = new BigDecimal(NumberUtils.integerComponent(operandNumber));
    this.v = BigDecimal.valueOf(NumberUtils.numberOfDecimalPlaces(operandNumber));
    this.w = BigDecimal.valueOf(NumberUtils.numberOfDecimalPlaces(strippedNumber));
    this.f = new BigDecimal(NumberUtils.fractionalComponent(operandNumber));
    this.t = new BigDecimal(NumberUtils.fractionalComponent(strippedNumber));
    this.c = exponent;
    this.e = exponent;
		this.sourceNumber = sourceNumber;
		this.explicitVisibleDecimalPlaces = explicitVisibleDecimalPlaces;
  }

  /**
   * Validates the non-materializing characteristics of a decimal before code derives integer and fractional operands.
   */
  @NonNull
  static BigDecimal validateNumericValue(@NonNull BigDecimal number, @NonNull String description) {
    return validateNumericValue(number, description, TranslationRuntimeLimits.defaults());
  }

  /**
   * Validates a decimal against the supplied runtime limits without materializing its integer or fraction digits.
   */
  @NonNull
  static BigDecimal validateNumericValue(@NonNull BigDecimal number, @NonNull String description,
                                         @NonNull TranslationRuntimeLimits runtimeLimits) {
    requireNonNull(number);
    requireNonNull(description);
    requireNonNull(runtimeLimits);

    long absoluteScale = Math.abs((long) number.scale());
    if (absoluteScale > runtimeLimits.getMaximumAbsoluteNumberScale())
      throw new IllegalArgumentException(format("%s scale %d exceeds the maximum absolute scale of %d",
          description, number.scale(), runtimeLimits.getMaximumAbsoluteNumberScale()));

    if (number.precision() > runtimeLimits.getMaximumNumberPrecision())
      throw new IllegalArgumentException(format("%s precision %d exceeds the maximum of %d",
          description, number.precision(), runtimeLimits.getMaximumNumberPrecision()));

    return number;
  }

  private static void validateMaterializedPrecision(long materializedPrecision) {
    if (materializedPrecision > MAXIMUM_MATERIALIZED_PRECISION)
      throw new IllegalArgumentException(format("Plural operand materialized precision %d exceeds the maximum of %d",
          materializedPrecision, MAXIMUM_MATERIALIZED_PRECISION));
  }

  /**
   * Creates a builder for CLDR plural operands backed by the given number.
   * <p>
   * Negative numbers are evaluated using their absolute value.
   *
   * @param number the number that drives pluralization, not null
   * @return a plural-operands builder, not null
   */
  @NonNull
  public static Builder forNumber(@NonNull Number number) {
    requireNonNull(number);
    return new Builder(number);
  }

  /**
   * Gets the absolute numeric value used for the CLDR {@code n} operand.
   *
   * @return the absolute numeric value, not null
   */
  @NonNull
  public BigDecimal getNumber() {
    return n;
  }

  /**
   * Gets the compact-decimal exponent used for the CLDR {@code c} and {@code e} operands.
   *
   * @return the compact-decimal exponent, not null
   */
  @NonNull
  public Integer getCompactExponent() {
    return e.intValueExact();
  }

  @NonNull
  BigDecimal n() {
    return n;
  }

  @NonNull
  BigDecimal i() {
    return i;
  }

  @NonNull
  BigDecimal v() {
    return v;
  }

  @NonNull
  BigDecimal w() {
    return w;
  }

  @NonNull
  BigDecimal f() {
    return f;
  }

  @NonNull
  BigDecimal t() {
    return t;
  }

  @NonNull
  BigDecimal c() {
    return c;
  }

	@NonNull
	BigDecimal sourceNumber() {
		return sourceNumber;
	}

	@NonNull
	Optional<@NonNull Integer> explicitVisibleDecimalPlaces() {
		return Optional.ofNullable(explicitVisibleDecimalPlaces);
	}

  @NonNull
  BigDecimal e() {
    return e;
  }

  /**
   * Generates a {@code String} representation of this object.
   *
   * @return a string representation of this object, not null
   */
  @Override
  @NonNull
  public String toString() {
    return format("%s{number=%s, compactExponent=%s}", getClass().getSimpleName(), getNumber(), getCompactExponent());
  }

  /**
   * Checks if this object is equal to another one.
   *
   * @param other the object to check, null returns false
   * @return true if this is equal to the other object, false otherwise
   */
  @Override
  public boolean equals(@Nullable Object other) {
    if (this == other)
      return true;

    if (other == null || !getClass().equals(other.getClass()))
      return false;

    PluralOperands pluralOperands = (PluralOperands) other;

    return Objects.equals(n(), pluralOperands.n())
        && Objects.equals(getCompactExponent(), pluralOperands.getCompactExponent());
  }

  /**
   * A hash code for this object.
   *
   * @return a suitable hash code
   */
  @Override
  public int hashCode() {
    return Objects.hash(n(), getCompactExponent());
  }

  /**
   * Builder for {@link PluralOperands}.
   */
  public static final class Builder {
    @NonNull
    private final Number number;
    @Nullable
    private Integer visibleDecimalPlaces;
    @Nullable
    private Integer compactExponent;
    @NonNull
    private TranslationRuntimeLimits runtimeLimits;

    private Builder(@NonNull Number number) {
      requireNonNull(number);
      this.number = number;
      this.runtimeLimits = TranslationRuntimeLimits.defaults();
    }

    /**
     * Specifies the number of decimal places that will be visible to the user.
     * <p>
     * If omitted, {@link BigDecimal} scale is preserved and other {@link Number} types use their normalized decimal places.
     * Reducing the scale does not round implicitly: callers must supply an already-rounded number, otherwise
     * {@link #build()} throws {@link ArithmeticException}.
     *
     * @param visibleDecimalPlaces the visible decimal places, from zero through
     *                             {@link #MAXIMUM_VISIBLE_DECIMAL_PLACES}, or null to use the number's natural scale
     * @return this builder, not null
     */
    @NonNull
    public Builder visibleDecimalPlaces(@Nullable Integer visibleDecimalPlaces) {
      this.visibleDecimalPlaces = visibleDecimalPlaces;
      return this;
    }

    /**
     * Specifies the compact-decimal exponent used by the CLDR {@code c} and {@code e} operands.
     * <p>
     * For example, a compact display such as {@code 1M} may be evaluated with a compact exponent of {@code 6}.
     *
     * @param compactExponent the compact-decimal exponent, from zero through {@link #MAXIMUM_COMPACT_EXPONENT},
     *                        or null to use zero
     * @return this builder, not null
     */
    @NonNull
    public Builder compactExponent(@Nullable Integer compactExponent) {
      this.compactExponent = compactExponent;
      return this;
    }

    /**
     * Applies safety limits to operand construction.
     *
     * @param runtimeLimits runtime limits, or null to use the library defaults
     * @return this builder, not null
     * @since 3.0.0
     */
    @NonNull
    public Builder runtimeLimits(@Nullable TranslationRuntimeLimits runtimeLimits) {
      this.runtimeLimits = runtimeLimits == null ? TranslationRuntimeLimits.defaults() : runtimeLimits;
      return this;
    }

    /**
     * Builds immutable plural operands.
     *
     * @return immutable plural operands, not null
     * @throws ArithmeticException if the requested visible decimal places would require rounding
     * @throws IllegalArgumentException if the number, visible decimal places, or compact exponent exceeds a supported
     *                                  safety limit
     */
    @NonNull
    public PluralOperands build() {
      int effectiveCompactExponent = compactExponent == null ? 0 : compactExponent;

      if (effectiveCompactExponent < 0)
        throw new IllegalArgumentException(format("Compact exponent must be non-negative, but was %d", effectiveCompactExponent));
      if (effectiveCompactExponent > runtimeLimits.getMaximumCompactExponent())
        throw new IllegalArgumentException(format("Compact exponent %d exceeds the maximum of %d",
            effectiveCompactExponent, runtimeLimits.getMaximumCompactExponent()));

      if (visibleDecimalPlaces != null) {
        if (visibleDecimalPlaces < 0)
          throw new IllegalArgumentException(format("Visible decimal places must be non-negative, but was %d", visibleDecimalPlaces));
        if (visibleDecimalPlaces > runtimeLimits.getMaximumVisibleDecimalPlaces())
          throw new IllegalArgumentException(format("Visible decimal places %d exceeds the maximum of %d",
              visibleDecimalPlaces, runtimeLimits.getMaximumVisibleDecimalPlaces()));
      }

      boolean numberIsBigDecimal = number instanceof BigDecimal;
      BigDecimal numberAsBigDecimal = numberIsBigDecimal ? (BigDecimal) number : NumberUtils.toBigDecimal(number);
      validateNumericValue(numberAsBigDecimal, "Number", runtimeLimits);
      numberAsBigDecimal = numberAsBigDecimal.abs();
      BigDecimal sourceNumber = numberAsBigDecimal;

      int effectiveScale = numberAsBigDecimal.scale();
      long materializedPrecision = numberAsBigDecimal.precision();

      if (visibleDecimalPlaces == null) {
        if (!numberIsBigDecimal) {
          effectiveScale = Math.max(0, numberAsBigDecimal.stripTrailingZeros().scale());
          materializedPrecision += Math.max(0L, (long) effectiveScale - numberAsBigDecimal.scale());
        }
      } else {
        effectiveScale = visibleDecimalPlaces;
        materializedPrecision += Math.max(0L, (long) effectiveScale - numberAsBigDecimal.scale());
      }

      materializedPrecision += Math.max(0L, (long) effectiveCompactExponent - effectiveScale);
      validateMaterializedPrecision(materializedPrecision);

      if (!numberIsBigDecimal || visibleDecimalPlaces != null)
        numberAsBigDecimal = numberAsBigDecimal.setScale(effectiveScale, RoundingMode.UNNECESSARY);

      return new PluralOperands(sourceNumber, numberAsBigDecimal, effectiveCompactExponent, visibleDecimalPlaces);
    }
  }
}
