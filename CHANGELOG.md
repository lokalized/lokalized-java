# Changelog

All notable changes to Lokalized will be documented in this file.

## 3.0.0 - 2026-07-27

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
- Replaced `LocalizedString.getLanguageFormTranslationsByPlaceholder()` and
  `LocalizedString.Builder.languageFormTranslationsByPlaceholder(...)` with the unified
  `getPlaceholderDefinitions()` and `placeholderDefinitions(...)` API. Inspection code now receives
  `Map<String, PlaceholderDefinition>` and must distinguish `LanguageFormTranslation` from
  `ExpressionTranslation`.
- Whole-message alternatives now inherit generated-placeholder definitions from every node on the selected path. A
  nearer child replaces a complete same-named ancestor definition, and selected ancestor definitions take precedence
  over same-named caller values during output interpolation.
- Loader validation is stricter and now rejects duplicate nested JSON members, malformed placeholders,
  reserved language-form placeholder names, invalid locale filenames in explicitly loaded filesystem localized strings
  directories, invalid alternative expressions, explicit null placeholder modes, blank/BOM-only localized strings
  files, and malformed UTF-8 at load time. Invalid locale filenames discovered on the classpath are warnings instead.
- Existing loader overloads now apply per-resource defaults of 8 MiB input, 8,388,608 reader UTF-16 code units, and 64
  JSON nesting levels, plus load-wide defaults of 32 MiB input, 256 localized strings files, 100,000 translation nodes,
  and 1,000 warnings. Translation-node and warning limits now also apply to single-resource `parse(...)` calls. The node count
  includes roots, whole-message alternatives, every placeholder definition, and every expression-fragment
  alternative. Classpath package paths must be nonempty, slash-relative, and free of traversal segments; trailing
  slashes are normalized. The loading-options APIs are named `maximumLocalizedStringsFiles(...)`/
  `getMaximumLocalizedStringsFiles()` and `maximumTranslationNodes(...)`/`getMaximumTranslationNodes()` accordingly.
- Locale matching now accepts at most 32 parsed language ranges per call. The count includes equivalent ranges added by
  `Locale.LanguageRange.parse(...)`, not only the comma-separated values in the source header.
- Runtime safety defaults are lower: 1,024 for numeric precision, absolute scale, and visible decimal places; 64 for
  compact exponents; 2,048 characters / 256 tokens / 32 nested groups for expressions; 32 generated-placeholder
  levels; 262,144 UTF-16 code units of interpolated output; and 1,048,576 code units of cumulative generated expansion.
  The previous values remain hard ceilings and can be selected explicitly with `TranslationRuntimeLimits`.
- `PluralOperands.visibleDecimalPlaces(...)` no longer floors discarded digits. Reducing scale now throws
  `ArithmeticException` unless the supplied number is already rounded to that scale.
- Programmatic sets of `LocalizedString` values now receive the same semantic validation as file-backed localized
  strings when `Strings` is built, so invalid expressions, form maps, and generated fragments fail earlier.
- Programmatic localized-string iterables now reject every duplicate translation key, including structurally equal
  roots that the previous defensive `Set` conversion silently collapsed.
- Programmatic locale inputs used for configuration, matching, loading, diagnostics, and plural-rule queries must now
  be well-formed IETF BCP 47 locales. Localized-string maps also reject distinct `Locale` keys that render to the same
  language tag, such as `Locale.ROOT` and `new Locale("und")`.
- Tiebreaker-map keys must be primary language codes, and keys that canonicalize to the same language, such as `he` and
  `iw`, are rejected instead of silently overwriting each other. Each tiebreaker list must contain every loaded locale
  for that language exactly once and no locale from another language.
- Removed the unused selector-driven placeholder format (`selectors`, rule-array `translations`, and `when`) and its
  public `LanguageFormType`, `LanguageFormSelector`, and `LanguageFormTranslationRule` APIs. Use ordered
  `alternatives` for multi-axis decisions owned by the localized strings file, or select a purpose-specific translation
  key in application code.
- Removed the unused `placeholderMetadata` localized strings file field and `LocalizedString.PlaceholderMetadata` API.
  Put concise translator guidance in message-level `commentary` or keep richer placeholder contracts in external
  translation tooling.
- `Range<T>` is now a final structurally immutable `Iterable<T>` instead of a `Collection<T>`. Its mutation methods,
  collection facade, and `getInfinite()` were removed; use `getValues()` and boxed `isInfinite()`. Factories now reject
  null arrays and null elements instead of treating a null array as empty or retaining null values.
- `LocalizedString` and its concrete immutable nested value types are final, and `LocalizedString` construction is
  builder-only; the immutability of localized strings at runtime can no longer be invalidated by subclasses with mutable
  overridden getters. The abstract `PlaceholderDefinition` base is closed by a private constructor.
- `LanguageForm` is explicitly closed to the Lokalized-provided enum types. External implementations were never
  resolvable by the translation runtime and are now documented as unsupported.
- Every object in an `alternatives` array must contain exactly one expression. Nested alternatives now halt at the first
  matching branch even when its nested subtree does not produce a translation.
- Exact `==`/`!=` predicates between a `CharSequence` operand and a numeric value now reject the nonnumeric operand
  before phonetic resolution. With fail-fast `Strings`, this may now surface `ExpressionEvaluationException` rather
  than the default `PhoneticResolver`'s `IllegalStateException`; ordered numeric comparisons already surfaced
  `ExpressionEvaluationException`. Pass a `Number` or numeric `PluralOperands` for numeric predicates.
- Exact `==`/`!=` predicates between two caller-supplied raw `CharSequence` values are rejected because the expression
  language has no textual equality. Compare phonetic input with a `PHONETIC_*` constant or explicit `Phonetic` value.
- Unknown `Number` implementations are no longer parsed through their unconstrained `toString()` representations.
  Numeric APIs accept the documented JDK boxed, big-number, atomic, adder, and accumulator types; convert custom
  numeric types to `BigDecimal` explicitly.
- The default locale-fallback policy no longer hides runtime resolution failures by trying later locales. It continues
  for missing translations and unmatched alternatives; use `TranslationFallbackPolicy.fallbackOnAnyFailure()` to
  preserve the legacy behavior.
- Removed logging-specific `LocalizedStringWarningHandler.log(...)` and
  `TranslationFailureHandler.logAndReturnKey(...)` factories. Lokalized does not own or configure a logging backend;
  applications receive structured warning and failure callbacks instead. `TranslationFailureHandler.returnKey(...)`
  accepts a `Consumer<? super TranslationFailure>` for observing failures while preserving fail-soft behavior.
- `MissingTranslationException.getLocale()` was replaced by `getLookupLocale()` and optional
  `getLocaleMatchResult()` diagnostics.
- Added diagnostic methods to `Strings` and `LocaleMatcher`; custom implementations must implement the full
  `getResult(String, Map, TranslationOptions)` method and strict `matchFor(List<LanguageRange>)` method. Convenience
  overloads are default methods.

### Features

- Added [`LocaleMatcher.bestMatchForAcceptLanguage(...)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchForAcceptLanguage(java.lang.String))
  for bounded, fail-soft parsing and matching of raw `Accept-Language` values without truncating parsed preferences.
  It accepts RFC 9110 horizontal-tab optional whitespace and empty list elements, and safely falls back for malformed
  inputs across supported JDKs.
- Added per-invocation `TranslationOptions` for locale, language-range, bidi-isolation, and
  translation-failure-handler overrides.
- Added `Strings` inspection APIs: `getSupportedLocales()`, `getKeysForLocale(Locale)`, and
  `getMissingKeys(Locale, Locale)`.
- `LocaleMatchResult` and `MissingTranslationException` now have supported Java serialized forms.
  `Locale.LanguageRange` values are preserved as range/weight pairs; serializing a
  `MissingTranslationException` requires each non-null placeholder value and its reachable object graph to be
  serializable. This establishes a new 3.0 format that is not compatible with 2.x streams.
- Added a public `LocalizedStringLoader.loadFromClasspath(ClassLoader, String)` overload and made the
  one-argument classpath loader prefer the thread context classloader when available.
- Added `LocalizedStringWarning`, `LocalizedStringWarningHandler`, and warning-aware loader overloads for
  incomplete CLDR cardinality/ordinality maps and invalid classpath locale filenames. Warning locale, key, and
  placeholder context is optional when a problem applies to a resource as a whole. Warnings are silently ignored by
  default; callers can collect, forward, or promote them to `LocalizedStringLoadingException`.
- Added a redacted `TranslationFailure.getMessage()` containing the key, lookup locale, reason, and attempted locales.
  It omits caller placeholder values and runtime-cause messages; the cause remains separately available via
  `getCause()`.
- Added [`LocalizedStringLoadingOptions`](https://javadoc.lokalized.com/com/lokalized/LocalizedStringLoadingOptions.html) for bounded input size, JSON nesting depth, discovery entries, and opt-in
  exhaustive classpath-root searching for JARs that omit directory entries. Ordinary classloader resource discovery
  remains the safe default.
- Added CLDR 48.2-backed cardinality, ordinality, cardinality-range, locale-alias, likely-subtag,
  parent-locale, and locale-validity behavior generated from pinned Unicode CLDR source data.
- Added [`PluralOperands`](https://javadoc.lokalized.com/com/lokalized/PluralOperands.html) plus
  [`Cardinality.forOperands(...)`](https://javadoc.lokalized.com/com/lokalized/Cardinality.html#forOperands(com.lokalized.PluralOperands,java.util.Locale))
  and [`Ordinality.forOperands(...)`](https://javadoc.lokalized.com/com/lokalized/Ordinality.html#forOperands(com.lokalized.PluralOperands,java.util.Locale))
  for visible-decimal-place and compact-decimal plural evaluation. `PluralOperands` values are accepted by cardinality,
  ordinality, and numeric alternative expressions as well as generated-placeholder rules. Numeric comparisons preserve
  the signed source value while CLDR category evaluation uses its absolute value.
- Added bidirectional isolation for caller-supplied placeholder values in resolved right-to-left locales,
  with [`BidiIsolation.NONE`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html#NONE) available as a global or per-invocation opt-out and [`BidiIsolation.ALWAYS`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html#ALWAYS) available when
  right-to-left caller values can appear in left-to-right translations. The RTL script set is generated from pinned
  CLDR script metadata instead of maintained by hand.
- Added escaped literal mustache support with `\{{...}}`.
- Added a packaged JSON Schema at `schema/lokalized-strings.schema.json`, with canonical
  `https://lokalized.com/schema/lokalized-strings.schema.json` identity and the same Unicode combining-mark identifier
  acceptance as the runtime loader.
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
- Added aggregate localized strings file limits and explicit locale-to-classpath-resource loading for containers and
  custom classloaders that can open resources but cannot enumerate standard `file:` or `jar:` package URLs.
- Added expression-selected generated fragments. A placeholder may declare a required default `translation` and an
  optional ordered list of string-valued expression alternatives; the first match wins. Omitting alternatives creates
  a scoped constant/composed fragment.

### Behavior Changes

- Missing translations and runtime resolution failures are routed through `TranslationFailureHandler`.
  The default handler silently returns the key with caller-supplied placeholders interpolated.
- Locale matching now uses full CLDR aliases, including compound aliases and context-sensitive
  multi-territory replacements, parent locales, likely subtags, and script-aware matching.
- Plural-rule lookup now canonicalizes the complete locale tag before selecting cardinality, ordinality, and range
  data, so compound language aliases such as `aa-Saaho` → `ssy` no longer fail or fall through to the wrong language.
- Exact loaded locale tags win before canonical-equivalent aliases; language-range quality weights and
  `q=0` exclusions are honored. Exact private-use tags such as `x-acme` can be selected consistently through locale
  and language-range options, while multiple private-use or undetermined localized strings files no longer require
  meaningless primary-language tiebreakers.
- Locale fallback chains are deduplicated after conversion to `Locale`, avoiding repeated attempts when RFC 4647
  truncation leaves a trailing extension singleton that Java discards.
- Fallback after a resolution failure preserves the first application exception without mutating it with suppressed
  failures from later locale attempts.
- Sparse locale files can fall back per key through the locale candidate chain.
- Placeholder and expression identifiers now share the same Unicode letter/digit naming policy.
- Selected generated-placeholder fragments may reference caller values and other generated placeholders recursively;
  cycles, excessive depth, and output above the configured limit are resolution failures. The default output limit is
  262,144 UTF-16 code units, with a 1,048,576-code-unit hard ceiling.
- Whole-message and expression-fragment predicates evaluate against one immutable caller-input snapshot and the locale
  of the localized strings for the candidate locale. Language-form selectors and range endpoints also always read raw caller
  input; generated values never become predicate operands or selector inputs.
- Expression fragments use ordered first-match/default selection and demand-driven dependency resolution. Unselected,
  shadowed, and unreachable definitions consume no lookup-time expansion work. Both placeholder kinds share recursive
  expansion, cycle detection, safety limits, fallback behavior, and bidi handling.
- Expression-fragment predicate and selected-fragment interpolation errors are resolution failures. The default
  fallback policy stops, while `fallbackOnAnyFailure()` may continue. Because every expression fragment has a default,
  it cannot itself produce `NO_MATCHING_ALTERNATIVE`.
- Alternatives-only keys for which no condition matches are reported as
  `TranslationFailureReason.NO_MATCHING_ALTERNATIVE` without a synthetic expression-evaluation cause.
- Failure-key interpolation uses the configured output limit, 262,144 UTF-16 code units by default; if interpolation
  would exceed the cap, the raw key is returned.
- Caller-supplied `CharSequence` placeholder values are length-checked against the remaining interpolation capacity
  before materialization. The same configured limit bounds phonetic inputs before conversion to `String`. Bidi
  balancing is performed directly into a bounded result; oversized failure-key values return the raw key without
  scanning or copying the complete input. Lokalized cannot bound application code inside an arbitrary
  non-`CharSequence` object's `toString()` implementation.
- [`bestMatchFor(...)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchFor(java.util.List)) resolves `Locale.ROOT`, `und`, wildcard-only language ranges, empty preference lists, and unmatched
  locale preferences to the configured fallback locale. Strict [`matchFor(...)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#matchFor(java.util.List)) never manufactures a fallback after a miss.
- Language-range matching accepts at most 32 parsed preferences per call. `Locale.LanguageRange.parse(...)` may expand
  one source range into multiple IANA-equivalent ranges, all of which count toward the limit. If every supported locale
  is excluded by `q=0`, `bestMatchFor(...)` returns the configured fallback while strict `matchFor(...)` reports no
  acceptable locale.
- Locale matching precomputes per-supported-locale CLDR canonicalization, fallback chains, and likely-subtag data once
  per `Strings` instance instead of on every request. Match results are byte-for-byte identical; the change removes a
  worst-case per-request CPU cost that scaled with (locales x requested ranges) and is influenceable through
  `Accept-Language`, so adversarial many-range/many-locale negotiation is dramatically cheaper. The one-time cost of
  building the precomputed data is paid at `Strings` construction.
- Language-range matching no longer crosses known likely-script boundaries and applies `q=0` exclusions to canonical
  alias descendants, such as `sh;q=0` excluding `sr-Latn-RS`.
- Internal-wildcard language ranges now use RFC 4647 extended filtering without CLDR, likely-subtag, or primary-language
  broadening; successful results consistently report [`EXTENDED_RANGE`](https://javadoc.lokalized.com/com/lokalized/LocaleMatchType.html#EXTENDED_RANGE). Locale-range specificity is ordered
  lexicographically over real structural constraints, so long ranges cannot overflow into a stronger match or
  exclusion category and redundant wildcard subtags cannot manufacture exclusion specificity. Available `und`/root
  data can no longer masquerade as a likely English match.
- Weighted locale diagnostics now retain the same most-specific preference range that determines a selected locale's
  effective quality. The reported match type and `TranslationResult.isFallback()` classification therefore describe
  the preference that actually won rather than an earlier, broader range with a higher raw weight.
- Fixed-English diagnostics now format numbers with locale-independent digits and separators, regardless of the
  process default locale.
- Generated placeholder expansion now has a cumulative work/output budget in addition to per-fragment and final-output
  limits, preventing many individually legal cached fragments from exhausting the heap. The default cumulative budget
  is 1,048,576 UTF-16 code units, with an 8,388,608-code-unit hard ceiling.
- Numeric literals and plural operands are validated before materialization, so compact exponents and decimal scales
  cannot create unbounded work.
- Expression tokenization is contiguous: only ASCII space, tab, carriage return, line feed, and form feed are ignored,
  and every other unsupported code point is rejected with its source index instead of being skipped.
- Programmatic localized-string graphs are validated before structural hashing, so excessive alternative nesting
  produces the controlled 128-level validation error instead of `StackOverflowError` during `Strings` construction.
- `LocalizedString.equals()` and `hashCode()` now traverse alternative graphs iteratively and memoize shared nodes, so
  direct value comparisons and hashing do not overflow on deep graphs or expand shared DAG work exponentially.
- [`LocalizedString.toString()`](https://javadoc.lokalized.com/com/lokalized/LocalizedString.html#toString()) is now an iterative, identity-aware, bounded diagnostic rendering, so deep, cyclic, or
  shared programmatic graphs cannot overflow the stack or expand output exponentially.
- Public localized-string value constructors now reject null collection keys, values, and elements at the construction
  boundary instead of deferring those failures to graph validation.
- JSON loading rejects unpaired Unicode surrogates and reports source lines consistently for LF, CRLF, and bare-CR
  input. Exploded-classpath discovery filters unrelated names before resolving real paths, explicit resource maps
  reject malformed or duplicate rendered locale tags, and a contract-violating zero-read input stream can no longer
  make parsing spin forever.
- Loader result maps preserve deterministic language-tag iteration order without comparator-based key equivalence, so
  ordinary `Map.get`, `containsKey`, and equality semantics remain based on `Locale.equals()`. Duplicate physical JAR
  entries for one logical runtime resource are rejected instead of allowing Lokalized and the JVM classloader to select
  different bytes.
- Explicit locale-to-resource mappings are checked against the localized-strings file-count limit before they are
  copied or sorted. Exhaustive discovery consistently recognizes an existing package whose only direct resources are
  warning-and-skip foreign JSON files, and the physical multi-release namespace under `META-INF/versions` is reserved
  from package discovery.
- Canonical-alias lookup records and evaluates against the locale of the loaded localized strings, deduplicates
  equivalent locale attempts, and keeps inspection APIs strict to exact members of `getSupportedLocales()`.
- Classpath loading honors runtime-selected multi-release JAR entries, expands filesystem manifest `Class-Path` roots
  during opt-in exhaustive discovery, rejects Windows drive-relative package escapes, and accepts `und` localized
  strings files.

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
  `TranslationFailureHandler.returnKey()` preserves the silent default soft-fail behavior,
  `returnKey(Consumer<? super TranslationFailure>)` also reports structured events to an application observer, and
  `TranslationFailureHandler.throwException()` provides fail-fast behavior.
- Replace direct construction or references to `DefaultStrings` with
  [`Strings.withFallbackLocale(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.html#withFallbackLocale(java.util.Locale)).
  A minimal replacement supplies both
  [`localizedStringSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localizedStringSupplier(java.util.function.Supplier))
  and [`localeSupplier(...)`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#localeSupplier(java.util.function.Function))
  before calling
  [`build()`](https://javadoc.lokalized.com/com/lokalized/Strings.Builder.html#build()):

  ```java
  Strings strings = Strings.withFallbackLocale(Locale.forLanguageTag("en-US"))
    .localizedStringSupplier(() -> LocalizedStringLoader.loadFromClasspath("strings"))
    .localeSupplier(matcher -> matcher.bestMatchFor(Locale.getDefault()))
    .build();
  ```
- Update custom `Strings` implementations with the three new inspection methods.
- Replace `Cardinality.getSupportedLanguageCodes()` and `Ordinality.getSupportedLanguageCodes()` with
  the corresponding `getSupportedLocaleTags()` calls.
- Replace `LocalizedString.Builder.languageFormTranslationsByPlaceholder(...)` with
  `placeholderDefinitions(...)`, and replace `getLanguageFormTranslationsByPlaceholder()` with
  `getPlaceholderDefinitions()`. The returned map is now `Map<String, PlaceholderDefinition>`; use `instanceof` before
  casting and handle both `LanguageFormTranslation` and `ExpressionTranslation` (plus unfamiliar future
  library-defined subtypes defensively).
- Replace `LocalizedStringLoadingOptions.Builder.maximumTranslations(...)` and `getMaximumTranslations()` with
  `maximumTranslationNodes(...)` and `getMaximumTranslationNodes()`. Recheck configured boundaries because every
  placeholder definition and expression-fragment alternative now counts in addition to roots and whole-message
  alternatives.
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
  have application code choose a purpose-specific translation key. The 3.0 loader rejects selector-driven localized
  strings files from 2.1.
- Move useful `placeholderMetadata` notes into message-level `commentary` or external translation documentation.
  Localized strings files containing `placeholderMetadata` are rejected by the 3.0 loader.
- Use `localeMatchSupplier(matcher -> matcher.matchFor(ranges))` instead of collapsing a request through
  `localeSupplier(matcher -> matcher.bestMatchFor(ranges))` when `TranslationResult` and failure diagnostics must retain
  the original language ranges.
- Locale matching now front-loads its per-supported-locale CLDR work to `Strings` construction. Applications that build
  very large `Strings` instances (hundreds of supported locales) and immediately discard them see a small one-time
  construction cost in exchange for much cheaper per-request matching; long-lived shared instances are strictly faster.
- Round values explicitly before reducing `PluralOperands.visibleDecimalPlaces(...)`; implicit flooring has been removed.
- Convert application-specific `Number` implementations to `BigDecimal` before passing them to Lokalized. The library
  no longer treats an arbitrary `Number.toString()` result as a decimal representation.
- Replace comparisons between two raw `CharSequence` placeholders with application-side textual selection or an
  explicit comparison between phonetic input and a `PHONETIC_*`/`Phonetic` value.
- Keep alternative expressions to the documented ASCII whitespace set. NEL, Unicode line/paragraph separators,
  no-break space, and other unsupported separators are now rejected at their exact source position.
- Expect invalid programmatic localized strings to fail during `Strings.build()` under the shared semantic validator.
- Review localized strings files under the stricter loader validation before release. Duplicate nested JSON members,
  malformed placeholders, whitespace-padded mustaches, reserved language-form placeholder names, invalid
  locale filenames in explicitly loaded filesystem directories containing localized strings files, invalid alternative expressions,
  explicit null placeholder modes, blank/BOM-only localized strings files, and malformed UTF-8 are rejected while
  loading. Use `{}` for an empty localized strings file.
- Review localized strings file sizes and nesting against the new per-resource defaults (8 MiB input, 8,388,608 reader
  UTF-16 code units, and depth 64) and load-wide defaults (32 MiB input, 256 localized strings files, and 100,000
  examined discovery entries). The
  100,000-translation-node budget counts roots, whole-message alternatives, placeholder definitions, and
  expression-fragment alternatives; it and the
  1,000-warning budget also apply to single-resource `parse(...)` calls. A configured total-input budget also applies
  to single-resource `Path` and `InputStream` parsing. Pass
  `LocalizedStringLoadingOptions` to select different limits; nesting cannot be raised above 128.
- Combine repeated `Accept-Language` field lines in received order and pass the combined value to
  [`LocaleMatcher.bestMatchForAcceptLanguage(...)`](https://javadoc.lokalized.com/com/lokalized/LocaleMatcher.html#bestMatchForAcceptLanguage(java.lang.String)).
  It bounds and parses raw input, falls back for unusable values, and never truncates preferences. The parsed-list APIs
  remain strict and accept at most 32 ranges.
- Review any application that relied on the previous runtime defaults. Use [`TranslationRuntimeLimits`](https://javadoc.lokalized.com/com/lokalized/TranslationRuntimeLimits.html) to opt back up
  where necessary; the previous numeric, expression, generated-placeholder, interpolation, and expansion values remain
  hard ceilings.
- Prefer a namespaced classpath package such as `com/example/myapp/strings`. Enable
  `LocalizedStringLoadingOptions.Builder.exhaustiveClasspathSearch(true)` only when a JAR omits package directory
  entries. Classpath `.json` resources whose filenames are not valid locale tags are warning-and-skip, but remain fatal
  in explicitly loaded filesystem directories containing localized strings files.
- Placeholder and alternative-expression identifiers now follow the same rule: start with a Unicode letter
  or underscore, then use Unicode letters, Unicode numbers, Unicode combining marks, underscores, or hyphens.
  Unicode property membership follows the executing JDK (or independent schema validator), so author identifiers for
  the oldest runtime in the deployment fleet; use `[A-Za-z_][A-Za-z0-9_-]*` for maximum cross-runtime portability.
- Expect CLDR-backed plural and locale matching behavior to differ from the older handwritten tables in
  some locales.
- Incomplete CLDR cardinality or ordinality maps now produce structured loading warnings, which are silently ignored by
  default. Pass a warning-handler lambda to collect or forward them, or use
  `LocalizedStringWarningHandler.throwException()` for build-time strictness.
- Right-to-left locale output may now include Unicode FSI/PDI controls around caller-supplied placeholder
  values. Use [`BidiIsolation.ALWAYS`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html#ALWAYS) when right-to-left caller values can appear in left-to-right translations, and use
  [`BidiIsolation.NONE`](https://javadoc.lokalized.com/com/lokalized/BidiIsolation.html#NONE) only for sinks that cannot accept bidi controls.

Selected-branch placeholder inheritance intentionally changes one caller-collision case. Given this localized strings file:

```json
{
  "Message" : {
    "placeholders" : {
      "label" : { "translation" : "file label" }
    },
    "alternatives" : [
      { "mode == 1" : "{{label}}" }
    ]
  }
}
```

the selected child previously rendered a caller-supplied `label`; it now inherits and renders `file label`. Rename
the caller value or add an explicit child definition when the old behavior was intentional. Inherited definitions also
affect interpolation and selectors differently by design: an inherited generated `count` supplies rendered
`{{count}}`, while a language-form definition whose `value` is `count` still classifies the caller's raw `count`.
`range.start` and `range.end` follow the same raw-input rule. Prefer distinct names such as `count` and `countText`.
