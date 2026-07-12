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
- Existing loader overloads now apply per-resource defaults of 8 MiB input, 8,388,608 reader UTF-16 code units, and 64
  JSON nesting levels, plus load-wide defaults of 32 MiB input, 256 catalogs, 100,000 localized-string nodes, and 1,000
  warnings. Translation and warning limits now also apply to single-resource `parse(...)` calls, and the translation
  count includes nested alternatives. Classpath package paths must be nonempty, slash-relative, and free of traversal
  segments; trailing slashes are normalized.
- Locale matching now accepts at most 32 parsed language ranges per call. The count includes equivalent ranges added by
  `Locale.LanguageRange.parse(...)`, not only the comma-separated values in the source header.
- Runtime safety defaults are lower: 1,024 for numeric precision, absolute scale, and visible decimal places; 64 for
  compact exponents; 2,048 characters / 256 tokens / 32 nested groups for expressions; 32 generated-placeholder
  levels; 262,144 UTF-16 code units of interpolated output; and 1,048,576 code units of cumulative generated expansion.
  The previous values remain hard ceilings and can be selected explicitly with `TranslationRuntimeLimits`.
- `PluralOperands.visibleDecimalPlaces(...)` no longer floors discarded digits. Reducing scale now throws
  `ArithmeticException` unless the supplied number is already rounded to that scale.
- Programmatic `LocalizedString` catalogs now receive the same semantic validation as file-backed catalogs when
  `Strings` is built, so invalid expressions, form maps, and generated fragments fail earlier.
- Removed the unused selector-driven placeholder format (`selectors`, rule-array `translations`, and `when`) and its
  public `LanguageFormType`, `LanguageFormSelector`, and `LanguageFormTranslationRule` APIs. Use ordered
  `alternatives` for catalog-owned multi-axis decisions, or select a purpose-specific translation key in application
  code.
- Removed the unused `placeholderMetadata` catalog field and `LocalizedString.PlaceholderMetadata` API. Put concise
  translator guidance in message-level `commentary` or keep richer placeholder contracts in external translation
  tooling.
- `Range<T>` is now a final immutable `Iterable<T>` instead of a `Collection<T>`. Its mutation methods, collection
  facade, and `getInfinite()` were removed; use `getValues()` and boxed `isInfinite()`. Factories now reject null arrays
  and null elements instead of treating a null array as empty or retaining null values.
- `LocalizedString` and its immutable nested value types are final, and `LocalizedString` construction is builder-only;
  runtime catalog immutability can no longer be invalidated by subclasses with mutable overridden getters.
- `LanguageForm` is explicitly closed to the Lokalized-provided enum types. External implementations were never
  resolvable by the translation runtime and are now documented as unsupported.
- Every object in an `alternatives` array must contain exactly one expression. Nested alternatives now halt at the first
  matching branch even when its nested subtree does not produce a translation.
- The default locale-fallback policy no longer hides runtime resolution failures by trying later locales. It continues
  for missing translations and unmatched alternatives; use `TranslationFallbackPolicy.fallbackOnAnyFailure()` to
  preserve the legacy behavior.
- `MissingTranslationException.getLocale()` was replaced by `getLookupLocale()` and optional
  `getLocaleMatchResult()` diagnostics.
- Added diagnostic methods to `Strings` and `LocaleMatcher`; custom implementations must implement the full
  `getResult(String, Map, TranslationOptions)` method and strict `matchFor(List<LanguageRange>)` method. Convenience
  overloads are default methods.

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
  ordinality, and numeric alternative expressions as well as generated-placeholder rules.
- Added bidirectional isolation for caller-supplied placeholder values in resolved right-to-left locales,
  with `BidiIsolation.NONE` available as a global or per-invocation opt-out. The RTL script set is generated
  from pinned CLDR script metadata instead of maintained by hand.
- Added escaped literal mustache support with `\{{...}}`.
- Added a packaged JSON Schema at `schema/lokalized-strings.schema.json`.
- Added `TranslationFallbackPolicy` so candidate-locale fallback is configured independently from final
  `TranslationFailureHandler` behavior. Global and per-invocation policies are supported.
- Added opt-in `TranslationResult` diagnostics with lookup/resolved locales, locales actually attempted, fallback
  status, final outcome, failure reason, runtime cause, and optional locale-negotiation result.
- Added strict `LocaleMatchResult` diagnostics with all requested ranges, selected locale, winning preference range,
  effective quality, match kind, considered locales, and an explicit unmatched state.
- Added `Strings.Builder.localeMatchSupplier(...)` so request-scoped negotiation can retain original language ranges
  and strict match diagnostics; `localeSupplier(...)` remains available when only a selected locale is needed.
- Added immutable `TranslationRuntimeLimits` for configuring numeric, expression, generated-placeholder, and
  interpolation safety limits within documented hard ceilings, with direct support in `PluralOperands`.
- Added aggregate catalog limits and explicit locale-to-classpath-resource loading for containers and custom
  classloaders that can open resources but cannot enumerate standard `file:` or `jar:` package URLs.

### Behavior Changes

- Missing translations and runtime resolution failures are routed through `TranslationFailureHandler`.
  The default handler returns the key with caller-supplied placeholders interpolated.
- Locale matching now uses full CLDR aliases, including compound aliases and context-sensitive
  multi-territory replacements, parent locales, likely subtags, and script-aware matching.
- Plural-rule lookup now canonicalizes the complete locale tag before selecting cardinality, ordinality, and range
  data, so compound language aliases such as `aa-Saaho` → `ssy` no longer fail or fall through to the wrong language.
- Exact loaded locale tags win before canonical-equivalent aliases; language-range quality weights and
  `q=0` exclusions are honored. Exact private-use tags such as `x-acme` can be selected consistently through locale
  and language-range options, while multiple private-use or undetermined catalogs no longer require meaningless
  primary-language tiebreakers.
- Locale fallback chains are deduplicated after conversion to `Locale`, avoiding repeated attempts when RFC 4647
  truncation leaves a trailing extension singleton that Java discards.
- Fallback after a resolution failure preserves the first application exception without mutating it with suppressed
  failures from later locale attempts.
- Sparse locale files can fall back per key through the locale candidate chain.
- Placeholder and expression identifiers now share the same Unicode letter/digit naming policy.
- Selected generated-placeholder fragments may reference caller values and other generated placeholders recursively;
  cycles, excessive depth, and output above the configured limit are resolution failures. The default output limit is
  262,144 UTF-16 code units, with a 1,048,576-code-unit hard ceiling.
- Alternatives-only keys for which no condition matches are reported as
  `TranslationFailureReason.NO_MATCHING_ALTERNATIVE` without a synthetic expression-evaluation cause.
- Failure-key interpolation uses the configured output limit, 262,144 UTF-16 code units by default; if interpolation
  would exceed the cap, the raw key is returned.
- `Locale.ROOT`, `und`, wildcard-only language ranges, empty preference lists, and unmatched locale
  preferences resolve to the configured fallback locale.
- Language-range matching accepts at most 32 parsed preferences per call. `Locale.LanguageRange.parse(...)` may expand
  one source range into multiple IANA-equivalent ranges, all of which count toward the limit. If every supported locale
  is excluded by `q=0`, `bestMatchFor(...)` returns the configured fallback while strict `matchFor(...)` reports no
  acceptable locale.
- Language-range matching no longer crosses known likely-script boundaries and applies `q=0` exclusions to canonical
  alias descendants, such as `sh;q=0` excluding `sr-Latn-RS`.
- Generated placeholder expansion now has a cumulative work/output budget in addition to per-fragment and final-output
  limits, preventing many individually legal cached fragments from exhausting the heap. The default cumulative budget
  is 1,048,576 UTF-16 code units, with an 8,388,608-code-unit hard ceiling.
- Numeric literals and plural operands are validated before materialization, so compact exponents and decimal scales
  cannot create unbounded work.
- Canonical-alias lookup records and evaluates against the actual loaded catalog locale, deduplicates equivalent
  catalog attempts, and keeps inspection APIs strict to exact members of `getSupportedLocales()`.
- Classpath loading honors runtime-selected multi-release JAR entries, expands filesystem manifest `Class-Path` roots
  during opt-in exhaustive discovery, rejects Windows drive-relative package escapes, and accepts `und` catalogs.

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
- Replace `Range` collection operations with `range.getValues()` and replace `getInfinite()` with boxed
  `isInfinite()`.
- If an application intentionally relied on fallback after a corrupt translation failed to resolve, configure
  `TranslationFallbackPolicy.fallbackOnAnyFailure()` explicitly. Missing translations and unmatched alternatives still
  fall back by default.
- Custom `Strings` and `LocaleMatcher` implementations must implement the new full diagnostic-result and strict
  language-range-match methods; convenience overloads delegate to those core methods.
- Replace `MissingTranslationException.getLocale()` with `getLookupLocale()`. Inspect `getLocaleMatchResult()` when the
  original negotiation outcome matters.
- Replace selector-driven placeholders with ordered `alternatives`, placing the most specific expression first, or
  have application code choose a purpose-specific translation key. Selector-driven catalogs from 2.1 are rejected by
  the 3.0 loader.
- Move useful `placeholderMetadata` notes into message-level `commentary` or external translation documentation.
  Catalogs containing `placeholderMetadata` are rejected by the 3.0 loader.
- Use `localeMatchSupplier(matcher -> matcher.matchFor(ranges))` instead of collapsing a request through
  `localeSupplier(matcher -> matcher.bestMatchFor(ranges))` when `TranslationResult` and failure diagnostics must retain
  the original language ranges.
- Round values explicitly before reducing `PluralOperands.visibleDecimalPlaces(...)`; implicit flooring has been removed.
- Expect invalid programmatic catalogs to fail during `Strings.build()` under the shared semantic validator.
- Review strings files under the stricter loader validation before release. Duplicate nested JSON members,
  malformed placeholders, whitespace-padded mustaches, reserved language-form placeholder names, invalid
  locale filenames in explicitly loaded filesystem catalog directories, invalid alternative expressions, explicit
  null placeholder modes, blank/BOM-only catalogs, and malformed UTF-8 are rejected while loading. Use `{}` for an
  empty catalog.
- Review catalog sizes and nesting against the new per-resource defaults (8 MiB input, 8,388,608 reader UTF-16 code
  units, and depth 64) and load-wide defaults (32 MiB input and 256 catalogs). The 100,000-translation budget counts
  top-level messages and nested alternatives, and it and the 1,000-warning budget also apply to single-resource `parse(...)`
  calls. A configured total-input budget also applies to single-resource `Path` and `InputStream` parsing. Pass
  `LocalizedStringLoadingOptions` to select different limits; nesting cannot be raised above 128.
- Bound the raw `Accept-Language` header size before calling `Locale.LanguageRange.parse(...)`, then ensure the parsed
  list contains at most 32 ranges. Parsing may add equivalent ranges, and Lokalized cannot bound work that occurs before
  it receives the parsed list.
- Review any application that relied on the previous runtime defaults. Use `TranslationRuntimeLimits` to opt back up
  where necessary; the previous numeric, expression, generated-placeholder, interpolation, and expansion values remain
  hard ceilings.
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
