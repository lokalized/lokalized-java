# The separate instrumentation APK calls these sample adapter entry points.
# Keep them available across APKs; the Lokalized library remains fully optimized.
-keep,allowobfuscation class com.lokalized.example.AndroidStrings {
    public <init>(android.content.Context);
    public void updateLocale(android.content.Context);
    public java.lang.String get(java.lang.String, java.util.Map);
    public java.lang.String get(java.lang.String, java.util.Map, java.util.Locale);
    public java.lang.String get(android.content.Context, java.lang.String, java.util.Map);
}
-keep,allowobfuscation class com.lokalized.example.ExampleApplication {
    public com.lokalized.example.AndroidStrings getStrings();
}
-keep,allowobfuscation class com.lokalized.example.CompatibilityProbe {
    public static java.util.Map results(com.lokalized.example.AndroidStrings);
}
