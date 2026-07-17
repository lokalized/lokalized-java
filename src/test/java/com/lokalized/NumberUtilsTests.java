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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link NumberUtils}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class NumberUtilsTests {
  @Test
  public void documentedNumberImplementationsConvertDeterministically() {
    LongAdder longAdder = new LongAdder();
    longAdder.add(18L);
    DoubleAdder doubleAdder = new DoubleAdder();
    doubleAdder.add(19.25);
    LongAccumulator longAccumulator = new LongAccumulator(Long::sum, 0L);
    longAccumulator.accumulate(23L);
    DoubleAccumulator doubleAccumulator = new DoubleAccumulator(Double::sum, 0.0);
    doubleAccumulator.accumulate(21.5);

    assertEquals(new BigDecimal("1.2300"), NumberUtils.toBigDecimal(new BigDecimal("1.2300")));
    assertEquals(new BigDecimal("12345678901234567890"),
        NumberUtils.toBigDecimal(new BigInteger("12345678901234567890")));
    assertEquals(new BigDecimal("-12"), NumberUtils.toBigDecimal(Byte.valueOf((byte) -12)));
    assertEquals(new BigDecimal("13"), NumberUtils.toBigDecimal(Short.valueOf((short) 13)));
    assertEquals(new BigDecimal("14"), NumberUtils.toBigDecimal(Integer.valueOf(14)));
    assertEquals(new BigDecimal("15"), NumberUtils.toBigDecimal(Long.valueOf(15L)));
    assertEquals(new BigDecimal("16.5"), NumberUtils.toBigDecimal(Float.valueOf(16.5F)));
    assertEquals(new BigDecimal("17.75"), NumberUtils.toBigDecimal(Double.valueOf(17.75)));
    assertEquals(new BigDecimal("18"), NumberUtils.toBigDecimal(new AtomicInteger(18)));
    assertEquals(new BigDecimal("19"), NumberUtils.toBigDecimal(new AtomicLong(19L)));
    assertEquals(new BigDecimal("18"), NumberUtils.toBigDecimal(longAdder));
    assertEquals(new BigDecimal("19.25"), NumberUtils.toBigDecimal(doubleAdder));
    assertEquals(new BigDecimal("23"), NumberUtils.toBigDecimal(longAccumulator));
    assertEquals(new BigDecimal("21.5"), NumberUtils.toBigDecimal(doubleAccumulator));
  }

  @Test
  public void nonFiniteFloatingPointValuesAreRejectedClearly() {
    DoubleAdder infiniteAdder = new DoubleAdder();
    infiniteAdder.add(Double.POSITIVE_INFINITY);

    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> NumberUtils.toBigDecimal(Float.NaN)).getMessage().contains("must be finite"));
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> NumberUtils.toBigDecimal(Double.NEGATIVE_INFINITY)).getMessage().contains("must be finite"));
    assertTrue(assertThrows(IllegalArgumentException.class,
        () -> NumberUtils.toBigDecimal(infiniteAdder)).getMessage().contains("must be finite"));
  }

  @Test
  public void unsupportedNumberImplementationsAreRejectedWithoutParsingToString() {
    assertUnsupportedNumber(new InheritedToStringNumber());
    assertUnsupportedNumber(new FractionNumber());
    assertUnsupportedNumber(new ExplodingToStringNumber());
  }

  private void assertUnsupportedNumber(Number number) {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
        () -> NumberUtils.toBigDecimal(number));

    assertFalse(exception instanceof NumberFormatException);
    assertTrue(exception.getMessage().contains("Unsupported Number implementation"));
    assertTrue(exception.getMessage().contains(number.getClass().getName()));
    assertTrue(exception.getMessage().contains("BigDecimal"));
  }

  @Test
  public void numberOfDecimalPlaces() {
    assertEquals(1, NumberUtils.numberOfDecimalPlaces(new BigDecimal("1.0")).intValue());
    assertEquals(0, NumberUtils.numberOfDecimalPlaces(new BigDecimal("1")).intValue());
    assertEquals(2, NumberUtils.numberOfDecimalPlaces(new BigDecimal("1.50")).intValue());
    assertEquals(0, NumberUtils.numberOfDecimalPlaces(1.0).intValue());
    assertEquals(1, NumberUtils.numberOfDecimalPlaces(1.50).intValue());
    assertEquals(0, NumberUtils.numberOfDecimalPlaces(150).intValue());
  }

  @Test
  public void integerComponent() {
    assertEquals(BigInteger.valueOf(0L), NumberUtils.integerComponent(new BigDecimal("0.0")));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.integerComponent(new BigDecimal("0.1")));
    assertEquals(BigInteger.valueOf(1L), NumberUtils.integerComponent(new BigDecimal("1.0")));
    assertEquals(BigInteger.valueOf(-1L), NumberUtils.integerComponent(new BigDecimal("-1")));
    assertEquals(BigInteger.valueOf(-1L), NumberUtils.integerComponent(new BigDecimal("-1.5")));
    assertEquals(BigInteger.valueOf(45L), NumberUtils.integerComponent(new BigDecimal("45")));
    assertEquals(BigInteger.valueOf(45L), NumberUtils.integerComponent(new BigDecimal("45.6")));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.integerComponent(0));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.integerComponent(0.1));
    assertEquals(BigInteger.valueOf(-1L), NumberUtils.integerComponent(-1));
    assertEquals(BigInteger.valueOf(-1L), NumberUtils.integerComponent(-1.5));
    assertEquals(BigInteger.valueOf(45L), NumberUtils.integerComponent(45));
    assertEquals(BigInteger.valueOf(45L), NumberUtils.integerComponent(45.6));
  }

  @Test
  public void fractionalComponent() {
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(new BigDecimal("0.0")));
    assertEquals(BigInteger.valueOf(1L), NumberUtils.fractionalComponent(new BigDecimal("0.1")));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(new BigDecimal("1.0")));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(new BigDecimal("-1")));
    assertEquals(BigInteger.valueOf(5L), NumberUtils.fractionalComponent(new BigDecimal("-1.5")));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(new BigDecimal("45")));
    assertEquals(BigInteger.valueOf(600L), NumberUtils.fractionalComponent(new BigDecimal("45.600")));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(0));
    assertEquals(BigInteger.valueOf(1L), NumberUtils.fractionalComponent(0.1));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(1.0));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(-1));
    assertEquals(BigInteger.valueOf(5L), NumberUtils.fractionalComponent(-1.5));
    assertEquals(BigInteger.valueOf(0L), NumberUtils.fractionalComponent(45));
    assertEquals(BigInteger.valueOf(6L), NumberUtils.fractionalComponent(45.600));
  }

  private static class InheritedToStringNumber extends Number {
    @Override public int intValue() { return 1; }
    @Override public long longValue() { return 1L; }
    @Override public float floatValue() { return 1.0F; }
    @Override public double doubleValue() { return 1.0; }
  }

  private static final class FractionNumber extends InheritedToStringNumber {
    @Override public String toString() { return "1/2"; }
  }

  private static final class ExplodingToStringNumber extends InheritedToStringNumber {
    @Override public String toString() { throw new AssertionError("toString() must not be called"); }
  }
}
