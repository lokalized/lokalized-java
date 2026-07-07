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

  private PluralOperands(@NonNull BigDecimal number, @NonNull Integer compactExponent) {
    requireNonNull(number);
    requireNonNull(compactExponent);

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

    private Builder(@NonNull Number number) {
      requireNonNull(number);
      this.number = number;
    }

    /**
     * Specifies the number of decimal places that will be visible to the user.
     * <p>
     * If omitted, {@link BigDecimal} scale is preserved and other {@link Number} types use their normalized decimal places.
     *
     * @param visibleDecimalPlaces the visible decimal places, may be null to use the number's natural scale
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
     * @param compactExponent the compact-decimal exponent, may be null to use zero
     * @return this builder, not null
     */
    @NonNull
    public Builder compactExponent(@Nullable Integer compactExponent) {
      this.compactExponent = compactExponent;
      return this;
    }

    /**
     * Builds immutable plural operands.
     *
     * @return immutable plural operands, not null
     */
    @NonNull
    public PluralOperands build() {
      boolean numberIsBigDecimal = number instanceof BigDecimal;
      BigDecimal numberAsBigDecimal = numberIsBigDecimal ? (BigDecimal) number : NumberUtils.toBigDecimal(number);
      numberAsBigDecimal = numberAsBigDecimal.abs();

      if (visibleDecimalPlaces == null) {
        if (!numberIsBigDecimal)
          numberAsBigDecimal = numberAsBigDecimal.setScale(NumberUtils.numberOfDecimalPlaces(number), RoundingMode.FLOOR);
      } else {
        if (visibleDecimalPlaces < 0)
          throw new IllegalArgumentException(format("Visible decimal places must be non-negative, but was %d", visibleDecimalPlaces));

        numberAsBigDecimal = numberAsBigDecimal.setScale(visibleDecimalPlaces, RoundingMode.FLOOR);
      }

      Integer effectiveCompactExponent = compactExponent == null ? 0 : compactExponent;

      if (effectiveCompactExponent < 0)
        throw new IllegalArgumentException(format("Compact exponent must be non-negative, but was %d", effectiveCompactExponent));

      return new PluralOperands(numberAsBigDecimal, effectiveCompactExponent);
    }
  }
}
