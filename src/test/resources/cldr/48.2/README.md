# CLDR 48.2 Data

These files are pinned from the Unicode CLDR 48.2 release tag:

* https://github.com/unicode-org/cldr/tree/release-48-2/common/supplemental
* https://github.com/unicode-org/cldr/tree/release-48-2/common/validity
* https://github.com/unicode-org/cldr/tree/release-48-2/common/properties

The pinned XML and TXT files are Unicode CLDR data files licensed under Unicode License v3.
Their notices are preserved in the source files, and the project-level Unicode notice is recorded in
`THIRD-PARTY-NOTICES.md` at the `lokalized-java` repository root.

Included files:

| File | SHA-256 |
|---|---|
| `common/supplemental/plurals.xml` | `d701d8b461afd2ba5eb21f9c1d645c8a661de225596afcf0f492ee36c69d727d` |
| `common/supplemental/ordinals.xml` | `129bf4aa6f41d47931bf908ebaa261ec0b73b768f1fe3be90c06ab11fb49880a` |
| `common/supplemental/pluralRanges.xml` | `42c82db9baaa8667921b4dea32b6322fd5e43004e166a43ae33b51cef5dc0f52` |
| `common/supplemental/likelySubtags.xml` | `b0049bc6420715b1e3010f43d95745dca227c5aa60357d1d89a1bdcb06287312` |
| `common/supplemental/supplementalMetadata.xml` | `36e807ce72b15304dd993f132216f408351be1c4376edaa2fe9e9547e2efcac1` |
| `common/supplemental/supplementalData.xml` | `cd2af39aef82fdbfba4d591c87548203350538ad2318486d104b3b38b8d62f1a` |
| `common/properties/scriptMetadata.txt` | `50a0499acddd7a5ce935e8d77b59ab789f91b16c15b95b00236d90137fe662a4` |
| `common/validity/language.xml` | `3f2be6cb8f4a1b506a5bc8d79d3a91349f3061ade565f836c534c00c2efd2996` |
| `common/validity/script.xml` | `30f7f5f64be0df200bdfe42f7ce103a045ab661490885a7f060f31d4199be948` |
| `common/validity/region.xml` | `e751e0eedd46b52c38f3cdb72b0fab61ac8b48e052e8b28ba74b6ac26c4c8cb1` |
| `common/validity/variant.xml` | `49019a8e903802a75197d47ef9854d24655bdac373b8667cf00a33fd9205676e` |

Refresh and regenerate command:

```shell
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/plurals.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/plurals.xml
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/ordinals.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/ordinals.xml
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/pluralRanges.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/pluralRanges.xml
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/likelySubtags.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/likelySubtags.xml
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/supplementalMetadata.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/supplementalMetadata.xml
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/supplementalData.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/supplementalData.xml
curl -s -L -o src/test/resources/cldr/48.2/common/properties/scriptMetadata.txt https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/properties/scriptMetadata.txt
curl -s -L -o src/test/resources/cldr/48.2/common/validity/language.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/validity/language.xml
curl -s -L -o src/test/resources/cldr/48.2/common/validity/script.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/validity/script.xml
curl -s -L -o src/test/resources/cldr/48.2/common/validity/region.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/validity/region.xml
curl -s -L -o src/test/resources/cldr/48.2/common/validity/variant.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/validity/variant.xml
shasum -a 256 src/test/resources/cldr/48.2/common/supplemental/*.xml src/test/resources/cldr/48.2/common/validity/*.xml src/test/resources/cldr/48.2/common/properties/scriptMetadata.txt
javac -d target/cldr-generator src/build/java/com/lokalized/cldr/CldrDataGenerator.java
java -cp target/cldr-generator com.lokalized.cldr.CldrDataGenerator
java -cp target/cldr-generator com.lokalized.cldr.CldrDataGenerator --check
```

The generator emits runtime plural and locale data under `src/main/java`, exhaustive conformance fixtures under
`src/test/java`, and grouped website data at `src/build/resources/cldr/cldr-plural-data.json`.
