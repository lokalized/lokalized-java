# Third-Party Notices

This project includes or generates data derived from third-party materials listed below.

## Unicode CLDR

Lokalized includes generated plural-rule and locale-metadata data derived from Unicode CLDR 48.2 data files:

* `common/supplemental/plurals.xml`
* `common/supplemental/ordinals.xml`
* `common/supplemental/pluralRanges.xml`
* `common/supplemental/likelySubtags.xml`
* `common/supplemental/supplementalMetadata.xml`
* `common/supplemental/supplementalData.xml`
* `common/properties/scriptMetadata.txt`
* `common/validity/language.xml`
* `common/validity/script.xml`
* `common/validity/region.xml`
* `common/validity/variant.xml`

Source:

* https://github.com/unicode-org/cldr/tree/release-48-2/common/supplemental
* https://github.com/unicode-org/cldr/tree/release-48-2/common/properties
* https://github.com/unicode-org/cldr/tree/release-48-2/common/validity

The pinned source files are stored under `src/test/resources/cldr/48.2/` and carry their own SPDX notices.
Generated Java files containing CLDR-derived data identify their CLDR source version in their file headers.

Unicode CLDR data files are licensed under Unicode License v3.

UNICODE LICENSE V3

COPYRIGHT AND PERMISSION NOTICE

Copyright © 2019-2025 Unicode, Inc.

NOTICE TO USER: Carefully read the following legal agreement. BY
DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR
SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE
TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT
DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.

Permission is hereby granted, free of charge, to any person obtaining a
copy of data files and any associated documentation (the "Data Files") or
software and any associated documentation (the "Software") to deal in the
Data Files or Software without restriction, including without limitation
the rights to use, copy, modify, merge, publish, distribute, and/or sell
copies of the Data Files or Software, and to permit persons to whom the
Data Files or Software are furnished to do so, provided that either (a)
this copyright and permission notice appear with all copies of the Data
Files or Software, or (b) this copyright and permission notice appear in
associated Documentation.

THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
THIRD PARTY RIGHTS.

IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE
BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES,
OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA
FILES OR SOFTWARE.

Except as contained in this notice, the name of a copyright holder shall
not be used in advertising or otherwise to promote the sale, use or other
dealings in these Data Files or Software without prior written
authorization of the copyright holder.

SPDX-License-Identifier: Unicode-3.0

## minimal-json

Lokalized embeds the minimal-json library.

Source:

* https://github.com/ralfstx/minimal-json

License text for minimal-json is included in `LICENSE`.
