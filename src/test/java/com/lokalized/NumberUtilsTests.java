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

import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.Assert.assertEquals;

/**
 * Exercises {@link NumberUtils}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class NumberUtilsTests {
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
}