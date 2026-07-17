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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
/**
 * Exercises {@link StringInterpolator}.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class StringInterpolatorTests {
  @Test
  public void placeholderNamesWithUnderscoreAndHyphen() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate("Hi {{user_name}} and {{user-name}}", Map.of(
        "user_name", "Ada",
        "user-name", "Bob"
    ));

    assertEquals("Hi Ada and Bob", result);
  }

  @Test
  public void unicodePlaceholderNames() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate(
        "Bonjour {{caféCount}} et {{количество2}} et {{नाम}} et {{a\u20DD\u216B\u00B2}}", Map.of(
        "caféCount", 1,
        "количество2", 2,
        "नाम", 3,
        "a\u20DD\u216B\u00B2", 4
    ));

    assertEquals("Bonjour 1 et 2 et 3 et 4", result);
  }

  @Test
  public void replacementValuesAreSafelyEscaped() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate("Total: {{amount}}", Map.of(
        "amount", "$24.99"
    ));

    assertEquals("Total: $24.99", result);
  }

  @Test
  public void lenientInterpolationCanBoundOutput() {
    StringInterpolator interpolator = new StringInterpolator();

    assertEquals("Hi Ada", interpolator.interpolate("Hi {{name}}", Map.of("name", "Ada"), 6));
    assertThrows(IllegalStateException.class,
        () -> interpolator.interpolate("Hi {{name}}", Map.of("name", "Ada"), 5));
  }

  @Test
  public void boundedInterpolationChecksCharSequenceLengthBeforeMaterialization() {
    StringInterpolator interpolator = new StringInterpolator();
    CharSequence oversized = new UnmaterializableCharSequence(1_000_000);

    IllegalStateException exception = assertThrows(IllegalStateException.class,
        () -> interpolator.interpolate("{{value}}", Map.of("value", oversized), 4));
    assertEquals("Interpolated output exceeds the maximum of 4 characters", exception.getMessage());
  }

  @Test
  public void escapedPlaceholdersAreRenderedLiterally() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate("Hi \\{{name}} and {{name}}", Map.of(
        "name", "Ada"
    ));

    assertEquals("Hi {{name}} and Ada", result);
  }

  @Test
  public void escapedClosingDelimitersAreRenderedLiterally() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate("Literal \\}} and {{name}}", Map.of(
        "name", "Ada"
    ));

    assertEquals("Literal }} and Ada", result);
  }

  @Test
  public void escapedBackslashCanPrecedeLivePlaceholder() {
    StringInterpolator interpolator = new StringInterpolator();
    String result = interpolator.interpolate("Literal \\\\{{name}}", Map.of(
        "name", "Ada"
    ));

    assertEquals("Literal \\Ada", result);
  }

  @Test
  public void strictInterpolationReportsUnresolvedPlaceholders() {
    StringInterpolator interpolator = new StringInterpolator();
    StringInterpolator.InterpolationResult result = interpolator.interpolateStrictly("Hi {{name}} from {{city}}", Map.of(
        "name", "Ada"
    ));

    assertEquals("Hi Ada from {{city}}", result.getValue());
    assertEquals(Set.of("city"), result.getUnresolvedPlaceholderNames());
  }

  @Test
  public void strictInterpolationDoesNotTreatReplacementMustachesAsUnresolved() {
    StringInterpolator interpolator = new StringInterpolator();
    StringInterpolator.InterpolationResult result = interpolator.interpolateStrictly("Hi {{name}}", Map.of(
        "name", "{{Ada}}"
    ));

    assertEquals("Hi {{Ada}}", result.getValue());
    assertEquals(Set.of(), result.getUnresolvedPlaceholderNames());
  }

  @Test
  public void strictInterpolationIgnoresEscapedPlaceholders() {
    StringInterpolator interpolator = new StringInterpolator();
    StringInterpolator.InterpolationResult result = interpolator.interpolateStrictly("Hi \\{{name}} and {{name}}", Map.of(
        "name", "Ada"
    ));

    assertEquals("Hi {{name}} and Ada", result.getValue());
    assertEquals(Set.of(), result.getUnresolvedPlaceholderNames());
  }

  @Test
  public void placeholderNameExtractionIgnoresEscapedPlaceholders() {
    assertEquals(Set.of("real"), StringInterpolator.placeholderNamesIn("\\{{ ignored }} and {{real}}"));
  }

  @Test
  public void placeholderNameExtractionHonorsEscapedBackslashBeforeLivePlaceholder() {
    assertEquals(Set.of("real"), StringInterpolator.placeholderNamesIn("\\\\{{real}} and \\{{ignored}}"));
  }

  @Test
  public void lenientPlaceholderNameExtractionIgnoresMalformedSyntax() {
    assertEquals(Set.of("real"), StringInterpolator.placeholderNamesInLeniently("bad}} {{invalid name}} {{real}}"));
  }

  @Test
  public void strictInterpolationRejectsMalformedPlaceholders() {
    StringInterpolator interpolator = new StringInterpolator();

    assertThrows(IllegalArgumentException.class,
        () -> interpolator.interpolateStrictly("Hi {{ name }}", Map.of()),
        "Expected placeholder names with spaces to be rejected");
  }

  private static final class UnmaterializableCharSequence implements CharSequence {
    private final int length;

    private UnmaterializableCharSequence(int length) {
      this.length = length;
    }

    @Override
    public int length() {
      return length;
    }

    @Override
    public char charAt(int index) {
      throw new AssertionError("Oversized input must be rejected before scanning");
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      throw new AssertionError("Oversized input must be rejected before slicing");
    }

    @Override
    public String toString() {
      throw new AssertionError("Oversized input must be rejected before materialization");
    }
  }
}
