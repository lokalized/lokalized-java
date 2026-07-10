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

import org.junit.jupiter.api.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises {@link PluralOperands}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class PluralOperandsTests {
  @Test
  public void bigDecimalScaleIsPreserved() {
    PluralOperands operands = PluralOperands.forNumber(new BigDecimal("-1.50")).build();

    assertEquals(new BigDecimal("1.50"), operands.getNumber());
    assertEquals(new BigDecimal("1"), operands.i());
    assertEquals(new BigDecimal("2"), operands.v());
    assertEquals(new BigDecimal("1"), operands.w());
    assertEquals(new BigDecimal("50"), operands.f());
    assertEquals(new BigDecimal("5"), operands.t());
  }

  @Test
  public void visibleDecimalPlacesAreApplied() {
    PluralOperands operands = PluralOperands.forNumber(1).visibleDecimalPlaces(2).build();

    assertEquals(new BigDecimal("1.00"), operands.getNumber());
    assertEquals(new BigDecimal("2"), operands.v());
    assertEquals(new BigDecimal("0"), operands.w());
    assertEquals(new BigDecimal("0"), operands.f());
    assertEquals(new BigDecimal("0"), operands.t());
  }

  @Test
  public void visibleDecimalPlacesDoNotRoundImplicitly() {
    assertThrows(ArithmeticException.class,
        () -> PluralOperands.forNumber(new BigDecimal("1.15")).visibleDecimalPlaces(1).build());

    assertEquals(new BigDecimal("1.2"),
        PluralOperands.forNumber(new BigDecimal("1.2")).visibleDecimalPlaces(1).build().getNumber());
  }

  @Test
  public void compactExponentSetsCAndEOperands() {
    PluralOperands operands = PluralOperands.forNumber(1).compactExponent(6).build();

    assertEquals(6, operands.getCompactExponent().intValue());
    assertEquals(new BigDecimal("6"), operands.c());
    assertEquals(new BigDecimal("6"), operands.e());
  }

  @Test
  public void compactExponentShiftsNumberOperands() {
    PluralOperands operands = PluralOperands.forNumber(new BigDecimal("1.2")).compactExponent(6).build();

    assertEquals(new BigDecimal("1200000"), operands.getNumber());
    assertEquals(new BigDecimal("1200000"), operands.i());
    assertEquals(new BigDecimal("0"), operands.v());
    assertEquals(new BigDecimal("0"), operands.w());
    assertEquals(new BigDecimal("0"), operands.f());
    assertEquals(new BigDecimal("0"), operands.t());
    assertEquals(new BigDecimal("6"), operands.c());
    assertEquals(new BigDecimal("6"), operands.e());
  }

  @Test
  public void equalityIncludesNumberScaleAndCompactExponent() {
    PluralOperands onePointZero = PluralOperands.forNumber(new BigDecimal("1.0")).build();
    PluralOperands onePointZeroAgain = PluralOperands.forNumber(new BigDecimal("1.0")).build();
    PluralOperands onePointZeroZero = PluralOperands.forNumber(new BigDecimal("1.00")).build();
    PluralOperands compactMillion = PluralOperands.forNumber(new BigDecimal("1.0")).compactExponent(6).build();

    assertEquals(onePointZero, onePointZeroAgain);
    assertEquals(onePointZero.hashCode(), onePointZeroAgain.hashCode());
    assertNotEquals(onePointZero, onePointZeroZero);
    assertNotEquals(onePointZero, compactMillion);
  }

  @Test
  public void invalidVisibleDecimalPlacesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(1).visibleDecimalPlaces(-1).build());
  }

  @Test
  public void invalidCompactExponentIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(1).compactExponent(-1).build());
  }

  @Test
  public void excessiveVisibleDecimalPlacesAreRejectedBeforeScaleExpansion() {
    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(1)
        .visibleDecimalPlaces(PluralOperands.MAXIMUM_VISIBLE_DECIMAL_PLACES + 1)
        .build());
  }

  @Test
  public void excessiveCompactExponentIsRejectedBeforeDecimalShifting() {
    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(1)
        .compactExponent(PluralOperands.MAXIMUM_COMPACT_EXPONENT + 1)
        .build());
  }

  @Test
  public void excessiveNumberScaleIsRejectedBeforeOperandMaterialization() {
    BigDecimal excessivePositiveScale = new BigDecimal(BigInteger.ONE,
        PluralOperands.MAXIMUM_ABSOLUTE_NUMBER_SCALE + 1);
    BigDecimal excessiveNegativeScale = new BigDecimal(BigInteger.ONE,
        -PluralOperands.MAXIMUM_ABSOLUTE_NUMBER_SCALE - 1);
    BigDecimal minimumIntegerScale = new BigDecimal(BigInteger.ONE, Integer.MIN_VALUE);

    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(excessivePositiveScale).build());
    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(excessiveNegativeScale).build());
    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(minimumIntegerScale).build());
  }

  @Test
  public void excessiveNumberPrecisionIsRejected() {
    BigDecimal excessivePrecision = new BigDecimal(BigInteger.TEN.pow(PluralOperands.MAXIMUM_NUMBER_PRECISION));

    assertEquals(PluralOperands.MAXIMUM_NUMBER_PRECISION + 1, excessivePrecision.precision());
    assertThrows(IllegalArgumentException.class, () -> PluralOperands.forNumber(excessivePrecision).build());
  }

  @Test
  public void safetyLimitsAcceptBoundaryValues() {
    BigDecimal maximumPrecision = new BigDecimal(
        BigInteger.TEN.pow(PluralOperands.MAXIMUM_NUMBER_PRECISION).subtract(BigInteger.ONE));
    PluralOperands operands = PluralOperands.forNumber(maximumPrecision)
        .compactExponent(PluralOperands.MAXIMUM_COMPACT_EXPONENT)
        .build();

    assertEquals(PluralOperands.MAXIMUM_NUMBER_PRECISION + PluralOperands.MAXIMUM_COMPACT_EXPONENT,
        operands.getNumber().precision());
    assertEquals(PluralOperands.MAXIMUM_COMPACT_EXPONENT, operands.getCompactExponent());
  }
}
