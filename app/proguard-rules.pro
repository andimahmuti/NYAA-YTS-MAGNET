# R8 Full-Mode Ultra-Compression Rules
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5
-dontusemixedcaseclassnames

# Remove unused attributes and debug info
-dontnote **
-dontwarn **
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Strip Kotlin debug assertions
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkNotNullExpressionValue(...);
}

# Keep project classes & scrapers intact
-keep class com.example.nyaamagnet.** { *; }

# Keep OkHttp & Jsoup
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class org.jsoup.** { *; }

# Keep ViewModels & Activities
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}
-keep class * extends androidx.activity.ComponentActivity {
    public <init>(...);
}
