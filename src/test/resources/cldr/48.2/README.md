# CLDR 48.2 Data

These files are pinned from the Unicode CLDR 48.2 release tag:

* https://github.com/unicode-org/cldr/tree/release-48-2/common/supplemental

The pinned XML files are Unicode CLDR data files licensed under Unicode License v3.
Their notices are preserved in the XML files, and the project-level Unicode notice is recorded in
`THIRD-PARTY-NOTICES.md` at the `lokalized-java` repository root.

Included files:

| File | SHA-256 |
|---|---|
| `common/supplemental/plurals.xml` | `d701d8b461afd2ba5eb21f9c1d645c8a661de225596afcf0f492ee36c69d727d` |
| `common/supplemental/ordinals.xml` | `129bf4aa6f41d47931bf908ebaa261ec0b73b768f1fe3be90c06ab11fb49880a` |
| `common/supplemental/pluralRanges.xml` | `42c82db9baaa8667921b4dea32b6322fd5e43004e166a43ae33b51cef5dc0f52` |

Refresh and regenerate command:

```shell
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/plurals.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/plurals.xml
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/ordinals.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/ordinals.xml
curl -s -L -o src/test/resources/cldr/48.2/common/supplemental/pluralRanges.xml https://raw.githubusercontent.com/unicode-org/cldr/release-48-2/common/supplemental/pluralRanges.xml
shasum -a 256 src/test/resources/cldr/48.2/common/supplemental/*.xml
javac -d target/cldr-generator src/build/java/com/lokalized/cldr/CldrDataGenerator.java
java -cp target/cldr-generator com.lokalized.cldr.CldrDataGenerator
```

The generator emits both runtime plural data under `src/main/java` and conformance fixtures under `src/test/java`.
