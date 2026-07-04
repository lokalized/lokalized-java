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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Package-private access to CLDR locale metadata used by locale matching and validation.
 *
 * @author <a href="https://revetkn.com">Mark Allen</a>
 */
@ThreadSafe
final class CldrLocaleData {
  @NonNull
  private static final String ROOT_PARENT = "root";
  @NonNull
  private static final String UNDETERMINED_LANGUAGE = "und";
  @NonNull
  private static final Map<@NonNull String, @NonNull String> LANGUAGE_ALIASES_BY_TAG;
  @NonNull
  private static final Map<@NonNull String, @NonNull String> SCRIPT_ALIASES_BY_TAG;
  @NonNull
  private static final Map<@NonNull String, @NonNull String> REGION_ALIASES_BY_TAG;
  @NonNull
  private static final Map<@NonNull String, @NonNull String> VARIANT_ALIASES_BY_TAG;
  @NonNull
  private static final Map<@NonNull String, @NonNull String> LIKELY_SUBTAGS_BY_TAG;
  @NonNull
  private static final Map<@NonNull String, @NonNull String> PARENT_LOCALES_BY_TAG;
  @NonNull
  private static final Set<@NonNull String> VALID_LANGUAGES;
  @NonNull
  private static final Set<@NonNull String> VALID_SCRIPTS;
  @NonNull
  private static final Set<@NonNull String> VALID_REGIONS;
  @NonNull
  private static final Set<@NonNull String> VALID_VARIANTS;

  static {
    LANGUAGE_ALIASES_BY_TAG = mapFor(GeneratedCldrLocaleData.LANGUAGE_ALIASES);
    SCRIPT_ALIASES_BY_TAG = mapFor(GeneratedCldrLocaleData.SCRIPT_ALIASES);
    REGION_ALIASES_BY_TAG = mapFor(GeneratedCldrLocaleData.REGION_ALIASES);
    VARIANT_ALIASES_BY_TAG = mapFor(GeneratedCldrLocaleData.VARIANT_ALIASES);
    LIKELY_SUBTAGS_BY_TAG = mapFor(GeneratedCldrLocaleData.LIKELY_SUBTAGS);
    PARENT_LOCALES_BY_TAG = mapFor(GeneratedCldrLocaleData.PARENT_LOCALES);
    VALID_LANGUAGES = setFor(GeneratedCldrLocaleData.VALID_LANGUAGES);
    VALID_SCRIPTS = setFor(GeneratedCldrLocaleData.VALID_SCRIPTS);
    VALID_REGIONS = setFor(GeneratedCldrLocaleData.VALID_REGIONS);
    VALID_VARIANTS = setFor(GeneratedCldrLocaleData.VALID_VARIANTS);
  }

  private CldrLocaleData() {
    // Non-instantiable
  }

  @NonNull
  static Optional<String> languageAliasFor(@NonNull String language) {
    requireNonNull(language);

    @Nullable String alias = LANGUAGE_ALIASES_BY_TAG.get(keyFor(language));

    if (alias == null)
      return Optional.empty();

    return Optional.of(languageFor(alias));
  }

  @NonNull
  static Locale canonicalLocale(@NonNull Locale locale) {
    requireNonNull(locale);
    return localeForTag(canonicalLanguageTag(locale.toLanguageTag()));
  }

  @NonNull
  static String canonicalLanguageTag(@NonNull String languageTag) {
    requireNonNull(languageTag);

    String canonicalLanguageTag = TagParts.forLanguageTag(languageTag).toLanguageTag();

    for (int i = 0; i < 8; ++i) {
      String aliasedLanguageTag = aliasLanguageTagOnce(canonicalLanguageTag);

      if (aliasedLanguageTag.equals(canonicalLanguageTag))
        return canonicalLanguageTag;

      canonicalLanguageTag = TagParts.forLanguageTag(aliasedLanguageTag).toLanguageTag();
    }

    return canonicalLanguageTag;
  }

  @NonNull
  static List<@NonNull Locale> fallbackLocalesFor(@NonNull Locale locale) {
    requireNonNull(locale);

    LinkedHashSet<@NonNull String> candidateTags = new LinkedHashSet<>();
    String requestedLanguageTag = locale.toLanguageTag();
    String canonicalLanguageTag = canonicalLanguageTag(requestedLanguageTag);

    addFallbackTags(candidateTags, requestedLanguageTag);
    addFallbackTags(candidateTags, canonicalLanguageTag);
    addParentTags(candidateTags, canonicalLanguageTag);

    List<@NonNull Locale> candidateLocales = new ArrayList<>(candidateTags.size());

    for (String candidateTag : candidateTags)
      if (!ROOT_PARENT.equals(candidateTag))
        candidateLocales.add(localeForTag(candidateTag));

    return Collections.unmodifiableList(candidateLocales);
  }

  @NonNull
  static Optional<String> likelySubtagFor(@NonNull Locale locale) {
    requireNonNull(locale);
    return likelySubtagFor(locale.toLanguageTag());
  }

  @NonNull
  static Optional<String> likelySubtagFor(@NonNull String languageTag) {
    requireNonNull(languageTag);

    TagParts tagParts = TagParts.forLanguageTag(canonicalLanguageTag(languageTag));

    if (tagParts.isPrivateUse() || tagParts.getLanguage().length() == 0)
      return Optional.empty();

    for (String candidateTag : likelySubtagCandidateTags(tagParts)) {
      @Nullable String likelySubtag = LIKELY_SUBTAGS_BY_TAG.get(keyFor(candidateTag));

      if (likelySubtag != null)
        return Optional.of(maximizedLikelySubtag(tagParts, likelySubtag));
    }

    return Optional.empty();
  }

  static boolean isKnownLanguageTag(@NonNull String languageTag) {
    requireNonNull(languageTag);

    TagParts tagParts = TagParts.forLanguageTag(languageTag);

    if (tagParts.isPrivateUse())
      return true;

    String language = tagParts.getLanguage();

    if (language.length() == 0)
      return false;

    if (!isKnownLanguage(language) && !LANGUAGE_ALIASES_BY_TAG.containsKey(keyFor(language)) &&
        !LANGUAGE_ALIASES_BY_TAG.containsKey(keyFor(tagParts.toLanguageTag())))
      return false;

    if (tagParts.getScript().length() > 0 && !isKnownScript(tagParts.getScript()) &&
        !SCRIPT_ALIASES_BY_TAG.containsKey(keyFor(tagParts.getScript())))
      return false;

    if (tagParts.getRegion().length() > 0 && !isKnownRegion(tagParts.getRegion()) &&
        !REGION_ALIASES_BY_TAG.containsKey(keyFor(tagParts.getRegion())))
      return false;

    for (String variant : tagParts.getVariants())
      if (!isKnownVariant(variant) && !VARIANT_ALIASES_BY_TAG.containsKey(keyFor(variant)))
        return false;

    return true;
  }

  static boolean equivalent(@NonNull Locale locale1, @NonNull Locale locale2) {
    requireNonNull(locale1);
    requireNonNull(locale2);
    return canonicalLanguageTag(locale1.toLanguageTag()).equalsIgnoreCase(canonicalLanguageTag(locale2.toLanguageTag()));
  }

  private static boolean isKnownLanguage(String language) {
    return VALID_LANGUAGES.contains(keyFor(language));
  }

  private static boolean isKnownScript(String script) {
    return VALID_SCRIPTS.contains(keyFor(script));
  }

  private static boolean isKnownRegion(String region) {
    return VALID_REGIONS.contains(keyFor(region));
  }

  private static boolean isKnownVariant(String variant) {
    return VALID_VARIANTS.contains(keyFor(variant));
  }

  @NonNull
  private static String aliasLanguageTagOnce(@NonNull String languageTag) {
    @Nullable String directAlias = LANGUAGE_ALIASES_BY_TAG.get(keyFor(languageTag));

    if (directAlias != null)
      return directAlias;

    TagParts tagParts = TagParts.forLanguageTag(languageTag);

    if (tagParts.isPrivateUse())
      return tagParts.toLanguageTag();

    @Nullable String languageAlias = LANGUAGE_ALIASES_BY_TAG.get(keyFor(tagParts.getLanguage()));

    if (languageAlias != null) {
      TagParts aliasParts = TagParts.forLanguageTag(languageAlias);
      tagParts = tagParts.withLanguage(aliasParts.getLanguage().length() == 0 ? tagParts.getLanguage() : aliasParts.getLanguage());

      if (tagParts.getScript().length() == 0 && aliasParts.getScript().length() > 0)
        tagParts = tagParts.withScript(aliasParts.getScript());

      if (tagParts.getRegion().length() == 0 && aliasParts.getRegion().length() > 0)
        tagParts = tagParts.withRegion(aliasParts.getRegion());

      if (tagParts.getVariants().isEmpty() && !aliasParts.getVariants().isEmpty())
        tagParts = tagParts.withVariants(aliasParts.getVariants());
    }

    if (tagParts.getScript().length() > 0) {
      @Nullable String scriptAlias = SCRIPT_ALIASES_BY_TAG.get(keyFor(tagParts.getScript()));

      if (scriptAlias != null)
        tagParts = tagParts.withScript(TagParts.forLanguageTag(UNDETERMINED_LANGUAGE + "-" + scriptAlias).getScript());
    }

    if (tagParts.getRegion().length() > 0) {
      @Nullable String regionAlias = REGION_ALIASES_BY_TAG.get(keyFor(tagParts.getRegion()));

      if (regionAlias != null)
        tagParts = tagParts.withRegion(regionAlias);
    }

    if (!tagParts.getVariants().isEmpty()) {
      List<@NonNull String> variants = new ArrayList<>(tagParts.getVariants().size());

      for (String variant : tagParts.getVariants()) {
        @Nullable String variantAlias = VARIANT_ALIASES_BY_TAG.get(keyFor(variant));
        variants.add(variantAlias == null ? variant : variantAlias);
      }

      tagParts = tagParts.withVariants(variants);
    }

    return tagParts.toLanguageTag();
  }

  private static void addFallbackTags(@NonNull LinkedHashSet<@NonNull String> candidateTags,
                                      @NonNull String languageTag) {
    candidateTags.add(languageTag);
    addParentTags(candidateTags, languageTag);

    String candidateTag = languageTag;
    int subtagSeparatorIndex = candidateTag.lastIndexOf('-');

    while (subtagSeparatorIndex > 0) {
      candidateTag = candidateTag.substring(0, subtagSeparatorIndex);
      candidateTags.add(candidateTag);
      addParentTags(candidateTags, candidateTag);
      subtagSeparatorIndex = candidateTag.lastIndexOf('-');
    }
  }

  private static void addParentTags(@NonNull LinkedHashSet<@NonNull String> candidateTags,
                                    @NonNull String languageTag) {
    String candidateTag = languageTag;
    Set<@NonNull String> seenTags = new HashSet<>();

    while (seenTags.add(keyFor(candidateTag))) {
      @Nullable String parentTag = PARENT_LOCALES_BY_TAG.get(keyFor(candidateTag));

      if (parentTag == null)
        return;

      candidateTags.add(parentTag);

      if (ROOT_PARENT.equals(parentTag))
        return;

      candidateTag = parentTag;
    }
  }

  @NonNull
  private static List<@NonNull String> likelySubtagCandidateTags(@NonNull TagParts tagParts) {
    LinkedHashSet<@NonNull String> candidateTags = new LinkedHashSet<>();
    String language = tagParts.getLanguage().length() == 0 ? UNDETERMINED_LANGUAGE : tagParts.getLanguage();
    String script = tagParts.getScript();
    String region = tagParts.getRegion();

    candidateTags.add(tagParts.toLanguageTag());

    if (script.length() > 0 && region.length() > 0)
      candidateTags.add(language + "-" + script + "-" + region);

    if (script.length() > 0)
      candidateTags.add(language + "-" + script);

    if (region.length() > 0)
      candidateTags.add(language + "-" + region);

    candidateTags.add(language);

    if (script.length() > 0)
      candidateTags.add(UNDETERMINED_LANGUAGE + "-" + script);

    if (region.length() > 0)
      candidateTags.add(UNDETERMINED_LANGUAGE + "-" + region);

    candidateTags.add(UNDETERMINED_LANGUAGE);
    return Collections.unmodifiableList(new ArrayList<>(candidateTags));
  }

  @NonNull
  private static String maximizedLikelySubtag(@NonNull TagParts requestedTagParts,
                                              @NonNull String likelySubtag) {
    TagParts likelySubtagParts = TagParts.forLanguageTag(canonicalLanguageTag(likelySubtag));
    String language = requestedTagParts.getLanguage().length() == 0 || UNDETERMINED_LANGUAGE.equals(requestedTagParts.getLanguage())
        ? likelySubtagParts.getLanguage()
        : requestedTagParts.getLanguage();
    String script = requestedTagParts.getScript().length() == 0 ? likelySubtagParts.getScript() : requestedTagParts.getScript();
    String region = requestedTagParts.getRegion().length() == 0 ? likelySubtagParts.getRegion() : requestedTagParts.getRegion();

    return new TagParts(language, script, region, requestedTagParts.getVariants(), Collections.emptyList(), false).toLanguageTag();
  }

  @NonNull
  private static Locale localeForTag(@NonNull String languageTag) {
    if (ROOT_PARENT.equals(languageTag))
      return Locale.ROOT;

    return Locale.forLanguageTag(languageTag);
  }

  @NonNull
  private static String languageFor(@NonNull String languageTag) {
    int separatorIndex = languageTag.indexOf('-');
    return (separatorIndex < 0 ? languageTag : languageTag.substring(0, separatorIndex)).toLowerCase(Locale.ROOT);
  }

  @NonNull
  private static Map<@NonNull String, @NonNull String> mapFor(@NonNull String @NonNull [] @NonNull [] pairs) {
    Map<@NonNull String, @NonNull String> map = new HashMap<>(pairs.length);

    for (String @NonNull [] pair : pairs)
      map.put(keyFor(pair[0]), pair[1]);

    return Collections.unmodifiableMap(map);
  }

  @NonNull
  private static Set<@NonNull String> setFor(@NonNull String @NonNull [] values) {
    Set<@NonNull String> set = new HashSet<>(values.length);

    for (String value : values)
      set.add(keyFor(value));

    return Collections.unmodifiableSet(set);
  }

  @NonNull
  private static String keyFor(@NonNull String value) {
    return value.toLowerCase(Locale.ROOT);
  }

  private static final class TagParts {
    @NonNull
    private final String language;
    @NonNull
    private final String script;
    @NonNull
    private final String region;
    @NonNull
    private final List<@NonNull String> variants;
    @NonNull
    private final List<@NonNull String> extensions;
    private final boolean privateUse;

    private TagParts(@NonNull String language,
                     @NonNull String script,
                     @NonNull String region,
                     @NonNull List<@NonNull String> variants,
                     @NonNull List<@NonNull String> extensions,
                     boolean privateUse) {
      this.language = language;
      this.script = script;
      this.region = region;
      this.variants = Collections.unmodifiableList(new ArrayList<>(variants));
      this.extensions = Collections.unmodifiableList(new ArrayList<>(extensions));
      this.privateUse = privateUse;
    }

    @NonNull
    static TagParts forLanguageTag(@NonNull String languageTag) {
      requireNonNull(languageTag);

      String normalizedLanguageTag = languageTag.trim().replace('_', '-');

      if (normalizedLanguageTag.toLowerCase(Locale.ROOT).startsWith("x-"))
        return new TagParts("", "", "", Collections.emptyList(),
            List.of(normalizedLanguageTag.toLowerCase(Locale.ROOT)), true);

      String[] subtags = normalizedLanguageTag.split("-");
      String language = "";
      String script = "";
      String region = "";
      List<@NonNull String> variants = new ArrayList<>();
      List<@NonNull String> extensions = new ArrayList<>();
      int index = 0;

      if (subtags.length > 0 && subtags[0].length() > 0) {
        language = subtags[0].toLowerCase(Locale.ROOT);
        index = 1;
      }

      if (index < subtags.length && isScriptSubtag(subtags[index])) {
        script = canonicalScriptSubtag(subtags[index]);
        ++index;
      }

      if (index < subtags.length && isRegionSubtag(subtags[index])) {
        region = subtags[index].toUpperCase(Locale.ROOT);
        ++index;
      }

      while (index < subtags.length) {
        String subtag = subtags[index];

        if (subtag.length() == 1)
          break;

        variants.add(subtag.toLowerCase(Locale.ROOT));
        ++index;
      }

      while (index < subtags.length) {
        String subtag = subtags[index];
        extensions.add(subtag.length() == 1 ? subtag.toLowerCase(Locale.ROOT) : subtag.toLowerCase(Locale.ROOT));
        ++index;
      }

      return new TagParts(language, script, region, variants, extensions, false);
    }

    @NonNull
    String getLanguage() {
      return language;
    }

    @NonNull
    String getScript() {
      return script;
    }

    @NonNull
    String getRegion() {
      return region;
    }

    @NonNull
    List<@NonNull String> getVariants() {
      return variants;
    }

    boolean isPrivateUse() {
      return privateUse;
    }

    @NonNull
    TagParts withLanguage(@NonNull String language) {
      requireNonNull(language);
      return new TagParts(language.toLowerCase(Locale.ROOT), script, region, variants, extensions, privateUse);
    }

    @NonNull
    TagParts withScript(@NonNull String script) {
      requireNonNull(script);
      return new TagParts(language, script.length() == 0 ? "" : canonicalScriptSubtag(script), region, variants, extensions, privateUse);
    }

    @NonNull
    TagParts withRegion(@NonNull String region) {
      requireNonNull(region);
      return new TagParts(language, script, region.toUpperCase(Locale.ROOT), variants, extensions, privateUse);
    }

    @NonNull
    TagParts withVariants(@NonNull List<@NonNull String> variants) {
      requireNonNull(variants);
      return new TagParts(language, script, region, variants, extensions, privateUse);
    }

    @NonNull
    String toLanguageTag() {
      if (privateUse)
        return String.join("-", extensions);

      List<@NonNull String> subtags = new ArrayList<>();

      if (language.length() > 0)
        subtags.add(language);

      if (script.length() > 0)
        subtags.add(script);

      if (region.length() > 0)
        subtags.add(region);

      subtags.addAll(variants);
      subtags.addAll(extensions);

      return subtags.size() == 0 ? UNDETERMINED_LANGUAGE : String.join("-", subtags);
    }

    private static boolean isScriptSubtag(@NonNull String subtag) {
      return subtag.length() == 4 && isAlphabetic(subtag);
    }

    private static boolean isRegionSubtag(@NonNull String subtag) {
      return (subtag.length() == 2 && isAlphabetic(subtag)) || (subtag.length() == 3 && isNumeric(subtag));
    }

    @NonNull
    private static String canonicalScriptSubtag(@NonNull String subtag) {
      return subtag.substring(0, 1).toUpperCase(Locale.ROOT) + subtag.substring(1).toLowerCase(Locale.ROOT);
    }

    private static boolean isAlphabetic(@NonNull String value) {
      for (int i = 0; i < value.length(); ++i)
        if (!Character.isLetter(value.charAt(i)))
          return false;

      return true;
    }

    private static boolean isNumeric(@NonNull String value) {
      for (int i = 0; i < value.length(); ++i)
        if (!Character.isDigit(value.charAt(i)))
          return false;

      return true;
    }
  }
}
