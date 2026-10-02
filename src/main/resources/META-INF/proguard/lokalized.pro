# Optional compile-time annotations do not participate in translation at runtime.
# R8 automatically reads this file from the Java JAR. No library keep rules needed.
-dontwarn javax.annotation.concurrent.Immutable
-dontwarn javax.annotation.concurrent.NotThreadSafe
-dontwarn javax.annotation.concurrent.ThreadSafe
-dontwarn org.jspecify.annotations.NonNull
-dontwarn org.jspecify.annotations.Nullable
-dontwarn org.jspecify.annotations.NullMarked
