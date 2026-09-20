package com.lokalized;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Locale.LanguageRange;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * IANA language-range equivalences, sourced from a pinned Language Subtag Registry snapshot rather
 * than from the running JDK.
 * <p>
 * <strong>Why this exists.</strong> {@link LanguageRange#parse(String)} expands a range using an
 * equivalence table baked into the JDK, and that table moves between JDK releases. Measured: the
 * same unmodified Lokalized jar answers {@code Accept-Language: yol} with the {@code en} catalog on
 * a JDK whose bundled registry is stamped 2025-05-15 and with the {@code enm} catalog on one
 * stamped 2026-05-05. A library whose selected catalog depends on the consumer's JVM is not
 * reproducible, and no existing test could see it, because every test runs on a single JDK.
 * <p>
 * This pins the LANGUAGE half of that table to registry {@code File-Date: 2026-09-17} —
 * 781 keys across 369 equivalence classes — so the answer is a function of the
 * library rather than of the platform.
 * <p>
 * <strong>What it deliberately does not change.</strong> Region and variant substitution stays the
 * JDK's, reproduced below in the JDK's own iteration order. That behaviour is a house dialect
 * rather than registry content — it expands {@code de-DE} to {@code de-dd}, resurrecting a region
 * deprecated in 1990, and the registry states no such mapping — but it is long-standing observable
 * behaviour and is inert in practice, so changing it would move answers for existing callers to no
 * measurable benefit. Pinning the ORDER is itself a correction: which substitution applies when two
 * are applicable was previously decided by {@code java.util.HashMap} iteration order.
 */
final class IanaLanguageEquivalents {
	/** The pinned registry snapshot this table was generated from. */
	static final String REGISTRY_FILE_DATE = "2026-09-17";

	/**
	 * Lowercased subtag to the OTHER members of its equivalence class, from the pinned registry.
	 * Generated; do not edit by hand.
	 */
	private static final Map<String, List<String>> LANGUAGE_EQUIVALENTS = Map.ofEntries(
			Map.entry("aam", List.of("aas")),
			Map.entry("aao", List.of("ar-aao")),
			Map.entry("aas", List.of("aam")),
			Map.entry("abh", List.of("ar-abh")),
			Map.entry("abv", List.of("ar-abv")),
			Map.entry("acm", List.of("ar-acm")),
			Map.entry("acn", List.of("xia")),
			Map.entry("acq", List.of("ar-acq")),
			Map.entry("acw", List.of("ar-acw")),
			Map.entry("acx", List.of("ar-acx")),
			Map.entry("acy", List.of("ar-acy")),
			Map.entry("adf", List.of("ar-adf")),
			Map.entry("adp", List.of("dz")),
			Map.entry("ads", List.of("sgn-ads")),
			Map.entry("adx", List.of("pcr")),
			Map.entry("aeb", List.of("ajt", "ar-aeb")),
			Map.entry("aec", List.of("ar-aec")),
			Map.entry("aed", List.of("sgn-aed")),
			Map.entry("aen", List.of("sgn-aen")),
			Map.entry("afb", List.of("ar-afb")),
			Map.entry("afg", List.of("sgn-afg")),
			Map.entry("ajp", List.of("apc", "ar-ajp", "ar-apc")),
			Map.entry("ajs", List.of("sgn-ajs")),
			Map.entry("ajt", List.of("aeb", "ar-aeb")),
			Map.entry("ami", List.of("i-ami")),
			Map.entry("aog", List.of("myd")),
			Map.entry("apc", List.of("ajp", "ar-ajp", "ar-apc")),
			Map.entry("apd", List.of("ar-apd")),
			Map.entry("ar-aao", List.of("aao")),
			Map.entry("ar-abh", List.of("abh")),
			Map.entry("ar-abv", List.of("abv")),
			Map.entry("ar-acm", List.of("acm")),
			Map.entry("ar-acq", List.of("acq")),
			Map.entry("ar-acw", List.of("acw")),
			Map.entry("ar-acx", List.of("acx")),
			Map.entry("ar-acy", List.of("acy")),
			Map.entry("ar-adf", List.of("adf")),
			Map.entry("ar-aeb", List.of("aeb", "ajt")),
			Map.entry("ar-aec", List.of("aec")),
			Map.entry("ar-afb", List.of("afb")),
			Map.entry("ar-ajp", List.of("apc", "ajp", "ar-apc")),
			Map.entry("ar-apc", List.of("apc", "ajp", "ar-ajp")),
			Map.entry("ar-apd", List.of("apd")),
			Map.entry("ar-arb", List.of("arb")),
			Map.entry("ar-arq", List.of("arq")),
			Map.entry("ar-ars", List.of("ars")),
			Map.entry("ar-ary", List.of("ary")),
			Map.entry("ar-arz", List.of("arz")),
			Map.entry("ar-auz", List.of("auz")),
			Map.entry("ar-avl", List.of("avl")),
			Map.entry("ar-ayh", List.of("ayh")),
			Map.entry("ar-ayl", List.of("ayl")),
			Map.entry("ar-ayn", List.of("ayn")),
			Map.entry("ar-ayp", List.of("ayp")),
			Map.entry("ar-bbz", List.of("bbz")),
			Map.entry("ar-pga", List.of("pga")),
			Map.entry("ar-shu", List.of("shu")),
			Map.entry("ar-ssh", List.of("ssh")),
			Map.entry("arb", List.of("ar-arb")),
			Map.entry("arq", List.of("ar-arq")),
			Map.entry("ars", List.of("ar-ars")),
			Map.entry("art-lojban", List.of("jbo")),
			Map.entry("ary", List.of("ar-ary")),
			Map.entry("arz", List.of("ar-arz")),
			Map.entry("asd", List.of("snz")),
			Map.entry("ase", List.of("sgn-ase", "sgn-us")),
			Map.entry("asf", List.of("sgn-asf")),
			Map.entry("asp", List.of("sgn-asp")),
			Map.entry("asq", List.of("sgn-asq")),
			Map.entry("asw", List.of("sgn-asw")),
			Map.entry("aue", List.of("ktz")),
			Map.entry("auz", List.of("ar-auz")),
			Map.entry("avl", List.of("ar-avl")),
			Map.entry("ayh", List.of("ar-ayh")),
			Map.entry("ayl", List.of("ar-ayl")),
			Map.entry("ayn", List.of("ar-ayn")),
			Map.entry("ayp", List.of("ar-ayp")),
			Map.entry("ayx", List.of("nun")),
			Map.entry("bbz", List.of("ar-bbz")),
			Map.entry("bcg", List.of("bgm")),
			Map.entry("bfi", List.of("sgn-bfi", "sgn-gb")),
			Map.entry("bfk", List.of("sgn-bfk")),
			Map.entry("bfy", List.of("ppa")),
			Map.entry("bgm", List.of("bcg")),
			Map.entry("bh", List.of("bih")),
			Map.entry("bic", List.of("bir")),
			Map.entry("bih", List.of("bh")),
			Map.entry("bir", List.of("bic")),
			Map.entry("bjd", List.of("drl")),
			Map.entry("bjn", List.of("ms-bjn")),
			Map.entry("blg", List.of("iba", "snb")),
			Map.entry("bmf", List.of("krm")),
			Map.entry("bnn", List.of("i-bnn")),
			Map.entry("bog", List.of("sgn-bog")),
			Map.entry("bpp", List.of("nxu")),
			Map.entry("bqn", List.of("sgn-bqn")),
			Map.entry("bqy", List.of("sgn-bqy")),
			Map.entry("btj", List.of("ms-btj")),
			Map.entry("bve", List.of("ms-bve")),
			Map.entry("bvl", List.of("sgn-bvl")),
			Map.entry("bvu", List.of("ms-bvu")),
			Map.entry("bzs", List.of("sgn-bzs", "sgn-br")),
			Map.entry("cax", List.of("xba")),
			Map.entry("cbr", List.of("nom")),
			Map.entry("ccq", List.of("rki", "ybd")),
			Map.entry("cdo", List.of("zh-cdo")),
			Map.entry("cds", List.of("sgn-cds")),
			Map.entry("cir", List.of("meg")),
			Map.entry("cjr", List.of("mom")),
			Map.entry("cjy", List.of("zh-cjy")),
			Map.entry("cka", List.of("cmr")),
			Map.entry("cmk", List.of("xch")),
			Map.entry("cmn", List.of("zh-cmn", "zh-guoyu")),
			Map.entry("cmn-hans", List.of("zh-cmn-hans")),
			Map.entry("cmn-hant", List.of("zh-cmn-hant")),
			Map.entry("cmr", List.of("cka")),
			Map.entry("cnp", List.of("zh-cnp")),
			Map.entry("coa", List.of("ms-coa")),
			Map.entry("coy", List.of("pij", "nts")),
			Map.entry("cpx", List.of("zh-cpx")),
			Map.entry("cqu", List.of("quh")),
			Map.entry("crr", List.of("pmk")),
			Map.entry("csc", List.of("sgn-csc")),
			Map.entry("csd", List.of("sgn-csd")),
			Map.entry("cse", List.of("sgn-cse")),
			Map.entry("csf", List.of("sgn-csf")),
			Map.entry("csg", List.of("sgn-csg")),
			Map.entry("csl", List.of("sgn-csl")),
			Map.entry("csn", List.of("sgn-csn", "sgn-co")),
			Map.entry("csp", List.of("zh-csp")),
			Map.entry("csq", List.of("sgn-csq")),
			Map.entry("csr", List.of("sgn-csr")),
			Map.entry("csx", List.of("sgn-csx")),
			Map.entry("czh", List.of("zh-czh")),
			Map.entry("czo", List.of("zh-czo")),
			Map.entry("dek", List.of("sqm")),
			Map.entry("dev", List.of("gav")),
			Map.entry("dif", List.of("dit")),
			Map.entry("dit", List.of("dif")),
			Map.entry("dmw", List.of("xrq")),
			Map.entry("doq", List.of("sgn-doq")),
			Map.entry("drh", List.of("khk")),
			Map.entry("drl", List.of("bjd")),
			Map.entry("drr", List.of("kzk", "gli")),
			Map.entry("drw", List.of("prs", "tnf")),
			Map.entry("dse", List.of("sgn-dse", "sgn-nl")),
			Map.entry("dsl", List.of("sgn-dsl", "sgn-dk")),
			Map.entry("dsz", List.of("sgn-dsz")),
			Map.entry("dtp", List.of("ktr", "kzj", "kzt", "tdu")),
			Map.entry("dup", List.of("ms-dup")),
			Map.entry("duz", List.of("guv")),
			Map.entry("dyl", List.of("sgn-dyl")),
			Map.entry("dz", List.of("adp")),
			Map.entry("ecs", List.of("sgn-ecs")),
			Map.entry("ehs", List.of("sgn-ehs")),
			Map.entry("eko", List.of("nte")),
			Map.entry("ema", List.of("uok")),
			Map.entry("en-gb-oed", List.of("en-gb-oxendict")),
			Map.entry("en-gb-oxendict", List.of("en-gb-oed")),
			Map.entry("enm", List.of("yol")),
			Map.entry("esl", List.of("sgn-esl")),
			Map.entry("esn", List.of("sgn-esn")),
			Map.entry("eso", List.of("sgn-eso")),
			Map.entry("eth", List.of("sgn-eth")),
			Map.entry("fcs", List.of("sgn-fcs")),
			Map.entry("fse", List.of("sgn-fse")),
			Map.entry("fsl", List.of("sgn-fsl", "sgn-fr")),
			Map.entry("fss", List.of("sgn-fss")),
			Map.entry("gal", List.of("ilw")),
			Map.entry("gan", List.of("zh-gan")),
			Map.entry("gav", List.of("dev")),
			Map.entry("gdj", List.of("kvs")),
			Map.entry("gds", List.of("sgn-gds")),
			Map.entry("gfx", List.of("vaj", "mwj", "oun")),
			Map.entry("ggn", List.of("gvr")),
			Map.entry("gli", List.of("kzk", "drr")),
			Map.entry("gom", List.of("kok-gom")),
			Map.entry("gse", List.of("sgn-gse")),
			Map.entry("gsg", List.of("sgn-gsg", "sgn-de")),
			Map.entry("gsm", List.of("sgn-gsm")),
			Map.entry("gss", List.of("sgn-gss", "sgn-gr")),
			Map.entry("gti", List.of("nyc")),
			Map.entry("gu", List.of("prp")),
			Map.entry("gus", List.of("sgn-gus")),
			Map.entry("guv", List.of("duz")),
			Map.entry("gvr", List.of("ggn")),
			Map.entry("hab", List.of("sgn-hab")),
			Map.entry("haf", List.of("sgn-haf")),
			Map.entry("hak", List.of("zh-hak", "i-hak", "zh-hakka")),
			Map.entry("hds", List.of("sgn-hds")),
			Map.entry("he", List.of("iw")),
			Map.entry("hji", List.of("ms-hji")),
			Map.entry("hks", List.of("sgn-hks")),
			Map.entry("hle", List.of("sca")),
			Map.entry("hnm", List.of("zh-hnm")),
			Map.entry("hos", List.of("sgn-hos")),
			Map.entry("hps", List.of("sgn-hps")),
			Map.entry("hrr", List.of("jal")),
			Map.entry("hsh", List.of("sgn-hsh")),
			Map.entry("hsl", List.of("sgn-hsl")),
			Map.entry("hsn", List.of("zh-hsn", "zh-xiang")),
			Map.entry("huw", List.of("pmc")),
			Map.entry("i-ami", List.of("ami")),
			Map.entry("i-bnn", List.of("bnn")),
			Map.entry("i-hak", List.of("hak", "zh-hak", "zh-hakka")),
			Map.entry("i-klingon", List.of("tlh")),
			Map.entry("i-lux", List.of("lb")),
			Map.entry("i-navajo", List.of("nv")),
			Map.entry("i-pwn", List.of("pwn")),
			Map.entry("i-tao", List.of("tao")),
			Map.entry("i-tay", List.of("tay")),
			Map.entry("i-tsu", List.of("tsu")),
			Map.entry("iba", List.of("blg", "snb")),
			Map.entry("ibi", List.of("opa")),
			Map.entry("icl", List.of("sgn-icl")),
			Map.entry("id", List.of("in")),
			Map.entry("iks", List.of("sgn-iks")),
			Map.entry("ils", List.of("sgn-ils")),
			Map.entry("ilw", List.of("gal")),
			Map.entry("in", List.of("id")),
			Map.entry("inl", List.of("sgn-inl")),
			Map.entry("ins", List.of("sgn-ins")),
			Map.entry("ise", List.of("sgn-ise", "sgn-it")),
			Map.entry("isg", List.of("sgn-isg", "sgn-ie")),
			Map.entry("isr", List.of("sgn-isr")),
			Map.entry("iw", List.of("he")),
			Map.entry("jak", List.of("ms-jak")),
			Map.entry("jal", List.of("hrr")),
			Map.entry("jax", List.of("ms-jax")),
			Map.entry("jbo", List.of("art-lojban")),
			Map.entry("jcs", List.of("sgn-jcs")),
			Map.entry("jeg", List.of("oyb", "skk", "thx")),
			Map.entry("jhs", List.of("sgn-jhs")),
			Map.entry("ji", List.of("yi")),
			Map.entry("jks", List.of("sgn-jks")),
			Map.entry("jls", List.of("sgn-jls")),
			Map.entry("jos", List.of("sgn-jos")),
			Map.entry("jsl", List.of("sgn-jsl", "sgn-jp")),
			Map.entry("jus", List.of("sgn-jus")),
			Map.entry("jv", List.of("jw")),
			Map.entry("jw", List.of("jv")),
			Map.entry("kak", List.of("tne")),
			Map.entry("kdz", List.of("ncp")),
			Map.entry("kgc", List.of("tdf")),
			Map.entry("kgh", List.of("kml")),
			Map.entry("kgi", List.of("sgn-kgi")),
			Map.entry("kgm", List.of("plu")),
			Map.entry("khk", List.of("drh")),
			Map.entry("kjh", List.of("zkb")),
			Map.entry("kmb", List.of("smd")),
			Map.entry("kml", List.of("kgh")),
			Map.entry("knn", List.of("kok-knn")),
			Map.entry("koj", List.of("kwv")),
			Map.entry("kok-gom", List.of("gom")),
			Map.entry("kok-knn", List.of("knn")),
			Map.entry("krm", List.of("bmf")),
			Map.entry("kru", List.of("kxl")),
			Map.entry("ksp", List.of("lak")),
			Map.entry("ktr", List.of("dtp", "kzj", "kzt", "tdu")),
			Map.entry("ktz", List.of("aue")),
			Map.entry("kvb", List.of("ms-kvb")),
			Map.entry("kvk", List.of("sgn-kvk")),
			Map.entry("kvr", List.of("ms-kvr")),
			Map.entry("kvs", List.of("gdj")),
			Map.entry("kwq", List.of("yam")),
			Map.entry("kwv", List.of("koj")),
			Map.entry("kxd", List.of("ms-kxd")),
			Map.entry("kxe", List.of("tvd")),
			Map.entry("kxl", List.of("kru")),
			Map.entry("kxr", List.of("pat")),
			Map.entry("kzj", List.of("dtp", "ktr", "kzt", "tdu")),
			Map.entry("kzk", List.of("drr", "gli")),
			Map.entry("kzt", List.of("dtp", "ktr", "kzj", "tdu")),
			Map.entry("lak", List.of("ksp")),
			Map.entry("lb", List.of("i-lux")),
			Map.entry("lbs", List.of("sgn-lbs")),
			Map.entry("lce", List.of("ms-lce")),
			Map.entry("lcf", List.of("ms-lcf")),
			Map.entry("lcq", List.of("ppr")),
			Map.entry("lgs", List.of("sgn-lgs")),
			Map.entry("lii", List.of("raq")),
			Map.entry("liw", List.of("ms-liw")),
			Map.entry("llo", List.of("ngt")),
			Map.entry("lls", List.of("sgn-lls")),
			Map.entry("lmm", List.of("rmx")),
			Map.entry("lrr", List.of("yma")),
			Map.entry("lsb", List.of("sgn-lsb")),
			Map.entry("lsc", List.of("sgn-lsc")),
			Map.entry("lsg", List.of("sgn-lsg")),
			Map.entry("lsl", List.of("sgn-lsl")),
			Map.entry("lsn", List.of("sgn-lsn")),
			Map.entry("lso", List.of("sgn-lso")),
			Map.entry("lsp", List.of("sgn-lsp")),
			Map.entry("lst", List.of("sgn-lst")),
			Map.entry("lsv", List.of("sgn-lsv")),
			Map.entry("lsw", List.of("sgn-lsw")),
			Map.entry("lsy", List.of("sgn-lsy")),
			Map.entry("ltg", List.of("lv-ltg")),
			Map.entry("luh", List.of("zh-luh")),
			Map.entry("lv-ltg", List.of("ltg")),
			Map.entry("lv-lvs", List.of("lvs")),
			Map.entry("lvs", List.of("lv-lvs")),
			Map.entry("lws", List.of("sgn-lws")),
			Map.entry("lzh", List.of("zh-lzh")),
			Map.entry("max", List.of("ms-max")),
			Map.entry("mdl", List.of("sgn-mdl")),
			Map.entry("meg", List.of("cir")),
			Map.entry("meo", List.of("ms-meo")),
			Map.entry("mfa", List.of("ms-mfa")),
			Map.entry("mfb", List.of("ms-mfb")),
			Map.entry("mfs", List.of("sgn-mfs", "sgn-mx")),
			Map.entry("mgp", List.of("mrd")),
			Map.entry("min", List.of("ms-min")),
			Map.entry("mnp", List.of("zh-mnp")),
			Map.entry("mo", List.of("ro")),
			Map.entry("mom", List.of("cjr")),
			Map.entry("mqg", List.of("ms-mqg")),
			Map.entry("mrd", List.of("mgp")),
			Map.entry("mre", List.of("sgn-mre")),
			Map.entry("mrh", List.of("shl")),
			Map.entry("mry", List.of("mst", "myt")),
			Map.entry("ms-bjn", List.of("bjn")),
			Map.entry("ms-btj", List.of("btj")),
			Map.entry("ms-bve", List.of("bve")),
			Map.entry("ms-bvu", List.of("bvu")),
			Map.entry("ms-coa", List.of("coa")),
			Map.entry("ms-dup", List.of("dup")),
			Map.entry("ms-hji", List.of("hji")),
			Map.entry("ms-jak", List.of("jak")),
			Map.entry("ms-jax", List.of("jax")),
			Map.entry("ms-kvb", List.of("kvb")),
			Map.entry("ms-kvr", List.of("kvr")),
			Map.entry("ms-kxd", List.of("kxd")),
			Map.entry("ms-lce", List.of("lce")),
			Map.entry("ms-lcf", List.of("lcf")),
			Map.entry("ms-liw", List.of("liw")),
			Map.entry("ms-max", List.of("max")),
			Map.entry("ms-meo", List.of("meo")),
			Map.entry("ms-mfa", List.of("mfa")),
			Map.entry("ms-mfb", List.of("mfb")),
			Map.entry("ms-min", List.of("min")),
			Map.entry("ms-mqg", List.of("mqg")),
			Map.entry("ms-msi", List.of("msi")),
			Map.entry("ms-mui", List.of("mui")),
			Map.entry("ms-orn", List.of("orn")),
			Map.entry("ms-ors", List.of("ors")),
			Map.entry("ms-pel", List.of("pel")),
			Map.entry("ms-pse", List.of("pse")),
			Map.entry("ms-tmw", List.of("tmw")),
			Map.entry("ms-urk", List.of("urk")),
			Map.entry("ms-vkk", List.of("vkk")),
			Map.entry("ms-vkt", List.of("vkt")),
			Map.entry("ms-xmm", List.of("xmm")),
			Map.entry("ms-zlm", List.of("zlm")),
			Map.entry("ms-zmi", List.of("zmi")),
			Map.entry("ms-zsm", List.of("zsm")),
			Map.entry("msd", List.of("sgn-msd")),
			Map.entry("msi", List.of("ms-msi")),
			Map.entry("msr", List.of("sgn-msr")),
			Map.entry("mst", List.of("mry", "myt")),
			Map.entry("mtm", List.of("ymt")),
			Map.entry("mui", List.of("ms-mui")),
			Map.entry("mwj", List.of("vaj", "gfx", "oun")),
			Map.entry("myd", List.of("aog")),
			Map.entry("myt", List.of("mry", "mst")),
			Map.entry("mzc", List.of("sgn-mzc")),
			Map.entry("mzg", List.of("sgn-mzg")),
			Map.entry("mzy", List.of("sgn-mzy")),
			Map.entry("nad", List.of("xny")),
			Map.entry("nan", List.of("zh-nan", "zh-min-nan")),
			Map.entry("nb", List.of("no-bok")),
			Map.entry("nbr", List.of("nns")),
			Map.entry("nbs", List.of("sgn-nbs")),
			Map.entry("ncp", List.of("kdz")),
			Map.entry("ncs", List.of("sgn-ncs", "sgn-ni")),
			Map.entry("ngt", List.of("llo")),
			Map.entry("ngv", List.of("nnx")),
			Map.entry("nn", List.of("no-nyn")),
			Map.entry("nns", List.of("nbr")),
			Map.entry("nnx", List.of("ngv")),
			Map.entry("no-bok", List.of("nb")),
			Map.entry("no-nyn", List.of("nn")),
			Map.entry("nom", List.of("cbr")),
			Map.entry("nsi", List.of("sgn-nsi")),
			Map.entry("nsl", List.of("sgn-nsl", "sgn-no")),
			Map.entry("nsp", List.of("sgn-nsp")),
			Map.entry("nsr", List.of("sgn-nsr")),
			Map.entry("nte", List.of("eko")),
			Map.entry("nts", List.of("pij", "coy")),
			Map.entry("nun", List.of("ayx")),
			Map.entry("nv", List.of("i-navajo")),
			Map.entry("nxu", List.of("bpp")),
			Map.entry("nyc", List.of("gti")),
			Map.entry("nzs", List.of("sgn-nzs")),
			Map.entry("okl", List.of("sgn-okl")),
			Map.entry("ola", List.of("thw")),
			Map.entry("opa", List.of("ibi")),
			Map.entry("orn", List.of("ms-orn")),
			Map.entry("ors", List.of("ms-ors")),
			Map.entry("oun", List.of("vaj", "gfx", "mwj")),
			Map.entry("oyb", List.of("jeg", "skk", "thx")),
			Map.entry("pat", List.of("kxr")),
			Map.entry("pcr", List.of("adx")),
			Map.entry("pel", List.of("ms-pel")),
			Map.entry("pga", List.of("ar-pga")),
			Map.entry("pgz", List.of("sgn-pgz")),
			Map.entry("phr", List.of("pmu")),
			Map.entry("pij", List.of("coy", "nts")),
			Map.entry("pks", List.of("sgn-pks")),
			Map.entry("plu", List.of("kgm")),
			Map.entry("pmc", List.of("huw")),
			Map.entry("pmk", List.of("crr")),
			Map.entry("pmu", List.of("phr")),
			Map.entry("ppa", List.of("bfy")),
			Map.entry("ppr", List.of("lcq")),
			Map.entry("prl", List.of("sgn-prl")),
			Map.entry("prp", List.of("gu")),
			Map.entry("prs", List.of("drw", "tnf")),
			Map.entry("prt", List.of("pry")),
			Map.entry("pry", List.of("prt")),
			Map.entry("prz", List.of("sgn-prz")),
			Map.entry("psc", List.of("sgn-psc")),
			Map.entry("psd", List.of("sgn-psd")),
			Map.entry("pse", List.of("ms-pse")),
			Map.entry("psg", List.of("sgn-psg")),
			Map.entry("psl", List.of("sgn-psl")),
			Map.entry("pso", List.of("sgn-pso")),
			Map.entry("psp", List.of("sgn-psp")),
			Map.entry("psr", List.of("sgn-psr", "sgn-pt")),
			Map.entry("pub", List.of("puz")),
			Map.entry("puz", List.of("pub")),
			Map.entry("pwn", List.of("i-pwn")),
			Map.entry("pys", List.of("sgn-pys")),
			Map.entry("quh", List.of("cqu")),
			Map.entry("raq", List.of("lii")),
			Map.entry("ras", List.of("tie")),
			Map.entry("rib", List.of("sgn-rib")),
			Map.entry("rki", List.of("ccq", "ybd")),
			Map.entry("rms", List.of("sgn-rms")),
			Map.entry("rmx", List.of("lmm")),
			Map.entry("rnb", List.of("sgn-rnb")),
			Map.entry("ro", List.of("mo")),
			Map.entry("rsi", List.of("sgn-rsi")),
			Map.entry("rsl", List.of("sgn-rsl")),
			Map.entry("rsm", List.of("sgn-rsm")),
			Map.entry("rsn", List.of("sgn-rsn")),
			Map.entry("sca", List.of("hle")),
			Map.entry("scv", List.of("zir")),
			Map.entry("sdl", List.of("sgn-sdl")),
			Map.entry("sfb", List.of("sgn-sfb", "sgn-be-fr")),
			Map.entry("sfs", List.of("sgn-sfs", "sgn-za")),
			Map.entry("sgg", List.of("sgn-sgg", "sgn-ch-de")),
			Map.entry("sgn-ads", List.of("ads")),
			Map.entry("sgn-aed", List.of("aed")),
			Map.entry("sgn-aen", List.of("aen")),
			Map.entry("sgn-afg", List.of("afg")),
			Map.entry("sgn-ajs", List.of("ajs")),
			Map.entry("sgn-ase", List.of("ase", "sgn-us")),
			Map.entry("sgn-asf", List.of("asf")),
			Map.entry("sgn-asp", List.of("asp")),
			Map.entry("sgn-asq", List.of("asq")),
			Map.entry("sgn-asw", List.of("asw")),
			Map.entry("sgn-be-fr", List.of("sfb", "sgn-sfb")),
			Map.entry("sgn-be-nl", List.of("vgt", "sgn-vgt")),
			Map.entry("sgn-bfi", List.of("bfi", "sgn-gb")),
			Map.entry("sgn-bfk", List.of("bfk")),
			Map.entry("sgn-bog", List.of("bog")),
			Map.entry("sgn-bqn", List.of("bqn")),
			Map.entry("sgn-bqy", List.of("bqy")),
			Map.entry("sgn-br", List.of("bzs", "sgn-bzs")),
			Map.entry("sgn-bvl", List.of("bvl")),
			Map.entry("sgn-bzs", List.of("bzs", "sgn-br")),
			Map.entry("sgn-cds", List.of("cds")),
			Map.entry("sgn-ch-de", List.of("sgg", "sgn-sgg")),
			Map.entry("sgn-co", List.of("csn", "sgn-csn")),
			Map.entry("sgn-csc", List.of("csc")),
			Map.entry("sgn-csd", List.of("csd")),
			Map.entry("sgn-cse", List.of("cse")),
			Map.entry("sgn-csf", List.of("csf")),
			Map.entry("sgn-csg", List.of("csg")),
			Map.entry("sgn-csl", List.of("csl")),
			Map.entry("sgn-csn", List.of("csn", "sgn-co")),
			Map.entry("sgn-csq", List.of("csq")),
			Map.entry("sgn-csr", List.of("csr")),
			Map.entry("sgn-csx", List.of("csx")),
			Map.entry("sgn-de", List.of("gsg", "sgn-gsg")),
			Map.entry("sgn-dk", List.of("dsl", "sgn-dsl")),
			Map.entry("sgn-doq", List.of("doq")),
			Map.entry("sgn-dse", List.of("dse", "sgn-nl")),
			Map.entry("sgn-dsl", List.of("dsl", "sgn-dk")),
			Map.entry("sgn-dsz", List.of("dsz")),
			Map.entry("sgn-dyl", List.of("dyl")),
			Map.entry("sgn-ecs", List.of("ecs")),
			Map.entry("sgn-ehs", List.of("ehs")),
			Map.entry("sgn-es", List.of("ssp", "sgn-ssp")),
			Map.entry("sgn-esl", List.of("esl")),
			Map.entry("sgn-esn", List.of("esn")),
			Map.entry("sgn-eso", List.of("eso")),
			Map.entry("sgn-eth", List.of("eth")),
			Map.entry("sgn-fcs", List.of("fcs")),
			Map.entry("sgn-fr", List.of("fsl", "sgn-fsl")),
			Map.entry("sgn-fse", List.of("fse")),
			Map.entry("sgn-fsl", List.of("fsl", "sgn-fr")),
			Map.entry("sgn-fss", List.of("fss")),
			Map.entry("sgn-gb", List.of("bfi", "sgn-bfi")),
			Map.entry("sgn-gds", List.of("gds")),
			Map.entry("sgn-gr", List.of("gss", "sgn-gss")),
			Map.entry("sgn-gse", List.of("gse")),
			Map.entry("sgn-gsg", List.of("gsg", "sgn-de")),
			Map.entry("sgn-gsm", List.of("gsm")),
			Map.entry("sgn-gss", List.of("gss", "sgn-gr")),
			Map.entry("sgn-gus", List.of("gus")),
			Map.entry("sgn-hab", List.of("hab")),
			Map.entry("sgn-haf", List.of("haf")),
			Map.entry("sgn-hds", List.of("hds")),
			Map.entry("sgn-hks", List.of("hks")),
			Map.entry("sgn-hos", List.of("hos")),
			Map.entry("sgn-hps", List.of("hps")),
			Map.entry("sgn-hsh", List.of("hsh")),
			Map.entry("sgn-hsl", List.of("hsl")),
			Map.entry("sgn-icl", List.of("icl")),
			Map.entry("sgn-ie", List.of("isg", "sgn-isg")),
			Map.entry("sgn-iks", List.of("iks")),
			Map.entry("sgn-ils", List.of("ils")),
			Map.entry("sgn-inl", List.of("inl")),
			Map.entry("sgn-ins", List.of("ins")),
			Map.entry("sgn-ise", List.of("ise", "sgn-it")),
			Map.entry("sgn-isg", List.of("isg", "sgn-ie")),
			Map.entry("sgn-isr", List.of("isr")),
			Map.entry("sgn-it", List.of("ise", "sgn-ise")),
			Map.entry("sgn-jcs", List.of("jcs")),
			Map.entry("sgn-jhs", List.of("jhs")),
			Map.entry("sgn-jks", List.of("jks")),
			Map.entry("sgn-jls", List.of("jls")),
			Map.entry("sgn-jos", List.of("jos")),
			Map.entry("sgn-jp", List.of("jsl", "sgn-jsl")),
			Map.entry("sgn-jsl", List.of("jsl", "sgn-jp")),
			Map.entry("sgn-jus", List.of("jus")),
			Map.entry("sgn-kgi", List.of("kgi")),
			Map.entry("sgn-kvk", List.of("kvk")),
			Map.entry("sgn-lbs", List.of("lbs")),
			Map.entry("sgn-lgs", List.of("lgs")),
			Map.entry("sgn-lls", List.of("lls")),
			Map.entry("sgn-lsb", List.of("lsb")),
			Map.entry("sgn-lsc", List.of("lsc")),
			Map.entry("sgn-lsg", List.of("lsg")),
			Map.entry("sgn-lsl", List.of("lsl")),
			Map.entry("sgn-lsn", List.of("lsn")),
			Map.entry("sgn-lso", List.of("lso")),
			Map.entry("sgn-lsp", List.of("lsp")),
			Map.entry("sgn-lst", List.of("lst")),
			Map.entry("sgn-lsv", List.of("lsv")),
			Map.entry("sgn-lsw", List.of("lsw")),
			Map.entry("sgn-lsy", List.of("lsy")),
			Map.entry("sgn-lws", List.of("lws")),
			Map.entry("sgn-mdl", List.of("mdl")),
			Map.entry("sgn-mfs", List.of("mfs", "sgn-mx")),
			Map.entry("sgn-mre", List.of("mre")),
			Map.entry("sgn-msd", List.of("msd")),
			Map.entry("sgn-msr", List.of("msr")),
			Map.entry("sgn-mx", List.of("mfs", "sgn-mfs")),
			Map.entry("sgn-mzc", List.of("mzc")),
			Map.entry("sgn-mzg", List.of("mzg")),
			Map.entry("sgn-mzy", List.of("mzy")),
			Map.entry("sgn-nbs", List.of("nbs")),
			Map.entry("sgn-ncs", List.of("ncs", "sgn-ni")),
			Map.entry("sgn-ni", List.of("ncs", "sgn-ncs")),
			Map.entry("sgn-nl", List.of("dse", "sgn-dse")),
			Map.entry("sgn-no", List.of("nsl", "sgn-nsl")),
			Map.entry("sgn-nsi", List.of("nsi")),
			Map.entry("sgn-nsl", List.of("nsl", "sgn-no")),
			Map.entry("sgn-nsp", List.of("nsp")),
			Map.entry("sgn-nsr", List.of("nsr")),
			Map.entry("sgn-nzs", List.of("nzs")),
			Map.entry("sgn-okl", List.of("okl")),
			Map.entry("sgn-pgz", List.of("pgz")),
			Map.entry("sgn-pks", List.of("pks")),
			Map.entry("sgn-prl", List.of("prl")),
			Map.entry("sgn-prz", List.of("prz")),
			Map.entry("sgn-psc", List.of("psc")),
			Map.entry("sgn-psd", List.of("psd")),
			Map.entry("sgn-psg", List.of("psg")),
			Map.entry("sgn-psl", List.of("psl")),
			Map.entry("sgn-pso", List.of("pso")),
			Map.entry("sgn-psp", List.of("psp")),
			Map.entry("sgn-psr", List.of("psr", "sgn-pt")),
			Map.entry("sgn-pt", List.of("psr", "sgn-psr")),
			Map.entry("sgn-pys", List.of("pys")),
			Map.entry("sgn-rib", List.of("rib")),
			Map.entry("sgn-rms", List.of("rms")),
			Map.entry("sgn-rnb", List.of("rnb")),
			Map.entry("sgn-rsi", List.of("rsi")),
			Map.entry("sgn-rsl", List.of("rsl")),
			Map.entry("sgn-rsm", List.of("rsm")),
			Map.entry("sgn-rsn", List.of("rsn")),
			Map.entry("sgn-sdl", List.of("sdl")),
			Map.entry("sgn-se", List.of("swl", "sgn-swl")),
			Map.entry("sgn-sfb", List.of("sfb", "sgn-be-fr")),
			Map.entry("sgn-sfs", List.of("sfs", "sgn-za")),
			Map.entry("sgn-sgg", List.of("sgg", "sgn-ch-de")),
			Map.entry("sgn-sgx", List.of("sgx")),
			Map.entry("sgn-slf", List.of("slf")),
			Map.entry("sgn-sls", List.of("sls")),
			Map.entry("sgn-sqk", List.of("sqk")),
			Map.entry("sgn-sqs", List.of("sqs")),
			Map.entry("sgn-sqx", List.of("sqx")),
			Map.entry("sgn-ssp", List.of("ssp", "sgn-es")),
			Map.entry("sgn-ssr", List.of("ssr")),
			Map.entry("sgn-svk", List.of("svk")),
			Map.entry("sgn-swl", List.of("swl", "sgn-se")),
			Map.entry("sgn-syy", List.of("syy")),
			Map.entry("sgn-szs", List.of("szs")),
			Map.entry("sgn-tse", List.of("tse")),
			Map.entry("sgn-tsm", List.of("tsm")),
			Map.entry("sgn-tsq", List.of("tsq")),
			Map.entry("sgn-tss", List.of("tss")),
			Map.entry("sgn-tsy", List.of("tsy")),
			Map.entry("sgn-tza", List.of("tza")),
			Map.entry("sgn-ugn", List.of("ugn")),
			Map.entry("sgn-ugy", List.of("ugy")),
			Map.entry("sgn-ukl", List.of("ukl")),
			Map.entry("sgn-uks", List.of("uks")),
			Map.entry("sgn-us", List.of("ase", "sgn-ase")),
			Map.entry("sgn-vgt", List.of("vgt", "sgn-be-nl")),
			Map.entry("sgn-vsi", List.of("vsi")),
			Map.entry("sgn-vsl", List.of("vsl")),
			Map.entry("sgn-vsv", List.of("vsv")),
			Map.entry("sgn-wbs", List.of("wbs")),
			Map.entry("sgn-xki", List.of("xki")),
			Map.entry("sgn-xml", List.of("xml")),
			Map.entry("sgn-xms", List.of("xms")),
			Map.entry("sgn-yds", List.of("yds")),
			Map.entry("sgn-ygs", List.of("ygs")),
			Map.entry("sgn-yhs", List.of("yhs")),
			Map.entry("sgn-ysl", List.of("ysl")),
			Map.entry("sgn-ysm", List.of("ysm")),
			Map.entry("sgn-za", List.of("sfs", "sgn-sfs")),
			Map.entry("sgn-zhk", List.of("zhk")),
			Map.entry("sgn-zib", List.of("zib")),
			Map.entry("sgn-zsl", List.of("zsl")),
			Map.entry("sgx", List.of("sgn-sgx")),
			Map.entry("shl", List.of("mrh")),
			Map.entry("shu", List.of("ar-shu")),
			Map.entry("sjc", List.of("zh-sjc")),
			Map.entry("skk", List.of("oyb", "jeg", "thx")),
			Map.entry("slf", List.of("sgn-slf")),
			Map.entry("sls", List.of("sgn-sls")),
			Map.entry("smd", List.of("kmb")),
			Map.entry("snb", List.of("iba", "blg")),
			Map.entry("snz", List.of("asd")),
			Map.entry("sqk", List.of("sgn-sqk")),
			Map.entry("sqm", List.of("dek")),
			Map.entry("sqs", List.of("sgn-sqs")),
			Map.entry("sqx", List.of("sgn-sqx")),
			Map.entry("ssh", List.of("ar-ssh")),
			Map.entry("ssp", List.of("sgn-ssp", "sgn-es")),
			Map.entry("ssr", List.of("sgn-ssr")),
			Map.entry("svk", List.of("sgn-svk")),
			Map.entry("sw-swc", List.of("swc")),
			Map.entry("sw-swh", List.of("swh")),
			Map.entry("swc", List.of("sw-swc")),
			Map.entry("swh", List.of("sw-swh")),
			Map.entry("swl", List.of("sgn-swl", "sgn-se")),
			Map.entry("syy", List.of("sgn-syy")),
			Map.entry("szd", List.of("umi")),
			Map.entry("szs", List.of("sgn-szs")),
			Map.entry("taj", List.of("tsf")),
			Map.entry("tao", List.of("i-tao")),
			Map.entry("tay", List.of("i-tay")),
			Map.entry("tdf", List.of("kgc")),
			Map.entry("tdg", List.of("tmk")),
			Map.entry("tdu", List.of("dtp", "ktr", "kzj", "kzt")),
			Map.entry("thc", List.of("tpo")),
			Map.entry("thw", List.of("ola")),
			Map.entry("thx", List.of("oyb", "jeg", "skk")),
			Map.entry("tie", List.of("ras")),
			Map.entry("tkk", List.of("twm")),
			Map.entry("tlh", List.of("i-klingon")),
			Map.entry("tlw", List.of("weo")),
			Map.entry("tmk", List.of("tdg")),
			Map.entry("tmp", List.of("tyj")),
			Map.entry("tmw", List.of("ms-tmw")),
			Map.entry("tne", List.of("kak")),
			Map.entry("tnf", List.of("prs", "drw")),
			Map.entry("tpn", List.of("tpw")),
			Map.entry("tpo", List.of("thc")),
			Map.entry("tpw", List.of("tpn")),
			Map.entry("tse", List.of("sgn-tse")),
			Map.entry("tsf", List.of("taj")),
			Map.entry("tsm", List.of("sgn-tsm")),
			Map.entry("tsq", List.of("sgn-tsq")),
			Map.entry("tss", List.of("sgn-tss")),
			Map.entry("tsu", List.of("i-tsu")),
			Map.entry("tsy", List.of("sgn-tsy")),
			Map.entry("tvd", List.of("kxe")),
			Map.entry("twm", List.of("tkk")),
			Map.entry("tyj", List.of("tmp")),
			Map.entry("tza", List.of("sgn-tza")),
			Map.entry("ugn", List.of("sgn-ugn")),
			Map.entry("ugy", List.of("sgn-ugy")),
			Map.entry("ukl", List.of("sgn-ukl")),
			Map.entry("uks", List.of("sgn-uks")),
			Map.entry("umi", List.of("szd")),
			Map.entry("uok", List.of("ema")),
			Map.entry("urk", List.of("ms-urk")),
			Map.entry("uz-uzn", List.of("uzn")),
			Map.entry("uz-uzs", List.of("uzs")),
			Map.entry("uzn", List.of("uz-uzn")),
			Map.entry("uzs", List.of("uz-uzs")),
			Map.entry("vaj", List.of("gfx", "mwj", "oun")),
			Map.entry("vgt", List.of("sgn-vgt", "sgn-be-nl")),
			Map.entry("vkk", List.of("ms-vkk")),
			Map.entry("vkt", List.of("ms-vkt")),
			Map.entry("vsi", List.of("sgn-vsi")),
			Map.entry("vsl", List.of("sgn-vsl")),
			Map.entry("vsv", List.of("sgn-vsv")),
			Map.entry("waw", List.of("xkh")),
			Map.entry("wbs", List.of("sgn-wbs")),
			Map.entry("weo", List.of("tlw")),
			Map.entry("wuu", List.of("zh-wuu")),
			Map.entry("xba", List.of("cax")),
			Map.entry("xch", List.of("cmk")),
			Map.entry("xia", List.of("acn")),
			Map.entry("xkh", List.of("waw")),
			Map.entry("xki", List.of("sgn-xki")),
			Map.entry("xml", List.of("sgn-xml")),
			Map.entry("xmm", List.of("ms-xmm")),
			Map.entry("xms", List.of("sgn-xms")),
			Map.entry("xny", List.of("nad")),
			Map.entry("xrq", List.of("dmw")),
			Map.entry("xss", List.of("zko")),
			Map.entry("yam", List.of("kwq")),
			Map.entry("ybd", List.of("rki", "ccq")),
			Map.entry("yds", List.of("sgn-yds")),
			Map.entry("ygs", List.of("sgn-ygs")),
			Map.entry("yhs", List.of("sgn-yhs")),
			Map.entry("yi", List.of("ji")),
			Map.entry("yma", List.of("lrr")),
			Map.entry("ymt", List.of("mtm")),
			Map.entry("yol", List.of("enm")),
			Map.entry("yos", List.of("zom")),
			Map.entry("ysl", List.of("sgn-ysl")),
			Map.entry("ysm", List.of("sgn-ysm")),
			Map.entry("yue", List.of("zh-yue")),
			Map.entry("yug", List.of("yuu")),
			Map.entry("yuu", List.of("yug")),
			Map.entry("zh-cdo", List.of("cdo")),
			Map.entry("zh-cjy", List.of("cjy")),
			Map.entry("zh-cmn", List.of("cmn", "zh-guoyu")),
			Map.entry("zh-cmn-hans", List.of("cmn-hans")),
			Map.entry("zh-cmn-hant", List.of("cmn-hant")),
			Map.entry("zh-cnp", List.of("cnp")),
			Map.entry("zh-cpx", List.of("cpx")),
			Map.entry("zh-csp", List.of("csp")),
			Map.entry("zh-czh", List.of("czh")),
			Map.entry("zh-czo", List.of("czo")),
			Map.entry("zh-gan", List.of("gan")),
			Map.entry("zh-guoyu", List.of("cmn", "zh-cmn")),
			Map.entry("zh-hak", List.of("hak", "i-hak", "zh-hakka")),
			Map.entry("zh-hakka", List.of("hak", "zh-hak", "i-hak")),
			Map.entry("zh-hnm", List.of("hnm")),
			Map.entry("zh-hsn", List.of("hsn", "zh-xiang")),
			Map.entry("zh-luh", List.of("luh")),
			Map.entry("zh-lzh", List.of("lzh")),
			Map.entry("zh-min-nan", List.of("nan", "zh-nan")),
			Map.entry("zh-mnp", List.of("mnp")),
			Map.entry("zh-nan", List.of("nan", "zh-min-nan")),
			Map.entry("zh-sjc", List.of("sjc")),
			Map.entry("zh-wuu", List.of("wuu")),
			Map.entry("zh-xiang", List.of("hsn", "zh-hsn")),
			Map.entry("zh-yue", List.of("yue")),
			Map.entry("zhk", List.of("sgn-zhk")),
			Map.entry("zib", List.of("sgn-zib")),
			Map.entry("zir", List.of("scv")),
			Map.entry("zkb", List.of("kjh")),
			Map.entry("zko", List.of("xss")),
			Map.entry("zlm", List.of("ms-zlm")),
			Map.entry("zmi", List.of("ms-zmi")),
			Map.entry("zom", List.of("yos")),
			Map.entry("zsl", List.of("sgn-zsl")),
			Map.entry("zsm", List.of("ms-zsm")));

	/**
	 * The JDK's {@code sun.util.locale.LocaleEquivalentMaps.regionVariantEquivMap}, in the order
	 * that JDK's {@code HashMap} happens to iterate it. Declared rather than iterated, because that
	 * order decides which substitution applies when two are applicable and is otherwise unspecified.
	 */
	private static final List<String[]> REGION_VARIANT_EQUIVALENTS = List.of(
			new String[] { "-bu", "-mm" }, new String[] { "-tl", "-tp" }, new String[] { "-zr", "-cd" },
			new String[] { "-tp", "-tl" }, new String[] { "-dd", "-de" }, new String[] { "-mm", "-bu" },
			new String[] { "-cd", "-zr" }, new String[] { "-de", "-dd" }, new String[] { "-heploc", "-alalc97" },
			new String[] { "-alalc97", "-heploc" }, new String[] { "-yd", "-ye" }, new String[] { "-fr", "-fx" },
			new String[] { "-ye", "-yd" }, new String[] { "-fx", "-fr" });

	/** {@code LanguageRange}'s MAX_WEIGHT and MIN_WEIGHT, which it does not expose. */
	private static final double MAXIMUM_WEIGHT = 1.0;
	private static final double MINIMUM_WEIGHT = 0.0;

	private IanaLanguageEquivalents() {}

	/**
	 * Parses an {@code Accept-Language} field value, expanding equivalences from the pinned registry.
	 * <p>
	 * This is {@code Locale.LanguageRange.parse}'s algorithm with ONE substitution: the language
	 * equivalence table. Everything else is reproduced because it is observable — the normalisation,
	 * Java's comma splitting (which drops trailing empty members and keeps interior ones), the
	 * {@code ;q=} weight grammar, the WEIGHT-ORDERED STABLE INSERTION, first-wins de-duplication, and
	 * the insertion of each derived range immediately after its original, which is why an equivalence
	 * class appears in reverse insertion order.
	 * <p>
	 * <strong>The range grammar and the weight bound stay the JDK's, deliberately.</strong>
	 * {@code new LanguageRange(range, weight)} validates both and expands NOTHING — verified rather
	 * than assumed: the constructor returns {@code iw} for {@code iw} and {@code de-de} for
	 * {@code de-DE}, where {@code parse} returns two ranges for each. Reimplementing the grammar
	 * would be a second thing to keep faithful for no benefit.
	 */
	static List<LanguageRange> parse(String ranges) {
		requireNonNull(ranges);

		String normalized = ranges.replace(" ", "").toLowerCase(Locale.ROOT);

		if (normalized.startsWith("accept-language:"))
			normalized = normalized.substring("accept-language:".length());

		List<LanguageRange> list = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();

		for (String member : normalized.split(",")) {
			String range = member;
			double weight = MAXIMUM_WEIGHT;
			int weightIndex = member.indexOf(";q=");

			if (weightIndex != -1) {
				range = member.substring(0, weightIndex);
				String text = member.substring(weightIndex + 3);

				try {
					weight = Double.parseDouble(text);
				} catch (NumberFormatException exception) {
					throw new IllegalArgumentException("weight=\"" + text + "\" for language range \"" + range + "\"");
				}

				if (weight < MINIMUM_WEIGHT || weight > MAXIMUM_WEIGHT)
					throw new IllegalArgumentException("weight=" + weight + " for language range \"" + range
							+ "\". It must be between " + MINIMUM_WEIGHT + " and " + MAXIMUM_WEIGHT + ".");
			}

			if (seen.contains(range))
				continue;

			// The JDK's own grammar check and its own refusal, without its equivalence table.
			LanguageRange parsed = new LanguageRange(range, weight);

			int index = list.size();

			for (int position = 0; position < list.size(); ++position)
				if (list.get(position).getWeight() < weight) {
					index = position;
					break;
				}

			list.add(index, parsed);
			seen.add(range);

			for (String equivalent : expansionsFor(range))
				if (seen.add(equivalent))
					list.add(index + 1, new LanguageRange(equivalent, weight));
		}

		return Collections.unmodifiableList(list);
	}


	/** Insertion-ordered expansions for one range: the region/variant arm, then each language arm. */
	private static List<String> expansionsFor(String range) {
		List<String> expansions = new ArrayList<>();

		String regionVariant = equivalentForRegionAndVariant(range);
		if (regionVariant != null)
			expansions.add(regionVariant);

		for (String equivalent : equivalentsForLanguage(range)) {
			expansions.add(equivalent);
			String nested = equivalentForRegionAndVariant(equivalent);
			if (nested != null)
				expansions.add(nested);
		}

		return expansions;
	}

	/** The longest registry-known prefix of the range, with the remainder carried across. */
	private static List<String> equivalentsForLanguage(String range) {
		String prefix = range;

		while (!prefix.isEmpty()) {
			List<String> equivalenceClass = LANGUAGE_EQUIVALENTS.get(prefix);

			if (equivalenceClass != null) {
				String suffix = range.substring(prefix.length());
				List<String> equivalents = new ArrayList<>(equivalenceClass.size());

				for (String equivalent : equivalenceClass)
					equivalents.add(equivalent + suffix);

				return equivalents;
			}

			int index = prefix.lastIndexOf('-');
			if (index == -1)
				break;

			prefix = prefix.substring(0, index);
		}

		return List.of();
	}

	/**
	 * {@code sun.util.locale.LocaleMatcher#getEquivalentForRegionAndVariant}, reproduced including its
	 * substring semantics: the subtag may sit anywhere in the range provided it ends at the range's
	 * end or at a hyphen and is not inside a singleton extension.
	 */
	private static String equivalentForRegionAndVariant(String range) {
		int keyIndex = extensionKeyIndex(range);

		for (String[] pair : REGION_VARIANT_EQUIVALENTS) {
			int index = range.indexOf(pair[0]);
			if (index == -1)
				continue;
			if (keyIndex != Integer.MIN_VALUE && index > keyIndex)
				continue;

			int end = index + pair[0].length();
			if (range.length() == end || range.charAt(end) == '-')
				return range.substring(0, index) + pair[1] + range.substring(end);
		}

		return null;
	}

	/** {@code LocaleMatcher#getExtentionKeyIndex}, misspelling and all. */
	private static int extensionKeyIndex(String range) {
		int index = Integer.MIN_VALUE;

		for (int position = 1; position < range.length(); ++position)
			if (range.charAt(position) == '-') {
				if (position - index == 2)
					return index;
				index = position;
			}

		return Integer.MIN_VALUE;
	}

}
