# Changelog

All notable changes to Lokalized will be documented in this file.

## 3.0.0-SNAPSHOT

### Breaking Changes

- Replaced the legacy failure handling API with `TranslationFailureHandler`, `TranslationFailure`,
  `TranslationFailureReason`, and `TranslationFailureResponse`.
- Added `TranslationFailureReason.NO_MATCHING_ALTERNATIVE`; exhaustive switches over this public enum must handle the
  new constant.
- Made `DefaultStrings` package-private; applications should construct instances through `Strings.Builder`.
- Added abstract inspection methods to `Strings`; custom `Strings` implementations must now implement
  `getSupportedLocales()`, `getKeysForLocale(Locale)`, and `getMissingKeys(Locale, Locale)`.
- Renamed `Cardinality.getSupportedLanguageCodes()` and `Ordinality.getSupportedLanguageCodes()` to
  `getSupportedLocaleTags()` because the returned values are BCP 47 locale tags, not only language codes.
- Loader validation is stricter and now rejects duplicate nested JSON members, malformed placeholders,
  reserved language-form placeholder names, invalid locale filenames in explicitly loaded filesystem catalog
  directories, invalid alternative expressions, explicit null placeholder modes, blank/BOM-only catalogs, and
  malformed UTF-8 at load time. Invalid locale filenames discovered on the classpath are warnings instead.
- Existing loader overloads now apply per-resource defaults of 16 MiB input, 16 MiB reader characters, and 128 JSON
  nesting levels. Classpath package paths must be nonempty, slash-relative, and free of traversal segments; trailing
  slashes are normalized.
- `PluralOperands.visibleDecimalPlaces(...)` no longer floors discarded digits. Reducing scale now throws
  `ArithmeticException` unless the supplied number is already rounded to that scale.
- Programmatic `LocalizedString` catalogs now receive the same semantic validation as file-backed catalogs when
  `Strings` is built, so invalid expressions, form maps, selectors, metadata, and generated fragments fail earlier.

### Features

- Added per-invocation `TranslationOptions` for locale, language-range, bidi-isolation, and
  translation-failure-handler overrides.
- Added `Strings` inspection APIs: `getSupportedLocales()`, `getKeysForLocale(Locale)`, and
  `getMissingKeys(Locale, Locale)`.
- Added a public `LocalizedStringLoader.loadFromClasspath(ClassLoader, String)` overload and made the
  one-argument classpath loader prefer the thread context classloader when available.
- Added `LocalizedStringWarning`, `LocalizedStringWarningHandler`, and warning-aware loader overloads for
  incomplete CLDR cardinality/ordinality maps and invalid classpath locale filenames. Warning locale, key, and
  placeholder context is optional when a problem applies to a resource as a whole. Warnings log at `WARNING` by
  default; callers can ignore, collect, or promote them to `LocalizedStringLoadingException`.
- Added `LocalizedStringLoadingOptions` for bounded input size, JSON nesting depth, and opt-in exhaustive classpath-root
  searching for JARs that omit directory entries. Ordinary classloader resource discovery remains the safe default.
- Added CLDR 48.2-backed cardinality, ordinality, cardinality-range, locale-alias, likely-subtag,
  parent-locale, and locale-validity behavior generated from pinned Unicode CLDR source data.
- Added `PluralOperands` plus `Cardinality.forOperands(...)` and `Ordinality.forOperands(...)` for
  visible-decimal-place and compact-decimal plural evaluation. `PluralOperands` values are accepted by cardinality,
  ordinality, and numeric alternative expressions as well as generated-placeholder rules and selectors.
- Added bidirectional isolation for caller-supplied placeholder values in resolved right-to-left locales,
  with `BidiIsolation.NONE` available as a global or per-invocation opt-out. The RTL script set is generated
  from pinned CLDR script metadata instead of maintained by hand.
- Added escaped literal mustache support with `\{{...}}`.
- Added a packaged JSON Schema at `schema/lokalized-strings.schema.json`.

### Behavior Changes

- Missing translations and runtime resolution failures are routed through `TranslationFailureHandler`.
  The default handler returns the key with caller-supplied placeholders interpolated.
- Locale matching now uses full CLDR aliases, including compound aliases and context-sensitive
  multi-territory replacements, parent locales, likely subtags, and script-aware matching.
- Exact loaded locale tags win before canonical-equivalent aliases; language-range quality weights and
  `q=0` exclusions are honored.
- Sparse locale files can fall back per key through the locale candidate chain.
- Placeholder and expression identifiers now share the same Unicode letter/digit naming policy.
- Selected generated-placeholder fragments may reference caller values and other generated placeholders recursively;
  cycles, excessive depth, and output above 1,048,576 characters are resolution failures.
- Alternatives-only keys for which no condition matches are reported as
  `TranslationFailureReason.NO_MATCHING_ALTERNATIVE` without a synthetic expression-evaluation cause.
- Failure-key interpolation is capped at 1,048,576 characters; if interpolation would exceed the cap, the raw key is
  returned.
- `Locale.ROOT`, `und`, wildcard-only language ranges, empty preference lists, and unmatched locale
  preferences resolve to the configured fallback locale.
- Language-range matching accepts at most 1,000 preferences per call. If every supported locale is excluded by
  `q=0`, the current non-null matching contract still returns the configured fallback.

### Packaging

- Added `Automatic-Module-Name: com.lokalized` to the JAR manifest.
- Added `THIRD-PARTY-NOTICES.md` and packaged `LICENSE` plus third-party notices under `META-INF`.
- Moved GPG signing behind the `release` Maven profile so normal `mvn verify` runs do not require
  Central Portal or GPG credentials.
- Kept the minimum runtime baseline at Java 9+.
- Pinned the JUnit engine and Surefire provider, made build timestamps reproducible, and now runs all
  non-schema tests on the Java 9 baseline while keeping the Java 17+ schema validator in an activated profile.
- Added exhaustive generated CLDR plural samples, category/range invariants, a generated website JSON
  artifact with an explicit format version, and byte-for-byte generator drift verification.

### Migration Notes

- Replace legacy failure handling configuration with `Strings.Builder.translationFailureHandler(...)`.
  `TranslationFailureHandler.returnKey()` preserves the default soft-fail behavior, while
  `TranslationFailureHandler.throwException()` provides fail-fast behavior.
- Replace direct construction or references to `DefaultStrings` with `Strings.withFallbackLocale(...).build()`.
- Update custom `Strings` implementations with the three new inspection methods.
- Replace `Cardinality.getSupportedLanguageCodes()` and `Ordinality.getSupportedLanguageCodes()` with
  the corresponding `getSupportedLocaleTags()` calls.
- Round values explicitly before reducing `PluralOperands.visibleDecimalPlaces(...)`; implicit flooring has been removed.
- Expect invalid programmatic catalogs to fail during `Strings.build()` under the shared semantic validator.
- Review strings files under the stricter loader validation before release. Duplicate nested JSON members,
  malformed placeholders, whitespace-padded mustaches, reserved language-form placeholder names, invalid
  locale filenames in explicitly loaded filesystem catalog directories, invalid alternative expressions, explicit
  null placeholder modes, blank/BOM-only catalogs, and malformed UTF-8 are rejected while loading. Use `{}` for an
  empty catalog.
- Review catalog sizes and nesting against the new per-resource defaults (16 MiB bytes/characters and depth 128).
  Pass `LocalizedStringLoadingOptions` to lower limits where appropriate; nesting cannot be raised above 128.
- Prefer a namespaced classpath package such as `com/example/myapp/strings`. Enable
  `LocalizedStringLoadingOptions.Builder.exhaustiveClasspathSearch(true)` only when a JAR omits package directory
  entries. Classpath `.json` resources whose filenames are not valid locale tags are warning-and-skip, but remain fatal
  in explicitly loaded filesystem catalog directories.
- Placeholder and alternative-expression identifiers now follow the same rule: start with a Unicode letter
  or underscore, then use Unicode letters, Unicode digits, underscores, or hyphens.
- Expect CLDR-backed plural and locale matching behavior to differ from the older handwritten tables in
  some locales.
- Incomplete CLDR cardinality or ordinality maps now log warnings during loading by default. Supply
  `LocalizedStringWarningHandler.ignore()` to retain silent loading or `throwException()` for build-time strictness.
- Right-to-left locale output may now include Unicode FSI/PDI controls around caller-supplied placeholder
  values. Use `BidiIsolation.NONE` only for sinks that cannot accept bidi controls.
