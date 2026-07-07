# Changelog

All notable changes to Lokalized will be documented in this file.

## 3.0.0-SNAPSHOT

### Breaking Changes

- Replaced the legacy failure handling API with `TranslationFailureHandler`, `TranslationFailure`,
  `TranslationFailureReason`, and `TranslationFailureResponse`.
- Removed public explicit-`Locale` `Strings.get(...)` overloads. Use `TranslationOptions.forLocale(...)`
  or `TranslationOptions.forLanguageRanges(...)` for per-invocation locale overrides.
- Made `DefaultStrings` package-private; applications should construct instances through `Strings.Builder`.
- Loader validation is stricter and now rejects duplicate nested JSON members, malformed placeholders,
  reserved language-form placeholder names, invalid locale filenames, and invalid alternative expressions at load time.

### Features

- Added per-invocation `TranslationOptions` for locale, language-range, bidi-isolation, and
  translation-failure-handler overrides.
- Added `Strings` inspection APIs: `getSupportedLocales()`, `getKeysForLocale(Locale)`, and
  `getMissingKeys(Locale, Locale)`.
- Added a public `LocalizedStringLoader.loadFromClasspath(ClassLoader, String)` overload and made the
  one-argument classpath loader prefer the thread context classloader when available.
- Added CLDR 48.2-backed cardinality, ordinality, cardinality-range, locale-alias, likely-subtag,
  parent-locale, and locale-validity behavior generated from pinned Unicode CLDR source data.
- Added `PluralOperands` plus `Cardinality.forOperands(...)` and `Ordinality.forOperands(...)` for
  visible-decimal-place and compact-decimal plural evaluation.
- Added bidirectional isolation for caller-supplied placeholder values in resolved right-to-left locales,
  with `BidiIsolation.NONE` available as a global or per-invocation opt-out.
- Added escaped literal mustache support with `\{{...}}`.
- Added a packaged JSON Schema at `schema/lokalized-strings.schema.json`.

### Behavior Changes

- Missing translations and runtime resolution failures are routed through `TranslationFailureHandler`.
  The default handler returns the key with caller-supplied placeholders interpolated.
- Locale matching now uses CLDR aliases, parent locales, likely subtags, and script-aware matching.
- Sparse locale files can fall back per key through the locale candidate chain.
- Placeholder and expression identifiers now share the same Unicode letter/digit naming policy.
- `Locale.ROOT`, `und`, wildcard-only language ranges, empty preference lists, and unmatched locale
  preferences resolve to the configured fallback locale.

### Packaging

- Added `Automatic-Module-Name: com.lokalized` to the JAR manifest.
- Added `THIRD-PARTY-NOTICES.md` and packaged `LICENSE` plus third-party notices under `META-INF`.
- Moved GPG signing behind the `release` Maven profile so normal `mvn verify` runs do not require
  Central Portal or GPG credentials.
- Kept the minimum runtime baseline at Java 9+.

### Migration Notes

- Replace legacy failure handling configuration with `Strings.Builder.translationFailureHandler(...)`.
  `TranslationFailureHandler.returnKey()` preserves the default soft-fail behavior, while
  `TranslationFailureHandler.throwException()` provides fail-fast behavior.
- Replace `strings.get(key, locale)` with `strings.get(key, TranslationOptions.forLocale(locale))`.
- Replace `strings.get(key, placeholders, locale)` with
  `strings.get(key, placeholders, TranslationOptions.forLocale(locale))`.
- Replace direct construction or references to `DefaultStrings` with `Strings.withFallbackLocale(...).build()`.
- Review strings files under the stricter loader validation before release. Duplicate nested JSON members,
  malformed placeholders, whitespace-padded mustaches, reserved language-form placeholder names, invalid
  locale filenames, and invalid alternative expressions are rejected while loading.
- Placeholder and alternative-expression identifiers now follow the same rule: start with a Unicode letter
  or underscore, then use Unicode letters, Unicode digits, underscores, or hyphens.
- Expect CLDR-backed plural and locale matching behavior to differ from the older handwritten tables in
  some locales.
- Right-to-left locale output may now include Unicode FSI/PDI controls around caller-supplied placeholder
  values. Use `BidiIsolation.NONE` only for sinks that cannot accept bidi controls.
