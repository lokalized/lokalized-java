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

import static com.lokalized.NumberUtils.numberOfDecimalPlaces;
import static org.junit.Assert.assertEquals;

/**
 * Exercises {@link NumberUtils}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class NumberUtilsTests {
  @Test
  public void testNumberOfDecimalPlaces() {
    assertEquals(1, numberOfDecimalPlaces(new BigDecimal("1.0")).intValue());
    assertEquals(0, numberOfDecimalPlaces(new BigDecimal("1")).intValue());
    assertEquals(2, numberOfDecimalPlaces(new BigDecimal("1.50")).intValue());
    assertEquals(1, numberOfDecimalPlaces(1.50).intValue());
    assertEquals(0, numberOfDecimalPlaces(150).intValue());
  }
}