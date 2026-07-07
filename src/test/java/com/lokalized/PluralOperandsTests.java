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
}
